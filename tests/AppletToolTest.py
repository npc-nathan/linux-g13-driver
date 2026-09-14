#!/usr/bin/env python3
"""Tests `g13-applet check`, without the pad.

It runs the tool the way a person would - as a command, on files - so what is tested is the
command rather than its insides: a good applet passes, a broken one fails with the reasons and a
non-zero exit code, and nothing in between crashes.

    python3 tests/AppletToolTest.py
"""

import json
import os
import shutil
import subprocess
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOL = os.path.join(REPO, "g13-driver", "src", "scripts", "g13-applet")
APPLETS = os.path.join(REPO, "g13-visuals", "applets")

failures = 0


def check(what, expected, actual):
    global failures
    ok = expected == actual
    if not ok:
        failures += 1
    print("%-58s %-24s %s" % (what, "-> " + str(actual), "ok" if ok else
                              "FAIL (expected %s)" % (expected,)))


def run(*args, config=None):
    environment = dict(os.environ)
    if config:
        environment["XDG_CONFIG_HOME"] = config
    result = subprocess.run([sys.executable, TOOL, *args], capture_output=True, text=True,
                            timeout=120, env=environment)
    return result.returncode, result.stdout + result.stderr


def written(directory, name, definition):
    path = os.path.join(directory, name)
    with open(path, "w") as handle:
        if isinstance(definition, str):
            handle.write(definition)
        else:
            json.dump(definition, handle)
    return path


def main():
    scratch = tempfile.mkdtemp(prefix="g13-applet-test-")

    # --- the applets that ship have to pass, or the tool is crying wolf -------------------
    shipped = sorted(path for path in os.listdir(APPLETS) if path.endswith(".json"))
    check("there are shipped applets to check", True, len(shipped) >= 1)
    for name in shipped:
        code, output = run("check", os.path.join(APPLETS, name))
        check("shipped applet %s is clean" % name, 0, code)

    good = written(scratch, "good.json", {
        "name": "good", "title": "GOOD", "interval": 0.5, "follow": {"seconds": 10},
        "sources": {"ammo": "json:/tmp/g13-not-there.json#ammo"},
        "widgets": [
            {"type": "bar", "x": 3, "y": 12, "w": 40, "h": 6, "source": "ammo", "max": 30},
            {"type": "text", "x": 50, "y": 12, "format": "AMMO {ammo:>3}"},
            {"type": "text", "x": 3, "y": 22, "format": "CPU {cpu:.0f}%  {day}"},
            {"type": "segments", "x": 3, "y": 32, "w": 60, "h": 5, "count": 10,
             "source": "memory", "max": 100},
        ],
    })
    code, output = run("check", good)
    check("a good applet passes", 0, code)
    check("and it says so", True, "ok" in output)

    # --- each kind of mistake, one at a time, reported rather than crashed on -------------
    broken = written(scratch, "broken.json", {
        "name": "broken", "interval": "soon",
        "widgets": [
            {"type": "box", "x": 40, "y": 12, "w": 60, "h": 10},
            {"type": "text", "x": 50, "y": 14, "format": "over the box"},
            {"type": "text", "x": 3, "y": 38, "format": "too low"},
            {"type": "sparkline", "x": 3, "y": 24},
            {"type": "text", "x": "left", "y": 26, "align": "middle", "format": "{ammoo}"},
            {"type": "text", "x": 3, "y": 30, "format": "load {cpu:>Z}"},
        ],
    })
    code, output = run("check", broken)
    check("a broken applet fails", 1, code)
    for expected in ("nothing draws a 'sparkline' widget",
                     "'interval' has to be a number",
                     "'ammoo' is not an alias",
                     "'align' is 'middle'",
                     "'x' has to be a number",
                     "cannot be read with any kind of value",
                     "sits on",
                     "text off the visible screen"):
        check("it reports %r" % expected[:34], True, expected in output)
    check("and it did not print a traceback", False, "Traceback" in output)

    # A label on a filled box is hundreds of colliding pixels; the report has to be one line.
    check("a pixel storm is summarised", True, output.count("sits on") == 2)

    # --- shapes that are not even the right shape -----------------------------------------
    code, output = run("check", written(scratch, "notjson.json", "{not json"))
    check("a file that is not JSON fails", 1, code)
    check("and says so", True, "not valid JSON" in output)

    code, output = run("check", written(scratch, "wrong.json", {"widgets": "lots"}))
    check("widgets that are not a list fail", 1, code)
    check("and says so", True, "'widgets' has to be a list" in output)
    check("without a traceback", False, "Traceback" in output)

    code, output = run("check", written(scratch, "list.json", [1, 2, 3]))
    check("a JSON array is not an applet", 1, code)
    check("and says so", True, "has to be a JSON object" in output)

    code, output = run("check", os.path.join(scratch, "not-there.json"))
    check("a missing file fails", 1, code)
    check("and says so", True, "cannot read" in output)

    # --- the extras ------------------------------------------------------------------------
    code, output = run("check", good, "--values")
    check("--values shows what the applet reads", True, "cpu" in output and "reads now" in output)

    code, output = run("check", broken, "--screen")
    check("--screen draws: ink, text, and collisions", True,
          "#" in output and "." in output and "!" in output)

    code, output = run("check", broken, "--json")
    check("--json is valid JSON", True, isinstance(json.loads(output.strip()), dict))
    check("--json keys by file name", True, "broken.json" in json.loads(output.strip()))

    # --- the directory path -----------------------------------------------------------------
    config = os.path.join(scratch, "config")
    os.makedirs(os.path.join(config, "g13", "applets"))
    shutil.copy(good, os.path.join(config, "g13", "applets", "one.json"))
    code, output = run("check", "--all", config=config)
    check("--all checks the applets directory", 0, code)

    empty = os.path.join(scratch, "empty")
    os.makedirs(os.path.join(empty, "g13", "applets"))
    code, output = run("check", "--all", config=empty)
    check("--all with nothing there says so", 2, code)
    check("and tells you where it looked", True, "applets" in output)

    # --json is what a build script reads, so it has to carry the same problems the human output
    # does. It used to report the structural ones only, which told a script that an applet drawn
    # on top of itself was clean - the layout half is the half the pad actually shows.
    on_a_box = os.path.join(scratch, "on-a-box.json")
    with open(on_a_box, "w") as handle:
        json.dump({"name": "on-a-box", "title": "BOX", "widgets": [
            {"type": "box", "x": 20, "y": 10, "w": 120, "h": 12},
            {"type": "text", "x": 30, "y": 12, "format": "over the box"}]}, handle)
    code, output = run("check", on_a_box, "--json")
    check("--json: a broken applet is not reported clean", 1, code)
    reported = json.loads(output[output.index("{"):]) if "{" in output else {}
    check("--json: and the layout problem is in it", True,
          any("sits on" in problem for problems in reported.values() for problem in problems))
    code, output = run("check", on_a_box, "--screen")
    check("and the same applet fails without --json too", 1, code)

    shutil.rmtree(scratch, ignore_errors=True)
    print("APPLET TOOL TEST: all checks passed" if failures == 0
          else "APPLET TOOL TEST: %d FAILURES" % failures)
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
