BÀN GIAO HẠ TẦNG MẠNG (SFU) - TUẦN 2

Người phụ trách: Thành viên 1

TIẾN ĐỘ THỰC HIỆN

Đã hoàn thành 100% nhiệm vụ Tuần 2 về thiết lập luồng truyền âm thanh qua máy chủ trung tâm:

[x] Cấu hình SFU: Khởi tạo thành công cụm máy chủ LiveKit Cloud phục vụ định tuyến âm thanh nhiều kênh cùng lúc.

[x] API Cấp Token: Xây dựng thành công hệ thống sinh mã JWT (Vé kết nối) bằng Node.js và LiveKit Server SDK.

[x] Phân quyền (Grants): Thiết lập thành công logic cấp phép cho người dùng (identity) tham gia vào phòng thoại chỉ định (roomJoin).

CÁCH CHẠY SERVER (Dành cho Localhost)

Mở Terminal trong thư mục dự án và chạy tuần tự:

npm install (Chỉ cần chạy lần đầu để tải express và livekit-server-sdk)

node api.js (Bật máy chủ cấp Token)

THÔNG SỐ TÍCH HỢP (Gửi Thành viên 2)

Đây là thông số để gắn vào app Android (Kotlin):

A. Gọi API lấy Token
Gửi request (GET) thẳng tới: http://[IP_MÁY_CHỦ]:3000/get-token

B. Bóc tách và Kết nối SFU
Server sẽ trả về một cục JSON ngay lập tức. Bạn lấy serverUrl và token này nạp vào hàm LiveKit.connect() của SDK trên Android:

// Server trả về:
{
"serverUrl": "wss://ung-dung-dam-thoai-pze2v7u9.livekit.cloud",
"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ2aWRlbyI6eyJyb29t... (mã rất dài)"
}

LƯU Ý (Gửi Thành viên 5)

Hiện tại trong file api.js, mình đang code cứng (hardcode) các thông số:

roomName = 'Kenh_Hau_Can'

participantName = 'Nguoi_Dung_Test_01'

Nhiệm vụ của bạn: Ráp đoạn code sinh Token (new AccessToken...) của mình vào hệ thống API chính thức. Thay thế các biến cứng này bằng dữ liệu thật truy vấn từ Database (User ID, Role, Channel) mà bạn thiết kế. Đồng thời, nhớ đưa 3 biến livekitHost, apiKey, và apiSecret vào file .env để bảo mật