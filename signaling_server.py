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
events = {}             # event_pin -> {"event_name", "organizer_id", "admin_code", "catalog"}
pending_seats = {}      # (event_pin, user_id) -> vai trò chủ sự kiện vừa đặt, chờ máy đó xác nhận
banned = {}             # event_pin -> {user_id} bị chủ sự kiện xoá (không vào lại được)

# ----------------------------------------------------------------------------
# GIÀNH LƯỢT NÓI (FLOOR CONTROL) — giống backend Go Tuần 4
# ----------------------------------------------------------------------------
# Mỗi kênh của mỗi sự kiện chỉ một người nói tại một thời điểm. Vai trò cao hơn được chen ngang
# (thu hồi lượt của người thấp hơn); ngang hàng thì ai bấm trước nói trước, người sau bị báo "bận".
ROLE_PRIORITY = {"ban": 3, "nhom": 2, "tv": 1}
COMMAND_CHANNEL = "ch_01"
# Giới hạn nói 60 giây chống kẹt phím (giống MaxSpeakDurationMs bên Go); đổi được qua biến môi trường
FLOOR_MAX_SECONDS = float(os.environ.get("FLOOR_MAX_SECONDS", "60"))
# Người giữ lượt không báo "vẫn đang nói" quá 5 giây (mất sóng) thì thu hồi (Go: StaleUdpSilenceMs)
FLOOR_STALE_SECONDS = float(os.environ.get("FLOOR_STALE_SECONDS", "5"))
floors = {}  # (event_pin, channel_id) -> {"user_id", "priority", "granted_at", "last_seen"}
conn_counter = 0  # Tăng mỗi lần một máy mở socket mới (xem "conn_id" trong handle_register)

# Lưu sự kiện (mã quản trị, chủ sự kiện, phòng ban), người bị xoá và vai trò chờ áp ra file,
# để khởi động lại server không làm mất. Đổi đường dẫn bằng biến STATE_FILE.
STATE_FILE = os.environ.get("STATE_FILE") or os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "server_state.json")


def save_state():
    try:
        state = {
            "events": events,
            "banned": {pin: sorted(ids) for pin, ids in banned.items()},
            "pending_seats": [[pin, uid, seat] for (pin, uid), seat in pending_seats.items()],
        }
        tmp = STATE_FILE + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(state, f, ensure_ascii=False)
        os.replace(tmp, STATE_FILE)
    except OSError as e:
        print(f"Không lưu được trạng thái ({STATE_FILE}): {e}")


def load_state():
    try:
        with open(STATE_FILE, encoding="utf-8") as f:
            state = json.load(f)
    except FileNotFoundError:
        return
    except (OSError, ValueError) as e:
        print(f"Bỏ qua file trạng thái hỏng ({STATE_FILE}): {e}")
        return
    events.update(state.get("events") or {})
    for pin, ids in (state.get("banned") or {}).items():
        banned[pin] = set(ids)
    for pin, uid, seat in state.get("pending_seats") or []:
        pending_seats[(pin, uid)] = seat
    print(f"Đã nạp {len(events)} sự kiện từ {STATE_FILE}.")


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
            # Phân tầng vai trò: app dùng để biết ai cùng kênh nào, ai được đàm thoại với ai
            "team_role": profile.get("team_role") or "tv",
            "channel_id": profile.get("channel_id") or "ch_general",
            "channel_name": profile.get("channel_name") or "",
            "conn_id": profile.get("conn_id") or 0,
        }
        for uid, profile in client_profiles.items()
        if uid in connected_clients and profile.get("event_pin", "") == event_pin
    ]
    members.sort(key=lambda m: (not m["is_organizer"], m["display_name"].lower()))
    return members


def seat_assigned_payload(event_pin, seat, by_name=None):
    """Gói "seat_assigned": báo một máy biết vai trò/phòng ban mới do chủ sự kiện đặt."""
    if not by_name:
        info = events.get(event_pin) or {}
        by_name = client_profiles.get(info.get("organizer_id"), {}).get("display_name")
    return json.dumps({
        "type": "seat_assigned",
        **seat,
        "by_name": by_name or "Chủ sự kiện",
    })


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
    info = events.get(event_pin) or {}
    return json.dumps({
        "type": "event_info",
        "event_pin": event_pin,
        "event_name": info.get("event_name") or "",
        "found": bool(info.get("event_name")),
        "catalog": info.get("catalog") or [],
    })


