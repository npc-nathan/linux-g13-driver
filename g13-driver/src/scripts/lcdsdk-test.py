#!/usr/bin/env python3
"""Checks the Logitech LCD SDK implementation: the symbols, the answers, and the bytes.

Three things, in order:
  1. the library exports exactly what a Logitech-aware program looks for, and answers "no
     screen" when our driver is not running (ctypes, as any language binding would);
  2. the compiled self-test walks the API, against a scratch directory where a regular file
     stands in for the driver's FIFO and a socket serves button events;
  3. the frames it wrote are decoded and checked pixel by pixel against the driver's layout.

    python3 g13-driver/src/scripts/lcdsdk-test.py
"""
import ctypes
import os
import socket
import subprocess
import sys
import tempfile
import threading

HERE = os.path.dirname(os.path.abspath(__file__))
LCDSDK = os.path.join(os.path.dirname(HERE), "lcdsdk")
SELFTEST = os.path.join(LCDSDK, "lcdsdk-selftest")
LIBRARY = os.path.join(LCDSDK, "liblogitechlcd.so")

WIDTH, HEIGHT = 160, 43
VISIBLE_HEIGHT = 43

failures = 0


def check(what, expected, actual):
    global failures
    ok = expected == actual
    if not ok:
        failures += 1
    print("%-56s %-22s %s" % (what, "-> " + repr(actual), "ok" if ok else
                              "FAIL (expected %r)" % (expected,)))


def serve_buttons(path, events, ready):
    """A stand-in for the driver's event socket: send button events, then hold it open."""
    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(path)
    server.listen(1)
    ready.set()
    connection, _ = server.accept()
    connection.sendall(events.encode())
    connection.settimeout(20)
    try:
        while connection.recv(1024):
            pass
    except (socket.timeout, OSError):
        pass
    finally:
        connection.close()
        server.close()


def pixel(frame, x, y):
    """One pixel of a driver frame: column-major vertical bytes, top row in bit 0."""
    return (frame[x + (y // 8) * WIDTH] >> (y % 8)) % 2 == 1


def check_library(screen_dir):
    """The library a game loads: its symbols, and its answers with and without a screen."""
    library = ctypes.CDLL(LIBRARY)
    for name in ("LogiLcdInit", "LogiLcdIsConnected", "LogiLcdIsButtonPressed", "LogiLcdUpdate",
                 "LogiLcdShutdown", "LogiLcdMonoSetBackground", "LogiLcdMonoSetText",
                 "LogiLcdColorSetBackground", "LogiLcdColorSetTitle", "LogiLcdColorSetText"):
        check("the library exports %s" % name, True, hasattr(library, name))

    name = (ctypes.c_uint16 * 5)(*[ord(character) for character in "GAME"])

    # A driver that is not there must read as "no screen", not as a crash.
    nothing = tempfile.mkdtemp(prefix="g13-nothing-")
    os.environ["G13_LCD_DIR"] = nothing
    check("init refuses a screen that is not there", False, bool(library.LogiLcdInit(name, 1)))
    check("mono reports not connected", False, bool(library.LogiLcdIsConnected(1)))
    check("a button is not pressed with no driver", False,
          bool(library.LogiLcdIsButtonPressed(1)))
    check("a frame can be pushed with no screen", True, library.LogiLcdMonoSetText(0, name))
    library.LogiLcdUpdate()   # must not crash or block with nothing on the other end
    library.LogiLcdShutdown()

    # And with a screen there, it says so.
    os.environ["G13_LCD_DIR"] = screen_dir
    check("init succeeds when the screen is there", True, bool(library.LogiLcdInit(name, 1)))
    check("mono reports connected", True, bool(library.LogiLcdIsConnected(1)))
    check("a colour panel is still not connected", False,
          bool(library.LogiLcdIsConnected(2)))
    library.LogiLcdShutdown()


def main():
    for path in (SELFTEST, LIBRARY):
        if not os.path.exists(path):
            print("build it first: make -C %s" % LCDSDK)
            return 2

    scratch = tempfile.mkdtemp(prefix="g13-lcdsdk-")
    sink = os.path.join(scratch, "g13-lcd")
    sock = os.path.join(scratch, "g13.sock")
    open(sink, "w").close()   # a regular file: the driver's FIFO would block with no reader

    check_library(scratch)

    ready = threading.Event()
    # L1 (code 25) and L3 (code 27) held down; the self-test expects exactly those two.
    thread = threading.Thread(target=serve_buttons,
                              args=(sock, "key 25 1\nkey 27 1\n", ready), daemon=True)
    thread.start()
    ready.wait(5)

    environment = dict(os.environ, G13_LCD_DIR=scratch)
    result = subprocess.run([SELFTEST], capture_output=True, text=True, env=environment,
                            timeout=60)
    print(result.stdout.strip() or "(no output from the self-test)")
    if result.returncode != 0:
        print("self-test exit code:", result.returncode)
        print(result.stderr.strip())
        return 1

    lines = [line for line in open(sink).read().splitlines() if line]

    # One frame per LogiLcdUpdate(): the bitmap and the text lines that followed it.
    frames = []
    for line in lines:
        if line.startswith("#bitmap"):
            frames.append({"bitmap": line.split()[1], "texts": []})
        elif frames:
            frames[-1]["texts"].append(line)

    check("two frames were written (one per update)", 2, len(frames))
    check("each frame is one full 960 byte image", 1920, len(frames[-1]["bitmap"]))

    frame = bytes.fromhex(frames[-1]["bitmap"])
    check("the top border is drawn", True, pixel(frame, 5, 0) and pixel(frame, 100, 0))
    check("the bottom border is drawn", True, pixel(frame, 5, HEIGHT - 1) and
          pixel(frame, 155, HEIGHT - 1))
    check("the left border is drawn", True, pixel(frame, 0, 20) and pixel(frame, 0, 40))
    check("the right border is drawn", True, pixel(frame, WIDTH - 1, 20))
    check("the block is filled", True, all(pixel(frame, x, y)
                                           for x in range(20, 30) for y in range(20, 30)))
    check("the space beside the block is clear", False, pixel(frame, 60, 20))
    check("nothing is drawn on rows the panel does not show", False,
          any(pixel(frame, x, y) for x in range(WIDTH) for y in range(VISIBLE_HEIGHT, HEIGHT)))

    texts = frames[-1]["texts"]
    check("four text lines are drawn in the last frame", 4, len(texts))
    check("line 0 is where the SDK's line 0 goes", "#text 3 2 AMMO 24", texts[0])
    check("line 1 changed between the two frames", ["#text 3 12 OBJECTIVE",
                                                    "#text 3 12 PLAIN C"],
          [frames[0]["texts"][1], texts[1]])
    check("line 2", "#text 3 22 NEXT 300m", texts[2])
    check("line 3", "#text 3 32 HP 98", texts[3])
    check("the background survived the second update", True,
          frames[0]["bitmap"] == frames[-1]["bitmap"])

    print("LCDSDK TEST: all checks passed" if failures == 0
          else "LCDSDK TEST: %d FAILURES" % failures)
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
