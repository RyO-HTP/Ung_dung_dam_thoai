# Checklist test thực địa TV4 — phần cứng và audio

## 1. Mục tiêu

Ghi nhận độ trễ của phím thay PTT, hoạt động của tai nghe/nút Bluetooth, thiết bị audio bị ngắt, và các điều kiện thực tế ngoài trời hoặc hội trường. Logger chỉ hoạt động trong bản debug; bản release không tạo CSV.

## 2. Cách dùng logger

1. Cài và chạy **bản debug** của ứng dụng trên điện thoại thử nghiệm.
2. Mở màn hình Demo Phím Cứng PTT. Thẻ **Test thực địa** chỉ xuất hiện trong bản debug.
3. Bấm **Bắt đầu ghi log** trước mỗi lượt thử. CSV được ghi vào thư mục app-specific `fieldtest`, không cần quyền lưu trữ.
4. Bấm **Dừng ghi log** để xả buffer và flush file trước khi lấy log. Nếu app bị đóng đột ngột, dữ liệu chưa flush có thể thiếu tối đa khoảng 2 giây.
5. Lấy thư mục CSV bằng ADB:

   ```text
   adb pull /sdcard/Android/data/com.example.damthoai/files/fieldtest ./fieldtest
   ```

   Trên một số máy, cần lấy đúng đường dẫn hiển thị trong thẻ Test thực địa; quyền truy cập thư mục Android/data từ trình quản lý file có thể bị giới hạn.
6. Tên file có dạng `fieldtest_yyyyMMdd_HHmmss_SSS.csv`. Các cột:

   `timestampWallMs,elapsedRealtimeMs,eventType,source,deviceName,extra`

   `key_input_to_app` ghi độ trễ từ `KeyEvent.eventTime` tới khi Volume Key Handler nhận sự kiện; `app_to_ptt_event` ghi độ trễ tới lúc PttController phát `PttPressed`/`PttReleased`. TV2 có thể gọi `mark("mic_open", ...)` và `mark("mic_closed", ...)` tại thời điểm mic thật sự mở/đóng.

   Các lần nhấn đếm trên thẻ là các sự kiện `PttPressed` đã phát, không phải toàn bộ số lần chạm/nút vật lý. Bộ đếm phím trùng và phím bị bỏ được ghi riêng.
   Logger có API `recordMediaKeyInput(KeyEvent, source)` cho callback phím media; callback hiện có của `HeadsetButtonManager` chưa được nối vì file này nằm ngoài phạm vi chỉnh sửa của lượt test này. Vì vậy, thời gian media key tới app cần được nối ở điểm nhận `KeyEvent` trước khi dùng làm số đo chính thức.

## 3. Danh sách thiết bị

Điền model, phiên bản OS và cấu hình tiết kiệm pin thực tế trước khi chạy. Dùng ít nhất ba hãng và ít nhất hai phiên bản Android.

| Hãng | Đời máy / model | Android | Ghi chú pin / tự khởi chạy |
|---|---|---|---|
| Samsung |  |  |  |
| Xiaomi |  |  |  |
| OPPO |  |  |  |
| Pixel |  |  |  |

## 4. Danh sách phụ kiện

| Phụ kiện | Hãng / model | Firmware / ghi chú |
|---|---|---|
| Tai nghe có dây một nút |  |  |
| Bluetooth nhét tai |  |  |
| Bluetooth chụp tai |  |  |
| Nút Bluetooth PTT rời |  |  |

## 5. Ma trận kịch bản