def kicked_payload(event_pin, by_name=None):
    return json.dumps({
        "type": "kicked",
        "event_pin": event_pin,
        "by_name": by_name or "Chủ sự kiện",
    })


def parse_catalog(raw):
    """Danh mục phòng ban chủ sự kiện gửi lên: [{"id", "name"}] (None nếu không gửi/sai định dạng)."""
    if not isinstance(raw, list):
        return None
    catalog = []
    for item in raw[:100]:
        if isinstance(item, dict):
            cid = str(item.get("id") or "").strip()[:64]
            name = str(item.get("name") or "").strip()[:80]
            if cid and name and cid not in (COMMAND_CHANNEL,):
                catalog.append({"id": cid, "name": name})
    return catalog


async def verify_organizer(websocket, user_id, event_pin, data):
    """Xác thực chủ sự kiện bằng mã quản trị.
    - Sự kiện chưa có chủ: máy đầu tiên gửi mã (không rỗng) thành chủ, mã đó gắn với sự kiện.
    - Đúng mã + đúng máy chủ hiện tại, hoặc đúng mã + "restore" (Khôi phục): là chủ sự kiện;
      khôi phục trên máy mới thì máy cũ mất quyền (nhận organizer_denied "revoked").
    - Sai mã, hoặc đúng mã nhưng máy cũ (đã bị chuyển quyền) cố giành lại: không phải chủ."""
    code = str(data.get("admin_code") or "").strip().upper()
    info = events.get(event_pin)

    def deny(reason):
        print(f"[{user_id}] không được công nhận là chủ sự kiện {event_pin}: {reason}.")
        return safe_send(websocket, json.dumps({
            "type": "organizer_denied", "event_pin": event_pin, "reason": reason,
        }))

    if not code:
        await deny("bad_code")
        return False
    if info is None or not info.get("admin_code"):
        info = events.setdefault(event_pin, {"event_name": "", "catalog": None})
        info["admin_code"] = code
        info["organizer_id"] = user_id
        return True
    if code != info["admin_code"]:
        await deny("bad_code")
        return False
    old_id = info.get("organizer_id")
    if old_id and old_id != user_id:
        if not data.get("restore"):
            await deny("revoked")
            return False
        # Khôi phục trên máy mới: máy cũ mất quyền chủ sự kiện
        old_profile = client_profiles.get(old_id)
        if old_profile is not None:
            old_profile["is_organizer"] = False
        old_ws = connected_clients.get(old_id)
        if old_ws is not None:
            await safe_send(old_ws, json.dumps({
                "type": "organizer_denied", "event_pin": event_pin, "reason": "revoked",
            }))
        print(f"[{user_id}] khôi phục quyền chủ sự kiện {event_pin} (máy cũ [{old_id}] mất quyền).")
    info["organizer_id"] = user_id
    return True


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
    display_name = str(data.get("display_name") or "").strip()

    # Đã bị chủ sự kiện xoá: không cho vào lại sự kiện này (kể cả quét QR / nhập PIN lại)
    if event_pin and user_id in banned.get(event_pin, set()):
        await safe_send(websocket, kicked_payload(event_pin))
        event_pin = ""

    # Chủ sự kiện phải chứng minh bằng mã quản trị; sai mã thì vào như người thường
    is_organizer = False
    if data.get("is_organizer") and event_pin:
        is_organizer = await verify_organizer(websocket, user_id, event_pin, data)

    # Mã phiên kết nối: đổi mỗi khi máy kết nối socket MỚI (mở lại app, đổi mạng...). Máy khác
    # thấy mã đổi thì bỏ kết nối P2P cũ (đã chết) với máy này và gọi lại ngay, không chờ hết hạn.
    global conn_counter
    prev_conn = client_profiles.get(user_id, {}).get("conn_id")
    if is_first_register or prev_conn is None:
        conn_counter += 1
        conn_id = conn_counter
    else:
        conn_id = prev_conn
    client_profiles[user_id] = {
        "conn_id": conn_id,
        "event_pin": event_pin,
        "display_name": display_name,
        "role_title": str(data.get("role_title") or "").strip(),
        "is_organizer": is_organizer,
        "team_role": str(data.get("team_role") or "tv").strip(),
        "channel_id": str(data.get("channel_id") or "ch_general").strip(),
        "channel_name": str(data.get("channel_name") or "").strip(),
    }
    # Chủ sự kiện đã đổi vai trò của máy này (có thể lúc máy đang offline): áp vai trò đó
    # cho tới khi máy xác nhận bằng cách đăng ký lại đúng vai trò mới.
    pending = pending_seats.get((event_pin, user_id))
    if pending:
        profile = client_profiles[user_id]
        if all(profile.get(k) == v for k, v in pending.items()):
            pending_seats.pop((event_pin, user_id), None)
            save_state()
        else:
            profile.update(pending)
            await safe_send(websocket, seat_assigned_payload(event_pin, pending))

    # Chủ sự kiện (đã xác thực) là nguồn duy nhất đặt tên sự kiện và danh mục phòng ban
    event_name = str(data.get("event_name") or "").strip()
    if is_organizer:
        info = events[event_pin]
        if event_name:
            info["event_name"] = event_name
        catalog = parse_catalog(data.get("catalog"))
        if catalog is not None:
            info["catalog"] = catalog
        save_state()

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
        # Chủ sự kiện vừa đặt/đổi tên: báo cho mọi người khác trong sự kiện
        if is_organizer and event_name:
            await broadcast(event_pin, event_info_payload(event_pin), exclude=websocket)

    await notify_user_list(event_pin)
    if previous_pin is not None and previous_pin != event_pin:
        await notify_user_list(previous_pin)  # Nhóm cũ: bớt một người

    # Đổi sự kiện/vai trò: thu hồi lượt nói ở những kênh không còn thuộc về
    await release_floors_of(user_id, keep_pin=event_pin, keep_channels=channels_of(client_profiles[user_id]))
    # Cho máy vừa vào biết kênh nào đang có người nói
    for (pin, channel_id), current in list(floors.items()):
        if pin == event_pin:
            await safe_send(websocket, floor_state_payload(channel_id, current["user_id"]))
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


