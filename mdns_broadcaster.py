import socket
import time
from zeroconf import ServiceInfo, Zeroconf

def get_local_ip():
    # Mở một kết nối socket tạm để lấy địa chỉ IP LAN thực tế của máy bạn
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.255.255.255', 1))
        IP = s.getsockname()[0]
    except Exception:
        IP = '127.0.0.1'
    finally:
        s.close()
    return IP

def broadcast_service():
    ip_address = get_local_ip()
    # Định nghĩa cấu trúc dịch vụ mDNS (tên dịch vụ: _bodam._tcp.local.)
    info = ServiceInfo(
        "_bodam._tcp.local.",
        "BoDamServer._bodam._tcp.local.",
        addresses=[socket.inet_aton(ip_address)],
        port=8765,
        properties={'desc': 'Signaling Server cho Ung_dung_dam_thoai'},
        server="bodam.local."
    )

    zeroconf = Zeroconf()
    print(f"Đang phát sóng mDNS... Các thiết bị Android sẽ tự động kết nối tới IP: {ip_address}:8765")
    zeroconf.register_service(info)

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        print("Đang tắt mDNS...")
        zeroconf.unregister_service(info)
        zeroconf.close()

if __name__ == "__main__":
    broadcast_service()