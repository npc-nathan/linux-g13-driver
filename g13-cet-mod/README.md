# g13-hud — Cyberpunk 2077 on a Logitech G13 screen

The pad's screen can show the game's own numbers: health, ammo, the tracked objective, the
district you are in, and an arrow pointing at your map pin with the distance.

It works in two halves that never touch each other. A small **Cyber Engine Tweaks mod** inside
the game writes the game's state to a JSON file about five times a second. On the Linux side the
visuals daemon reads that file and draws it. The mod changes nothing about the game, and the
screen is designed on the Linux side — so if you want a different layout, you edit an applet,
not Lua.

- Mod that goes in the game: `g13-cet-mod/init.lua`
- Applet that draws it on the pad: `g13-visuals/applets/cp2077-hud.json`

## What you need

| | |
| --- | --- |
| The driver and the visuals daemon | installed and running — see **Build & Installation** in [`../README.md`](../README.md). `make install-user` does it. |
| Cyber Engine Tweaks | version 1.37 or newer, installed in the game (<https://www.nexusmods.com/cyberpunk2077/mods/107>). `install.sh` checks for it and stops with a message if it is missing. |
| Where the game is | usually one of the paths below. Quoted, because of the space in the name. |

```text
~/Games/Heroic/Cyberpunk 2077                                    # Heroic (GOG or Epic)
~/.local/share/Steam/steamapps/common/Cyberpunk 2077             # Steam
~/.steam/steam/steamapps/common/Cyberpunk 2077                   # Steam, older layout
~/GOG Games/Cyberpunk 2077                                       # GOG Galaxy
```

With no argument `install.sh` tries those in order and says which it used. If your game lives
inside a **Wine or Lutris prefix**, pass the path *through the prefix* — the mod belongs next to
`Cyberpunk2077.exe`, on the Linux side of the same file system:

```bash
./install.sh "$HOME/Games/cyberpunk/drive_c/GOG Games/Cyberpunk 2077"
```

Nothing else is needed on the Linux side. You do **not** need Lua installed to run this — only
the repository's own test uses it.

## Install

Three steps, and only the third is this mod.

**1. The driver** (if it is not already running):

```bash
cd g13-driver/src && make install-user
systemctl --user is-active g13 g13-visuals     # expect: active, active
```

**2. Cyber Engine Tweaks**, in the game, if it is not already there. It is a normal CET install:
the CET archive goes next to `Cyberpunk2077.exe`. Nothing here needs configuring afterwards.

**3. This mod**, from the repository root:

```bash
./g13-cet-mod/install.sh                        # finds the game, says which it used
./g13-cet-mod/install.sh "/path/to/Cyberpunk 2077"
```

```text
using the game at: /home/you/Games/Heroic/Cyberpunk 2077
installed:
  mod:    /home/you/Games/Heroic/Cyberpunk 2077/bin/x64/plugins/cyber_engine_tweaks/mods/g13-hud/init.lua
  applet: /home/you/.config/g13/applets/cp2077-hud.json
```

Two files, nothing else:

| file | what it is |
| --- | --- |
| `<game>/bin/x64/plugins/cyber_engine_tweaks/mods/g13-hud/init.lua` | the mod the game loads |
| `~/.config/g13/applets/cp2077-hud.json` | the applet the pad draws (with `{GAME}` replaced by your path) |

Running it again updates both — that is also how you update.

<details>
<summary>Installing by hand instead</summary>

Copy the two files yourself. The applet is the same JSON with `{GAME}` replaced; the quotes are
needed because the path has a space in it:

```bash
GAME="$HOME/Games/Heroic/Cyberpunk 2077"
mkdir -p "$GAME/bin/x64/plugins/cyber_engine_tweaks/mods/g13-hud"
cp g13-cet-mod/init.lua "$GAME/bin/x64/plugins/cyber_engine_tweaks/mods/g13-hud/"
mkdir -p ~/.config/g13/applets
sed "s|{GAME}|$GAME|g" g13-visuals/applets/cp2077-hud.json > ~/.config/g13/applets/cp2077-hud.json
```

If you edit the path, edit every `json:` line in the applet — they each carry the full path to
`hud.json`.
</details>

## It comes and goes with the game

You do not have to choose it: while the game is writing `hud.json`, the pad shows NIGHT CITY by
itself, and about twenty seconds after you quit it goes back to whatever was showing before. The
grace period is there because a long load or a paused game can stop the writing for a while.

It follows the game **running**, not the game being focused, so alt-tabbing to a browser changes
nothing — CET keeps writing while the game is loaded. Switching by hand while it is running is
respected: pick the clock and it stays on the clock until the game goes away.

That behaviour is one line in the applet, and removing it makes the applet an ordinary one you
choose yourself:

```json
"follow": {"seconds": 20},
```

## On the pad

Tap the round **G24** button (left of the screen) until the title reads **NIGHT CITY**, or open
the config tool's **Screen…** window, select `cp2077-hud` and press **Show now**. Hold G24 for
the menu; L2/L3 move, L4 selects, L1 goes back.

The screen shows:

- health as a segmented bar, `AMMO` from the magazine, `LVL`
- the tracked objective, scrolling if it is long
- an arrow to your **map pin** and the distance, plus the district — straight up on the arrow
  means straight ahead

## Check it works

1. **Start the game** and load a save. In the CET console you should see the mod announce
   itself: `[g13-hud] v1.0: writing hud.json every 0.20s`.
2. **The file appears** and keeps changing:

   ```bash
   ls -l "$HOME/Games/Heroic/Cyberpunk 2077/bin/x64/plugins/cyber_engine_tweaks/mods/g13-hud/hud.json"
   cat  "$HOME/Games/Heroic/Cyberpunk 2077/bin/x64/plugins/cyber_engine_tweaks/mods/g13-hud/hud.json"
   ```

3. **The pad follows it** — health drops when you take a hit, ammo when you shoot.
4. **`G13Probe()`** in the CET console prints what your build answers for the player, the weapon,
   the stats, the journal and every map mappin. Worth running once; see *Finding out what your
   build answers* below.

## If something is blank

| symptom | what it means |
| --- | --- |
| no `hud.json` at all | the game is not running, or CET did not load the mod — look for the `[g13-hud]` line in the CET console. A Lua error there is reported by CET, not silently swallowed. |
| `hud.json` there, pad blank | the applet is not looking at that file, or the visual is not enabled. Check the path inside `~/.config/g13/applets/cp2077-hud.json`, check `cp2077-hud` is in the Screen window's list, and that the pad is not on another visual. |
| some fields blank (`ammo`, pin) | by design: those are probed rather than known. Run `G13Probe()`. A game build that refuses a call gives a blank instead of an error. |
| the arrow points nowhere useful | no pin is set on the map, or its variant is not the one this mod looks for. The probe lists every mappin variant with its distance. |
| the distance looks wrong by a factor | the mod assumes 100 world units to the metre. One constant to change (`PIN_UNITS_PER_METRE`). |

## Remove

```bash
./g13-cet-mod/install.sh --remove               # finds the game, as above
./g13-cet-mod/install.sh --remove "/path/to/Cyberpunk 2077"
```

That deletes the mod folder from the game and the applet from `~/.config/g13/applets/`. Nothing
else of yours is touched, and the game itself was never modified — the mod is confined to its own
folder by CET's sandbox, which is also why it writes `hud.json` there rather than anywhere it
likes.

## What it reads, and how sure that is

Every call below was checked against mods already running in an installation, not against
documentation:

| field | source | confidence |
| --- | --- | --- |
| `health`, `stamina`, `level`, `streetcred` | `Game.GetStatsSystem():GetStatValue(entityId, 'Health')` | verified in use |
| `objective`, `quest`, `objective_id`, `quest_id` | `Game.GetJournalManager():GetTrackedEntry()` then two `GetParentEntry()` steps | verified in use |
| `weapon` | the active weapon's `GetName()` loc key through `GetLocalizedText` | verified in use |
| `x`, `y`, `z`, `heading` | `GetWorldPosition()`, `GetWorldForward()` | verified in use |
| `district` | `PreventionSystem.districtManager:GetCurrentDistrict()` → its TweakDB record's `LocalizedName()` | pattern verified in use |
| `near` | the nearest map mappin that has a name | pattern verified in use |
| `pin_bearing`, `pin_distance`, `pin_relative`, `route` | `GetMappinSystem():GetMappins(Map)`, the mappin whose variant is `CustomPositionVariant` | **probed**: see `G13Probe()` |
| `ammo`, `ammo_total`, `ammo_max` | the weapon's magazine/ammo calls | **probed**: whichever the build answers |

Anything that cannot be read is simply absent from the file — a game build that refuses a call
gives a blank on the screen, never an error. `tests/cet-mod-test.lua` runs the mod against a
stub that refuses those calls, to keep it that way.

## Finding out what your build answers

In the CET console:

```lua
G13Probe()
```

It prints what this build allows for the player, the held weapon, the four stats and the tracked
journal entry, then every map mappin by distance with its variant — which is how the pin is
identified, and how you would add another destination (the tracked quest, say) if it turns out
to be there. If `ammo` is blank on the screen, this output shows which call to use, and it is one
line to wire in.

## What is not there yet

- **No turn-by-turn.** The game's own route is not readable anywhere found, so there is no next
  corner and no road to follow on the screen. What there is instead: **an arrow to your map
  pin** — the game's own custom-position mappin — with the distance, and it points relative to
  which way you are facing, so straight up means straight ahead.
- **The nearest map label is often the road you are on.** `near` came back as `Drake Ave` on the
  first run here — the map's own labels include street names, so this frequently *is* the street,
  but there is no street API behind it: it is whichever named label is closest, which may be a
  shop or a building instead.
- **The distance to a pin is a straight line**, and assumes 100 world units to the metre. Drive a
  known distance once with the pin set; if the number is out by a factor, it is one constant
  (`PIN_UNITS_PER_METRE`).
- **The pin variant is matched by name** (`CustomPosition`) as well as by identity, because enum
  tables differ between builds. If no pin is found, `G13Probe()` prints the variants present and
  the name is one line to add.
- **The heading convention is worth a glance in game.** It is written as 0° = north, 90° = east.
  If it reads backwards, it is one line in `init.lua`.
- **`objective` is only the current phase's text.** Sub-objectives and the "N/M" counters some
  quests show in the HUD are not read.

## The file it writes

`<mod folder>/hud.json`, replaced whole each time (written beside itself and renamed over, so a
reader never sees half of it):

```json
{
  "mod": "g13-hud", "version": "1.0", "updated": "21:14:07",
  "health": 87, "stamina": 61, "level": 32, "streetcred": 50,
  "objective": "Deliver the package to Vex", "objective_id": "q005_rogue_obj_2",
  "quest": "Rogue's request", "quest_id": "q005_rogue",
  "weapon": "Overture", "ammo": 24, "ammo_total": 180, "ammo_max": 24,
  "x": 100.5, "y": -200.25, "z": 8.0, "heading": 137,
  "district": "Watson", "near": "Megabuilding H10",
  "pin_bearing": 42, "pin_distance": 240, "pin_distance_raw": 24013,
  "pin_relative": 265, "route": "PIN 240m  Watson"
}
```

`route` is one line already made up for a small screen: the pin and its distance when there is
one, otherwise the district and the nearest named place — so an applet does not have to choose
between fields it cannot test for itself.

Any program may read it, and any game that can write a file like it can drive the same applet —
`json:<path>#<field>` is all the Linux side needs. For a game with no scripting layer of its
own, the Logitech LCD SDK route in [`../README.md`](../README.md) is the other way in.
