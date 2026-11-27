package week5livecue

import (
	"encoding/json"
	"net/http"
	"strconv"
	"sync"
	"time"

	"damthoai/nguoi5server/identity"
	"github.com/gorilla/websocket"
)

type CueStatus string

const (
	CueCompleted   CueStatus = "COMPLETED"
	CueDone        CueStatus = "DONE"
	CueActiveNow   CueStatus = "ACTIVE_NOW"
	CueActive      CueStatus = "ACTIVE"
	CueNextStandby CueStatus = "NEXT_STANDBY"
	CueUpcoming    CueStatus = "UPCOMING"
)

type CueItem struct {
	CueID             string          `json:"cueId"`
	TimeSpec          string          `json:"timeSpec"`
	Title             string          `json:"title"`
	Message           string          `json:"message"`
	ChannelName       string          `json:"channelName"`
	IsPriority        bool            `json:"isPriority"`
	LiveCueHeader     string          `json:"liveCueHeader"`
	LiveCueTitle      string          `json:"liveCueTitle"`
	LiveCueSubtitle   string          `json:"liveCueSubtitle"`
	BannerText        string          `json:"bannerText"`
	DetailInstruction string          `json:"detailInstruction"`
	TargetDepartments []string        `json:"targetDepartments"`
	Status            CueStatus       `json:"status"`
	FiredByDirector   string          `json:"firedByDirector,omitempty"`
	FiredAtMs         int64           `json:"firedAtMs,omitempty"`
	AckedDepartments  map[string]bool `json:"ackedDepartments"`
}

type LiveCueBannerPayload struct {
	Type            string     `json:"type"`
	EventID         int64      `json:"eventId"`
	Revision        int64      `json:"revision"`
	Title           string     `json:"title"`
	Message         string     `json:"message"`
	ChannelName     string     `json:"channelName"`
	IsPriority      bool       `json:"isPriority"`
	LiveCueHeader   string     `json:"liveCueHeader"`
	LiveCueTitle    string     `json:"liveCueTitle"`
	LiveCueSubtitle string     `json:"liveCueSubtitle"`
	ActiveCue       *CueItem   `json:"activeCue"`
	NextCue         *CueItem   `json:"nextCue"`
	AllCues         []*CueItem `json:"allCues"`
	ServerTimeMs    int64      `json:"serverTimeMs"`
}

type CueWsCommand struct {
	Action          string `json:"action"`
	CueID           string `json:"cueId"`
	UserID          string `json:"userId"`
	EventID         int64  `json:"eventId"`
	Title           string `json:"title"`
	Message         string `json:"message"`
	ChannelName     string `json:"channelName"`
	IsPriority      *bool  `json:"isPriority"`
	LiveCueTitle    string `json:"liveCueTitle"`
	LiveCueSubtitle string `json:"liveCueSubtitle"`
	CustomBanner    string `json:"customBanner"`
	CustomSubtitle  string `json:"customSubtitle"`
	ActorName       string `json:"actorName"`
	Department      string `json:"department"`
}

type CueSubscriber struct {
	UserID     string
	Department string
	Conn       *websocket.Conn
	SendChan   chan []byte
}

type LiveCueBannerHub struct {
	mu          sync.RWMutex
	eventID     int64
	revision    int64
	cues        []*CueItem
	subscribers map[string]*CueSubscriber
	upgrader    websocket.Upgrader
	resolver    identity.Resolver
}

type LiveCueRegistry struct {
	mu       sync.RWMutex
	resolver identity.Resolver
	hubs     map[int64]*LiveCueBannerHub
}

func NewLiveCueRegistry(resolver identity.Resolver) *LiveCueRegistry {
	return &LiveCueRegistry{
		resolver: resolver,
		hubs:     make(map[int64]*LiveCueBannerHub),
	}
}

