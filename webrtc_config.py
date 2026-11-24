"""
Cấu hình STUN/TURN gửi cho điện thoại.

Tài khoản TURN KHÔNG ghi trong code (repo public). Đặt qua biến môi trường:
  TURN_USERNAME, TURN_CREDENTIAL   tài khoản TURN (lấy trên dashboard Metered)
  TURN_HOST                        mặc định global.relay.metered.ca
  STUN_URL                         mặc định stun:stun.relay.metered.ca:80

Chạy trên máy tính: chép .env.example thành .env rồi điền giá trị.
Chạy trên Render: điền các biến này trong mục Environment của service.
Thiếu tài khoản TURN thì server chỉ gửi STUN: gọi trong cùng mạng vẫn được,
nhưng hai máy ở hai mạng khác nhau (4G / Wi-Fi khác) có thể không nối được.
"""
import json
import os


def load_dotenv(path=None):
    """Đọc file .env (dạng KEY=VALUE) vào os.environ, không ghi đè biến đã có.
    Viết tay để không phải cài thêm thư viện."""
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
            value = value.strip().strip('"').strip("'")
            if key and key not in os.environ:
                os.environ[key] = value


load_dotenv()


def get_ice_servers_config():
    stun_url = os.environ.get("STUN_URL", "stun:stun.relay.metered.ca:80")
    turn_host = os.environ.get("TURN_HOST", "global.relay.metered.ca")
    username = os.environ.get("TURN_USERNAME", "").strip()
    credential = os.environ.get("TURN_CREDENTIAL", "").strip()

    ice_servers = [{"urls": stun_url}]
    if username and credential:
        for url in (
            f"turn:{turn_host}:80",
            f"turn:{turn_host}:80?transport=tcp",
            f"turn:{turn_host}:443",
            f"turns:{turn_host}:443?transport=tcp",
        ):
            ice_servers.append({"urls": url, "username": username, "credential": credential})
    else:
        print("[webrtc_config] Chưa đặt TURN_USERNAME / TURN_CREDENTIAL: chỉ dùng STUN, "
              "gọi khác mạng có thể không nối được.")

    return json.dumps({"iceServers": ice_servers})


if __name__ == "__main__":
    print("Cấu hình ICE Servers ban đầu:")
    print(get_ice_servers_config())
