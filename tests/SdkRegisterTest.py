"""Registering the shim in a Wine prefix: the key Logitech's own SDK loader reads.

A game can use that SDK without its name appearing in a single string, because the loader asks
Windows for a class id and reads the DLL's path from it. This checks the key is written the way
Wine's registry file needs it, that a backup is taken, and that it can be taken back out.
"""
import os
import shutil
import subprocess
import sys
import tempfile

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
    print("%-58s %-24s %s" % (what, "-> " + str(actual), "ok" if ok else "FAIL (expected %s)" % expected))
    sys.stdout.flush()


def fake_prefix(name, with_registry=True):
    prefix = os.path.join(scratch, name)
    os.makedirs(os.path.join(prefix, "drive_c"), exist_ok=True)
    if with_registry:
        with open(os.path.join(prefix, "system.reg"), "w") as handle:
            handle.write("WINE REGISTRY Version 2\n\n")
            handle.write("[Software\\\\Classes\\\\CLSID\\\\%s] 1700000000\n"
                         "@=\"C:\\\\old\\\\copy.dll\"\n\n" % CLSID)
    return prefix


def run(*args):
    finished = subprocess.run([sys.executable, TOOL] + list(args), capture_output=True, text=True,
                              timeout=120)
    return finished.returncode, finished.stdout + finished.stderr


prefix = fake_prefix("steam-prefix")
code, output = run(prefix)
check("a prefix is registered", True, "registered" in output)
registry = open(os.path.join(prefix, "system.reg")).read()
check("the class id key is written", True, CLSID in registry)
check("and points inside the prefix", True,
      ("C:" + chr(92) * 2 + "g13" + chr(92) * 2 + "LogitechLcd.dll") in registry)
check("one ServerBinary key per class-id view, and no more", 3, registry.count("ServerBinary"))
check("the DLL is copied into the prefix", True,
      os.path.exists(os.path.join(prefix, "drive_c", "g13", "LogitechLcd.dll")))
check("a backup of the registry is kept", True,
      os.path.exists(os.path.join(prefix, "system.reg.before-g13")))
check("the rest of the registry is left alone", True, "WINE REGISTRY Version 2" in registry)

code, output = run("--remove", prefix)
registry = open(os.path.join(prefix, "system.reg")).read()
check("--remove takes the keys back out", False, "ServerBinary" in registry)
check("and leaves the file otherwise intact", True, "WINE REGISTRY Version 2" in registry)

bare = fake_prefix("no-registry", with_registry=False)
code, output = run(bare)
check("a prefix with no registry says so", True, "no system.reg" in output)

code, output = run("--steam-appid", "999999")
check("an app that has never run is reported", 1, code)
check("with the reason", True, "has to have been run once" in output)

code, output = run("--help")
check("--help explains it", True, "ServerBinary" in output)
check("and names the class id", True, CLSID in output)

shutil.rmtree(scratch, ignore_errors=True)
print("SDK REGISTER TEST: all checks passed" if failures == 0
      else "SDK REGISTER TEST: %d FAILURES" % failures)
sys.exit(0 if failures == 0 else 1)
