package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"strings"

	week2routing "damthoai/nguoi5server/Tuan_2_API_Quan_ly_Su_kien_Kenh_va_Dinh_tuyen_Am_thanh"
	week3qr "damthoai/nguoi5server/Tuan_3_API_Sinh_QR_Phan_Vai_Tu_Dong_Role_Provisioning"
	week4floor "damthoai/nguoi5server/Tuan_4_Floor_Control_CAS_va_Priority_Override"
	week5livecue "damthoai/nguoi5server/Tuan_5_Dong_bo_Thanh_Kich_ban_Live_Cue_Banner_WebSocket"
)

// =============================================================================
// ENTRYPOINT HỢP NHẤT BACKEND GO CỦA NGƯỜI 5 (BACKEND & REALTIME SERVER)
// Máy chủ Signaling WebRTC dùng riêng signaling_server.py (Python);
// Backend Go phụ trách: Sự kiện/PIN/Kênh, QR Phân vai, Floor Control, Live Cue.
// =============================================================================

func main() {
	httpAddr := resolveHTTPAddr()
	udpAddr := getEnv("UDP_RELAY_ADDR", ":50000")

	// 1. Tuần 2: Khởi tạo Bộ định tuyến Âm thanh UDP (ChannelAudioRouter) & Quản lý Sự kiện/Kênh (PIN: 240826, 889900)
	audioRouter := week2routing.NewChannelAudioRouter()
	eventManager := week2routing.NewEventManager(audioRouter)

	// 2. Tuần 3: Khởi tạo Dịch vụ Sinh & Xác thực Mã QR Phân vai Tự động (HMAC-SHA256 + MOCK_QR_EVENT_889900)
	qrService := week3qr.NewQrRoleProvisioningService("0123456789abcdef0123456789abcdef", eventManager)

	// 3. Tuần 4: Khởi tạo Bộ điều khiển Quyền nói Lock-Free CAS & Priority Override (Cấp 1/2/3 <-> Priority 3/2/1)
	floorEngine := week4floor.NewFloorControlEngine(func(decision week4floor.FloorDecision) {
		log.Printf("[FloorControl] Kênh %s (%d) -> %s (Reason: %s)",
			decision.ChannelCode, decision.ChannelID, decision.Status, decision.Reason)
	})
	floorEngine.SetIdentityResolver(eventManager)
	floorHub := week4floor.NewFloorHub(floorEngine)
	stopWatchdog := make(chan struct{})
	defer close(stopWatchdog)
	go floorEngine.RunWatchdogLoop(stopWatchdog)

	// 4. Tuần 5: Khởi tạo Registry Đồng bộ Thanh Kịch bản (Live Cue Banner Registry)
	liveCueRegistry := week5livecue.NewLiveCueRegistry(eventManager)

	// Khởi chạy UDP Audio Fast-Path trên cổng 50000
	go func() {
		if err := audioRouter.ListenAndRouteUDP(udpAddr); err != nil {
			log.Printf("[UDP Audio Router] Lỗi lắng nghe UDP %s: %v", udpAddr, err)
		}
	}()

	mux := newHTTPMux(eventManager, qrService, floorEngine, floorHub, liveCueRegistry)

	log.Printf("[DamThoai Server - Người 5] Đang chạy HTTP/WS tại %s và UDP Audio tại %s", httpAddr, udpAddr)
	log.Fatal(http.ListenAndServe(httpAddr, mux))
}

func newHTTPMux(
	eventManager *week2routing.EventManager,
	qrService *week3qr.QrRoleProvisioningService,
	floorEngine *week4floor.FloorControlEngine,
	floorHub *week4floor.FloorHub,
	liveCueRegistry *week5livecue.LiveCueRegistry,
) *http.ServeMux {
	mux := http.NewServeMux()

	// Health check cho Render / Railway
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("OK\n"))
	})

	// Thông tin trạng thái máy chủ (không đăng ký /ws/signaling để tránh trùng với signaling_server.py)
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
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
	mux.HandleFunc("/api/v1/events/qr-roles", qrService.HandleGenerateDepartmentQrCodes)
	mux.HandleFunc("/api/v1/events/", func(w http.ResponseWriter, r *http.Request) {
		path := r.URL.Path
		trimmed := strings.TrimPrefix(path, "/api/v1/events/")
		parts := strings.Split(strings.Trim(trimmed, "/"), "/")

		if trimmed == "restore" {
			eventManager.HandleRestoreEvent(w, r)
			return
		}
		if trimmed == "qr-roles" {
			qrService.HandleGenerateDepartmentQrCodes(w, r)
			return
		}

		if len(parts) == 1 && parts[0] != "" {
			if r.Method == http.MethodGet {
				eventManager.HandleGetEvent(w, r)
				return
			}
		}

		if len(parts) >= 2 {
			sub := parts[1]
			if sub == "channels" {
				if r.Method == http.MethodPost && len(parts) == 2 {
					eventManager.HandleAddChannel(w, r)
					return
				}
				if len(parts) >= 3 && (r.Method == http.MethodPatch || r.Method == http.MethodPut) {
					eventManager.HandleUpdateChannel(w, r)
					return
				}
				if len(parts) >= 3 && r.Method == http.MethodDelete {
					eventManager.HandleDeleteChannel(w, r)
					return
				}
			}
			if sub == "members" {
				if r.Method == http.MethodGet && len(parts) == 2 {
					eventManager.HandleListMembers(w, r)
					return
				}
				if len(parts) >= 3 && (r.Method == http.MethodPatch || r.Method == http.MethodPut) {
					eventManager.HandleUpdateMember(w, r)
					return
				}
				if len(parts) >= 3 && r.Method == http.MethodDelete {
					eventManager.HandleDeleteMember(w, r)
					return
				}
			}
		}
		http.NotFound(w, r)
	})
	mux.HandleFunc("/api/v1/auth/join-pin", eventManager.HandleJoinByPin)
	mux.HandleFunc("/api/v1/users/switch-channel", eventManager.HandleSwitchChannel)
	mux.HandleFunc("/api/v1/users/leave-channel", eventManager.HandleLeaveChannel)

	// Endpoints Tuần 3 (QR Role Provisioning)
	mux.HandleFunc("/api/v1/auth/join-by-qr", qrService.HandleJoinByDepartmentQr)

	// Endpoints Tuần 4 (Floor Control CAS & Priority Override)
	mux.HandleFunc("/api/v1/floor/request", floorEngine.HandleFloorRequestHTTP)
	mux.HandleFunc("/api/v1/floor/release", floorEngine.HandleFloorReleaseHTTP)
	mux.HandleFunc("/api/v1/floor/emergency-override", floorEngine.HandleEmergencyOverrideHTTP)
	mux.HandleFunc("/ws/floor", floorHub.ServeFloorWS)

	// Endpoints Tuần 5 (Live Cue Banner WebSocket & REST)
	mux.HandleFunc("/ws/live-cue", liveCueRegistry.ServeLiveCueWS)
	mux.HandleFunc("/api/v1/live-cue/fire", liveCueRegistry.HandleFireCueHTTP)

	return mux
}

// resolveHTTPAddr ưu tiên HTTP_ADDR, sau đó đến biến PORT của Render/Railway, mặc định ":8080".
func resolveHTTPAddr() string {
	if addr := strings.TrimSpace(os.Getenv("HTTP_ADDR")); addr != "" {
		return addr
	}
	if port := strings.TrimSpace(os.Getenv("PORT")); port != "" {
		if strings.Contains(port, ":") {
			return port
		}
		return ":" + port
	}
	return ":8080"
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
