package week1signaling

import (
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/gorilla/websocket"
)

// =============================================================================
// TUẦN 1 (NGƯỜI 5): MÁY CHỦ BÁO HIỆU WEBRTC (SIGNALING SERVER SDP/ICE & STUN/TURN)
// Đã đồng bộ 100% với SignalingClient.kt & SignalingModels.kt trong dự án DuAnLamChung
// =============================================================================

// WebRTCConfigPayload khớp với trường `config` trong gói tin `{"type": "webrtc_config", "config": {"iceServers": [...]}}`
// mà SignalingClient.kt (dòng 94-99) trên Android mong đợi.
type WebRTCConfigPayload struct {
	IceServers []IceServer `json:"iceServers"`
}

// SignalingPacket định nghĩa cấu trúc gói tin JSON bắt tay đa năng:
// Hỗ trợ ĐỒNG THỜI chuẩn JSON snake_case của Android (`SignalingClient.kt`)
// VÀ chuẩn camelCase của Server Go (`stress_test_300_connections.js`).
type SignalingPacket struct {
	// Trường dùng chung:
	// - Android: "register" | "webrtc_config" | "offer" | "answer" | "ice_candidate"
	// - Go/Test: "ICE_SERVERS_CONFIG" | "SDP_OFFER" | "SDP_ANSWER" | "ICE_CANDIDATE" | "PEER_JOINED"
	Type string `json:"type"`

	// Các trường tương thích trực tiếp với Android SignalingClient.kt & SignalingModels.kt:
	UserID   string               `json:"user_id,omitempty"`   // Dùng khi Android gửi {"type": "register", "user_id": "Minh_Hau"}
	SenderID string               `json:"sender_id,omitempty"` // ID người gửi dạng chuỗi trên Android
	TargetID string               `json:"target_id,omitempty"` // ID người nhận dạng chuỗi trên Android
	Config   *WebRTCConfigPayload `json:"config,omitempty"`    // Cấu hình STUN/TURN cho Android WebRTC

	// Các trường tương thích với Hệ thống Quản lý Kênh & Stress Test:
	EventID       int64       `json:"eventId,omitempty"`
	ChannelID     int         `json:"channelId,omitempty"`
	ChannelCode   string      `json:"channelCode,omitempty"` // "ch_01".."ch_05"
	SenderUserID  int         `json:"senderUserId,omitempty"`
	SenderName    string      `json:"senderName,omitempty"`
	TargetUserID  int         `json:"targetUserId,omitempty"`
	SDP           string      `json:"sdp,omitempty"`
	Candidate     string      `json:"candidate,omitempty"`
	SDPMid        string      `json:"sdpMid,omitempty"`
	SDPMLineIndex int         `json:"sdpMLineIndex,omitempty"`
	IceServers    []IceServer `json:"iceServers,omitempty"`
	TimestampMs   int64       `json:"timestampMs,omitempty"`
}

// IceServer chứa cấu hình STUN/TURN trả về cho WebRTC PeerConnection trên Android.
type IceServer struct {
	URLs       []string `json:"urls"`
	Username   string   `json:"username,omitempty"`
	Credential string   `json:"credential,omitempty"`
}

// ClientConn đại diện cho một kết nối WebSocket của 1 máy Android đang online.
type ClientConn struct {
	NumericID   int    // ID số nguyên (dùng cho bảng định tuyến nội bộ & Stress Test)
	UserCode    string // ID chuỗi từ Android (VD: "Minh_Hau", "user_101")
	DisplayName string
	EventID     int64
	ChannelID   int
	ChannelCode string
	Conn        *websocket.Conn
	SendChan    chan []byte
}

// SignalingHub quản lý toàn bộ các phiên báo hiệu theo cả ID số (NumericID) và ID chuỗi (UserCode).
type SignalingHub struct {
	mu            sync.RWMutex
	clientsByInt  map[int]*ClientConn         // NumericID -> *ClientConn
	clientsByCode map[string]*ClientConn      // UserCode ("Minh_Hau") -> *ClientConn
	channels      map[int]map[int]*ClientConn // channelID -> (NumericID -> *ClientConn)
	turnHost      string
	turnSecret    string
	autoIDCounter int64
	upgrader      websocket.Upgrader
}

