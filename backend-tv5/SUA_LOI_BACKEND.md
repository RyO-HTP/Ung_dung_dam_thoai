# Sửa lỗi backend sau khi chạy go test (3 test FAIL)

## Nguyên nhân
1. TestQRComprehensive và TestQrRolesCompatibleWithAndroidRepo (qr-roles trả 404):
   test gọi POST /events/restore với adminCode "240826" trên sự kiện mẫu, nhưng sự kiện mẫu
   không có adminCode nên restore trả 404, userId rỗng, rồi qr-roles báo "thiếu actor" (404).
   Code backend đúng, test sai. Đã sửa test: tạo sự kiện thật kèm adminCode, dùng session Cấp 1 trả về.
2. TestLiveCueRegistryAndGroupA7: test gọi FireOrCreateModelCue (hàm GỬI cue) rồi đòi hub rỗng,
   nên luôn fail. Đã thêm LiveCueBannerHub.Snapshot() (chỉ đọc) và viết lại test.

## Lỗi thật tìm thêm trong code (đã sửa)
- live-cue/fire: gửi không kèm userId thì BỎ QUA kiểm tra quyền, ai cũng gửi được cue. Nay trả 403 USER_NOT_IN_EVENT.
- qr-roles: Cấp 2 sinh được thẻ QR Cấp 1 (leo thang đặc quyền). Nay chỉ sinh thẻ có cấp bằng hoặc thấp hơn người gọi.
- Giới hạn thử sai restore là biến toàn cục nên các test ảnh hưởng nhau: thêm ResetRestoreRateLimit(), gọi khi dựng server test.

## File đã sửa
- Tuan_5_.../live_cue_banner_ws.go (fire cần userId, thêm Snapshot)
- Tuan_3_.../qr_role_provisioning.go (lọc thẻ theo cấp người gọi)
- Tuan_2_.../event_admin_api.go (ResetRestoreRateLimit)
- main_test.go, comprehensive_test.go (helper mustCreateAdminEvent, test hồi quy Live Cue cô lập giữa 2 sự kiện)
