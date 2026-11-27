# Tài liệu API Bổ sung — Đàm Thoại Backend (Render / Go Server)

Tài liệu này tổng hợp toàn bộ các API HTTP RESTful và kênh WebSocket được triển khai trên máy chủ Backend Go (`damthoai-backend`), bao gồm các luồng Quản trị sự kiện, Khôi phục quyền Cấp 1, Phân vai PIN/QR, Quản lý kênh, Quản lý thành viên, Tranh chấp quyền nói (Floor Control CAS) và Đồng bộ thanh kịch bản (Live Cue Banner).

---

## 1. Quy chuẩn Phân vai & Cấp độ Người dùng

Hệ thống quy đổi tương đương giữa Cấp độ hiển thị trên ứng dụng Android (`UserLevel`) và Độ ưu tiên xử lý tranh chấp mic trên Server (`RolePriority`):

| Vai trò | Android UI (`userLevel`) | Server CAS (`rolePriority`) | Quyền hạn nổi bật |
| :--- | :---: | :---: | :--- |
| **Tổng Đạo diễn / Chỉ huy** | `1` | `3` | Tạo/Xóa sự kiện, Khôi phục Cấp 1, Xóa/Sửa kênh & thành viên, Cắt mic khẩn cấp, Bắn Live Cue |
| **Trưởng ban (Sân khấu, Kỹ thuật...)** | `2` | `2` | Sinh mã QR phân vai ban, phát biểu ưu tiên hơn CTV |
| **Cộng tác viên (CTV)** | `3` | `1` | Nhận vai qua PIN/QR, đàm thoại PTT chuẩn, xem thanh kịch bản |

---

## 2. Chi tiết Các Endpoints API

### 2.1. Quản lý Sự kiện & Khôi phục (Admin)

#### 1. Tạo sự kiện mới
- **Endpoint**: `POST /api/v1/events`
- **Request Body**:
```json
{
  "eventName": "Đêm nhạc Chào Tân sinh viên 2026",
  "description": "Sự kiện điều phối bộ đàm đa kênh thời gian thực",
  "pinCode": "240826",
  "adminCode": "admin-secret-999",
  "displayName": "Tổng Đạo Diễn Phương",
  "roleTitle": "Tổng đạo diễn"
}
```
- **Response (201 Created)**:
```json
{
  "eventId": 4,
  "eventName": "Đêm nhạc Chào Tân sinh viên 2026",
  "description": "Sự kiện điều phối bộ đàm đa kênh thời gian thực",
  "pinCode": "240826",
  "createdAt": "2026-03-30T10:00:00Z",
  "adminCode": "admin-secret-999",
  "channelList": [
    { "id": "ch_01", "channelId": 1, "name": "Toàn sự kiện", "departmentCode": "ALL_EVENT", "defaultPriority": 3, "onlineCount": 0, "statusText": "" },
    { "id": "ch_02", "channelId": 2, "name": "Sân khấu", "departmentCode": "STAGE", "defaultPriority": 2, "onlineCount": 0, "statusText": "" }
  ],
  "session": {
    "numericUserId": 101,
    "userId": "Tong_Dao_Dien_Phuong_101_a1b2",
    "userLevel": 1,
    "rolePriority": 3,
    "primaryTxChannel": 1,
    "listeningChannels": [1, 2]
  }
}
```
- **cURL Example**:
```bash
curl -X POST https://damthoai-backend.onrender.com/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{"eventName":"Demo","adminCode":"admin-secret-999","displayName":"Đạo diễn"}'
```

#### 2. Khôi phục quyền Cấp 1 qua Admin Code
- **Endpoint**: `POST /api/v1/events/restore`
- **Request Body**:
```json
{
  "adminCode": "admin-secret-999",
  "displayName": "Tổng Đạo Diễn Mới",
  "roleTitle": "Tổng đạo diễn"
}
```
- **Response (200 OK)**:
```json
{
  "event": {
    "eventId": 4,
    "eventName": "Đêm nhạc Chào Tân sinh viên 2026",
    "pinCode": "240826"
  },
  "session": {
    "numericUserId": 105,
    "userId": "Tong_Dao_Dien_Moi_105_f3e4",
    "userLevel": 1,
    "rolePriority": 3
  }
}
```
- **Lưu ý**: Tự động hạ cấp tất cả các phiên Cấp 1 cũ của sự kiện này xuống Cấp 3 (`userLevel = 3`, `rolePriority = 1`, `roleTitle = "Cộng tác viên"`). Giới hạn 5 lần thử / phút / IP (trả về `429 RATE_LIMITED` khi vượt quá).

