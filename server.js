const WebSocket = require('ws');

// Khởi tạo máy chủ WebSocket ở cổng 8080
const wss = new WebSocket.Server({ port: 8080 });

// Danh sách lưu trữ các Client đang kết nối
const clients = new Map();

wss.on('connection', (ws) => {
    console.log('Một thiết bị mới đã kết nối vào Signaling Server.');

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);

            // 1. Ghi nhận user đăng nhập
            if (data.type === 'register') {
                clients.set(data.user_id, ws);
                ws.userId = data.user_id;
                console.log(`User [${data.user_id}] đã đăng ký thành công.`);
            }

            // 2. Chuyển tiếp luồng SDP (Thiết lập cuộc gọi P2P)
            if (data.type === 'offer' || data.type === 'answer') {
                const targetWs = clients.get(data.target_id);
                if (targetWs && targetWs.readyState === WebSocket.OPEN) {
                    targetWs.send(JSON.stringify(data));
                    console.log(`Đã chuyển ${data.type} từ ${data.user_id} tới ${data.target_id}`);
                }
            }

            // 3. TÍNH NĂNG TUẦN 3: ICE Gathering (Thu thập & chuyển tiếp đường truyền)
            if (data.type === 'ice_candidate') {
                const targetWs = clients.get(data.target_id);
                if (targetWs && targetWs.readyState === WebSocket.OPEN) {
                    // Chuyển tiếp ngay lập tức IP/Port mạng mới (4G/Wi-Fi) cho người kia
                    targetWs.send(JSON.stringify({
                        type: 'ice_candidate',
                        sender_id: data.user_id,
                        candidate: data.candidate
                    }));
                    console.log(`[ICE] Cập nhật đường truyền từ ${data.user_id} tới ${data.target_id}`);
                }
            }

            // 4. TÍNH NĂNG TUẦN 3: Seamless Handover (Báo cáo chuyển mạng)
            if (data.type === 'network_change') {
                console.log(`[HANDOVER] User ${data.user_id} vừa chuyển mạng sang ${data.network_type}. Đang giữ phiên...`);
                // Thông báo cho các máy khác trong phòng biết để chuẩn bị nhận luồng âm thanh từ IP mới
            }

        } catch (error) {
            console.error('Lỗi xử lý tin nhắn:', error);
        }
    });

    ws.on('close', () => {
        if (ws.userId) {
            clients.delete(ws.userId);
            console.log(`User [${ws.userId}] đã ngắt kết nối.`);
        }
    });
});

console.log('Signaling Server (Tuần 3) đang chạy tại ws://localhost:8080');