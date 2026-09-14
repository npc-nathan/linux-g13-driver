"""The game checker: it has to tell a game that wants the SDK from one that does not.

Getting this wrong costs somebody an evening and a 20 GB download, and it has one trap worth a
test on its own: the driver's own shim is called LogitechLcd.dll and is full of SDK names, so a
folder that has already been set up must not be reported as a game that uses the SDK.
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(HERE, "..", "g13-driver", "src", "scripts", "g13-lcd-game-check")

failures = 0
scratch = tempfile.mkdtemp(prefix="g13-game-check-")


def check(what, expected, actual):
    global failures
    ok = expected == actual
    if not ok:
        failures += 1
    print("%-58s %-26s %s" % (what, "-> " + str(actual), "ok" if ok else "FAIL (expected %s)" % expected))
    sys.stdout.flush()


def game(name, files):
    """A folder shaped like a game: some files with some strings in them."""
    folder = os.path.join(scratch, name)
    os.makedirs(folder, exist_ok=True)
    for filename, content in files.items():
        with open(os.path.join(folder, filename), "w") as handle:
            handle.write(content)
    return folder


def run(*args):
    finished = subprocess.run([sys.executable, TOOL] + list(args), capture_output=True, text=True,
                              timeout=180)
    return finished.returncode, finished.stdout + finished.stderr


sdk = game("sdk-game", {"thing.dll": "blah LogiLcdInit blah LogiLcdMonoSetText blah"})
code, output = run(sdk)
check("a game that wants the SDK says so", True, "Logitech LCD SDK" in output)
check("and it says which build to copy", True, "copy LogitechLcd.dll" in output)
check("and it exits 0, because that game can be set up", 0, code)

old = game("old-game", {"thing.dll": "lgLcdInit lgLcdUpdateBitmap lglcd.dll"})
code, output = run(old)
check("a game on the older API is named as such", True, "LCD Manager API" in output)
check("and it is not promised something that will not work", False, "copy LogitechLcd.dll" in output)
check("and it exits 1: a different shim would be needed", 1, code)

device = game("device-game", {"thing.exe": "\\\\.\\LGVirHid something"})
code, output = run(device)
check("a game that opens the driver's device is named as such", True, "driver's device" in output)
check("and it exits 1", 1, code)

# The trap: our own shim sitting in a folder that has already been set up.
already = game("already-set-up", {
    "LogitechLcd.dll": "LogiLcdInit LogiLcdMonoSetText LogiLcdUpdate lcd-probe.log",
    "game.exe": "nothing to see here",
})
code, output = run(already)
check("the driver's own shim is recognised", True, "already in place" in output)
check("and it is not counted as the game using the SDK", True, "1 of 0" not in output)
check("so that folder reports nothing found", True, "nothing found" in output)

quiet = game("quiet-game", {"game.exe": "an ordinary executable"})
code, output = run(quiet)
check("a game with nothing says nothing found", True, "nothing found" in output)
check("and says why that is not proof", True, "packed" in output)
check("and exits 0: there is nothing to set up", 0, code)

code, output = run("--help")
check("--help explains itself", True, "g13-lcd-game-check" in output)
check("and exits 0", 0, code)

code, output = run(os.path.join(scratch, "not-a-folder"))
check("a path that is not there is reported", True, "not a folder" in output)

shutil.rmtree(scratch, ignore_errors=True)
print("GAME CHECK TEST: all checks passed" if failures == 0
      else "GAME CHECK TEST: %d FAILURES" % failures)
sys.exit(0 if failures == 0 else 1)