def channels_of(profile):
    """Các kênh một người thuộc về (giống TeamChannels.channelsOf bên app)."""
    role = profile.get("team_role") or "tv"
    group = profile.get("channel_id") or "ch_general"
    if role == "ban":
        return {COMMAND_CHANNEL}
    if role == "nhom":
        return {COMMAND_CHANNEL, group}
    return {group}


def floor_state_payload(channel_id, holder_id, preempted_id=None, reason=""):
    return json.dumps({
        "type": "floor_state",
        "channel_id": channel_id,
        "holder_id": holder_id,
        "preempted_id": preempted_id,
        "reason": reason,
    })


async def handle_floor_request(websocket, user_id, data):
    """Xin lượt nói trên một kênh. Trả "floor_granted" hoặc "floor_denied" cho người xin,
    và phát "floor_state" (ai đang giữ lượt) cho cả sự kiện khi lượt đổi chủ."""
    profile = client_profiles.get(user_id, {})
    pin = profile.get("event_pin", "")
    channel_id = str(data.get("channel_id") or "")
    if channel_id not in channels_of(profile):
        await safe_send(websocket, json.dumps({
            "type": "floor_denied", "channel_id": channel_id, "holder_id": None, "reason": "not_member",
        }))
        return

    key = (pin, channel_id)
    priority = ROLE_PRIORITY.get(profile.get("team_role") or "tv", 1)
    current = floors.get(key)
    held_by_other = current is not None and current["user_id"] != user_id and current["user_id"] in connected_clients
    if held_by_other and priority <= current["priority"]:
        # Ngang hoặc thấp hơn người đang nói: phải chờ
        await safe_send(websocket, json.dumps({
            "type": "floor_denied", "channel_id": channel_id, "holder_id": current["user_id"], "reason": "busy",
        }))
        return

    preempted = current["user_id"] if held_by_other else None
    now = asyncio.get_running_loop().time()
    floors[key] = {
        "user_id": user_id,
        "priority": priority,
        "granted_at": now,
        # App mới gửi "keepalive": true và cứ 2 giây báo "vẫn đang nói"; im quá FLOOR_STALE_SECONDS
        # (mất sóng giữa chừng) thì tự thu hồi. App cũ không gửi: chỉ áp giới hạn 60 giây.
        "last_seen": now if data.get("keepalive") else None,
    }
    if preempted:
        print(f"[{user_id}] chen ngang lượt nói của [{preempted}] trên kênh {channel_id}.")
    await safe_send(websocket, json.dumps({"type": "floor_granted", "channel_id": channel_id}))
    await broadcast(pin, floor_state_payload(channel_id, user_id, preempted, "preempted" if preempted else ""))


async def handle_floor_release(user_id, data):
    """Nhả lượt nói (chỉ người đang giữ mới nhả được)."""
    pin = client_profiles.get(user_id, {}).get("event_pin", "")
    await release_floor((pin, str(data.get("channel_id") or "")), user_id)


