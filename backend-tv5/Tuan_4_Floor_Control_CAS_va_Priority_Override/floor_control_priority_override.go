package week4floor

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"damthoai/nguoi5server/identity"
)

// =============================================================================
// TUẦN 4 (NGƯỜI 5): LẬP TRÌNH FLOOR CONTROL (LOCK-FREE CAS) & PRIORITY OVERRIDE
// Đã đồng bộ 100% với dự án Android:
//   - ActiveCallScreen.kt (6 trạng thái UI: CallIdle, CallTalk, CallTake, CallBusy, CallCut, CallOffline)
//   - ChannelListScreen.kt (Tự động bật cờ isDucked80 = true "HẠ ÂM 80%" khi Cấp 1 phát trên kênh ch_01)
//   - EmergencyCallSelectScreen.kt (API kích hoạt Khẩn cấp / Cắt lời toàn sự kiện)
// =============================================================================

const (
	RoleVolunteer uint8 = 1 // Cộng tác viên (Android: Cấp 3 -> Server Priority 1)
	RoleTeamLead  uint8 = 2 // Trưởng ban Sân khấu / Hậu cần / Kỹ thuật (Android: Cấp 2 -> Server Priority 2)
	RoleDirector  uint8 = 3 // Tổng Đạo diễn / Chỉ huy Sự kiện (Android: Cấp 1 -> Server Priority 3)

	MaxSpeakDurationMs = 60_000 // Giới hạn nói tối đa 60 giây chống kẹt phím cứng trong túi quần (Pocket Mode)
	StaleUdpSilenceMs  = 2_500  // Tự động nhả mic nếu luồng UDP đang hoạt động bị đứt > 2,5 giây
)

type FloorDecisionStatus string

const (
	StatusFloorGranted   FloorDecisionStatus = "FLOOR_GRANTED"
	StatusFloorDenied    FloorDecisionStatus = "FLOOR_DENIED"
	StatusFloorPreempted FloorDecisionStatus = "FLOOR_PREEMPTED" // Cấp cao hơn cắt ngang thành công
	StatusFloorReleased  FloorDecisionStatus = "FLOOR_RELEASED"
)

// FlexibleUserID chấp nhận cả userId dạng chuỗi ("Minh_Hau_101_a1b2") từ app Android lẫn số nguyên từ script test.
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

// NormalizeRoleAndLevel đồng bộ giữa Cấp độ trên UI Android (UserLevel 1,2,3)
// và Độ ưu tiên xử lý CAS trên Server (RolePriority 3,2,1).
// Công thức: RolePriority = 4 - UserLevel
func NormalizeRoleAndLevel(userLevel uint8, rolePriority uint8) (level uint8, priority uint8) {
	if rolePriority >= 1 && rolePriority <= 3 {
		priority = rolePriority
		level = 4 - rolePriority
		return level, priority
	}
	if userLevel >= 1 && userLevel <= 3 {
		level = userLevel
		priority = 4 - userLevel
		return level, priority
	}
	return 2, 2
}

// FloorHolder lưu thông tin người đang giữ quyền phát sóng trên 1 kênh.
type FloorHolder struct {
	ChannelID      uint8  `json:"channelId"`
	ChannelCode    string `json:"channelCode"` // "ch_01".."ch_05"
	UserID         string `json:"userId"`
	DisplayName    string `json:"displayName"`
	RoleTitle      string `json:"roleTitle,omitempty"`
	UserLevel      uint8  `json:"userLevel"`    // 1 = Tổng đạo diễn, 2 = Trưởng ban, 3 = CTV
	RolePriority   uint8  `json:"rolePriority"` // 3 = Tổng đạo diễn, 2 = Trưởng ban, 1 = CTV
	GrantedAtMs    int64  `json:"grantedAtMs"`
	LastAudioPktMs int64  `json:"lastAudioPktMs"`
	UdpActive      bool   `json:"-"`
}

// FloorRequest là yêu cầu xin quyền nói từ thiết bị Android gửi lên Server.
type FloorRequest struct {
	ChannelID    uint8          `json:"channelId"`
	ChannelCode  string         `json:"channelCode,omitempty"`
	UserID       FlexibleUserID `json:"userId"`
	DisplayName  string         `json:"displayName"`
	RoleTitle    string         `json:"roleTitle,omitempty"`
	UserLevel    uint8          `json:"userLevel,omitempty"`    // Hỗ trợ gửi từ Android (1, 2, 3)
	RolePriority uint8          `json:"rolePriority,omitempty"` // Hỗ trợ gửi từ Stress Test (3, 2, 1)
}

