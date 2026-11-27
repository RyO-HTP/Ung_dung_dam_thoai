package week2routing

import (
	"crypto/subtle"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"
)

var (
	restoreRateMu      sync.Mutex
	restoreRateRecords = make(map[string][]time.Time)
)

func checkRestoreRateLimit(r *http.Request) bool {
	ip := r.Header.Get("X-Forwarded-For")
	if ip == "" {
		ip = r.RemoteAddr
	} else {
		parts := strings.Split(ip, ",")
		ip = strings.TrimSpace(parts[0])
	}
	if idx := strings.LastIndex(ip, ":"); idx != -1 {
		ip = ip[:idx]
	}

	restoreRateMu.Lock()
	defer restoreRateMu.Unlock()

	now := time.Now()
	cutoff := now.Add(-1 * time.Minute)

	for k, times := range restoreRateRecords {
		valid := times[:0]
		for _, t := range times {
			if t.After(cutoff) {
				valid = append(valid, t)
			}
		}
		if len(valid) == 0 {
			delete(restoreRateRecords, k)
		} else {
			restoreRateRecords[k] = valid
		}
	}

	attempts := restoreRateRecords[ip]
	validAttempts := attempts[:0]
	for _, t := range attempts {
		if t.After(cutoff) {
			validAttempts = append(validAttempts, t)
		}
	}

	if len(validAttempts) >= 5 {
		restoreRateRecords[ip] = validAttempts
		return false
	}

	validAttempts = append(validAttempts, now)
	restoreRateRecords[ip] = validAttempts
	return true
}