func (reg *LiveCueRegistry) GetHub(eventID int64) *LiveCueBannerHub {
	if eventID == 0 {
		eventID = 1
	}
	reg.mu.Lock()
	defer reg.mu.Unlock()
	if hub, exists := reg.hubs[eventID]; exists {
		return hub
	}
	hub := NewEmptyLiveCueBannerHub(eventID)
	hub.resolver = reg.resolver
	reg.hubs[eventID] = hub
	return hub
}

func NewEmptyLiveCueBannerHub(eventID int64) *LiveCueBannerHub {
	return &LiveCueBannerHub{
		eventID:     eventID,
		revision:    1,
		cues:        []*CueItem{},
		subscribers: make(map[string]*CueSubscriber),
		upgrader: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool { return true },
		},
	}
}

func NewLiveCueBannerHub(eventID int64) *LiveCueBannerHub {
	hub := NewEmptyLiveCueBannerHub(eventID)
	hub.cues = []*CueItem{
		{
			CueID:             "C01",
			TimeSpec:          "19:30",
			Title:             "Tiết mục 1 – Mở màn Intro Ánh sáng & Khói lạnh",
			Message:           "Kỹ thuật: tắt đèn khán phòng, bật laser intro",
			ChannelName:       "Toàn sự kiện",
			IsPriority:        true,
			LiveCueHeader:     "LIVE CUE · ĐÃ QUA",
			LiveCueTitle:      "Tiết mục 1 – Mở màn Intro Ánh sáng & Khói lạnh",
			LiveCueSubtitle:   "Kỹ thuật: tắt đèn khán phòng, bật laser intro",
			BannerText:        "🎬 Tiết mục 1 – Mở màn Intro Ánh sáng & Khói lạnh",
			DetailInstruction: "Kỹ thuật: tắt đèn khán phòng, bật laser intro",
			TargetDepartments: []string{"TECHNICAL", "STAGE"},
			Status:            CueCompleted,
			AckedDepartments:  map[string]bool{"TECHNICAL": true, "STAGE": true},
		},
		{
			CueID:             "C02",
			TimeSpec:          "19:35",
			Title:             "Tiết mục 2 – Phát biểu Khai mạc Đêm nhạc",
			Message:           "Sân khấu: mở Mic cổ ngỗng bục phát biểu",
			ChannelName:       "Toàn sự kiện",
			IsPriority:        true,
			LiveCueHeader:     "LIVE CUE · ĐÃ QUA",
			LiveCueTitle:      "Tiết mục 2 – Phát biểu Khai mạc Đêm nhạc",
			LiveCueSubtitle:   "Sân khấu: mở Mic cổ ngỗng bục phát biểu",
			BannerText:        "🎤 Tiết mục 2 – Phát biểu Khai mạc Đêm nhạc",
			DetailInstruction: "Sân khấu: mở Mic cổ ngỗng bục phát biểu",
			TargetDepartments: []string{"STAGE", "TECHNICAL"},
			Status:            CueCompleted,
			AckedDepartments:  map[string]bool{"STAGE": true, "TECHNICAL": true},
		},
		{
			CueID:             "C03",
			TimeSpec:          "19:42",
			Title:             "Tiết mục 3 – Nhảy hiện đại CLB Sinh viên",
			Message:           "An ninh: giữ khoảng cách hàng rào sân khấu 2m",
			ChannelName:       "Toàn sự kiện",
			IsPriority:        true,
			LiveCueHeader:     "LIVE CUE · ĐÃ QUA",
			LiveCueTitle:      "Tiết mục 3 – Nhảy hiện đại CLB Sinh viên",
			LiveCueSubtitle:   "An ninh: giữ khoảng cách hàng rào sân khấu 2m",
			BannerText:        "🔥 Tiết mục 3 – Nhảy hiện đại CLB Sinh viên",
			DetailInstruction: "An ninh: giữ khoảng cách hàng rào sân khấu 2m",
			TargetDepartments: []string{"LOGISTICS_SEC", "STAGE"},
			Status:            CueCompleted,
			AckedDepartments:  map[string]bool{"LOGISTICS_SEC": true},
		},
		{
			CueID:             "C04",
			TimeSpec:          "19:50",
			Title:             "Tiết mục 4 – Ban nhạc khách mời",
			Message:           "Hậu cần: chuẩn bị đạo cụ chuyển cảnh",
			ChannelName:       "Toàn sự kiện",
			IsPriority:        true,
			LiveCueHeader:     "LIVE CUE · ĐANG DIỄN",
			LiveCueTitle:      "Tiết mục 4 – Ban nhạc khách mời",
			LiveCueSubtitle:   "Hậu cần: chuẩn bị đạo cụ chuyển cảnh",
			BannerText:        "🎸 Tiết mục 4 – Ban nhạc khách mời — Hậu cần: chuẩn bị đạo cụ chuyển cảnh",
			DetailInstruction: "Hậu cần: chuẩn bị đạo cụ chuyển cảnh; Kỹ thuật: kiểm tra đường line guitar & trống.",
			TargetDepartments: []string{"LOGISTICS_SEC", "STAGE", "TECHNICAL"},
			Status:            CueActiveNow,
			FiredByDirector:   "Lan Phương (Tổng đạo diễn)",
			FiredAtMs:         time.Now().UnixMilli(),
			AckedDepartments:  map[string]bool{},
		},
		{
			CueID:             "C05",
			TimeSpec:          "20:05",
			Title:             "Tiết mục 5 – Giao lưu Khán giả & Bốc thăm",
			Message:           "Đón tiếp: chuẩn bị 5 phần quà lên cánh gà phải",
			ChannelName:       "Toàn sự kiện",
			IsPriority:        true,
			LiveCueHeader:     "LIVE CUE · CHUẨN BỊ",
			LiveCueTitle:      "Tiết mục 5 – Giao lưu Khán giả & Bốc thăm",
			LiveCueSubtitle:   "Đón tiếp: chuẩn bị 5 phần quà lên cánh gà phải",
			BannerText:        "🎁 Tiết mục 5 – Giao lưu Khán giả & Bốc thăm",
			DetailInstruction: "Đón tiếp: chuẩn bị 5 phần quà lên cánh gà phải",
			TargetDepartments: []string{"RECEPTION", "STAGE"},
			Status:            CueNextStandby,
			AckedDepartments:  map[string]bool{},
		},
	}
	return hub
}

