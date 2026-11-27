package week2routing

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"math/big"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"damthoai/nguoi5server/identity"
)

// ChannelIDList là danh sách ID kênh (1..5). Mặc định Go mã hóa []uint8 thành chuỗi base64
// ("AQI="), làm app Android (mong đợi mảng số [1,2]) bị lỗi parse. Kiểu này luôn
// mã hóa thành mảng số, và khi nhận vào chấp nhận cả mảng số lẫn chuỗi base64.
type ChannelIDList []uint8

func (c ChannelIDList) MarshalJSON() ([]byte, error) {
	out := make([]int, len(c))
	for i, v := range c {
		out[i] = int(v)
	}
	return json.Marshal(out)
}

func (c *ChannelIDList) UnmarshalJSON(data []byte) error {
	var nums []int
	if err := json.Unmarshal(data, &nums); err == nil {
		res := make([]uint8, 0, len(nums))
		for _, n := range nums {
			if n < 0 || n > 255 {
				return fmt.Errorf("channel id ngoài khoảng: %d", n)
			}
			res = append(res, uint8(n))
		}
		*c = res
		return nil
	}
	var raw []byte // chuỗi base64
	if err := json.Unmarshal(data, &raw); err != nil {
		return err
	}
	*c = raw
	return nil
}

// FlexibleUserID chấp nhận cả chuỗi ("Minh_Hau_101_a1b2") lẫn số nguyên (101) trong JSON request.
type FlexibleUserID string

func (u *FlexibleUserID) UnmarshalJSON(data []byte) error {
	trimmed := strings.TrimSpace(string(data))
	if trimmed == "" || trimmed == "null" {
		*u = ""
		return nil
	}
	var s string
	if err := json.Unmarshal(data, &s); err == nil {
		*u = FlexibleUserID(strings.TrimSpace(s))
		return nil
	}
	var n uint64
	if err := json.Unmarshal(data, &n); err == nil {
		*u = FlexibleUserID(strconv.FormatUint(n, 10))
		return nil
	}
	return fmt.Errorf("userId không hợp lệ: %s", trimmed)
}

// SignalingConfigFromEnv đọc cấu hình máy chủ Signaling Python từ biến môi trường,
// tự động chuẩn hóa https:// -> wss:// và http:// -> ws:// nếu người dùng dán URL HTTP từ Render/Railway.
func SignalingConfigFromEnv() (signalingURL string, signalingToken string) {
	rawURL := strings.TrimRight(strings.TrimSpace(os.Getenv("SIGNALING_URL")), "/")
	switch {
	case strings.HasPrefix(rawURL, "https://"):
		rawURL = "wss://" + strings.TrimPrefix(rawURL, "https://")
	case strings.HasPrefix(rawURL, "http://"):
		rawURL = "ws://" + strings.TrimPrefix(rawURL, "http://")
	}
	return rawURL, strings.TrimSpace(os.Getenv("SIGNALING_TOKEN"))
}

