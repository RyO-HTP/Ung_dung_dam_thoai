package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	week1signaling "damthoai/nguoi5server/Tuan_1_Signaling_SDP_ICE_va_Database"
	week2routing "damthoai/nguoi5server/Tuan_2_API_Quan_ly_Su_kien_Kenh_va_Dinh_tuyen_Am_thanh"
	week3qr "damthoai/nguoi5server/Tuan_3_API_Sinh_QR_Phan_Vai_Tu_Dong_Role_Provisioning"
	week4floor "damthoai/nguoi5server/Tuan_4_Floor_Control_CAS_va_Priority_Override"
)

func envOrDefault(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}

func main() {
	turnHost := envOrDefault("TURN_HOST", "localhost")
	turnSecret := envOrDefault("TURN_SECRET", "change-me-in-production")
	qrSecret := envOrDefault("QR_MASTER_SECRET", "")
	port := envOrDefault("PORT", "8787")

	signalingHub := week1signaling.NewSignalingHub(turnHost, turnSecret)
	audioRouter := week2routing.NewChannelAudioRouter()
	eventManager := week2routing.NewEventManager(audioRouter)
	qrService := week3qr.NewQrRoleProvisioningService(qrSecret)
	floorEngine := week4floor.NewFloorControlEngine(func(decision week4floor.FloorDecision) {
		log.Printf("floor event: status=%s channel=%d user=%d", decision.Status, decision.ChannelID, decision.RevokedUserID)
	})

	// Watchdog tự động thu hồi mic khi giữ quá 60s hoặc mất tín hiệu > 2,5s
	watchdogStop := make(chan struct{})
	go floorEngine.RunWatchdogLoop(watchdogStop)
	defer close(watchdogStop)

	mux := http.NewServeMux()
	mux.HandleFunc("/ws/signaling", signalingHub.ServeWS)
	mux.HandleFunc("/", signalingHub.ServeWS)
	mux.HandleFunc("/api/v1/events", eventManager.HandleCreateEvent)
	mux.HandleFunc("/api/v1/auth/join-pin", eventManager.HandleJoinByPin)
	mux.HandleFunc("/api/v1/channels/switch", eventManager.HandleSwitchChannel)
	mux.HandleFunc("/api/v1/channels/leave", eventManager.HandleLeaveChannel)
	mux.HandleFunc("/api/v1/events/qr-roles", qrService.HandleGenerateDepartmentQrCodes)
	mux.HandleFunc("/api/v1/auth/join-by-qr", qrService.HandleJoinByDepartmentQr)
	mux.HandleFunc("/api/v1/floor/request", floorEngine.HandleFloorRequestHTTP)
	mux.HandleFunc("/api/v1/floor/release", floorEngine.HandleFloorReleaseHTTP)
	mux.HandleFunc("/api/v1/floor/emergency-override", floorEngine.HandleEmergencyOverrideHTTP)

	server := &http.Server{
		Addr:              ":" + port,
		Handler:           mux,
		ReadHeaderTimeout: 10 * time.Second,
	}

	serverErrors := make(chan error, 1)
	go func() {
		log.Printf("Đàm Thoại Go backend đang chạy tại http://localhost:%s", port)
		serverErrors <- server.ListenAndServe()
	}()

	shutdown := make(chan os.Signal, 1)
	signal.Notify(shutdown, os.Interrupt, syscall.SIGTERM)
	select {
	case err := <-serverErrors:
		if !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("server error: %v", err)
		}
	case <-shutdown:
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := server.Shutdown(ctx); err != nil {
			log.Printf("shutdown error: %v", err)
		}
	}
}
