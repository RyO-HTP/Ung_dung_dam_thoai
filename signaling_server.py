import asyncio
import websockets
import json
from webrtc_config import get_ice_servers_config # Gọi module cấu hình NAT

connected_clients = {}
offline_queue = {}

async def signaling_handler(websocket):
    user_id = None
    try:
        async for message in websocket:
            data = json.loads(message)
            
            # 1. Xử lý khi thiết bị gửi gói tin đăng ký
            if data['type'] == 'register':
                user_id = data['user_id']
                connected_clients[user_id] = websocket
                print(f"[{user_id}] đã kết nối mạng.")
                
                # --- PHẦN MỚI THÊM: Gửi cấu hình STUN/TURN cho thiết bị ---
                ice_config_msg = {
                    "type": "webrtc_config",
                    "config": json.loads(get_ice_servers_config())
                }
                await websocket.send(json.dumps(ice_config_msg))
                print(f"Đã cấp phát cấu hình STUN/TURN cho [{user_id}]")
                # ------------------------------------------------------------
                
                # Logic khôi phục gói tin nhỡ (Store-and-Forward) giữ nguyên
                if user_id in offline_queue and len(offline_queue[user_id]) > 0:
                    print(f"Đang khôi phục {len(offline_queue[user_id])} gói tin nhỡ cho [{user_id}]")
                    for missed_msg in offline_queue[user_id]:
                        await websocket.send(json.dumps(missed_msg))
                    offline_queue[user_id] = []
            
            # Logic định tuyến offer, answer, ice_candidate giữ nguyên...
            elif data['type'] in ['offer', 'answer', 'ice_candidate']:
                target_id = data['target_id']
                if target_id in connected_clients:
                    await connected_clients[target_id].send(message)
                else:
                    if target_id not in offline_queue:
                        offline_queue[target_id] = []
                    offline_queue[target_id].append(data)

    except websockets.exceptions.ConnectionClosed:
        print(f"[{user_id}] đã ngắt kết nối đột ngột.")
    finally:
        if user_id in connected_clients:
            del connected_clients[user_id]

async def main():
    server = await websockets.serve(signaling_handler, "0.0.0.0", 8765)
    print("Signaling Server đang chạy trên cổng 8765...")
    await server.wait_closed()

if __name__ == "__main__":
    asyncio.run(main())