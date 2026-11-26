# Tài liệu API Bổ Sung - Đàm Thoại Backend (Prompt 3A)

Tài liệu này mô tả các REST API và WebSocket endpoint được bổ sung trong Tuần 3A phục vụ quản trị sự kiện, khôi phục quyền Cấp 1, quản lý kênh (thêm, sửa, xóa) và quản lý thành viên.

## 1. Quản lý Sự kiện & Khôi phục (Admin)
- **Tạo sự kiện**: `POST /api/v1/events`
  - Request JSON: `{"eventName": "...", "description": "...", "pinCode": "...", "adminCode": "..."}`
  - Response (201): `EventInfo` (kèm thông tin sự kiện và 8 kênh).
- **Khôi phục sự kiện**: `POST /api/v1/events/restore`
  - Request JSON: `{"adminCode": "...", "displayName": "..."}`
  - Response (200): `{"event": EventInfo, "session": UserSessionDto}` (Cấp 1 mới, máy cũ mất quyền quản trị).

## 2. Quản lý Kênh
- **Thêm kênh**: `POST /api/v1/events/{eventId}/channels`
  - Request JSON: `{"userId": "...", "name": "..."}`
  - Response (201): `ChannelInfo`
- **Đổi tên kênh**: `PATCH /api/v1/events/{eventId}/channels/{channelId}`
  - Request JSON: `{"userId": "...", "name": "..."}`
  - Response (200): `ChannelInfo`
- **Xóa kênh**: `DELETE /api/v1/events/{eventId}/channels/{channelId}`
  - Request JSON: `{"userId": "..."}`
  - Response (200): `{"status": "DELETED", "channelId": "..."}`

## 3. Quản lý Thành viên
- **Danh sách thành viên**: `GET /api/v1/events/{eventId}/members`
  - Response (200): `[UserSessionDto, ...]`
- **Cập nhật thành viên**: `PATCH /api/v1/events/{eventId}/members/{userId}`
  - Request JSON: `{"actorUserId": "...", "userLevel": 2, "roleTitle": "..."}`
  - Response (200): `UserSessionDto`
- **Xóa thành viên**: `DELETE /api/v1/events/{eventId}/members/{userId}`
  - Request JSON: `{"actorUserId": "..."}`
  - Response (200): `{"status": "REMOVED", "userId": "..."}`

## 4. WebSocket Phát lượt nói (Floor Control Broadcast)
- **Endpoint**: `/ws/floor?userId=...&eventId=...`
  - Gói tin đẩy xuống client: `FloorDecision` (JSON).
