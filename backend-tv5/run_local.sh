#!/usr/bin/env bash
# Chạy Backend Go (sự kiện / PIN / QR / Floor / Live Cue) trên máy local.
set -euo pipefail
cd "$(dirname "$0")"

export GOPROXY="${GOPROXY:-https://proxy.golang.org,direct}"
export HTTP_ADDR="${HTTP_ADDR:-:8080}"
export UDP_RELAY_ADDR="${UDP_RELAY_ADDR:-:50000}"

echo "==> DamThoai backend-tv5"
echo "    HTTP_ADDR=$HTTP_ADDR"
echo "    UDP_RELAY_ADDR=$UDP_RELAY_ADDR"
echo "    PIN mẫu: 240826 | 889900 | 482910"
echo "    Admin restore: ADM-DEMO01 | ADM-DEMO02 | ADM-DEMO03"
echo "    Trên điện thoại (cùng Wi‑Fi): Cài đặt → Máy chủ Backend → http://<IP-máy>:8080"
echo ""

if ! command -v go >/dev/null 2>&1; then
  echo "Chưa cài Go. Cài tại https://go.dev/dl/ rồi chạy lại." >&2
  exit 1
fi

go run .
