#!/bin/sh
set -eu
# Compatibility entry point for an isolated Linux developer machine.
# macOS packages are built on native GitHub macOS runners.
cd "$(dirname "$0")/.."
if [ -d /var/www/skin-api ] || [ -f /etc/systemd/system/batclient-skin-api.service ]; then
    echo 'Refusing to package on the Bat Client production server.' >&2
    exit 1
fi
npm ci --no-audit --no-fund
node test/platform.test.cjs
npm run dist:linux
