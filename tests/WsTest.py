"""Reading a web socket: the last message that arrived, with nothing to install.

A web socket server is written here in sockets, handshake and frames included, so this proves the
whole path with no network and no dependency: the client's upgrade handshake and its Accept check,
the payload an endpoint tells it to send on connect, text frames in all three length forms, a JSON
message walked with `#field`, and empty (instantly) when there is no such endpoint or the far end is
not a web socket at all.
"""
import base64
import hashlib
import importlib.machinery
import importlib.util
import json
import os
import socket
import sys
import tempfile
import threading
import time

GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
HERE = os.path.dirname(os.path.abspath(__file__))
DAEMON = os.path.join(HERE, "..", "g13-visuals", "g13-visuals")

failures = 0
scratch = tempfile.mkdtemp(prefix="g13-ws-")


def check(what, expected, actual):
    global failures
    ok = expected == actual
    if not ok:
        failures += 1
    print("%-58s %-24s %s" % (what, "-> " + repr(actual), "ok" if ok else "FAIL (expected %r)" % (expected,)))
    sys.stdout.flush()


def read_frame(conn, buffered=b""):
    """One frame from the client (masked), as (opcode, text)."""
    buffer = bytearray(buffered)
    while len(buffer) < 2:
        chunk = conn.recv(4096)
        if not chunk:
            return None, None
        buffer += chunk
    opcode = buffer[0] & 0x0F
    masked = buffer[1] & 0x80
    length, offset = buffer[1] & 0x7F, 2
    if length == 126:
        while len(buffer) < 4:
            buffer += conn.recv(4096)
        length, offset = int.from_bytes(buffer[2:4], "big"), 4
    elif length == 127:
        while len(buffer) < 10:
            buffer += conn.recv(4096)
        length, offset = int.from_bytes(buffer[2:10], "big"), 10
    mask = b""
    if masked:
        while len(buffer) < offset + 4:
            buffer += conn.recv(4096)
        mask, offset = bytes(buffer[offset:offset + 4]), offset + 4
    while len(buffer) < offset + length:
        chunk = conn.recv(4096)
        if not chunk:
            return None, None
        buffer += chunk
    payload = bytes(buffer[offset:offset + length])
    if masked:
        payload = bytes(byte ^ mask[i % 4] for i, byte in enumerate(payload))
    return opcode, payload.decode("utf-8", "replace")


def send_text(conn, text):
    body = text.encode()
    length = len(body)
    if length < 126:
        header = bytes([0x81, length])
    elif length < 65536:
        header = bytes([0x81, 126]) + length.to_bytes(2, "big")
    else:
        header = bytes([0x81, 127]) + length.to_bytes(8, "big")
    conn.sendall(header + body)


class FakeSocket(threading.Thread):
    """Upgrades one connection, notes what the client sent on connect, then talks."""

    def __init__(self, messages, refuse=False):
        super().__init__(daemon=True)
        self.messages = messages
        self.refuse = refuse
        self.sock = socket.socket()
        self.sock.bind(("127.0.0.1", 0))
        self.sock.listen(1)
        self.port = self.sock.getsockname()[1]
        self.hello = None
        self.upgraded = False

    def run(self):
        connection, _ = self.sock.accept()
        connection.settimeout(20)
        data = b""
        while b"\r\n\r\n" not in data:
            chunk = connection.recv(4096)
            if not chunk:
                return
            data += chunk
        head = data.split(b"\r\n\r\n", 1)[0].decode("utf-8", "replace")
        if self.refuse:
            connection.sendall(b"HTTP/1.1 403 Forbidden\r\n\r\n")
            return
        key = ""
        for line in head.split("\r\n"):
            if line.lower().startswith("sec-websocket-key"):
                key = line.split(":", 1)[1].strip()
        accept = base64.b64encode(hashlib.sha1((key + GUID).encode()).digest()).decode()
        connection.sendall(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n"
                            "Connection: Upgrade\r\nSec-WebSocket-Accept: %s\r\n\r\n" % accept).encode())
        self.upgraded = True
        opcode, self.hello = read_frame(connection)          # whatever the endpoint sends on connect
        for message in self.messages:
            send_text(connection, message)
            time.sleep(0.2)
        while True:
            try:
                opcode, _text = read_frame(connection)
            except OSError:
                return
            if opcode is None:
                return


def daemon_module(config_home):
    os.environ["XDG_CONFIG_HOME"] = config_home
    os.environ["G13_HOME"] = os.path.join(config_home, "g13")
    loader = importlib.machinery.SourceFileLoader("g13vis_ws", DAEMON)
    spec = importlib.util.spec_from_loader(loader.name, loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module


hi = '{"op": 1, "d": {"eventSubscriptions": 33}}'
server = FakeSocket(['"a plain string"', json.dumps({"d": {"settings": {"fps": 60}, "state": "live"}})])
server.start()
refuser = FakeSocket([], refuse=True)
refuser.start()

config_home = os.path.join(scratch, "config")
os.makedirs(os.path.join(config_home, "g13"), exist_ok=True)
with open(os.path.join(config_home, "g13", "endpoints.json"), "w") as handle:
    json.dump({"feed": {"url": "ws://127.0.0.1:%d/live" % server.port, "subscribe": hi},
               "refused": {"url": "ws://127.0.0.1:%d" % refuser.port},
               "notws": {"url": "http://127.0.0.1:%d" % server.port}}, handle)

gv = daemon_module(config_home)
values = gv.Values()

check("ws: is a kind the daemon knows", True, "ws" in [n for n, _ in gv.SOURCE_KINDS])

started = time.monotonic()
check("before anything arrives it reads empty", "", values.raw("ws:feed#d.state"))
check("and it does not wait for it", True, time.monotonic() - started < 0.25)

for _ in range(60):
    if values.raw("ws:feed#d.state") == "live":
        break
    time.sleep(0.1)

check("the socket was upgraded", True, server.upgraded)
check("the endpoint's subscribe payload was sent on connect", hi, server.hello)
check("a JSON message is walked with #field", "live", values.raw("ws:feed#d.state"))
check("including a nested one", 60, values.raw("ws:feed#d.settings.fps"))
check("a field that is not there reads empty", "", values.raw("ws:feed#d.nope"))
check("a message from an endpoint with no subscribe still lands", "", values.raw("ws:refused#x"))

started = time.monotonic()
check("a socket that refuses the upgrade reads empty", "", values.raw("ws:refused#x"))
check("and does not hold the screen up", True, time.monotonic() - started < 0.25)
check("an http: address is not taken as a socket", "", values.raw("ws:notws#x"))
check("a spec with no endpoint reads empty", "", values.raw("ws:"))
check("an endpoint that is not configured reads empty", "", values.raw("ws:nosuch#x"))

started = time.monotonic()
check("a socket that is not listening reads empty", "", values.raw("ws:dead#x"))
check("and still does not block", True, time.monotonic() - started < 0.25)

print("WS TEST: all checks passed" if failures == 0 else "WS TEST: %d FAILURES" % failures)
sys.exit(0 if failures == 0 else 1)
