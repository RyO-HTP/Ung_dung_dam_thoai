BÀN GIAO HẠ TẦNG MẠNG - TUẦN 2 ĐẾN TUẦN 6

Người phụ trách: Thành viên 1

1. TỔNG QUAN TIẾN ĐỘ

| Tuần | Nhiệm vụ | Trạng thái | File chính |
|---|---|---|---|
| 1 | Cấu hình WebRTC + STUN/TURN miễn phí | Hoàn thành (nâng cấp `webrtc_config.py`) | `webrtc_config.py`, `WebRTCClient.kt` |
| 2 | Luồng truyền âm thanh P2P | Hoàn thành (sửa lỗi track âm thanh không được gửi) | `WebRTCClient.kt`, `DamThoaiCallManager.kt` |
| 3 | ICE Gathering liên tục | Hoàn thành | `WebRTCClient.kt`, `SignalingChannel.kt` |
| 4 | Seamless Handover Wi-Fi <-> 4G | Hoàn thành | `NetworkChangeReceiver.kt`, `SignalingClient.kt`, `DamThoaiCallManager.kt` |
| 5 | Local LAN Fallback (mDNS) | Hoàn thành | `LocalLanSignaling.kt`, `DamThoaiCallManager.kt` |
| 6 | Giả lập Packet Loss + đo tải server | Hoàn thành (công cụ + chỉ số trong app) | `tools/`, `WebRTCClient.collectStats()` |

2. CHI TIẾT TỪNG TUẦN

Tuần 1 - STUN/TURN
- Nâng cấp `webrtc_config.py`: thứ tự nguồn TURN là credential tạm thời qua Metered REST API (đặt `METERED_APP_NAME`, `METERED_API_KEY`) -> coturn tự dựng (`TURN_URLS`, `TURN_USERNAME`, `TURN_CREDENTIAL`) -> tài khoản TURN Metered của nhóm (mặc định) + STUN Google/Cloudflare. Đặt `TURN_FALLBACK_OPENRELAY=1` để thêm TURN công cộng OpenRelay dự phòng.
- App có sẵn bộ STUN/TURN mặc định (`WebRTCClient.DEFAULT_ICE_SERVERS`) phòng khi chưa nhận được cấu hình từ server.

Tuần 2 - Âm thanh P2P
- Lỗi cũ: audio track được tạo nhưng KHÔNG `addTrack` vào PeerConnection -> SDP không có luồng gửi âm thanh, hai máy kết nối nhưng không nghe được nhau. Đã sửa.
- Chuyển sang Unified Plan; nhận track từ xa qua `onAddTrack` (Unified Plan không gọi `onAddStream`). Observer giờ được gắn `webRTCClient` nên `setOutputMuted` (AudioRouteGuard của TV4) hoạt động với track thật.
- ICE candidate đến trước Remote Description được giữ lại và nạp sau (trước đây bị WebRTC bỏ qua).
- Thêm gói `hangup` để hai bên cùng kết thúc cuộc gọi.

Tuần 3 - ICE Gathering liên tục
- `continualGatheringPolicy = GATHER_CONTINUALLY`: WebRTC tự thu thập candidate mới bất cứ khi nào có giao diện mạng mới (bật 4G, vào vùng Wi-Fi).
- Candidate chết (mất Wi-Fi) được thu hồi qua `onIceCandidatesRemoved` -> gửi gói `ice_candidates_removed` để đối tác bỏ đi.
- `iceCandidatePoolSize = 2` (dựng sẵn candidate), `iceConnectionReceivingTimeout = 3000` (phát hiện nhanh đường truyền chết).

Tuần 4 - Seamless Handover
- `NetworkChangeReceiver` theo dõi MẠNG MẶC ĐỊNH (`registerDefaultNetworkCallback`), chống dội 800ms.
- Khi đổi mạng: (1) `SignalingClient.reconnectNow()` mở WebSocket mới ngay, (2) ICE restart tạo Offer mới, Offer được xếp hàng và gửi ngay khi WebSocket mở lại. Track âm thanh, DTLS/SRTP giữ nguyên -> không rớt cuộc gọi.
- ICE `DISCONNECTED` không còn kết thúc cuộc gọi như trước: chờ 2.5s cho ICE tự phục hồi, quá hạn thì ICE restart; tối đa 5 lần (backoff tăng dần) mới kết thúc. UI có trạng thái mới `CallState.Reconnecting`.
- Xử lý va chạm Offer khi hai máy cùng restart: máy có user_id lớn hơn rollback Offer của mình.
- Server: sửa lỗi kết nối cũ (Wi-Fi) đóng sau làm xoá nhầm kết nối mới (4G) khỏi bảng định tuyến. Server tự gắn `sender_id` thật (chống giả mạo).