var vietnameseASCII = strings.NewReplacer(
	"à", "a", "á", "a", "ả", "a", "ã", "a", "ạ", "a",
	"ă", "a", "ằ", "a", "ắ", "a", "ẳ", "a", "ẵ", "a", "ặ", "a",
	"â", "a", "ầ", "a", "ấ", "a", "ẩ", "a", "ẫ", "a", "ậ", "a",
	"À", "A", "Á", "A", "Ả", "A", "Ã", "A", "Ạ", "A",
	"Ă", "A", "Ằ", "A", "Ắ", "A", "Ẳ", "A", "Ẵ", "A", "Ặ", "A",
	"Â", "A", "Ầ", "A", "Ấ", "A", "Ẩ", "A", "Ẫ", "A", "Ậ", "A",
	"đ", "d", "Đ", "D",
	"è", "e", "é", "e", "ẻ", "e", "ẽ", "e", "ẹ", "e",
	"ê", "e", "ề", "e", "ế", "e", "ể", "e", "ễ", "e", "ệ", "e",
	"È", "E", "É", "E", "Ẻ", "E", "Ẽ", "E", "Ẹ", "E",
	"Ê", "E", "Ề", "E", "Ế", "E", "Ể", "E", "Ễ", "E", "Ệ", "E",
	"ì", "i", "í", "i", "ỉ", "i", "ĩ", "i", "ị", "i",
	"Ì", "I", "Í", "I", "Ỉ", "I", "Ĩ", "I", "Ị", "I",
	"ò", "o", "ó", "o", "ỏ", "o", "õ", "o", "ọ", "o",
	"ô", "o", "ồ", "o", "ố", "o", "ổ", "o", "ỗ", "o", "ộ", "o",
	"ơ", "o", "ờ", "o", "ớ", "o", "ở", "o", "ỡ", "o", "ợ", "o",
	"Ò", "O", "Ó", "O", "Ỏ", "O", "Õ", "O", "Ọ", "O",
	"Ô", "O", "Ồ", "O", "Ố", "O", "Ổ", "O", "Ỗ", "O", "Ộ", "O",
	"Ơ", "O", "Ờ", "O", "Ớ", "O", "Ở", "O", "Ỡ", "O", "Ợ", "O",
	"ù", "u", "ú", "u", "ủ", "u", "ũ", "u", "ụ", "u",
	"ư", "u", "ừ", "u", "ứ", "u", "ử", "u", "ữ", "u", "ự", "u",
	"Ù", "U", "Ú", "U", "Ủ", "U", "Ũ", "U", "Ụ", "U",
	"Ư", "U", "Ừ", "U", "Ứ", "U", "Ử", "U", "Ữ", "U", "Ự", "U",
	"ỳ", "y", "ý", "y", "ỷ", "y", "ỹ", "y", "ỵ", "y",
	"Ỳ", "Y", "Ý", "Y", "Ỷ", "Y", "Ỹ", "Y", "Ỵ", "Y",
)

// SanitizeUserIDPrefix chuyển tên hiển thị sang chuỗi chỉ gồm [A-Za-z0-9._-] khớp signaling_server.py.
func SanitizeUserIDPrefix(displayName string) string {
	ascii := vietnameseASCII.Replace(strings.TrimSpace(displayName))
	var b strings.Builder
	lastUnderscore := false
	for _, r := range ascii {
		switch {
		case (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || r == '.' || r == '-':
			b.WriteRune(r)
			lastUnderscore = false
		case r == '_' || r == ' ':
			if !lastUnderscore && b.Len() > 0 {
				b.WriteByte('_')
				lastUnderscore = true
			}
		}
		if b.Len() >= 24 {
			break
		}
	}
	res := strings.Trim(b.String(), "._-")
	if res == "" {
		return "user"
	}
	return res
}

// =============================================================================
// TUẦN 2 (NGƯỜI 5): XÂY DỰNG REST API QUẢN LÝ NGƯỜI DÙNG, SỰ KIỆN, KÊNH
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
	MemberCount int                    `json:"memberCount"`
	Channels    map[uint8]*ChannelInfo `json:"channels"`
	ChannelList []*ChannelInfo         `json:"channelList"` // Mảng kênh tuần tự phục vụ LazyColumn trên Android
	AdminCode   string                 `json:"-"`
}

type UserSession struct {
	NumericID         uint32        `json:"numericUserId"`     // ID số nội bộ cho gói UDP 18-byte
	UserID            string        `json:"userId"`            // ID duy nhất gồm chữ, số và . _ - (khớp signaling_server.py)
	UserCode          string        `json:"userCode"`          // Đồng bộ với UserID cho Android
	EventID           int64         `json:"eventId"`           // ID sự kiện
	DisplayName       string        `json:"displayName"`       // VD: "Minh Hậu"
	RoleTitle         string        `json:"roleTitle"`         // VD: "Trưởng ban Sân khấu", "Trưởng ban Hậu cần"
	UserLevel         uint8         `json:"userLevel"`         // Chuẩn UI Android: 1 = Tổng đạo diễn, 2 = Trưởng ban, 3 = CTV
	RolePriority      uint8         `json:"rolePriority"`      // Chuẩn Server CAS: 3 = Tổng đạo diễn, 2 = Trưởng ban, 1 = CTV
	PrimaryTxChannel  uint8         `json:"primaryTxChannel"`  // Kênh phát âm thanh chính (1..5)
	PrimaryChannelID  string        `json:"primaryChannelId"`  // Mã kênh phát âm thanh trên Android ("ch_01".."ch_05")
	ListeningChannels ChannelIDList `json:"listeningChannels"` // Danh sách kênh đang nghe (tối đa 3 kênh)
	SignalingURL      string        `json:"signalingUrl"`      // Đọc từ biến môi trường SIGNALING_URL
	SignalingToken    string        `json:"signalingToken"`    // Đọc từ biến môi trường SIGNALING_TOKEN
	HmacSecretHex     string        `json:"hmacSecretHex"`
	HmacSecretBytes   []byte        `json:"-"`
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
	mu              sync.RWMutex
	eventsByPin     map[string]*EventInfo
	eventsByID      map[int64]*EventInfo
	usersByID       map[uint32]*UserSession
	usersByCode     map[string]*UserSession
	userSeq         atomic.Uint32
	eventSeq        atomic.Int64
	audioRouter     *ChannelAudioRouter
	OnMemberRemoved func(UserSession)
}

