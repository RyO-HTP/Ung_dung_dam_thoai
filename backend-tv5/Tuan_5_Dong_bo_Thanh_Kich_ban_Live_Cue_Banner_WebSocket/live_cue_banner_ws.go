package week5livecue

import (
	"encoding/json"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// =============================================================================
// TUẦN 5 (NGƯỜI 5): ĐỒNG BỘ HÓA THANH KỊCH BẢN (LIVE CUE BANNER) QUA WEBSOCKETS
// Đã đồng bộ 100% với dự án Android DuAnLamChung trên nhánh Tuan-4:
//   - LiveCueCard.kt & FakeRemainingUiData.kt (data class LiveCue: title, message, channelName, isPriority)
//   - ProfileLiveCueScreens.kt (LiveCueDraftScreen: nút "Đẩy lên mọi máy")
//   - ChannelListUiState.kt & ChannelListScreen.kt (liveCueHeader, liveCueTitle, liveCueSubtitle)
//   - ActiveCallScreen.kt (Thanh Live Cue hiển thị cố định phía trên nút PTT)
// =============================================================================

type CueStatus string

const (
	CueCompleted   CueStatus = "COMPLETED"
	CueActiveNow   CueStatus = "ACTIVE_NOW"   // Đang hiển thị trên Thanh Kịch bản (Live Cue Banner)
	CueNextStandby CueStatus = "NEXT_STANDBY" // Tiết mục chuẩn bị kế tiếp
	CueUpcoming    CueStatus = "UPCOMING"
)

type CueItem struct {
	CueID             string          `json:"cueId"`             // Mã tiết mục: C01, C02, C03, C04...
	TimeSpec          string          `json:"timeSpec"`          // Giờ kịch bản: 19:30, 19:50...
	Title             string          `json:"title"`             // Khớp data class LiveCue.title trên Android
	Message           string          `json:"message"`           // Khớp data class LiveCue.message trên Android
	ChannelName       string          `json:"channelName"`       // Khớp data class LiveCue.channelName ("Toàn sự kiện")
	IsPriority        bool            `json:"isPriority"`        // Khớp data class LiveCue.isPriority (true)
	LiveCueHeader     string          `json:"liveCueHeader"`     // Khớp ChannelListUiState: "LIVE CUE · ĐANG DIỄN"
	LiveCueTitle      string          `json:"liveCueTitle"`      // Khớp ChannelListUiState: "Tiết mục 4 – Ban nhạc khách mời"
	LiveCueSubtitle   string          `json:"liveCueSubtitle"`   // Khớp ChannelListUiState: "Hậu cần: chuẩn bị đạo cụ chuyển cảnh"
	BannerText        string          `json:"bannerText"`        // Dòng chữ nhắc việc tổng hợp
	DetailInstruction string          `json:"detailInstruction"` // Hướng dẫn chi tiết cho từng bộ phận
	TargetDepartments []string        `json:"targetDepartments"` // ["ALL_EVENT", "STAGE", "LOGISTICS_SEC", "TECHNICAL", "RECEPTION"]
	Status            CueStatus       `json:"status"`
	FiredByDirector   string          `json:"firedByDirector,omitempty"`
	FiredAtMs         int64           `json:"firedAtMs,omitempty"`
	AckedDepartments  map[string]bool `json:"ackedDepartments"` // Các bộ phận đã bấm xác nhận thấy lệnh nhắc việc
}

// LiveCueBannerPayload là bản tin WebSocket đẩy xuống toàn bộ thiết bị Android.
type LiveCueBannerPayload struct {
	Type            string     `json:"type"` // LIVE_CUE_BANNER_SYNC | LIVE_CUE_FIRED | LIVE_CUE_ACK_UPDATED
	EventID         int64      `json:"eventId"`
	Revision        int64      `json:"revision"`        // Số thứ tự phiên bản kịch bản tăng dần
	Title           string     `json:"title"`           // Khớp trực tiếp LiveCue.title (LiveCueCard.kt)
	Message         string     `json:"message"`         // Khớp trực tiếp LiveCue.message (LiveCueCard.kt)
	ChannelName     string     `json:"channelName"`     // Khớp trực tiếp LiveCue.channelName ("Toàn sự kiện")
	IsPriority      bool       `json:"isPriority"`      // Khớp trực tiếp LiveCue.isPriority
	LiveCueHeader   string     `json:"liveCueHeader"`   // Khớp trực tiếp ChannelListUiState.liveCueHeader
	LiveCueTitle    string     `json:"liveCueTitle"`    // Khớp trực tiếp ChannelListUiState.liveCueTitle
	LiveCueSubtitle string     `json:"liveCueSubtitle"` // Khớp trực tiếp ChannelListUiState.liveCueSubtitle
	ActiveCue       *CueItem   `json:"activeCue"`       // Tiết mục đang diễn ra (hiển thị ngay trên Live Cue Banner)
	NextCue         *CueItem   `json:"nextCue"`         // Tiết mục chuẩn bị tiếp theo
	AllCues         []*CueItem `json:"allCues"`         // Toàn bộ danh sách kịch bản sự kiện
	ServerTimeMs    int64      `json:"serverTimeMs"`
}

type CueWsCommand struct {
	Action          string `json:"action"` // FIRE_CUE | NEXT_CUE | ACK_CUE | UPDATE_BANNER_TEXT
	CueID           string `json:"cueId"`
	Title           string `json:"title"`           // Hỗ trợ gửi từ LiveCueDraftScreen ("Tiết mục đang diễn")
	Message         string `json:"message"`         // Hỗ trợ gửi từ LiveCueDraftScreen ("Nhắc việc cho các ban")
	ChannelName     string `json:"channelName"`     // Hỗ trợ gửi từ LiveCueDraftScreen ("Toàn sự kiện")
	IsPriority      *bool  `json:"isPriority"`      // Hỗ trợ gửi từ LiveCueDraftScreen (true)
	LiveCueTitle    string `json:"liveCueTitle"`    // Hỗ trợ gửi theo chuẩn ChannelListUiState
	LiveCueSubtitle string `json:"liveCueSubtitle"` // Hỗ trợ gửi theo chuẩn ChannelListUiState
	CustomBanner    string `json:"customBanner"`    // Cho phép Đạo diễn gõ nhanh dòng nhắc việc đột xuất
	CustomSubtitle  string `json:"customSubtitle"`  // Ghi chú nhắc việc phụ cho bộ phận
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
}

func NewLiveCueBannerHub(eventID int64) *LiveCueBannerHub {
	defaultCues := []*CueItem{
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
			// Khớp 100% với dữ liệu hiển thị trên FakeRemainingUiData.kt, ChannelListUiState.kt & ActiveCallScreen.kt
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

	return &LiveCueBannerHub{
		eventID:     eventID,
		revision:    1,
		cues:        defaultCues,
		subscribers: make(map[string]*CueSubscriber),
		upgrader: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool { return true },
		},
	}
}

// resolveCommandText chuẩn hóa dữ liệu từ cả LiveCueDraftScreen (title/message) lẫn ChannelListUiState (liveCueTitle/liveCueSubtitle).
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

// FireCue chuyển Thanh Kịch bản sang tiết mục được chọn và đẩy ngay lập tức xuống mọi thiết bị qua WebSocket.
func (h *LiveCueBannerHub) FireCue(cueID string, directorName string, customTitle string, customSubtitle string) LiveCueBannerPayload {
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
	} else if customTitle != "" {
		// Cho phép Tổng Đạo diễn phát một dòng chữ nhắc việc từ LiveCueDraftScreen ("Đẩy lên mọi máy")
		if customSubtitle == "" {
			customSubtitle = "Lệnh điều phối trực tiếp từ " + directorName
		}
		adHocCue := &CueItem{
			CueID:             "CUE-HOT",
			TimeSpec:          time.Now().Format("15:04"),
			Title:             customTitle,
			Message:           customSubtitle,
			ChannelName:       "Toàn sự kiện",
			IsPriority:        true,
			LiveCueHeader:     "LIVE CUE · ĐANG DIỄN",
			LiveCueTitle:      customTitle,
			LiveCueSubtitle:   customSubtitle,
			BannerText:        customTitle + " — " + customSubtitle,
			DetailInstruction: customSubtitle,
			TargetDepartments: []string{"ALL_EVENT", "STAGE", "LOGISTICS_SEC", "TECHNICAL", "RECEPTION"},
			Status:            CueActiveNow,
			FiredByDirector:   directorName,
			FiredAtMs:         nowMs,
			AckedDepartments:  map[string]bool{},
		}
		for _, c := range h.cues {
			if c.Status == CueActiveNow {
				c.Status = CueCompleted
				c.LiveCueHeader = "LIVE CUE · ĐÃ QUA"
			}
		}
		h.cues = append([]*CueItem{adHocCue}, h.cues...)
	}

	h.revision++
	snapshot := h.buildSnapshotLocked("LIVE_CUE_FIRED")
	h.mu.Unlock()

	h.broadcastPayload(snapshot)
	return snapshot
}

