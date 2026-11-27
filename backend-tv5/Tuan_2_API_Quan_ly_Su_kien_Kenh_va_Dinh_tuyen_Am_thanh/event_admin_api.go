package week2routing

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"
)

// EventInfoAdmin mở rộng EventInfo khi cần trả kèm mã quản trị.
type EventInfoAdmin struct {
	EventID     int64                  `json:"eventId"`
	EventName   string                 `json:"eventName"`
	Description string                 `json:"description"`
	PinCode     string                 `json:"pinCode"`
	AdminCode   string                 `json:"adminCode,omitempty"`
	CreatedAt   time.Time              `json:"createdAt"`
	Channels    map[uint8]*ChannelInfo `json:"channels"`
	ChannelList []*ChannelInfo         `json:"channelList"`
}

// HandleRestoreEvent xử lý POST /api/v1/events/restore {adminCode, displayName}
func (em *EventManager) HandleRestoreEvent(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		AdminCode   string `json:"adminCode"`
		DisplayName string `json:"displayName"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	trimmedCode := strings.TrimSpace(req.AdminCode)
	em.mu.Lock()
	var targetEvent *EventInfo
	if trimmedCode != "" {
		for _, ev := range em.eventsByPin {
			if strings.EqualFold(ev.AdminCode, trimmedCode) {
				targetEvent = ev
				break
			}
		}
	}
	if targetEvent == nil {
		em.mu.Unlock()
		writeJSON(w, http.StatusNotFound, map[string]interface{}{
			"error":   "ADMIN_CODE_NOT_FOUND",
			"message": "Mã quản trị sự kiện không tồn tại hoặc không chính xác",
		})
		return
	}

	if strings.TrimSpace(req.DisplayName) == "" {
		req.DisplayName = "Tổng đạo diễn (Khôi phục)"
	}

	newNumericID := em.userSeq.Add(1)
	uniqueUserID := em.generateUniqueUserIDLocked(req.DisplayName, newNumericID)
	secretBytes := make([]byte, 32)
	_, _ = rand.Read(secretBytes)
	sigURL, sigToken := SignalingConfigFromEnv()

	session := &UserSession{
		NumericID:         newNumericID,
		UserID:            uniqueUserID,
		UserCode:          uniqueUserID,
		EventID:           targetEvent.EventID,
		DisplayName:       req.DisplayName,
		RoleTitle:         "Tổng đạo diễn",
		UserLevel:         1,
		RolePriority:      3,
		PrimaryTxChannel:  1,
		PrimaryChannelID:  "ch_01",
		ListeningChannels: []uint8{1, 2},
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

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"userId":         session.UserID,
		"signalingUrl":   session.SignalingURL,
		"signalingToken": session.SignalingToken,
		"event":          targetEvent,
		"session":        session,
	})
}

// HandleAddChannel xử lý POST /api/v1/events/{eventId}/channels {userId, name}
func (em *EventManager) HandleAddChannel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		UserID FlexibleUserID `json:"userId"`
		Name   string         `json:"name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	em.mu.Lock()
	defer em.mu.Unlock()

	session := em.findSessionLocked(req.UserID)
	if session == nil || session.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]string{"error": "FORBIDDEN_NOT_ADMIN"})
		return
	}

	targetEvent := em.eventsByID[session.EventID]
	if targetEvent == nil {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "EVENT_NOT_FOUND"})
		return
	}

	if len(targetEvent.ChannelList) >= 8 {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "CHANNEL_LIMIT_REACHED", "message": "Tối đa 8 kênh mỗi sự kiện"})
		return
	}

	trimmedName := strings.TrimSpace(req.Name)
	if trimmedName == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "EMPTY_NAME"})
		return
	}

	for _, ch := range targetEvent.ChannelList {
		if strings.EqualFold(ch.Name, trimmedName) {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "DUPLICATE_NAME"})
			return
		}
	}

	newIDNum := uint8(len(targetEvent.ChannelList) + 1)
	for targetEvent.Channels[newIDNum] != nil {
		newIDNum++
	}
	newChID := fmt.Sprintf("ch_0%d", newIDNum)
	newCh := &ChannelInfo{
		ID:              newChID,
		ChannelID:       newIDNum,
		Name:            trimmedName,
		DepartmentCode:  "CUSTOM",
		DefaultPriority: 2,
		OnlineCount:     1,
		StatusText:      "Kênh mới tạo",
	}

	targetEvent.Channels[newIDNum] = newCh
	targetEvent.ChannelList = append(targetEvent.ChannelList, newCh)

	writeJSON(w, http.StatusCreated, newCh)
}