func isValidAdminCode(code string) bool {
	if len(code) < 6 || len(code) > 32 {
		return false
	}
	for _, ch := range code {
		if !((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '-') {
			return false
		}
	}
	return true
}

// HandleRestoreEvent xử lý POST /api/v1/events/restore
func (em *EventManager) HandleRestoreEvent(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]interface{}{
			"error":   "METHOD_NOT_ALLOWED",
			"message": "Method not allowed",
		})
		return
	}

	if !checkRestoreRateLimit(r) {
		writeJSON(w, http.StatusTooManyRequests, map[string]interface{}{
			"error":   "RATE_LIMITED",
			"message": "Quá giới hạn thử lại, vui lòng thử lại sau 1 phút",
		})
		return
	}

	var req struct {
		AdminCode   string `json:"adminCode"`
		DisplayName string `json:"displayName"`
		RoleTitle   string `json:"roleTitle"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	adminCode := strings.TrimSpace(req.AdminCode)
	if adminCode == "" {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{
			"error":   "ADMIN_CODE_EMPTY",
			"message": "Mã quản trị không được để trống",
		})
		return
	}

	em.mu.Lock()
	var matchedEvent *EventInfo
	normReqCode := []byte(strings.ToLower(adminCode))

	for _, ev := range em.eventsByID {
		if ev.AdminCode != "" {
			normEvCode := []byte(strings.ToLower(ev.AdminCode))
			if len(normReqCode) == len(normEvCode) && subtle.ConstantTimeCompare(normReqCode, normEvCode) == 1 {
				matchedEvent = ev
			}
		}
	}

	if matchedEvent == nil {
		em.mu.Unlock()
		writeJSON(w, http.StatusNotFound, map[string]interface{}{
			"error":   "ADMIN_CODE_NOT_FOUND",
			"message": "Mã quản trị không tồn tại",
		})
		return
	}

	// Hạ mọi phiên Cấp 1 cũ của sự kiện xuống Cấp 3
	for _, sess := range em.usersByID {
		if sess.EventID == matchedEvent.EventID && sess.UserLevel == 1 {
			sess.UserLevel = 3
			sess.RolePriority = 1
			sess.RoleTitle = "Cộng tác viên"
		}
	}
	em.mu.Unlock()

	displayName := strings.TrimSpace(req.DisplayName)
	if displayName == "" {
		displayName = "Tổng đạo diễn"
	}
	roleTitle := strings.TrimSpace(req.RoleTitle)
	if roleTitle == "" {
		roleTitle = "Tổng đạo diễn"
	}

	session := em.CreateSession(matchedEvent, displayName, roleTitle, 1, 3, 1, nil)

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"event":   matchedEvent,
		"session": session,
	})
}

// Extract eventId and actorUserId helper
func extractEventAndActor(r *http.Request) (int64, string, error) {
	parts := strings.Split(strings.TrimPrefix(r.URL.Path, "/api/v1/events/"), "/")
	if len(parts) == 0 || parts[0] == "" {
		return 0, "", fmt.Errorf("EVENT_ID_REQUIRED")
	}
	eventID, err := strconv.ParseInt(parts[0], 10, 64)
	if err != nil {
		return 0, "", fmt.Errorf("INVALID_EVENT_ID")
	}
	actorUserId := r.URL.Query().Get("actorUserId")
	if actorUserId == "" {
		actorUserId = r.Header.Get("X-Actor-User-Id")
	}
	return eventID, actorUserId, nil
}

// A3: HandleGetEvent
func (em *EventManager) HandleGetEvent(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]interface{}{"error": "METHOD_NOT_ALLOWED"})
		return
	}
	eventID, actorUserId, err := extractEventAndActor(r)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": err.Error()})
		return
	}

	_, err = em.requireMember(eventID, FlexibleUserID(actorUserId))
	if err != nil {
		writeJSON(w, http.StatusForbidden, map[string]interface{}{
			"error":   "USER_NOT_IN_EVENT",
			"message": "User not in event or event mismatch",
		})
		return
	}

	em.mu.Lock()
	ev, exists := em.eventsByID[eventID]
	if !exists {
		em.mu.Unlock()
		writeJSON(w, http.StatusNotFound, map[string]interface{}{"error": "EVENT_NOT_FOUND"})
		return
	}

	memberCount := 0
	channelListeners := make(map[uint8]int)
	for _, sess := range em.usersByID {
		if sess.EventID == eventID {
			memberCount++
			for _, chID := range sess.ListeningChannels {
				channelListeners[chID]++
			}
		}
	}

	ev.MemberCount = memberCount
	for chID, chInfo := range ev.Channels {
		chInfo.OnlineCount = channelListeners[chID]
		chInfo.StatusText = ""
	}
	for _, chInfo := range ev.ChannelList {
		if count, ok := channelListeners[chInfo.ChannelID]; ok {
			chInfo.OnlineCount = count
		} else {
			chInfo.OnlineCount = 0
		}
		chInfo.StatusText = ""
	}

	evCopy := *ev
	em.mu.Unlock()

	writeJSON(w, http.StatusOK, evCopy)
}

// A4: Channels APIs
func (em *EventManager) HandleAddChannel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]interface{}{"error": "METHOD_NOT_ALLOWED"})
		return
	}
	eventID, actorUserId, err := extractEventAndActor(r)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": err.Error()})
		return
	}

	_, err = em.requireMember(eventID, FlexibleUserID(actorUserId))
	if err != nil {
		writeJSON(w, http.StatusForbidden, map[string]interface{}{"error": "USER_NOT_IN_EVENT"})
		return
	}

	var req struct {
		Name            string `json:"name"`
		DepartmentCode  string `json:"departmentCode"`
		DefaultPriority uint8  `json:"defaultPriority"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	req.Name = strings.TrimSpace(req.Name)
	if req.Name == "" {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": "CHANNEL_NAME_REQUIRED"})
		return
	}

	em.mu.Lock()
	ev, exists := em.eventsByID[eventID]
	if !exists {
		em.mu.Unlock()
		writeJSON(w, http.StatusNotFound, map[string]interface{}{"error": "EVENT_NOT_FOUND"})
		return
	}

	for _, ch := range ev.Channels {
		if strings.EqualFold(ch.Name, req.Name) {
			em.mu.Unlock()
			writeJSON(w, http.StatusConflict, map[string]interface{}{"error": "CHANNEL_NAME_TAKEN"})
			return
		}
	}

	var newChID uint8 = 0
	for id := uint8(1); id <= 10; id++ {
		if _, exists := ev.Channels[id]; !exists {
			newChID = id
			break
		}
	}
	if newChID == 0 {
		em.mu.Unlock()
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": "MAX_CHANNELS_REACHED"})
		return
	}

	if req.DepartmentCode == "" {
		req.DepartmentCode = fmt.Sprintf("DEPT_%d", newChID)
	}
	if req.DefaultPriority == 0 {
		req.DefaultPriority = 2
	}

	chInfo := &ChannelInfo{
		ID:              fmt.Sprintf("ch_0%d", newChID),
		ChannelID:       newChID,
		Name:            req.Name,
		DepartmentCode:  req.DepartmentCode,
		DefaultPriority: req.DefaultPriority,
		OnlineCount:     0,
		StatusText:      "",
	}

	ev.Channels[newChID] = chInfo
	ev.ChannelList = append(ev.ChannelList, chInfo)
	em.mu.Unlock()

	writeJSON(w, http.StatusCreated, chInfo)
}

