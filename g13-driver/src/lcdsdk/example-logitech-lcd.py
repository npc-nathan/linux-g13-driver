#!/usr/bin/env python3
"""An example Logitech LCD SDK program, in Python, using ctypes.

This is what a game or an applet does: initialise, set some text, draw a background, call
update, and ask about the four buttons. Run it and the G13 shows it; stop it and the screen
goes back to whatever the visuals daemon was showing.

    python3 g13-driver/src/lcdsdk/example-logitech-lcd.py [--seconds 20]

Requires the library: make -C g13-driver/src/lcdsdk install
"""
import argparse
import ctypes
import os
import sys
import time

WIDTH, HEIGHT = 160, 43
MONO = 1
BUTTONS = ("button 0 (L1)", "button 1 (L2)", "button 2 (L3)", "button 3 (L4)")


def load_library():
    for candidate in (os.path.expanduser("~/.local/lib/liblogitechlcd.so"),
                      "liblogitechlcd.so"):
        try:
            return ctypes.CDLL(candidate)
        except OSError:
            continue
    raise SystemExit("liblogitechlcd.so not found: run 'make -C g13-driver/src/lcdsdk install'")


def wide(text):
    return (ctypes.c_uint16 * (len(text) + 1))(*[ord(character) for character in text])


def frame_with_border():
    """A 160x43 field, 8 bits per pixel, as LogiLcdMonoSetBackground wants it."""
    pixels = (ctypes.c_uint8 * (WIDTH * HEIGHT))()
    for x in range(WIDTH):
        pixels[x] = 255
        pixels[(HEIGHT - 1) * WIDTH + x] = 255
    for y in range(HEIGHT):
        pixels[y * WIDTH] = 255
        pixels[y * WIDTH + WIDTH - 1] = 255
    return pixels


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seconds", type=float, default=20.0)
    arguments = parser.parse_args()

    lcd = load_library()

    if not lcd.LogiLcdInit(wide("G13 example"), MONO):
        print("no screen: is the driver running?")
        return 1
    print("connected:", bool(lcd.LogiLcdIsConnected(MONO)))

    lcd.LogiLcdMonoSetBackground(frame_with_border())
    lcd.LogiLcdMonoSetText(0, wide("LOGITECH LCD SDK"))
    lcd.LogiLcdMonoSetText(1, wide("drawn over the SDK"))
    lcd.LogiLcdMonoSetText(2, wide("buttons beside it:"))
    lcd.LogiLcdMonoSetText(3, wide("press L1-L4"))

    end = time.time() + arguments.seconds
    seen = None
    while time.time() < end:
        lcd.LogiLcdUpdate()
        for index, label in enumerate(BUTTONS):
            if lcd.LogiLcdIsButtonPressed(1 << index):
                if seen != index:
                    seen = index
                    print("%s is pressed" % label, flush=True)
            elif seen == index:
                seen = None
        time.sleep(0.1)          # a game would do this once per frame

    lcd.LogiLcdShutdown()
    print("screen handed back")
    return 0


if __name__ == "__main__":
    sys.exit(main())
