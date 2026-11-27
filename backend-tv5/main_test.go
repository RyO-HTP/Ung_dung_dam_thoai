package main

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"regexp"
	"strings"
	"testing"

	week2routing "damthoai/nguoi5server/Tuan_2_API_Quan_ly_Su_kien_Kenh_va_Dinh_tuyen_Am_thanh"
	week3qr "damthoai/nguoi5server/Tuan_3_API_Sinh_QR_Phan_Vai_Tu_Dong_Role_Provisioning"
	week4floor "damthoai/nguoi5server/Tuan_4_Floor_Control_CAS_va_Priority_Override"
	week5livecue "damthoai/nguoi5server/Tuan_5_Dong_bo_Thanh_Kich_ban_Live_Cue_Banner_WebSocket"
)

var validUserIDRegex = regexp.MustCompile(`^[A-Za-z0-9._-]{1,64}$`)

func buildTestServer() *httptest.Server {
	audioRouter := week2routing.NewChannelAudioRouter()
	eventManager := week2routing.NewEventManager(audioRouter)
	qrService := week3qr.NewQrRoleProvisioningService("0123456789abcdef0123456789abcdef", eventManager)
	floorEngine := week4floor.NewFloorControlEngine(nil)
	liveCueHub := week5livecue.NewLiveCueBannerHub(1)
	mux := newHTTPMux(eventManager, qrService, floorEngine, liveCueHub)
	return httptest.NewServer(mux)
}