func NewEventManager(router *ChannelAudioRouter) *EventManager {
	em := &EventManager{
		eventsByPin: make(map[string]*EventInfo),
		eventsByID:  make(map[int64]*EventInfo),
		usersByID:   make(map[uint32]*UserSession),
		usersByCode: make(map[string]*UserSession),
		audioRouter: router,
	}
	em.userSeq.Store(100)
	em.eventSeq.Store(3)

	// PIN mẫu để join nhanh + adminCode để test restore Cấp 1.
	seedEvents := []struct {
		id        int64
		name      string
		desc      string
		pin       string
		adminCode string
	}{
		{1, "Đêm nhạc Chào Tân sinh viên", "Sự kiện âm nhạc thường niên tại Hội trường A", "240826", "ADM-DEMO01"},
		{2, "Đêm nhạc Chào Tân sinh viên", "Sự kiện âm nhạc thường niên tại Hội trường A (QR/PIN)", "889900", "ADM-DEMO02"},
		{3, "Đêm Nhạc Acoustic 2026", "Sự kiện biểu diễn Acoustic Hội trường lớn", "482910", "ADM-DEMO03"},
	}

	for _, item := range seedEvents {
		chMap, chList := createDefaultFiveChannels()
		ev := &EventInfo{
			EventID:     item.id,
			EventName:   item.name,
			Description: item.desc,
			PinCode:     item.pin,
			CreatedAt:   time.Now(),
			Channels:    chMap,
			ChannelList: chList,
			AdminCode:   item.adminCode,
		}
		em.eventsByPin[item.pin] = ev
		em.eventsByID[item.id] = ev
	}

	return em
}

// A0. Identity Resolver & Member requirement
func (em *EventManager) Resolve(ref string) (identity.Identity, bool) {
	em.mu.RLock()
	defer em.mu.RUnlock()
	s := em.findSessionLocked(FlexibleUserID(ref))
	if s == nil {
		return identity.Identity{}, false
	}
	return identity.Identity{
		UserID:       s.UserID,
		NumericID:    s.NumericID,
		EventID:      s.EventID,
		DisplayName:  s.DisplayName,
		RoleTitle:    s.RoleTitle,
		UserLevel:    s.UserLevel,
		RolePriority: s.RolePriority,
	}, true
}

func (em *EventManager) requireMember(eventID int64, ref FlexibleUserID) (*UserSession, error) {
	em.mu.RLock()
	defer em.mu.RUnlock()
	s := em.findSessionLocked(ref)
	if s == nil || s.EventID != eventID {
		return nil, fmt.Errorf("USER_NOT_IN_EVENT")
	}
	return s, nil
}

func (em *EventManager) RequireMember(eventID int64, ref FlexibleUserID) (*UserSession, error) {
	return em.requireMember(eventID, ref)
}

func (em *EventManager) FindEventByID(id int64) *EventInfo {
	em.mu.RLock()
	defer em.mu.RUnlock()
	return em.eventsByID[id]
}

func (em *EventManager) FindEventByPin(pin string) *EventInfo {
	em.mu.RLock()
	defer em.mu.RUnlock()
	return em.eventsByPin[pin]
}

// GetOrDefaultEvent giữ cho test cũ
func (em *EventManager) GetOrDefaultEvent(eventID int64, pinCode string) *EventInfo {
	em.mu.RLock()
	defer em.mu.RUnlock()

	if pinCode != "" {
		if ev, ok := em.eventsByPin[pinCode]; ok {
			return ev
		}
	}
	if eventID > 0 {
		if ev, ok := em.eventsByID[eventID]; ok {
			return ev
		}
	}
	return em.eventsByID[1]
}

