#!/usr/bin/env bash
# Cài bản iOS của Đàm Thoại lên iPhone thật từ Windows (WSL) hoặc Linux bằng xtool.
#
#   GitHub Actions (macOS) build file DamThoai-unsigned.ipa  ->  xtool ký bằng Apple ID của bạn  ->  cài qua USB
#
# Dùng:
#   scripts/ios/install-iphone.sh                      # tự tải .ipa mới nhất của nhánh ios-kmp (cần GitHub CLI "gh")
#   scripts/ios/install-iphone.sh path/to/app.ipa      # cài file .ipa có sẵn
#   BRANCH=main scripts/ios/install-iphone.sh          # lấy bản build của nhánh khác
#
# Chuẩn bị một lần: xem iosApp/README.md, mục "Cài lên iPhone bằng xtool".
set -euo pipefail

ARTIFACT_NAME="DamThoai-iphone-unsigned-ipa"
IPA_NAME="DamThoai-unsigned.ipa"
WORKFLOW="ios.yml"
BRANCH="${BRANCH:-ios-kmp}"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/damthoai-ios"

die() { echo "❌ $*" >&2; exit 1; }
info() { echo "▶ $*"; }

command -v xtool >/dev/null 2>&1 || die "Chưa cài xtool. Xem https://xtool.sh/documentation/xtooldocs/installation-linux"

# 1. Lấy file .ipa
if [[ $# -ge 1 ]]; then
    IPA="$1"
    [[ -f "$IPA" ]] || die "Không thấy file: $IPA"
else
    command -v gh >/dev/null 2>&1 || die "Chưa có GitHub CLI (gh). Cài: sudo apt install gh && gh auth login
   Hoặc tải thủ công artifact '$ARTIFACT_NAME' ở tab Actions trên GitHub rồi chạy: $0 đường/dẫn/$IPA_NAME"

    info "Tìm lần build iOS thành công mới nhất của nhánh '$BRANCH'..."
    RUN_ID="$(gh run list --workflow "$WORKFLOW" --branch "$BRANCH" --status success --limit 1 \
        --json databaseId --jq '.[0].databaseId')"
    [[ -n "$RUN_ID" && "$RUN_ID" != "null" ]] || die "Chưa có lần build iOS nào thành công trên nhánh '$BRANCH' (xem tab Actions)."

    rm -rf "$CACHE_DIR" && mkdir -p "$CACHE_DIR"
    info "Tải artifact '$ARTIFACT_NAME' từ lần chạy #$RUN_ID..."
    gh run download "$RUN_ID" --name "$ARTIFACT_NAME" --dir "$CACHE_DIR"
    IPA="$CACHE_DIR/$IPA_NAME"
    [[ -f "$IPA" ]] || die "Artifact không chứa $IPA_NAME"
fi

# 2. Kiểm tra iPhone đã cắm và tin cậy máy tính
info "Thiết bị đang kết nối:"
DEVICES="$(xtool devices 2>&1 || true)"
echo "$DEVICES"
if ! grep -qiE "iphone|ipad|udid" <<<"$DEVICES"; then
    die "Không thấy iPhone. Kiểm tra: cáp USB, đã bấm 'Tin cậy máy tính này', và (WSL) đã 'usbipd attach --wsl' cho iPhone."
fi

# 3. Ký bằng Apple ID đã đăng nhập (xtool setup) và cài
info "Ký và cài $IPA ..."
xtool install "$IPA"

cat <<'EOF'

✅ Đã cài xong. Trên iPhone:
   - Lần đầu: Cài đặt → Cài đặt chung → Quản lý VPN & Thiết bị → chọn Apple ID của bạn → Tin cậy.
   - Bật "Chế độ nhà phát triển" nếu iOS yêu cầu (Cài đặt → Quyền riêng tư & Bảo mật).
   - Apple ID miễn phí: app hết hạn sau 7 ngày, chạy lại script này để cài lại.
EOF
