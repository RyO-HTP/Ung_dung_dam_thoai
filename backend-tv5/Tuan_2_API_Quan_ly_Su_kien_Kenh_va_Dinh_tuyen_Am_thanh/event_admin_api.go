package week2routing

import (
	"crypto/subtle"
	"encoding/json"
	"net/http"
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

// Stubs for A4 admin/channel/member API methods
func (em *EventManager) HandleAddChannel(w http.ResponseWriter, r *http.Request)    {}
func (em *EventManager) HandleUpdateChannel(w http.ResponseWriter, r *http.Request) {}
func (em *EventManager) HandleDeleteChannel(w http.ResponseWriter, r *http.Request) {}
func (em *EventManager) HandleListMembers(w http.ResponseWriter, r *http.Request)   {}
func (em *EventManager) HandleUpdateMember(w http.ResponseWriter, r *http.Request)  {}
func (em *EventManager) HandleDeleteMember(w http.ResponseWriter, r *http.Request)  {}