async def release_floor(key, user_id, reason="released"):
    current = floors.get(key)
    if current is None or current["user_id"] != user_id:
        return
    del floors[key]
    await broadcast(key[0], floor_state_payload(key[1], None, reason=reason))


async def handle_floor_keepalive(user_id, data):
    """Người giữ lượt báo "vẫn đang nói" (app gửi mỗi 2 giây khi đang giữ PTT)."""
    pin = client_profiles.get(user_id, {}).get("event_pin", "")
    current = floors.get((pin, str(data.get("channel_id") or "")))
    if current is not None and current["user_id"] == user_id:
        current["last_seen"] = asyncio.get_running_loop().time()


async def floor_watchdog():
    """Mỗi 0,5 giây thu hồi lượt nói bị kẹt (giống RunWatchdogLoop bên Go):
    - giữ quá FLOOR_MAX_SECONDS (kẹt phím trong túi...) → reason "timeout";
    - không báo "vẫn đang nói" quá FLOOR_STALE_SECONDS (mất sóng) → reason "stale"."""
    while True:
        await asyncio.sleep(0.5)
        now = asyncio.get_running_loop().time()
        for key, current in list(floors.items()):
            reason = None
            if now - current["granted_at"] >= FLOOR_MAX_SECONDS:
                reason = "timeout"
            elif current["last_seen"] is not None and now - current["last_seen"] >= FLOOR_STALE_SECONDS:
                reason = "stale"
            if reason:
                print(f"[{current['user_id']}] tự thu hồi lượt nói trên {key[1]} ({reason}).")
                try:
                    await release_floor(key, current["user_id"], reason=reason)
                except Exception as e:  # Không để một lỗi làm chết vòng giám sát
                    print(f"Lỗi thu hồi lượt nói {key}: {e!r}")


async def release_floors_of(user_id, keep_pin=None, keep_channels=()):
    """Thu hồi mọi lượt nói của một người (ngắt kết nối, hoặc đổi sự kiện/vai trò).
    Giữ lại lượt trên các kênh người đó vẫn thuộc về (VD kết nối lại cùng vai trò)."""
    for key, current in list(floors.items()):
        if current["user_id"] != user_id:
            continue
        if key[0] == keep_pin and key[1] in keep_channels:
            continue
        await release_floor(key, user_id, reason="left")


async def relay_ptt(websocket, user_id, data):
    """Gói "ptt": báo bắt đầu/ngừng nói trên một kênh. Gửi cho cả sự kiện; app mỗi máy tự lọc
    theo kênh và luật vai trò (chỉ hiện/trả lời người được phép đàm thoại với mình)."""
    my_pin = client_profiles.get(user_id, {}).get("event_pin", "")
    await broadcast(my_pin, json.dumps({
        "type": "ptt",
        "sender_id": user_id,  # Server tự gắn người gửi: thiết bị không giả mạo được
        "channel_id": str(data.get("channel_id") or ""),
        "talking": bool(data.get("talking")),
    }), exclude=websocket)


async def handle_kick(websocket, user_id, data):
    """Gói "kick": CHỈ chủ sự kiện được xoá người khác khỏi sự kiện. Máy bị xoá nhận "kicked",
    rời sự kiện và bị chặn vào lại (bằng PIN hay QR) cho tới khi sự kiện kết thúc."""
    sender = client_profiles.get(user_id, {})
    pin = sender.get("event_pin", "")
    target_id = str(data.get("target_id") or "")
    target = client_profiles.get(target_id)

    def deny(reason):
        print(f"[{user_id}] bị từ chối xoá [{target_id}]: {reason}.")
        return safe_send(websocket, json.dumps({
            "type": "set_seat_denied", "target_id": target_id, "reason": reason,
        }))

    if not sender.get("is_organizer") or not pin:
        return await deny("not_organizer")
    if target_id == user_id or not target_id:
        return await deny("not_in_event")
    if target is not None and target.get("is_organizer"):
        return await deny("target_is_organizer")

    # Chặn cả khi người đó đang offline (vào lại sẽ bị đẩy ra ngay)
    banned.setdefault(pin, set()).add(target_id)
    pending_seats.pop((pin, target_id), None)
    save_state()
    print(f"[{user_id}] xoá [{target_id}] khỏi sự kiện {pin}.")
    if target is not None and target.get("event_pin") == pin:
        target["event_pin"] = ""
        target_ws = connected_clients.get(target_id)
        if target_ws is not None:
            await safe_send(target_ws, kicked_payload(pin, sender.get("display_name")))
        await release_floors_of(target_id)
    await notify_user_list(pin)