func resolveCommandText(cmd CueWsCommand) (title string, subtitle string, channelName string, isPriority bool) {
	title = cmd.Title
	if title == "" {
		title = cmd.LiveCueTitle
	}
	if title == "" {
		title = cmd.CustomBanner
	}
	subtitle = cmd.Message
	if subtitle == "" {
		subtitle = cmd.LiveCueSubtitle
	}
	if subtitle == "" {
		subtitle = cmd.CustomSubtitle
	}
	channelName = cmd.ChannelName
	if channelName == "" {
		channelName = "Toàn sự kiện"
	}
	isPriority = true
	if cmd.IsPriority != nil {
		isPriority = *cmd.IsPriority
	}
	return title, subtitle, channelName, isPriority
}

func (h *LiveCueBannerHub) FireOrCreateModelCue(cueID string, directorName string, customTitle string, customSubtitle string) LiveCueBannerPayload {
	h.mu.Lock()
	targetIdx := -1
	if cueID != "" {
		for i, c := range h.cues {
			if c.CueID == cueID {
				targetIdx = i
				break
			}
		}
	}

	nowMs := time.Now().UnixMilli()
	if targetIdx >= 0 {
		for i, c := range h.cues {
			switch {
			case i < targetIdx:
				c.Status = CueCompleted
				c.LiveCueHeader = "LIVE CUE · ĐÃ QUA"
			case i == targetIdx:
				c.Status = CueActiveNow
				c.LiveCueHeader = "LIVE CUE · ĐANG DIỄN"
				c.FiredByDirector = directorName
				c.FiredAtMs = nowMs
				if customTitle != "" {
					c.Title = customTitle
					c.LiveCueTitle = customTitle
					c.BannerText = customTitle
				}
				if customSubtitle != "" {
					c.Message = customSubtitle
					c.LiveCueSubtitle = customSubtitle
				}
			case i == targetIdx+1:
				c.Status = CueNextStandby
				c.LiveCueHeader = "LIVE CUE · CHUẨN BỊ"
			default:
				c.Status = CueUpcoming
			}
		}
	} else {
		for _, c := range h.cues {
			if c.Status == CueActiveNow || c.Status == CueActive {
				c.Status = CueDone
				c.LiveCueHeader = "LIVE CUE · ĐÃ QUA"
			}
		}
		newCueID := cueID
		if newCueID == "" {
			newCueID = "CUE-" + time.Now().Format("150405")
		}
		if customTitle == "" {
			customTitle = "Tiết mục mới"
		}
		newCue := &CueItem{
			CueID:             newCueID,
			TimeSpec:          time.Now().Format("15:04"),
			Title:             customTitle,
			Message:           customSubtitle,
			ChannelName:       "Toàn sự kiện",
			IsPriority:        true,
			LiveCueHeader:     "LIVE CUE · ĐANG DIỄN",
			LiveCueTitle:      customTitle,
			LiveCueSubtitle:   customSubtitle,
			BannerText:        customTitle,
			DetailInstruction: customSubtitle,
			TargetDepartments: []string{"ALL_EVENT", "STAGE", "LOGISTICS_SEC", "TECHNICAL", "RECEPTION"},
			Status:            CueActiveNow,
			FiredByDirector:   directorName,
			FiredAtMs:         nowMs,
			AckedDepartments:  map[string]bool{},
		}
		h.cues = append(h.cues, newCue)
	}

	h.revision++
	snapshot := h.buildSnapshotLocked("LIVE_CUE_FIRED")
	h.mu.Unlock()

	h.broadcastPayload(snapshot)
	return snapshot
}

