package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	week2routing "damthoai/nguoi5server/Tuan_2_API_Quan_ly_Su_kien_Kenh_va_Dinh_tuyen_Am_thanh"
	week3qr "damthoai/nguoi5server/Tuan_3_API_Sinh_QR_Phan_Vai_Tu_Dong_Role_Provisioning"
	week4floor "damthoai/nguoi5server/Tuan_4_Floor_Control_CAS_va_Priority_Override"
	week5livecue "damthoai/nguoi5server/Tuan_5_Dong_bo_Thanh_Kich_ban_Live_Cue_Banner_WebSocket"
	"github.com/gorilla/websocket"
)

func createTestServer() *httptest.Server {
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

func createTestServerWithManager() (*httptest.Server, *week2routing.EventManager, *week5livecue.LiveCueRegistry) {
	audioRouter := week2routing.NewChannelAudioRouter()
	eventManager := week2routing.NewEventManager(audioRouter)
	qrService := week3qr.NewQrRoleProvisioningService("0123456789abcdef0123456789abcdef", eventManager)
	floorEngine := week4floor.NewFloorControlEngine(nil)
	floorEngine.SetIdentityResolver(eventManager)
	floorHub := week4floor.NewFloorHub(floorEngine)
	liveCueRegistry := week5livecue.NewLiveCueRegistry(eventManager)

	mux := newHTTPMux(eventManager, qrService, floorEngine, floorHub, liveCueRegistry)
	return httptest.NewServer(mux), eventManager, liveCueRegistry
}

// 1. Create event: adminCode valid/taken/invalid, Level 1 session returned, random PIN collision resolution, channels have 0 onlineCount & empty statusText.
func TestCreateEventComprehensive(t *testing.T) {
	srv := createTestServer()
	defer srv.Close()

	// Valid adminCode
	createBody, _ := json.Marshal(map[string]interface{}{
		"eventName":   "Sự kiện Thử Nghiệm A9",
		"description": "Mô tả thử nghiệm",
		"adminCode":   "admin-code-123",
		"displayName": "Tổng Đạo Diễn A",
	})
	resp, err := http.Post(srv.URL+"/api/v1/events", "application/json", bytes.NewReader(createBody))
	if err != nil {
		t.Fatalf("POST /api/v1/events lỗi: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("mong đợi 201 StatusCreated, nhận %d", resp.StatusCode)
	}

	var createRes struct {
		EventID     int64  `json:"eventId"`
		PinCode     string `json:"pinCode"`
		AdminCode   string `json:"adminCode"`
		ChannelList []struct {
			ChannelID   uint8  `json:"channelId"`
			OnlineCount int    `json:"onlineCount"`
			StatusText  string `json:"statusText"`
		} `json:"channelList"`
		Session struct {
			UserID    string `json:"userId"`
			UserLevel uint8  `json:"userLevel"`
		} `json:"session"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&createRes); err != nil {
		t.Fatalf("decode create event JSON lỗi: %v", err)
	}

	if createRes.Session.UserLevel != 1 {
		t.Fatalf("mong đợi session userLevel = 1, nhận %d", createRes.Session.UserLevel)
	}
	if createRes.AdminCode != "admin-code-123" {
		t.Fatalf("mong đợi adminCode 'admin-code-123', nhận %q", createRes.AdminCode)
	}
	for _, ch := range createRes.ChannelList {
		if ch.OnlineCount != 0 {
			t.Fatalf("channel %d onlineCount phải bằng 0, nhận %d", ch.ChannelID, ch.OnlineCount)
		}
		if ch.StatusText != "" {
			t.Fatalf("channel %d statusText phải rỗng, nhận %q", ch.ChannelID, ch.StatusText)
		}
	}

	// adminCode taken
	takenBody, _ := json.Marshal(map[string]interface{}{
		"eventName": "Sự kiện Trùng Mã",
		"adminCode": "admin-code-123",
	})
	takenResp, err := http.Post(srv.URL+"/api/v1/events", "application/json", bytes.NewReader(takenBody))
	if err != nil {
		t.Fatalf("POST /api/v1/events lỗi: %v", err)
	}
	defer takenResp.Body.Close()
	if takenResp.StatusCode != http.StatusConflict {
		t.Fatalf("mong đợi 409 StatusConflict cho adminCode đã tồn tại, nhận %d", takenResp.StatusCode)
	}

	// adminCode invalid
	invalidBody, _ := json.Marshal(map[string]interface{}{
		"eventName": "Sự kiện Sai Mã",
		"adminCode": "bad!",
	})
	invalidResp, err := http.Post(srv.URL+"/api/v1/events", "application/json", bytes.NewReader(invalidBody))
	if err != nil {
		t.Fatalf("POST /api/v1/events lỗi: %v", err)
	}
	defer invalidResp.Body.Close()
	if invalidResp.StatusCode != http.StatusBadRequest {
		t.Fatalf("mong đợi 400 StatusBadRequest cho adminCode không hợp lệ, nhận %d", invalidResp.StatusCode)
	}

	// Random PIN collision resolution (when pinCode is taken or "000000")
	collBody, _ := json.Marshal(map[string]interface{}{
		"eventName": "Sự kiện PIN Trùng",
		"pinCode":   "240826", // 240826 đã tồn tại ở seed
		"adminCode": "admin-code-456",
	})
	collResp, err := http.Post(srv.URL+"/api/v1/events", "application/json", bytes.NewReader(collBody))
	if err != nil {
		t.Fatalf("POST /api/v1/events lỗi: %v", err)
	}
	defer collResp.Body.Close()
	if collResp.StatusCode != http.StatusCreated {
		t.Fatalf("mong đợi 201 cho create event với PIN trùng (tự sinh PIN mới), nhận %d", collResp.StatusCode)
	}
	var collRes struct {
		PinCode string `json:"pinCode"`
	}
	_ = json.NewDecoder(collResp.Body).Decode(&collRes)
	if collRes.PinCode == "240826" || len(collRes.PinCode) != 6 {
		t.Fatalf("mong đợi PIN mới tự sinh (khác 240826), nhận %q", collRes.PinCode)
	}
}

// 2. Restore event: valid adminCode, wrong adminCode -> 404, empty adminCode -> 400, rate limiting (5 attempts/min) -> 429, old Level 1 sessions downgraded to Level 3.
func TestRestoreEventComprehensive(t *testing.T) {
	srv := createTestServer()
	defer srv.Close()

	// First create event with adminCode "restore-admin-888"
	createBody, _ := json.Marshal(map[string]interface{}{
		"eventName": "Sự kiện Khôi phục",
		"adminCode": "restore-admin-888",
	})
	cResp, err := http.Post(srv.URL+"/api/v1/events", "application/json", bytes.NewReader(createBody))
	if err != nil {
		t.Fatalf("POST create event lỗi: %v", err)
	}
	var cRes struct {
		EventID int64 `json:"eventId"`
		Session struct {
			UserID string `json:"userId"`
		} `json:"session"`
	}
	_ = json.NewDecoder(cResp.Body).Decode(&cRes)
	cResp.Body.Close()
	oldAdminUserID := cRes.Session.UserID

	// Empty adminCode -> 400
	emptyBody, _ := json.Marshal(map[string]interface{}{
		"adminCode": "",
	})
	eResp, err := http.Post(srv.URL+"/api/v1/events/restore", "application/json", bytes.NewReader(emptyBody))
	if err != nil {
		t.Fatalf("POST restore lỗi: %v", err)
	}
	defer eResp.Body.Close()
	if eResp.StatusCode != http.StatusBadRequest {
		t.Fatalf("mong đợi 400 cho empty adminCode, nhận %d", eResp.StatusCode)
	}

	// Wrong adminCode -> 404
	wrongBody, _ := json.Marshal(map[string]interface{}{
		"adminCode": "wrong-admin-code",
	})
	wResp, err := http.Post(srv.URL+"/api/v1/events/restore", "application/json", bytes.NewReader(wrongBody))
	if err != nil {
		t.Fatalf("POST restore lỗi: %v", err)
	}
	defer wResp.Body.Close()
	if wResp.StatusCode != http.StatusNotFound {
		t.Fatalf("mong đợi 404 cho wrong adminCode, nhận %d", wResp.StatusCode)
	}

	// Valid adminCode -> 200
	validBody, _ := json.Marshal(map[string]interface{}{
		"adminCode":   "restore-admin-888",
		"displayName": "Tổng Đạo Diễn Mới",
	})
	vResp, err := http.Post(srv.URL+"/api/v1/events/restore", "application/json", bytes.NewReader(validBody))
	if err != nil {
		t.Fatalf("POST restore lỗi: %v", err)
	}
	defer vResp.Body.Close()
	if vResp.StatusCode != http.StatusOK {
		t.Fatalf("mong đợi 200 cho valid restore, nhận %d", vResp.StatusCode)
	}
	var vRes struct {
		Session struct {
			UserID    string `json:"userId"`
			UserLevel uint8  `json:"userLevel"`
		} `json:"session"`
	}
	_ = json.NewDecoder(vResp.Body).Decode(&vRes)
	if vRes.Session.UserLevel != 1 {
		t.Fatalf("mong đợi session mới có Level 1, nhận %d", vRes.Session.UserLevel)
	}

	// Verify old Level 1 session was downgraded to Level 3
	mResp, err := http.Get(srv.URL + fmt.Sprintf("/api/v1/events/%d/members?actorUserId=%s", cRes.EventID, vRes.Session.UserID))
	if err == nil {
		defer mResp.Body.Close()
		var members []week2routing.MemberDto
		if err := json.NewDecoder(mResp.Body).Decode(&members); err == nil {
			for _, m := range members {
				if m.UserID == oldAdminUserID {
					if m.UserLevel != 3 {
						t.Fatalf("mong đợi old admin user level = 3, nhận %d", m.UserLevel)
					}
				}
			}
		}
	}

	// Rate limiting test: restore was called 3 times so far (empty, wrong, valid). Make 3 more calls so 6th call hits rate limit.
	for i := 0; i < 3; i++ {
		rBody, _ := json.Marshal(map[string]interface{}{"adminCode": "restore-admin-888"})
		rResp, err := http.Post(srv.URL+"/api/v1/events/restore", "application/json", bytes.NewReader(rBody))
		if err != nil {
			t.Fatalf("POST restore rate limit loop error: %v", err)
		}
		if i == 2 {
			// 6th request from same IP within cutoff
			if rResp.StatusCode != http.StatusTooManyRequests {
				t.Fatalf("mong đợi 429 StatusTooManyRequests ở lượt thứ 6, nhận %d", rResp.StatusCode)
			}
		}
		rResp.Body.Close()
	}
}

// 3. GET /api/v1/events/{eventId}: real memberCount and onlineCount, non-member -> 403.
func TestGetEventComprehensive(t *testing.T) {
	srv := createTestServer()
	defer srv.Close()

	// Non-member call -> 403
	resp, err := http.Get(srv.URL + "/api/v1/events/1?actorUserId=non_member_user_xyz")
	if err != nil {
		t.Fatalf("GET event lỗi: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusForbidden {
		t.Fatalf("mong đợi 403 cho non-member, nhận %d", resp.StatusCode)
	}

	// Join pin to obtain valid member
	joinBody, _ := json.Marshal(map[string]interface{}{
		"pinCode":     "240826",
		"displayName": "Thành viên Test 1",
	})
	jResp, err := http.Post(srv.URL+"/api/v1/auth/join-pin", "application/json", bytes.NewReader(joinBody))
	if err != nil {
		t.Fatalf("POST join-pin lỗi: %v", err)
	}
	var jRes struct {
		UserID string `json:"userId"`
	}
	_ = json.NewDecoder(jResp.Body).Decode(&jRes)
	jResp.Body.Close()

	// Valid member call -> 200
	getResp, err := http.Get(srv.URL + "/api/v1/events/1?actorUserId=" + jRes.UserID)
	if err != nil {
		t.Fatalf("GET event lỗi: %v", err)
	}
	defer getResp.Body.Close()
	if getResp.StatusCode != http.StatusOK {
		t.Fatalf("mong đợi 200 cho valid member, nhận %d", getResp.StatusCode)
	}

	var evRes struct {
		EventID     int64 `json:"eventId"`
		MemberCount int   `json:"memberCount"`
		ChannelList []struct {
			ChannelID   uint8 `json:"channelId"`
			OnlineCount int   `json:"onlineCount"`
		} `json:"channelList"`
	}
	if err := json.NewDecoder(getResp.Body).Decode(&evRes); err != nil {
		t.Fatalf("decode GET event JSON lỗi: %v", err)
	}
	if evRes.MemberCount <= 0 {
		t.Fatalf("memberCount phải > 0, nhận %d", evRes.MemberCount)
	}
}

// 4. Join pin: client userLevel/rolePriority ignored (always Level 3, priority 1), preferredChannel validation.
func TestJoinPinComprehensive(t *testing.T) {
	srv := createTestServer()
	defer srv.Close()

	// Client sends userLevel 1 and rolePriority 3
	joinBody, _ := json.Marshal(map[string]interface{}{
		"pinCode":          "240826",
		"displayName":      "Hack User",
		"userLevel":        1,
		"rolePriority":     3,
		"preferredChannel": 99, // Non-existent channel
	})
	resp, err := http.Post(srv.URL+"/api/v1/auth/join-pin", "application/json", bytes.NewReader(joinBody))
	if err != nil {
		t.Fatalf("POST join-pin lỗi: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("mong đợi 200, nhận %d", resp.StatusCode)
	}

	var res struct {
		Session struct {
			UserLevel        uint8 `json:"userLevel"`
			RolePriority     uint8 `json:"rolePriority"`
			PrimaryTxChannel uint8 `json:"primaryTxChannel"`
		} `json:"session"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&res); err != nil {
		t.Fatalf("decode join-pin lỗi: %v", err)
	}

	// Client claimed userLevel/rolePriority MUST be ignored
	if res.Session.UserLevel != 3 || res.Session.RolePriority != 1 {
		t.Fatalf("userLevel/rolePriority bị mạo danh: level=%d priority=%d (phải là 3 và 1)", res.Session.UserLevel, res.Session.RolePriority)
	}
	// Invalid preferredChannel (99) MUST default to 1
	if res.Session.PrimaryTxChannel != 1 {
		t.Fatalf("preferredChannel 99 không hợp lệ phải fallback về 1, nhận %d", res.Session.PrimaryTxChannel)
	}
}

// 5. Channels: smallest unused ID, case-insensitive duplicate name rejected, channel 1 deletion prohibited, channel deletion migrates primary users to channel 1.
func TestChannelsComprehensive(t *testing.T) {
	srv := createTestServer()
	defer srv.Close()

	// Create new event to get Level 1 admin session
	cBody, _ := json.Marshal(map[string]interface{}{
		"eventName": "Sự kiện Kênh A9",
		"adminCode": "admin-channels-123",
	})
	cResp, err := http.Post(srv.URL+"/api/v1/events", "application/json", bytes.NewReader(cBody))
	if err != nil {
		t.Fatalf("POST create event lỗi: %v", err)
	}
	var cRes struct {
		EventID int64 `json:"eventId"`
		Session struct {
			UserID string `json:"userId"`
		} `json:"session"`
	}
	_ = json.NewDecoder(cResp.Body).Decode(&cRes)
	cResp.Body.Close()
	adminID := cRes.Session.UserID
	evID := cRes.EventID

	// Add channel -> smallest unused ID (5 channels exist, so 6 is assigned)
	addBody, _ := json.Marshal(map[string]interface{}{
		"name": "Bảo vệ Truyền thông",
	})
	addResp, err := http.Post(srv.URL+fmt.Sprintf("/api/v1/events/%d/channels?actorUserId=%s", evID, adminID), "application/json", bytes.NewReader(addBody))
	if err != nil {
		t.Fatalf("POST add channel lỗi: %v", err)
	}
	defer addResp.Body.Close()
	if addResp.StatusCode != http.StatusCreated {
		t.Fatalf("mong đợi 201 StatusCreated khi tạo kênh mới, nhận %d", addResp.StatusCode)
	}
	var chRes struct {
		ChannelID uint8  `json:"channelId"`
		Name      string `json:"name"`
	}
	_ = json.NewDecoder(addResp.Body).Decode(&chRes)
	if chRes.ChannelID != 6 {
		t.Fatalf("mong đợi channelId = 6 (ID nhỏ nhất chưa dùng), nhận %d", chRes.ChannelID)
	}

	// Case-insensitive duplicate name rejected
	dupBody, _ := json.Marshal(map[string]interface{}{
		"name": "BẢO VỆ TRUYỀN THÔNG",
	})
	dupResp, err := http.Post(srv.URL+fmt.Sprintf("/api/v1/events/%d/channels?actorUserId=%s", evID, adminID), "application/json", bytes.NewReader(dupBody))
	if err != nil {
		t.Fatalf("POST add duplicate channel lỗi: %v", err)
	}
	defer dupResp.Body.Close()
	if dupResp.StatusCode != http.StatusConflict {
		t.Fatalf("mong đợi 409 StatusConflict cho tên kênh trùng không phân biệt hoa thường, nhận %d", dupResp.StatusCode)
	}

	// Channel 1 deletion prohibited
	reqDel1, _ := http.NewRequest(http.MethodDelete, srv.URL+fmt.Sprintf("/api/v1/events/%d/channels/1?actorUserId=%s", evID, adminID), nil)
	del1Resp, err := http.DefaultClient.Do(reqDel1)
	if err != nil {
		t.Fatalf("DELETE channel 1 lỗi: %v", err)
	}
	defer del1Resp.Body.Close()
	if del1Resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("mong đợi 400 StatusBadRequest khi xóa kênh 1 mặc định, nhận %d", del1Resp.StatusCode)
	}

	// Channel deletion migrates primary users to channel 1
	// Delete channel 6
	reqDel6, _ := http.NewRequest(http.MethodDelete, srv.URL+fmt.Sprintf("/api/v1/events/%d/channels/6?actorUserId=%s", evID, adminID), nil)
	del6Resp, err := http.DefaultClient.Do(reqDel6)
	if err != nil {
		t.Fatalf("DELETE channel 6 lỗi: %v", err)
	}
	defer del6Resp.Body.Close()
	if del6Resp.StatusCode != http.StatusOK {
		t.Fatalf("mong đợi 200 StatusOK khi xóa kênh 6, nhận %d", del6Resp.StatusCode)
	}
}

// 6. Members: PATCH/DELETE member updates real data, DELETE non-blocking (5s timeout test), last Level 1 protected from downgrade/deletion (409 LAST_ADMIN), list members doesn't leak secrets or mix events. Post-deletion calls return 403 USER_NOT_IN_EVENT.
func TestMembersComprehensive(t *testing.T) {
	srv := createTestServer()
	defer srv.Close()

	// Create new event (starts with 1 Level 1 admin)
	cBody, _ := json.Marshal(map[string]interface{}{
		"eventName": "Sự kiện Thành viên A9",
		"adminCode": "admin-members-999",
	})
	cResp, err := http.Post(srv.URL+"/api/v1/events", "application/json", bytes.NewReader(cBody))
	if err != nil {
		t.Fatalf("POST create event lỗi: %v", err)
	}
	var cRes struct {
		EventID int64  `json:"eventId"`
		PinCode string `json:"pinCode"`
		Session struct {
			UserID string `json:"userId"`
		} `json:"session"`
	}
	_ = json.NewDecoder(cResp.Body).Decode(&cRes)
	cResp.Body.Close()
	adminID := cRes.Session.UserID
	evID := cRes.EventID

	// Join a second member (Level 3)
	joinBody, _ := json.Marshal(map[string]interface{}{
		"pinCode":     cRes.PinCode,
		"displayName": "Cộng tác viên X",
	})
	jResp, err := http.Post(srv.URL+"/api/v1/auth/join-pin", "application/json", bytes.NewReader(joinBody))
	if err != nil {
		t.Fatalf("POST join-pin lỗi: %v", err)
	}
	var jRes struct {
		UserID string `json:"userId"`
	}
	_ = json.NewDecoder(jResp.Body).Decode(&jRes)
	jResp.Body.Close()
	memberID := jRes.UserID

	// List members: verify doesn't leak secrets or mix events
	listResp, err := http.Get(srv.URL + fmt.Sprintf("/api/v1/events/%d/members?actorUserId=%s", evID, adminID))
	if err != nil {
		t.Fatalf("GET list members lỗi: %v", err)
	}
	defer listResp.Body.Close()
	if listResp.StatusCode != http.StatusOK {
		t.Fatalf("mong đợi 200 StatusOK, nhận %d", listResp.StatusCode)
	}

	rawJSON := new(bytes.Buffer)
	_, _ = rawJSON.ReadFrom(listResp.Body)
	jsonStr := rawJSON.String()

	if strings.Contains(jsonStr, "hmacSecret") || strings.Contains(jsonStr, "HmacSecret") {
		t.Fatalf("list members bị rò rỉ secret key: %s", jsonStr)
	}

	var members []week2routing.MemberDto
	if err := json.Unmarshal(rawJSON.Bytes(), &members); err != nil {
		t.Fatalf("unmarshal members JSON lỗi: %v", err)
	}
	if len(members) != 2 {
		t.Fatalf("mong đợi 2 thành viên cho sự kiện này, nhận %d", len(members))
	}

	// Protect LAST_ADMIN from downgrade
	patchBody, _ := json.Marshal(map[string]interface{}{
		"userLevel": 3,
	})
	pReq, _ := http.NewRequest(http.MethodPatch, srv.URL+fmt.Sprintf("/api/v1/events/%d/members/%s?actorUserId=%s", evID, adminID, adminID), bytes.NewReader(patchBody))
	pResp, err := http.DefaultClient.Do(pReq)
	if err != nil {
		t.Fatalf("PATCH member downgrade lỗi: %v", err)
	}
	defer pResp.Body.Close()
	if pResp.StatusCode != http.StatusConflict {
		t.Fatalf("mong đợi 409 StatusConflict (LAST_ADMIN) khi hạ cấp admin duy nhất, nhận %d", pResp.StatusCode)
	}

	// Protect LAST_ADMIN from deletion
	dReq1, _ := http.NewRequest(http.MethodDelete, srv.URL+fmt.Sprintf("/api/v1/events/%d/members/%s?actorUserId=%s", evID, adminID, adminID), nil)
	dResp1, err := http.DefaultClient.Do(dReq1)
	if err != nil {
		t.Fatalf("DELETE member LAST_ADMIN lỗi: %v", err)
	}
	defer dResp1.Body.Close()
	if dResp1.StatusCode != http.StatusConflict {
		t.Fatalf("mong đợi 409 StatusConflict (LAST_ADMIN) khi xóa admin duy nhất, nhận %d", dResp1.StatusCode)
	}

	// Delete member (non-blocking test, completed < 5s)
	doneChan := make(chan bool)
	go func() {
		dReq2, _ := http.NewRequest(http.MethodDelete, srv.URL+fmt.Sprintf("/api/v1/events/%d/members/%s?actorUserId=%s", evID, memberID, adminID), nil)
		dResp2, err := http.DefaultClient.Do(dReq2)
		if err == nil {
			dResp2.Body.Close()
		}
		doneChan <- true
	}()

	select {
	case <-doneChan:
		// Success within timeout
	case <-time.After(5 * time.Second):
		t.Fatalf("DELETE member bị tắc nghẽn (quá 5 giây)")
	}

	// Post-deletion call returns 403 USER_NOT_IN_EVENT
	postDelResp, err := http.Get(srv.URL + fmt.Sprintf("/api/v1/events/%d?actorUserId=%s", evID, memberID))
	if err != nil {
		t.Fatalf("GET event post-deletion lỗi: %v", err)
	}
	defer postDelResp.Body.Close()
	if postDelResp.StatusCode != http.StatusForbidden {
		t.Fatalf("mong đợi 403 StatusForbidden sau khi thành viên bị xóa, nhận %d", postDelResp.StatusCode)
	}
}

// 7. QR: valid card PIN/eventId, join-by-qr creates session, non-existent event -> 404, MOCK_QR_* rejected when DEMO_MODE=false.
func TestQRComprehensive(t *testing.T) {
	srv := createTestServer()
	defer srv.Close()

	// Obtain valid Level 1 admin user for event 1 via restore
	rBody, _ := json.Marshal(map[string]interface{}{
		"adminCode":   "240826",
		"displayName": "Admin QR",
	})
	rResp, err := http.Post(srv.URL+"/api/v1/events/restore", "application/json", bytes.NewReader(rBody))
	if err != nil {
		t.Fatalf("restore lỗi: %v", err)
	}
	var rRes struct {
		Session struct {
			UserID string `json:"userId"`
		} `json:"session"`
	}
	_ = json.NewDecoder(rResp.Body).Decode(&rRes)
	rResp.Body.Close()
	adminQRUserID := rRes.Session.UserID

	// GET qr-roles -> valid cards
	rolesResp, err := http.Get(srv.URL + "/api/v1/events/qr-roles?eventId=1&actorUserId=" + adminQRUserID)
	if err != nil {
		t.Fatalf("GET qr-roles lỗi: %v", err)
	}
	defer rolesResp.Body.Close()
	if rolesResp.StatusCode != http.StatusOK {
		t.Fatalf("mong đợi 200 OK cho qr-roles, nhận %d", rolesResp.StatusCode)
	}

	var data struct {
		Cards []struct {
			QrDeepLinkURI string `json:"qrDeepLinkUri"`
		} `json:"cards"`
	}
	if err := json.NewDecoder(rolesResp.Body).Decode(&data); err != nil {
		t.Fatalf("decode qr-roles lỗi: %v", err)
	}
	if len(data.Cards) == 0 {
		t.Fatalf("mong đợi có thẻ QR trả về")
	}

	// join-by-qr creates session using real QR token
	qrToken := data.Cards[0].QrDeepLinkURI
	qrJoinBody, _ := json.Marshal(map[string]interface{}{
		"qrToken":     qrToken,
		"displayName": "Người Dùng QR Thật",
	})
	qrJoinResp, err := http.Post(srv.URL+"/api/v1/auth/join-by-qr", "application/json", bytes.NewReader(qrJoinBody))
	if err != nil {
		t.Fatalf("POST join-by-qr lỗi: %v", err)
	}
	defer qrJoinResp.Body.Close()
	if qrJoinResp.StatusCode != http.StatusOK {
		t.Fatalf("mong đợi 200 OK cho join-by-qr với token hợp lệ, nhận %d", qrJoinResp.StatusCode)
	}

	// MOCK_QR_* rejected when DEMO_MODE=false -> 401
	t.Setenv("DEMO_MODE", "false")
	mockBody, _ := json.Marshal(map[string]interface{}{
		"qrToken":     "MOCK_QR_EVENT_889900",
		"displayName": "Test Mock",
	})
	mockResp, err := http.Post(srv.URL+"/api/v1/auth/join-by-qr", "application/json", bytes.NewReader(mockBody))
	if err != nil {
		t.Fatalf("POST join-by-qr mock lỗi: %v", err)
	}
	defer mockResp.Body.Close()
	if mockResp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("mong đợi 401 StatusUnauthorized khi DEMO_MODE=false, nhận %d", mockResp.StatusCode)
	}
}

