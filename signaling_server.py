import asyncio
import json
import os
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

stats = {"messages_forwarded": 0, "messages_queued": 0, "registrations": 0}


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
                user_id = data.get("user_id")
                if not user_id:
                    continue
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

            elif msg_type == "stats":
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
            await broadcast_peers()


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
    )


async def main():
    server = await start_server()
    port = server.sockets[0].getsockname()[1]
    print(f"Signaling Server đang chạy trên cổng {port}...")
    await server.wait_closed()


if __name__ == "__main__":
    asyncio.run(main())