func (h *LiveCueBannerHub) FireCue(cueID string, directorName string, customTitle string, customSubtitle string) LiveCueBannerPayload {
	return h.FireOrCreateModelCue(cueID, directorName, customTitle, customSubtitle)
}

func (h *LiveCueBannerHub) AcknowledgeCue(cueID string, department string) LiveCueBannerPayload {
	h.mu.Lock()
	for _, c := range h.cues {
		if c.CueID == cueID {
			if c.AckedDepartments == nil {
				c.AckedDepartments = make(map[string]bool)
			}
			c.AckedDepartments[department] = true
			break
		}
	}
	h.revision++
	snapshot := h.buildSnapshotLocked("LIVE_CUE_ACK_UPDATED")
	h.mu.Unlock()

	h.broadcastPayload(snapshot)
	return snapshot
}

func (h *LiveCueBannerHub) buildSnapshotLocked(msgType string) LiveCueBannerPayload {
	var activeCue *CueItem
	var nextCue *CueItem
	for _, c := range h.cues {
		if (c.Status == CueActiveNow || c.Status == CueActive) && activeCue == nil {
			activeCue = c
		} else if c.Status == CueNextStandby && nextCue == nil {
			nextCue = c
		}
	}

	header := ""
	title := ""
	subtitle := ""
	channelName := ""
	isPriority := false

	if activeCue != nil {
		if activeCue.LiveCueHeader != "" {
			header = activeCue.LiveCueHeader
		} else {
			header = "LIVE CUE · ĐANG DIỄN"
		}
		if activeCue.LiveCueTitle != "" {
			title = activeCue.LiveCueTitle
		} else if activeCue.Title != "" {
			title = activeCue.Title
		}
		if activeCue.LiveCueSubtitle != "" {
			subtitle = activeCue.LiveCueSubtitle
		} else if activeCue.Message != "" {
			subtitle = activeCue.Message
		}
		if activeCue.ChannelName != "" {
			channelName = activeCue.ChannelName
		} else {
			channelName = "Toàn sự kiện"
		}
		isPriority = activeCue.IsPriority
	}

	allCues := h.cues
	if allCues == nil {
		allCues = []*CueItem{}
	}

	return LiveCueBannerPayload{
		Type:            msgType,
		EventID:         h.eventID,
		Revision:        h.revision,
		Title:           title,
		Message:         subtitle,
		ChannelName:     channelName,
		IsPriority:      isPriority,
		LiveCueHeader:   header,
		LiveCueTitle:    title,
		LiveCueSubtitle: subtitle,
		ActiveCue:       activeCue,
		NextCue:         nextCue,
		AllCues:         allCues,
		ServerTimeMs:    time.Now().UnixMilli(),
	}
}

