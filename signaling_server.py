import asyncio
import hmac
import http
import json
import os
import re
import time

import websockets

from webrtc_config import get_ice_servers_config  # Gọi module cấu hình NAT (STUN/TURN)

# user_id -> websocket đang hoạt động
connected_clients = {}
# user_id -> danh sách (thời điểm, gói tin) nhỡ khi người nhận đang rớt mạng (Store-and-Forward)
offline_queue = {}

# SDP/ICE quá cũ sẽ vô dụng (đường truyền đã đổi) -> chỉ giữ trong 30 giây
OFFLINE_MESSAGE_TTL_SECONDS = 30
MAX_OFFLINE_MESSAGES_PER_USER = 200

# Các loại gói tin chỉ cần chuyển tiếp nguyên vẹn tới target_id
FORWARD_TYPES = {
    "offer",
    "answer",
    "ice_candidate",
    "ice_candidates_removed",  # Tuần 3: ICE Gathering liên tục - ứng viên hết hạn khi đổi mạng
    "hangup",
    "voice_note",              # Tuần 5 (TV2): Hàng đợi ngoại tuyến Store-and-Forward
}

stats = {"messages_forwarded": 0, "messages_queued": 0, "registrations": 0, "rejected": 0}

# ---------------------------------------------------------------- Bảo mật (TV1)
# 1) Mã truy cập chung: đặt SIGNALING_TOKEN trong .env / biến môi trường của máy chủ cloud.
#    App phải gửi đúng mã trong gói register mới được nhận cấu hình STUN/TURN và gọi điện.
#    Để trống = chế độ thử nghiệm trong mạng nhà (ai cũng vào được, có cảnh báo khi khởi động).
# 2) Giữ chỗ user_id: mỗi máy tự sinh device_key ngẫu nhiên và gửi kèm khi register.
#    user_id đang thuộc máy khác (đang online hoặc vừa rớt mạng < CLAIM_TTL_SECONDS)
#    thì máy lạ không đăng ký đè được -> không "cướp" được cuộc gọi của người khác.
#    Chính máy đó đổi Wi-Fi -> 4G vẫn đăng ký lại bình thường vì có cùng device_key.
CLAIM_TTL_SECONDS = 600
USER_ID_PATTERN = re.compile(r"^[\w.\-]{1,64}$")
DEVICE_KEY_MIN_LEN, DEVICE_KEY_MAX_LEN = 16, 128

CLOSE_BAD_REQUEST = 4000
CLOSE_UNAUTHORIZED = 4001
CLOSE_USER_ID_TAKEN = 4009

# user_id -> {"key": device_key, "released_at": None (đang online) | thời điểm rớt mạng}
claims = {}


def _same_secret(a, b):
    return isinstance(a, str) and isinstance(b, str) and hmac.compare_digest(a.encode(), b.encode())


def check_token(token):
    expected = os.environ.get("SIGNALING_TOKEN", "").strip()
    if not expected:
        return True
    return _same_secret(token, expected)


def claim_user_id(user_id, device_key, now=None):
    """Trả về True nếu device_key được phép dùng user_id này."""
    now = now or time.time()
    for uid in [u for u, c in claims.items()
                if c["released_at"] is not None and now - c["released_at"] >= CLAIM_TTL_SECONDS]:
        del claims[uid]
    current = claims.get(user_id)
    if current is not None and not _same_secret(current["key"], device_key):
        return False
    claims[user_id] = {"key": device_key, "released_at": None}
    return True


def release_user_id(user_id, now=None):
    if user_id in claims:
        claims[user_id]["released_at"] = now or time.time()


def valid_register(user_id, device_key):
    return (
        isinstance(user_id, str) and USER_ID_PATTERN.match(user_id) is not None
        and isinstance(device_key, str) and DEVICE_KEY_MIN_LEN <= len(device_key) <= DEVICE_KEY_MAX_LEN
    )


async def reject(websocket, code, reason):
    stats["rejected"] += 1
    print(f"Từ chối kết nối: {reason}")
    await websocket.close(code, reason)


async def safe_send(ws, payload):
    try:
        await ws.send(payload)
        return True
    except websockets.exceptions.ConnectionClosed:
        return False


def prune_offline_queue(now=None):
    """Bỏ các gói tin nhỡ đã quá hạn, tránh bộ nhớ phình ra khi người nhận không bao giờ quay lại."""
    now = now or time.time()
    for target_id in list(offline_queue):
        fresh = [(ts, msg) for (ts, msg) in offline_queue[target_id] if now - ts <= OFFLINE_MESSAGE_TTL_SECONDS]
        if fresh:
            offline_queue[target_id] = fresh
        else:
            del offline_queue[target_id]


def queue_offline(target_id, data):
    prune_offline_queue()
    queue = offline_queue.setdefault(target_id, [])
    queue.append((time.time(), data))
    if len(queue) > MAX_OFFLINE_MESSAGES_PER_USER:
        del queue[: len(queue) - MAX_OFFLINE_MESSAGES_PER_USER]
    stats["messages_queued"] += 1


# Gộp nhiều lần vào/ra sát nhau (vd: 300 máy cùng vào khi stress test) thành 1 lần gửi danh sách
PEERS_BROADCAST_DELAY_SECONDS = 0.2
_peers_broadcast_task = None


async def _broadcast_peers_now():
    global _peers_broadcast_task
    await asyncio.sleep(PEERS_BROADCAST_DELAY_SECONDS)
    _peers_broadcast_task = None
    payload = json.dumps({"type": "peers", "peers": sorted(connected_clients)})
    for ws in list(connected_clients.values()):
        await safe_send(ws, payload)