async def handle_set_seat(websocket, user_id, data):
    """Gói "set_seat": CHỈ chủ sự kiện được đổi vai trò/phòng ban của người khác trong sự kiện.
    Server cập nhật hồ sơ, phát danh sách mới và báo máy đó (máy đó tự chuyển kênh)."""
    sender = client_profiles.get(user_id, {})
    pin = sender.get("event_pin", "")
    target_id = str(data.get("target_id") or "")
    target = client_profiles.get(target_id)
    role = str(data.get("team_role") or "")

    def deny(reason):
        print(f"[{user_id}] bị từ chối đổi vai trò của [{target_id}]: {reason}.")
        return safe_send(websocket, json.dumps({
            "type": "set_seat_denied", "target_id": target_id, "reason": reason,
        }))

    if not sender.get("is_organizer") or not pin:
        return await deny("not_organizer")
    if not target or target.get("event_pin") != pin or target_id == user_id:
        return await deny("not_in_event")
    if target.get("is_organizer"):
        return await deny("target_is_organizer")
    if role not in ROLE_PRIORITY:
        return await deny("bad_role")

    if role == "ban":
        seat = {"team_role": "ban", "channel_id": COMMAND_CHANNEL, "channel_name": "Ban chỉ huy"}
    else:
        seat = {
            "team_role": role,
            "channel_id": str(data.get("channel_id") or "ch_general").strip(),
            "channel_name": str(data.get("channel_name") or "").strip(),
        }
    pending_seats[(pin, target_id)] = seat
    save_state()
    target.update(seat)
    print(f"[{user_id}] đổi vai trò của [{target_id}] thành {seat}.")
    # Lượt nói trên kênh người đó không còn thuộc về thì nhả ra
    await release_floors_of(target_id, keep_pin=pin, keep_channels=channels_of(target))
    target_ws = connected_clients.get(target_id)
    if target_ws is not None:
        await safe_send(target_ws, seat_assigned_payload(pin, seat, sender.get("display_name")))
    await notify_user_list(pin)


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
            elif msg_type == "ptt" and user_id:
                await relay_ptt(websocket, user_id, data)
            elif msg_type == "floor_request" and user_id:
                await handle_floor_request(websocket, user_id, data)
            elif msg_type == "floor_release" and user_id:
                await handle_floor_release(user_id, data)
            elif msg_type == "floor_keepalive" and user_id:
                await handle_floor_keepalive(user_id, data)
            elif msg_type == "set_seat" and user_id:
                await handle_set_seat(websocket, user_id, data)
            elif msg_type == "kick" and user_id:
                await handle_kick(websocket, user_id, data)
    except websockets.exceptions.ConnectionClosed:
        print(f"[{user_id}] đã ngắt kết nối đột ngột.")
    finally:
        # Chỉ gỡ nếu ID vẫn trỏ tới đúng socket này (không xóa nhầm phiên kết nối lại mới hơn)
        if user_id is not None and connected_clients.get(user_id) is websocket:
            del connected_clients[user_id]
            profile = client_profiles.pop(user_id, {})
            await notify_user_list(profile.get("event_pin", ""))
            await release_floors_of(user_id)  # Mất kết nối: nhả lượt nói cho người khác


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
    load_state()
    try:
        server = await websockets.serve(
            signaling_handler, "0.0.0.0", PORT,
            process_request=health_check,
            ping_interval=20, ping_timeout=20,  # Dọn kết nối chết (máy mất sóng không kịp báo đóng)
            # Thiết bị đóng kết nối: chỉ chờ 1 giây để TCP đóng hẳn (mặc định 10 giây). Qua proxy
            # của Render, TCP phía server không bao giờ đóng sớm nên luôn phải chờ hết thời gian này
            # → người đang giữ lượt rời đi thì 10 giây sau mới trả lượt cho người khác.
            close_timeout=1,
        )
    except OSError as e:
        print(f"KHÔNG mở được cổng {PORT}: {e}")
        print("Có thể một server khác đang chạy. Tắt cửa sổ server cũ rồi chạy lại.")
        sys.exit(1)

    # Vòng giám sát lượt nói (hết 60 giây / mất sóng); giữ tham chiếu để task không bị dọn
    watchdog = asyncio.ensure_future(floor_watchdog())

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