// Snapshot trả về trạng thái hiện tại của hub mà không thay đổi gì (hub mới: không có cue nào).
func (h *LiveCueBannerHub) Snapshot() LiveCueBannerPayload {
	h.mu.Lock()
	defer h.mu.Unlock()
	return h.buildSnapshotLocked("LIVE_CUE_SYNC")
}

func (h *LiveCueBannerHub) broadcastPayload(payload LiveCueBannerPayload) {
	raw, err := json.Marshal(payload)
	if err != nil {
		return
	}
	h.mu.RLock()
	for _, sub := range h.subscribers {
		select {
		case sub.SendChan <- raw:
		default:
		}
	}
	h.mu.RUnlock()
}

func (h *LiveCueBannerHub) ServeLiveCueWSCore(w http.ResponseWriter, r *http.Request, userID string, dept string, eventID int64, userLevel uint8, sessionActorName string) {
	conn, err := h.upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}

	subKey := userID
	if subKey == "" {
		subKey = "anon-" + time.Now().Format("150405.000")
	}

	sub := &CueSubscriber{
		UserID:     userID,
		Department: dept,
		Conn:       conn,
		SendChan:   make(chan []byte, 64),
	}

	h.mu.Lock()
	h.subscribers[subKey] = sub
	initialSnapshot := h.buildSnapshotLocked("LIVE_CUE_BANNER_SYNC")
	h.mu.Unlock()

	if raw, err := json.Marshal(initialSnapshot); err == nil {
		sub.SendChan <- raw
	}

	defer func() {
		h.mu.Lock()
		if h.subscribers[subKey] == sub {
			delete(h.subscribers, subKey)
			close(sub.SendChan)
		}
		h.mu.Unlock()
		_ = conn.Close()
	}()

	go func() {
		for msg := range sub.SendChan {
			_ = conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if err := conn.WriteMessage(websocket.TextMessage, msg); err != nil {
				return
			}
		}
	}()

	for {
		_, rawCmd, err := conn.ReadMessage()
		if err != nil {
			break
		}
		var cmd CueWsCommand
		if err := json.Unmarshal(rawCmd, &cmd); err != nil {
			continue
		}
		customTitle, customSubtitle, _, _ := resolveCommandText(cmd)
		switch cmd.Action {
		case "FIRE_CUE", "UPDATE_BANNER_TEXT", "NEXT_CUE", "":
			if userLevel != 1 {
				errBytes, _ := json.Marshal(map[string]string{
					"type":  "ERROR",
					"error": "FORBIDDEN_NOT_ADMIN",
				})
				sub.SendChan <- errBytes
				break
			}
			h.FireOrCreateModelCue(cmd.CueID, sessionActorName, customTitle, customSubtitle)
		case "ACK_CUE":
			h.AcknowledgeCue(cmd.CueID, sub.Department)
		}
	}
}

func (h *LiveCueBannerHub) ServeLiveCueWS(w http.ResponseWriter, r *http.Request) {
	userId := r.URL.Query().Get("userId")
	eventIdStr := r.URL.Query().Get("eventId")
	var eventID int64 = h.eventID
	if eventIdStr != "" {
		eventID, _ = strconv.ParseInt(eventIdStr, 10, 64)
	}
	dept := r.URL.Query().Get("dept")
	if dept == "" {
		dept = "STAGE"
	}

	var userLevel uint8 = 1
	var sessionActorName = userId
	if h.resolver != nil && userId != "" {
		if id, ok := h.resolver.Resolve(userId); ok {
			userLevel = id.UserLevel
			if id.DisplayName != "" {
				sessionActorName = id.DisplayName
			} else {
				sessionActorName = id.UserID
			}
		}
	}
	if sessionActorName == "" {
		sessionActorName = "Đạo diễn"
	}

	h.ServeLiveCueWSCore(w, r, userId, dept, eventID, userLevel, sessionActorName)
}