async def broadcast_peers():
    """Gửi danh sách user_id đang online cho mọi thiết bị để app biết có thể gọi ai."""
    global _peers_broadcast_task
    if _peers_broadcast_task is None:
        _peers_broadcast_task = asyncio.create_task(_broadcast_peers_now())


async def flush_offline_queue(user_id, websocket):
    queue = offline_queue.pop(user_id, [])
    now = time.time()
    fresh = [msg for (ts, msg) in queue if now - ts <= OFFLINE_MESSAGE_TTL_SECONDS]
    if fresh:
        print(f"Đang khôi phục {len(fresh)} gói tin nhỡ cho [{user_id}]")
    for missed_msg in fresh:
        await safe_send(websocket, json.dumps(missed_msg))


async def signaling_handler(websocket):
    user_id = None
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
            except json.JSONDecodeError:
                continue
            msg_type = data.get("type")

            # 1. Thiết bị đăng ký (Handshake)
            if msg_type == "register":
                new_id = data.get("user_id")
                device_key = data.get("device_key")
                if not check_token(data.get("token")):
                    await reject(websocket, CLOSE_UNAUTHORIZED, "unauthorized")
                    return
                if not valid_register(new_id, device_key):
                    await reject(websocket, CLOSE_BAD_REQUEST, "bad register")
                    return
                if user_id is not None and user_id != new_id:
                    continue  # 1 kết nối chỉ mang 1 user_id
                if not claim_user_id(new_id, device_key):
                    await reject(websocket, CLOSE_USER_ID_TAKEN, "user_id taken")
                    return
                user_id = new_id
                connected_clients[user_id] = websocket  # kết nối mới (vd: sau khi đổi 4G) thay thế kết nối cũ
                stats["registrations"] += 1
                print(f"[{user_id}] đã kết nối mạng.")

                # Cấp cấu hình STUN/TURN cho thiết bị
                ice_config_msg = {
                    "type": "webrtc_config",
                    "config": json.loads(get_ice_servers_config()),
                }
                await safe_send(websocket, json.dumps(ice_config_msg))
                print(f"Đã cấp phát cấu hình STUN/TURN cho [{user_id}]")

                await flush_offline_queue(user_id, websocket)
                await broadcast_peers()

            # 2. Định tuyến P2P: offer / answer / ice_candidate / ...
            elif msg_type in FORWARD_TYPES:
                if user_id is None:
                    continue  # chưa register thì không được gửi
                target_id = data.get("target_id")
                if not target_id:
                    continue
                # Server tự gắn sender_id thật -> chống giả mạo người gửi
                data["sender_id"] = user_id
                target_ws = connected_clients.get(target_id)
                if target_ws is not None and await safe_send(target_ws, json.dumps(data)):
                    stats["messages_forwarded"] += 1
                else:
                    queue_offline(target_id, data)

            # 3. Thông báo chuyển mạng (Seamless Handover) - chỉ ghi log để theo dõi thực địa
            elif msg_type == "network_change":
                print(f"[HANDOVER] [{user_id}] chuyển mạng sang {data.get('network_type')}")

            # 4. Đo độ trễ signaling (dùng cho stress test Tuần 6)
            elif msg_type == "ping":
                await safe_send(websocket, json.dumps({"type": "pong", "ts": data.get("ts")}))

            elif msg_type == "stats" and user_id is not None:
                await safe_send(websocket, json.dumps({
                    "type": "stats",
                    "online": len(connected_clients),
                    **stats,
                }))

    except websockets.exceptions.ConnectionClosed:
        print(f"[{user_id}] đã ngắt kết nối đột ngột.")
    finally:
        # Chỉ xoá nếu đây vẫn là kết nối hiện hành. Khi đổi Wi-Fi -> 4G, app mở kết nối mới
        # trước khi kết nối cũ timeout; xoá nhầm sẽ làm mất định tuyến của kết nối mới.
        if user_id is not None and connected_clients.get(user_id) is websocket:
            del connected_clients[user_id]
            release_user_id(user_id)
            await broadcast_peers()


async def health_check(path, request_headers):
    """Máy chủ cloud (Render, Railway...) gọi GET /healthz để biết dịch vụ còn sống."""
    if path == "/healthz":
        return http.HTTPStatus.OK, [("Content-Type", "text/plain")], b"OK\n"
    return None


async def start_server(host="0.0.0.0", port=None):
    """Mở máy chủ (dùng chung cho chạy thật và cho bài kiểm tra tự động). Cổng mặc định 8765, đổi bằng biến PORT."""
    port = int(os.environ.get("PORT", 8765)) if port is None else port
    return await websockets.serve(
        signaling_handler,
        host,
        port,
        ping_interval=15,
        ping_timeout=20,
        max_size=2 ** 20,
        process_request=health_check,
    )


async def main():
    if not os.environ.get("SIGNALING_TOKEN", "").strip():
        print("[CẢNH BÁO] Chưa đặt SIGNALING_TOKEN: ai biết địa chỉ máy chủ cũng vào được. "
              "Chỉ dùng như vậy khi thử trong mạng nhà.")
    server = await start_server()
    port = server.sockets[0].getsockname()[1]
    print(f"Signaling Server đang chạy trên cổng {port}...")
    await server.wait_closed()


if __name__ == "__main__":
    asyncio.run(main())