#### 3. Lấy chi tiết sự kiện
- **Endpoint**: `GET /api/v1/events/{eventId}?actorUserId={userId}`
- **Response (200 OK)**:
```json
{
  "eventId": 1,
  "eventName": "Đêm nhạc Chào Tân sinh viên",
  "memberCount": 12,
  "channelList": [
    { "id": "ch_01", "channelId": 1, "name": "Toàn sự kiện", "onlineCount": 5, "statusText": "" },
    { "id": "ch_02", "channelId": 2, "name": "Sân khấu", "onlineCount": 3, "statusText": "" }
  ]
}
```
- **Response Error (403 Forbidden)**: `{"error": "USER_NOT_IN_EVENT", "message": "User not in event or event mismatch"}`

---

### 2.2. Xác thực & Phân vai qua PIN / QR

#### 4. Tham gia sự kiện bằng mã PIN 6 số
- **Endpoint**: `POST /api/v1/auth/join-pin`
- **Request Body**:
```json
{
  "pinCode": "240826",
  "displayName": "Nguyễn Văn A",
  "roleTitle": "Cộng tác viên Đón tiếp",
  "preferredChannel": 2
}
```
- **Response (200 OK)**:
```json
{
  "userId": "Nguyen_Van_A_102_c3d4",
  "signalingUrl": "wss://damthoai-signaling.onrender.com",
  "signalingToken": "secret_token_123",
  "session": {
    "numericUserId": 102,
    "userId": "Nguyen_Van_A_102_c3d4",
    "userLevel": 3,
    "rolePriority": 1,
    "primaryTxChannel": 2,
    "listeningChannels": [1, 2]
  }
}
```
- **Lưu ý**: Client gửi `userLevel` hay `rolePriority` bất kỳ đều bị bỏ qua, phiên tạo ra luôn được gán cứng `userLevel = 3` và `rolePriority = 1`.

#### 5. Sinh danh mục mã QR phân vai theo ban
- **Endpoint**: `GET /api/v1/events/qr-roles?eventId={eventId}&actorUserId={actorUserId}`
- **Quyền hạn**: Chỉ người dùng Cấp 1 hoặc Cấp 2 trong sự kiện mới được phép gọi.
- **Response (200 OK)**:
```json
{
  "eventId": 1,
  "eventName": "Đêm nhạc Chào Tân sinh viên",
  "pinCode": "240826",
  "cards": [
    {
      "deptCode": "STAGE",
      "deptTitle": "Sân khấu",
      "roleTitle": "Trưởng ban Sân khấu",
      "userLevel": 2,
      "assignedPriority": 2,
      "primaryChannelId": 2,
      "primaryChannelCode": "ch_02",
      "autoListenChannels": [1, 2],
      "defaultUiMode": "PRO_MULTI_CHANNEL",
      "qrDeepLinkUri": "damthoai://join?token=eyJ...payload....sig",
      "signedToken": "eyJ...payload....sig",
      "expiresAt": 1774900000
    }
  ]
}
```

#### 6. Tham gia sự kiện bằng mã QR
- **Endpoint**: `POST /api/v1/auth/join-by-qr`
- **Request Body**:
```json
{
  "qrToken": "damthoai://join?token=eyJ...payload....sig",
  "displayName": "Lê Thị B"
}
```
- **Response (200 OK)**:
```json
{
  "status": "AUTO_PROVISIONED",
  "userId": "Le_Thi_B_103_e5f6",
  "userLevel": 2,
  "departmentCode": "STAGE",
  "session": {
    "userLevel": 2,
    "rolePriority": 2,
    "primaryTxChannel": 2,
    "listeningChannels": [1, 2]
  }
}
```

---

### 2.3. Quản lý Kênh (Channels)

#### 7. Thêm kênh mới
- **Endpoint**: `POST /api/v1/events/{eventId}/channels?actorUserId={actorUserId}`
- **Request Body**: `{"name": "Bảo vệ & Giữ xe", "departmentCode": "SECURITY", "defaultPriority": 2}`
- **Response (201 Created)**: `{"id": "ch_06", "channelId": 6, "name": "Bảo vệ & Giữ xe", "departmentCode": "SECURITY", "defaultPriority": 2, "onlineCount": 0, "statusText": ""}`
- **Lưu ý**: Tự động gán `channelId` số nguyên nhỏ nhất chưa sử dụng (1..10). Trả về `409 CHANNEL_NAME_TAKEN` nếu trùng tên (không phân biệt hoa/thường).