func TestHealthzAndRemovedWsSignaling(t *testing.T) {
	srv := buildTestServer()
	defer srv.Close()

	resp, err := http.Get(srv.URL + "/healthz")
	if err != nil {
		t.Fatalf("GET /healthz lỗi: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("mong đợi 200 cho /healthz, nhận %d", resp.StatusCode)
	}

	wsResp, err := http.Get(srv.URL + "/ws/signaling")
	if err != nil {
		t.Fatalf("GET /ws/signaling lỗi: %v", err)
	}
	defer wsResp.Body.Close()
	if wsResp.StatusCode != http.StatusNotFound {
		t.Fatalf("mong đợi 404 sau khi gỡ /ws/signaling, nhận %d", wsResp.StatusCode)
	}
}

func TestQrRolesCompatibleWithAndroidRepo(t *testing.T) {
	t.Setenv("DEMO_MODE", "true")
	srv := buildTestServer()
	defer srv.Close()

	createBody, _ := json.Marshal(map[string]interface{}{
		"pinCode":     "240826",
		"displayName": "Tổng Đạo Diễn",
		"userLevel":   1,
	})
	joinResp, err := http.Post(srv.URL+"/api/v1/auth/join-pin", "application/json", bytes.NewReader(createBody))
	if err != nil {
		t.Fatalf("join-pin lỗi: %v", err)
	}
	var joinRes struct {
		UserID string `json:"userId"`
	}
	_ = json.NewDecoder(joinResp.Body).Decode(&joinRes)
	joinResp.Body.Close()

	resp, err := http.Get(srv.URL + "/api/v1/events/qr-roles?eventId=1&actorUserId=" + joinRes.UserID)
	if err != nil {
		t.Fatalf("GET qr-roles lỗi: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("mong đợi 200, nhận %d", resp.StatusCode)
	}

	var data struct {
		Cards []struct {
			DeptCode      string `json:"deptCode"`
			DeptTitle     string `json:"deptTitle"`
			RoleTitle     string `json:"roleTitle"`
			UserLevel     int    `json:"userLevel"`
			QrDeepLinkURI string `json:"qrDeepLinkUri"`
			ExpiresAt     int64  `json:"expiresAt"`
		} `json:"cards"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		t.Fatalf("decode JSON qr-roles lỗi: %v", err)
	}
	if len(data.Cards) != 5 {
		t.Fatalf("mong đợi 5 thẻ QR bộ phận, nhận %d", len(data.Cards))
	}
	for _, c := range data.Cards {
		if c.DeptCode == "" || c.DeptTitle == "" || c.RoleTitle == "" {
			t.Fatalf("thiếu trường trên thẻ QR: %+v", c)
		}
		if c.ExpiresAt <= 0 {
			t.Fatalf("expiresAt phải là epoch seconds > 0, nhận %d", c.ExpiresAt)
		}
		if !strings.HasPrefix(c.QrDeepLinkURI, "damthoai://join?token=") {
			t.Fatalf("qrDeepLinkUri sai định dạng: %s", c.QrDeepLinkURI)
		}
		tokenPart := strings.TrimPrefix(c.QrDeepLinkURI, "damthoai://join?token=")
		parts := strings.Split(tokenPart, ".")
		if len(parts) != 2 {
			t.Fatalf("token QR phải có 2 phần: %s", tokenPart)
		}
		rawPayload, err := base64.RawURLEncoding.DecodeString(parts[0])
		if err != nil {
			t.Fatalf("base64url decode lỗi: %v", err)
		}
		var payload struct {
			ListenCh []int `json:"listenCh"`
		}
		if err := json.Unmarshal(rawPayload, &payload); err != nil {
			t.Fatalf("listenCh không phải mảng số nguyên hợp lệ cho Android: %v (json=%s)", err, string(rawPayload))
		}
		if len(payload.ListenCh) == 0 {
			t.Fatalf("listenCh rỗng")
		}
	}
}

func TestJoinPinAndJoinByQrReturnUniqueUserIDAndSignalingEnv(t *testing.T) {
	t.Setenv("SIGNALING_URL", "https://damthoai-signaling.onrender.com/")
	t.Setenv("SIGNALING_TOKEN", "secret_token_test_123")
	t.Setenv("DEMO_MODE", "true")

	srv := buildTestServer()
	defer srv.Close()

	seenIDs := make(map[string]bool)

	for i := 0; i < 5; i++ {
		body, _ := json.Marshal(map[string]interface{}{
			"pinCode":          "240826",
			"displayName":      "Trần Nguyễn Tấn Tài",
			"roleTitle":        "Trưởng ban Kỹ thuật",
			"userLevel":        2,
			"preferredChannel": 4,
		})
		resp, err := http.Post(srv.URL+"/api/v1/auth/join-pin", "application/json", bytes.NewReader(body))
		if err != nil {
			t.Fatalf("POST join-pin lỗi: %v", err)
		}
		var res struct {
			UserID         string `json:"userId"`
			SignalingURL   string `json:"signalingUrl"`
			SignalingToken string `json:"signalingToken"`
			Session        struct {
				UserID            string `json:"userId"`
				SignalingURL      string `json:"signalingUrl"`
				SignalingToken    string `json:"signalingToken"`
				ListeningChannels []int  `json:"listeningChannels"`
				UserLevel         uint8  `json:"userLevel"`
			} `json:"session"`
		}
		if err := json.NewDecoder(resp.Body).Decode(&res); err != nil {
			resp.Body.Close()
			t.Fatalf("decode join-pin lỗi: %v", err)
		}
		resp.Body.Close()

		if !validUserIDRegex.MatchString(res.UserID) {
			t.Fatalf("userId không hợp lệ: %q", res.UserID)
		}
		if seenIDs[res.UserID] {
			t.Fatalf("userId bị trùng: %q", res.UserID)
		}
		seenIDs[res.UserID] = true

		if res.Session.UserLevel != 3 {
			t.Fatalf("mong đợi userLevel = 3 từ join-pin, nhận %d", res.Session.UserLevel)
		}

		if res.SignalingURL != "wss://damthoai-signaling.onrender.com" || res.SignalingToken != "secret_token_test_123" {
			t.Fatalf("signaling env không khớp: url=%q token=%q", res.SignalingURL, res.SignalingToken)
		}
	}

	qrBody, _ := json.Marshal(map[string]interface{}{
		"qrToken":     "MOCK_QR_EVENT_889900",
		"displayName": "Minh Hậu",
	})
	qrResp, err := http.Post(srv.URL+"/api/v1/auth/join-by-qr", "application/json", bytes.NewReader(qrBody))
	if err != nil {
		t.Fatalf("POST join-by-qr lỗi: %v", err)
	}
	defer qrResp.Body.Close()
	if qrResp.StatusCode != http.StatusOK {
		t.Fatalf("mong đợi 200 cho join-by-qr, nhận %d", qrResp.StatusCode)
	}
	var qrRes struct {
		UserID         string `json:"userId"`
		SignalingURL   string `json:"signalingUrl"`
		SignalingToken string `json:"signalingToken"`
		Event          struct {
			EventID int64 `json:"eventId"`
		} `json:"event"`
		Session struct {
			UserID         string `json:"userId"`
			SignalingURL   string `json:"signalingUrl"`
			SignalingToken string `json:"signalingToken"`
		} `json:"session"`
	}
	if err := json.NewDecoder(qrResp.Body).Decode(&qrRes); err != nil {
		t.Fatalf("decode join-by-qr lỗi: %v", err)
	}
	if !validUserIDRegex.MatchString(qrRes.UserID) || seenIDs[qrRes.UserID] {
		t.Fatalf("join-by-qr userId không hợp lệ hoặc trùng: %q", qrRes.UserID)
	}
}

func TestMockQrDisabledWhenDemoModeFalse(t *testing.T) {
	t.Setenv("DEMO_MODE", "false")
	srv := buildTestServer()
	defer srv.Close()

	qrBody, _ := json.Marshal(map[string]interface{}{
		"qrToken":     "MOCK_QR_EVENT_889900",
		"displayName": "Minh Hậu",
	})
	qrResp, err := http.Post(srv.URL+"/api/v1/auth/join-by-qr", "application/json", bytes.NewReader(qrBody))
	if err != nil {
		t.Fatalf("POST join-by-qr lỗi: %v", err)
	}
	defer qrResp.Body.Close()
	if qrResp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("mong đợi 401 khi DEMO_MODE=false, nhận %d", qrResp.StatusCode)
	}
}
