# TUẦN 3 — NGƯỜI 5: VIẾT LOGIC API SINH MÃ QR ĐỊNH DANH PHÂN VAI TỰ ĐỘNG CHO TỪNG BỘ PHẬN (HẬU CẦN, KỸ THUẬT, AN NINH, SÂN KHẤU, CHỈ HUY)

---

## 1. Đối chiếu Kiến trúc Dự án & Phân tích Nghiệp vụ (`1_BA`)

### 1.1. Bài toán Nghiệp vụ Thực tế tại Hiện trường Sự kiện (Tại sao cần QR Phân vai Tự động?)
Theo `1_BA/03_Yeu_cau_chuc_nang.md` (`FR-02`, `FR-03`) và `1_BA/08_Quy_tac_Nghiep_vu_va_Kich_ban.md` (`BR-01`..`BR-04`):
- Trước giờ diễn 15 phút, có hàng trăm nhân sự thuộc 5 bộ phận khác nhau (**Ban Chỉ huy, Sân khấu, Kỹ thuật Âm thanh - Ánh sáng, An ninh - Y tế, Hậu cần**) cùng tập trung tại bàn điểm danh.
- Nếu chỉ dùng 1 mã QR chung, từng tình nguyện viên sau khi quét xong lại phải tự tìm kênh và dễ **chọn nhầm sang kênh Chỉ huy hoặc tự nâng quyền ưu tiên của mình lên mức Đạo diễn (`Priority 3`)**.
- Do đó, ở **Tuần 3**, **Người 5** xây dựng **API Sinh Mã QR Định danh Phân vai Tự động (`qr_role_provisioning.go`)**:
  - Ban Tổ Chức chỉ cần gọi 1 API duy nhất để sinh ra **5 tấm thẻ QR riêng biệt cho 5 bộ phận**.
  - Khi nhân sự bộ phận **An ninh** quét tấm thẻ QR dán ở bàn An ninh, Server tự động giải mã chữ ký số `HMAC-SHA256`, gán cứng:
    - Bộ phận: `SECURITY` (`AN NINH - Y TẾ`)
    - Cấp độ quyền ưu tiên: `RolePriority = 2`
    - Kênh phát sóng chính mặc định: `Channel 4 (AN NINH - Y TẾ)`
    - Các kênh nghe tự động: `Channel 1 (CHỈ HUY)` + `Channel 4 (AN NINH - Y TẾ)`
    - Giao diện mặc định: `EASY_SINGLE_BUTTON` (hoặc `PRO_MULTI_CHANNEL` đối với Kỹ thuật/Đạo diễn).

### 1.2. Bảng Phân Vai Tự Động 5 Bộ Phận trong Mã QR của Người 5

| Mã Bộ phận (`deptCode`) | Tên Bộ phận Hiển thị | Quyền Ưu tiên (`rolePriority`) | Kênh Phát Chính (`primaryChannel`) | Kênh Nghe Mặc định (`listenChannels`) | Chế độ Giao diện Mặc định |
|---|---|---|---|---|---|
| `DIRECTOR` | ★ Ban Chỉ Huy / Tổng Đạo Diễn | **Priority 3** (Cao nhất — Chiếm mic) | Kênh 1 (`CHỈ HUY`) | `[1, 2, 3]` | `PRO_MULTI_CHANNEL` (`S06`/`S12`) |
| `STAGE` | 🎭 Điều phối Sân Khấu | **Priority 2** (Tổ trưởng) | Kênh 2 (`SÂN KHẤU`) | `[1, 2]` | `PRO_MULTI_CHANNEL` (`S06`) |
| `TECH_AV` | 🔊 Kỹ thuật Âm Thanh & Ánh Sáng | **Priority 2** (Tổ trưởng) | Kênh 3 (`ÂM THANH & ÁNH SÁNG`) | `[1, 2, 3]` | `PRO_MULTI_CHANNEL` (`S06`) |
| `SECURITY` | 🛡️ An Ninh & Y Tế | **Priority 2** (Trưởng ca trực) | Kênh 4 (`AN NINH - Y TẾ`) | `[1, 4]` | `EASY_SINGLE_BUTTON` (`S07`) |
| `LOGISTICS` | 📦 Tình nguyện viên Hậu Cần | **Priority 1** (Cộng tác viên) | Kênh 5 (`HẬU CẦN`) | `[1, 5]` | `EASY_SINGLE_BUTTON` (`S07`) |

---

## 2. Giải thích Chi tiết Mã nguồn [`qr_role_provisioning.go`](backend-tv5/Tuan_3_API_Sinh_QR_Phan_Vai_Tu_Dong_Role_Provisioning/qr_role_provisioning.go)

1. **Cấu trúc `DepartmentQrTokenPayload`:**
   - Đóng gói đầy đủ thông tin định danh bộ phận: `EventID`, `DepartmentCode`, `DepartmentTitle`, `AssignedPriority`, `PrimaryChannelID`, `AutoListenChannels`, `DefaultUiMode`, và `ExpiresAtUnix`.
2. **Hàm `signQrPayload(payload DepartmentQrTokenPayload, masterSecret []byte)`:**
   - Chuyển `payload` thành JSON, mã hóa `Base64URL`, sau đó tính chữ ký số `HMAC-SHA256(base64Payload, masterSecret)` và ghép thành định dạng URI chuẩn:
     `damthoai://join?token=<base64Payload>.<hmacSignatureHex>`
   - Nhờ chữ ký `HMAC-SHA256`, không một người dùng nào có thể tự sửa nội dung QR để nâng khống quyền từ `LOGISTICS (Priority 1)` lên `DIRECTOR (Priority 3)`.
3. **Hàm `HandleGenerateDepartmentQrCodes` (`GET /api/v1/events/qr-roles`):**
   - Sinh đồng loạt bộ 5 mã QR định danh cho 5 bộ phận của sự kiện chỉ trong 1 lần gọi API.
4. **Hàm `HandleJoinByDepartmentQr` (`POST /api/v1/auth/join-by-qr`):**
   - Nhận chuỗi QR người dùng vừa quét từ Camera, kiểm tra hạn sử dụng (`ExpiresAtUnix`) và chữ ký `HMAC-SHA256`.
   - Tự động khởi tạo phiên người dùng với đúng quyền ưu tiên, đúng kênh bộ phận và trả về cấu hình hoàn chỉnh để App Android vào thẳng màn hình bộ đàm mà không cần hỏi lại vai trò.
