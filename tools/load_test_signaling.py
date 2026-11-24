"""
TV1 - Tuần 6: Đo độ chịu tải thực tế của Signaling Server.

Mô phỏng N thiết bị bộ đàm cùng kết nối, chia thành từng cặp và bắn liên tục offer/answer/ice_candidate
qua server giống hệt app Android. Đo:
  - Thời gian kết nối + nhận cấu hình STUN/TURN (register -> webrtc_config)
  - Độ trễ chuyển tiếp 1 chiều của gói tin signaling (A -> server -> B)
  - Tỉ lệ gói bị mất / lỗi kết nối, thông lượng (msg/s)
  - (tuỳ chọn) --drop: tự ý "rớt" một phần gói phía client để kiểm tra server không bị treo

Cách chạy:
    pip install websockets==12.0
    python signaling_server.py                     # cửa sổ 1
    python tools/load_test_signaling.py --clients 200 --messages 50   # cửa sổ 2

    # Thử với server trên Cloud:
    python tools/load_test_signaling.py --url ws://<IP>:8765 --clients 500 --ramp 10
"""

import argparse
import asyncio
import json
import statistics
import time

import websockets


def pct(values, p):
    if not values:
        return float("nan")
    values = sorted(values)
    k = min(len(values) - 1, max(0, int(round(p / 100 * (len(values) - 1)))))
    return values[k]


class Results:
    def __init__(self):
        self.connect_ms = []
        self.forward_ms = []
        self.ping_ms = []
        self.sent = 0
        self.received = 0
        self.connect_failures = 0
        self.errors = 0


async def client(idx, peer_idx, args, res, start_barrier, done_event):
    user_id = f"load_{args.run_id}_{idx}"
    peer_id = f"load_{args.run_id}_{peer_idx}"
    t0 = time.perf_counter()
    try:
        ws = await asyncio.wait_for(websockets.connect(args.url, max_size=2 ** 20), timeout=10)
    except Exception:
        res.connect_failures += 1
        await start_barrier.wait()
        return

    try:
        await run_session(ws, user_id, peer_id, t0, args, res, start_barrier, done_event)
    finally:
        await ws.close()


async def run_session(ws, user_id, peer_id, t0, args, res, start_barrier, done_event):
    if True:
        try:
            await ws.send(json.dumps({"type": "register", "user_id": user_id}))
            while True:
                msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=10))
                if msg.get("type") == "webrtc_config":
                    res.connect_ms.append((time.perf_counter() - t0) * 1000)
                    break
        except Exception:
            res.connect_failures += 1
            await start_barrier.wait()
            return

        await start_barrier.wait()  # đợi mọi client đã đăng ký rồi mới bắn tải

        async def receiver():
            try:
                async for raw in ws:
                    msg = json.loads(raw)
                    t = msg.get("type")
                    if t in ("offer", "answer", "ice_candidate"):
                        sent_at = float(msg.get("sdp") or msg.get("candidate") or 0)
                        res.forward_ms.append((time.time() - sent_at) * 1000)
                        res.received += 1
                    elif t == "pong":
                        res.ping_ms.append((time.time() - float(msg.get("ts", 0))) * 1000)
            except websockets.exceptions.ConnectionClosed:
                pass

        recv_task = asyncio.create_task(receiver())
        import random

        types = ["offer", "answer", "ice_candidate", "ice_candidate", "ice_candidate"]
        for i in range(args.messages):
            t = types[i % len(types)]
            now = str(time.time())
            payload = {"type": t, "target_id": peer_id}
            if t == "ice_candidate":
                payload.update({"sdpMid": "0", "sdpMLineIndex": 0, "candidate": now})
            else:
                payload["sdp"] = now
            if args.drop > 0 and random.random() < args.drop:
                await asyncio.sleep(args.interval)
                continue  # mô phỏng gói rớt phía client
            try:
                await ws.send(json.dumps(payload))
                res.sent += 1
                if i % 10 == 0:
                    await ws.send(json.dumps({"type": "ping", "ts": time.time()}))
            except Exception:
                res.errors += 1
                break
            await asyncio.sleep(args.interval)

        await done_event.wait()
        recv_task.cancel()


