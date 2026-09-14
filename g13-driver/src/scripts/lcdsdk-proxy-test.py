#!/usr/bin/env python3
"""Checks the Windows path without Windows: the bridge, and the transport the DLL uses.

The PE LogitechLcd.dll needs Wine to run, but the code inside it does not: the same source
builds for Linux with -DG13_TCP_TRANSPORT=1 (make lcdsdk-selftest-tcp) and talks to
g13-lcd-bridge exactly as the Windows build does. So this starts the bridge against a scratch
runtime directory, runs that self-test through it, and checks what came out the other side.

Three things are being proven:
  1. a client of the bridge gets frames to the driver's screen, byte for byte;
  2. button events travel back up to that client, which is how the SDK's button query works
     for a game under Proton;
  3. the state file the visuals daemon watches appears while a client is connected and goes
     away when it leaves - that is what makes an SDK client take precedence over the menu.

    python3 g13-driver/src/scripts/lcdsdk-proxy-test.py
"""
import ctypes
import os
import socket
import subprocess
import sys
import tempfile
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
LCDSDK = os.path.join(os.path.dirname(HERE), "lcdsdk")
TCP_SELFTEST = os.path.join(LCDSDK, "lcdsdk-selftest-tcp")
TCP_LIBRARY = os.path.join(LCDSDK, "liblogitechlcd-tcp.so")
BRIDGE = os.path.join(LCDSDK, "g13-lcd-bridge")

WIDTH, HEIGHT = 160, 43
failures = 0


def check(what, expected, actual):
    global failures
    ok = expected == actual
    if not ok:
        failures += 1
    print("%-56s %-22s %s" % (what, "-> " + repr(actual), "ok" if ok else
                              "FAIL (expected %r)" % (expected,)))


def serve_driver_events(path, events, ready):
    """The driver's event socket, as far as the bridge is concerned.

    The events repeat: a client that connects a moment from now still has to be able to ask
    which buttons are down, and the bridge only passes on what it hears.
    """
    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(path)
    server.listen(1)
    ready.set()
    connection, _ = server.accept()
    connection.settimeout(0.3)
    deadline = time.time() + 14
    while time.time() < deadline:
        try:
            connection.sendall(events.encode())
        except OSError:
            break
        try:
            connection.recv(1024)
        except (socket.timeout, OSError):
            pass
        time.sleep(0.25)
    try:
        connection.close()
        server.close()
    except OSError:
        pass


def free_port():
    probe = socket.socket()
    probe.bind(("127.0.0.1", 0))
    port = probe.getsockname()[1]
    probe.close()
    return port


def start_bridge(scratch, port):
    environment = dict(os.environ, XDG_RUNTIME_DIR=scratch)
    bridge = subprocess.Popen([sys.executable, BRIDGE, "--port", str(port), "--verbose"],
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                              env=environment)
    deadline = time.time() + 8
    while time.time() < deadline:
        probe = socket.socket()
        probe.settimeout(0.3)
        try:
            probe.connect(("127.0.0.1", port))
            probe.close()
            return bridge
        except OSError:
            time.sleep(0.2)
        finally:
            probe.close()
    bridge.kill()
    return None


