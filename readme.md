# Đàm Thoại

Ứng dụng bộ đàm trên điện thoại Android cho đội sự kiện: bấm giữ để nói (PTT), chia kênh theo phòng ban, âm thanh đi bằng WebRTC. Đây là bài làm của nhóm 5 thành viên (TV1–TV5) theo kế hoạch 6 tuần. Chi tiết từng tuần nằm trong các file `TVx Báo cáo Tuần y.md`.

## Cấu trúc thư mục

| Thư mục / file | Nội dung | Phụ trách |
|---|---|---|
| `app/` | Ứng dụng Android (Kotlin, Jetpack Compose, WebRTC) | Cả nhóm |
| `signaling_server.py` | Máy chủ Signaling (Python, WebSocket cổng 8765) mà app kết nối | TV1, TV2 |
| `webrtc_config.py` | Danh sách STUN/TURN máy chủ gửi cho app | TV1 |
| `mdns_broadcaster.py` | Quảng bá máy chủ trong mạng LAN qua mDNS (`_bodam._tcp`) | TV1 |
| `tools/` | Đo tải máy chủ (`load_test_signaling.py`), giả lập mất gói (`simulate_packet_loss.sh`) | TV1 |
| `api.js` | API cấp token LiveKit (Node, cổng 3000) | TV2 |
| `server.js` | Bản thử máy chủ signaling bằng Node (cổng 8080) | TV2 |
| `backend-tv5/` | Backend Go: API sự kiện/PIN/kênh, sinh QR phân vai, giành lượt nói (Floor Control), Live Cue | TV5 |

## Tài khoản và khoá bí mật

Repo này công khai, nên **không ghi tài khoản hay khoá vào code**. Chép `.env.example` thành `.env` rồi điền giá trị thật. File `.env` đã nằm trong `.gitignore`, không bị commit.

| Biến | Dùng cho |
|---|---|
| `SIGNALING_URL`, `SIGNALING_TOKEN` | Địa chỉ WebSocket (`wss://...`) và mã truy cập máy chủ Signaling; `backend-tv5` trả kèm khi app gọi `join-pin` / `join-by-qr` |
| `TURN_USERNAME`, `TURN_CREDENTIAL` | Tài khoản TURN Metered của nhóm (`webrtc_config.py`) |
| `METERED_APP_NAME`, `METERED_API_KEY` | (Tuỳ chọn) lấy TURN tạm thời qua API Metered |
| `TURN_URLS` | (Tuỳ chọn) TURN tự dựng, ví dụ coturn |
| `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | `api.js` |

Chưa cấu hình TURN thì máy chủ dùng TURN công cộng OpenRelay. Cách này vẫn gọi được, nhưng chậm và kém ổn định hơn.

## 1. Chạy app Android

Cần **Android Studio** (đã kèm JDK 17) và điện thoại Android 7.0 trở lên đã bật *Gỡ lỗi USB*.

1. Kiểm tra `BACKEND_BASE_URL` trong `gradle.properties` (mặc định trỏ tới dịch vụ cloud `https://damthoai-backend.onrender.com`; khi chạy thử nội bộ có thể đổi sang `http://<IP-máy-tính>:8080`).
2. Android Studio → **File › Open** → chọn thư mục repo, chờ Gradle sync xong.
3. Cắm điện thoại, chọn cấu hình **app**, bấm **Run ▶**.

Hoặc build bằng dòng lệnh:

```bash
./gradlew :app:assembleDebug        # Windows: gradlew.bat :app:assembleDebug
./gradlew :app:testDebugUnitTest    # chạy unit test
```

File APK nằm ở `app/build/outputs/apk/debug/`.

## 2. Chạy máy chủ Signaling (Python)

```bash
pip install -r requirements.txt
python signaling_server.py
```

- Máy chủ mở cổng **8765** (đổi bằng biến môi trường `PORT`).
- Để app trên cùng Wi-Fi tự tìm thấy máy chủ, mở thêm một cửa sổ và chạy `python mdns_broadcaster.py`.
- Trên Windows, cho phép Python qua **Windows Firewall** (mạng Private).
- Thử độ chịu tải: `python tools/load_test_signaling.py --clients 200 --messages 30`
- Kiểm tra tự động giao thức máy chủ: `python -m unittest discover -s tests -v`

### Bảo mật máy chủ

- **Mã truy cập:** đặt `SIGNALING_TOKEN` trong `.env`. App phải nhập đúng mã mới kết nối được và mới nhận được tài khoản TURN. Để trống thì ai biết địa chỉ cũng vào được, chỉ nên dùng khi thử trong mạng nhà.
- **Giữ tên:** mỗi máy tự sinh một mã riêng (`device_key`). Tên (`user_id`) đang được máy khác dùng, hoặc vừa rớt mạng chưa quá 10 phút, thì máy lạ không đăng ký đè được.
- Bị từ chối thì app báo lỗi và **không** tự kết nối lại: sai mã truy cập (4001), trùng tên (4009), tên không hợp lệ (4000).

### Đưa máy chủ lên cloud (Render, miễn phí)

1. Đăng nhập https://render.com, chọn **New → Blueprint**, chọn repo này (`render.yaml` khai báo sẵn 2 dịch vụ: `damthoai-signaling` và `damthoai-backend`).
2. Với `damthoai-signaling`: điền `TURN_USERNAME` / `TURN_CREDENTIAL`; xem `SIGNALING_TOKEN` được tự sinh ở tab **Environment**.
3. Với `damthoai-backend`: điền `SIGNALING_URL` (`wss://<tên-signaling>.onrender.com`) và `SIGNALING_TOKEN` giống bên `damthoai-signaling`.
4. Kiểm tra cả hai dịch vụ bằng đường dẫn `/healthz` (trả về `OK`), rồi cập nhật `BACKEND_BASE_URL=https://<tên-backend>.onrender.com` trong `gradle.properties`.
5. Gói miễn phí sẽ ngủ sau 15 phút không dùng, lần kết nối đầu sau đó mất khoảng 1 phút.

