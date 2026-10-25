package week2routing

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/binary"
	"log"
	"net"
	"sync"
	"sync/atomic"
)

// =============================================================================
// TUẦN 2 (NGƯỜI 5): BỘ ĐỊNH TUYẾN LUỒNG ÂM THANH THEO KÊNH (CHANNEL AUDIO ROUTER)
// Nhận gói UDP 18-Byte + Opus Payload + 4-Byte HMAC4 và chuyển tiếp chính xác
// tới những người dùng đã chọn nghe kênh tương ứng (Zero-Allocation sync.Pool).
// =============================================================================

const (
	HeaderSize          = 18
	Hmac4Size           = 4
	MaxUdpPacketSize    = 512
	PacketTypeAudio     = 0x01
	PacketTypeHeartbeat = 0x02
)

type PeerEndpoint struct {
	UserID     uint32
	HmacSecret []byte
	UDPAddr    *net.UDPAddr
}

type ChannelAudioRouter struct {
	mu                 sync.RWMutex
	peers              map[uint32]*PeerEndpoint          // userID -> *PeerEndpoint
	channelSubscribers map[uint8]map[uint32]*PeerEndpoint // channelID -> (userID -> *PeerEndpoint)
	packetPool         sync.Pool
	ForwardedPackets   atomic.Uint64
	RejectedHmacCount  atomic.Uint64
}

func NewChannelAudioRouter() *ChannelAudioRouter {
	return &ChannelAudioRouter{
		peers:              make(map[uint32]*PeerEndpoint),
		channelSubscribers: make(map[uint8]map[uint32]*PeerEndpoint),
		packetPool: sync.Pool{
			New: func() interface{} {
				buf := make([]byte, MaxUdpPacketSize)
				return &buf
			},
		},
	}
}

// RegisterUserSession đăng ký phiên người dùng và các kênh họ đã chọn nghe vào bảng định tuyến.
func (r *ChannelAudioRouter) RegisterUserSession(userID uint32, hmacSecret []byte, channels []uint8) {
	r.mu.Lock()
	defer r.mu.Unlock()

	peer := &PeerEndpoint{
		UserID:     userID,
		HmacSecret: hmacSecret,
	}
	r.peers[userID] = peer

	for _, chID := range channels {
		if r.channelSubscribers[chID] == nil {
			r.channelSubscribers[chID] = make(map[uint32]*PeerEndpoint)
		}
		r.channelSubscribers[chID][userID] = peer
	}
}

// UpdateUserSubscribedChannels cập nhật lại bảng định tuyến khi người dùng chuyển kênh trên App.
func (r *ChannelAudioRouter) UpdateUserSubscribedChannels(userID uint32, newChannels []uint8) {
	r.mu.Lock()
	defer r.mu.Unlock()

	peer, exists := r.peers[userID]
	if !exists {
		return
	}

	// Xóa đăng ký cũ trên tất cả các kênh
	for _, subs := range r.channelSubscribers {
		delete(subs, userID)
	}

	// Đăng ký vào các kênh mới đã chọn
	for _, chID := range newChannels {
		if r.channelSubscribers[chID] == nil {
			r.channelSubscribers[chID] = make(map[uint32]*PeerEndpoint)
		}
		r.channelSubscribers[chID][userID] = peer
	}
}

// ListenAndRouteUDP khởi chạy vòng lặp nhận và định tuyến gói âm thanh UDP trên cổng 50000.
func (r *ChannelAudioRouter) ListenAndRouteUDP(listenAddr string) error {
	addr, err := net.ResolveUDPAddr("udp", listenAddr)
	if err != nil {
		return err
	}
	conn, err := net.ListenUDP("udp", addr)
	if err != nil {
		return err
	}
	defer conn.Close()

	log.Printf("[ChannelAudioRouter] Đang lắng nghe và định tuyến âm thanh UDP tại %s", listenAddr)

	for {
		bufPtr := r.packetPool.Get().(*[]byte)
		buf := *bufPtr

		n, senderAddr, err := conn.ReadFromUDP(buf)
		if err != nil {
			r.packetPool.Put(bufPtr)
			continue
		}

		if n < HeaderSize+Hmac4Size {
			r.packetPool.Put(bufPtr)
			continue
		}

		r.routePacketToChannel(conn, buf[:n], senderAddr)
		r.packetPool.Put(bufPtr)
	}
}

func (r *ChannelAudioRouter) routePacketToChannel(conn *net.UDPConn, packet []byte, senderAddr *net.UDPAddr) {
	// 1. Giải mã Header 18-byte Big-Endian
	byte0 := packet[0]
	packetType := (byte0 >> 2) & 0x0F
	channelID := packet[1]
	senderID := binary.BigEndian.Uint32(packet[2:6])

	// 2. Tra cứu phiên của người gửi và kiểm tra chữ ký HMAC4 (4 byte cuối)
	r.mu.RLock()
	senderPeer, exists := r.peers[senderID]
	r.mu.RUnlock()
	if !exists {
		return
	}

	if !verifyPacketHmac4(packet, senderPeer.HmacSecret) {
		r.RejectedHmacCount.Add(1)
		return
	}

	// 3. Cập nhật địa chỉ IP:Port thực tế (hỗ trợ giữ kết nối NAT và chuyển mạng 0-RTT)
	r.mu.Lock()
	senderPeer.UDPAddr = senderAddr
	r.mu.Unlock()

	if packetType == PacketTypeHeartbeat {
		return
	}

	// 4. Nếu là gói Âm thanh (PacketTypeAudio = 0x01): Định tuyến tới đúng những người đang nghe channelID
	if packetType == PacketTypeAudio {
		r.mu.RLock()
		subscribers := r.channelSubscribers[channelID]
		for listenerID, listenerPeer := range subscribers {
			if listenerID == senderID || listenerPeer.UDPAddr == nil {
				continue // Không gửi ngược lại cho người đang nói
			}
			_, err := conn.WriteToUDP(packet, listenerPeer.UDPAddr)
			if err == nil {
				r.ForwardedPackets.Add(1)
			}
		}
		r.mu.RUnlock()
	}
}

func verifyPacketHmac4(packet []byte, secret []byte) bool {
	dataLen := len(packet) - Hmac4Size
	mac := hmac.New(sha256.New, secret)
	mac.Write(packet[:dataLen])
	expected := mac.Sum(nil)[:4]
	actual := packet[dataLen:]
	return hmac.Equal(expected, actual)
}