async def main():
    ap = argparse.ArgumentParser(description="Stress test Signaling Server bộ đàm")
    ap.add_argument("--url", default="ws://127.0.0.1:8765")
    ap.add_argument("--clients", type=int, default=100, help="số thiết bị giả lập (chẵn)")
    ap.add_argument("--messages", type=int, default=30, help="số gói signaling mỗi thiết bị gửi")
    ap.add_argument("--interval", type=float, default=0.05, help="giãn cách giữa 2 gói (giây)")
    ap.add_argument("--ramp", type=float, default=2.0, help="thời gian tăng dần số kết nối (giây)")
    ap.add_argument("--drop", type=float, default=0.0, help="tỉ lệ gói client cố tình bỏ (0-1)")
    args = ap.parse_args()
    args.clients += args.clients % 2
    args.run_id = int(time.time())

    res = Results()
    barrier = asyncio.Barrier(args.clients)
    done = asyncio.Event()

    tasks = []
    t_start = time.perf_counter()
    for i in range(args.clients):
        peer = i + 1 if i % 2 == 0 else i - 1
        tasks.append(asyncio.create_task(client(i, peer, args, res, barrier, done)))
        if args.ramp > 0:
            await asyncio.sleep(args.ramp / args.clients)

    # chờ gửi xong rồi cho thêm 2s để các gói cuối kịp tới nơi
    send_time = args.messages * args.interval
    await asyncio.sleep(send_time + 2.0)
    done.set()
    for r in await asyncio.gather(*tasks, return_exceptions=True):
        if isinstance(r, Exception):
            res.errors += 1
            print(f"[lỗi client] {r!r}")
    elapsed = time.perf_counter() - t_start

    # Hỏi server thống kê
    server_stats = {}
    try:
        async with websockets.connect(args.url) as ws:
            await ws.send(json.dumps({"type": "register", "user_id": f"load_{args.run_id}_stats"}))
            await ws.recv()
            await ws.send(json.dumps({"type": "stats"}))
            server_stats = json.loads(await asyncio.wait_for(ws.recv(), timeout=5))
    except Exception:
        pass

    delivered = res.received / res.sent * 100 if res.sent else 0
    print("\n===== KẾT QUẢ STRESS TEST SIGNALING =====")
    print(f"Server                : {args.url}")
    print(f"Thiết bị giả lập      : {args.clients} (lỗi kết nối: {res.connect_failures})")
    print(f"Gói đã gửi / nhận     : {res.sent} / {res.received}  ({delivered:.2f}% tới nơi)")
    print(f"Lỗi gửi               : {res.errors}")
    print(f"Thông lượng           : {res.received / elapsed:.1f} gói/s")
    if res.connect_ms:
        print(f"Kết nối+cấu hình TURN : p50={pct(res.connect_ms, 50):.1f}ms "
              f"p95={pct(res.connect_ms, 95):.1f}ms max={max(res.connect_ms):.1f}ms")
    if res.forward_ms:
        print(f"Trễ chuyển tiếp SDP/ICE: p50={pct(res.forward_ms, 50):.1f}ms "
              f"p95={pct(res.forward_ms, 95):.1f}ms p99={pct(res.forward_ms, 99):.1f}ms "
              f"tb={statistics.mean(res.forward_ms):.1f}ms")
    if res.ping_ms:
        print(f"Ping server (RTT)     : p50={pct(res.ping_ms, 50):.1f}ms p95={pct(res.ping_ms, 95):.1f}ms")
    if server_stats:
        print(f"Thống kê phía server  : {server_stats}")


if __name__ == "__main__":
    asyncio.run(main())