Tuần 5 - Local LAN Fallback
- `LocalLanSignaling`: mỗi điện thoại mở cổng TCP và quảng bá dịch vụ mDNS `_bodampeer._tcp` kèm user_id, quét tìm các máy khác, trao đổi SDP/ICE trực tiếp (không cần máy chủ).
- `DamThoaiCallManager` tự bật chế độ LAN khi Signaling Server không liên lạc được quá 6s; bỏ STUN/TURN (chỉ dùng IP nội bộ). Nếu đang gọi dở thì ICE restart qua kênh LAN. Server sống lại -> tự quay về chế độ Server (đợi hết cuộc gọi đang diễn ra).
- Socket LAN được bind vào mạng Wi-Fi để vẫn đi được khi Android đang ưu tiên 4G.
- Vẫn giữ cách cũ: tìm máy chủ PC trong LAN qua `mdns_broadcaster.py` (`autoDiscoverAndConnect`).
- API cho UI (TV3): `callManager.connectionMode` (SERVER / LAN_P2P), `callManager.lanPeers`, `callManager.enableLanFallback()`.

Tuần 6 - Packet Loss & độ chịu tải
- Trong app: `callManager.callStats` (StateFlow, 2s/lần) gồm RTT, tỉ lệ mất gói, jitter, tỉ lệ âm thanh bị vá (concealment), đi qua relay hay trực tiếp. Ghi vào CSV FieldTestLogger với sự kiện `webrtc_stats`, `ice_restart`, `ice_connected`, `network_change`, `lan_fallback_on/off`.
- `tools/simulate_packet_loss.sh`: giả lập rớt gói 5/10/20/30%, rớt theo cụm, jitter, 4G xấu, mất mạng 3s (tc netem trên máy Linux làm hotspot; Windows dùng clumsy).
- `tools/load_test_signaling.py`: giả lập hàng trăm thiết bị bắn offer/answer/ICE vào server, đo độ trễ chuyển tiếp p50/p95/p99, tỉ lệ tới nơi, thông lượng.

3. KẾT QUẢ ĐO THỬ (máy local, Windows, Python 3.13)

```
python tools/load_test_signaling.py --clients 200 --messages 30 --interval 0.03 --ramp 2
Thiết bị giả lập      : 200 (lỗi kết nối: 0)
Gói đã gửi / nhận     : 6000 / 6000  (100.00% tới nơi)
Thông lượng           : 990.3 gói/s
Kết nối+cấu hình TURN : p50=1.5ms p95=2.1ms
Trễ chuyển tiếp SDP/ICE: p50=3.2ms p95=10.3ms p99=15.9ms
```

Số liệu phục hồi âm thanh khi mất gói (lossRate, conceal) cần đo trên điện thoại thật theo kịch bản trong `simulate_packet_loss.sh`, dùng chung buổi test thực địa với TV4/TV5.

4. CÁCH CHẠY

```
pip install -r requirements.txt
python signaling_server.py          # máy chủ trung tâm, cổng 8765
python mdns_broadcaster.py          # (tuỳ chọn) phát IP máy chủ trong LAN
python tools/load_test_signaling.py --clients 200
```

5. LƯU Ý CHO CÁC THÀNH VIÊN

- TV2: Factory/Opus/ADM vẫn giữ nguyên như cũ, TV1 chỉ đổi `RTCConfiguration` và luồng track.
- TV3: Có thể hiển thị `CallState.Reconnecting` ("Đang chuyển mạng...") và `connectionMode == LAN_P2P` ("Chế độ nội bộ - không Internet"). Có thể lấy RTT từ `callStats` thay cho ping TCP trong `NetworkQualityMonitor`.
- TV5: Giao thức có thêm các gói `ice_candidates_removed`, `hangup`, `network_change`, `ping`/`pong`, `stats`. Server Go cần chuyển tiếp nguyên vẹn các gói này và tự gắn `sender_id`.
- Bảo mật: `api.js` đang ghi thẳng LiveKit API key/secret trong code; nên chuyển sang biến môi trường và đổi secret.