func (em *EventManager) generateUniqueUserIDLocked(displayName string, numericID uint32) string {
	prefix := SanitizeUserIDPrefix(displayName)
	for {
		randBytes := make([]byte, 2)
		_, _ = rand.Read(randBytes)
		candidate := fmt.Sprintf("%s_%d_%s", prefix, numericID, hex.EncodeToString(randBytes))
		if len(candidate) > 64 {
			candidate = candidate[:64]
		}
		if _, exists := em.usersByCode[candidate]; !exists {
			return candidate
		}
	}
}

func (em *EventManager) findSessionLocked(ref FlexibleUserID) *UserSession {
	key := strings.TrimSpace(string(ref))
	if key == "" {
		return nil
	}
	if s, ok := em.usersByCode[key]; ok {
		return s
	}
	if n, err := strconv.ParseUint(key, 10, 32); err == nil {
		if s, ok := em.usersByID[uint32(n)]; ok {
			return s
		}
	}
	return nil
}

// CreateSession khởi tạo phiên người dùng mới với userId duy nhất ([A-Za-z0-9._-]) và thông tin Signaling từ biến môi trường.
func (em *EventManager) CreateSession(
	ev *EventInfo,
	displayName string,
	roleTitle string,
	userLevel uint8,
	rolePriority uint8,
	primaryCh uint8,
	listening []uint8,
) *UserSession {
	secretBytes := make([]byte, 32)
	_, _ = rand.Read(secretBytes)
	newNumericID := em.userSeq.Add(1)
	sigURL, sigToken := SignalingConfigFromEnv()

	if primaryCh < 1 || primaryCh > 8 {
		primaryCh = 2
	}
	if len(listening) == 0 {
		listening = []uint8{1, primaryCh}
		if primaryCh == 1 {
			listening = []uint8{1, 2}
		}
	}

	em.mu.Lock()
	uniqueUserID := em.generateUniqueUserIDLocked(displayName, newNumericID)
	session := &UserSession{
		NumericID:         newNumericID,
		UserID:            uniqueUserID,
		UserCode:          uniqueUserID,
		EventID:           ev.EventID,
		DisplayName:       displayName,
		RoleTitle:         roleTitle,
		UserLevel:         userLevel,
		RolePriority:      rolePriority,
		PrimaryTxChannel:  primaryCh,
		PrimaryChannelID:  fmt.Sprintf("ch_0%d", primaryCh),
		ListeningChannels: listening,
		SignalingURL:      sigURL,
		SignalingToken:    sigToken,
		HmacSecretHex:     hex.EncodeToString(secretBytes),
		HmacSecretBytes:   secretBytes,
	}
	em.usersByID[newNumericID] = session
	em.usersByCode[uniqueUserID] = session
	em.mu.Unlock()

	if em.audioRouter != nil {
		em.audioRouter.RegisterUserSession(newNumericID, secretBytes, session.ListeningChannels)
	}
	return session
}

// createDefaultFiveChannels tạo 5 kênh mặc định với OnlineCount 0 và StatusText "".
func createDefaultFiveChannels() (map[uint8]*ChannelInfo, []*ChannelInfo) {
	list := []*ChannelInfo{
		{ID: "ch_01", ChannelID: 1, Name: "Toàn sự kiện", DepartmentCode: "ALL_EVENT", DefaultPriority: 3, OnlineCount: 0, StatusText: ""},
		{ID: "ch_02", ChannelID: 2, Name: "Sân khấu", DepartmentCode: "STAGE", DefaultPriority: 2, OnlineCount: 0, StatusText: ""},
		{ID: "ch_03", ChannelID: 3, Name: "Hậu cần – An ninh", DepartmentCode: "LOGISTICS_SEC", DefaultPriority: 2, OnlineCount: 0, StatusText: ""},
		{ID: "ch_04", ChannelID: 4, Name: "Kỹ thuật", DepartmentCode: "TECHNICAL", DefaultPriority: 2, OnlineCount: 0, StatusText: ""},
		{ID: "ch_05", ChannelID: 5, Name: "Đón tiếp", DepartmentCode: "RECEPTION", DefaultPriority: 1, OnlineCount: 0, StatusText: ""},
	}
	chMap := make(map[uint8]*ChannelInfo, len(list))
	for _, ch := range list {
		chMap[ch.ChannelID] = ch
	}
	return chMap, list
}

