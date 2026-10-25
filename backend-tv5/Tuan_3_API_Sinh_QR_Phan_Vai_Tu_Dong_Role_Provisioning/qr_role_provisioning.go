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
	"strings"
	"time"
)

// =============================================================================
// TUẦN 3 (NGƯỜI 5): LOGIC API SINH MÃ QR ĐỊNH DANH PHÂN VAI TỰ ĐỘNG CHO TỪNG BỘ PHẬN
// Tự động gán Quyền ưu tiên (Priority 1/2/3), Kênh phát chính, Kênh nghe và Chế độ UI
// cho 5 bộ phận: Chỉ huy, Sân khấu, Kỹ thuật Âm thanh-Ánh sáng, An ninh-Y tế, Hậu cần.
// =============================================================================

type DepartmentProfile struct {
	DepartmentCode     string  `json:"departmentCode"`
	DepartmentTitle    string  `json:"departmentTitle"`
	AssignedPriority   uint8   `json:"assignedPriority"`   // 3: Tổng đạo diễn, 2: Kỹ thuật/An ninh, 1: Hậu cần/CTV
	PrimaryChannelID   uint8   `json:"primaryChannelId"`   // Kênh phát sóng chính mặc định
	AutoListenChannels []uint8 `json:"autoListenChannels"` // Các kênh tự động bật nghe khi quét QR
	DefaultUiMode      string  `json:"defaultUiMode"`      // EASY_SINGLE_BUTTON | PRO_MULTI_CHANNEL
}

// Danh mục 5 Bộ phận Chuẩn của Sự kiện
var StandardDepartmentProfiles = []DepartmentProfile{
	{
		DepartmentCode:     "DIRECTOR",
		DepartmentTitle:    "★ Ban Chỉ Huy / Tổng Đạo Diễn",
		AssignedPriority:   3,
		PrimaryChannelID:   1,
		AutoListenChannels: []uint8{1, 2, 3},
		DefaultUiMode:      "PRO_MULTI_CHANNEL",
	},
	{
		DepartmentCode:     "STAGE",
		DepartmentTitle:    "🎭 Điều Phối Sân Khấu",
		AssignedPriority:   2,
		PrimaryChannelID:   2,
		AutoListenChannels: []uint8{1, 2},
		DefaultUiMode:      "PRO_MULTI_CHANNEL",
	},
	{
		DepartmentCode:     "TECH_AV",
		DepartmentTitle:    "🔊 Kỹ Thuật Âm Thanh & Ánh Sáng",
		AssignedPriority:   2,
		PrimaryChannelID:   3,
		AutoListenChannels: []uint8{1, 2, 3},
		DefaultUiMode:      "PRO_MULTI_CHANNEL",
	},
	{
		DepartmentCode:     "SECURITY",
		DepartmentTitle:    "🛡️ An Ninh & Y Tế Thường Trực",
		AssignedPriority:   2,
		PrimaryChannelID:   4,
		AutoListenChannels: []uint8{1, 4},
		DefaultUiMode:      "EASY_SINGLE_BUTTON",
	},
	{
		DepartmentCode:     "LOGISTICS",
		DepartmentTitle:    "📦 Tình Nguyện Viên Hậu Cần",
		AssignedPriority:   1,
		PrimaryChannelID:   5,
		AutoListenChannels: []uint8{1, 5},
		DefaultUiMode:      "EASY_SINGLE_BUTTON",
	},
}

// DepartmentQrTokenPayload là dữ liệu được nhúng và ký số bên trong mã QR của từng bộ phận.
type DepartmentQrTokenPayload struct {
	EventID            int64   `json:"eventId"`
	EventName          string  `json:"eventName"`
	DepartmentCode     string  `json:"deptCode"`
	DepartmentTitle    string  `json:"deptTitle"`
	AssignedPriority   uint8   `json:"priority"`
	PrimaryChannelID   uint8   `json:"primaryCh"`
	AutoListenChannels []uint8 `json:"listenCh"`
	DefaultUiMode      string  `json:"uiMode"`
	ExpiresAtUnix      int64   `json:"exp"`
}

