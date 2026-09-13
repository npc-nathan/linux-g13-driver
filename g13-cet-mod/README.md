# g13-hud — Cyberpunk 2077 on a Logitech G13 screen

A Cyber Engine Tweaks mod that writes the game's numbers to a small JSON file, about five times
a second. The Linux side reads that file with the driver's `json:` data source and draws it, so
the screen is designed on the Linux side: this mod is data, not a picture.

It writes one file inside its own folder and changes nothing about the game.

## Install

```bash
./install.sh                          # default game: ~/Games/Heroic/Cyberpunk 2077
./install.sh "/path/to/Cyberpunk 2077"
./install.sh --remove                 # takes the mod and the applet away again
```

That copies `init.lua` to `<game>/bin/x64/plugins/cyber_engine_tweaks/mods/g13-hud/`, and the
applet to `~/.config/g13/applets/cp2077-hud.json` with the path placeholder replaced by wherever
the game actually is. Requires CET 1.37+ (written against 1.37.1).

## On the pad

Tap the round button until the screen reads **NIGHT CITY**, or pick it in the config tool's
Screen window and press **Show now**.

## What it reads, and how sure that is

Every call below was checked against mods already running in this installation, not against
documentation:

| field | source | confidence |
| --- | --- | --- |
| `health`, `stamina`, `level`, `streetcred` | `Game.GetStatsSystem():GetStatValue(entityId, 'Health')` | verified in use |
| `objective`, `quest`, `objective_id`, `quest_id` | `Game.GetJournalManager():GetTrackedEntry()` then two `GetParentEntry()` steps | verified in use |
| `weapon` | the active weapon's `GetName()` loc key through `GetLocalizedText` | verified in use |
| `x`, `y`, `z`, `heading` | `GetWorldPosition()`, `GetWorldForward()` | verified in use |
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
journal entry. If `ammo` is blank on the screen, that output shows which call to use and it is
one line to wire in.

## What is not there yet

- **No turn-by-turn arrow.** The heading arrow is the direction you are facing, from
  `GetWorldForward()`. A bearing to the tracked waypoint needs the waypoint's world position,
  and the call that reads it has not been found — the mappin system's setter is visible in other
  mods, not a getter. `G13Probe()` checks `GetMappinPath` and `GetPosition` on the tracked entry
  for exactly this.
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
  "x": 100.5, "y": -200.25, "z": 8.0, "heading": 137
}
```

Any program may read it, and any game that can write a file like it can drive the same applet —
`json:<path>#<field>` is all the Linux side needs.