func NewSignalingHub(turnHost string, turnSecret string) *SignalingHub {
	return &SignalingHub{
		clientsByInt:  make(map[int]*ClientConn),
		clientsByCode: make(map[string]*ClientConn),
		channels:      make(map[int]map[int]*ClientConn),
		turnHost:      turnHost,
		turnSecret:    turnSecret,
		autoIDCounter: 1000,
		upgrader: websocket.Upgrader{
			ReadBufferSize:  4096,
			WriteBufferSize: 4096,
			CheckOrigin:     func(r *http.Request) bool { return true },
		},
	}
}

// generateTurnCredentials sinh tài khoản TURN ngắn hạn (TTL 12 giờ) bằng thuật toán HMAC-SHA1 (Chuẩn COTURN REST API).
func (h *SignalingHub) generateTurnCredentials(userIdentifier string) []IceServer {
	expiryUnix := time.Now().Add(12 * time.Hour).Unix()
	username := fmt.Sprintf("%d:%s", expiryUnix, userIdentifier)

	mac := hmac.New(sha1.New, []byte(h.turnSecret))
	mac.Write([]byte(username))
	credential := base64.StdEncoding.EncodeToString(mac.Sum(nil))

	return []IceServer{
		{
			URLs: []string{
				fmt.Sprintf("stun:%s:3478", h.turnHost),
				"stun:stun.l.google.com:19302",
			},
		},
		{
			URLs: []string{
				fmt.Sprintf("turn:%s:3478?transport=udp", h.turnHost),
				fmt.Sprintf("turn:%s:3478?transport=tcp", h.turnHost),
			},
			Username:   username,
			Credential: credential,
		},
	}
}

// mapChannelCodeToID ánh xạ mã kênh trên giao diện Android ("ch_01".."ch_05") sang ID số (1..5).
func mapChannelCodeToID(code string, fallback int) (int, string) {
	switch strings.ToLower(strings.TrimSpace(code)) {
	case "ch_01", "1":
		return 1, "ch_01"
	case "ch_02", "2":
		return 2, "ch_02"
	case "ch_03", "3":
		return 3, "ch_03"
	case "ch_04", "4":
		return 4, "ch_04"
	case "ch_05", "5":
		return 5, "ch_05"
	default:
		if fallback >= 1 && fallback <= 5 {
			return fallback, fmt.Sprintf("ch_0%d", fallback)
		}
		return 2, "ch_02" // Mặc định vào Kênh 2 (Sân khấu)
	}
}

// ServeWS xử lý kết nối WebSocket tại cả `/ws/signaling` và `/` (tương thích với SignalingClient.kt trên Android).
func (h *SignalingHub) ServeWS(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query()
	rawUserID := query.Get("userId")
	if rawUserID == "" {
		rawUserID = query.Get("user_id")
	}

	numericID, _ := strconv.Atoi(rawUserID)
	if numericID <= 0 {
		numericID = int(atomic.AddInt64(&h.autoIDCounter, 1))
	}
	userCode := rawUserID
	if userCode == "" {
		userCode = fmt.Sprintf("user_%d", numericID)
	}

	rawChannel := query.Get("channelId")
	if rawChannel == "" {
		rawChannel = query.Get("channel")
	}
	parsedChInt, _ := strconv.Atoi(rawChannel)
	channelID, channelCode := mapChannelCodeToID(rawChannel, parsedChInt)

	displayName := query.Get("name")
	if displayName == "" {
		displayName = userCode
	}

	wsConn, err := h.upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("[Signaling] Lỗi nâng cấp WebSocket cho user %s: %v", userCode, err)
		return
	}

	client := &ClientConn{
		NumericID:   numericID,
		UserCode:    userCode,
		DisplayName: displayName,
		EventID:     1,
		ChannelID:   channelID,
		ChannelCode: channelCode,
		Conn:        wsConn,
		SendChan:    make(chan []byte, 128),
	}

	h.registerClient(client)
	defer h.unregisterClient(client)

	// Gửi ngay cấu hình STUN/TURN cho Client vừa kết nối (hỗ trợ cả 2 chuẩn Android & Go)
	h.sendWebRTCConfigToClient(client)

	// Thông báo cho các thành viên khác trong cùng kênh biết có máy mới tham gia
	h.broadcastToChannel(client.ChannelID, client.NumericID, SignalingPacket{
		Type:         "PEER_JOINED",
		EventID:      client.EventID,
		ChannelID:    client.ChannelID,
		ChannelCode:  client.ChannelCode,
		SenderUserID: client.NumericID,
		SenderID:     client.UserCode,
		SenderName:   client.DisplayName,
		TimestampMs:  time.Now().UnixMilli(),
	})

	go h.writePump(client)
	h.readPump(client)
}