// FloorDecision là kết quả phán quyết của Server trả về và quảng bá.
type FloorDecision struct {
	Status             FloorDecisionStatus `json:"status"`
	ChannelID          uint8               `json:"channelId"`
	ChannelCode        string              `json:"channelCode"`                // "ch_01".."ch_05"
	RequesterCallState string              `json:"requesterCallState"`         // CallTalk | CallBusy | CallIdle
	ListenerCallState  string              `json:"listenerCallState"`          // CallTake | CallIdle
	RevokedCallState   string              `json:"revokedCallState,omitempty"` // CallCut (hiển thị banner đỏ "BỊ NGẮT LỜI" trên máy bị cắt)
	IsDucked80         bool                `json:"isDucked80"`                 // true -> Hiển thị nhãn "HẠ ÂM 80%" (-12dB) trên các kênh phụ
	DuckingDb          int                 `json:"duckingDb"`                  // -12 (dB) khi Tổng đạo diễn phát lệnh
	ActiveHolder       *FloorHolder        `json:"activeHolder,omitempty"`
	RevokedUserID      string              `json:"revokedUserId,omitempty"`      // ID của người vừa bị ngắt mic
	RevokedDisplayName string              `json:"revokedDisplayName,omitempty"` // Tên của người vừa bị cắt ngang
	Reason             string              `json:"reason,omitempty"`
	TimestampMs        int64               `json:"timestampMs"`
}

type ChannelFloorSlot struct {
	holder atomic.Pointer[FloorHolder]
}

type FloorControlEngine struct {
	mu                sync.RWMutex
	identityResolver  identity.Resolver
	slots             [33]ChannelFloorSlot
	onFloorEvent      func(decision FloorDecision) // Callback đẩy thông báo tức thì cho các máy
	TotalGrants       atomic.Uint64
	TotalPreemptions  atomic.Uint64
	TotalDenied       atomic.Uint64
	TotalAutoReleases atomic.Uint64
}

func NewFloorControlEngine(onFloorEvent func(decision FloorDecision)) *FloorControlEngine {
	return &FloorControlEngine{
		onFloorEvent: onFloorEvent,
	}
}

func (fc *FloorControlEngine) SetIdentityResolver(resolver identity.Resolver) {
	fc.mu.Lock()
	defer fc.mu.Unlock()
	fc.identityResolver = resolver
}

func (fc *FloorControlEngine) Snapshot() []FloorHolder {
	nowMs := time.Now().UnixMilli()
	var holders []FloorHolder
	for chID := uint8(1); chID <= 32; chID++ {
		slot := &fc.slots[chID]
		current := slot.holder.Load()
		if current != nil && !fc.isHolderExpired(current, nowMs) {
			holders = append(holders, *current)
		}
	}
	if holders == nil {
		holders = []FloorHolder{}
	}
	return holders
}