// A1. HandleCreateEvent
func (em *EventManager) HandleCreateEvent(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]interface{}{
			"error":   "METHOD_NOT_ALLOWED",
			"message": "Method not allowed",
		})
		return
	}

	var req struct {
		EventName   string `json:"eventName"`
		Description string `json:"description"`
		PinCode     string `json:"pinCode"`
		AdminCode   string `json:"adminCode"`
		DisplayName string `json:"displayName"`
		RoleTitle   string `json:"roleTitle"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	if req.EventName == "" {
		req.EventName = "Đêm nhạc Chào Tân sinh viên"
	}
	if req.Description == "" {
		req.Description = "Sự kiện điều phối bộ đàm đa kênh thời gian thực"
	}

	adminCode := strings.TrimSpace(req.AdminCode)
	if adminCode != "" {
		if !isValidAdminCode(adminCode) {
			writeJSON(w, http.StatusBadRequest, map[string]interface{}{
				"error":   "ADMIN_CODE_INVALID",
				"message": "Mã quản trị không hợp lệ (6-32 ký tự [A-Za-z0-9-])",
			})
			return
		}
		em.mu.RLock()
		for _, ev := range em.eventsByID {
			if ev.AdminCode != "" && strings.EqualFold(ev.AdminCode, adminCode) {
				em.mu.RUnlock()
				writeJSON(w, http.StatusConflict, map[string]interface{}{
					"error":   "ADMIN_CODE_TAKEN",
					"message": "Mã quản trị đã tồn tại ở sự kiện khác",
				})
				return
			}
		}
		em.mu.RUnlock()
	}

	pinCode := strings.TrimSpace(req.PinCode)
	isSixDigits := len(pinCode) == 6
	for _, ch := range pinCode {
		if ch < '0' || ch > '9' {
			isSixDigits = false
			break
		}
	}

	em.mu.Lock()
	if !isSixDigits || pinCode == "000000" {
		for {
			pinNum, _ := rand.Int(rand.Reader, big.NewInt(900000))
			pinCode = fmt.Sprintf("%06d", pinNum.Int64()+100000)
			if _, exists := em.eventsByPin[pinCode]; !exists {
				break
			}
		}
	} else {
		if _, exists := em.eventsByPin[pinCode]; exists {
			for {
				pinNum, _ := rand.Int(rand.Reader, big.NewInt(900000))
				pinCode = fmt.Sprintf("%06d", pinNum.Int64()+100000)
				if _, existsNew := em.eventsByPin[pinCode]; !existsNew {
					break
				}
			}
		}
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
		AdminCode:   adminCode,
	}

	em.eventsByPin[pinCode] = ev
	em.eventsByID[eventID] = ev
	em.mu.Unlock()

	displayName := strings.TrimSpace(req.DisplayName)
	if displayName == "" {
		displayName = "Tổng đạo diễn"
	}
	roleTitle := strings.TrimSpace(req.RoleTitle)
	if roleTitle == "" {
		roleTitle = "Tổng đạo diễn"
	}

	session := em.CreateSession(ev, displayName, roleTitle, 1, 3, 1, nil)

	writeJSON(w, http.StatusCreated, map[string]interface{}{
		"eventId":     ev.EventID,
		"eventName":   ev.EventName,
		"description": ev.Description,
		"pinCode":     ev.PinCode,
		"createdAt":   ev.CreatedAt,
		"channels":    ev.Channels,
		"channelList": ev.ChannelList,
		"adminCode":   adminCode,
		"session":     session,
	})
}

// HandleJoinByPin xử lý POST /api/v1/auth/join-pin — Người dùng nhập PIN 6 số để tham gia sự kiện.
func (em *EventManager) HandleJoinByPin(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]interface{}{
			"error":   "METHOD_NOT_ALLOWED",
			"message": "Method not allowed",
		})
		return
	}

	var req struct {
		PinCode          string `json:"pinCode"`
		DisplayName      string `json:"displayName"`
		RoleTitle        string `json:"roleTitle"`
		UserLevel        uint8  `json:"userLevel"`
		RolePriority     uint8  `json:"rolePriority"`
		PreferredChannel uint8  `json:"preferredChannel"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{
			"error":   "INVALID_JSON",
			"message": "Invalid JSON body",
		})
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

	// A5: do not trust client userLevel/rolePriority (always Level 3, priority 1)
	userLevel := uint8(3)
	rolePriority := uint8(1)
	roleTitle := "Cộng tác viên"

	if strings.TrimSpace(req.DisplayName) == "" {
		req.DisplayName = "Minh Hậu"
	}
	if strings.TrimSpace(req.RoleTitle) != "" {
		roleTitle = req.RoleTitle
	}

	// preferredChannel must exist in event or default to 1
	if req.PreferredChannel == 0 || ev.Channels[req.PreferredChannel] == nil {
		req.PreferredChannel = 1
	}

	listening := []uint8{1, req.PreferredChannel}
	if req.PreferredChannel == 1 {
		listening = []uint8{1, 2}
	}
	listening = ensureValidListeningChannels(req.PreferredChannel, listening)

	session := em.CreateSession(ev, req.DisplayName, roleTitle, userLevel, rolePriority, req.PreferredChannel, listening)

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"userId":         session.UserID,
		"signalingUrl":   session.SignalingURL,
		"signalingToken": session.SignalingToken,
		"event":          ev,
		"session":        session,
	})
}