| Kịch bản | Kỳ vọng | Đạt/Không đạt/Ghi chú |
|---|---|---|
| Màn hình sáng | Nhấn/thả PTT đúng nguồn; không có nhấn lặp ngoài dự kiến; ghi log đủ. |  |
| Màn hình tắt | Kiểm tra phím PTT/tai nghe theo khả năng hệ điều hành; không tự phát âm thanh ra loa ngoài khi đổi tuyến. |  |
| Bỏ túi | Kiểm tra phím không bị kích hoạt ngoài ý muốn; PTT được thả sau khi nhả nút. |  |
| Ngoài trời nắng, có gió | Nút vẫn nhận ổn định; kiểm tra khả năng đọc trạng thái và cảnh báo rung/flash. |  |
| Hội trường đông thiết bị Bluetooth | Ghép đúng tai nghe/nút PTT; kiểm tra trễ, nhiễu và ngắt kết nối. |  |
| Rút tai nghe giữa chừng | Đầu ra bị mute hoặc chuyển tuyến an toàn; không tự mở loa ngoài; PTT được thả nếu đang nhấn. |  |
| Bluetooth ngắt giữa chừng | Ghi sự cố Bluetooth; không tự unmute hoặc chuyển âm thanh ra loa ngoài. |  |
| Có cuộc gọi điện thoại đến | Mất audio focus được ghi nhận; PTT được thả và đầu ra mute; lấy lại focus không tự unmute. |  |
| Bật tiết kiệm pin | Theo dõi việc hệ điều hành hạn chế app/thiết bị; ghi lại khác biệt theo hãng. |  |
| Xiaomi/OPPO chưa bật “Tự khởi chạy” | Ghi nhận app có bị dừng hoặc mất sự kiện khi chạy nền; không kết luận từ một lần thử. |  |
| Cảnh báo khẩn cấp rung và nháy đèn dưới nắng | Xác nhận rung/flash hoạt động và trạng thái/lý do khả dụng được cập nhật. |  |
| Giữ Volume Down khi tắt màn hình (cần Trợ năng) | Nếu đã bật Trợ năng, kiểm tra nhận/phát/thả PTT; nếu chưa bật, ghi rõ giới hạn. |  |

## 6. Bảng đo độ trễ

Mỗi phép đo lặp **ít nhất 20 lần cho từng tổ hợp máy/phụ kiện**. Tính thống kê từ các mẫu hợp lệ trong CSV hoặc video; không gộp các tổ hợp thiết bị khác nhau.

| Phép đo | Máy / phụ kiện | Số mẫu (>=20) | Min (ms) | Trung vị (ms) | p95 (ms) | Max (ms) | Đạt/Không đạt/Ghi chú |
|---|---|---:|---:|---:|---:|---:|---|
| Phím tới app (`key_input_to_app`) |  |  |  |  |  |  |  |
| App tới PttEvent (`app_to_ptt_event`) |  |  |  |  |  |  |  |
| Ước lượng đầu ra Bluetooth (`bt_output_latency_est_ms`) |  |  |  |  |  |  |  |

### Đo tay bằng video chậm 240 fps

1. Đặt điện thoại quay sao cho thấy được thao tác nhấn PTT và có thể đồng bộ mốc âm thanh (ví dụ tiếng click/tick nghe được).
2. Quay ở 240 fps, lặp ít nhất 20 lần và xem từng cặp mốc: thao tác phím tới phản hồi app; hoặc lúc phát tick tới lúc nghe thấy âm thanh ở tai nghe.
3. Đếm số frame giữa hai mốc. Công thức: `độ trễ (ms) = số frame / 240 × 1000`, tương đương khoảng `4,17 ms/frame`.
4. Ghi rõ cách xác định mốc bắt đầu/kết thúc và sai số quan sát. Nếu có thể, dùng cùng video để so sánh các máy/phụ kiện.

`AudioTimestamp` là **ước lượng phần mềm** từ timestamp frame của `AudioTrack` và thời điểm bắt đầu phát. Đường truyền Bluetooth có thể thêm buffer/codec/firmware mà AudioTimestamp không quan sát đầy đủ. Con số này không phải phép đo âm học và **không thay thế phép đo bằng quay video chậm**.

## 7. Ngưỡng chấp nhận gợi ý

- Phím tới PttEvent: dưới 100 ms.
- Ước lượng trễ Bluetooth: dưới 300 ms.

**Ngưỡng gợi ý, cả nhóm thống nhất lại.** Ghi rõ thiết bị, chế độ codec, phiên bản Android, cách đo và điều kiện môi trường; không dùng riêng ngưỡng này để kết luận Bluetooth đạt/chưa đạt nếu chưa đối chiếu đo tay.

## 8. Mẫu báo cáo lỗi

- **Mã lỗi / ngày giờ:**
- **Bước tái hiện:**
  1.
  2.
  3.
- **Kết quả mong đợi:**
- **Kết quả thực tế:**
- **Hãng / model máy:**
- **Phiên bản Android:**
- **Phụ kiện / model / firmware:**
- **Trạng thái màn hình, pin, tự khởi chạy và Trợ năng:**
- **Số lần thử / tần suất gặp:**
- **CSV đính kèm:**
- **Video 240 fps đính kèm (nếu có):**
- **Ghi chú môi trường (ngoài trời/hội trường, số thiết bị Bluetooth lân cận):**
