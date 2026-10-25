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
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// =============================================================================
// TUẦN 1 (NGƯỜI 5): MÁY CHỦ BÁO HIỆU WEBRTC (SIGNALING SERVER SDP/ICE & STUN/TURN)
// Phối hợp trực tiếp với Người 1 (Cấu hình WebRTC + STUN/TURN vượt tường lửa NAT)
// =============================================================================

// SignalingPacket định nghĩa cấu trúc gói tin JSON bắt tay giữa các máy Android.
type SignalingPacket struct {
	Type          string      `json:"type"`                    // ICE_SERVERS_CONFIG | SDP_OFFER | SDP_ANSWER | ICE_CANDIDATE | PEER_JOINED
	EventID       int64       `json:"eventId"`                 // ID sự kiện
	ChannelID     int         `json:"channelId"`               // Kênh bộ đàm (1..5)
	SenderUserID  int         `json:"senderUserId"`            // ID người gửi
	SenderName    string      `json:"senderName,omitempty"`    // Tên hiển thị người gửi
	TargetUserID  int         `json:"targetUserId,omitempty"`  // 0 = Broadcast cả kênh, >0 = Gửi đích danh P2P
	SDP           string      `json:"sdp,omitempty"`           // Nội dung SDP Offer / Answer (Opus 16kHz Mono 20ms)
	Candidate     string      `json:"candidate,omitempty"`     // Chuỗi ICE Candidate (host / srflx / relay)
	SDPMid        string      `json:"sdpMid,omitempty"`        // Media stream identification
	SDPMLineIndex int         `json:"sdpMLineIndex,omitempty"` // Chỉ số dòng m=audio trong SDP
	IceServers    []IceServer `json:"iceServers,omitempty"`    // Danh sách máy chủ STUN/TURN cấp cho Client lúc mới kết nối
	TimestampMs   int64       `json:"timestampMs"`             // Thời gian gửi (ms)
}

// IceServer chứa cấu hình STUN/TURN trả về cho WebRTC PeerConnection của Người 1.
type IceServer struct {
	URLs       []string `json:"urls"`
	Username   string   `json:"username,omitempty"`
	Credential string   `json:"credential,omitempty"`
}

// ClientConn đại diện cho một kết nối WebSocket của 1 máy Android đang online.
type ClientConn struct {
	UserID      int
	DisplayName string
	EventID     int64
	ChannelID   int
	Conn        *websocket.Conn
	SendChan    chan []byte
}

// SignalingHub quản lý toàn bộ các phiên báo hiệu theo từng Kênh bộ đàm.
type SignalingHub struct {
	mu         sync.RWMutex
	clients    map[int]*ClientConn         // userID -> *ClientConn
	channels   map[int]map[int]*ClientConn // channelID -> (userID -> *ClientConn)
	turnHost   string
	turnSecret string
	upgrader   websocket.Upgrader
}

func NewSignalingHub(turnHost string, turnSecret string) *SignalingHub {
	return &SignalingHub{
		clients:    make(map[int]*ClientConn),
		channels:   make(map[int]map[int]*ClientConn),
		turnHost:   turnHost,
		turnSecret: turnSecret,
		upgrader: websocket.Upgrader{
			ReadBufferSize:  4096,
			WriteBufferSize: 4096,
			CheckOrigin:     func(r *http.Request) bool { return true },
		},
	}
}

