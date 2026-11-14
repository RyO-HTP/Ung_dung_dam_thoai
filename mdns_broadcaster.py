"""
Chỉ phát mDNS (không chạy signaling server).

THƯỜNG KHÔNG CẦN chạy file này: signaling_server.py đã tự phát mDNS khi khởi động.
Chỉ dùng khi server chạy ở nơi khác nhưng muốn máy này quảng bá địa chỉ cho app trong mạng Wi-Fi.

Dùng lại đúng hàm của signaling_server.py để hai nơi không lệch nhau
(VD: bản sửa lỗi phát nhầm IP card ảo VMware chỉ cần sửa một chỗ).
"""
import asyncio

from signaling_server import PORT, force_utf8_output, get_lan_ip, start_mdns


async def main():
    ip = get_lan_ip()
    azc, info = await start_mdns(ip, PORT)
    if azc is None:
        return
    print(f"Các thiết bị Android sẽ tự kết nối tới: ws://{ip}:{PORT}  (Ctrl+C để dừng)")
    try:
        await asyncio.Event().wait()  # Chạy mãi tới khi bấm Ctrl+C
    finally:
        print("Đang tắt mDNS...")
        await azc.async_unregister_service(info)
        await azc.async_close()


if __name__ == "__main__":
    force_utf8_output()
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