// RequestFloor xử lý tranh chấp quyền nói bằng thuật toán Lock-Free Compare-And-Swap (CAS).
func (fc *FloorControlEngine) RequestFloor(req FloorRequest) FloorDecision {
	nowMs := time.Now().UnixMilli()
	if req.ChannelID == 0 || req.ChannelID > 32 {
		req.ChannelID = 2
	}
	channelCode := req.ChannelCode
	if channelCode == "" {
		channelCode = fmt.Sprintf("ch_0%d", req.ChannelID)
	}
	reqUserID := strings.TrimSpace(string(req.UserID))
	if reqUserID == "" {
		reqUserID = "anonymous"
	}
	userLevel, rolePriority := NormalizeRoleAndLevel(req.UserLevel, req.RolePriority)
	req.UserLevel = userLevel
	req.RolePriority = rolePriority

	isDirectorOverride := (rolePriority == RoleDirector) || (req.ChannelID == 1)
	duckDb := 0
	if isDirectorOverride {
		duckDb = -12
	}

	slot := &fc.slots[req.ChannelID]

	for {
		current := slot.holder.Load()

		// Kiểm tra xem người giữ mic hiện tại đã bị quá hạn 60s (hoặc đứt luồng UDP > 2,5s) chưa
		if current != nil && fc.isHolderExpired(current, nowMs) {
			slot.holder.CompareAndSwap(current, nil)
			current = nil
		}

		// TRƯỜNG HỢP 1: Kênh đang rảnh -> Cấp quyền giữ mic bằng CAS
		if current == nil {
			next := &FloorHolder{
				ChannelID:      req.ChannelID,
				ChannelCode:    channelCode,
				UserID:         reqUserID,
				DisplayName:    req.DisplayName,
				RoleTitle:      req.RoleTitle,
				UserLevel:      req.UserLevel,
				RolePriority:   req.RolePriority,
				GrantedAtMs:    nowMs,
				LastAudioPktMs: nowMs,
			}
			if slot.holder.CompareAndSwap(nil, next) {
				fc.TotalGrants.Add(1)
				decision := FloorDecision{
					Status:             StatusFloorGranted,
					ChannelID:          req.ChannelID,
					ChannelCode:        channelCode,
					RequesterCallState: "CallTalk",
					ListenerCallState:  "CallTake",
					IsDucked80:         isDirectorOverride,
					DuckingDb:          duckDb,
					ActiveHolder:       next,
					Reason:             "CHANNEL_IDLE_GRANTED",
					TimestampMs:        nowMs,
				}
				fc.emitEvent(decision)
				return decision
			}
			continue
		}

		// TRƯỜNG HỢP 2: Chính người đang giữ mic gửi lại yêu cầu (Idempotent Keep-Alive)
		if current.UserID == reqUserID {
			updated := &FloorHolder{
				ChannelID:      current.ChannelID,
				ChannelCode:    current.ChannelCode,
				UserID:         current.UserID,
				DisplayName:    current.DisplayName,
				RoleTitle:      current.RoleTitle,
				UserLevel:      current.UserLevel,
				RolePriority:   current.RolePriority,
				GrantedAtMs:    current.GrantedAtMs,
				LastAudioPktMs: nowMs,
				UdpActive:      current.UdpActive,
			}
			slot.holder.CompareAndSwap(current, updated)
			return FloorDecision{
				Status:             StatusFloorGranted,
				ChannelID:          req.ChannelID,
				ChannelCode:        channelCode,
				RequesterCallState: "CallTalk",
				ListenerCallState:  "CallTake",
				IsDucked80:         isDirectorOverride,
				DuckingDb:          duckDb,
				ActiveHolder:       updated,
				Reason:             "ALREADY_HOLDING_FLOOR",
				TimestampMs:        nowMs,
			}
		}

		// TRƯỜNG HỢP 3: PRIORITY OVERRIDE (Tổng đạo diễn P3 > Trưởng ban P2 > Cộng tác viên P1)
		if req.RolePriority > current.RolePriority {
			directorHolder := &FloorHolder{
				ChannelID:      req.ChannelID,
				ChannelCode:    channelCode,
				UserID:         reqUserID,
				DisplayName:    req.DisplayName,
				RoleTitle:      req.RoleTitle,
				UserLevel:      req.UserLevel,
				RolePriority:   req.RolePriority,
				GrantedAtMs:    nowMs,
				LastAudioPktMs: nowMs,
			}
			if slot.holder.CompareAndSwap(current, directorHolder) {
				fc.TotalPreemptions.Add(1)
				decision := FloorDecision{
					Status:             StatusFloorPreempted,
					ChannelID:          req.ChannelID,
					ChannelCode:        channelCode,
					RequesterCallState: "CallTalk",
					ListenerCallState:  "CallTake",
					RevokedCallState:   "CallCut",
					IsDucked80:         true,
					DuckingDb:          -12,
					ActiveHolder:       directorHolder,
					RevokedUserID:      current.UserID,
					RevokedDisplayName: current.DisplayName,
					Reason:             "DIRECTOR_PRIORITY_OVERRIDE",
					TimestampMs:        nowMs,
				}
				fc.emitEvent(decision)
				return decision
			}
			continue
		}

		// TRƯỜNG HỢP 4: Quyền ưu tiên thấp hơn hoặc bằng người đang nói -> Từ chối (FLOOR_DENIED -> CallBusy)
		fc.TotalDenied.Add(1)
		return FloorDecision{
			Status:             StatusFloorDenied,
			ChannelID:          req.ChannelID,
			ChannelCode:        channelCode,
			RequesterCallState: "CallBusy",
			ListenerCallState:  "CallTake",
			IsDucked80:         current.RolePriority == RoleDirector,
			DuckingDb:          duckDb,
			ActiveHolder:       current,
			Reason:             "CHANNEL_BUSY_BY_EQUAL_OR_HIGHER_PRIORITY",
			TimestampMs:        nowMs,
		}
	}
}

