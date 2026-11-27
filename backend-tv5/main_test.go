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
	"github.com/gorilla/websocket"
)

var validUserIDRegex = regexp.MustCompile(`^[A-Za-z0-9._-]{1,64}$`)

func buildTestServer() *httptest.Server {
	audioRouter := week2routing.NewChannelAudioRouter()
	eventManager := week2routing.NewEventManager(audioRouter)
	qrService := week3qr.NewQrRoleProvisioningService("0123456789abcdef0123456789abcdef", eventManager)
	floorEngine := week4floor.NewFloorControlEngine(nil)
	floorEngine.SetIdentityResolver(eventManager)
	floorHub := week4floor.NewFloorHub(floorEngine)
	liveCueRegistry := week5livecue.NewLiveCueRegistry(eventManager)
	mux := newHTTPMux(eventManager, qrService, floorEngine, floorHub, liveCueRegistry)
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

func TestFloorControlResolverAndErrors(t *testing.T) {
	t.Setenv("DEMO_MODE", "true")
	srv := buildTestServer()
	defer srv.Close()

	// 1. Request floor with unknown user -> 403 USER_NOT_IN_EVENT
	reqBody, _ := json.Marshal(map[string]interface{}{
		"channelId": 1,
		"userId":    "nonexistent_user",
	})
	resp, err := http.Post(srv.URL+"/api/v1/floor/request", "application/json", bytes.NewReader(reqBody))
	if err != nil {
		t.Fatalf("POST /api/v1/floor/request lỗi: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusForbidden {
		t.Fatalf("mong đợi 403 cho user không tồn tại, nhận %d", resp.StatusCode)
	}
	var errRes map[string]string
	_ = json.NewDecoder(resp.Body).Decode(&errRes)
	if errRes["error"] != "USER_NOT_IN_EVENT" {
		t.Fatalf("mong đợi lỗi USER_NOT_IN_EVENT, nhận %+v", errRes)
	}

	// 2. Join pin as volunteer (Level 3)
	joinBody, _ := json.Marshal(map[string]interface{}{
		"pinCode":     "240826",
		"displayName": "Cộng tác viên A",
		"userLevel":   3,
	})
	joinResp, err := http.Post(srv.URL+"/api/v1/auth/join-pin", "application/json", bytes.NewReader(joinBody))
	if err != nil {
		t.Fatalf("join-pin lỗi: %v", err)
	}
	var joinRes struct {
		UserID string `json:"userId"`
	}
	_ = json.NewDecoder(joinResp.Body).Decode(&joinRes)
	joinResp.Body.Close()

	// 3. Try emergency override as volunteer (Level 3) -> 403 FORBIDDEN_NOT_ADMIN
	overrideBody, _ := json.Marshal(map[string]interface{}{
		"userId": joinRes.UserID,
	})
	overrideResp, err := http.Post(srv.URL+"/api/v1/floor/emergency-override", "application/json", bytes.NewReader(overrideBody))
	if err != nil {
		t.Fatalf("POST emergency-override lỗi: %v", err)
	}
	defer overrideResp.Body.Close()
	if overrideResp.StatusCode != http.StatusForbidden {
		t.Fatalf("mong đợi 403 khi Level 3 gọi emergency override, nhận %d", overrideResp.StatusCode)
	}
	var overrideErrRes map[string]string
	_ = json.NewDecoder(overrideResp.Body).Decode(&overrideErrRes)
	if overrideErrRes["error"] != "FORBIDDEN_NOT_ADMIN" {
		t.Fatalf("mong đợi lỗi FORBIDDEN_NOT_ADMIN, nhận %+v", overrideErrRes)
	}
}

func TestFloorHubWebSocketAndSnapshot(t *testing.T) {
	t.Setenv("DEMO_MODE", "true")
	srv := buildTestServer()
	defer srv.Close()

	// 1. Join pin to get valid user
	joinBody, _ := json.Marshal(map[string]interface{}{
		"pinCode":     "240826",
		"displayName": "Nguyễn Minh Hậu",
		"userLevel":   2,
	})
	joinResp, err := http.Post(srv.URL+"/api/v1/auth/join-pin", "application/json", bytes.NewReader(joinBody))
	if err != nil {
		t.Fatalf("join-pin lỗi: %v", err)
	}
	var joinRes struct {
		UserID string `json:"userId"`
	}
	_ = json.NewDecoder(joinResp.Body).Decode(&joinRes)
	joinResp.Body.Close()

	// 2. Connect to /ws/floor via WebSocket
	wsURL := "ws" + strings.TrimPrefix(srv.URL, "http") + "/ws/floor?userId=" + joinRes.UserID + "&eventId=1"
	ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("dial /ws/floor lỗi: %v", err)
	}
	defer ws.Close()

	// 3. Read initial FLOOR_SNAPSHOT message
	_, msg, err := ws.ReadMessage()
	if err != nil {
		t.Fatalf("đọc websocket message lỗi: %v", err)
	}
	var snap struct {
		Type string `json:"type"`
	}
	if err := json.Unmarshal(msg, &snap); err != nil {
		t.Fatalf("unmarshal snapshot lỗi: %v", err)
	}
	if snap.Type != "FLOOR_SNAPSHOT" {
		t.Fatalf("mong đợi type FLOOR_SNAPSHOT, nhận %q (msg=%s)", snap.Type, string(msg))
	}

	// 4. Request floor via REST API
	reqBody, _ := json.Marshal(map[string]interface{}{
		"channelId":   2,
		"channelCode": "ch_02",
		"userId":      joinRes.UserID,
		"displayName": "Nguyễn Minh Hậu",
		"userLevel":   2,
	})
	postResp, err := http.Post(srv.URL+"/api/v1/floor/request", "application/json", bytes.NewReader(reqBody))
	if err != nil {
		t.Fatalf("POST /api/v1/floor/request lỗi: %v", err)
	}
	postResp.Body.Close()

	// 5. Read broadcast FLOOR_DECISION over WebSocket
	_, decMsg, err := ws.ReadMessage()
	if err != nil {
		t.Fatalf("đọc websocket broadcast lỗi: %v", err)
	}
	var dec struct {
		Type   string `json:"type"`
		Status string `json:"status"`
	}
	if err := json.Unmarshal(decMsg, &dec); err != nil {
		t.Fatalf("unmarshal decision lỗi: %v", err)
	}
	if dec.Type != "FLOOR_DECISION" || dec.Status != "FLOOR_GRANTED" {
		t.Fatalf("mong đợi FLOOR_DECISION / FLOOR_GRANTED, nhận type=%q status=%q (msg=%s)", dec.Type, dec.Status, string(decMsg))
	}
}

func TestLiveCueRegistryAndGroupA7(t *testing.T) {
	t.Setenv("DEMO_MODE", "true")
	srv := buildTestServer()
	defer srv.Close()

	// 1. Join pin as Level 3 (Volunteer)
	joinBody3, _ := json.Marshal(map[string]interface{}{
		"pinCode":     "240826",
		"displayName": "Cộng tác viên B",
		"userLevel":   3,
	})
	resp3, err := http.Post(srv.URL+"/api/v1/auth/join-pin", "application/json", bytes.NewReader(joinBody3))
	if err != nil {
		t.Fatalf("join-pin lỗi: %v", err)
	}
	var res3 struct {
		UserID string `json:"userId"`
	}
	_ = json.NewDecoder(resp3.Body).Decode(&res3)
	resp3.Body.Close()

	// 2. Join pin as Level 1 (Director)
	joinBody1, _ := json.Marshal(map[string]interface{}{
		"pinCode":     "240826",
		"displayName": "Tổng Đạo Diễn A",
		"userLevel":   1,
	})
	resp1, err := http.Post(srv.URL+"/api/v1/auth/join-pin", "application/json", bytes.NewReader(joinBody1))
	if err != nil {
		t.Fatalf("join-pin lỗi: %v", err)
	}
	var res1 struct {
		UserID string `json:"userId"`
	}
	_ = json.NewDecoder(resp1.Body).Decode(&res1)
	resp1.Body.Close()

	// 3. Level 3 tries to fire cue via HTTP -> 403 FORBIDDEN_NOT_ADMIN
	fireBody3, _ := json.Marshal(map[string]interface{}{
		"userId":  res3.UserID,
		"eventId": 1,
		"title":   "Tiết mục mới",
		"message": "Nội dung mới",
	})
	fireResp3, err := http.Post(srv.URL+"/api/v1/live-cue/fire", "application/json", bytes.NewReader(fireBody3))
	if err != nil {
		t.Fatalf("POST /api/v1/live-cue/fire lỗi: %v", err)
	}
	defer fireResp3.Body.Close()
	if fireResp3.StatusCode != http.StatusForbidden {
		t.Fatalf("mong đợi 403 khi Level 3 gọi fire cue, nhận %d", fireResp3.StatusCode)
	}

	// 4. Level 1 fires cue (empty cueId -> creates new cue) via HTTP -> 200 OK
	fireBody1, _ := json.Marshal(map[string]interface{}{
		"userId":  res1.UserID,
		"eventId": 1,
		"title":   "Tiết mục đặc biệt A7",
		"message": "Kỹ thuật: kiểm tra âm thanh",
	})
	fireResp1, err := http.Post(srv.URL+"/api/v1/live-cue/fire", "application/json", bytes.NewReader(fireBody1))
	if err != nil {
		t.Fatalf("POST /api/v1/live-cue/fire lỗi: %v", err)
	}
	defer fireResp1.Body.Close()
	if fireResp1.StatusCode != http.StatusOK {
		t.Fatalf("mong đợi 200 khi Level 1 gọi fire cue, nhận %d", fireResp1.StatusCode)
	}
	var snap struct {
		Type      string `json:"type"`
		ActiveCue struct {
			Title   string `json:"title"`
			Message string `json:"message"`
		} `json:"activeCue"`
	}
	if err := json.NewDecoder(fireResp1.Body).Decode(&snap); err != nil {
		t.Fatalf("decode snapshot lỗi: %v", err)
	}
	if snap.ActiveCue.Title != "Tiết mục đặc biệt A7" {
		t.Fatalf("mong đợi title 'Tiết mục đặc biệt A7', nhận %q", snap.ActiveCue.Title)
	}

	// 5. Test empty hub initialization
	emptyHub := week5livecue.NewEmptyLiveCueBannerHub(99)
	emptySnap := emptyHub.FireOrCreateModelCue("", "Đạo diễn", "", "")
	if emptySnap.Title != "" || emptySnap.ActiveCue != nil {
		t.Fatalf("empty hub snapshot phải rỗng, nhận title=%q activeCue=%v", emptySnap.Title, emptySnap.ActiveCue)
	}
}
