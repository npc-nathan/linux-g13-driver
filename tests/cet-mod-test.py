#!/usr/bin/env python3
"""Tests the Cyberpunk 2077 mod without the game.

Three things:
  1. the mod is valid Lua (luac -p), so it cannot fail silently in game with a syntax error;
  2. its logic, driven by stubs of the game API (tests/cet-mod-test.lua), produces the fields
     it should - including when a game build refuses calls the mod is not sure about;
  3. the file it writes is real JSON, and the applet the Linux side ships reads it and lays out
     without putting text on top of ink.

Needs lua5.4 (apt install lua5.4). Skips with a warning if it is missing.

    python3 tests/cet-mod-test.py
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD = os.path.join(REPO, "g13-cet-mod", "init.lua")
STUB = os.path.join(REPO, "tests", "cet-mod-test.lua")
APPLET = os.path.join(REPO, "g13-visuals", "applets", "cp2077-hud.json")

failures = 0


def check(what, expected, actual):
    global failures
    ok = expected == actual
    if not ok:
        failures += 1
    print("%-56s %-22s %s" % (what, "-> " + repr(actual), "ok" if ok else
                              "FAIL (expected %r)" % (expected,)))


def main():
    lua = shutil.which("lua5.4") or shutil.which("lua")
    luac = shutil.which("luac5.4") or shutil.which("luac")
    if not lua or not luac:
        print("lua5.4 is not installed (apt install lua5.4): skipping the mod tests")
        return 0

    # 1. Syntax: a broken mod fails inside the game with nothing to go on.
    result = subprocess.run([luac, "-p", MOD], capture_output=True, text=True)
    check("the mod is valid Lua", 0, result.returncode)
    if result.returncode != 0:
        print(result.stderr.strip())

    # 2. Logic, with the game stubbed out. The mod writes into the current directory, which is
    #    why this runs in a scratch one.
    scratch = tempfile.mkdtemp(prefix="g13-cet-")
    environment = dict(os.environ, G13_TEST_MOD=MOD)
    result = subprocess.run([lua, STUB], capture_output=True, text=True, cwd=scratch,
                            env=environment, timeout=60)
    print(result.stdout.strip())
    if result.returncode != 0:
        print(result.stderr.strip())
    check("the mod's own checks pass", 0, result.returncode)

    # 3. The file it wrote has to be JSON the Linux side can read, with the applet's fields.
    state_path = os.path.join(scratch, "hud.json")
    check("it wrote hud.json", True, os.path.exists(state_path))
    if os.path.exists(state_path):
        try:
            state = json.load(open(state_path))
        except ValueError as error:
            state = None
            print("hud.json is not valid JSON:", error)
        check("hud.json parses as JSON", True, state is not None)
        if state:
            for field in ("health", "level", "objective", "heading", "updated"):
                check("hud.json carries %s" % field, True, field in state)

        # The applet, pointed at that file, must lay out cleanly - and that is the rule the
        # daemon enforces too: one colour of ink, so text must sit on blank pixels.
        if os.path.exists(APPLET):
            sys.path.insert(0, os.path.join(REPO, "g13-driver", "src", "scripts"))
            sys.path.insert(0, os.path.join(REPO, "tests"))
            import importlib.machinery
            import importlib.util

            loader = importlib.machinery.SourceFileLoader(
                "g13visuals", os.path.join(REPO, "g13-visuals", "g13-visuals"))
            spec = importlib.util.spec_from_loader("g13visuals", loader)
            gv = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(gv)

            definition = json.load(open(APPLET))
            definition = json.loads(json.dumps(definition).replace(
                "{GAME}", scratch.replace(os.path.join(scratch, "hud.json"), scratch)))
            # Point every source at the file the mod just wrote.
            for name, source in list((definition.get("sources") or {}).items()):
                if "hud.json" in source:
                    definition["sources"][name] = "json:%s#%s" % (
                        state_path, source.rsplit("#", 1)[-1])

            values = gv.Values()
            screen = gv.Screen()
            visual = gv.LayoutVisual(definition, values)
            visual.render(screen, {})

            text = [placement[2] for placement in screen.texts]
            joined = " | ".join(text)
            # The objective slides along a 25-character window, and which part is showing
            # depends on the clock, so look for a word that this 26-character phrase always
            # has inside any such window. The Lua test above already checks the whole string.
            check("the applet draws the mod's data", True,
                  ("Deliver" in joined or "Vex" in joined) and ("AMMO" in joined))
            check("and the level from the mod's file", True, "LVL 32" in joined)
            check("and the line the mod made up for it", True, "PIN 5m  Watson" in joined)

            # The text-on-ink rule itself lives in VisualsTest.py, which applies it to every
            # applet in the repo - including this one - when the suite runs. One owner for the
            # rule, rather than a second opinion about it here.

    print("CET MOD TEST: all checks passed" if failures == 0
          else "CET MOD TEST: %d FAILURES" % failures)
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
