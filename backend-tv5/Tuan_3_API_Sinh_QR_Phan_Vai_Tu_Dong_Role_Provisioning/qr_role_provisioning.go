package week3qr

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"sync/atomic"
	"time"

	week2routing "damthoai/nguoi5server/Tuan_2_API_Quan_ly_Su_kien_Kenh_va_Dinh_tuyen_Am_thanh"
)

// =============================================================================
// TUẦN 3 (NGƯỜI 5): LOGIC API SINH MÃ QR ĐỊNH DANH PHÂN VAI TỰ ĐỘNG CHO TỪNG BỘ PHẬN
// Tương thích đầy đủ với RemoteQrCardsRepository (/api/v1/events/qr-roles),
// QrTokenParser và RemoteEventRepository.joinByQr (/api/v1/auth/join-by-qr).
// =============================================================================

type DepartmentProfile struct {
	DepartmentCode     string `json:"departmentCode"`
	DepartmentTitle    string `json:"departmentTitle"`
	RoleTitle          string `json:"roleTitle"`          // Hiển thị trên UserInfoScreen.kt & ChannelListScreen.kt
	UserLevel          uint8  `json:"userLevel"`          // Chuẩn UI Android: 1 = Tổng đạo diễn, 2 = Trưởng ban, 3 = CTV
	AssignedPriority   uint8  `json:"assignedPriority"`   // Chuẩn Server CAS: 3 = Tổng đạo diễn, 2 = Trưởng ban, 1 = CTV
	PrimaryChannelID   uint8  `json:"primaryChannelId"`   // Kênh phát sóng chính mặc định (1..5)
	PrimaryChannelCode string `json:"primaryChannelCode"` // Mã kênh trên Android ("ch_01".."ch_05")
	AutoListenChannels []int  `json:"autoListenChannels"` // Mảng số nguyên (không dùng []uint8 tránh bị mã hóa base64)
	DefaultUiMode      string `json:"defaultUiMode"`      // EASY_SINGLE_BUTTON | PRO_MULTI_CHANNEL
}

// StandardDepartmentProfiles là danh mục 5 Bộ phận Chuẩn của Sự kiện khớp 5 kênh trong app.
var StandardDepartmentProfiles = []DepartmentProfile{
	{
		DepartmentCode:     "ALL_EVENT",
		DepartmentTitle:    "Toàn sự kiện",
		RoleTitle:          "Tổng đạo diễn",
		UserLevel:          1,
		AssignedPriority:   3,
		PrimaryChannelID:   1,
		PrimaryChannelCode: "ch_01",
		AutoListenChannels: []int{1, 2, 3},
		DefaultUiMode:      "PRO_MULTI_CHANNEL",
	},
	{
		DepartmentCode:     "STAGE",
		DepartmentTitle:    "Sân khấu",
		RoleTitle:          "Trưởng ban Sân khấu",
		UserLevel:          2,
		AssignedPriority:   2,
		PrimaryChannelID:   2,
		PrimaryChannelCode: "ch_02",
		AutoListenChannels: []int{1, 2},
		DefaultUiMode:      "PRO_MULTI_CHANNEL",
	},
	{
		DepartmentCode:     "LOGISTICS_SEC",
		DepartmentTitle:    "Hậu cần – An ninh",
		RoleTitle:          "Trưởng ban Hậu cần",
		UserLevel:          2,
		AssignedPriority:   2,
		PrimaryChannelID:   3,
		PrimaryChannelCode: "ch_03",
		AutoListenChannels: []int{1, 2, 3},
		DefaultUiMode:      "PRO_MULTI_CHANNEL",
	},
	{
		DepartmentCode:     "TECHNICAL",
		DepartmentTitle:    "Kỹ thuật",
		RoleTitle:          "Trưởng ban Kỹ thuật",
		UserLevel:          2,
		AssignedPriority:   2,
		PrimaryChannelID:   4,
		PrimaryChannelCode: "ch_04",
		AutoListenChannels: []int{1, 4},
		DefaultUiMode:      "PRO_MULTI_CHANNEL",
	},
	{
		DepartmentCode:     "RECEPTION",
		DepartmentTitle:    "Đón tiếp",
		RoleTitle:          "Cộng tác viên Đón tiếp",
		UserLevel:          3,
		AssignedPriority:   1,
		PrimaryChannelID:   5,
		PrimaryChannelCode: "ch_05",
		AutoListenChannels: []int{1, 5},
		DefaultUiMode:      "EASY_SINGLE_BUTTON",
	},
}

