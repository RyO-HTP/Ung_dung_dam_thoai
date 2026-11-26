package week2routing

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

// Bổ sung AdminCode vào EventInfo
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

	em.mu.Lock()
	defer em.mu.Unlock()

	var targetEvent *EventInfo
	for _, ev := range em.eventsByPin {
		if ev.AdminCode == req.AdminCode {
			targetEvent = ev
			break
		}
	}

	if targetEvent == nil {
		writeJSON(w, http.StatusNotFound, map[string]interface{}{
			"error":   "ADMIN_CODE_NOT_FOUND",
			"message": "Mã quản trị sự kiện không tồn tại hoặc không chính xác",
		})
		return
	}

	if req.DisplayName == "" {
		req.DisplayName = "Tổng đạo diễn (Khôi phục)"
	}

	newUserID := em.userSeq.Add(1)
	secretBytes := make([]byte, 32)
	session := &UserSession{
		UserID:            newUserID,
		UserCode:          fmt.Sprintf("user_%d", newUserID),
		EventID:           targetEvent.EventID,
		DisplayName:       req.DisplayName,
		RoleTitle:         "Tổng đạo diễn",
		UserLevel:         1,
		RolePriority:      3,
		PrimaryTxChannel:  1,
		PrimaryChannelID:  "ch_01",
		ListeningChannels: []uint8{1, 2},
		HmacSecretBytes:   secretBytes,
	}
	em.usersByID[newUserID] = session

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"event":   targetEvent,
		"session": session,
	})
}

// HandleAddChannel xử lý POST /api/v1/events/{eventId}/channels {userId, name}
func (em *EventManager) HandleAddChannel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		UserID uint32 `json:"userId"`
		Name   string `json:"name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	em.mu.Lock()
	defer em.mu.Unlock()

	session, exists := em.usersByID[req.UserID]
	if !exists || session.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]string{"error": "FORBIDDEN_NOT_ADMIN"})
		return
	}

	// Tìm sự kiện của session
	var targetEvent *EventInfo
	for _, ev := range em.eventsByPin {
		if ev.EventID == session.EventID {
			targetEvent = ev
			break
		}
	}
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

	// Lấy channelId từ URL path đơn giản
	path := r.URL.Path
	parts := strings.Split(path, "/")
	channelIdStr := parts[len(parts)-1]

	var req struct {
		UserID uint32 `json:"userId"`
		Name   string `json:"name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	em.mu.Lock()
	defer em.mu.Unlock()

	session, exists := em.usersByID[req.UserID]
	if !exists || session.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]string{"error": "FORBIDDEN_NOT_ADMIN"})
		return
	}

	var targetEvent *EventInfo
	for _, ev := range em.eventsByPin {
		if ev.EventID == session.EventID {
			targetEvent = ev
			break
		}
	}
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

	path := r.URL.Path
	parts := strings.Split(path, "/")
	channelIdStr := parts[len(parts)-1]

	var req struct {
		UserID uint32 `json:"userId"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)

	em.mu.Lock()
	defer em.mu.Unlock()

	session, exists := em.usersByID[req.UserID]
	if !exists || session.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]string{"error": "FORBIDDEN_NOT_ADMIN"})
		return
	}

	var targetEvent *EventInfo
	for _, ev := range em.eventsByPin {
		if ev.EventID == session.EventID {
			targetEvent = ev
			break
		}
	}
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

	em.mu.RLock()
	members := make([]*UserSession, 0, len(em.usersByID))
	for _, u := range em.usersByID {
		members = append(members, u)
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

	var req struct {
		ActorUserID uint32 `json:"actorUserId"`
		UserLevel   uint8  `json:"userLevel"`
		RoleTitle   string `json:"roleTitle"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	em.mu.Lock()
	defer em.mu.Unlock()

	actor, exists := em.usersByID[req.ActorUserID]
	if !exists || actor.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]string{"error": "FORBIDDEN_NOT_ADMIN"})
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

	var req struct {
		ActorUserID uint32 `json:"actorUserId"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)

	em.mu.Lock()
	defer em.mu.Unlock()

	actor, exists := em.usersByID[req.ActorUserID]
	if !exists || actor.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]string{"error": "FORBIDDEN_NOT_ADMIN"})
		return
	}

	writeJSON(w, http.StatusOK, map[string]string{"status": "REMOVED"})
}
