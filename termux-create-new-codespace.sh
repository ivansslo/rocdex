#!/data/data/com.termux/files/usr/bin/bash
# Buat GitHub Codespace baru dari Termux untuk repo codex-web.
# Default machine: standardLinux32gb = 4 cores, 16 GB RAM, 32 GB storage.
#
# Cara pakai:
#   curl -L -o termux-create-new-codespace.sh https://raw.githubusercontent.com/ivansslo/codex-web/main/termux-create-new-codespace.sh
#   chmod +x termux-create-new-codespace.sh
#   ./termux-create-new-codespace.sh
#
# Custom:
#   MACHINE=basicLinux32gb ./termux-create-new-codespace.sh
#   DISPLAY_NAME=codex-web-baru ./termux-create-new-codespace.sh

set -euo pipefail

REPO="${REPO:-ivansslo/codex-web}"
BRANCH="${BRANCH:-main}"
MACHINE="${MACHINE:-standardLinux32gb}"
IDLE_TIMEOUT_MINUTES="${IDLE_TIMEOUT_MINUTES:-30}"
DISPLAY_NAME="${DISPLAY_NAME:-codex-web-termux-$(date +%Y%m%d-%H%M%S)}"
CONFIG_FILE="${CONFIG_FILE:-$HOME/.codex-web-codespace}"

info() { printf '\033[1;34m[info]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
err() { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; }

if command -v pkg >/dev/null 2>&1; then
  info "Install dependency Termux..."
  pkg update -y
  pkg install -y gh git openssh curl
fi

if ! command -v gh >/dev/null 2>&1; then
  err "GitHub CLI belum tersedia. Install: pkg install gh"
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  if [ -n "${GH_TOKEN:-}" ]; then
    info "Login GitHub CLI memakai GH_TOKEN..."
    printf '%s' "$GH_TOKEN" | gh auth login --with-token
  else
    warn "Belum login GitHub CLI."
    warn "Buat token GitHub dengan scope minimal: codespace dan repo."
    printf 'Paste GitHub token (input disembunyikan): '
    stty -echo
    read -r TOKEN
    stty echo
    printf '\n'
    if [ -z "${TOKEN}" ]; then
      err "Token kosong. Batal."
      exit 1
    fi
    printf '%s' "$TOKEN" | gh auth login --with-token
    unset TOKEN
  fi
fi

info "Repo: ${REPO}"
info "Branch: ${BRANCH}"
info "Machine: ${MACHINE}"
info "Display name: ${DISPLAY_NAME}"

info "Machine yang tersedia untuk repo ini:"
gh api "repos/${REPO}/codespaces/machines" \
  --jq '.machines[] | "- \(.name): \(.display_name)"' || true

PAYLOAD_FILE="${TMPDIR:-/tmp}/codespace-create-$$.json"
cat > "$PAYLOAD_FILE" <<JSON
{
  "ref": "${BRANCH}",
  "machine": "${MACHINE}",
  "idle_timeout_minutes": ${IDLE_TIMEOUT_MINUTES},
  "display_name": "${DISPLAY_NAME}"
}
JSON

info "Membuat Codespace baru..."
CREATE_RESPONSE="$(gh api -X POST "repos/${REPO}/codespaces" --input "$PAYLOAD_FILE")"
rm -f "$PAYLOAD_FILE"

CODESPACE_NAME="$(printf '%s' "$CREATE_RESPONSE" | gh api --input - --jq '.name' 2>/dev/null || true)"
WEB_URL="$(printf '%s' "$CREATE_RESPONSE" | gh api --input - --jq '.web_url' 2>/dev/null || true)"

# Fallback parser kalau gh api --input - tidak support di versi lama.
if [ -z "$CODESPACE_NAME" ]; then
  CODESPACE_NAME="$(printf '%s' "$CREATE_RESPONSE" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("name", ""))' 2>/dev/null || true)"
fi
if [ -z "$WEB_URL" ]; then
  WEB_URL="$(printf '%s' "$CREATE_RESPONSE" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("web_url", ""))' 2>/dev/null || true)"
fi

if [ -z "$CODESPACE_NAME" ]; then
  err "Gagal membaca nama Codespace dari response:"
  printf '%s\n' "$CREATE_RESPONSE" >&2
  exit 1
fi

info "Codespace dibuat: ${CODESPACE_NAME}"
[ -n "$WEB_URL" ] && info "Web URL: ${WEB_URL}"

cat > "$CONFIG_FILE" <<EOF
export CODESPACE_NAME='${CODESPACE_NAME}'
export REPO='${REPO}'
export CODESPACE_WEB_URL='${WEB_URL}'
EOF

info "Config tersimpan: ${CONFIG_FILE}"
info "Untuk pakai di script lain: source ${CONFIG_FILE}"

info "Menunggu state Available..."
for i in $(seq 1 60); do
  STATE="$(gh api "user/codespaces/${CODESPACE_NAME}" --jq '.state' 2>/dev/null || true)"
  printf '[%02d/60] state: %s\n' "$i" "${STATE:-unknown}"
  if [ "$STATE" = "Available" ]; then
    break
  fi
  sleep 5
done

info "Masuk SSH Codespace:"
printf '  gh codespace ssh -c %s\n' "$CODESPACE_NAME"

info "Buka web Codespace:"
if [ -n "$WEB_URL" ]; then
  printf '  %s\n' "$WEB_URL"
  if command -v termux-open-url >/dev/null 2>&1; then
    termux-open-url "$WEB_URL" || true
  fi
fi

info "Kalau ingin langsung SSH sekarang, jalankan:"
printf '  gh codespace ssh -c %s\n' "$CODESPACE_NAME"

info "Kalau ingin forward localhost dev server Android, jalankan:"
printf '  source %s\n' "$CONFIG_FILE"
printf '  ./termux-open-codespace-localhost.sh\n'