func (h *LiveCueBannerHub) HandleFireCueHTTP(w http.ResponseWriter, r *http.Request) {
	var cmd CueWsCommand
	if err := json.NewDecoder(r.Body).Decode(&cmd); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}
	actorName := cmd.ActorName
	if actorName == "" {
		actorName = "Đạo diễn"
	}
	customTitle, customSubtitle, _, _ := resolveCommandText(cmd)
	snapshot := h.FireOrCreateModelCue(cmd.CueID, actorName, customTitle, customSubtitle)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(snapshot)
}

func (reg *LiveCueRegistry) ServeLiveCueWS(w http.ResponseWriter, r *http.Request) {
	userId := r.URL.Query().Get("userId")
	eventIdStr := r.URL.Query().Get("eventId")
	var eventID int64
	if eventIdStr != "" {
		eventID, _ = strconv.ParseInt(eventIdStr, 10, 64)
	}
	dept := r.URL.Query().Get("dept")
	if dept == "" {
		dept = "STAGE"
	}

	var userLevel uint8 = 1
	var sessionActorName string = userId

	if reg.resolver != nil {
		if userId == "" {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "USER_NOT_IN_EVENT", "message": "Missing userId"})
			return
		}
		id, ok := reg.resolver.Resolve(userId)
		if !ok {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "USER_NOT_IN_EVENT", "message": "User not found in event"})
			return
		}
		if eventID != 0 && id.EventID != eventID {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "USER_NOT_IN_EVENT", "message": "User does not belong to event"})
			return
		}
		if eventID == 0 {
			eventID = id.EventID
		}
		userLevel = id.UserLevel
		if id.DisplayName != "" {
			sessionActorName = id.DisplayName
		} else {
			sessionActorName = id.UserID
		}
	} else {
		if eventID == 0 {
			eventID = 1
		}
		if userId == "" {
			userId = "anon-" + time.Now().Format("150405.000")
		}
		sessionActorName = userId
	}

	hub := reg.GetHub(eventID)
	hub.ServeLiveCueWSCore(w, r, userId, dept, eventID, userLevel, sessionActorName)
}

func (reg *LiveCueRegistry) HandleFireCueHTTP(w http.ResponseWriter, r *http.Request) {
	var cmd CueWsCommand
	if err := json.NewDecoder(r.Body).Decode(&cmd); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	userId := cmd.UserID
	eventId := cmd.EventID
	actorName := cmd.ActorName
	if actorName == "" {
		actorName = "Đạo diễn"
	}

	if reg.resolver != nil {
		if userId == "" {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "USER_NOT_IN_EVENT", "message": "Missing userId"})
			return
		}
		id, ok := reg.resolver.Resolve(userId)
		if !ok {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "USER_NOT_IN_EVENT"})
			return
		}
		if eventId != 0 && id.EventID != eventId {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "USER_NOT_IN_EVENT"})
			return
		}
		if eventId == 0 {
			eventId = id.EventID
		}
		if id.UserLevel != 1 {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{"error": "FORBIDDEN_NOT_ADMIN"})
			return
		}
		if id.DisplayName != "" {
			actorName = id.DisplayName
		} else {
			actorName = id.UserID
		}
	} else if eventId == 0 {
		eventId = 1
	}

	hub := reg.GetHub(eventId)
	customTitle, customSubtitle, _, _ := resolveCommandText(cmd)
	snapshot := hub.FireOrCreateModelCue(cmd.CueID, actorName, customTitle, customSubtitle)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(snapshot)
}