#### 8. Cập nhật kênh
- **Endpoint**: `PATCH /api/v1/events/{eventId}/channels/{channelId}?actorUserId={actorUserId}`
- **Request Body**: `{"name": "Truyền thông & Báo chí"}`
- **Response (200 OK)**: Thông tin kênh sau cập nhật.

#### 9. Xóa kênh
- **Endpoint**: `DELETE /api/v1/events/{eventId}/channels/{channelId}?actorUserId={actorUserId}`
- **Quyền hạn**: Chỉ dành cho Cấp 1 (`UserLevel 1`).
- **Response (200 OK)**: `{"status": "CHANNEL_DELETED", "channelId": 6}`
- **Lưu ý**: Cấm xóa Kênh 1 (`400 CANNOT_DELETE_DEFAULT_CHANNEL`). Khi kênh bị xóa, tất cả thành viên đang đặt kênh đó làm kênh phát chính sẽ được tự động chuyển về kênh 1 (`ch_01`).

#### 10. Chuyển kênh phát/nghe
- **Endpoint**: `POST /api/v1/users/switch-channel`
- **Request Body**: `{"userId": "Nguyen_Van_A_102_c3d4", "primaryTxChannel": 3, "listeningChannels": [1, 3]}`
- **Response (200 OK)**: Dữ liệu `UserSession` cập nhật.

#### 11. Rời kênh đang nghe
- **Endpoint**: `POST /api/v1/users/leave-channel`
- **Request Body**: `{"userId": "Nguyen_Van_A_102_c3d4", "channelId": 3}`
- **Response (200 OK)**: `{"status": "LEFT_CHANNEL", "userId": "...", "channelId": 3}`

---

### 2.4. Quản lý Thành viên (Members)

#### 12. Danh sách thành viên sự kiện
- **Endpoint**: `GET /api/v1/events/{eventId}/members?actorUserId={actorUserId}`
- **Response (200 OK)**:
```json
[
  {
    "numericUserId": 101,
    "userId": "Tong_Dao_Dien_101_a1b2",
    "displayName": "Lan Phương",
    "roleTitle": "Tổng đạo diễn",
    "userLevel": 1,
    "rolePriority": 3,
    "primaryTxChannel": 1,
    "online": true
  }
]
```
- **Bảo mật**: Tuyệt đối không trả về chuỗi hay mảng byte mã khóa bí mật (`hmacSecretHex` / `HmacSecretBytes`). Chỉ lọc thành viên đúng thuộc `eventId`.

#### 13. Cập nhật thành viên
- **Endpoint**: `PATCH /api/v1/events/{eventId}/members/{userId}?actorUserId={actorUserId}`
- **Request Body**: `{"userLevel": 2, "roleTitle": "Trưởng ban Hậu cần"}`
- **Response (200 OK)**: Dữ liệu thành viên `MemberDto` sau cập nhật.
- **Bảo vệ Admin duy nhất**: Nếu thành viên là Cấp 1 cuối cùng của sự kiện, hành động hạ cấp sẽ bị chặn với mã lỗi `409 LAST_ADMIN`.

#### 14. Xóa thành viên
- **Endpoint**: `DELETE /api/v1/events/{eventId}/members/{userId}?actorUserId={actorUserId}`
- **Response (200 OK)**: `{"status": "MEMBER_REMOVED", "userId": "..."}`
- **Bảo vệ Admin duy nhất**: Không cho phép xóa Cấp 1 cuối cùng (`409 LAST_ADMIN`).
- **Sau khi xóa**: Thành viên bị gỡ ngay khỏi hệ thống (non-blocking). Mọi yêu cầu API tiếp theo sử dụng `userId` này sẽ bị từ chối với lỗi `403 USER_NOT_IN_EVENT`.

---

### 2.5. Điều phối Quyền nói (Floor Control & Priority Override)