// DepartmentQrTokenPayload là dữ liệu được nhúng và ký số bên trong mã QR của từng bộ phận.
type DepartmentQrTokenPayload struct {
	EventID            int64  `json:"eventId"`
	EventName          string `json:"eventName"`
	PinCode            string `json:"pinCode"`
	DepartmentCode     string `json:"deptCode"`
	DepartmentTitle    string `json:"deptTitle"`
	RoleTitle          string `json:"roleTitle"`
	UserLevel          uint8  `json:"userLevel"`
	AssignedPriority   uint8  `json:"priority"`
	PrimaryChannelID   uint8  `json:"primaryCh"`
	PrimaryChannelCode string `json:"primaryChCode"`
	AutoListenChannels []int  `json:"listenCh"`
	DefaultUiMode      string `json:"uiMode"`
	ExpiresAtUnix      int64  `json:"exp"`
}

// DepartmentQrCard khớp trực tiếp cả RoleQrCard (RemoteQrCardsRepository.kt) lẫn schema mở rộng của Backend.
type DepartmentQrCard struct {
	DeptCode           string `json:"deptCode"`
	DeptTitle          string `json:"deptTitle"`
	DepartmentCode     string `json:"departmentCode"`
	DepartmentTitle    string `json:"departmentTitle"`
	RoleTitle          string `json:"roleTitle"`
	UserLevel          uint8  `json:"userLevel"`
	AssignedPriority   uint8  `json:"assignedPriority"`
	PrimaryChannelID   uint8  `json:"primaryChannelId"`
	PrimaryChannelCode string `json:"primaryChannelCode"`
	AutoListenChannels []int  `json:"autoListenChannels"`
	DefaultUiMode      string `json:"defaultUiMode"`
	QrDeepLinkURI      string `json:"qrDeepLinkUri"` // Chuỗi đưa vào bộ vẽ mã QR: damthoai://join?token=...
	SignedToken        string `json:"signedToken"`
	ExpiresAt          int64  `json:"expiresAt"`    // Epoch seconds (khớp RoleQrCard.expiresAtEpochSeconds: Long)
	ExpiresAtISO       string `json:"expiresAtIso"` // Chuỗi RFC3339 phục vụ đọc log/debug
}

type QrRoleProvisioningService struct {
	masterSecret []byte
	eventManager *week2routing.EventManager
	fallbackSeq  atomic.Uint32
}

func NewQrRoleProvisioningService(masterSecretHex string, eventManagers ...*week2routing.EventManager) *QrRoleProvisioningService {
	secret, err := hex.DecodeString(masterSecretHex)
	if err != nil || len(secret) < 16 {
		secret = []byte("damthoai_master_qr_secret_2026_key")
	}
	var em *week2routing.EventManager
	if len(eventManagers) > 0 {
		em = eventManagers[0]
	}
	svc := &QrRoleProvisioningService{
		masterSecret: secret,
		eventManager: em,
	}
	svc.fallbackSeq.Store(500)
	return svc
}

// SignDepartmentQrToken đóng gói thông tin phân vai bộ phận và ký HMAC-SHA256 chống sửa đổi.
func (s *QrRoleProvisioningService) SignDepartmentQrToken(payload DepartmentQrTokenPayload) (string, error) {
	rawJSON, err := json.Marshal(payload)
	if err != nil {
		return "", err
	}
	encodedPayload := base64.RawURLEncoding.EncodeToString(rawJSON)

	mac := hmac.New(sha256.New, s.masterSecret)
	mac.Write([]byte(encodedPayload))
	signatureHex := hex.EncodeToString(mac.Sum(nil)[:16]) // 16-byte (128-bit) HMAC signature cho mã QR gọn nhẹ dễ quét

	return fmt.Sprintf("%s.%s", encodedPayload, signatureHex), nil
}