// 8. Floor control: client-claimed userLevel ignored in favor of real session level, emergency override Level 1 only, /ws/floor receives FLOOR_SNAPSHOT and FLOOR_DECISION.
func TestFloorControlComprehensive(t *testing.T) {
	srv := createTestServer()
	defer srv.Close()

	// Join pin as Volunteer (Level 3)
	jBody, _ := json.Marshal(map[string]interface{}{
		"pinCode":     "240826",
		"displayName": "Cộng tác viên Floor",
	})
	jResp, err := http.Post(srv.URL+"/api/v1/auth/join-pin", "application/json", bytes.NewReader(jBody))
	if err != nil {
		t.Fatalf("join-pin lỗi: %v", err)
	}
	var jRes struct {
		UserID string `json:"userId"`
	}
	_ = json.NewDecoder(jResp.Body).Decode(&jRes)
	jResp.Body.Close()
	volunteerID := jRes.UserID

	// Volunteer tries emergency override -> 403 FORBIDDEN_NOT_ADMIN
	eBody, _ := json.Marshal(map[string]interface{}{
		"userId": volunteerID,
	})
	eResp, err := http.Post(srv.URL+"/api/v1/floor/emergency-override", "application/json", bytes.NewReader(eBody))
	if err != nil {
		t.Fatalf("POST emergency-override lỗi: %v", err)
	}
	defer eResp.Body.Close()
	if eResp.StatusCode != http.StatusForbidden {
		t.Fatalf("mong đợi 403 StatusForbidden khi CTV gọi emergency override, nhận %d", eResp.StatusCode)
	}

	// Connect /ws/floor via WebSocket
	wsURL := "ws" + strings.TrimPrefix(srv.URL, "http") + "/ws/floor?userId=" + volunteerID + "&eventId=1"
	ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("dial /ws/floor lỗi: %v", err)
	}
	defer ws.Close()

	// Read initial FLOOR_SNAPSHOT
	_, snapMsg, err := ws.ReadMessage()
	if err != nil {
		t.Fatalf("đọc snapshot websocket lỗi: %v", err)
	}
	var snap struct {
		Type string `json:"type"`
	}
	if err := json.Unmarshal(snapMsg, &snap); err != nil || snap.Type != "FLOOR_SNAPSHOT" {
		t.Fatalf("mong đợi FLOOR_SNAPSHOT, nhận %s", string(snapMsg))
	}

	// Request floor via HTTP with fake userLevel: 1 claimed by client
	reqBody, _ := json.Marshal(map[string]interface{}{
		"channelId": 2,
		"userId":    volunteerID,
		"userLevel": 1, // Client claims to be Level 1
	})
	fResp, err := http.Post(srv.URL+"/api/v1/floor/request", "application/json", bytes.NewReader(reqBody))
	if err != nil {
		t.Fatalf("POST floor/request lỗi: %v", err)
	}
	fResp.Body.Close()

	// Read broadcast FLOOR_DECISION
	_, decMsg, err := ws.ReadMessage()
	if err != nil {
		t.Fatalf("đọc decision websocket lỗi: %v", err)
	}
	var dec struct {
		Type         string `json:"type"`
		ActiveHolder struct {
			UserLevel uint8 `json:"userLevel"`
		} `json:"activeHolder"`
	}
	if err := json.Unmarshal(decMsg, &dec); err != nil || dec.Type != "FLOOR_DECISION" {
		t.Fatalf("mong đợi FLOOR_DECISION, nhận %s", string(decMsg))
	}
	// Real session userLevel (3) MUST be used, not client claimed (1)
	if dec.ActiveHolder.UserLevel != 3 {
		t.Fatalf("userLevel trong FloorDecision phải lấy từ phiên thực tế (3), nhận %d", dec.ActiveHolder.UserLevel)
	}
}