#### 15. Xin quyền phát sóng PTT
- **Endpoint**: `POST /api/v1/floor/request`
- **Request Body**: `{"channelId": 2, "userId": "User_102", "displayName": "Văn A"}`
- **Response (200 OK)**:
```json
{
  "status": "FLOOR_GRANTED",
  "channelId": 2,
  "channelCode": "ch_02",
  "requesterCallState": "CallTalk",
  "listenerCallState": "CallTake",
  "isDucked80": false,
  "activeHolder": {
    "channelId": 2,
    "userId": "User_102",
    "displayName": "Văn A",
    "userLevel": 3,
    "rolePriority": 1
  },
  "timestampMs": 1774900050000
}
```
- **Lưu ý**: Server tự động tra cứu cấp độ thực tế của `userId` từ phiên đăng ký (bỏ qua `userLevel` do client mạo danh). Cấp 1 (`RolePriority 3`) có quyền ngắt lời Cấp 2 và Cấp 3 (`StatusFloorPreempted`).

#### 16. Nhả quyền phát sóng PTT
- **Endpoint**: `POST /api/v1/floor/release`
- **Request Body**: `{"channelId": 2, "userId": "User_102"}`
- **Response (200 OK)**: `{"released": true, "channelId": 2, "userId": "User_102"}`

#### 17. Ngắt mic khẩn cấp toàn sự kiện
- **Endpoint**: `POST /api/v1/floor/emergency-override`
- **Quyền hạn**: Chỉ dành cho Cấp 1 (`UserLevel 1`). CTV/Trưởng ban gọi sẽ nhận `403 FORBIDDEN_NOT_ADMIN`.
- **Request Body**: `{"userId": "Tong_Dao_Dien_101", "displayName": "Lan Phương"}`
- **Response (200 OK)**:
```json
{
  "status": "EMERGENCY_OVERRIDE_ACTIVE",
  "decisions": [
    { "status": "FLOOR_PREEMPTED", "channelId": 1, "isDucked80": true, "duckingDb": -12 },
    { "status": "FLOOR_PREEMPTED", "channelId": 2, "isDucked80": true, "duckingDb": -12 }
  ]
}
```

#### 18. WebSocket Floor Control Broadcast
- **URL**: `wss://damthoai-backend.onrender.com/ws/floor?userId={userId}&eventId={eventId}`
- **Khởi tạo**: Ngay khi kết nối thành công, Server tự động đẩy gói snapshot trạng thái mic hiện tại:
```json
{
  "type": "FLOOR_SNAPSHOT",
  "eventId": 1,
  "holders": [ ... ],
  "serverTimeMs": 1774900000000
}
```
- **Sự kiện thay đổi**: Khi có bất kỳ thay đổi quyền nói nào trên các kênh, Server phát gói broadcast:
```json
{
  "type": "FLOOR_DECISION",
  "eventId": 1,
  "status": "FLOOR_GRANTED",
  "channelId": 2,
  "channelCode": "ch_02",
  "activeHolder": { ... }
}
```

---

### 2.6. Đồng bộ Thanh Kịch bản (Live Cue Banner)

#### 19. Bắn lệnh chuyển tiết mục kịch bản
- **Endpoint**: `POST /api/v1/live-cue/fire`
- **Quyền hạn**: Chỉ dành cho Cấp 1 (`UserLevel 1`).
- **Request Body**:
```json
{
  "userId": "Tong_Dao_Dien_101",
  "eventId": 1,
  "cueId": "C04",
  "title": "Tiết mục 4 – Ban nhạc khách mời",
  "message": "Hậu cần: chuẩn bị đạo cụ chuyển cảnh"
}
```
- **Response (200 OK)**: Dữ liệu snapshot `LiveCueBannerPayload` mới nhất.

#### 20. WebSocket Thanh Kịch bản
- **URL**: `wss://damthoai-backend.onrender.com/ws/live-cue?userId={userId}&eventId={eventId}&dept={dept}`
- **Khởi tạo**: Đẩy snapshot `LIVE_CUE_BANNER_SYNC` chứa tiết mục đang diễn (`activeCue`), tiết mục chuẩn bị (`nextCue`) và danh sách kịch bản (`allCues`).
- **Lệnh qua WS từ client Cấp 1**: Gửi JSON `{"action": "FIRE_CUE", "cueId": "C05"}` để chuyển tiết mục. Gửi `{"action": "ACK_CUE", "cueId": "C04"}` để xác nhận đã nhận lệnh từ ban chuyên môn.

---

## 3. Bảng Tổng hợp Mã Lỗi Chuẩn (Error Codes)