// AcknowledgeCue ghi nhận một bộ phận (Âm thanh, Sân khấu, An ninh...) đã đọc dòng nhắc việc trên Banner.
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
		if c.Status == CueActiveNow && activeCue == nil {
			activeCue = c
		} else if c.Status == CueNextStandby && nextCue == nil {
			nextCue = c
		}
	}

	header := "LIVE CUE · ĐANG DIỄN"
	title := "Tiết mục 4 – Ban nhạc khách mời"
	subtitle := "Hậu cần: chuẩn bị đạo cụ chuyển cảnh"
	channelName := "Toàn sự kiện"
	isPriority := true

	if activeCue != nil {
		if activeCue.LiveCueHeader != "" {
			header = activeCue.LiveCueHeader
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
		}
		isPriority = activeCue.IsPriority
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
		AllCues:         h.cues,
		ServerTimeMs:    time.Now().UnixMilli(),
	}
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

// ServeLiveCueWS xử lý kết nối WebSocket tại GET /ws/live-cue?userId=102&dept=STAGE
func (h *LiveCueBannerHub) ServeLiveCueWS(w http.ResponseWriter, r *http.Request) {
	userID := r.URL.Query().Get("userId")
	if userID == "" {
		userID = "anon-" + time.Now().Format("150405.000")
	}
	dept := r.URL.Query().Get("dept")
	if dept == "" {
		dept = "STAGE"
	}

	conn, err := h.upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}

	sub := &CueSubscriber{
		UserID:     userID,
		Department: dept,
		Conn:       conn,
		SendChan:   make(chan []byte, 64),
	}

	h.mu.Lock()
	h.subscribers[userID] = sub
	initialSnapshot := h.buildSnapshotLocked("LIVE_CUE_BANNER_SYNC")
	h.mu.Unlock()

	// Đẩy ngay bản chụp Thanh Kịch bản hiện tại cho thiết bị vừa kết nối (hoặc vừa có mạng lại)
	if raw, err := json.Marshal(initialSnapshot); err == nil {
		sub.SendChan <- raw
	}

	defer func() {
		h.mu.Lock()
		delete(h.subscribers, userID)
		close(sub.SendChan)
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
		case "FIRE_CUE", "UPDATE_BANNER_TEXT", "":
			h.FireCue(cmd.CueID, cmd.ActorName, customTitle, customSubtitle)
		case "ACK_CUE":
			h.AcknowledgeCue(cmd.CueID, sub.Department)
		}
	}
}

// HandleFireCueHTTP cung cấp REST API chuyển tiết mục Live Cue: POST /api/v1/live-cue/fire
func (h *LiveCueBannerHub) HandleFireCueHTTP(w http.ResponseWriter, r *http.Request) {
	var cmd CueWsCommand
	if err := json.NewDecoder(r.Body).Decode(&cmd); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}
	if cmd.ActorName == "" {
		cmd.ActorName = "Lan Phương (Tổng đạo diễn)"
	}
	customTitle, customSubtitle, _, _ := resolveCommandText(cmd)
	snapshot := h.FireCue(cmd.CueID, cmd.ActorName, customTitle, customSubtitle)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(snapshot)
}