// HandleUpdateChannel xử lý PATCH /api/v1/events/{eventId}/channels/{channelId} {userId, name}
func (em *EventManager) HandleUpdateChannel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPatch && r.Method != http.MethodPut {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	parts := strings.Split(r.URL.Path, "/")
	channelIdStr := parts[len(parts)-1]

	var req struct {
		UserID FlexibleUserID `json:"userId"`
		Name   string         `json:"name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	em.mu.Lock()
	defer em.mu.Unlock()

	session := em.findSessionLocked(req.UserID)
	if session == nil || session.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]string{"error": "FORBIDDEN_NOT_ADMIN"})
		return
	}

	targetEvent := em.eventsByID[session.EventID]
	if targetEvent == nil {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "EVENT_NOT_FOUND"})
		return
	}

	var foundCh *ChannelInfo
	for _, ch := range targetEvent.ChannelList {
		if ch.ID == channelIdStr {
			foundCh = ch
			break
		}
	}
	if foundCh == nil {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "CHANNEL_NOT_FOUND"})
		return
	}

	trimmedName := strings.TrimSpace(req.Name)
	if trimmedName == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "EMPTY_NAME"})
		return
	}

	foundCh.Name = trimmedName
	writeJSON(w, http.StatusOK, foundCh)
}

// HandleDeleteChannel xử lý DELETE /api/v1/events/{eventId}/channels/{channelId} {userId}
func (em *EventManager) HandleDeleteChannel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodDelete {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	parts := strings.Split(r.URL.Path, "/")
	channelIdStr := parts[len(parts)-1]

	var req struct {
		UserID FlexibleUserID `json:"userId"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)

	em.mu.Lock()
	defer em.mu.Unlock()

	session := em.findSessionLocked(req.UserID)
	if session == nil || session.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]string{"error": "FORBIDDEN_NOT_ADMIN"})
		return
	}

	targetEvent := em.eventsByID[session.EventID]
	if targetEvent == nil {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "EVENT_NOT_FOUND"})
		return
	}

	if channelIdStr == "ch_01" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "CANNOT_DELETE_DEFAULT_CHANNEL", "message": "Không được xóa kênh Toàn sự kiện"})
		return
	}

	newList := make([]*ChannelInfo, 0, len(targetEvent.ChannelList))
	for _, ch := range targetEvent.ChannelList {
		if ch.ID != channelIdStr {
			newList = append(newList, ch)
		} else {
			delete(targetEvent.Channels, ch.ChannelID)
		}
	}
	targetEvent.ChannelList = newList

	writeJSON(w, http.StatusOK, map[string]interface{}{"status": "DELETED", "channelId": channelIdStr})
}

// HandleListMembers xử lý GET /api/v1/events/{eventId}/members
func (em *EventManager) HandleListMembers(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var filterEventID int64
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	if len(parts) >= 4 {
		if parsed, err := strconv.ParseInt(parts[3], 10, 64); err == nil && parsed > 0 {
			filterEventID = parsed
		}
	}

	em.mu.RLock()
	members := make([]*UserSession, 0, len(em.usersByID))
	for _, u := range em.usersByID {
		if filterEventID == 0 || u.EventID == filterEventID {
			members = append(members, u)
		}
	}
	em.mu.RUnlock()

	writeJSON(w, http.StatusOK, members)
}

// HandleUpdateMember xử lý PATCH /api/v1/events/{eventId}/members/{userId} {actorUserId, userLevel, roleTitle}
func (em *EventManager) HandleUpdateMember(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPatch && r.Method != http.MethodPut {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	parts := strings.Split(r.URL.Path, "/")
	targetIDStr := FlexibleUserID(parts[len(parts)-1])

	var req struct {
		ActorUserID FlexibleUserID `json:"actorUserId"`
		UserLevel   uint8          `json:"userLevel"`
		RoleTitle   string         `json:"roleTitle"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	em.mu.Lock()
	defer em.mu.Unlock()

	actor := em.findSessionLocked(req.ActorUserID)
	if actor == nil || actor.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]string{"error": "FORBIDDEN_NOT_ADMIN"})
		return
	}

	target := em.findSessionLocked(targetIDStr)
	if target != nil {
		lvl, prio, _ := ConvertUserLevelToPriority(req.UserLevel, 0)
		target.UserLevel = lvl
		target.RolePriority = prio
		if strings.TrimSpace(req.RoleTitle) != "" {
			target.RoleTitle = strings.TrimSpace(req.RoleTitle)
		}
		writeJSON(w, http.StatusOK, target)
		return
	}

	writeJSON(w, http.StatusOK, map[string]string{"status": "UPDATED"})
}

// HandleDeleteMember xử lý DELETE /api/v1/events/{eventId}/members/{userId} {actorUserId}
func (em *EventManager) HandleDeleteMember(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodDelete {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	parts := strings.Split(r.URL.Path, "/")
	targetIDStr := FlexibleUserID(parts[len(parts)-1])

	var req struct {
		ActorUserID FlexibleUserID `json:"actorUserId"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)

	em.mu.Lock()
	defer em.mu.Unlock()

	actor := em.findSessionLocked(req.ActorUserID)
	if actor == nil || actor.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]string{"error": "FORBIDDEN_NOT_ADMIN"})
		return
	}

	if target := em.findSessionLocked(targetIDStr); target != nil {
		delete(em.usersByID, target.NumericID)
		delete(em.usersByCode, target.UserID)
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{"status": "REMOVED", "userId": targetIDStr})
}