func (h *SignalingHub) sendWebRTCConfigToClient(client *ClientConn) {
	iceServers := h.generateTurnCredentials(client.UserCode)

	// 1. Gói tin chuẩn Android SignalingClient.kt (dòng 94): {"type": "webrtc_config", "config": {"iceServers": [...]}}
	androidConfigPkt := SignalingPacket{
		Type:        "webrtc_config",
		UserID:      client.UserCode,
		Config:      &WebRTCConfigPayload{IceServers: iceServers},
		IceServers:  iceServers,
		TimestampMs: time.Now().UnixMilli(),
	}
	if raw, err := json.Marshal(androidConfigPkt); err == nil {
		select {
		case client.SendChan <- raw:
		default:
		}
	}

	// 2. Gói tin chuẩn Go / Stress Test: {"type": "ICE_SERVERS_CONFIG", "iceServers": [...]}
	goConfigPkt := SignalingPacket{
		Type:         "ICE_SERVERS_CONFIG",
		EventID:      client.EventID,
		ChannelID:    client.ChannelID,
		ChannelCode:  client.ChannelCode,
		SenderUserID: 0,
		TargetUserID: client.NumericID,
		TargetID:     client.UserCode,
		IceServers:   iceServers,
		TimestampMs:  time.Now().UnixMilli(),
	}
	if raw, err := json.Marshal(goConfigPkt); err == nil {
		select {
		case client.SendChan <- raw:
		default:
		}
	}
}

func (h *SignalingHub) registerClient(c *ClientConn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.clientsByInt[c.NumericID] = c
	h.clientsByCode[c.UserCode] = c
	if h.channels[c.ChannelID] == nil {
		h.channels[c.ChannelID] = make(map[int]*ClientConn)
	}
	h.channels[c.ChannelID][c.NumericID] = c
}

func (h *SignalingHub) updateClientIdentity(c *ClientConn, newUserCode string) {
	if newUserCode == "" {
		return
	}
	h.mu.Lock()
	defer h.mu.Unlock()
	delete(h.clientsByCode, c.UserCode)
	c.UserCode = newUserCode
	if c.DisplayName == "" || strings.HasPrefix(c.DisplayName, "user_") {
		c.DisplayName = newUserCode
	}
	h.clientsByCode[c.UserCode] = c
	if parsedInt, err := strconv.Atoi(newUserCode); err == nil && parsedInt > 0 {
		delete(h.clientsByInt, c.NumericID)
		if chMap, ok := h.channels[c.ChannelID]; ok {
			delete(chMap, c.NumericID)
			chMap[parsedInt] = c
		}
		c.NumericID = parsedInt
		h.clientsByInt[c.NumericID] = c
	}
}

func (h *SignalingHub) unregisterClient(c *ClientConn) {
	h.mu.Lock()
	delete(h.clientsByInt, c.NumericID)
	delete(h.clientsByCode, c.UserCode)
	if chMap, ok := h.channels[c.ChannelID]; ok {
		delete(chMap, c.NumericID)
	}
	close(c.SendChan)
	h.mu.Unlock()
	_ = c.Conn.Close()
}

