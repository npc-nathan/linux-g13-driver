"""Teaching a game's prefix where the LCD SDK lives, and proving the key survives.

The SDK's loader reads a ServerBinary path from a class id in the registry, so this is the only
thing standing between a game that supports the LCD and a game that silently finds nothing. Two
lessons are baked into these checks: Wine owns its registry file (writing it by hand is discarded,
so the tool must go through `wine reg add`), and the Wine used must be the one that runs the game,
because a Steam Proton pointed at a Heroic prefix -- or the reverse -- is how a prefix gets altered
behind a game's back.
"""
import glob
import os
import shutil
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(HERE, "..", "g13-driver", "src", "scripts", "g13-lcd-sdk-register")
CLSID = "{d0e790a5-01a7-49ae-ae0b-e986bdd0c21b}"

failures = 0
scratch = tempfile.mkdtemp(prefix="g13-sdk-register-")


def check(what, expected, actual):
    global failures
    ok = expected == actual
    if not ok:
        failures += 1
    print("%-56s %-22s %s" % (what, "-> " + str(actual), "ok" if ok else "FAIL (expected %s)" % expected))
    sys.stdout.flush()


def run(*args, timeout=300):
    started = time.time()
    finished = subprocess.run([sys.executable, TOOL] + list(args), capture_output=True, text=True,
                              timeout=timeout)
    return finished.returncode, finished.stdout + finished.stderr, time.time() - started


def a_wine():
    """Any Wine on this machine, to exercise the round trip; None if there is none."""
    found = shutil.which("wine")
    if found:
        return found
    for pattern in ("~/.var/app/com.heroicgameslauncher.hgl/config/heroic/tools/proton/*/files/bin/wine",
                    "~/.steam/*/steamapps/common/Proton*/files/bin/wine"):
        for binary in sorted(glob.glob(os.path.expanduser(pattern))):
            return binary
    return None


code, output, _ = run("--help")
check("--help names the class id", True, CLSID in output)
check("--help says where the DLL goes", True, "C:" + chr(92) + "g13" in output)
check("--help offers a forced Wine", True, "--wine" in output)

# The regression that made this tool unusable: reading every prefix with a fresh Wine launch.
code, output, seconds = run("--status")
check("--status is instant, not a Wine launch per prefix", True, seconds < 5.0)
check("--status does not claim Wine work", False, "registered via" in output)

code, output, _ = run("--steam-appid", "999999")
check("an app that has never run is reported", 1, code)
check("with the reason", True, "has to have been run once" in output)

wine = a_wine()
if not wine:
    print("no Wine on this machine: skipping the register/verify/remove round trip")
else:
    prefix = os.path.join(scratch, "game", "pfx")
    os.makedirs(os.path.join(prefix, "drive_c"), exist_ok=True)
    code, output, _ = run("--wine", wine, prefix)
    check("a prefix is registered", 0, code)
    check("and it says which Wine did it", True, "--wine" in output)
    check("the DLL is copied inside", True,
          os.path.exists(os.path.join(prefix, "drive_c", "g13", "LogitechLcd.dll")))

    # The point of the whole exercise: Wine can read the key back, the way a 32-bit game would.
    code, output, _ = run("--wine", wine, "--verify", prefix)
    check("the key reads back afterwards", 0, code)

    code, output, _ = run("--wine", wine, "--remove", prefix)
    check("--remove clears it", False, os.path.exists(os.path.join(prefix, "drive_c", "g13")))
    code, output, _ = run("--wine", wine, "--verify", prefix)
    check("and the key really is gone", 1, code)

    # A path that is not a prefix must be refused, not written into.
    plain = os.path.join(scratch, "not-a-prefix")
    os.makedirs(plain, exist_ok=True)
    code, output, _ = run("--wine", wine, plain)
    check("a directory that is not a prefix is refused", 1, code)
    check("with the reason", True, "not a Wine prefix" in output)

shutil.rmtree(scratch, ignore_errors=True)
print("SDK REGISTER TEST: all checks passed" if failures == 0
      else "SDK REGISTER TEST: %d FAILURES" % failures)
sys.exit(0 if failures == 0 else 1)
