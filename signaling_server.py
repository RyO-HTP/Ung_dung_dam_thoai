"""
Signaling Server cho ứng dụng Đàm Thoại.

Nhiệm vụ: giúp các điện thoại tìm thấy nhau và trao đổi thông tin để mở cuộc gọi WebRTC.
Âm thanh KHÔNG đi qua server này (đi thẳng giữa các máy, hoặc qua máy chủ TURN).

Luồng chính:
  1. Điện thoại kết nối WebSocket và gửi gói "register" (ID, mã PIN sự kiện, tên, vai trò).
  2. Server gửi lại cấu hình STUN/TURN, tên sự kiện, và danh sách thành viên online cùng PIN.
  3. Server chuyển tiếp offer / answer / ice_candidate giữa hai máy trong CÙNG sự kiện.

Chạy trên máy tính:  python signaling_server.py   (cổng 8765, tự phát mDNS để app tìm thấy)
Chạy trên đám mây:   Render đặt biến PORT và RENDER (xem render.yaml)
"""
import asyncio
import json
import os
import re
import socket
import sys

import websockets

from webrtc_config import get_ice_servers_config


def force_utf8_output():
    """Console Windows (cp1252), IDE hay pipe không in được tiếng Việt: print() sẽ ném
    UnicodeEncodeError và làm sập kết nối của thiết bị đang xử lý. Ép stdout/stderr dùng UTF-8."""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass


force_utf8_output()

# ----------------------------------------------------------------------------
# CẤU HÌNH
# ----------------------------------------------------------------------------
# Render/Fly... cấp cổng qua biến môi trường PORT; chạy trên máy tính thì dùng 8765
PORT = int(os.environ.get("PORT", "8765"))
# Trên đám mây không có mạng LAN nên bỏ mDNS (Render tự đặt biến RENDER)
ON_CLOUD = bool(os.environ.get("RENDER") or os.environ.get("DISABLE_MDNS"))
SERVICE_TYPE = "_bodam._tcp.local."  # Tên dịch vụ mDNS mà app Android tìm kiếm

# ----------------------------------------------------------------------------
# TRẠNG THÁI (chỉ lưu trong bộ nhớ, mất khi tắt server)
# ----------------------------------------------------------------------------
connected_clients = {}  # user_id -> websocket đang mở
client_profiles = {}    # user_id -> {"event_pin", "display_name", "role_title", "is_organizer"}
events = {}             # event_pin -> {"event_name", "organizer_id"} (do ban tổ chức đặt)


# ----------------------------------------------------------------------------
# HÀM TIỆN ÍCH
# ----------------------------------------------------------------------------
def members_of(event_pin):
    """Danh sách thành viên đang online trong cùng một sự kiện (cùng mã PIN).
    Ban tổ chức đứng đầu, sau đó sắp theo tên."""
    members = [
        {
            "user_id": uid,
            "display_name": profile.get("display_name") or uid,
            "role_title": profile.get("role_title") or "",
            "is_organizer": bool(profile.get("is_organizer")),
        }
        for uid, profile in client_profiles.items()
        if uid in connected_clients and profile.get("event_pin", "") == event_pin
    ]
    members.sort(key=lambda m: (not m["is_organizer"], m["display_name"].lower()))
    return members


def client_ip(ws):
    """IP thật của thiết bị. Khi đi qua Cloudflare/Render, socket chỉ thấy IP của proxy;
    IP thật nằm trong header CF-Connecting-IP / X-Forwarded-For."""
    headers = getattr(ws, "request_headers", None)
    if headers is not None:
        forwarded = headers.get("CF-Connecting-IP") or headers.get("X-Forwarded-For")
        if forwarded:
            return forwarded.split(",")[0].strip() + " (qua Internet)"
    remote = getattr(ws, "remote_address", None)
    return remote[0] if remote else "?"


async def safe_send(ws, payload):
    """Gửi tới một socket. Socket đã chết thì trả False và bỏ qua,
    thay vì ném lỗi làm sập luôn kết nối của người gửi."""
    try:
        await ws.send(payload)
        return True
    except websockets.exceptions.ConnectionClosed:
        return False


async def broadcast(event_pin, payload, exclude=None):
    """Gửi một gói tin cho mọi thành viên online của sự kiện (trừ `exclude`).
    Gửi SONG SONG: một máy mạng chậm không làm trễ cả nhóm."""
    targets = [
        ws for uid, ws in connected_clients.items()
        if ws is not exclude and client_profiles.get(uid, {}).get("event_pin", "") == event_pin
    ]
    await asyncio.gather(*(safe_send(ws, payload) for ws in targets))


def event_info_payload(event_pin):
    """Gói "event_info": tên sự kiện do ban tổ chức đặt cho mã PIN (found=False nếu chưa có)."""
    info = events.get(event_pin)
    return json.dumps({
        "type": "event_info",
        "event_pin": event_pin,
        "event_name": info["event_name"] if info else "",
        "found": info is not None,
    })


