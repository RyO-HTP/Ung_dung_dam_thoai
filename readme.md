# Đàm Thoại

Ứng dụng bộ đàm trên điện thoại cho đội sự kiện: mọi người vào cùng một sự kiện, chia kênh, bấm giữ để nói (PTT) như bộ đàm, không cần thuê bộ đàm.

- **Android**: Kotlin + Compose Multiplatform, âm thanh đi bằng WebRTC (thẳng giữa các máy, hoặc qua máy chủ TURN khi cần).
- **Máy chủ Signaling**: `signaling_server.py` (Python, WebSocket, cổng 8765). Máy chủ chỉ giúp các máy tìm nhau và giành lượt nói, **âm thanh không đi qua máy chủ**.
- **iOS** (đang làm): xem [`iosApp/README.md`](iosApp/README.md).

> Bản đang phát triển nằm ở nhánh **`backup`**. Hãy clone và làm việc trên nhánh này.

## Cấu trúc thư mục

| Thư mục / file | Nội dung |
|---|---|
| `app/` | Ứng dụng Android (Activity, service chạy nền, WebRTC, phím cứng PTT, quyền) |
| `shared/` | Giao diện và logic dùng chung Android/iOS (màn hình, kết nối máy chủ, giành lượt nói) |
| `iosApp/` | Phần Swift của bản iOS |
| `signaling_server.py` | Máy chủ Signaling **mà app đang dùng** |
| `webrtc_config.py` | Danh sách máy chủ STUN/TURN gửi cho app |
| `render.yaml` | Cấu hình deploy máy chủ lên Render |
| `mdns_broadcaster.py` | Chỉ phát mDNS (thường không cần, server đã tự phát) |
| `backend-tv5/` | Backend Go theo từng tuần (tài liệu học tập, app hiện **không** dùng) |
| `server.js`, `api.js` | Thử nghiệm LiveKit cũ (app hiện **không** dùng) |
| `docs/` | Tài liệu test thực địa |
| `TVx Báo cáo Tuần y.md` | Báo cáo tuần của từng thành viên |

## 1. Chạy app Android

### Cần chuẩn bị

- **Android Studio** bản mới (hỗ trợ Android Gradle Plugin 8.13). Android Studio đã kèm sẵn JDK 17.
- Điện thoại Android **7.0 (API 24) trở lên**, bật *Tùy chọn nhà phát triển* → *Gỡ lỗi USB*.
  Nên dùng máy thật: máy ảo không có micro/loa tốt và khó vào cùng mạng Wi-Fi.

### Mở và chạy bằng Android Studio

1. Clone repo và chuyển sang nhánh `backup`:
   ```bash
   git clone https://github.com/RyO-HTP/Ung_dung_dam_thoai.git
   cd Ung_dung_dam_thoai
   git checkout backup
   ```
2. Android Studio → **File › Open** → chọn thư mục `Ung_dung_dam_thoai`. Chờ Gradle sync xong (lần đầu tải thư viện khá lâu).
3. Cắm điện thoại, chọn cấu hình **app**, bấm **Run ▶**.
4. Lần đầu mở app, cấp quyền **Micro**, **Thông báo**, **Camera** (quét QR) khi được hỏi.

### Build APK bằng dòng lệnh

```bash
# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

File APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`. Gửi file này cho các máy khác để cài (cần cho phép *Cài ứng dụng không rõ nguồn gốc*).

Chạy unit test:

```bash
./gradlew :app:testDebugUnitTest
```

### App kết nối máy chủ nào?

App tự tìm máy chủ theo thứ tự:

1. Địa chỉ bạn tự nhập trong mục **Địa chỉ Server nội bộ (WebSocket)** (nếu có).
2. Máy chủ đám mây khai báo trong `gradle.properties`:
   ```properties
   damthoai.cloudSignalingUrl=wss://ung-dung-dam-thoai.onrender.com
   ```
   Dùng được cả Wi-Fi lẫn 4G. Để trống dòng này nếu chỉ muốn dùng máy chủ trong mạng LAN.
