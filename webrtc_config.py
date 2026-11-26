"""
TV1 - Tuần 1: Cấu hình WebRTC cơ bản & máy chủ STUN/TURN miễn phí (vượt NAT/tường lửa).

signaling_server.py gọi get_ice_servers_config() mỗi khi một thiết bị "register" và gửi kết quả
về app dưới dạng gói {"type": "webrtc_config", "config": {...}}.

Thứ tự ưu tiên nguồn TURN:
  1. Metered.ca (gói miễn phí 50GB/tháng) nếu đặt biến môi trường:
        METERED_APP_NAME   ví dụ: "ung-dung-dam-thoai"  (=> ung-dung-dam-thoai.metered.live)
        METERED_API_KEY    API key lấy trong dashboard Metered
     -> lấy credential TURN tạm thời qua REST API (an toàn hơn để lộ mật khẩu cố định).
  2. TURN tự dựng (coturn) qua biến môi trường TURN_URLS / TURN_USERNAME / TURN_CREDENTIAL.
  3. Tài khoản TURN Metered của nhóm (global.relay.metered.ca): đặt TURN_USERNAME / TURN_CREDENTIAL
     (không đặt TURN_URLS). Tài khoản KHÔNG ghi trong code vì repo công khai.
  4. Dự phòng: OpenRelay Project (TURN công cộng miễn phí). Tự bật khi không có nguồn TURN nào ở trên,
     hoặc thêm vào cùng nguồn khác khi đặt TURN_FALLBACK_OPENRELAY=1.

Biến môi trường đặt trong file .env (chép từ .env.example) hoặc trên máy chủ deploy.

Chạy thử: python webrtc_config.py
"""

import json
import os
import time
import urllib.request

# STUN miễn phí: đủ cho phần lớn NAT "dễ" (full-cone, restricted-cone)
FREE_STUN_SERVERS = [
    {"urls": ["stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"]},
    {"urls": "stun:stun.cloudflare.com:3478"},
]

def _load_dotenv(path=None):
    """Đọc file .env (KEY=VALUE) cạnh file này vào os.environ, không ghi đè biến đã có."""
    path = path or os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
    if not os.path.isfile(path):
        return
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            if key and key not in os.environ:
                os.environ[key] = value.strip().strip('"').strip("'")


_load_dotenv()


def _team_metered_turn():
    """Tài khoản TURN Metered của nhóm (cấu hình gốc Tuần 1), đọc từ biến môi trường."""
    username = os.environ.get("TURN_USERNAME", "").strip()
    credential = os.environ.get("TURN_CREDENTIAL", "").strip()
    if not username or not credential:
        return None
    return [
        {"urls": "stun:stun.relay.metered.ca:80"},
        {
            "urls": [
                "turn:global.relay.metered.ca:80",
                "turn:global.relay.metered.ca:80?transport=tcp",
                "turn:global.relay.metered.ca:443",
                "turns:global.relay.metered.ca:443?transport=tcp",
            ],
            "username": username,
            "credential": credential,
        },
    ]

# TURN công cộng miễn phí (OpenRelay) - dùng khi 4G/NAT đối xứng chặn kết nối P2P trực tiếp.
# Cổng 443/TCP + TLS giúp vượt cả tường lửa chỉ mở HTTPS ở hội trường/khách sạn.
OPEN_RELAY_TURN = {
    "urls": [
        "turn:openrelay.metered.ca:80",
        "turn:openrelay.metered.ca:443",
        "turn:openrelay.metered.ca:443?transport=tcp",
        "turns:openrelay.metered.ca:443?transport=tcp",
    ],
    "username": "openrelayproject",
    "credential": "openrelayproject",
}

_CACHE_TTL_SECONDS = 10 * 60
_cache = {"expires_at": 0.0, "servers": None}


def _fetch_metered_turn():
    app_name = os.environ.get("METERED_APP_NAME", "").strip()
    api_key = os.environ.get("METERED_API_KEY", "").strip()
    if not app_name or not api_key:
        return None
    url = f"https://{app_name}.metered.live/api/v1/turn/credentials?apiKey={api_key}"
    try:
        with urllib.request.urlopen(url, timeout=4) as resp:
            servers = json.loads(resp.read().decode("utf-8"))
        if isinstance(servers, list) and servers:
            return servers
    except Exception as exc:  # mạng lỗi -> rơi xuống nguồn dự phòng
        print(f"[webrtc_config] Không lấy được TURN từ Metered: {exc}")
    return None


def _custom_turn_from_env():
    urls = os.environ.get("TURN_URLS", "").strip()
    if not urls:
        return None
    return [{
        "urls": [u.strip() for u in urls.split(",") if u.strip()],
        "username": os.environ.get("TURN_USERNAME", ""),
        "credential": os.environ.get("TURN_CREDENTIAL", ""),
    }]


def get_ice_servers_list():
    now = time.time()
    if _cache["servers"] is not None and now < _cache["expires_at"]:
        return _cache["servers"]

    turn = _fetch_metered_turn() or _custom_turn_from_env() or _team_metered_turn()
    if not turn:
        print("[webrtc_config] Chưa cấu hình TURN riêng (xem .env.example): dùng TURN công cộng OpenRelay.")
        turn = [OPEN_RELAY_TURN]
    elif os.environ.get("TURN_FALLBACK_OPENRELAY") == "1":
        turn = turn + [OPEN_RELAY_TURN]
    servers = FREE_STUN_SERVERS + turn
    _cache["servers"] = servers
    _cache["expires_at"] = now + _CACHE_TTL_SECONDS
    return servers


def get_ice_servers_config():
    """Trả về chuỗi JSON dạng {"iceServers": [...]} (giữ nguyên giao kèo với signaling_server.py)."""
    return json.dumps({"iceServers": get_ice_servers_list()})


if __name__ == "__main__":
    print(json.dumps(json.loads(get_ice_servers_config()), indent=2))
