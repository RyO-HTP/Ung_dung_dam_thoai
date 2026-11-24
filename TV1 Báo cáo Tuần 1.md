BÀN GIAO HẠ TẦNG MẠNG - TUẦN 1

Người phụ trách: Thành viên 1

1. TIẾN ĐỘ THỰC HIỆN

Đã hoàn thành 100% nhiệm vụ Tuần 1 và vượt tiến độ một số tính năng Tuần 5:

[x] Signaling Server: Định tuyến tin nhắn qua WebSocket.

[x] Cấp STUN/TURN: Tự động trả về cấu hình vượt tường lửa 4G (dùng Metered).

[x] mDNS & Hàng đợi: Phát IP nội bộ mạng LAN và lưu tin nhắn nhỡ khi rớt mạng.

2. CÁCH CHẠY SERVER (Dành cho Localhost)

Mở Terminal và chạy tuần tự:

pip install -r requirements.txt (Chỉ cần chạy lần đầu)

python signaling_server.py (Bật máy chủ trung tâm)

python mdns_broadcaster.py (Bật phát sóng IP cho app Android tự tìm)

3. THÔNG SỐ TÍCH HỢP (Gửi Thành viên 2)

Đây là các luồng dữ liệu để gắn vào Kotlin/Coroutines:

A. URL Kết Nối

Connect WebSocket thẳng tới: ws://[IP_MÁY_CHỦ]:8765

(Sau này app quét mDNS sẽ tự động bắt được cái IP này).

B. Đăng nhập (Handshake)
Ngay khi connect thành công, gửi cục này lên server:

{
    "type": "register",
    "user_id": "Tong_Dao_Dien"
}


C. Lấy cấu hình WebRTC
Server sẽ trả về một cục JSON ngay lập tức. Bạn lấy phần tử iceServers bên trong nó và nạp vào hàm khởi tạo PeerConnection:

// Server trả về:
{
    "type": "webrtc_config",
    "config": {
        "iceServers": [ /* ... Mảng chứa STUN và TURN thật ... */ ]
    }
}


D. Định tuyến P2P (Gửi SDP/ICE)
Khi 2 máy cần gọi nhau, bạn bọc thông số vào form này, server sẽ tự đưa tới đúng người:

{
    "type": "offer", // hoặc "answer", "ice_candidate"
    "target_id": "ID_Nguoi_Nhan",
    "sdp": "..."
}


4. LƯU Ý (Gửi Thành viên 5)

Hiện tại trường user_id đang tạm fix bằng chữ để test. Tuần 2 khi bạn thiết kế xong Database, mình sẽ ngồi lại để thống nhất cách nối channel_id và role_id