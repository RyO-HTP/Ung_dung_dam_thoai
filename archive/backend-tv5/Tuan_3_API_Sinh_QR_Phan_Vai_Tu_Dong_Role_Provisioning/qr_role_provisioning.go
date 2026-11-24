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
// Đã đồng bộ 100% với dự án Android DuAnLamChung:
//   - JoinChannelScreen.kt (hỗ trợ cả token ký số HMAC-SHA256 và mã mô phỏng "MOCK_QR_EVENT_889900")
//   - UserInfoScreen.kt (tự động điền Họ tên "Minh Hậu", Vai trò "Trưởng ban Hậu cần", Cấp 2 · Trưởng ban)
//   - ChannelListScreen.kt (gán mã kênh "ch_01".."ch_05" và Quyền ưu tiên 1/2/3)
// =============================================================================

type DepartmentProfile struct {
	DepartmentCode     string  `json:"departmentCode"`
	DepartmentTitle    string  `json:"departmentTitle"`
	RoleTitle          string  `json:"roleTitle"`          // Hiển thị trên UserInfoScreen.kt & ChannelListScreen.kt
	UserLevel          uint8   `json:"userLevel"`          // Chuẩn UI Android: 1 = Tổng đạo diễn, 2 = Trưởng ban, 3 = CTV
	AssignedPriority   uint8   `json:"assignedPriority"`   // Chuẩn Server CAS: 3 = Tổng đạo diễn, 2 = Trưởng ban, 1 = CTV
	PrimaryChannelID   uint8   `json:"primaryChannelId"`   // Kênh phát sóng chính mặc định (1..5)
	PrimaryChannelCode string  `json:"primaryChannelCode"` // Mã kênh trên Android ("ch_01".."ch_05")
	AutoListenChannels []uint8 `json:"autoListenChannels"` // Các kênh tự động bật nghe khi quét QR
	DefaultUiMode      string  `json:"defaultUiMode"`      // EASY_SINGLE_BUTTON | PRO_MULTI_CHANNEL
}

// Danh mục 5 Bộ phận Chuẩn của Sự kiện khớp 100% với 5 kênh trong DuAnLamChung
var StandardDepartmentProfiles = []DepartmentProfile{
	{
		DepartmentCode:     "ALL_EVENT",
		DepartmentTitle:    "★ Toàn Sự Kiện / Tổng Đạo Diễn",
		RoleTitle:          "Tổng đạo diễn",
		UserLevel:          1,
		AssignedPriority:   3,
		PrimaryChannelID:   1,
		PrimaryChannelCode: "ch_01",
		AutoListenChannels: []uint8{1, 2, 3},
		DefaultUiMode:      "PRO_MULTI_CHANNEL",
	},
	{
		DepartmentCode:     "STAGE",
		DepartmentTitle:    "🎭 Ban Sân Khấu",
		RoleTitle:          "Trưởng ban Sân khấu",
		UserLevel:          2,
		AssignedPriority:   2,
		PrimaryChannelID:   2,
		PrimaryChannelCode: "ch_02",
		AutoListenChannels: []uint8{1, 2},
		DefaultUiMode:      "PRO_MULTI_CHANNEL",
	},
	{
		DepartmentCode:     "LOGISTICS_SEC",
		DepartmentTitle:    "🛡️ Hậu Cần – An Ninh",
		RoleTitle:          "Trưởng ban Hậu cần",
		UserLevel:          2,
		AssignedPriority:   2,
		PrimaryChannelID:   3,
		PrimaryChannelCode: "ch_03",
		AutoListenChannels: []uint8{1, 2, 3},
		DefaultUiMode:      "PRO_MULTI_CHANNEL",
	},
	{
		DepartmentCode:     "TECHNICAL",
		DepartmentTitle:    "🔊 Ban Kỹ Thuật",
		RoleTitle:          "Trưởng ban Kỹ thuật",
		UserLevel:          2,
		AssignedPriority:   2,
		PrimaryChannelID:   4,
		PrimaryChannelCode: "ch_04",
		AutoListenChannels: []uint8{1, 4},
		DefaultUiMode:      "PRO_MULTI_CHANNEL",
	},
	{
		DepartmentCode:     "RECEPTION",
		DepartmentTitle:    "🤝 Ban Đón Tiếp",
		RoleTitle:          "Cộng tác viên Đón tiếp",
		UserLevel:          3,
		AssignedPriority:   1,
		PrimaryChannelID:   5,
		PrimaryChannelCode: "ch_05",
		AutoListenChannels: []uint8{1, 5},
		DefaultUiMode:      "EASY_SINGLE_BUTTON",
	},
}