type DepartmentQrCard struct {
	DepartmentProfile
	QrDeepLinkURI string `json:"qrDeepLinkUri"` // Chuỗi đưa vào bộ vẽ mã QR: damthoai://join?token=...
	SignedToken   string `json:"signedToken"`
	ExpiresAt     string `json:"expiresAt"`
}

type QrRoleProvisioningService struct {
	masterSecret []byte
}

func NewQrRoleProvisioningService(masterSecretHex string) *QrRoleProvisioningService {
	secret, err := hex.DecodeString(masterSecretHex)
	if err != nil || len(secret) < 16 {
		secret = []byte("damthoai_master_qr_secret_2026_key")
	}
	return &QrRoleProvisioningService{masterSecret: secret}
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

// VerifyAndDecodeQrToken xác thực chữ ký HMAC-SHA256 và hạn sử dụng khi nhân sự quét mã QR.
func (s *QrRoleProvisioningService) VerifyAndDecodeQrToken(tokenOrURI string) (*DepartmentQrTokenPayload, error) {
	cleaned := strings.TrimSpace(tokenOrURI)
	if strings.HasPrefix(cleaned, "damthoai://join?token=") {
		cleaned = strings.TrimPrefix(cleaned, "damthoai://join?token=")
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

// HandleGenerateDepartmentQrCodes xử lý GET /api/v1/events/qr-roles
// Trả về danh sách 5 mã QR định danh tự động cho 5 bộ phận trong sự kiện.
func (s *QrRoleProvisioningService) HandleGenerateDepartmentQrCodes(w http.ResponseWriter, r *http.Request) {
	eventID := int64(1)
	eventName := "Đêm Nhạc Acoustic 2026"
	expiresAt := time.Now().Add(24 * time.Hour)

	cards := make([]DepartmentQrCard, 0, len(StandardDepartmentProfiles))
	for _, prof := range StandardDepartmentProfiles {
		payload := DepartmentQrTokenPayload{
			EventID:            eventID,
			EventName:          eventName,
			DepartmentCode:     prof.DepartmentCode,
			DepartmentTitle:    prof.DepartmentTitle,
			AssignedPriority:   prof.AssignedPriority,
			PrimaryChannelID:   prof.PrimaryChannelID,
			AutoListenChannels: prof.AutoListenChannels,
			DefaultUiMode:      prof.DefaultUiMode,
			ExpiresAtUnix:      expiresAt.Unix(),
		}

		signedToken, err := s.SignDepartmentQrToken(payload)
		if err != nil {
			continue
		}

		cards = append(cards, DepartmentQrCard{
			DepartmentProfile: prof,
			QrDeepLinkURI:     "damthoai://join?token=" + signedToken,
			SignedToken:       signedToken,
			ExpiresAt:         expiresAt.Format(time.RFC3339),
		})
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"eventId":         eventID,
		"eventName":       eventName,
		"departmentCards": cards,
	})
}

// HandleJoinByDepartmentQr xử lý POST /api/v1/auth/join-by-qr
// Khi nhân sự quét QR của bộ phận nào, Server tự động phân vai, gán kênh và cấp khóa phiên UDP.
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
		req.DisplayName = claims.DepartmentTitle
	}

	sessionSecret := make([]byte, 32)
	_, _ = rand.Read(sessionSecret)

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status":             "AUTO_PROVISIONED",
		"eventId":            claims.EventID,
		"eventName":          claims.EventName,
		"displayName":        req.DisplayName,
		"departmentCode":     claims.DepartmentCode,
		"departmentTitle":    claims.DepartmentTitle,
		"assignedPriority":   claims.AssignedPriority,
		"primaryTxChannelId": claims.PrimaryChannelID,
		"autoListenChannels": claims.AutoListenChannels,
		"defaultUiMode":      claims.DefaultUiMode,
		"hmacSecretHex":      hex.EncodeToString(sessionSecret),
	})
}
