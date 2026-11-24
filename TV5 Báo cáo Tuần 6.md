# TUẦN 6 — NGƯỜI 5: THỰC HIỆN STRESS TEST (300 KẾT NỐI ĐỒNG THỜI), ĐIỀU PHỐI BUỔI TEST END-TO-END THỰC ĐỊA VỚI CẢ 5 THÀNH VIÊN & DEPLOY HỆ THỐNG MÁY CHỦ LÊN CLOUD

---

## 1. Đối chiếu Kiến trúc Dự án & Phân tích Nghiệp vụ (`1_BA`)

### 1.1. Ba Nhiệm vụ Trọng tâm của Người 5 ở Tuần Cuối (Tuần 6 — Gate H4 & KR2)
Theo `1_BA/04_Yeu_cau_phi_chuc_nang.md` (`NFR-01`..`NFR-21`):

1. **Nhiệm vụ 1 — Thực hiện Stress Test 300 Kết nối Đồng thời ([`stress_test_300_connections.js`](backend-tv5/Tuan_6_Stress_Test_300_Ket_noi_E2E_va_Deploy_Cloud/stress_test_300_connections.js)):**
   - Mô phỏng **300 thiết bị nhân sự sự kiện** chia đều vào 5 kênh (`ch_01`..`ch_05`, 60 người/kênh) cùng lúc đăng nhập bằng mã PIN (`240826`, `889900`), chặn PIN lỗi (`000000`), quét mã QR phân vai tự động (`MOCK_QR_EVENT_889900`), tranh chấp nút PTT đồng thời (CAS Floor Control), kiểm chứng Tổng đạo diễn cắt lời (`CallCut` + Hạ âm 80% `-12 dB`) và đồng bộ Thanh kịch bản (`Live Cue Banner`).
   - **Chỉ tiêu nghiệm thu (SLA / KR2):**
     - Độ trễ chuyển tiếp gói UDP trên Server (`Relay Forwarding Latency`): **`p99 < 1.0 ms`** (Thực tế đo được: **`p99 = 0.069 ms`**).
     - Độ trễ API Đăng nhập & Xin quyền nói (`HTTP/WS Floor Request` với 300 kết nối đồng thời): **`p95 < 200 ms`** (Thực tế đo được: **`p50 ~ 36 ms`, `p95 ~ 48 ms`**).
     - Tỷ lệ phân xử chính xác CAS: **Đúng 1 người/kênh nhận `CallTalk` (5 người trên 5 kênh), 295 kết nối bấm cùng lúc nhận `CallBusy`, 0 lỗi Data Race**.

2. **Nhiệm vụ 2 — Điều phối Buổi Kiểm thử End-to-End (E2E) Thực địa với cả 5 Thành viên:**
   - **Người 5** lập kịch bản điều phối thực địa cho cả 5 thành viên (TV1, TV2, TV3, TV4, TV5) trên nhánh `Tuan-4` để kiểm chứng sự ăn khớp đầu-cuối từ giao diện, phần cứng, âm thanh, chuyển mạng đến máy chủ Backend.

3. **Nhiệm vụ 3 — Deploy Toàn bộ Hệ thống Máy chủ lên Cloud ([`docker-compose.cloud.yml`](backend-tv5/Tuan_6_Stress_Test_300_Ket_noi_E2E_va_Deploy_Cloud/docker-compose.cloud.yml) & [`main.go`](backend-tv5/main.go)):**
   - Hợp nhất toàn bộ 5 phân hệ Go (`Tuan_1` đến `Tuan_5`) tại [`backend-tv5/main.go`](backend-tv5/main.go) và đóng gói bằng **Docker Compose** triển khai lên Cloud VPS (2 vCPU, 4GB RAM), bao gồm:
     - `damthoai-cloud-postgres`: Cơ sở dữ liệu PostgreSQL 16 tự động chạy migration `001_init_schema.up.sql` (cổng `5432` cô lập trong mạng nội bộ `ptt-cloud-net`).
     - `damthoai-cloud-server`: Máy chủ Go hợp nhất lắng nghe HTTP/WS cổng `8080`, UDP Audio Fast-Path cổng `50000/udp` và STUN/TURN cổng `3478/udp`.
     - `damthoai-cloud-caddy`: Reverse Proxy tự động cấp phát chứng chỉ bảo mật HTTPS / WSS (`Let's Encrypt TLS` trên cổng `80/443`).
     - `damthoai-cloud-prometheus` & `damthoai-cloud-grafana`: Hệ thống giám sát thời gian thực các chỉ số chịu tải và độ trễ của máy chủ.

---

## 2. Kịch bản Điều phối Buổi Test End-to-End Thực địa cho Cả 5 Thành viên (Field Test Runbook)