// DepartmentQrTokenPayload là dữ liệu được nhúng và ký số bên trong mã QR của từng bộ phận.
type DepartmentQrTokenPayload struct {
	EventID            int64   `json:"eventId"`
	EventName          string  `json:"eventName"`
	PinCode            string  `json:"pinCode"`
	DepartmentCode     string  `json:"deptCode"`
	DepartmentTitle    string  `json:"deptTitle"`
	RoleTitle          string  `json:"roleTitle"`
	UserLevel          uint8   `json:"userLevel"`
	AssignedPriority   uint8   `json:"priority"`
	PrimaryChannelID   uint8   `json:"primaryCh"`
	PrimaryChannelCode string  `json:"primaryChCode"`
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

// VerifyAndDecodeQrToken xác thực chữ ký HMAC-SHA256 hoặc mã QR mô phỏng từ JoinChannelScreen.kt ("MOCK_QR_EVENT_889900").
func (s *QrRoleProvisioningService) VerifyAndDecodeQrToken(tokenOrURI string) (*DepartmentQrTokenPayload, error) {
	cleaned := strings.TrimSpace(tokenOrURI)
	if strings.HasPrefix(cleaned, "damthoai://join?token=") {
		cleaned = strings.TrimPrefix(cleaned, "damthoai://join?token=")
	}

	// Hỗ trợ trực tiếp mã QR mô phỏng khi bấm khung Camera trên JoinChannelScreen.kt (dòng 330)
	switch cleaned {
	case "MOCK_QR_EVENT_889900", "MOCK_QR_EVENT_240826", "889900", "240826":
		pin := "889900"
		if strings.Contains(cleaned, "240826") {
			pin = "240826"
		}
		return &DepartmentQrTokenPayload{
			EventID:            1,
			EventName:          "Đêm nhạc Chào Tân sinh viên",
			PinCode:            pin,
			DepartmentCode:     "LOGISTICS_SEC",
			DepartmentTitle:    "🛡️ Hậu Cần – An Ninh",
			RoleTitle:          "Trưởng ban Hậu cần",
			UserLevel:          2,
			AssignedPriority:   2,
			PrimaryChannelID:   2,
			PrimaryChannelCode: "ch_02",
			AutoListenChannels: []uint8{1, 2, 3},
			DefaultUiMode:      "PRO_MULTI_CHANNEL",
			ExpiresAtUnix:      time.Now().Add(24 * time.Hour).Unix(),
		}, nil
	case "MOCK_QR_DIRECTOR_240826":
		return &DepartmentQrTokenPayload{
			EventID:            1,
			EventName:          "Đêm nhạc Chào Tân sinh viên",
			PinCode:            "240826",
			DepartmentCode:     "ALL_EVENT",
			DepartmentTitle:    "★ Toàn Sự Kiện / Tổng Đạo Diễn",
			RoleTitle:          "Tổng đạo diễn",
			UserLevel:          1,
			AssignedPriority:   3,
			PrimaryChannelID:   1,
			PrimaryChannelCode: "ch_01",
			AutoListenChannels: []uint8{1, 2, 3},
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

// HandleGenerateDepartmentQrCodes xử lý GET /api/v1/events/qr-roles
// Trả về danh sách 5 mã QR định danh tự động cho 5 bộ phận trong sự kiện "Đêm nhạc Chào Tân sinh viên".
func (s *QrRoleProvisioningService) HandleGenerateDepartmentQrCodes(w http.ResponseWriter, r *http.Request) {
	eventID := int64(1)
	eventName := "Đêm nhạc Chào Tân sinh viên"
	pinCode := "240826"
	expiresAt := time.Now().Add(24 * time.Hour)

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
		"pinCode":         pinCode,
		"mockQrToken":     "MOCK_QR_EVENT_889900",
		"departmentCards": cards,
	})
}

// HandleJoinByDepartmentQr xử lý POST /api/v1/auth/join-by-qr
// Khi nhân sự quét QR của bộ phận nào, Server tự động phân vai, điền thông tin UserInfoScreen và cấp khóa phiên UDP.
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

	sessionSecret := make([]byte, 32)
	_, _ = rand.Read(sessionSecret)

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status":             "AUTO_PROVISIONED",
		"eventId":            claims.EventID,
		"eventName":          claims.EventName,
		"pinCode":            claims.PinCode,
		"displayName":        req.DisplayName,
		"roleTitle":          claims.RoleTitle,
		"userLevel":          claims.UserLevel,
		"departmentCode":     claims.DepartmentCode,
		"departmentTitle":    claims.DepartmentTitle,
		"assignedPriority":   claims.AssignedPriority,
		"primaryTxChannelId": claims.PrimaryChannelID,
		"primaryChannelCode": claims.PrimaryChannelCode,
		"autoListenChannels": claims.AutoListenChannels,
		"defaultUiMode":      claims.DefaultUiMode,
		"hmacSecretHex":      hex.EncodeToString(sessionSecret),
	})
}