async def notify_user_list(event_pin):
    """Phát danh sách thành viên online mới nhất cho cả sự kiện."""
    members = members_of(event_pin)
    print(f"Sự kiện [{event_pin or '-'}]: {len(members)} người online: {[m['user_id'] for m in members]}")
    await broadcast(event_pin, json.dumps({
        "type": "user_list",
        "event_pin": event_pin,
        "users": [m["user_id"] for m in members],  # Trường cũ, giữ để tương thích app bản trước
        "members": members,                         # Trường mới: kèm tên và vai trò
    }))


# ----------------------------------------------------------------------------
# XỬ LÝ GÓI TIN
# ----------------------------------------------------------------------------
async def handle_register(websocket, user_id, data):
    """Gói "register": thiết bị đăng ký lần đầu, hoặc gửi lại khi đổi tên/vai trò/sự kiện.
    Trả về user_id mới của socket này (None nếu gói tin không hợp lệ)."""
    new_user_id = data.get("user_id")
    if not new_user_id:
        return user_id

    # Sự kiện cũ (của socket này, hoặc phiên trước cùng ID) để báo cho nhóm cũ khi đổi sự kiện
    previous_pin = client_profiles.get(user_id or new_user_id, {}).get("event_pin")

    # Socket này trước đó đăng ký bằng ID khác: gỡ ID cũ
    if user_id and user_id != new_user_id and connected_clients.get(user_id) is websocket:
        del connected_clients[user_id]
        client_profiles.pop(user_id, None)

    # Cùng ID đăng nhập lại từ socket mới (app kết nối lại khi đổi mạng): đóng phiên cũ
    # để không còn "phiên ma" giữ chỗ và nhận nhầm gói tin.
    old_ws = connected_clients.get(new_user_id)
    is_first_register = old_ws is not websocket
    if old_ws is not None and old_ws is not websocket:
        asyncio.ensure_future(old_ws.close(code=4000, reason="Đăng nhập từ kết nối khác"))

    user_id = new_user_id
    connected_clients[user_id] = websocket
    event_pin = str(data.get("event_pin") or "").strip()
    is_organizer = bool(data.get("is_organizer"))
    display_name = str(data.get("display_name") or "").strip()
    client_profiles[user_id] = {
        "event_pin": event_pin,
        "display_name": display_name,
        "role_title": str(data.get("role_title") or "").strip(),
        "is_organizer": is_organizer,
    }

    # Ban tổ chức là nguồn duy nhất đặt tên sự kiện cho mã PIN
    event_name = str(data.get("event_name") or "").strip()
    if is_organizer and event_pin and event_name:
        events[event_pin] = {"event_name": event_name, "organizer_id": user_id}

    if is_first_register:
        print(f"[{user_id}] đã kết nối mạng từ {client_ip(websocket)} "
              f"(sự kiện {event_pin or '-'}, tên '{display_name or '-'}').")
        # Cấp cấu hình STUN/TURN để thiết bị mở được cuộc gọi WebRTC
        await safe_send(websocket, json.dumps({
            "type": "webrtc_config",
            "config": json.loads(get_ice_servers_config()),
        }))

    if event_pin:
        await safe_send(websocket, event_info_payload(event_pin))
        # Ban tổ chức vừa đặt/đổi tên: báo cho mọi người khác trong sự kiện
        if is_organizer and event_name:
            await broadcast(event_pin, event_info_payload(event_pin), exclude=websocket)

    await notify_user_list(event_pin)
    if previous_pin is not None and previous_pin != event_pin:
        await notify_user_list(previous_pin)  # Nhóm cũ: bớt một người
    return user_id


async def route_peer_message(user_id, msg_type, data):
    """Chuyển tiếp offer / answer / ice_candidate tới máy đích trong CÙNG sự kiện."""
    target_id = data.get("target_id")
    data["sender_id"] = user_id  # Server tự gắn người gửi: thiết bị không giả mạo được
    my_pin = client_profiles.get(user_id, {}).get("event_pin", "")
    target_pin = client_profiles.get(target_id, {}).get("event_pin") if target_id else None

    if target_id in connected_clients and target_pin == my_pin:
        if not await safe_send(connected_clients[target_id], json.dumps(data)):
            print(f"[{user_id}] gửi {msg_type} tới [{target_id}] thất bại (socket đã đóng).")
    elif target_id in connected_clients:
        print(f"[{user_id}] chặn {msg_type} tới [{target_id}] ở sự kiện khác.")
    else:
        # Không lưu lại để gửi sau: SDP/ICE cũ đến muộn sẽ làm hỏng phiên WebRTC mới.
        # Khi máy kia vào lại, danh sách user_list mới sẽ kích hoạt cuộc gọi lại từ đầu.
        print(f"[{user_id}] gửi {msg_type} tới [{target_id}] đang offline, bỏ qua.")


