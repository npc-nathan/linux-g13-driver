#!/usr/bin/env python3
"""Writes the JSON a game would write, so a HUD applet can be designed without the game.

Point an applet's sources at the file this writes - `applets/cyberpunk-hud.json` already
does - and run this while you build the screen. It drives the same fields the real game
would: ammo counting down as it fires, a magazine that empties and reloads, health, an
objective that changes, a corner coming up, and a distance that closes.

    python3 g13-visuals/tools/simulate-game-hud.py
    python3 g13-visuals/tools/simulate-game-hud.py --path /tmp/hud.json --hz 5

The real thing writes the same shape, one field per thing the screen wants:

    {"ammo": 24, "mag": 7, "health": 86, "objective": "Deliver the package to Vex",
     "turn": -35, "distance": 240}
"""
import argparse
import json
import math
import os
import random
import time
from pathlib import Path

OBJECTIVES = [
    "Deliver the package to Vex",
    "Jack into the subnet relay",
    "Lose the pursuit, 4 blocks clear",
    "Meet Rook on the rooftop",
    "Recover the stolen deck",
]

#: A run of corners: negative is left, positive is right, matching a game's own sign.
CORNERS = [-90, -35, 0, 0, 45, 90, 0, -20, 115, 0, 180]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--path", default="~/.local/share/cyberpunk-gta/hud.json",
                        help="the file to write (the applet's json: source reads this)")
    parser.add_argument("--hz", type=float, default=5.0, help="updates per second")
    arguments = parser.parse_args()

    path = Path(os.path.expanduser(arguments.path))
    path.parent.mkdir(parents=True, exist_ok=True)
    print("writing %s at %.1f Hz - Ctrl-C to stop" % (path, arguments.hz))

    total = 120
    ammo = 24
    health = 86
    corner = 0
    distance = 640
    objective = random.choice(OBJECTIVES)
    started = time.time()
    sleep_for = 1.0 / max(0.2, arguments.hz)

    while True:
        elapsed = time.time() - started

        # Firing: a magazine empties over a few seconds, then a reload.
        if int(elapsed * 2) % 24 == 0 and ammo > 0 and int(elapsed * 2) % 48 == 0:
            ammo = max(0, ammo - 1)
        if ammo == 0:
            ammo, total = 24, 120
            time.sleep(0.6)

        # A corner every few seconds, with the distance closing onto it.
        step = int(elapsed / 4) % len(CORNERS)
        corner = CORNERS[step]
        distance = max(20, 640 - int((elapsed * 40) % 640))
        if distance <= 24:
            objective = random.choice(OBJECTIVES)
        health = max(12, 86 - int(30 * abs(math.sin(elapsed / 7))))

        state = {
            "ammo": ammo,
            "mag": 7,
            "total": total,
            "health": health,
            "objective": objective,
            "turn": corner,
            "distance": distance,
        }

        # Write to a temporary file and rename it over the real one: a reader must never
        # catch the file half-written, and rename is atomic within a directory.
        temporary = path.with_name(path.name + ".tmp")
        temporary.write_text(json.dumps(state, indent=2) + "\n")
        temporary.replace(path)

        time.sleep(sleep_for)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nstopped")