// VerifyAndDecodeQrToken xác thực chữ ký HMAC-SHA256 hoặc mã QR mô phỏng từ JoinChannelScreen.kt ("MOCK_QR_EVENT_889900").
func (s *QrRoleProvisioningService) VerifyAndDecodeQrToken(tokenOrURI string) (*DepartmentQrTokenPayload, error) {
	cleaned := strings.TrimSpace(tokenOrURI)
	if strings.HasPrefix(cleaned, "damthoai://join?token=") {
		cleaned = strings.TrimPrefix(cleaned, "damthoai://join?token=")
	}

	switch cleaned {
	case "MOCK_QR_EVENT_889900", "MOCK_QR_EVENT_240826", "889900", "240826":
		pin := "889900"
		eventID := int64(2)
		if strings.Contains(cleaned, "240826") {
			pin = "240826"
			eventID = 1
		}
		return &DepartmentQrTokenPayload{
			EventID:            eventID,
			EventName:          "Đêm nhạc Chào Tân sinh viên",
			PinCode:            pin,
			DepartmentCode:     "LOGISTICS_SEC",
			DepartmentTitle:    "Hậu cần – An ninh",
			RoleTitle:          "Trưởng ban Hậu cần",
			UserLevel:          2,
			AssignedPriority:   2,
			PrimaryChannelID:   2,
			PrimaryChannelCode: "ch_02",
			AutoListenChannels: []int{1, 2, 3},
			DefaultUiMode:      "PRO_MULTI_CHANNEL",
			ExpiresAtUnix:      time.Now().Add(24 * time.Hour).Unix(),
		}, nil
	case "MOCK_QR_DIRECTOR_240826":
		return &DepartmentQrTokenPayload{
			EventID:            1,
			EventName:          "Đêm nhạc Chào Tân sinh viên",
			PinCode:            "240826",
			DepartmentCode:     "ALL_EVENT",
			DepartmentTitle:    "Toàn sự kiện",
			RoleTitle:          "Tổng đạo diễn",
			UserLevel:          1,
			AssignedPriority:   3,
			PrimaryChannelID:   1,
			PrimaryChannelCode: "ch_01",
			AutoListenChannels: []int{1, 2, 3},
			DefaultUiMode:      "PRO_MULTI_CHANNEL",
			ExpiresAtUnix:      time.Now().Add(24 * time.Hour).Unix(),
		}, nil
	}

	parts := strings.Split(cleaned, ".")
	if len(parts) != 2 {
		return nil, errors.New("định dạng mã QR không hợp lệ")
	}

	encodedPayload, sigHex := parts[0], parts[1]
	mac := hmac.New(sha256.New, s.masterSecret)
	mac.Write([]byte(encodedPayload))
	expectedSigHex := hex.EncodeToString(mac.Sum(nil)[:16])

	if !hmac.Equal([]byte(sigHex), []byte(expectedSigHex)) {
		return nil, errors.New("chữ ký bảo mật mã QR không hợp lệ (nghi vấn giả mạo phân vai)")
	}

	rawJSON, err := base64.RawURLEncoding.DecodeString(encodedPayload)
	if err != nil {
		return nil, errors.New("không thể giải mã dữ liệu phân vai trong QR")
	}

	var payload DepartmentQrTokenPayload
	if err := json.Unmarshal(rawJSON, &payload); err != nil {
		return nil, err
	}

	if time.Now().Unix() > payload.ExpiresAtUnix {
		return nil, errors.New("mã QR sự kiện đã hết hạn")
	}

	return &payload, nil
}

// HandleGenerateDepartmentQrCodes xử lý GET /api/v1/events/qr-roles?eventId=...
// Trả về danh sách 5 thẻ QR bộ phận tương thích trực tiếp với RemoteQrCardsRepository trên Android.
func (s *QrRoleProvisioningService) HandleGenerateDepartmentQrCodes(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet && r.Method != "" {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	eventID := int64(1)
	if rawID := strings.TrimSpace(r.URL.Query().Get("eventId")); rawID != "" {
		if parsed, err := strconv.ParseInt(rawID, 10, 64); err == nil && parsed > 0 {
			eventID = parsed
		}
	}
	eventName := "Đêm nhạc Chào Tân sinh viên"
	pinCode := "240826"

	if s.eventManager != nil {
		if ev := s.eventManager.GetOrDefaultEvent(eventID, ""); ev != nil {
			eventID = ev.EventID
			eventName = ev.EventName
			pinCode = ev.PinCode
		}
	}

	expiresAt := time.Now().Add(24 * time.Hour)
	expiresUnix := expiresAt.Unix()

	cards := make([]DepartmentQrCard, 0, len(StandardDepartmentProfiles))
	for _, prof := range StandardDepartmentProfiles {
		payload := DepartmentQrTokenPayload{
			EventID:            eventID,
			EventName:          eventName,
			PinCode:            pinCode,
			DepartmentCode:     prof.DepartmentCode,
			DepartmentTitle:    prof.DepartmentTitle,
			RoleTitle:          prof.RoleTitle,
			UserLevel:          prof.UserLevel,
			AssignedPriority:   prof.AssignedPriority,
			PrimaryChannelID:   prof.PrimaryChannelID,
			PrimaryChannelCode: prof.PrimaryChannelCode,
			AutoListenChannels: prof.AutoListenChannels,
			DefaultUiMode:      prof.DefaultUiMode,
			ExpiresAtUnix:      expiresUnix,
		}

		signedToken, err := s.SignDepartmentQrToken(payload)
		if err != nil {
			continue
		}

		cards = append(cards, DepartmentQrCard{
			DeptCode:           prof.DepartmentCode,
			DeptTitle:          prof.DepartmentTitle,
			DepartmentCode:     prof.DepartmentCode,
			DepartmentTitle:    prof.DepartmentTitle,
			RoleTitle:          prof.RoleTitle,
			UserLevel:          prof.UserLevel,
			AssignedPriority:   prof.AssignedPriority,
			PrimaryChannelID:   prof.PrimaryChannelID,
			PrimaryChannelCode: prof.PrimaryChannelCode,
			AutoListenChannels: prof.AutoListenChannels,
			DefaultUiMode:      prof.DefaultUiMode,
			QrDeepLinkURI:      "damthoai://join?token=" + signedToken,
			SignedToken:        signedToken,
			ExpiresAt:          expiresUnix,
			ExpiresAtISO:       expiresAt.Format(time.RFC3339),
		})
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"eventId":         eventID,
		"eventName":       eventName,
		"pinCode":         pinCode,
		"mockQrToken":     "MOCK_QR_EVENT_889900",
		"cards":           cards,
		"departmentCards": cards,
	})
}

