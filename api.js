const express = require('express');
const { AccessToken } = require('livekit-server-sdk');

const app = express();
const port = 3000;

const livekitHost = 'wss://ung-dung-dam-thoai-pze2v7u9.livekit.cloud'; 
const apiKey = 'API35CD8iXuuHQP';
const apiSecret = 'skNGqlelCYwJieKdYxa6if5aEf1kS9eh2PcxfoXWl4xF';

// Bổ sung 'async' ở dòng này
app.get('/get-token', async (req, res) => {
    const roomName = req.query.room || 'Kenh_Hau_Can';
    const participantName = req.query.identity || req.query.user_id || 'Nguoi_Dung_Test_01';

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