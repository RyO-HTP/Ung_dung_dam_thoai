package week2routing

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"math/big"
	"net/http"
	"sync"
	"sync/atomic"
	"time"
)

// =============================================================================
// TUẦN 2 (NGƯỜI 5): XÂY DỰNG REST API QUẢN LÝ NGƯỜI DÙNG, SỰ KIỆN, KÊNH
// Đã đồng bộ 100% với dự án Android DuAnLamChung:
//   - CreateEventScreen.kt & OnboardingScreen.kt (PIN: "240826")
//   - JoinChannelScreen.kt & FakeRepositories.kt (PIN: "889900", chặn PIN "000000" -> JoinErrorScreen)
//   - ChannelListScreen.kt (5 kênh ch_01..ch_05, UserLevel Cấp 1/2/3 <-> RolePriority 3/2/1)
// =============================================================================

type ChannelInfo struct {
	ID              string `json:"id"`              // Khớp với ChannelItem.id trên Android ("ch_01".."ch_05")
	ChannelID       uint8  `json:"channelId"`       // ID số nguyên (1..5) dùng trong Header UDP 18-byte
	Name            string `json:"name"`            // Tên kênh khớp giao diện Android
	DepartmentCode  string `json:"departmentCode"`  // Mã ban chuyên môn
	DefaultPriority uint8  `json:"defaultPriority"` // Độ ưu tiên mặc định (3=Cao nhất, 2=Trưởng ban, 1=CTV)
	OnlineCount     int    `json:"onlineCount"`     // Số thành viên đang trực tuyến
	StatusText      string `json:"statusText"`      // Trạng thái hiển thị trên thẻ kênh Android
}

type EventInfo struct {
	EventID     int64                  `json:"eventId"`
	EventName   string                 `json:"eventName"`
	Description string                 `json:"description"`
	PinCode     string                 `json:"pinCode"`
	CreatedAt   time.Time              `json:"createdAt"`
	Channels    map[uint8]*ChannelInfo `json:"channels"`
	ChannelList []*ChannelInfo         `json:"channelList"` // Mảng kênh tuần tự phục vụ LazyColumn trên Android
}

type UserSession struct {
	UserID            uint32   `json:"userId"`
	UserCode          string   `json:"userCode"`          // ID chuỗi trên Android (VD: "Minh_Hau")
	EventID           int64    `json:"eventId"`
	DisplayName       string   `json:"displayName"`       // VD: "Minh Hậu"
	RoleTitle         string   `json:"roleTitle"`         // VD: "Trưởng ban Sân khấu", "Trưởng ban Hậu cần"
	UserLevel         uint8    `json:"userLevel"`         // Chuẩn UI Android: 1 = Tổng đạo diễn, 2 = Trưởng ban, 3 = CTV
	RolePriority      uint8    `json:"rolePriority"`      // Chuẩn Server CAS: 3 = Tổng đạo diễn, 2 = Trưởng ban, 1 = CTV
	PrimaryTxChannel  uint8    `json:"primaryTxChannel"`  // Kênh phát âm thanh chính (1..5)
	PrimaryChannelID  string   `json:"primaryChannelId"`  // Mã kênh phát âm thanh trên Android ("ch_01".."ch_05")
	ListeningChannels []uint8  `json:"listeningChannels"` // Danh sách kênh đang nghe (tối đa 3 kênh)
	HmacSecretHex     string   `json:"hmacSecretHex"`
	HmacSecretBytes   []byte   `json:"-"`
}

// ConvertUserLevelToPriority quy đổi giữa Cấp bậc trên giao diện Android (UserLevel 1,2,3)
// và Độ ưu tiên xử lý tranh chấp trên Server (RolePriority 3,2,1).
// Công thức: RolePriority = 4 - UserLevel
func ConvertUserLevelToPriority(userLevel uint8, rolePriority uint8) (level uint8, priority uint8, defaultTitle string) {
	if userLevel >= 1 && userLevel <= 3 {
		level = userLevel
		priority = 4 - userLevel
	} else if rolePriority >= 1 && rolePriority <= 3 {
		priority = rolePriority
		level = 4 - rolePriority
	} else {
		level = 2
		priority = 2
	}

	switch level {
	case 1:
		defaultTitle = "Tổng đạo diễn"
	case 2:
		defaultTitle = "Trưởng ban Sân khấu"
	default:
		defaultTitle = "Cộng tác viên"
	}
	return level, priority, defaultTitle
}

type EventManager struct {
	mu          sync.RWMutex
	eventsByPin map[string]*EventInfo
	usersByID   map[uint32]*UserSession
	userSeq     atomic.Uint32
	eventSeq    atomic.Int64
	audioRouter *ChannelAudioRouter
}

