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
// Phối hợp với Người 3 (Màn hình Danh sách Kênh & Nhập PIN tham gia sự kiện)
// =============================================================================

type ChannelInfo struct {
	ChannelID       uint8  `json:"channelId"`
	Name            string `json:"name"`
	DepartmentCode  string `json:"departmentCode"`
	DefaultPriority uint8  `json:"defaultPriority"`
	OnlineCount     int    `json:"onlineCount"`
}

type EventInfo struct {
	EventID   int64                  `json:"eventId"`
	EventName string                 `json:"eventName"`
	PinCode   string                 `json:"pinCode"`
	CreatedAt time.Time              `json:"createdAt"`
	Channels  map[uint8]*ChannelInfo `json:"channels"`
}

type UserSession struct {
	UserID            uint32   `json:"userId"`
	EventID           int64    `json:"eventId"`
	DisplayName       string   `json:"displayName"`
	RolePriority      uint8    `json:"rolePriority"`
	PrimaryTxChannel  uint8    `json:"primaryTxChannel"`
	ListeningChannels []uint8  `json:"listeningChannels"`
	HmacSecretHex     string   `json:"hmacSecretHex"`
	HmacSecretBytes   []byte   `json:"-"`
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
	em.eventSeq.Store(1)

	// Khởi tạo sẵn Sự kiện mặc định (PIN: 482910) với 5 kênh chuẩn
	defaultEvent := &EventInfo{
		EventID:   1,
		EventName: "Đêm Nhạc Acoustic 2026",
		PinCode:   "482910",
		CreatedAt: time.Now(),
		Channels:  createDefaultFiveChannels(),
	}
	em.eventsByPin["482910"] = defaultEvent
	return em
}

func createDefaultFiveChannels() map[uint8]*ChannelInfo {
	return map[uint8]*ChannelInfo{
		1: {ChannelID: 1, Name: "CHỈ HUY", DepartmentCode: "DIRECTOR", DefaultPriority: 3},
		2: {ChannelID: 2, Name: "SÂN KHẤU", DepartmentCode: "STAGE", DefaultPriority: 2},
		3: {ChannelID: 3, Name: "ÂM THANH & ÁNH SÁNG", DepartmentCode: "TECH_AV", DefaultPriority: 2},
		4: {ChannelID: 4, Name: "AN NINH - Y TẾ", DepartmentCode: "SECURITY", DefaultPriority: 2},
		5: {ChannelID: 5, Name: "HẬU CẦN", DepartmentCode: "LOGISTICS", DefaultPriority: 1},
	}
}

// HandleCreateEvent xử lý POST /api/v1/events — Tạo sự kiện mới & sinh mã PIN 6 số.
func (em *EventManager) HandleCreateEvent(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		EventName string `json:"eventName"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.EventName == "" {
		req.EventName = "Sự kiện Mới"
	}

	pinNum, _ := rand.Int(rand.Reader, big.NewInt(900000))
	pinCode := fmt.Sprintf("%06d", pinNum.Int64()+100000)
	eventID := em.eventSeq.Add(1)

	ev := &EventInfo{
		EventID:   eventID,
		EventName: req.EventName,
		PinCode:   pinCode,
		CreatedAt: time.Now(),
		Channels:  createDefaultFiveChannels(),
	}

	em.mu.Lock()
	em.eventsByPin[pinCode] = ev
	em.mu.Unlock()

	writeJSON(w, http.StatusCreated, ev)
}

// HandleJoinByPin xử lý POST /api/v1/auth/join-pin — Người dùng nhập PIN 6 số để tham gia sự kiện.
func (em *EventManager) HandleJoinByPin(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		PinCode          string `json:"pinCode"`
		DisplayName      string `json:"displayName"`
		RolePriority     uint8  `json:"rolePriority"`
		PreferredChannel uint8  `json:"preferredChannel"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON body", http.StatusBadRequest)
		return
	}

	em.mu.RLock()
	ev, exists := em.eventsByPin[req.PinCode]
	em.mu.RUnlock()
	if !exists {
		http.Error(w, "Mã PIN sự kiện không tồn tại hoặc đã hết hạn", http.StatusNotFound)
		return
	}

	if req.RolePriority < 1 || req.RolePriority > 3 {
		req.RolePriority = 1
	}
	if req.PreferredChannel < 1 || req.PreferredChannel > 5 {
		req.PreferredChannel = 2 // Mặc định vào kênh SÂN KHẤU
	}

	secretBytes := make([]byte, 32)
	_, _ = rand.Read(secretBytes)
	newUserID := em.userSeq.Add(1)

	session := &UserSession{
		UserID:            newUserID,
		EventID:           ev.EventID,
		DisplayName:       req.DisplayName,
		RolePriority:      req.RolePriority,
		PrimaryTxChannel:  req.PreferredChannel,
		ListeningChannels: []uint8{req.PreferredChannel},
		HmacSecretHex:     hex.EncodeToString(secretBytes),
		HmacSecretBytes:   secretBytes,
	}

	em.mu.Lock()
	em.usersByID[newUserID] = session
	em.mu.Unlock()

	// Đăng ký người dùng và kênh đã chọn vào Bộ định tuyến Âm thanh UDP (ChannelAudioRouter)
	em.audioRouter.RegisterUserSession(newUserID, secretBytes, session.ListeningChannels)

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
	session.ListeningChannels = req.ListeningChannels
	em.mu.Unlock()

	// Cập nhật tức thì bảng định tuyến luồng âm thanh trên Server
	em.audioRouter.UpdateUserSubscribedChannels(req.UserID, req.ListeningChannels)

	writeJSON(w, http.StatusOK, session)
}

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}
