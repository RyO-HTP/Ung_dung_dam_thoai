package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"strings"

	week1signaling "damthoai/nguoi5server/Tuan_1_Signaling_SDP_ICE_va_Database"
	week2routing "damthoai/nguoi5server/Tuan_2_API_Quan_ly_Su_kien_Kenh_va_Dinh_tuyen_Am_thanh"
	week3qr "damthoai/nguoi5server/Tuan_3_API_Sinh_QR_Phan_Vai_Tu_Dong_Role_Provisioning"
	week4floor "damthoai/nguoi5server/Tuan_4_Floor_Control_CAS_va_Priority_Override"
	week5livecue "damthoai/nguoi5server/Tuan_5_Dong_bo_Thanh_Kich_ban_Live_Cue_Banner_WebSocket"
)

// =============================================================================
// ENTRYPOINT HỢP NHẤT TOÀN BỘ 6 TUẦN CỦA NGƯỜI 5 (BACKEND & REALTIME SERVER)
// Đã đồng bộ 100% các API và WebSocket với ứng dụng Android trên nhánh Tuan-4
// =============================================================================

func main() {
	httpAddr := getEnv("HTTP_ADDR", ":8080")
	udpAddr := getEnv("UDP_RELAY_ADDR", ":50000")
	turnHost := getEnv("TURN_PUBLIC_IP", "10.0.2.2")
	turnSecret := getEnv("TURN_SECRET", "damthoai_turn_secret_2026")

	// 1. Tuần 1: Khởi tạo WebRTC Signaling Hub (SDP Offer/Answer, ICE Candidate, STUN/TURN, Voice Note, Handover)
	signalingHub := week1signaling.NewSignalingHub(turnHost, turnSecret)

	// 2. Tuần 2: Khởi tạo Bộ định tuyến Âm thanh UDP (ChannelAudioRouter) & Quản lý Sự kiện/Kênh (PIN: 240826, 889900)
	audioRouter := week2routing.NewChannelAudioRouter()
	eventManager := week2routing.NewEventManager(audioRouter)

	// 3. Tuần 3: Khởi tạo Dịch vụ Sinh & Xác thực Mã QR Phân vai Tự động (HMAC-SHA256 + MOCK_QR_EVENT_889900)
	qrService := week3qr.NewQrRoleProvisioningService("0123456789abcdef0123456789abcdef")

	// 4. Tuần 4: Khởi tạo Bộ điều khiển Quyền nói Lock-Free CAS & Priority Override (Cấp 1/2/3 <-> Priority 3/2/1)
	floorEngine := week4floor.NewFloorControlEngine(func(decision week4floor.FloorDecision) {
		log.Printf("[FloorControl] Kênh %s (%d) -> %s (Reason: %s)",
			decision.ChannelCode, decision.ChannelID, decision.Status, decision.Reason)
	})
	stopWatchdog := make(chan struct{})
	defer close(stopWatchdog)
	go floorEngine.RunWatchdogLoop(stopWatchdog)

	// 5. Tuần 5: Khởi tạo Hub Đồng bộ Thanh Kịch bản (Live Cue Banner: "Tiết mục 4 – Ban nhạc khách mời")
	liveCueHub := week5livecue.NewLiveCueBannerHub(1)

	// Khởi chạy UDP Audio Fast-Path trên cổng 50000
	go func() {
		if err := audioRouter.ListenAndRouteUDP(udpAddr); err != nil {
			log.Printf("[UDP Audio Router] Lỗi lắng nghe UDP %s: %v", udpAddr, err)
		}
	}()

	// Đăng ký toàn bộ HTTP REST API & WebSocket Endpoints
	mux := http.NewServeMux()

	// Endpoints Tuần 1 (Hỗ trợ cả "/ws/signaling" và "/" cho SignalingClient.kt trên Android)
	mux.HandleFunc("/ws/signaling", signalingHub.ServeWS)
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/" && r.Header.Get("Upgrade") == "websocket" {
			signalingHub.ServeWS(w, r)
			return
		}
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"service": "DamThoai Push-To-Talk Unified Server (Người 5 - 6 Tuần)",
			"status":  "ONLINE",
			"pins":    []string{"240826", "889900", "482910"},
		})
	})

	// Endpoints Tuần 2 (Sự kiện, PIN, Kênh & Admin)
	mux.HandleFunc("/api/v1/events", eventManager.HandleCreateEvent)
	mux.HandleFunc("/api/v1/events/restore", eventManager.HandleRestoreEvent)
	mux.HandleFunc("/api/v1/events/", func(w http.ResponseWriter, r *http.Request) {
		path := r.URL.Path
		if strings.HasSuffix(path, "/channels") && r.Method == http.MethodPost {
			eventManager.HandleAddChannel(w, r)
			return
		}
		if strings.Contains(path, "/channels/") {
			if r.Method == http.MethodPatch || r.Method == http.MethodPut {
				eventManager.HandleUpdateChannel(w, r)
				return
			}
			if r.Method == http.MethodDelete {
				eventManager.HandleDeleteChannel(w, r)
				return
			}
		}
		if strings.HasSuffix(path, "/members") && r.Method == http.MethodGet {
			eventManager.HandleListMembers(w, r)
			return
		}
		if strings.Contains(path, "/members/") {
			if r.Method == http.MethodPatch || r.Method == http.MethodPut {
				eventManager.HandleUpdateMember(w, r)
				return
			}
			if r.Method == http.MethodDelete {
				eventManager.HandleDeleteMember(w, r)
				return
			}
		}
		http.NotFound(w, r)
	})
	mux.HandleFunc("/api/v1/auth/join-pin", eventManager.HandleJoinByPin)
	mux.HandleFunc("/api/v1/users/switch-channel", eventManager.HandleSwitchChannel)
	mux.HandleFunc("/api/v1/users/leave-channel", eventManager.HandleLeaveChannel)

	// Endpoints Tuần 3 (QR Role Provisioning)
	mux.HandleFunc("/api/v1/events/qr-roles", qrService.HandleGenerateDepartmentQrCodes)
	mux.HandleFunc("/api/v1/auth/join-by-qr", qrService.HandleJoinByDepartmentQr)

	// Endpoints Tuần 4 (Floor Control CAS & Priority Override)
	mux.HandleFunc("/api/v1/floor/request", floorEngine.HandleFloorRequestHTTP)
	mux.HandleFunc("/api/v1/floor/release", floorEngine.HandleFloorReleaseHTTP)
	mux.HandleFunc("/api/v1/floor/emergency-override", floorEngine.HandleEmergencyOverrideHTTP)

	// Endpoints Tuần 5 (Live Cue Banner WebSocket & REST)
	mux.HandleFunc("/ws/live-cue", liveCueHub.ServeLiveCueWS)
	mux.HandleFunc("/api/v1/live-cue/fire", liveCueHub.HandleFireCueHTTP)

	log.Printf("[DamThoai Server - Người 5] Đang chạy HTTP/WS tại %s và UDP Audio tại %s", httpAddr, udpAddr)
	log.Fatal(http.ListenAndServe(httpAddr, mux))
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