async def signaling_handler(websocket):
    """Một vòng lặp cho mỗi thiết bị kết nối, chạy tới khi socket đóng."""
    user_id = None
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
            except (json.JSONDecodeError, TypeError):
                print(f"[{user_id}] gửi gói tin không phải JSON, bỏ qua.")
                continue
            if not isinstance(data, dict):
                continue

            msg_type = data.get("type")
            if msg_type == "register":
                user_id = await handle_register(websocket, user_id, data)
            elif msg_type in ("offer", "answer", "ice_candidate") and user_id:
                await route_peer_message(user_id, msg_type, data)
    except websockets.exceptions.ConnectionClosed:
        print(f"[{user_id}] đã ngắt kết nối đột ngột.")
    finally:
        # Chỉ gỡ nếu ID vẫn trỏ tới đúng socket này (không xóa nhầm phiên kết nối lại mới hơn)
        if user_id is not None and connected_clients.get(user_id) is websocket:
            del connected_clients[user_id]
            profile = client_profiles.pop(user_id, {})
            await notify_user_list(profile.get("event_pin", ""))


# ----------------------------------------------------------------------------
# MẠNG LAN & mDNS (chỉ dùng khi chạy trên máy tính)
# ----------------------------------------------------------------------------
def get_lan_ip():
    """IP LAN thật của máy (card mạng đi ra router), bỏ qua card ảo VMware/VirtualBox.
    Mẹo: "kết nối" UDP tới một địa chỉ bất kỳ rồi xem hệ điều hành chọn IP nguồn nào."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


async def start_mdns(ip, port):
    """Phát mDNS để app tự tìm máy chủ trong mạng Wi-Fi.
    Thiếu thư viện zeroconf hoặc lỗi cũng không sao: app vẫn tự quét mạng tìm cổng 8765."""
    try:
        from zeroconf import IPVersion, ServiceInfo
        from zeroconf.asyncio import AsyncZeroconf
    except ImportError:
        print("(Không có thư viện zeroconf: bỏ qua mDNS, app vẫn tự quét mạng tìm máy chủ.)")
        return None, None

    host = re.sub(r"[^A-Za-z0-9-]", "-", socket.gethostname()) or "server"
    info = ServiceInfo(
        SERVICE_TYPE,
        f"BoDamServer-{host}.{SERVICE_TYPE}",
        addresses=[socket.inet_aton(ip)],
        port=port,
        properties={"desc": "Signaling Server cho Ung_dung_dam_thoai"},
        # Tên host riêng: nếu dùng đúng tên máy, Windows tự trả lời kèm IP card ảo làm app nối nhầm
        server=f"bodam-{host}.local.",
    )
    azc = AsyncZeroconf(ip_version=IPVersion.V4Only, interfaces=[ip])  # Chỉ phát trên card LAN thật
    try:
        # allow_name_change: tên đã có người dùng (chạy 2 lần) thì tự đổi tên, không sập
        await azc.async_register_service(info, allow_name_change=True)
        print(f"Đang phát mDNS: {info.name} -> {ip}:{port}")
        return azc, info
    except Exception as e:
        print(f"Không phát được mDNS ({e!r}); app vẫn tự quét mạng tìm máy chủ.")
        await azc.async_close()
        return None, None


# ----------------------------------------------------------------------------
# KHỞI ĐỘNG
# ----------------------------------------------------------------------------
async def health_check(path, request_headers):
    """Yêu cầu HTTP thường (không phải WebSocket) như /healthz: trả "OK <số người> online".
    Render dùng để biết server còn sống; app dùng để kiểm tra và giữ máy chủ free không ngủ."""
    if request_headers.get("Upgrade", "").lower() != "websocket":
        body = f"OK {len(connected_clients)} online".encode()
        return 200, [("Content-Type", "text/plain; charset=utf-8")], body
    return None  # Là WebSocket: xử lý bình thường


async def main():
    try:
        server = await websockets.serve(
            signaling_handler, "0.0.0.0", PORT,
            process_request=health_check,
            ping_interval=20, ping_timeout=20,  # Dọn kết nối chết (máy mất sóng không kịp báo đóng)
        )
    except OSError as e:
        print(f"KHÔNG mở được cổng {PORT}: {e}")
        print("Có thể một server khác đang chạy. Tắt cửa sổ server cũ rồi chạy lại.")
        sys.exit(1)

    if ON_CLOUD:
        print(f"Signaling Server (đám mây) đang chạy trên cổng {PORT}")
        await server.wait_closed()
        return

    ip = get_lan_ip()
    print("=" * 60)
    print(f"Signaling Server đang chạy trên cổng {PORT}")
    print(f"Địa chỉ cho điện thoại (cùng Wi-Fi): ws://{ip}:{PORT}")
    print("App tự tìm máy chủ; chỉ cần nhập địa chỉ trên nếu không tìm thấy.")
    print("=" * 60)

    azc, info = await start_mdns(ip, PORT)
    try:
        await server.wait_closed()
    finally:
        if azc is not None:
            await azc.async_unregister_service(info)
            await azc.async_close()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Đã tắt server.")
