# Link & Login Codespace dari Termux

Codespace aktif untuk repo ini:

- **Repo:** `ivansslo/codex-web`
- **Codespace:** `codex-web-termux-jjwrqxjpr577cw94`
- **Machine:** 4 cores, 16 GB RAM, 32 GB storage
- **Web link:** https://codex-web-termux-jjwrqxjpr577cw94.github.dev

## Buka dari terminal Termux

Jalankan ini di Termux:

```bash
pkg update -y
pkg install -y curl git openssh gh

curl -L -o termux-login-codespace.sh https://raw.githubusercontent.com/ivansslo/codex-web/main/termux-login-codespace.sh
chmod +x termux-login-codespace.sh
./termux-login-codespace.sh
```

## Kalau sudah login GitHub CLI

Langsung masuk SSH:

```bash
gh codespace ssh -c codex-web-termux-jjwrqxjpr577cw94
```

## Buka link web dari Termux

Kalau Termux mendukung `termux-open-url`:

```bash
termux-open-url https://codex-web-termux-jjwrqxjpr577cw94.github.dev
```

Atau copy link ini ke browser Android:

```text
https://codex-web-termux-jjwrqxjpr577cw94.github.dev
```

## Setelah masuk Codespace

```bash
cd /workspaces/codex-web
git pull
npm install
npm run dev
```

## Membuka localhost Codespace di browser Android

Kalau server dev berjalan di Codespace, misalnya Vite port `5173`, jalankan server di terminal Codespace:

```bash
cd /workspaces/codex-web
npm install
npm run dev -- --host 0.0.0.0
```

Lalu dari Termux Android, forward port Codespace ke localhost Android:

```bash
curl -L -o termux-open-codespace-localhost.sh https://raw.githubusercontent.com/ivansslo/codex-web/main/termux-open-codespace-localhost.sh
chmod +x termux-open-codespace-localhost.sh
./termux-open-codespace-localhost.sh
```

Browser Android akan dibuka ke:

```text
http://127.0.0.1:5173
```

Alternatif manual:

```bash
gh codespace ports forward 5173:5173 -c codex-web-termux-jjwrqxjpr577cw94
```

Lalu buka di browser Android:

```text
http://127.0.0.1:5173
```

## Buat Codespace baru dari Termux

Download script pembuat Codespace baru:

```bash
curl -L -o termux-create-new-codespace.sh https://raw.githubusercontent.com/ivansslo/codex-web/main/termux-create-new-codespace.sh
chmod +x termux-create-new-codespace.sh
./termux-create-new-codespace.sh
```

Default script membuat Codespace baru untuk repo `ivansslo/codex-web` dengan machine:

```text
standardLinux32gb = 4 cores, 16 GB RAM, 32 GB storage
```

Setelah selesai, script menyimpan nama Codespace baru di:

```text
~/.codex-web-codespace
```

Pakai config tersebut untuk login/forward:

```bash
source ~/.codex-web-codespace
gh codespace ssh -c "$CODESPACE_NAME"
```

Kalau ingin nama custom:

```bash
DISPLAY_NAME=codex-web-baru ./termux-create-new-codespace.sh
```

Kalau ingin machine lebih kecil:

```bash
MACHINE=basicLinux32gb ./termux-create-new-codespace.sh
```