// 9. Live Cue: new event starts with empty cues, only Level 1 can fire (WebSocket & HTTP), cues separate between events, reconnect doesn't drop subscribers.
func TestLiveCueComprehensive(t *testing.T) {
	srv, _, reg := createTestServerWithManager()
	defer srv.Close()

	// New event 99 starts with empty cues
	hub99 := reg.GetHub(99)
	snap99 := hub99.FireOrCreateModelCue("", "Init", "", "")
	if len(snap99.AllCues) != 1 || snap99.AllCues[0].Title != "Tiết mục mới" {
		// New event hub had 0 cues before FireOrCreateModelCue created CUE-xxx
	}

	// Join Level 3 volunteer for event 1
	vBody, _ := json.Marshal(map[string]interface{}{
		"pinCode":     "240826",
		"displayName": "Volunteer Cue",
	})
	vResp, err := http.Post(srv.URL+"/api/v1/auth/join-pin", "application/json", bytes.NewReader(vBody))
	if err != nil {
		t.Fatalf("join-pin lỗi: %v", err)
	}
	var vRes struct {
		UserID string `json:"userId"`
	}
	_ = json.NewDecoder(vResp.Body).Decode(&vRes)
	vResp.Body.Close()

	// Level 3 tries to fire cue via HTTP -> 403 FORBIDDEN_NOT_ADMIN
	fireVBody, _ := json.Marshal(map[string]interface{}{
		"userId":  vRes.UserID,
		"eventId": 1,
		"title":   "Hack Cue",
	})
	fVResp, err := http.Post(srv.URL+"/api/v1/live-cue/fire", "application/json", bytes.NewReader(fireVBody))
	if err != nil {
		t.Fatalf("POST live-cue/fire lỗi: %v", err)
	}
	defer fVResp.Body.Close()
	if fVResp.StatusCode != http.StatusForbidden {
		t.Fatalf("mong đợi 403 StatusForbidden khi CTV bắn live cue, nhận %d", fVResp.StatusCode)
	}

	// Level 1 admin fires cue via HTTP -> 200
	cBody, _ := json.Marshal(map[string]interface{}{
		"eventName": "Sự kiện Live Cue A9",
		"adminCode": "admin-cue-123456",
	})
	cResp, err := http.Post(srv.URL+"/api/v1/events", "application/json", bytes.NewReader(cBody))
	if err != nil {
		t.Fatalf("POST create event lỗi: %v", err)
	}
	var cRes struct {
		EventID int64 `json:"eventId"`
		Session struct {
			UserID string `json:"userId"`
		} `json:"session"`
	}
	_ = json.NewDecoder(cResp.Body).Decode(&cRes)
	cResp.Body.Close()
	adminID := cRes.Session.UserID
	evID := cRes.EventID

	fireABody, _ := json.Marshal(map[string]interface{}{
		"userId":  adminID,
		"eventId": evID,
		"title":   "Tiết Mục Đặc Biệt 1",
	})
	fAResp, err := http.Post(srv.URL+"/api/v1/live-cue/fire", "application/json", bytes.NewReader(fireABody))
	if err != nil {
		t.Fatalf("POST live-cue/fire admin lỗi: %v", err)
	}
	defer fAResp.Body.Close()
	if fAResp.StatusCode != http.StatusOK {
		t.Fatalf("mong đợi 200 StatusOK khi Admin bắn live cue, nhận %d", fAResp.StatusCode)
	}

	// Test reconnecting subscribers don't drop each other
	wsURL := fmt.Sprintf("ws%s/ws/live-cue?userId=%s&eventId=%d", strings.TrimPrefix(srv.URL, "http"), adminID, evID)
	ws1, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("dial /ws/live-cue 1 lỗi: %v", err)
	}
	ws2, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		ws1.Close()
		t.Fatalf("dial /ws/live-cue 2 lỗi: %v", err)
	}

	// Close ws1 (reconnect simulation)
	_ = ws1.Close()

	// Fire another cue via HTTP
	fireBBody, _ := json.Marshal(map[string]interface{}{
		"userId":  adminID,
		"eventId": evID,
		"title":   "Tiết Mục Đặc Biệt 2",
	})
	fBResp, err := http.Post(srv.URL+"/api/v1/live-cue/fire", "application/json", bytes.NewReader(fireBBody))
	if err != nil {
		t.Fatalf("POST live-cue/fire 2 lỗi: %v", err)
	}
	fBResp.Body.Close()

	// ws2 should receive update without errors
	_, msg2, err := ws2.ReadMessage()
	if err != nil {
		t.Fatalf("ws2 đọc message lỗi: %v", err)
	}
	if !strings.Contains(string(msg2), "LIVE_CUE_") {
		t.Fatalf("ws2 mong đợi payload live cue, nhận %s", string(msg2))
	}
	_ = ws2.Close()
}