func NewEventManager(router *ChannelAudioRouter) *EventManager {
	em := &EventManager{
		eventsByPin: make(map[string]*EventInfo),
		usersByID:   make(map[uint32]*UserSession),
		audioRouter: router,
	}
	em.userSeq.Store(100)
	em.eventSeq.Store(3)

	// Khởi tạo sẵn 3 mã PIN khớp với toàn bộ kịch bản trong dự án DuAnLamChung:
	// 1. PIN "240826": Hiển thị trên OnboardingScreen.kt & CreateEventScreen.kt
	// 2. PIN "889900": Mặc định trong FakeRepositories.kt & JoinChannelScreen.kt
	// 3. PIN "482910": Mã dự phòng trong script Stress Test
	seedEvents := []struct {
		id   int64
		name string
		desc string
		pin  string
	}{
		{1, "Đêm nhạc Chào Tân sinh viên", "Sự kiện âm nhạc thường niên tại Hội trường A", "240826"},
		{2, "Đêm nhạc Chào Tân sinh viên", "Sự kiện âm nhạc thường niên tại Hội trường A (QR/PIN)", "889900"},
		{3, "Đêm Nhạc Acoustic 2026", "Sự kiện biểu diễn Acoustic Hội trường lớn", "482910"},
	}

	for _, item := range seedEvents {
		chMap, chList := createDefaultFiveChannels()
		em.eventsByPin[item.pin] = &EventInfo{
			EventID:     item.id,
			EventName:   item.name,
			Description: item.desc,
			PinCode:     item.pin,
			CreatedAt:   time.Now(),
			Channels:    chMap,
			ChannelList: chList,
		}
	}

	return em
}

// createDefaultFiveChannels tạo 5 kênh mặc định khớp 100% với CreateEventScreen.kt (dòng 138) và ChannelListScreen.kt.
func createDefaultFiveChannels() (map[uint8]*ChannelInfo, []*ChannelInfo) {
	list := []*ChannelInfo{
		{ID: "ch_01", ChannelID: 1, Name: "Toàn sự kiện", DepartmentCode: "ALL_EVENT", DefaultPriority: 3, OnlineCount: 48, StatusText: "Đạo diễn A đang nói · Ưu tiên Cấp 1"},
		{ID: "ch_02", ChannelID: 2, Name: "Sân khấu", DepartmentCode: "STAGE", DefaultPriority: 2, OnlineCount: 16, StatusText: "Đang nghe · Bấm giữ PTT để nói"},
		{ID: "ch_03", ChannelID: 3, Name: "Hậu cần – An ninh", DepartmentCode: "LOGISTICS_SEC", DefaultPriority: 2, OnlineCount: 12, StatusText: "Rảnh · 12 người trực tuyến"},
		{ID: "ch_04", ChannelID: 4, Name: "Kỹ thuật", DepartmentCode: "TECHNICAL", DefaultPriority: 2, OnlineCount: 8, StatusText: "Rảnh · 8 người trực tuyến"},
		{ID: "ch_05", ChannelID: 5, Name: "Đón tiếp", DepartmentCode: "RECEPTION", DefaultPriority: 1, OnlineCount: 10, StatusText: "Chưa tham gia · Bấm để vào kênh"},
	}
	chMap := make(map[uint8]*ChannelInfo, len(list))
	for _, ch := range list {
		chMap[ch.ChannelID] = ch
	}
	return chMap, list
}