// readPump đọc liên tục cả gói tin chuẩn Android ("register", "offer", "answer", "ice_candidate")
// và gói tin chuẩn Go ("SDP_OFFER", "SDP_ANSWER", "ICE_CANDIDATE").
func (h *SignalingHub) readPump(c *ClientConn) {
	c.Conn.SetReadLimit(65536)
	_ = c.Conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	c.Conn.SetPongHandler(func(string) error {
		_ = c.Conn.SetReadDeadline(time.Now().Add(60 * time.Second))
		return nil
	})

	for {
		_, rawMsg, err := c.Conn.ReadMessage()
		if err != nil {
			break
		}

		var pkt SignalingPacket
		if err := json.Unmarshal(rawMsg, &pkt); err != nil {
			continue
		}

		// Xử lý bản tin đăng ký định danh từ Android SignalingClient.kt:
		// {"type": "register", "user_id": "Minh_Hau"}
		if pkt.Type == "register" {
			h.updateClientIdentity(c, pkt.UserID)
			h.sendWebRTCConfigToClient(c)
			continue
		}

		// Chuẩn hóa thông tin người gửi cho cả 2 chuẩn (Android & Go)
		pkt.SenderUserID = c.NumericID
		if pkt.SenderID == "" {
			pkt.SenderID = c.UserCode
		}
		if pkt.SenderName == "" {
			pkt.SenderName = c.DisplayName
		}
		pkt.TimestampMs = time.Now().UnixMilli()
		if pkt.ChannelID == 0 {
			pkt.ChannelID = c.ChannelID
		}
		if pkt.ChannelCode == "" {
			pkt.ChannelCode = c.ChannelCode
		}

		switch pkt.Type {
		case "offer", "answer", "ice_candidate", "SDP_OFFER", "SDP_ANSWER", "ICE_CANDIDATE":
			// Ưu tiên định tuyến P2P theo TargetID chuỗi (Android) hoặc TargetUserID số (Go)
			if pkt.TargetID != "" {
				h.sendToUserByCode(pkt.TargetID, pkt)
			} else if pkt.TargetUserID > 0 {
				h.sendToUserByInt(pkt.TargetUserID, pkt)
			} else {
				h.broadcastToChannel(pkt.ChannelID, c.NumericID, pkt)
			}
		}
	}
}

func (h *SignalingHub) writePump(c *ClientConn) {
	ticker := time.NewTicker(25 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case msg, ok := <-c.SendChan:
			_ = c.Conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if !ok {
				_ = c.Conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			if err := c.Conn.WriteMessage(websocket.TextMessage, msg); err != nil {
				return
			}
		case <-ticker.C:
			_ = c.Conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if err := c.Conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

func (h *SignalingHub) sendToUserByCode(targetCode string, pkt SignalingPacket) {
	raw, err := json.Marshal(pkt)
	if err != nil {
		return
	}
	h.mu.RLock()
	target, exists := h.clientsByCode[targetCode]
	if !exists {
		if parsedInt, err := strconv.Atoi(targetCode); err == nil {
			target, exists = h.clientsByInt[parsedInt]
		}
	}
	h.mu.RUnlock()

	if exists {
		select {
		case target.SendChan <- raw:
		default:
		}
	}
}

func (h *SignalingHub) sendToUserByInt(targetUserID int, pkt SignalingPacket) {
	raw, err := json.Marshal(pkt)
	if err != nil {
		return
	}
	h.mu.RLock()
	target, exists := h.clientsByInt[targetUserID]
	h.mu.RUnlock()
	if exists {
		select {
		case target.SendChan <- raw:
		default:
		}
	}
}

func (h *SignalingHub) broadcastToChannel(channelID int, excludeNumericID int, pkt SignalingPacket) {
	raw, err := json.Marshal(pkt)
	if err != nil {
		return
	}
	h.mu.RLock()
	chMembers := h.channels[channelID]
	for uid, member := range chMembers {
		if uid == excludeNumericID {
			continue
		}
		select {
		case member.SendChan <- raw:
		default:
		}
	}
	h.mu.RUnlock()
}