// generateTurnCredentials sinh tài khoản TURN ngắn hạn (TTL 12 giờ) bằng thuật toán HMAC-SHA1.
// Giúp Người 1 cấu hình vượt tường lửa đối칭 (Symmetric NAT 4G) an toàn mà không lộ mật khẩu tĩnh.
func (h *SignalingHub) generateTurnCredentials(userID int) []IceServer {
	expiryUnix := time.Now().Add(12 * time.Hour).Unix()
	username := fmt.Sprintf("%d:%d", expiryUnix, userID)

	mac := hmac.New(sha1.New, []byte(h.turnSecret))
	mac.Write([]byte(username))
	credential := base64.StdEncoding.EncodeToString(mac.Sum(nil))

	return []IceServer{
		{
			URLs: []string{
				fmt.Sprintf("stun:%s:3478", h.turnHost),
				fmt.Sprintf("stun:stun.l.google.com:19302"),
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

// ServeWS xử lý kết nối WebSocket tại endpoint: GET /ws/signaling?userId=101&channelId=2&name=Minh
func (h *SignalingHub) ServeWS(w http.ResponseWriter, r *http.Request) {
	userID, _ := strconv.Atoi(r.URL.Query().Get("userId"))
	channelID, _ := strconv.Atoi(r.URL.Query().Get("channelId"))
	displayName := r.URL.Query().Get("name")
	if userID <= 0 {
		http.Error(w, "Missing valid userId", http.StatusBadRequest)
		return
	}
	if channelID <= 0 {
		channelID = 2 // Mặc định vào Kênh 2 (SÂN KHẤU)
	}

	wsConn, err := h.upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("[Signaling] Lỗi nâng cấp WebSocket cho user %d: %v", userID, err)
		return
	}

	client := &ClientConn{
		UserID:      userID,
		DisplayName: displayName,
		EventID:     1,
		ChannelID:   channelID,
		Conn:        wsConn,
		SendChan:    make(chan []byte, 128),
	}

	h.registerClient(client)
	defer h.unregisterClient(client)

	// 1. Gửi ngay cấu hình STUN/TURN cho máy Android vừa kết nối
	iceInitPacket := SignalingPacket{
		Type:         "ICE_SERVERS_CONFIG",
		EventID:      client.EventID,
		ChannelID:    client.ChannelID,
		SenderUserID: 0,
		TargetUserID: client.UserID,
		IceServers:   h.generateTurnCredentials(client.UserID),
		TimestampMs:  time.Now().UnixMilli(),
	}
	if raw, err := json.Marshal(iceInitPacket); err == nil {
		client.SendChan <- raw
	}

	// 2. Thông báo cho các thành viên khác trong cùng kênh biết có máy mới tham gia để bắt đầu trao đổi SDP Offer
	h.broadcastToChannel(client.ChannelID, client.UserID, SignalingPacket{
		Type:         "PEER_JOINED",
		EventID:      client.EventID,
		ChannelID:    client.ChannelID,
		SenderUserID: client.UserID,
		SenderName:   client.DisplayName,
		TimestampMs:  time.Now().UnixMilli(),
	})

	// 3. Khởi chạy 2 goroutine Đọc & Ghi độc lập cho mỗi kết nối WebSocket
	go h.writePump(client)
	h.readPump(client)
}

func (h *SignalingHub) registerClient(c *ClientConn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.clients[c.UserID] = c
	if h.channels[c.ChannelID] == nil {
		h.channels[c.ChannelID] = make(map[int]*ClientConn)
	}
	h.channels[c.ChannelID][c.UserID] = c
}

func (h *SignalingHub) unregisterClient(c *ClientConn) {
	h.mu.Lock()
	delete(h.clients, c.UserID)
	if chMap, ok := h.channels[c.ChannelID]; ok {
		delete(chMap, c.UserID)
	}
	close(c.SendChan)
	h.mu.Unlock()
	_ = c.Conn.Close()
}

// readPump đọc liên tục các gói SDP_OFFER, SDP_ANSWER, ICE_CANDIDATE từ máy Android gửi lên.
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

		pkt.SenderUserID = c.UserID
		pkt.SenderName = c.DisplayName
		pkt.TimestampMs = time.Now().UnixMilli()
		if pkt.ChannelID == 0 {
			pkt.ChannelID = c.ChannelID
		}

		switch pkt.Type {
		case "SDP_OFFER", "SDP_ANSWER", "ICE_CANDIDATE":
			// Nếu chỉ định TargetUserID cụ thể -> định tuyến P2P 1-1; ngược lại -> quảng bá trong kênh
			if pkt.TargetUserID > 0 {
				h.sendToUser(pkt.TargetUserID, pkt)
			} else {
				h.broadcastToChannel(pkt.ChannelID, c.UserID, pkt)
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

func (h *SignalingHub) sendToUser(targetUserID int, pkt SignalingPacket) {
	raw, err := json.Marshal(pkt)
	if err != nil {
		return
	}
	h.mu.RLock()
	target, exists := h.clients[targetUserID]
	h.mu.RUnlock()
	if exists {
		select {
		case target.SendChan <- raw:
		default:
			// Bỏ qua nếu bộ đệm gửi của client bị đầy để không làm treo Hub
		}
	}
}

func (h *SignalingHub) broadcastToChannel(channelID int, excludeUserID int, pkt SignalingPacket) {
	raw, err := json.Marshal(pkt)
	if err != nil {
		return
	}
	h.mu.RLock()
	chMembers := h.channels[channelID]
	for uid, member := range chMembers {
		if uid == excludeUserID {
			continue
		}
		select {
		case member.SendChan <- raw:
		default:
		}
	}
	h.mu.RUnlock()
}