3. Máy chủ trong cùng mạng Wi-Fi, tìm bằng mDNS hoặc quét cổng 8765.

## 2. Chạy máy chủ Signaling

Có hai cách. Chọn **một** trong hai.

### Cách A: chạy trên máy tính trong cùng mạng Wi-Fi (khuyên dùng khi test và tại sự kiện không có Internet ổn định)

Cần **Python 3.10+**.

```bash
pip install -r requirements.txt
python signaling_server.py
```

Server in ra địa chỉ, ví dụ:

```
Signaling Server đang chạy trên cổng 8765
Địa chỉ cho điện thoại (cùng Wi-Fi): ws://192.168.1.8:8765
```

- Điện thoại và máy tính phải **cùng một mạng Wi-Fi**. App thường tự tìm thấy server (mDNS). Nếu không thấy, nhập địa chỉ in ở trên vào mục **Địa chỉ Server nội bộ**.
- Trên Windows, lần đầu chạy hãy cho phép Python qua **Windows Firewall** (mạng Private), nếu không điện thoại sẽ không kết nối được cổng 8765.
- Wi-Fi công cộng / Wi-Fi khách thường chặn các máy nói chuyện với nhau. Khi đó dùng điểm phát Wi-Fi riêng (router mang theo hoặc phát từ điện thoại).
- Dữ liệu sự kiện chỉ nằm trong bộ nhớ: tắt server là mất.

Kiểm tra server còn sống: mở `http://<IP>:8765/healthz` trên trình duyệt, sẽ thấy `OK <số người> online`.

### Cách B: deploy lên Render (dùng được qua 4G)

Repo đã có sẵn `render.yaml` (gói miễn phí, vùng Singapore, deploy từ nhánh `backup`).

1. Đăng nhập [Render](https://render.com) → **New › Blueprint** → chọn repo này.
2. Render đọc `render.yaml` và tạo dịch vụ `damthoai-signaling`. Mỗi lần push lên nhánh `backup`, Render tự deploy lại.
3. Lấy địa chỉ dịch vụ (dạng `https://<tên>.onrender.com`), đổi thành `wss://<tên>.onrender.com` và điền vào `damthoai.cloudSignalingUrl` trong `gradle.properties`, rồi build lại app.

Lưu ý: gói miễn phí của Render **ngủ sau khoảng 15 phút không có ai dùng**, lần kết nối đầu tiên sau đó có thể chờ tới gần 1 phút. App đã tự chờ và tự gửi tín hiệu giữ máy chủ thức khi đang dùng.

### Máy chủ TURN

Khi hai máy không nói chuyện trực tiếp được (4G, mạng khác nhau), âm thanh đi qua máy chủ TURN cấu hình trong `webrtc_config.py` (hiện dùng Metered). Nếu đổi tài khoản TURN, sửa file này rồi khởi động lại server.

## 3. Thử nhanh với 2 điện thoại

1. Bật máy chủ (Cách A hoặc B).
2. Máy 1: tạo sự kiện mới, đặt tên kênh. App hiện **mã PIN** và **mã QR**.
3. Máy 2: quét QR hoặc nhập PIN để vào cùng sự kiện, chọn cùng kênh.
4. Bấm giữ nút nói trên một máy, máy kia sẽ nghe thấy. Mỗi kênh chỉ một người nói một lúc.

## Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| Gradle báo lỗi phiên bản Java | Android Studio › Settings › Build Tools › Gradle › **Gradle JDK** chọn JDK 17 trở lên |
| App báo không tìm thấy máy chủ | Kiểm tra cùng Wi-Fi, tường lửa Windows, hoặc nhập thẳng `ws://<IP>:8765` |
| Vào được sự kiện nhưng không nghe tiếng | Kiểm tra quyền Micro; nếu khác mạng thì kiểm tra tài khoản TURN trong `webrtc_config.py` |
| Lần đầu kết nối đám mây rất lâu | Render đang ngủ, chờ khoảng 1 phút |