func (em *EventManager) HandleUpdateChannel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPatch && r.Method != http.MethodPut {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]interface{}{"error": "METHOD_NOT_ALLOWED"})
		return
	}
	eventID, actorUserId, err := extractEventAndActor(r)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": err.Error()})
		return
	}

	_, err = em.requireMember(eventID, FlexibleUserID(actorUserId))
	if err != nil {
		writeJSON(w, http.StatusForbidden, map[string]interface{}{"error": "USER_NOT_IN_EVENT"})
		return
	}

	parts := strings.Split(strings.TrimPrefix(r.URL.Path, "/api/v1/events/"), "/")
	if len(parts) < 3 {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": "CHANNEL_ID_REQUIRED"})
		return
	}
	chIdStr := parts[2]
	var targetChID uint8
	if strings.HasPrefix(chIdStr, "ch_") {
		if parsed, err := strconv.ParseUint(strings.TrimPrefix(chIdStr, "ch_"), 10, 8); err == nil {
			targetChID = uint8(parsed)
		}
	} else {
		if parsed, err := strconv.ParseUint(chIdStr, 10, 8); err == nil {
			targetChID = uint8(parsed)
		}
	}

	var req struct {
		Name            string `json:"name"`
		DepartmentCode  string `json:"departmentCode"`
		DefaultPriority uint8  `json:"defaultPriority"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)

	em.mu.Lock()
	ev, exists := em.eventsByID[eventID]
	if !exists {
		em.mu.Unlock()
		writeJSON(w, http.StatusNotFound, map[string]interface{}{"error": "EVENT_NOT_FOUND"})
		return
	}

	chInfo, exists := ev.Channels[targetChID]
	if !exists {
		em.mu.Unlock()
		writeJSON(w, http.StatusNotFound, map[string]interface{}{"error": "CHANNEL_NOT_FOUND"})
		return
	}

	if req.Name != "" && !strings.EqualFold(req.Name, chInfo.Name) {
		for id, ch := range ev.Channels {
			if id != targetChID && strings.EqualFold(ch.Name, req.Name) {
				em.mu.Unlock()
				writeJSON(w, http.StatusConflict, map[string]interface{}{"error": "CHANNEL_NAME_TAKEN"})
				return
			}
		}
		chInfo.Name = req.Name
	}
	if req.DepartmentCode != "" {
		chInfo.DepartmentCode = req.DepartmentCode
	}
	if req.DefaultPriority > 0 {
		chInfo.DefaultPriority = req.DefaultPriority
	}
	em.mu.Unlock()

	writeJSON(w, http.StatusOK, chInfo)
}

func (em *EventManager) HandleDeleteChannel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodDelete {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]interface{}{"error": "METHOD_NOT_ALLOWED"})
		return
	}
	eventID, actorUserId, err := extractEventAndActor(r)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": err.Error()})
		return
	}

	caller, err := em.requireMember(eventID, FlexibleUserID(actorUserId))
	if err != nil {
		writeJSON(w, http.StatusForbidden, map[string]interface{}{"error": "USER_NOT_IN_EVENT"})
		return
	}
	if caller.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]interface{}{"error": "INSUFFICIENT_PERMISSIONS"})
		return
	}

	parts := strings.Split(strings.TrimPrefix(r.URL.Path, "/api/v1/events/"), "/")
	if len(parts) < 3 {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": "CHANNEL_ID_REQUIRED"})
		return
	}
	chIdStr := parts[2]
	var targetChID uint8
	if strings.HasPrefix(chIdStr, "ch_") {
		if parsed, err := strconv.ParseUint(strings.TrimPrefix(chIdStr, "ch_"), 10, 8); err == nil {
			targetChID = uint8(parsed)
		}
	} else {
		if parsed, err := strconv.ParseUint(chIdStr, 10, 8); err == nil {
			targetChID = uint8(parsed)
		}
	}

	if targetChID == 1 {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": "CANNOT_DELETE_DEFAULT_CHANNEL"})
		return
	}

	em.mu.Lock()
	ev, exists := em.eventsByID[eventID]
	if !exists {
		em.mu.Unlock()
		writeJSON(w, http.StatusNotFound, map[string]interface{}{"error": "EVENT_NOT_FOUND"})
		return
	}

	if _, exists := ev.Channels[targetChID]; !exists {
		em.mu.Unlock()
		writeJSON(w, http.StatusNotFound, map[string]interface{}{"error": "CHANNEL_NOT_FOUND"})
		return
	}

	delete(ev.Channels, targetChID)
	newChannelList := make([]*ChannelInfo, 0, len(ev.ChannelList)-1)
	for _, ch := range ev.ChannelList {
		if ch.ChannelID != targetChID {
			newChannelList = append(newChannelList, ch)
		}
	}
	ev.ChannelList = newChannelList

	var sessionsToUpdate []struct {
		numericID         uint32
		listeningChannels []uint8
	}

	for _, sess := range em.usersByID {
		if sess.EventID != eventID {
			continue
		}
		updated := false
		if sess.PrimaryTxChannel == targetChID {
			sess.PrimaryTxChannel = 1
			sess.PrimaryChannelID = "ch_01"
			updated = true
		}
		hasDeleted := false
		var newListening []uint8
		for _, ch := range sess.ListeningChannels {
			if ch == targetChID {
				hasDeleted = true
			} else {
				newListening = append(newListening, ch)
			}
		}
		if hasDeleted {
			updated = true
			sess.ListeningChannels = ensureValidListeningChannels(sess.PrimaryTxChannel, newListening)
		}
		if updated {
			sessionsToUpdate = append(sessionsToUpdate, struct {
				numericID         uint32
				listeningChannels []uint8
			}{sess.NumericID, sess.ListeningChannels})
		}
	}
	em.mu.Unlock()

	if em.audioRouter != nil {
		for _, u := range sessionsToUpdate {
			em.audioRouter.UpdateUserSubscribedChannels(u.numericID, u.listeningChannels)
		}
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":    "CHANNEL_DELETED",
		"channelId": targetChID,
	})
}

// A4: Member DTO & APIs
type MemberDto struct {
	NumericID         uint32        `json:"numericUserId"`
	UserID            string        `json:"userId"`
	UserCode          string        `json:"userCode"`
	EventID           int64         `json:"eventId"`
	DisplayName       string        `json:"displayName"`
	RoleTitle         string        `json:"roleTitle"`
	UserLevel         uint8         `json:"userLevel"`
	RolePriority      uint8         `json:"rolePriority"`
	PrimaryTxChannel  uint8         `json:"primaryTxChannel"`
	PrimaryChannelID  string        `json:"primaryChannelId"`
	ListeningChannels ChannelIDList `json:"listeningChannels"`
	SignalingURL      string        `json:"signalingUrl"`
	SignalingToken    string        `json:"signalingToken"`
	Online            bool          `json:"online"`
}

func sessionToMemberDto(s *UserSession, isOnline bool) MemberDto {
	return MemberDto{
		NumericID:         s.NumericID,
		UserID:            s.UserID,
		UserCode:          s.UserCode,
		EventID:           s.EventID,
		DisplayName:       s.DisplayName,
		RoleTitle:         s.RoleTitle,
		UserLevel:         s.UserLevel,
		RolePriority:      s.RolePriority,
		PrimaryTxChannel:  s.PrimaryTxChannel,
		PrimaryChannelID:  s.PrimaryChannelID,
		ListeningChannels: s.ListeningChannels,
		SignalingURL:      s.SignalingURL,
		SignalingToken:    s.SignalingToken,
		Online:            isOnline,
	}
}

func (em *EventManager) HandleListMembers(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]interface{}{"error": "METHOD_NOT_ALLOWED"})
		return
	}
	eventID, actorUserId, err := extractEventAndActor(r)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": err.Error()})
		return
	}

	_, err = em.requireMember(eventID, FlexibleUserID(actorUserId))
	if err != nil {
		writeJSON(w, http.StatusForbidden, map[string]interface{}{"error": "USER_NOT_IN_EVENT"})
		return
	}

	em.mu.RLock()
	var members []MemberDto
	for _, sess := range em.usersByID {
		if sess.EventID == eventID {
			online := false
			if em.audioRouter != nil {
				online = em.audioRouter.IsUserRegistered(sess.NumericID)
			}
			members = append(members, sessionToMemberDto(sess, online))
		}
	}
	em.mu.RUnlock()

	writeJSON(w, http.StatusOK, members)
}

func (em *EventManager) HandleUpdateMember(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPatch && r.Method != http.MethodPut {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]interface{}{"error": "METHOD_NOT_ALLOWED"})
		return
	}
	eventID, actorUserId, err := extractEventAndActor(r)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": err.Error()})
		return
	}

	caller, err := em.requireMember(eventID, FlexibleUserID(actorUserId))
	if err != nil {
		writeJSON(w, http.StatusForbidden, map[string]interface{}{"error": "USER_NOT_IN_EVENT"})
		return
	}
	if caller.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]interface{}{"error": "INSUFFICIENT_PERMISSIONS"})
		return
	}

	parts := strings.Split(strings.TrimPrefix(r.URL.Path, "/api/v1/events/"), "/")
	if len(parts) < 3 {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": "USER_ID_REQUIRED"})
		return
	}
	targetRef := FlexibleUserID(parts[2])

	var req struct {
		UserLevel    uint8  `json:"userLevel"`
		RoleTitle    string `json:"roleTitle"`
		RolePriority uint8  `json:"rolePriority"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)

	if req.UserLevel < 1 || req.UserLevel > 3 {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": "INVALID_LEVEL"})
		return
	}

	em.mu.Lock()
	target := em.findSessionLocked(targetRef)
	if target == nil || target.EventID != eventID {
		em.mu.Unlock()
		writeJSON(w, http.StatusNotFound, map[string]interface{}{"error": "USER_NOT_FOUND"})
		return
	}

	if target.UserLevel == 1 && req.UserLevel > 1 {
		level1Count := 0
		for _, sess := range em.usersByID {
			if sess.EventID == eventID && sess.UserLevel == 1 {
				level1Count++
			}
		}
		if level1Count <= 1 {
			em.mu.Unlock()
			writeJSON(w, http.StatusConflict, map[string]interface{}{"error": "LAST_ADMIN"})
			return
		}
	}

	level, priority, defaultTitle := ConvertUserLevelToPriority(req.UserLevel, req.RolePriority)
	target.UserLevel = level
	target.RolePriority = priority
	if strings.TrimSpace(req.RoleTitle) != "" {
		target.RoleTitle = req.RoleTitle
	} else {
		target.RoleTitle = defaultTitle
	}

	online := false
	if em.audioRouter != nil {
		online = em.audioRouter.IsUserRegistered(target.NumericID)
	}
	dto := sessionToMemberDto(target, online)
	em.mu.Unlock()

	writeJSON(w, http.StatusOK, dto)
}

