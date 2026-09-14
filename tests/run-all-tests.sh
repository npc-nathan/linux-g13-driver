#!/bin/bash
# Every test this repository has, in one command.
#
#   bash tests/run-all-tests.sh        (or: make test)
#
# Nothing here touches the live configuration, the running daemon, or the device: each suite
# gets its own scratch directories, and the two that need a screen endpoint use a regular
# file where the driver would have a FIFO. Build first - the suites are skipped with a
# message rather than failing obscurely if the binaries are missing.
set -u

REPO="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPTS="$REPO/g13-driver/src/scripts"
CLASSES="$REPO/g13-config-tool/target/classes"
TESTS="$REPO/tests"
SCRATCH="$(mktemp -d "${TMPDIR:-/tmp}/g13-tests-XXXXXX")"

passed=0
failed=0

run() {
    local name="$1"
    shift
    # The name is for reading; the file it logs to needs a name without spaces or slashes.
    local slug
    slug="$(printf '%s' "$name" | tr -c 'A-Za-z0-9._-' '-')"
    printf '%-52s' "$name"
    if "$@" > "$SCRATCH/$slug.log" 2>&1; then
        printf 'ok\n'
        passed=$((passed + 1))
    else
        printf 'FAILED\n'
        failed=$((failed + 1))
        tail -3 "$SCRATCH/$slug.log" | sed 's/^/      /'
    fi
}

missing() {
    echo "missing: $1 - build first (make all, or make build-gui)"
    exit 2
}

[ -d "$CLASSES" ] || missing "$CLASSES"

if ! command -v java >/dev/null 2>&1; then
    echo "java is needed for the tool's tests"
    exit 2
fi

echo
echo "The screen's visuals"
run "applets, menu, layout rule, button mode" python3 "$TESTS/VisualsTest.py"

echo
echo "The config tool"
run "the screen model (Lcd/LcdFont)" java -Djava.awt.headless=true \
    -Dg13.repo="$REPO" -cp "$CLASSES:$TESTS" "$TESTS/FrameTest.java"
run "the tool and the daemon draw the same pixels" python3 "$TESTS/frame-check.py"
run "the screen window and the button mode" env XDG_CONFIG_HOME="$SCRATCH/config" \
    java -Djava.awt.headless=true -cp "$CLASSES:$TESTS" "$TESTS/ScreenPanelTest.java"
run "the sources panel and what it switches" env XDG_CONFIG_HOME="$SCRATCH/config" \
    XDG_RUNTIME_DIR="$SCRATCH/run" java -Djava.awt.headless=true -cp "$CLASSES:$TESTS" \
    "$TESTS/SourcesPanelTest.java"
# The designer writes into a directory of its own: the sources panel's suite deliberately leaves
# broken applets in the shared one to prove the checker reports them, and a check of *this*
# directory should only ever be judging what the designer produced.
run "the endpoints editor, and where the token lives" env XDG_CONFIG_HOME="$SCRATCH/designer" \
    java -Djava.awt.headless=true -cp "$CLASSES:$TESTS" "$TESTS/EndpointsTest.java"
run "the designer's model, and the applet it writes" env XDG_CONFIG_HOME="$SCRATCH/designer" \
    java -Djava.awt.headless=true -cp "$CLASSES:$TESTS" "$TESTS/AppletEditorTest.java"
run "and that applet is one the pad would accept" env XDG_CONFIG_HOME="$SCRATCH/designer" \
    python3 "$SCRIPTS/g13-applet" check --all

run "the guide agrees with the code" python3 "$TESTS/DocTest.py"
run "reading a topic from a broker" python3 "$TESTS/MqttTest.py"
run "reading a message from a web socket" python3 "$TESTS/WsTest.py"

echo
echo "The applet tool"
run "checking applets, and the lists it checks against" python3 "$TESTS/AppletToolTest.py"

echo
echo "The Cyberpunk 2077 mod"
run "the mod, without the game" python3 "$TESTS/cet-mod-test.py"

echo
echo "passed: $passed   failed: $failed   (logs: $SCRATCH)"
[ "$failed" -eq 0 ] || exit 1
