const express = require('express');
const { AccessToken } = require('livekit-server-sdk');

const app = express();
const port = 3000;

// Khoá LiveKit KHÔNG ghi trong code (repo công khai): đặt trong file .env (chép từ .env.example)
// rồi chạy: node --env-file=.env api.js   (hoặc: npm run token)
const livekitHost = process.env.LIVEKIT_URL;
const apiKey = process.env.LIVEKIT_API_KEY;
const apiSecret = process.env.LIVEKIT_API_SECRET;

if (!livekitHost || !apiKey || !apiSecret) {
    console.error('Thiếu LIVEKIT_URL / LIVEKIT_API_KEY / LIVEKIT_API_SECRET. Xem .env.example.');
    process.exit(1);
}

// Bổ sung 'async' ở dòng này
app.get('/get-token', async (req, res) => {
    const roomName = 'Kenh_Hau_Can';
    const participantName = 'Nguoi_Dung_Test_01';

    const at = new AccessToken(apiKey, apiSecret, {
        identity: participantName,
    });
    
    at.addGrant({ roomJoin: true, room: roomName });

    // Bổ sung 'await' ở dòng này để Node.js chờ tạo xong token
    const token = await at.toJwt();
    console.log(`Đã cấp vé cho ${participantName} vào phòng ${roomName}`);
    
    res.json({ 
        serverUrl: livekitHost,
        token: token 
    });
});

app.listen(port, () => {
    console.log(`API Server đang chạy tại http://localhost:${port}`);
});