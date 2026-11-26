"""
TV1 - Kiểm tra tự động giao thức Signaling Server (signaling_server.py).

Chạy:  python -m unittest discover -s tests -v
Không cần mạng ngoài: mỗi bài mở máy chủ thật trên một cổng ngẫu nhiên của máy.
"""

import asyncio
import json
import os
import sys
import time
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import websockets  # noqa: E402

import signaling_server as srv  # noqa: E402


class SignalingServerTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        srv.connected_clients.clear()
        srv.offline_queue.clear()
        srv.claims.clear()
        srv.stats.update(messages_forwarded=0, messages_queued=0, registrations=0, rejected=0)
        self._old_token = os.environ.pop("SIGNALING_TOKEN", None)
        srv.PEERS_BROADCAST_DELAY_SECONDS = 0.02
        srv._peers_broadcast_task = None
        self.server = await srv.start_server("127.0.0.1", 0)
        self.url = f"ws://127.0.0.1:{self.server.sockets[0].getsockname()[1]}"
        self.sockets = []

    async def asyncTearDown(self):
        for ws in self.sockets:
            await ws.close()
        self.server.close()
        await self.server.wait_closed()
        os.environ.pop("SIGNALING_TOKEN", None)
        if self._old_token is not None:
            os.environ["SIGNALING_TOKEN"] = self._old_token

    # ------------------------------------------------------------ tiện ích

    async def connect(self, user_id=None, device_key=None, token=None):
        ws = await websockets.connect(self.url)
        self.sockets.append(ws)
        if user_id is not None:
            msg = {"type": "register", "user_id": user_id,
                   "device_key": device_key or f"may-cua-{user_id}-0123456789"}
            if token is not None:
                msg["token"] = token
            await ws.send(json.dumps(msg))
        return ws

    async def expect_closed(self, ws, code):
        with self.assertRaises(websockets.exceptions.ConnectionClosed) as ctx:
            while True:
                await asyncio.wait_for(ws.recv(), timeout=2)
        self.assertEqual(ctx.exception.rcvd.code, code)

    async def recv_type(self, ws, msg_type, timeout=2.0):
        """Đọc tới khi gặp gói đúng loại (bỏ qua các gói khác, vd: peers)."""
        deadline = time.monotonic() + timeout
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise AssertionError(f"Không nhận được gói '{msg_type}' trong {timeout}s")
            try:
                raw = await asyncio.wait_for(ws.recv(), timeout=remaining)
            except asyncio.TimeoutError:
                raise AssertionError(f"Không nhận được gói '{msg_type}' trong {timeout}s")
            msg = json.loads(raw)
            if msg.get("type") == msg_type:
                return msg

    async def latest_peers(self, ws, expected, timeout=2.0):
        """Đợi tới khi danh sách online đúng bằng expected."""
        deadline = time.monotonic() + timeout
        last = None
        while time.monotonic() < deadline:
            try:
                last = await self.recv_type(ws, "peers", timeout=deadline - time.monotonic())
            except AssertionError:
                break
            if sorted(last["peers"]) == sorted(expected):
                return last
        raise AssertionError(f"Danh sách online cuối cùng {last} khác mong đợi {expected}")

    # ------------------------------------------------------------ Tuần 1

    async def test_register_returns_webrtc_config(self):
        ws = await self.connect("A")
        msg = await self.recv_type(ws, "webrtc_config")
        ice = msg["config"]["iceServers"]
        self.assertTrue(ice, "phải có ít nhất 1 ICE server")
        self.assertTrue(any("stun:" in json.dumps(s) for s in ice))
        self.assertTrue(any("turn:" in json.dumps(s) for s in ice), "phải có TURN để vượt NAT 4G")

    async def test_forward_offer_and_rewrite_sender(self):
        a = await self.connect("A")
        b = await self.connect("B")
        await self.recv_type(a, "webrtc_config")
        await self.recv_type(b, "webrtc_config")
        # A cố giả mạo sender_id -> server phải ghi đè bằng user_id thật
        await a.send(json.dumps({"type": "offer", "target_id": "B", "sender_id": "KeGiaMao", "sdp": "v=0"}))
        msg = await self.recv_type(b, "offer")
        self.assertEqual(msg["sender_id"], "A")
        self.assertEqual(msg["sdp"], "v=0")

    async def test_unregistered_client_cannot_send(self):
        b = await self.connect("B")
        await self.recv_type(b, "webrtc_config")
        stranger = await self.connect()  # không register
        await stranger.send(json.dumps({"type": "offer", "target_id": "B", "sdp": "x"}))
        with self.assertRaises(AssertionError):
            await self.recv_type(b, "offer", timeout=0.3)

    # ------------------------------------------------------------ Danh sách online

    async def test_peers_list_updates_on_join_and_leave(self):
        a = await self.connect("A")
        await self.latest_peers(a, ["A"])
        b = await self.connect("B")
        await self.latest_peers(a, ["A", "B"])
        await self.latest_peers(b, ["A", "B"])
        await b.close()
        await self.latest_peers(a, ["A"])

    # ------------------------------------------------------------ Tuần 3

    async def test_forward_ice_candidates_removed_and_hangup(self):
        a = await self.connect("A")
        b = await self.connect("B")
        await self.recv_type(b, "webrtc_config")
        removed = {"type": "ice_candidates_removed", "target_id": "B",
                   "candidates": [{"sdpMid": "0", "sdpMLineIndex": 0, "candidate": "candidate:1"}]}
        await a.send(json.dumps(removed))
        msg = await self.recv_type(b, "ice_candidates_removed")
        self.assertEqual(msg["candidates"], removed["candidates"])
        await a.send(json.dumps({"type": "hangup", "target_id": "B"}))
        self.assertEqual((await self.recv_type(b, "hangup"))["sender_id"], "A")

    # ------------------------------------------------------------ Tuần 4

    async def test_handover_new_connection_replaces_old(self):
        """Đổi Wi-Fi -> 4G: kết nối mới vào trước khi kết nối cũ đóng; đóng cái cũ không được làm mất định tuyến."""
        b = await self.connect("B")
        await self.recv_type(b, "webrtc_config")
        old_a = await self.connect("A")
        await self.recv_type(old_a, "webrtc_config")
        new_a = await self.connect("A")
        await self.recv_type(new_a, "webrtc_config")
        await old_a.close()
        await asyncio.sleep(0.1)
        self.assertIn("A", srv.connected_clients)
        await b.send(json.dumps({"type": "answer", "target_id": "A", "sdp": "ans"}))
        self.assertEqual((await self.recv_type(new_a, "answer"))["sdp"], "ans")

    async def test_offline_queue_delivered_after_reconnect(self):
        a = await self.connect("A")
        await self.recv_type(a, "webrtc_config")
        await a.send(json.dumps({"type": "ice_candidate", "target_id": "B", "candidate": "c1",
                                 "sdpMid": "0", "sdpMLineIndex": 0}))
        await asyncio.sleep(0.1)
        self.assertEqual(srv.stats["messages_queued"], 1)
        b = await self.connect("B")  # B vào sau -> nhận bù gói nhỡ
        self.assertEqual((await self.recv_type(b, "ice_candidate"))["candidate"], "c1")

    async def test_expired_offline_messages_are_dropped(self):
        srv.offline_queue["B"] = [(time.time() - srv.OFFLINE_MESSAGE_TTL_SECONDS - 1, {"type": "offer"})]
        srv.offline_queue["C"] = [(time.time() - srv.OFFLINE_MESSAGE_TTL_SECONDS - 1, {"type": "offer"})]
        srv.prune_offline_queue()
        self.assertEqual(srv.offline_queue, {})

    # ------------------------------------------------------------ Tuần 6

    async def test_ping_pong_and_stats(self):
        a = await self.connect("A")
        await a.send(json.dumps({"type": "ping", "ts": 123.5}))
        self.assertEqual((await self.recv_type(a, "pong"))["ts"], 123.5)
        await a.send(json.dumps({"type": "stats"}))
        st = await self.recv_type(a, "stats")
        self.assertEqual(st["online"], 1)
        self.assertEqual(st["registrations"], 1)

    # ------------------------------------------------------------ Bảo mật

    async def test_token_required_when_configured(self):
        os.environ["SIGNALING_TOKEN"] = "ma-bi-mat-cua-nhom"
        stranger = await self.connect("KeLa", token="doan-bua")
        await self.expect_closed(stranger, srv.CLOSE_UNAUTHORIZED)
        no_token = await self.connect("KeLa2")
        await self.expect_closed(no_token, srv.CLOSE_UNAUTHORIZED)
        member = await self.connect("A", token="ma-bi-mat-cua-nhom")
        await self.recv_type(member, "webrtc_config")

    async def test_cannot_steal_online_user_id(self):
        victim = await self.connect("A", device_key="may-that-cua-A-123456")
        await self.recv_type(victim, "webrtc_config")
        thief = await self.connect("A", device_key="may-ke-gian-999999999")
        await self.expect_closed(thief, srv.CLOSE_USER_ID_TAKEN)
        b = await self.connect("B")
        await self.recv_type(b, "webrtc_config")
        await b.send(json.dumps({"type": "offer", "target_id": "A", "sdp": "x"}))
        self.assertEqual((await self.recv_type(victim, "offer"))["sdp"], "x")

    async def test_user_id_reserved_after_drop_then_expires(self):
        a = await self.connect("A", device_key="may-that-cua-A-123456")
        await self.recv_type(a, "webrtc_config")
        await a.close()
        await asyncio.sleep(0.1)
        thief = await self.connect("A", device_key="may-ke-gian-999999999")
        await self.expect_closed(thief, srv.CLOSE_USER_ID_TAKEN)
        # Sau CLAIM_TTL_SECONDS tên được nhả ra cho máy khác dùng
        srv.claims["A"]["released_at"] -= srv.CLAIM_TTL_SECONDS + 1
        newcomer = await self.connect("A", device_key="may-moi-cua-A-77777777")
        await self.recv_type(newcomer, "webrtc_config")

    async def test_bad_register_rejected(self):
        no_key = await websockets.connect(self.url)
        self.sockets.append(no_key)
        await no_key.send(json.dumps({"type": "register", "user_id": "A"}))
        await self.expect_closed(no_key, srv.CLOSE_BAD_REQUEST)
        weird = await self.connect("A B<script>")
        await self.expect_closed(weird, srv.CLOSE_BAD_REQUEST)

    async def test_stats_requires_registration(self):
        stranger = await self.connect()
        await stranger.send(json.dumps({"type": "stats"}))
        with self.assertRaises(AssertionError):
            await self.recv_type(stranger, "stats", timeout=0.3)

    async def test_health_check(self):
        port = self.server.sockets[0].getsockname()[1]
        reader, writer = await asyncio.open_connection("127.0.0.1", port)
        writer.write(b"GET /healthz HTTP/1.1\r\nHost: x\r\n\r\n")
        await writer.drain()
        status = await asyncio.wait_for(reader.readline(), timeout=2)
        writer.close()
        self.assertIn(b"200", status)

    async def test_bad_json_is_ignored(self):
        a = await self.connect("A")
        await a.send("khong phai json")
        await a.send(json.dumps({"type": "ping", "ts": 1}))
        self.assertEqual((await self.recv_type(a, "pong"))["ts"], 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
