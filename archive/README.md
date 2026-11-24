# Lưu trữ: các backend không dùng

Server chính của app là `signaling_server.py` ở gốc repo (Python, WebSocket cổng 8765, deploy lên Render qua `render.yaml`). App Android/iOS chỉ nói chuyện với server này.

Thư mục này giữ lại bài làm của nhóm để tham khảo, **không chạy, không deploy**:

| Thư mục / file | Là gì | Vì sao không dùng |
|---|---|---|
| `backend-tv5/` | Server Go của TV5 (Tuần 1-4: signaling, API sự kiện/kênh, sinh QR, giành lượt nói) | Giao thức chỉ có `register`, `offer`, `webrtc_config`, không khớp các tin app gửi (`floor_request`, `set_seat`, `kick`, ...). `go build ./...` vẫn chạy được. |
| `server.js` | Server signaling bằng Node (cổng 8080) | Bản thử, app không kết nối tới. |
| `api.js`, `package.json`, `package-lock.json` | Cấp token LiveKit (Express) | App không dùng LiveKit. |

Nếu sau này muốn dùng lại phần nào, chuyển nó ra khỏi `archive/` và nối vào app.