| HTTP Status | Mã Lỗi (`error`) | Mô tả chi tiết |
| :---: | :--- | :--- |
| `400` | `ADMIN_CODE_EMPTY` | Mã quản trị để trống khi khôi phục sự kiện |
| `400` | `ADMIN_CODE_INVALID` | Mã quản trị không hợp lệ (cần 6-32 ký tự alphanumeric/dash) |
| `400` | `CANNOT_DELETE_DEFAULT_CHANNEL` | Không được phép xóa Kênh 1 ("Toàn sự kiện") |
| `400` | `CHANNEL_NAME_REQUIRED` | Tên kênh không được để trống khi khởi tạo |
| `401` | `UNAUTHORIZED` | Truy cập bị từ chối (ví dụ: quét QR mô phỏng khi `DEMO_MODE=false`) |
| `403` | `USER_NOT_IN_EVENT` | Người dùng chưa tham gia hoặc không thuộc sự kiện chỉ định |
| `403` | `FORBIDDEN_NOT_ADMIN` | Thao tác yêu cầu quyền Tổng Đạo diễn (Cấp 1) |
| `403` | `INSUFFICIENT_PERMISSIONS` | Quyền hạn người dùng không đủ thực hiện hành động |
| `404` | `ADMIN_CODE_NOT_FOUND` | Mã quản trị không khớp với bất kỳ sự kiện nào |
| `404` | `EVENT_NOT_FOUND` | Không tìm thấy sự kiện với ID tương ứng |
| `404` | `CHANNEL_NOT_FOUND` | Không tìm thấy kênh chỉ định |
| `404` | `USER_NOT_FOUND` | Không tìm thấy phiên làm việc của người dùng |
| `409` | `ADMIN_CODE_TAKEN` | Mã quản trị đã được đăng ký bởi sự kiện khác |
| `409` | `CHANNEL_NAME_TAKEN` | Tên kênh đã bị trùng trong cùng một sự kiện |
| `409` | `LAST_ADMIN` | Không thể hạ cấp hoặc xóa Admin Cấp 1 duy nhất của sự kiện |
| `410` | `QR_REVOKED` | Mã QR hết hạn, bị thu hồi hoặc thông tin sự kiện không khớp |
| `429` | `RATE_LIMITED` | Quá giới hạn thử lại (quá 5 lần/phút) |

---

## 4. Hạn chế đã biết (Known Limitations)

1. **Floor Engine dùng ID kênh toàn cục (Global Channel IDs across Events)**:
   - Bộ máy `FloorControlEngine` quản lý mảng 32 slot kênh theo chỉ số kênh số nguyên (`1..32`) dùng chung toàn máy chủ, chưa phân tách vùng nhớ riêng lẻ (scop) theo `eventId`.
2. **Lưu trữ dữ liệu hoàn toàn trong Bộ nhớ (In-Memory Data Storage)**:
   - Toàn bộ danh sách sự kiện, kênh, phiên người dùng, quyền phát sóng và kịch bản Live Cue được lưu tạm thời trên bộ nhớ RAM (`sync.RWMutex` & Go `map`). Khi máy chủ Render khởi động lại, deploy bản dựng mới hoặc bị ngắt do không có lưu lượng truy cập ở gói Free (Sleep), dữ liệu sẽ khôi phục về trạng thái hạt giống mặc định (`seedEvents`).
3. **Chưa có Endpoint thu hồi / đặt lại (No "Reset QR/PIN" Endpoint)**:
   - Hệ thống hiện tại chưa hỗ trợ REST API chuyên biệt để thu hồi lập tức một mã QR / mã PIN cũ hoặc tạo mã PIN mới cho sự kiện đã khởi tạo mà không qua bước tạo mới sự kiện.
4. **Luồng UDP không chạy được trên Render Free/Web Service**:
   - Render Web Service chỉ hỗ trợ chuyển tiếp các kết nối HTTP/HTTPS và WebSocket qua giao thức TCP. Cổng UDP Fast-Path (`:50000`) dùng cho truyền âm thanh Opus trực tiếp không thể mở cổng lắng nghe công khai ra Internet trên Render.
5. **Chưa có Kênh Push Cảnh báo Khẩn cấp riêng biệt ngoài `FLOOR_DECISION`**:
   - Tín hiệu cắt lời toàn sự kiện khẩn cấp được phát đi thông qua các gói tin broadcast `FLOOR_DECISION` với trạng thái `FLOOR_PREEMPTED` trên kết nối WebSocket `/ws/floor`, chưa có kênh push notification riêng biệt (như Firebase Cloud Messaging hay WebPush) bên ngoài kết nối socket.