// HandleCreateEvent xử lý POST /api/v1/events — Tạo sự kiện mới & sinh mã PIN 6 số.
func (em *EventManager) HandleCreateEvent(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		EventName   string `json:"eventName"`
		Description string `json:"description"`
		PinCode     string `json:"pinCode"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.EventName == "" {
		req.EventName = "Đêm nhạc Chào Tân sinh viên"
	}
	if req.Description == "" {
		req.Description = "Sự kiện điều phối bộ đàm đa kênh thời gian thực"
	}

	pinCode := req.PinCode
	if len(pinCode) != 6 || pinCode == "000000" {
		pinNum, _ := rand.Int(rand.Reader, big.NewInt(900000))
		pinCode = fmt.Sprintf("%06d", pinNum.Int64()+100000)
	}
	eventID := em.eventSeq.Add(1)
	chMap, chList := createDefaultFiveChannels()

	ev := &EventInfo{
		EventID:     eventID,
		EventName:   req.EventName,
		Description: req.Description,
		PinCode:     pinCode,
		CreatedAt:   time.Now(),
		Channels:    chMap,
		ChannelList: chList,
	}

	em.mu.Lock()
	em.eventsByPin[pinCode] = ev
	em.mu.Unlock()

	writeJSON(w, http.StatusCreated, ev)
}

// HandleJoinByPin xử lý POST /api/v1/auth/join-pin — Người dùng nhập PIN 6 số để tham gia sự kiện.
// Nếu nhập PIN "000000" hoặc PIN không tồn tại -> trả về HTTP 404 kèm thông báo cho JoinErrorScreen.kt.
func (em *EventManager) HandleJoinByPin(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		PinCode          string `json:"pinCode"`
		DisplayName      string `json:"displayName"`
		RoleTitle        string `json:"roleTitle"`
		UserLevel        uint8  `json:"userLevel"`        // Android: 1 (Đạo diễn), 2 (Trưởng ban), 3 (CTV)
		RolePriority     uint8  `json:"rolePriority"`     // Server: 3 (Đạo diễn), 2 (Trưởng ban), 1 (CTV)
		PreferredChannel uint8  `json:"preferredChannel"` // 1..5
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON body", http.StatusBadRequest)
		return
	}

	if req.PinCode == "000000" {
		writeJSON(w, http.StatusNotFound, map[string]interface{}{
			"error":       "PIN_EXPIRED_OR_INVALID",
			"invalidCode": req.PinCode,
			"message":     "Mã PIN sự kiện 000000 không tồn tại hoặc đã hết hạn",
		})
		return
	}

	em.mu.RLock()
	ev, exists := em.eventsByPin[req.PinCode]
	em.mu.RUnlock()
	if !exists {
		writeJSON(w, http.StatusNotFound, map[string]interface{}{
			"error":       "PIN_NOT_FOUND",
			"invalidCode": req.PinCode,
			"message":     "Mã PIN sự kiện không tồn tại hoặc đã hết hạn",
		})
		return
	}

	userLevel, rolePriority, defaultTitle := ConvertUserLevelToPriority(req.UserLevel, req.RolePriority)
	if req.DisplayName == "" {
		req.DisplayName = "Minh Hậu"
	}
	if req.RoleTitle == "" {
		req.RoleTitle = defaultTitle
	}
	if req.PreferredChannel < 1 || req.PreferredChannel > 5 {
		req.PreferredChannel = 2 // Mặc định vào kênh 2 (Sân khấu - ch_02)
	}

	secretBytes := make([]byte, 32)
	_, _ = rand.Read(secretBytes)
	newUserID := em.userSeq.Add(1)

	listening := []uint8{1, req.PreferredChannel}
	if req.PreferredChannel == 1 {
		listening = []uint8{1, 2}
	}

	session := &UserSession{
		UserID:            newUserID,
		UserCode:          fmt.Sprintf("user_%d", newUserID),
		EventID:           ev.EventID,
		DisplayName:       req.DisplayName,
		RoleTitle:         req.RoleTitle,
		UserLevel:         userLevel,
		RolePriority:      rolePriority,
		PrimaryTxChannel:  req.PreferredChannel,
		PrimaryChannelID:  fmt.Sprintf("ch_0%d", req.PreferredChannel),
		ListeningChannels: listening,
		HmacSecretHex:     hex.EncodeToString(secretBytes),
		HmacSecretBytes:   secretBytes,
	}

	em.mu.Lock()
	em.usersByID[newUserID] = session
	em.mu.Unlock()

	// Đăng ký người dùng và kênh đã chọn vào Bộ định tuyến Âm thanh UDP (ChannelAudioRouter)
	if em.audioRouter != nil {
		em.audioRouter.RegisterUserSession(newUserID, secretBytes, session.ListeningChannels)
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"event":   ev,
		"session": session,
	})
}

// HandleSwitchChannel xử lý POST /api/v1/users/switch-channel — Đổi kênh nói chính & danh sách kênh nghe (tối đa 3 kênh).
func (em *EventManager) HandleSwitchChannel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		UserID            uint32  `json:"userId"`
		PrimaryTxChannel  uint8   `json:"primaryTxChannel"`
		ListeningChannels []uint8 `json:"listeningChannels"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	// Giới hạn tối đa 3 kênh nghe đồng thời theo NFR để tránh quá tải thính giác
	if len(req.ListeningChannels) > 3 {
		req.ListeningChannels = req.ListeningChannels[:3]
	}

	em.mu.Lock()
	session, exists := em.usersByID[req.UserID]
	if !exists {
		em.mu.Unlock()
		http.Error(w, "User session not found", http.StatusNotFound)
		return
	}
	session.PrimaryTxChannel = req.PrimaryTxChannel
	session.PrimaryChannelID = fmt.Sprintf("ch_0%d", req.PrimaryTxChannel)
	session.ListeningChannels = req.ListeningChannels
	em.mu.Unlock()

	if em.audioRouter != nil {
		em.audioRouter.UpdateUserSubscribedChannels(req.UserID, req.ListeningChannels)
	}

	writeJSON(w, http.StatusOK, session)
}

// HandleLeaveChannel xử lý POST /api/v1/users/leave-channel — Tương ứng nút "Rời kênh" trong ChannelMenuSheet trên Android.
func (em *EventManager) HandleLeaveChannel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		UserID    uint32 `json:"userId"`
		ChannelID uint8  `json:"channelId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	em.mu.Lock()
	session, exists := em.usersByID[req.UserID]
	if exists {
		filtered := make([]uint8, 0, len(session.ListeningChannels))
		for _, ch := range session.ListeningChannels {
			if ch != req.ChannelID {
				filtered = append(filtered, ch)
			}
		}
		session.ListeningChannels = filtered
	}
	em.mu.Unlock()

	if em.audioRouter != nil && exists {
		em.audioRouter.UpdateUserSubscribedChannels(req.UserID, session.ListeningChannels)
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":    "LEFT_CHANNEL",
		"userId":    req.UserID,
		"channelId": req.ChannelID,
	})
}

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}
