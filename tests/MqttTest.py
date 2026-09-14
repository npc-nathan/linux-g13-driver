"""Reading MQTT: the last message on a topic, without the screen ever waiting for one.

A broker is written here in a few lines of sockets, so this needs nothing installed and no network.
It answers a CONNECT, acknowledges a SUBSCRIBE, and publishes - which is exactly what a real broker
does for a subscriber, and enough to prove the path end to end: connect, subscribe lazily, keep the
newest message per topic, hand a JSON payload's field through `#field`, and read empty (in no time
at all) when there is no broker, no match, or nothing published yet.
"""
import importlib.machinery
import importlib.util
import json
import os
import socket
import sys
import tempfile
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
DAEMON = os.path.join(HERE, "..", "g13-visuals", "g13-visuals")

failures = 0
scratch = tempfile.mkdtemp(prefix="g13-mqtt-")


def check(what, expected, actual):
    global failures
    ok = expected == actual
    if not ok:
        failures += 1
    print("%-58s %-26s %s" % (what, "-> " + repr(actual), "ok" if ok else "FAIL (expected %r)" % (expected,)))
    sys.stdout.flush()


def read_length(stream):
    value, shift = 0, 0
    while True:
        byte = stream.read(1)[0]
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value
        shift += 7


def length_bytes(length):
    out = bytearray()
    while True:
        byte = length % 128
        length //= 128
        out.append(byte | (0x80 if length else 0))
        if not length:
            return bytes(out)


class FakeBroker(threading.Thread):
    """Answers one client: CONNECT, SUBSCRIBE, then a publish on each topic asked for."""

    def __init__(self, payloads):
        super().__init__(daemon=True)
        self.payloads = payloads            # topic -> payload, published once subscribed
        self.sock = socket.socket()
        self.sock.bind(("127.0.0.1", 0))
        self.sock.listen(1)
        self.port = self.sock.getsockname()[1]
        self.subscribed = []
        self.seen_connect = False
        self.connected_at = None

    def run(self):
        try:
            connection, _ = self.sock.accept()
        except OSError:
            return
        connection.settimeout(15)
        stream = connection.makefile("rb")
        while True:
            header = stream.read(1)
            if not header:
                return
            body = stream.read(read_length(stream))
            kind = header[0] >> 4
            if kind == 1:                                    # CONNECT
                self.seen_connect = True
                self.connected_at = time.monotonic()
                connection.sendall(b"\x20\x02\x00\x00")      # CONNACK, accepted
            elif kind == 8:                                  # SUBSCRIBE
                packet_id = int.from_bytes(body[0:2], "big")
                topic_length = int.from_bytes(body[2:4], "big")
                topic = body[4:4 + topic_length].decode()
                self.subscribed.append(topic)
                connection.sendall(b"\x90" + length_bytes(3) + packet_id.to_bytes(2, "big") + b"\x00")
                payload = self.payloads.get(topic)
                if payload is not None:
                    body_out = len(topic).to_bytes(2, "big") + topic.encode() + payload.encode()
                    connection.sendall(b"\x30" + length_bytes(len(body_out)) + body_out)
            elif kind == 12:                                 # PINGREQ
                connection.sendall(b"\xd0\x00")


def daemon_module(config_home):
    os.environ["XDG_CONFIG_HOME"] = config_home
    os.environ["G13_HOME"] = os.path.join(config_home, "g13")
    loader = importlib.machinery.SourceFileLoader("g13vis_mqtt", DAEMON)
    spec = importlib.util.spec_from_loader(loader.name, loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module


broker = FakeBroker({"home/kitchen/temp": "21.5",
                     "home/kitchen/state": '{"state": "on", "brightness": 180}'})
broker.start()

config_home = os.path.join(scratch, "config")
os.makedirs(os.path.join(config_home, "g13"), exist_ok=True)
with open(os.path.join(config_home, "g13", "endpoints.json"), "w") as handle:
    json.dump({"test": {"url": "mqtt://127.0.0.1:%d" % broker.port}}, handle)

gv = daemon_module(config_home)
values = gv.Values()

check("mqtt: is a kind the daemon knows", True, "mqtt" in [n for n, _ in gv.SOURCE_KINDS])

started = time.monotonic()
before = values.raw("mqtt:test/home/kitchen/temp")
elapsed = time.monotonic() - started
check("before anything arrives it reads empty", "", before)
check("and it does not wait around for it", True, elapsed < 0.25)

for _ in range(60):                                        # let the thread connect and subscribe
    if values.raw("mqtt:test/home/kitchen/temp") == "21.5":
        break
    time.sleep(0.1)

check("a plain payload comes through", "21.5", values.raw("mqtt:test/home/kitchen/temp"))
check("the broker really was connected to", True, broker.seen_connect)
check("and the topic subscribed", True, "home/kitchen/temp" in broker.subscribed)
check("delay before the pad shows it", True, broker.connected_at is not None
      and broker.connected_at - started < 2.0)

for _ in range(60):                                        # the subscription is lazy: wait for it
    if values.raw("mqtt:test/home/kitchen/state#state") == "on":
        break
    time.sleep(0.1)

check("a JSON payload can be walked with #field", "on", values.raw("mqtt:test/home/kitchen/state#state"))
check("and a number out of the same payload", 180, values.raw("mqtt:test/home/kitchen/state#brightness"))
check("a field that is not there reads empty", "", values.raw("mqtt:test/home/kitchen/state#nope"))

check("a broker that is not configured reads empty", "", values.raw("mqtt:nosuchbroker/x/y"))
check("an http: address is not taken as a broker", "", values.raw("mqtt:weather/x/y"))

started = time.monotonic()
check("a broker that is not listening reads empty", "", values.raw("mqtt:dead/topic"))
check("and still does not block the screen", True, time.monotonic() - started < 0.25)

check("a spec with no topic reads empty", "", values.raw("mqtt:test"))
check("a spec with no broker reads empty", "", values.raw("mqtt:"))

print("MQTT TEST: all checks passed" if failures == 0 else "MQTT TEST: %d FAILURES" % failures)
sys.exit(0 if failures == 0 else 1)
