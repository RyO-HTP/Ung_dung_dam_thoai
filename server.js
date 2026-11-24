const WebSocket = require('ws');

// Bản Node.js tương đương signaling_server.py (cổng 8080). Server chính dùng signaling_server.py (cổng 8765).
const wss = new WebSocket.Server({ port: 8080 });

// user_id -> WebSocket đang hoạt động
const clients = new Map();

const ICE_SERVERS = [
    { urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] },
    {
        urls: [
            'turn:openrelay.metered.ca:80',
            'turn:openrelay.metered.ca:443',
            'turn:openrelay.metered.ca:443?transport=tcp',
        ],
        username: 'openrelayproject',
        credential: 'openrelayproject',
    },
];

// Các gói tin chuyển tiếp nguyên vẹn (giữ sdpMid, sdpMLineIndex... để app Android dựng lại IceCandidate)
const FORWARD_TYPES = new Set(['offer', 'answer', 'ice_candidate', 'ice_candidates_removed', 'hangup']);

wss.on('connection', (ws) => {
    console.log('Một thiết bị mới đã kết nối vào Signaling Server.');

    ws.on('message', (message) => {
        let data;
        try {
            data = JSON.parse(message);
        } catch (error) {
            console.error('Gói tin không phải JSON:', error.message);
            return;
        }

        // 1. Ghi nhận user đăng nhập + cấp cấu hình STUN/TURN
        if (data.type === 'register' && data.user_id) {
            clients.set(data.user_id, ws);
            ws.userId = data.user_id;
            ws.send(JSON.stringify({ type: 'webrtc_config', config: { iceServers: ICE_SERVERS } }));
            console.log(`User [${data.user_id}] đã đăng ký thành công.`);
            return;
        }

        // 2. Chuyển tiếp SDP/ICE (Tuần 2-3), server tự gắn sender_id thật
        if (FORWARD_TYPES.has(data.type)) {
            if (!ws.userId || !data.target_id) return;
            data.sender_id = ws.userId;
            const targetWs = clients.get(data.target_id);
            if (targetWs && targetWs.readyState === WebSocket.OPEN) {
                targetWs.send(JSON.stringify(data));
                console.log(`Đã chuyển ${data.type} từ ${ws.userId} tới ${data.target_id}`);
            }
            return;
        }

        // 3. Seamless Handover (Tuần 4): ghi log chuyển mạng
        if (data.type === 'network_change') {
            console.log(`[HANDOVER] User ${ws.userId} vừa chuyển mạng sang ${data.network_type}.`);
        }
    });

    ws.on('close', () => {
        // Chỉ xoá nếu đây vẫn là kết nối hiện hành (khi đổi mạng, kết nối mới có thể đã đăng ký trước)
        if (ws.userId && clients.get(ws.userId) === ws) {
            clients.delete(ws.userId);
            console.log(`User [${ws.userId}] đã ngắt kết nối.`);
        }
    });
});

console.log('Signaling Server (Node.js) đang chạy tại ws://localhost:8080');