// ReleaseFloor giải phóng kênh khi người nói nhả nút PTT (chuyển các máy về CallIdle).
func (fc *FloorControlEngine) ReleaseFloor(channelID uint8, userID string) bool {
	if channelID == 0 || channelID > 32 {
		return false
	}
	userID = strings.TrimSpace(userID)
	slot := &fc.slots[channelID]
	current := slot.holder.Load()
	if current == nil || current.UserID != userID {
		return false
	}
	if slot.holder.CompareAndSwap(current, nil) {
		fc.emitEvent(FloorDecision{
			Status:             StatusFloorReleased,
			ChannelID:          channelID,
			ChannelCode:        fmt.Sprintf("ch_0%d", channelID),
			RequesterCallState: "CallIdle",
			ListenerCallState:  "CallIdle",
			IsDucked80:         false,
			DuckingDb:          0,
			Reason:             "USER_RELEASED_PTT",
			TimestampMs:        time.Now().UnixMilli(),
		})
		return true
	}
	return false
}

// ValidateAndTouchAudioPacket được gọi trên mỗi gói UDP âm thanh (50 gói/giây).
func (fc *FloorControlEngine) ValidateAndTouchAudioPacket(channelID uint8, senderID uint32) bool {
	if channelID == 0 || channelID > 32 {
		return false
	}
	senderStr := strconv.FormatUint(uint64(senderID), 10)
	slot := &fc.slots[channelID]
	current := slot.holder.Load()
	if current == nil || (current.UserID != senderStr && !strings.Contains(current.UserID, "_"+senderStr+"_")) {
		return false
	}

	nowMs := time.Now().UnixMilli()
	if fc.isHolderExpired(current, nowMs) {
		slot.holder.CompareAndSwap(current, nil)
		return false
	}

	updated := &FloorHolder{
		ChannelID:      current.ChannelID,
		ChannelCode:    current.ChannelCode,
		UserID:         current.UserID,
		DisplayName:    current.DisplayName,
		RoleTitle:      current.RoleTitle,
		UserLevel:      current.UserLevel,
		RolePriority:   current.RolePriority,
		GrantedAtMs:    current.GrantedAtMs,
		LastAudioPktMs: nowMs,
		UdpActive:      true,
	}
	slot.holder.CompareAndSwap(current, updated)
	return true
}

// TriggerDirectorEmergencyOverrideAllChannels cưỡng bức ngắt mic trên toàn bộ 5 kênh sự kiện.
func (fc *FloorControlEngine) TriggerDirectorEmergencyOverrideAllChannels(directorUserID string, directorName string) []FloorDecision {
	nowMs := time.Now().UnixMilli()
	directorUserID = strings.TrimSpace(directorUserID)
	if directorUserID == "" {
		directorUserID = "director_999"
	}
	decisions := make([]FloorDecision, 0, 5)

	for chID := uint8(1); chID <= 5; chID++ {
		chCode := fmt.Sprintf("ch_0%d", chID)
		slot := &fc.slots[chID]
		prev := slot.holder.Load()
		emergencyHolder := &FloorHolder{
			ChannelID:      chID,
			ChannelCode:    chCode,
			UserID:         directorUserID,
			DisplayName:    directorName,
			RoleTitle:      "Tổng đạo diễn (Khẩn cấp)",
			UserLevel:      1,
			RolePriority:   RoleDirector,
			GrantedAtMs:    nowMs,
			LastAudioPktMs: nowMs,
		}
		slot.holder.Store(emergencyHolder)

		var revokedID string
		var revokedName string
		if prev != nil && prev.UserID != directorUserID {
			revokedID = prev.UserID
			revokedName = prev.DisplayName
		}

		d := FloorDecision{
			Status:             StatusFloorPreempted,
			ChannelID:          chID,
			ChannelCode:        chCode,
			RequesterCallState: "CallTalk",
			ListenerCallState:  "CallTake",
			RevokedCallState:   "CallCut",
			IsDucked80:         true,
			DuckingDb:          -12,
			ActiveHolder:       emergencyHolder,
			RevokedUserID:      revokedID,
			RevokedDisplayName: revokedName,
			Reason:             "EMERGENCY_ALL_CHANNEL_OVERRIDE",
			TimestampMs:        nowMs,
		}
		fc.emitEvent(d)
		decisions = append(decisions, d)
	}
	return decisions
}