### Gọi thử thật giữa 2 điện thoại

1. Chạy máy chủ như trên, xem IP của máy tính (`ipconfig`).
2. Cài app lên 2 điện thoại cùng Wi-Fi với máy tính (hoặc dùng 4G nếu đã đưa lên Render).
3. Vào sự kiện bằng mã PIN (`240826` / `889900`) hoặc quét QR — app nhận `userId`, `signalingUrl`, `signalingToken` từ backend Go và tự kết nối máy chủ Signaling. Cũng có thể thử trực tiếp tại **Cài đặt → Kiểm tra mạng**.
4. Mỗi máy sẽ thấy máy kia trong mục "Đang online". Bấm **Gọi**, rồi giữ nút tròn để nói.
5. Mục "Chất lượng" cho biết đi thẳng hay qua TURN, độ trễ, tỉ lệ mất gói. Thử tắt Wi-Fi một máy để xem cuộc gọi tự nối lại qua 4G.

## 3. Backend Go (TV5) và API Floor Control

```bash
cd backend-tv5
go build ./...
go run .               # HTTP :8080 (hoặc biến PORT trên cloud), UDP relay :50000
```

- Kiểm tra sống: `GET /healthz` → `OK`.
- Lấy thẻ QR phân vai 5 bộ phận: `GET /api/v1/events/qr-roles?eventId=1`.
- Vào sự kiện: `POST /api/v1/auth/join-pin` và `POST /api/v1/auth/join-by-qr` trả về `event`, `session`, kèm `userId` (duy nhất, chỉ gồm chữ, số và `. _ -`), `signalingUrl`, `signalingToken`.

### Cách gọi Floor Control (`request`, `release`, `emergency`)

App gọi 3 REST API dưới đây (JSON, `Content-Type: application/json`) để xin/nhả lượt nói và cắt lời theo cấp bậc (`userLevel`: `1` Tổng đạo diễn, `2` Trưởng ban, `3` Cộng tác viên):

1. **Xin quyền nói (khi nhấn giữ PTT):** `POST /api/v1/floor/request`
   ```json
   {
     "channelId": 2,
     "channelCode": "ch_02",
     "userId": "Minh_Hau_101_4f8a",
     "displayName": "Minh Hậu",
     "roleTitle": "Trưởng ban Sân khấu",
     "userLevel": 2
   }
   ```
   - Phản hồi trả về `status` và trạng thái UI tương ứng:
     - `"FLOOR_GRANTED"` (`requesterCallState: "CallTalk"`): kênh rảnh, được bật mic phát âm thanh.
     - `"FLOOR_PREEMPTED"` (`requesterCallState: "CallTalk"`, `revokedCallState: "CallCut"`, `revokedUserId`, `isDucked80: true`, `duckingDb: -12`): cấp cao hơn cắt ngang thành công; máy bị cắt chuyển sang `CallCut`.
     - `"FLOOR_DENIED"` (`requesterCallState: "CallBusy"`, `activeHolder`): kênh đang bận bởi người cùng hoặc cao cấp hơn.
   - Server tự thu hồi lượt nói sau tối đa **60 giây** giữ liên tục để chống kẹt phím cứng trong túi quần.

2. **Nhả quyền nói (khi thả nút PTT):** `POST /api/v1/floor/release`
   ```json
   {
     "channelId": 2,
     "userId": "Minh_Hau_101_4f8a"
   }
   ```
   - Phản hồi: `{"released": true, "channelId": 2, "userId": "Minh_Hau_101_4f8a"}` — giải phóng kênh về `CallIdle`.

3. **Phát khẩn cấp toàn sự kiện (chỉ Cấp 1 – Tổng đạo diễn):** `POST /api/v1/floor/emergency-override`
   ```json
   {
     "userId": "Dao_dien_100_9c1b",
     "displayName": "Tổng đạo diễn"
   }
   ```
   - Phản hồi: `{"status": "EMERGENCY_OVERRIDE_ACTIVE", "decisions": [...]}` — cưỡng bức chiếm quyền nói trên cả 5 kênh (`ch_01`..`ch_05`), đặt `revokedCallState: "CallCut"` cho người đang nói và bật hạ âm `isDucked80: true` (`-12 dB`).

## 4. Các phần khác

**API cấp token LiveKit (TV2):**

```bash
npm install
npm run token          # = node --env-file=.env api.js, cần Node 20.6 trở lên
# mở http://localhost:3000/get-token
```

## Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| Gradle báo lỗi phiên bản Java | Android Studio › Settings › Build Tools › Gradle › **Gradle JDK** chọn JDK 17 |
| App không tìm thấy máy chủ | Kiểm tra `BACKEND_BASE_URL` hoặc cùng Wi-Fi và tường lửa Windows |
| Vào được nhưng khác mạng không nghe tiếng | Kiểm tra `TURN_USERNAME` / `TURN_CREDENTIAL` và `SIGNALING_URL` / `SIGNALING_TOKEN` trên máy chủ |
| `api.js` báo thiếu `LIVEKIT_...` | Chưa tạo `.env`, hoặc chưa chạy bằng `npm run token` |