// HandleSwitchChannel xử lý POST /api/v1/users/switch-channel
func (em *EventManager) HandleSwitchChannel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]interface{}{
			"error":   "METHOD_NOT_ALLOWED",
			"message": "Method not allowed",
		})
		return
	}

	var req struct {
		UserID            FlexibleUserID `json:"userId"`
		PrimaryTxChannel  uint8          `json:"primaryTxChannel"`
		ListeningChannels ChannelIDList  `json:"listeningChannels"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{
			"error":   "INVALID_JSON",
			"message": "Invalid JSON",
		})
		return
	}

	em.mu.Lock()
	session := em.findSessionLocked(req.UserID)
	if session == nil {
		em.mu.Unlock()
		writeJSON(w, http.StatusNotFound, map[string]interface{}{
			"error":   "USER_NOT_FOUND",
			"message": "User session not found",
		})
		return
	}

	ev := em.eventsByID[session.EventID]
	if ev != nil && req.PrimaryTxChannel > 0 && ev.Channels[req.PrimaryTxChannel] != nil {
		session.PrimaryTxChannel = req.PrimaryTxChannel
		session.PrimaryChannelID = fmt.Sprintf("ch_0%d", req.PrimaryTxChannel)
	}

	listening := ensureValidListeningChannels(session.PrimaryTxChannel, req.ListeningChannels)
	session.ListeningChannels = listening
	numericID := session.NumericID
	em.mu.Unlock()

	if em.audioRouter != nil {
		em.audioRouter.UpdateUserSubscribedChannels(numericID, listening)
	}

	writeJSON(w, http.StatusOK, session)
}

// HandleLeaveChannel xử lý POST /api/v1/users/leave-channel
func (em *EventManager) HandleLeaveChannel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]interface{}{
			"error":   "METHOD_NOT_ALLOWED",
			"message": "Method not allowed",
		})
		return
	}

	var req struct {
		UserID    FlexibleUserID `json:"userId"`
		ChannelID uint8          `json:"channelId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{
			"error":   "INVALID_JSON",
			"message": "Invalid JSON",
		})
		return
	}

	em.mu.Lock()
	session := em.findSessionLocked(req.UserID)
	var numericID uint32
	if session != nil {
		numericID = session.NumericID
		filtered := make([]uint8, 0, len(session.ListeningChannels))
		for _, ch := range session.ListeningChannels {
			if ch != req.ChannelID {
				filtered = append(filtered, ch)
			}
		}
		session.ListeningChannels = ensureValidListeningChannels(session.PrimaryTxChannel, filtered)
	}
	em.mu.Unlock()

	if em.audioRouter != nil && session != nil {
		em.audioRouter.UpdateUserSubscribedChannels(numericID, session.ListeningChannels)
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":    "LEFT_CHANNEL",
		"userId":    req.UserID,
		"channelId": req.ChannelID,
	})
}

func ensureValidListeningChannels(primaryCh uint8, channels []uint8) []uint8 {
	exists := false
	var res []uint8
	for _, ch := range channels {
		if ch == primaryCh {
			exists = true
		}
		found := false
		for _, r := range res {
			if r == ch {
				found = true
				break
			}
		}
		if !found {
			res = append(res, ch)
		}
	}
	if !exists {
		res = append([]uint8{primaryCh}, res...)
	}
	if len(res) > 3 {
		res = res[:3]
	}
	return res
}

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}