def pixel(frame, x, y):
    return (frame[x + (y // 8) * WIDTH] >> (y % 8)) % 2 == 1


def frames_from(sink):
    frames = []
    for line in open(sink).read().splitlines():
        if not line:
            continue
        if line.startswith("#bitmap"):
            frames.append({"bitmap": line.split()[1], "texts": []})
        elif frames:
            frames[-1]["texts"].append(line)
    return frames


def watch_client_file(path, seen):
    """Sample the state file while the client is connected."""
    deadline = time.time() + 15
    while time.time() < deadline:
        if os.path.exists(path):
            seen.append(True)
            return
        time.sleep(0.05)


def check_windows_proxy():
    """The Windows artifacts: a 32-bit and a 64-bit PE DLL exporting exactly the SDK's functions.

    The 32-bit one carries the name a game loads, because that is what the LCD-era games need: a
    2009 title like Dragon Age: Origins is 32-bit and cannot load a 64-bit library at all. The
    64-bit one is built beside it, to be renamed on the way into a modern game's folder.
    """
    exports = ("LogiLcdInit", "LogiLcdIsConnected", "LogiLcdIsButtonPressed", "LogiLcdUpdate",
               "LogiLcdShutdown", "LogiLcdMonoSetBackground", "LogiLcdMonoSetText",
               "LogiLcdColorSetBackground", "LogiLcdColorSetTitle", "LogiLcdColorSetText")

    def dump_of(program, path):
        return subprocess.run([program, "-p", path], capture_output=True, text=True).stdout

    thirty_two = os.path.join(LCDSDK, "LogitechLcd.dll")
    if not os.path.exists(thirty_two):
        print("%-56s %-22s %s" % ("the Windows proxy exists", "-> not built",
                                  "warning (make -C lcdsdk windows)"))
        return

    dump = dump_of("i686-w64-mingw32-objdump", thirty_two)
    check("the DLL a game loads is 32-bit", True, "pei-i386" in dump)
    for name in exports:
        check("it exports %s" % name, True, name in dump)
    check("it needs only the usual Windows libraries", True,
          all(library in dump for library in ("KERNEL32.dll", "WS2_32.dll", "msvcrt.dll")))
    check("it is the size a small shim should be", True,
          os.path.getsize(thirty_two) < 2 * 1024 * 1024)
    # The probe is what makes a game that says nothing diagnosable, so its two strings have to be
    # in the artifact rather than merely in the source.
    notes = subprocess.run(["strings", "-a", thirty_two], capture_output=True, text=True).stdout
    check("it carries the probe that records what a game asked", True,
          "lcd-probe.log" in notes and "LogiLcdInit(name=" in notes)

    sixty_four = os.path.join(LCDSDK, "LogitechLcd.x64.dll")
    if os.path.exists(sixty_four):
        dump64 = dump_of("x86_64-w64-mingw32-objdump", sixty_four)
        check("the second build is 64-bit", True, "pei-x86-64" in dump64)
        check("it exports the same SDK", True, all(name in dump64 for name in exports))
    else:
        check("the 64-bit build was made alongside it", "a file", "missing")


def main():
    for path in (TCP_SELFTEST, BRIDGE):
        if not os.path.exists(path):
            print("build it first: make -C %s" % LCDSDK)
            return 2

    scratch = tempfile.mkdtemp(prefix="g13-proxy-")
    sink = os.path.join(scratch, "g13-lcd")
    driver_socket = os.path.join(scratch, "g13.sock")
    client_file = os.path.join(scratch, "g13-sdk-client")
    open(sink, "w").close()

    ready = threading.Event()
    threading.Thread(target=serve_driver_events,
                     args=(driver_socket, "key 25 1\nkey 27 1\n", ready), daemon=True).start()
    ready.wait(5)

    port = free_port()
    bridge = start_bridge(scratch, port)
    if bridge is None:
        print("the bridge did not start")
        return 1
    check("the bridge accepts connections", True, True)

    seen = []
    # The state file appears and disappears around a client's lifetime, which is far too
    # quick to sample. Hold a connection of our own open instead, later on.
    environment = dict(os.environ, G13_LCD_TCP="127.0.0.1:%d" % port)
    # Let the bridge hear a round of button events first: it tells a connecting client which
    # buttons are already held, and the self-test asks within milliseconds of connecting.
    time.sleep(1.2)
    result = subprocess.run([TCP_SELFTEST], capture_output=True, text=True, env=environment,
                            timeout=60)
    print(result.stdout.strip() or "(no output from the self-test)")
    check("the TCP self-test passes", 0, result.returncode)

    time.sleep(0.8)
    frames = frames_from(sink)
    check("the frames reached the driver's screen", 2, len(frames))
    check("the frame is one full 960 byte image", 1920, len(frames[-1]["bitmap"]))
    frame = bytes.fromhex(frames[-1]["bitmap"])
    check("the top border arrived", True, pixel(frame, 5, 0) and pixel(frame, 100, 0))
    check("the block arrived", True, all(pixel(frame, x, y)
                                         for x in range(20, 30) for y in range(20, 30)))
    check("the text arrived", "#text 3 2 AMMO 24", frames[-1]["texts"][0])

    holder = socket.create_connection(("127.0.0.1", port), timeout=2)
    appeared = False
    deadline = time.time() + 3
    while time.time() < deadline and not appeared:
        appeared = os.path.exists(client_file)
        time.sleep(0.05)
    check("an SDK client is announced to the daemon", True, appeared)
    holder.close()
    withdrawn = False
    deadline = time.time() + 3
    while time.time() < deadline and not withdrawn:
        withdrawn = not os.path.exists(client_file)
        time.sleep(0.05)
    check("the announcement is withdrawn when the client goes", True, withdrawn)

    bridge.terminate()
    bridge.wait(timeout=5)

    # And with no driver at all: the bridge answers, but the screen must not claim to exist.
    empty = tempfile.mkdtemp(prefix="g13-empty-")
    port = free_port()
    bridge = start_bridge(empty, port)
    if bridge is None:
        print("the bridge did not start for the second run")
        return 1
    os.environ["G13_LCD_TCP"] = "127.0.0.1:%d" % port
    library = ctypes.CDLL(TCP_LIBRARY)
    name = (ctypes.c_uint16 * 5)(*[ord(character) for character in "GAME"])
    check("with no driver, init still succeeds (the bridge answers)", True,
          bool(library.LogiLcdInit(name, 1)))
    time.sleep(0.6)          # let the bridge say what it knows
    check("but it does not pretend a screen is there", False,
          bool(library.LogiLcdIsConnected(1)))
    library.LogiLcdMonoSetText(0, name)
    library.LogiLcdUpdate()  # must not block or crash with nothing behind it
    check("a frame sent with no driver goes nowhere and does not hang", True, True)
    library.LogiLcdShutdown()
    bridge.terminate()
    bridge.wait(timeout=5)

    check_windows_proxy()

    print("LCDSDK PROXY TEST: all checks passed" if failures == 0
          else "LCDSDK PROXY TEST: %d FAILURES" % failures)
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