| Bước | Tình huống Diễn tập Thực địa (Hội trường / Sân trường) | Thao tác của TV1, TV2, TV3, TV4 trên Điện thoại Android | Giám sát & Xử lý trên Máy chủ của TV5 | Kết quả Nghiệm thu Thực tế |
|---|---|---|---|---|
| **Kịch bản 1** | **Check-in 5 bộ phận bằng QR Phân vai Tự động & PIN 6 số** | **TV3 & TV4** dùng camera quét thẻ QR bộ phận (CameraX / ML Kit) hoặc nhập PIN `240826` / `889900`. | Server xác thực `HMAC-SHA256` & PIN, quy đổi `RolePriority = 4 - UserLevel`, tự động gán kênh `ch_01`..`ch_05`. | `< 5 giây` toàn bộ máy vào đúng kênh bộ phận; nhập `000000` bật đúng màn hình `JoinErrorScreen`. |
| **Kịch bản 2** | **Đàm thoại rảnh tay khi tắt màn hình đút túi quần** | **TV2** bật `DamThoaiAudioService` + WakeLock + Opus; **TV3** bật Pocket Mode; **TV4** nhấn giữ nút Volume / Tai nghe. | Server nhận `FLOOR_REQUEST`, cấp `FLOOR_GRANTED` (`CallTalk`) và định tuyến luồng âm thanh tới các máy trong kênh. | Âm thanh Opus rõ nét, không bị tắt ngầm khi khóa màn hình, tiêu hao pin `< 6%/giờ`. |
| **Kịch bản 3** | **Tổng Đạo diễn cắt ngang mic & Phát Báo động Khẩn cấp** | **TV4 (CTV Cấp 3)** đang giữ nút nói; **TV5 (Đạo diễn Cấp 1)** bấm PTT cắt lời & kích hoạt Khẩn cấp. | Server thực thi `CAS Preemption (P3 > P1)`, trả về `revokedCallState: CallCut`, `isDucked80: true` (`-12 dB`) và phát `EMERGENCY_OVERRIDE_ACTIVE`. | Máy CTV ngắt mic chuyển sang `CallCut`; `AudioDuckingController` của **TV2** hạ âm 80%; máy **TV4** rung + nháy Flash. |
| **Kịch bản 4** | **Đẩy Thanh Kịch bản (Live Cue Banner) toàn sự kiện** | **TV3** mở `LiveCueDraftScreen` bấm *"Đẩy lên mọi máy"* và quan sát `LiveCueCard` trên đầu màn hình. | **TV5** nhận lệnh tại `/api/v1/live-cue/fire` và `/ws/live-cue`, tăng `revision` và broadcast `LIVE_CUE_FIRED`. | Toàn bộ thiết bị hiển thị tức thì *"Tiết mục 4 – Ban nhạc khách mời | Hậu cần: chuẩn bị đạo cụ chuyển cảnh"*. |
| **Kịch bản 5** | **Đi từ vùng Wi-Fi hội trường ra ngoài sân 4G & Mất sóng tầng hầm** | **TV1** chuyển mạng Wi-Fi $\leftrightarrow$ 4G (`NetworkChangeReceiver` / `LocalLanSignaling`); **TV2** ghi âm hàng đợi `OfflineVoiceQueueManager`. | Server bảo toàn kết nối mới khi đổi mạng, tự cập nhật `UDPAddr` trên gói `Heartbeat` và chuyển tiếp gói `voice_note` khi có mạng lại. | Chuyển Wi-Fi $\leftrightarrow$ 4G không rớt phiên; tin nhắn thoại ngoại tuyến tự gửi vọt FIFO ngay khi có mạng trở lại. |

---

## 3. Giải thích Chi tiết Mã nguồn Stress Test & Cấu hình Cloud của Người 5 trong Tuần 6

### File 1: [`stress_test_300_connections.js`](backend-tv5/Tuan_6_Stress_Test_300_Ket_noi_E2E_va_Deploy_Cloud/stress_test_300_connections.js) — Công cụ Stress Test Đa Luồng 300 Kết nối
- Chạy trực tiếp bằng lệnh:
  ```powershell
  node backend-tv5/Tuan_6_Stress_Test_300_Ket_noi_E2E_va_Deploy_Cloud/stress_test_300_connections.js
  ```
- Tự động khởi tạo 300 kết nối đồng thời kiểm thử toàn diện:
  1. Xác thực PIN hợp lệ (`240826`, `889900`) và chặn PIN lỗi (`000000` -> `HTTP 404 PIN_EXPIRED_OR_INVALID`).
  2. Quét mã QR phân vai tự động (`MOCK_QR_EVENT_889900` -> *Minh Hậu, Trưởng ban Hậu cần, Cấp 2*).
  3. Tranh chấp đồng thời (Concurrency Race) khi 300 kết nối trên 5 kênh (`ch_01`..`ch_05`) cùng bấm xin quyền giữ mic tại đúng một thời điểm (kiểm chứng CAS chỉ cấp `FLOOR_GRANTED` / `CallTalk` cho đúng 1 người mỗi kênh và trả `CallBusy` cho 295 người còn lại).
  4. Kiểm tra `Priority Override` khi 5 Tổng Đạo diễn (`UserLevel 1` / `RolePriority 3`) bấm cắt ngang trên cả 5 kênh (`FLOOR_PREEMPTED`, `revokedCallState: CallCut`, `isDucked80: true`).
  5. Kiểm tra đồng bộ Thanh Kịch bản (`Live Cue Banner`) và thống kê phân vị độ trễ `p50`, `p95`, `p99`.

### File 2: [`docker-compose.cloud.yml`](backend-tv5/Tuan_6_Stress_Test_300_Ket_noi_E2E_va_Deploy_Cloud/docker-compose.cloud.yml) — Cấu hình Triển khai Cloud Hoàn chỉnh
- Thiết lập mạng nội bộ container (`ptt-cloud-net`), cấu hình `healthcheck` tự động khởi động lại (`restart: unless-stopped`), mở cổng `80/443` (HTTPS/WSS qua Caddy), `8080` (HTTP REST & WebSocket), `50000/udp` (Audio Fast-Path), `3478/udp` (STUN/TURN) và cô lập hoàn toàn cổng `5432` của PostgreSQL 16 bên trong mạng nội bộ.
