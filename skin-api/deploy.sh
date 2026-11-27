#!/usr/bin/env bash
set -euo pipefail

# Deploy the Bat Client hosted skin API to the batclient.xyz VM.
# Run locally as:  ssh -i ~/.ssh/oracle.key ubuntu@51.170.86.154 'sudo bash /tmp/deploy-api.sh'
# Or copy this script to /tmp on the VM and run as root.

APP_DIR=/var/www/skin-api
SERVICE=/etc/systemd/system/batclient-skin-api.service

if [[ $EUID -ne 0 ]]; then
  echo "Run as root (sudo)."
  exit 1
fi

echo "--- Installing Node.js 18.x (if missing) ---"
if ! command -v node >/dev/null 2>&1; then
  curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
  apt-get install -y nodejs
fi
node -v || true

echo "--- Placing app files ---"
mkdir -p "$APP_DIR"
# scp server.js + package.json + .env.example into $APP_DIR first from the repo.
cp -r . "$APP_DIR" 2>/dev/null || true
chown -R www-data:www-data "$APP_DIR"
chmod 700 "$APP_DIR/keys" "$APP_DIR/data" 2>/dev/null || true

echo "--- Install service ---"
cp -f batclient-skin-api.service "$SERVICE"
systemctl daemon-reload

if [[ -f "$APP_DIR/.env" ]]; then
  echo "--- .env present, validating token ---"
  grep -q BAT_SKIN_UPLOAD_TOKEN "$APP_DIR/.env" || { echo "ERROR: .env missing token"; exit 1; }
else
  echo "WARNING: no .env yet — create $APP_DIR/.env with BAT_SKIN_UPLOAD_TOKEN."
fi

echo "--- Enable + start ---"
systemctl enable batclient-skin-api
systemctl restart batclient-skin-api
sleep 1
systemctl status batclient-skin-api --no-pager | head -15 || true

echo "--- Nginx check (apply location manually if needed) ---"
echo "Add to /etc/nginx/sites-enabled/default inside the server block:"
echo '  location /skinapi/ { proxy_pass http://127.0.0.1:8090/; proxy_set_header Host $host; }'
echo "Then: nginx -t && systemctl reload nginx"
echo "DONE."