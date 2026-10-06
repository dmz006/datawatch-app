#!/usr/bin/env python3
"""Keep demo sessions "running" while screenshots are taken.

The daemon only watches a session's tmux pane for activity while a client is
subscribed to it; an unwatched session with no structural channel events is
flipped to "waiting for input" after 15 s. This subscribes (like the PWA) to
the given sessions over the daemon's WebSocket and just drains the stream, so
their live panes (the watch spinner, etc.) keep counting as activity.

Usage: keep-active.py <session-id> [<session-id> ...]
Env:   DW_URL (default https://127.0.0.1:18443), DW_TOKEN. Stdlib only.
"""
import base64
import json
import os
import socket
import ssl
import struct
import sys
import time
from urllib.parse import urlparse


def frame(text):
    payload = text.encode()
    mask = os.urandom(4)
    head = bytes([0x81])
    n = len(payload)
    if n < 126:
        head += bytes([0x80 | n])
    elif n < 65536:
        head += bytes([0x80 | 126]) + struct.pack("!H", n)
    else:
        head += bytes([0x80 | 127]) + struct.pack("!Q", n)
    return head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(payload))


def run(url, token, ids):
    u = urlparse(url)
    raw = socket.create_connection((u.hostname, u.port or 443), timeout=30)
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE  # demo server's self-signed cert, loopback only
    sock = ctx.wrap_socket(raw, server_hostname=u.hostname)
    key = base64.b64encode(os.urandom(16)).decode()
    req = (
        f"GET /ws HTTP/1.1\r\nHost: {u.hostname}:{u.port}\r\nUpgrade: websocket\r\n"
        f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n"
        f"Authorization: Bearer {token}\r\n\r\n"
    )
    sock.sendall(req.encode())
    resp = b""
    while b"\r\n\r\n" not in resp:
        chunk = sock.recv(4096)
        if not chunk:
            raise RuntimeError("connection closed during handshake")
        resp += chunk
    status = resp.split(b"\r\n", 1)[0].decode()
    if " 101 " not in status:
        raise RuntimeError(f"handshake failed: {status}")
    for sid in ids:
        sock.sendall(frame(json.dumps({"type": "subscribe", "data": {"session_id": sid}})))
    print(f"keep-active: subscribed {ids}", flush=True)
    sock.settimeout(None)
    last_ping = time.time()
    while True:
        if not sock.recv(65536):
            raise RuntimeError("connection closed")
        if time.time() - last_ping > 20:
            sock.sendall(frame(json.dumps({"type": "ping"})))
            last_ping = time.time()


if __name__ == "__main__":
    url = os.environ.get("DW_URL", "https://127.0.0.1:18443")
    token = os.environ.get("DW_TOKEN", "dw-test-token-12345")
    while True:  # reconnect on any drop
        try:
            run(url, token, sys.argv[1:])
        except Exception as e:  # noqa: BLE001
            print(f"keep-active: {e}; reconnecting", flush=True)
            time.sleep(2)