func (em *EventManager) HandleDeleteMember(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodDelete {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]interface{}{"error": "METHOD_NOT_ALLOWED"})
		return
	}
	eventID, actorUserId, err := extractEventAndActor(r)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": err.Error()})
		return
	}

	caller, err := em.requireMember(eventID, FlexibleUserID(actorUserId))
	if err != nil {
		writeJSON(w, http.StatusForbidden, map[string]interface{}{"error": "USER_NOT_IN_EVENT"})
		return
	}
	if caller.UserLevel != 1 {
		writeJSON(w, http.StatusForbidden, map[string]interface{}{"error": "INSUFFICIENT_PERMISSIONS"})
		return
	}

	parts := strings.Split(strings.TrimPrefix(r.URL.Path, "/api/v1/events/"), "/")
	if len(parts) < 3 {
		writeJSON(w, http.StatusBadRequest, map[string]interface{}{"error": "USER_ID_REQUIRED"})
		return
	}
	targetRef := FlexibleUserID(parts[2])

	em.mu.Lock()
	target := em.findSessionLocked(targetRef)
	if target == nil || target.EventID != eventID {
		em.mu.Unlock()
		writeJSON(w, http.StatusNotFound, map[string]interface{}{"error": "USER_NOT_FOUND"})
		return
	}

	if target.UserLevel == 1 {
		level1Count := 0
		for _, sess := range em.usersByID {
			if sess.EventID == eventID && sess.UserLevel == 1 {
				level1Count++
			}
		}
		if level1Count <= 1 {
			em.mu.Unlock()
			writeJSON(w, http.StatusConflict, map[string]interface{}{"error": "LAST_ADMIN"})
			return
		}
	}

	delete(em.usersByID, target.NumericID)
	delete(em.usersByCode, target.UserID)
	if target.UserCode != target.UserID {
		delete(em.usersByCode, target.UserCode)
	}
	numericID := target.NumericID
	targetCopy := *target
	hook := em.OnMemberRemoved
	em.mu.Unlock()

	if em.audioRouter != nil {
		em.audioRouter.UnregisterUserSession(numericID)
	}

	if hook != nil {
		hook(targetCopy)
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status": "MEMBER_REMOVED",
		"userId": targetRef,
	})
}