// HandleJoinByDepartmentQr xử lý POST /api/v1/auth/join-by-qr.
// Trả về đầy đủ userId (duy nhất, chỉ gồm chữ, số và . _ -), signalingUrl, signalingToken, event và session.
func (s *QrRoleProvisioningService) HandleJoinByDepartmentQr(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		QrToken     string `json:"qrToken"`
		DisplayName string `json:"displayName"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	claims, err := s.VerifyAndDecodeQrToken(req.QrToken)
	if err != nil {
		http.Error(w, err.Error(), http.StatusUnauthorized)
		return
	}

	if strings.TrimSpace(req.DisplayName) == "" {
		req.DisplayName = "Minh Hậu"
	}

	primaryCh := claims.PrimaryChannelID
	if primaryCh < 1 || primaryCh > 8 {
		primaryCh = 2
	}
	listenUint8 := make([]uint8, 0, len(claims.AutoListenChannels))
	for _, ch := range claims.AutoListenChannels {
		if ch >= 1 && ch <= 8 {
			listenUint8 = append(listenUint8, uint8(ch))
		}
	}

	if s.eventManager != nil {
		ev := s.eventManager.GetOrDefaultEvent(claims.EventID, claims.PinCode)
		session := s.eventManager.CreateSession(
			ev,
			req.DisplayName,
			claims.RoleTitle,
			claims.UserLevel,
			claims.AssignedPriority,
			primaryCh,
			listenUint8,
		)
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"status":             "AUTO_PROVISIONED",
			"userId":             session.UserID,
			"signalingUrl":       session.SignalingURL,
			"signalingToken":     session.SignalingToken,
			"event":              ev,
			"session":            session,
			"eventId":            ev.EventID,
			"eventName":          ev.EventName,
			"pinCode":            ev.PinCode,
			"displayName":        session.DisplayName,
			"roleTitle":          session.RoleTitle,
			"userLevel":          session.UserLevel,
			"departmentCode":     claims.DepartmentCode,
			"departmentTitle":    claims.DepartmentTitle,
			"assignedPriority":   session.RolePriority,
			"primaryTxChannelId": session.PrimaryTxChannel,
			"primaryChannelCode": session.PrimaryChannelID,
			"autoListenChannels": claims.AutoListenChannels,
			"defaultUiMode":      claims.DefaultUiMode,
			"hmacSecretHex":      session.HmacSecretHex,
		})
		return
	}

	// Fallback độc lập nếu không gắn EventManager (ví dụ unit test riêng lẻ)
	seq := s.fallbackSeq.Add(1)
	randBytes := make([]byte, 2)
	_, _ = rand.Read(randBytes)
	userID := fmt.Sprintf("%s_%d_%s", week2routing.SanitizeUserIDPrefix(req.DisplayName), seq, hex.EncodeToString(randBytes))
	sigURL, sigToken := week2routing.SignalingConfigFromEnv()
	sessionSecret := make([]byte, 32)
	_, _ = rand.Read(sessionSecret)

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status":             "AUTO_PROVISIONED",
		"userId":             userID,
		"signalingUrl":       sigURL,
		"signalingToken":     sigToken,
		"eventId":            claims.EventID,
		"eventName":          claims.EventName,
		"pinCode":            claims.PinCode,
		"displayName":        req.DisplayName,
		"roleTitle":          claims.RoleTitle,
		"userLevel":          claims.UserLevel,
		"departmentCode":     claims.DepartmentCode,
		"departmentTitle":    claims.DepartmentTitle,
		"assignedPriority":   claims.AssignedPriority,
		"primaryTxChannelId": primaryCh,
		"primaryChannelCode": fmt.Sprintf("ch_0%d", primaryCh),
		"autoListenChannels": claims.AutoListenChannels,
		"defaultUiMode":      claims.DefaultUiMode,
		"hmacSecretHex":      hex.EncodeToString(sessionSecret),
	})
}
