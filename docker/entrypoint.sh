#!/usr/bin/env bash
set -euo pipefail

: "${NOVNC_PASSWORD:?NOVNC_PASSWORD must be set}"

mkdir -p /app/data/browser-profile /app/data/state /app/data/diagnostics
chmod 700 /app/data /app/data/browser-profile /app/data/state /app/data/diagnostics 2>/dev/null || true

export DISPLAY=:99
Xvfb :99 -screen 0 1920x1080x24 -nolisten tcp &
XVFB_PID=$!
sleep 1
if ! kill -0 "$XVFB_PID" 2>/dev/null; then
    echo "Xvfb failed to start on DISPLAY=:99" >&2
    exit 1
fi

fluxbox >/tmp/fluxbox.log 2>&1 &

x11vnc -storepasswd "$NOVNC_PASSWORD" /app/data/.vncpass >/dev/null 2>&1
x11vnc -display :99 -localhost -forever -shared -rfbauth /app/data/.vncpass \
    -rfbport 5900 -noxdamage >/tmp/x11vnc.log 2>&1 &

websockify --web=/usr/share/novnc 6080 localhost:5900 >/tmp/websockify.log 2>&1 &

exec java -XX:MaxRAMPercentage=75 -jar /app/app.jar
