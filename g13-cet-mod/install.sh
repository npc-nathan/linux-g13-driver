#!/bin/bash
# Puts the Cyberpunk 2077 mod in the game, and the matching applet on the Linux side.
#
#   ./install.sh                       (default game: ~/Games/Heroic/Cyberpunk 2077)
#   ./install.sh "/path/to/Cyberpunk 2077"
#   ./install.sh --remove              takes both away again
#
# It copies init.lua into the game's CET mods folder as g13-hud/, and copies the applet to
# ~/.config/g13/applets/cp2077-hud.json with the path placeholder replaced by wherever the
# game actually is. Nothing is overwritten outside those two places, and re-running it is how
# to update.
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
APPLETS_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/g13/applets"
APPLET_NAME="cp2077-hud.json"
MOD_NAME="g13-hud"

REMOVE=0
if [ "${1:-}" = "--remove" ]; then
    REMOVE=1
    GAME="${2:-$HOME/Games/Heroic/Cyberpunk 2077}"
else
    GAME="${1:-$HOME/Games/Heroic/Cyberpunk 2077}"
fi

MODS_DIR="$GAME/bin/x64/plugins/cyber_engine_tweaks/mods"

if [ "$REMOVE" = "1" ]; then
    rm -rf "$MODS_DIR/$MOD_NAME"
    rm -f "$APPLETS_DIR/$APPLET_NAME"
    echo "removed:"
    echo "  $MODS_DIR/$MOD_NAME"
    echo "  $APPLETS_DIR/$APPLET_NAME"
    exit 0
fi

if [ ! -d "$GAME" ]; then
    echo "no game at: $GAME"
    echo "pass the path: $0 \"/path/to/Cyberpunk 2077\""
    exit 1
fi

if [ ! -d "$GAME/bin/x64/plugins/cyber_engine_tweaks" ]; then
    echo "Cyber Engine Tweaks is not installed in that game:"
    echo "  expected $GAME/bin/x64/plugins/cyber_engine_tweaks"
    echo "Install CET first (nexusmods.com/cyberpunk2077/mods/107), then run this again."
    exit 1
fi

install -d "$MODS_DIR/$MOD_NAME"
install -m 644 "$HERE/init.lua" "$MODS_DIR/$MOD_NAME/init.lua"
install -m 644 "$HERE/README.md" "$MODS_DIR/$MOD_NAME/README.md"

install -d "$APPLETS_DIR"
sed "s|{GAME}|$GAME|g" "$HERE/../g13-visuals/applets/$APPLET_NAME" > "$APPLETS_DIR/$APPLET_NAME"

echo "installed:"
echo "  mod:    $MODS_DIR/$MOD_NAME/init.lua"
echo "  applet: $APPLETS_DIR/$APPLET_NAME"
echo
echo "Next:"
echo "  1. start the game - the mod writes hud.json in its own folder about five times a second"
echo "  2. in the CET console, G13Probe() lists what this build answers (useful if ammo is blank)"
echo "  3. on the pad, tap the round button until the screen shows NIGHT CITY, or pick it in the"
echo "     config tool's Screen window and press 'Show now'"
