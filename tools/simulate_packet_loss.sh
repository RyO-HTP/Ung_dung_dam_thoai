#!/usr/bin/env bash
# TV1 - Tuần 6: Giả lập rớt gói tin (Packet Loss), trễ, jitter để kiểm tra khả năng phục hồi âm thanh.
#
# Dùng trên máy Linux đóng vai trò cục phát Wi-Fi / router cho các điện thoại test
# (vd: laptop Ubuntu bật Hotspot, hoặc Raspberry Pi). Cần quyền root và gói iproute2 (tc netem).
#
# Cách dùng:
#   sudo ./simulate_packet_loss.sh <interface> <kịch_bản>
#   sudo ./simulate_packet_loss.sh wlan0 loss10      # rớt 10% gói
#   sudo ./simulate_packet_loss.sh wlan0 off         # tắt giả lập
#
# Trong lúc chạy, xem chỉ số trên app (log tag "DamThoaiCallManager" / file CSV của FieldTestLogger,
# sự kiện "webrtc_stats"): lossRate, jitterMs, conceal (tỉ lệ âm thanh phải vá), rtt.
#
# Trên Windows: dùng công cụ "clumsy" (https://jagt.github.io/clumsy/) với filter "udp" và
# bật Drop 5/10/20/30%, Lag 100-300ms, tương đương các kịch bản dưới.

set -euo pipefail

IFACE="${1:-}"
SCENARIO="${2:-}"

if [[ -z "$IFACE" || -z "$SCENARIO" ]]; then
  sed -n '2,20p' "$0"
  echo
  echo "Kịch bản: off | loss5 | loss10 | loss20 | loss30 | burst | jitter | 4g_bad | handover_blackout"
  exit 1
fi

tc qdisc del dev "$IFACE" root 2>/dev/null || true

case "$SCENARIO" in
  off)
    echo "Đã tắt giả lập trên $IFACE" ;;
  loss5)
    tc qdisc add dev "$IFACE" root netem loss 5% ;;
  loss10)
    tc qdisc add dev "$IFACE" root netem loss 10% ;;
  loss20)
    tc qdisc add dev "$IFACE" root netem loss 20% ;;
  loss30)
    tc qdisc add dev "$IFACE" root netem loss 30% ;;
  burst)
    # Rớt gói theo cụm (Gilbert-Elliott) - giống sóng chập chờn trong hội trường đông người
    tc qdisc add dev "$IFACE" root netem loss gemodel 5% 30% 70% 0.1% ;;
  jitter)
    tc qdisc add dev "$IFACE" root netem delay 80ms 40ms distribution normal ;;
  4g_bad)
    tc qdisc add dev "$IFACE" root netem delay 150ms 60ms loss 8% reorder 2% rate 256kbit ;;
  handover_blackout)
    # Mất mạng hoàn toàn 3 giây rồi phục hồi - đo thời gian ICE restart nối lại
    tc qdisc add dev "$IFACE" root netem loss 100%
    echo "Mất mạng 100% trong 3 giây..."
    sleep 3
    tc qdisc del dev "$IFACE" root
    echo "Đã phục hồi mạng. Xem log 'ice_restart' / 'ice_connected' để tính thời gian nối lại." ;;
  *)
    echo "Kịch bản không hợp lệ: $SCENARIO"; exit 1 ;;
esac

if [[ "$SCENARIO" != "off" && "$SCENARIO" != "handover_blackout" ]]; then
  echo "Đang áp dụng '$SCENARIO' trên $IFACE:"
  tc qdisc show dev "$IFACE"
fi