// RunWatchdogLoop tự động thu hồi mic nếu người nói giữ quá 60s (hoặc mất luồng UDP đang chạy > 2,5s).
func (fc *FloorControlEngine) RunWatchdogLoop(stopCh <-chan struct{}) {
	ticker := time.NewTicker(500 * time.Millisecond)
	defer ticker.Stop()

	for {
		select {
		case <-stopCh:
			return
		case <-ticker.C:
			nowMs := time.Now().UnixMilli()
			for chID := uint8(1); chID <= 5; chID++ {
				slot := &fc.slots[chID]
				current := slot.holder.Load()
				if current != nil && fc.isHolderExpired(current, nowMs) {
					if slot.holder.CompareAndSwap(current, nil) {
						fc.TotalAutoReleases.Add(1)
						fc.emitEvent(FloorDecision{
							Status:             StatusFloorReleased,
							ChannelID:          chID,
							ChannelCode:        fmt.Sprintf("ch_0%d", chID),
							RequesterCallState: "CallIdle",
							ListenerCallState:  "CallIdle",
							IsDucked80:         false,
							DuckingDb:          0,
							RevokedUserID:      current.UserID,
							RevokedDisplayName: current.DisplayName,
							Reason:             "WATCHDOG_TIMEOUT_AUTO_RELEASE",
							TimestampMs:        nowMs,
						})
					}
				}
			}
		}
	}
}

func (fc *FloorControlEngine) isHolderExpired(h *FloorHolder, nowMs int64) bool {
	if nowMs-h.GrantedAtMs >= MaxSpeakDurationMs {
		return true
	}
	if h.UdpActive && nowMs-h.LastAudioPktMs >= StaleUdpSilenceMs {
		return true
	}
	return false
}

func (fc *FloorControlEngine) emitEvent(d FloorDecision) {
	if fc.onFloorEvent != nil {
		fc.onFloorEvent(d)
	}
}

// HandleFloorRequestHTTP cung cấp REST API xin quyền nói: POST /api/v1/floor/request
func (fc *FloorControlEngine) HandleFloorRequestHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req FloorRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	fc.mu.RLock()
	resolver := fc.identityResolver
	fc.mu.RUnlock()

	if resolver != nil {
		id, ok := resolver.Resolve(string(req.UserID))
		if !ok {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{
				"error":   "USER_NOT_IN_EVENT",
				"message": "User not found in event",
			})
			return
		}
		req.UserID = FlexibleUserID(id.UserID)
		req.DisplayName = id.DisplayName
		req.RoleTitle = id.RoleTitle
		req.UserLevel = id.UserLevel
		req.RolePriority = id.RolePriority
	}

	decision := fc.RequestFloor(req)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(decision)
}

// HandleFloorReleaseHTTP cung cấp REST API nhả nút PTT: POST /api/v1/floor/release
func (fc *FloorControlEngine) HandleFloorReleaseHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		ChannelID uint8          `json:"channelId"`
		UserID    FlexibleUserID `json:"userId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	fc.mu.RLock()
	resolver := fc.identityResolver
	fc.mu.RUnlock()

	resolvedUserID := string(req.UserID)
	if resolver != nil {
		id, ok := resolver.Resolve(resolvedUserID)
		if !ok {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{
				"error":   "USER_NOT_IN_EVENT",
				"message": "User not found in event",
			})
			return
		}
		resolvedUserID = id.UserID
		req.UserID = FlexibleUserID(id.UserID)
	}

	released := fc.ReleaseFloor(req.ChannelID, resolvedUserID)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"released":  released,
		"channelId": req.ChannelID,
		"userId":    req.UserID,
	})
}

// HandleEmergencyOverrideHTTP cung cấp REST API phát khẩn cấp toàn sự kiện: POST /api/v1/floor/emergency-override
func (fc *FloorControlEngine) HandleEmergencyOverrideHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		UserID      FlexibleUserID `json:"userId"`
		DisplayName string         `json:"displayName"`
		UserLevel   uint8          `json:"userLevel"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	uid := strings.TrimSpace(string(req.UserID))
	if uid == "" {
		uid = "director_999"
	}
	displayName := strings.TrimSpace(req.DisplayName)
	if displayName == "" {
		displayName = "Tổng đạo diễn A"
	}
	userLevel := req.UserLevel

	fc.mu.RLock()
	resolver := fc.identityResolver
	fc.mu.RUnlock()

	if resolver != nil {
		id, ok := resolver.Resolve(uid)
		if !ok {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{
				"error":   "USER_NOT_IN_EVENT",
				"message": "User not found in event",
			})
			return
		}
		uid = id.UserID
		displayName = id.DisplayName
		userLevel = id.UserLevel
	}

	if userLevel != 1 {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.WriteHeader(http.StatusForbidden)
		_ = json.NewEncoder(w).Encode(map[string]string{
			"error":   "FORBIDDEN_NOT_ADMIN",
			"message": "Only Director (Level 1) can trigger emergency override",
		})
		return
	}

	decisions := fc.TriggerDirectorEmergencyOverrideAllChannels(uid, displayName)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status":    "EMERGENCY_OVERRIDE_ACTIVE",
		"decisions": decisions,
	})
}
