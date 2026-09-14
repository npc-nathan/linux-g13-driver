# G13 Linux Driver & GUI (Modernized Fork)

This is a modernized fork of the G13 driver for Linux.
The original project is over 10 years old. This fork has been refactored to use modern C++ standards for the driver and modern Java standards (Java 17 with Maven) for the configuration GUI.

## Features

* **The pad works as a pad.** Profiles with M1/M2/M3, the buttons and the joystick, macros,
  pass-through keys, and a working MR record button — with the profile bug of the upstream
  driver fixed (the M buttons are report bits 29-32, not 25-28).
* **A real screen.** The LCD is a 160x43 display anything can draw on: text, frames, bars and
  applets, driven by `g13-lcd` or by writing the driver's pipe directly.
* **A screen that does something by itself.** `g13-visuals` draws live visuals — clock,
  system, media, pad state, your own applets — with the four buttons beside the screen
  choosing and navigating (`g24`: tap for the next one, hold for the menu; L1-L4: back, up,
  down, select).
* **Applets in JSON.** A screen can be described rather than programmed: widgets bound to
  data sources, live values, and the config tool previews it against real data.
* **The Logitech LCD SDK, on Linux.** The functions games and applets call on Windows
  (`LogiLcdInit`, `LogiLcdMonoSetText`, ...) implemented here, including a PE
  `LogitechLcd.dll` for games running under Wine/Proton, with `g13-lcd-bridge` relaying.
* **A Java configuration tool.** Java 17 and Maven, with keys, profiles, macros, and a Screen
  window that shows what the panel is showing.
* **Helpers you can script.** `g13-lcd`, `g13-watch`, `g13-keywatch`, `g13-buttons`,
  `g13-service`, and a test suite covering all of it (`make test`).

## Requirements

### Base Requirements

You need to install the following packages via your package manager:

* `make`, `cmake`, `gcc`/`g++`
* `gtk3` / `gtk3-devel`
* `libusb-1.0-0` (on some distros named `libusb-1.0-0-dev` or `libusb1-devel`)
* `libappindicator-gtk3` (or `libayatana-appindicator3-dev` on Debian/Ubuntu 22.04+)
* `Maven` and `Java 17` or higher (for the configuration tool)
* `python3` (the screen tools and the tests are standard library only)

Optional:

* `mingw-w64` - to build the Windows `LogitechLcd.dll` for games under Wine/Proton
  (`make build-lcdsdk-windows`)
* `evtest` - what `g13-keywatch` runs, to see what a key actually sends

### Automated Dependency Installation

Alternatively, all needed dependencies can be installed via the `install_deps.sh` script located in the scripts folder.

```bash
cd src/scripts
chmod +x install_deps.sh
./install_deps.sh
```

## Build & Installation

1.  Open a terminal and navigate to the project directory.
2.  Build everything:

    ```bash
    make all          # the driver, the configuration tool, and the Logitech LCD SDK
    make test         # every test in the repository, without touching the device
    ```

## Choose your Installation Method

### Option A: System-Wide Installation (Standard)
Binaries go to `/usr/bin`, the library and header to `/usr/lib` and `/usr/include`, the menu
entries to `/usr/share/applications`, and the user units to `/usr/lib/systemd/user`.

```bash
sudo make install
sudo make install-udev       # optional: ship the udev rule to /usr/lib/udev/rules.d
```

Nothing is started for you: a system install never touches your session. Enable the three
user services once:

```bash
systemctl --user enable --now g13 g13-visuals g13-lcd-bridge
```

To take it all back out: `sudo make uninstall` (see **Uninstallation** at the end).

#### Option B: User-Local Installation (Developer Mode)
Everything goes into `~/.local` (`bin`, `lib`, `include`, `share/applications`) and the units
into `~/.config/systemd/user`. No root, and it is what this machine uses.

```bash
make install-user       # installs everything and starts the three services
make install-udev       # once, separately: the udev rule needs sudo
```

`install-user` deliberately runs no `sudo`: the only step that needs root is the udev rule in
`/etc`, and keeping it out means a reinstall never asks for a password. To bring the services
back after a change, `make enable-services` (or `g13-service start all`). To take it back out:
`make uninstall-user` — it leaves your configuration in `~/.config/g13` alone.

### What gets installed

| Piece | What it is |
| --- | --- |
| `linux-g13-driver` | the driver itself, run as `g13.service` |
| `g13-gui` / `Linux-G13-GUI.jar` | the configuration tool: keys, profiles, macros, the Screen, Sources and designer windows |
| `g13-visuals` | draws on the screen and runs the menu (`g13-visuals.service`) |
| `g13-lcd-bridge` | relays a Windows `LogitechLcd.dll` to the driver (`g13-lcd-bridge.service`) |
| `g13-lcd` / `g13lcd.py` | text, images and frames on the screen, as a tool or an importable module |
| `g13-buttons` | says or sets who owns the screen and the four buttons (`auto`, `visuals`, `sdk`) |
| `g13-service` | start, stop or restart the services, with a desktop notification |
| `g13-keywatch` / `g13-watch` | what the pad sends / the event bus it publishes |
| `g13-applet` | checks an applet before it reaches the pad (`g13-applet check FILE`, `--all`) |
| `liblogitechlcd.so`, `LogitechLcd.h` | the Logitech LCD SDK for programs here; `LogitechLcd.dll` for Windows ones |

The menu entries (`G13 Configuration`, `G13 Start Driver`, `G13 Stop Driver`, `G13 Start All`,
`G13 Stop All`) land in your applications menu.

Nothing above touches a game. The Cyberpunk 2077 screen is a **separate, optional install**
that writes into the game's own directory, with its own installer and its own walkthrough:
[`g13-cet-mod/README.md`](g13-cet-mod/README.md).

Note on permissions: the udev rule lets the driver claim the device without sudo, and makes
the driver's own virtual keyboard readable by the `plugdev` group, so `evtest` and
`g13-keywatch` work without root. Unplug and replug the pad once after installing it.

Checking what a binding sends: run `g13-keywatch` and press keys on the pad; it prints the
events the driver emits, including the M button codes (`688` = MR, `691`/`692`/`693` =
M1/M2/M3). If it says the device is not readable, the udev rule is missing: `make
install-udev`, or `sudo cp udev/99-g13.rules /etc/udev/rules.d/ && sudo udevadm control
--reload-rules && sudo udevadm trigger --subsystem-match=input`.

## How to use the Driver and GUI

### Run the driver

### Controlling the Driver
The driver runs in the background via Systemd.

Check Status:

```bash
systemctl --user status g13
```

View Logs:

```bash
journalctl --user -u g13 -f
```

Restart Driver:

```bash
systemctl --user restart g13
```


### Use the Config Tool

After starting the driver, you will see a new icon in your system tray/taskbar. This allows you to open the config menu or quit the driver.

Alternatively, run it from the terminal:

```bash
g13-gui
```

This will bring up the UI.

Profiles: pick one with the **Profile** selector at the top of the panel (**M1, M2,
M3**). That loads `bindings-N.properties` for editing *and* activates it on the
device — the selector writes the same `~/.config/g13/active-profile` file the driver
watches, so the pad switches over within a second. **MR is not a profile**: it is the
macro record button and sends `KEY_MACRO_RECORD_START`, unless the profile binds it.
Pressing any of the M buttons on the pad selects it for editing like any other key.

Button types: a selected key can be **None**, **Pass Through**, **Macro** or
**M Key event**. For the M buttons the M key list carries one extra entry at the top,
`(default) switch profile` on M1-M3 and `(default) macro record` on MR — that is what
the button does when the profile contains no entry for it, and picking it removes the
entry again. Choosing *None* for an M button stores `x`, an explicit "do nothing",
because an absent entry there means the device's own behaviour.

Save: Changes are saved automatically to `~/.config/g13/bindings-*.properties`.

## The LCD screen as a display

`g13-lcd` (installed alongside the driver) writes to the screen: 160x43 pixels, one bit
each.

```sh
g13-lcd "CPU 42%" "MEM 61%"     # a screen of text lines (clears first)
g13-lcd --at 4 8 "top left"     # one line at a position, without clearing
g13-lcd --clear
g13-lcd --image shot.pbm        # a bitmap, scaled to fit and centred
g13-lcd --hex 0001ff...         # a raw 960 byte frame (1920 hex digits)
```

Anything that can write to a file can drive the screen: the driver reads
`$XDG_RUNTIME_DIR/g13-lcd`. Plain lines of text work as they always did (the first line
clears the screen and each following line goes below the last), and lines starting with
`#` are commands:

| Command | Effect |
| --- | --- |
| `#clear` | blank the screen |
| `#text <x> <y> <text>` | draw text at a position, leaving the rest of the screen alone |
| `#bitmap <1920 hex digits>` | replace the screen with a raw 960 byte frame |

Everything read in one go is painted as a single frame, so a screen built from several
lines does not flicker. Frames are 160x48, one bit per pixel, stored as vertical bytes:
byte `x + (y / 8) * 160` holds the eight pixels of column `x` starting at row
`(y / 8) * 8`, with the lowest row of that group in bit 0. **The panel is 160x43**: the top
43 rows of that 48-row buffer are the visible screen, so keep content inside y 0-42 or it
is off the glass (rows 43-47 are invisible, and text lines that would be cut off are
skipped rather than half drawn). `g13-lcd` fits images to the visible area and exposes it
as `g13lcd.VISIBLE_HEIGHT`. `g13-lcd` also works as a
module - `g13lcd.at()`, `g13lcd.frame()`, `g13lcd.pixels_to_frame()` - so a game can put
its own stats on the screen.

The mechanism is the reproducible part: an application pushes data and the driver renders it.
Logitech's own way of doing that - the SDK games and applets call on Windows - is implemented
here too, including the Windows library itself for games under Wine or Proton: see
[The Logitech LCD SDK, on Linux](#the-logitech-lcd-sdk-on-linux) below. Games that never used
it (and have no mod support) are still out of reach; nothing on Linux can make a game draw
somewhere it never tried to.

## The Logitech LCD SDK, on Linux

Games and applets written against Logitech's SDK call `LogitechLcd.dll`, which ships with
Logitech Gaming Software on Windows - which is why a panel like this one shows nothing from
them here, and why "Logitech supports this game" never helped on Linux. `src/lcdsdk/` is that
missing half: the same functions, the same types, the same button bits, forwarding to this
driver.

    make build-lcdsdk          # liblogitechlcd.so and the self-test
    make test-lcdsdk           # the whole surface, checked without a device
    make build-lcdsdk-windows  # a proxy LogitechLcd.dll for Wine/Proton; needs mingw-w64

`make install-user` puts the library in `~/.local/lib` and the header in `~/.local/include`.

What each call becomes:

| SDK call | here |
| --- | --- |
| `LogiLcdInit(name, MONO)` | opens the driver's LCD pipe; false when the driver is not running |
| `LogiLcdIsConnected(MONO)` | whether that pipe is open - `COLOR` is always false, this panel is monochrome |
| `LogiLcdMonoSetText(line, text)` | line 0..3, drawn at x=3, y = 2 + 10 * line |
| `LogiLcdMonoSetBackground(bitmap)` | the whole 160x43 field, 8 bits per pixel, pixel on at >= 128 |
| `LogiLcdUpdate()` | one frame: the background as `#bitmap`, then the text lines as `#text` |
| `LogiLcdIsButtonPressed(mask)` | the four mono buttons (`0x01, 0x02, 0x04, 0x08`) are L1..L4 on the pad |
| `LogiLcdShutdown()` | closes up |

`LogiLcdUpdate()` writes the whole frame in a single write, because the driver paints whatever
arrives in one `read()` as one frame - which is what keeps a screen built from several lines
from flickering.

Limits, stated rather than discovered later: two programs calling `LogiLcdUpdate()` will fight
over the screen (Logitech's LCD Manager rotated between applets; there is no manager here).

### Games under Wine or Proton

A Windows program cannot use the driver's Unix sockets, so the Windows build of the library
talks to `g13-lcd-bridge` over TCP on 127.0.0.1 instead, and the bridge relays both ways
(frames down to the driver, button events back up):

    make -C g13-driver/src/lcdsdk windows   # a PE32+ LogitechLcd.dll, built with mingw-w64
    g13-lcd-bridge                          # the other half of that conversation

Put the DLL in the game's prefix with a DLL override (`WINEDLLOVERRIDES=LogitechLcd=n,b`) and
run the bridge. `G13_LCD_TCP=host:port` overrides where the library looks.

The Windows transport is also built for Linux (`-DG13_TCP_TRANSPORT=1`), which is how it is
tested here without Wine: `make test-lcdsdk-proxy` runs the same code through the bridge,
checks the frames pixel by pixel, the button events coming back, and that the DLL's exports
are the SDK's ten. What has *not* been run is a real game under Proton - the plumbing is
proven, the game is not.

An example client, in Python, using the same library a game would load:

    python3 g13-driver/src/lcdsdk/example-logitech-lcd.py

### Who owns the screen and the four buttons

The buttons beside the screen are shared: the visuals menu uses them, and so does an SDK
client (that is what its button query is for). One setting decides:

| mode | the screen and L1-L4 |
| --- | --- |
| `auto` (default) | an SDK client takes over while it is connected, otherwise the visuals |
| `visuals` | the visuals always keep them - an SDK client would have to wait |
| `sdk` | an SDK client always has them, connected or not |

    g13-buttons            # show the mode, and who has the screen now
    g13-buttons auto       # or visuals, or sdk

The same control is in the config tool's **Screen** window ("Screen and L1-L4"), and in the
file both write: `$XDG_CONFIG_HOME/g13/button-mode`. It applies within a second - no restart,
no confirm button.

How the daemon knows a client is there: the library touches `$XDG_RUNTIME_DIR/g13-sdk-client`
on every frame, and the bridge keeps it fresh while a Windows client is connected. A file
older than three seconds counts as gone, so a client that is killed outright cannot leave the
pad deaf to its own buttons. While a client owns the screen the daemon stops drawing and
stops reading the buttons, and it says so in the tool ("now: the SDK client"): the preview
then shows the last frame the visuals drew, not what is on the panel.

## A game on the screen: Cyberpunk 2077

The pad can show a game's own numbers. `g13-cet-mod/` is a Cyber Engine Tweaks mod that writes
the game's state to a small JSON file about five times a second:

**→ Full walkthrough, for anyone installing it: [`g13-cet-mod/README.md`](g13-cet-mod/README.md)**
— what you need, where the game lives on Steam/Heroic/GOG or inside a Wine prefix, how to check
it worked, what to do when a field is blank, and how to take it out again.

```bash
./g13-cet-mod/install.sh                       # finds the game in the usual places
./g13-cet-mod/install.sh "/path/to/Cyberpunk 2077"
./g13-cet-mod/install.sh --remove              # takes the mod and the applet away again
```

It reads health, stamina, level and street cred, the tracked quest and objective (the text
resolved from its loc key), the held weapon and its ammo where the build answers for it, and your
position and heading. The applet `cp2077-hud` draws them: health as segments, ammo, the objective
scrolling along, and a compass arrow. On the pad, tap the round button until it reads **NIGHT
CITY**, or pick it in the Screen window and press **Show now**. Its own `README.md` lists every
field and how sure each one is.

Two deliberate gaps: there is **no turn-by-turn** (the game's own route is not readable), and
**ammo is probed, not known**, because builds differ in what they answer. What there is instead
of turn-by-turn: an **arrow to your map pin** with the distance, pointing relative to which way
you are facing, plus the **district** you are in and the nearest map label — which on the first
run here was `Drake Ave`, i.e. the road. `G13Probe()` in the CET console prints what your build
allows — including every map mappin variant with its distance, which is how the pin is
identified — and every uncertain call is guarded, so a build that refuses one leaves a blank
rather than an error.

The pad **shows this by itself while the game is running** and goes back to the previous visual
about twenty seconds after you quit, because the applet says `"follow": {"seconds": 20}`. It
follows the game running, not the game being focused, so alt-tabbing does not flicker the
screen; choosing another visual by hand is respected until the game goes away.

**Which framework?** CET (Lua) is the only practical way to get live state out of a running
Cyberpunk 2077 — it can write files, and REDscript cannot. REDscript is also Cyberpunk-only (its
own README says so), and The Witcher 3's REDkit/REDmod scripts have no Lua layer to inject into,
so the two do not share a mod framework despite the shared engine. That does not matter here:
anything that can write a JSON file, or call the Logitech LCD SDK above, can drive this panel.

## The event bus

Pad presses are published on a Unix socket, `$XDG_RUNTIME_DIR/g13.sock`, owner-only:

```
key 32 1        # MR pressed
key 32 0        # MR released
```

Any number of programs can connect and every one of them sees every event. That is why this
is a socket and not the older `g13-events` pipe, which still works: a pipe splits its stream
between readers, so a second consumer silently steals events from the first. Commands go the
other way on the same connection - `record 1` / `record 0` - and `g13-watch` prints events
with their key names (`g13-watch --press` for presses only). Note that the display buttons
L1-L4 are the screen's menu buttons (the kernel calls them `KEY_KBD_LCD_MENU1..4`) and are
not profile selectors.

## The screen's visuals and menu

`g13-visuals` (a user service, installed and enabled by `make install-user`) owns the
screen once it is running. Buttons on the pad drive it:

| Button | Action |
| --- | --- |
| **G24** (round button left of the screen) | short press: next visual; hold: open/close the menu |
| **L1** | back (closes the menu) |
| **L2** / **L3** | up / down |
| **L4** | select |

The same choices from a terminal. This edits the same `visuals.json` the daemon reads about once
a second, so nothing talks to a running daemon and none of it needs one — a change made with no
daemon running is what it shows next time it starts.

```bash
g13-visuals --list                       # what can be shown, and what is switched on
g13-visuals --select night-city          # show it now: name, file name, or the title on the pad
g13-visuals --enable clock               # add it to what the round button cycles through
g13-visuals --disable media              # take it out
g13-visuals --status                     # what is showing, who owns the screen, is the daemon up
```

`--list` marks the one that is showing, and when an applet's file name differs from the name
inside it, shows both (`applet:demo-stats  (example-stats.json)`). A name that does not exist is
an error with a suggestion rather than a guess.

Visuals: `clock`, `system` (cpu, memory, load, uptime), `media` (whatever `playerctl`
reports), `pad` (active profile, recording state, recent presses) and `custom` (four lines
from `$XDG_CONFIG_HOME/g13/screen.txt`, so anything that can write a file can drive the
screen). The menu also toggles **auto-cycle**, which steps through the enabled visuals on a
timer; a tap of G24 always moves on regardless.

Which visuals are enabled, their order, the current one and the cycle time live in
`$XDG_CONFIG_HOME/g13/visuals.json`. The daemon reloads that file within a second of it
changing, so the config tool can edit it without anything being restarted:

```json
{
  "enabled": ["clock", "system", "media", "pad", "custom"],
  "active": "clock",
  "cycle": false,
  "cycle_seconds": 10
}
```

The screen is one colour of ink, so a menu selection cannot be a filled row with text on
it - the text would vanish into it. The selected row gets a marker bar beside the label and
a line underneath instead, and every visual keeps its text on blank pixels. That rule is
enforced by the tests, not just by convention.

### Designed applets

An applet is a JSON file that draws things on the screen, bound to values. The whole of it — the
tutorials, the variables, the widgets and their fields, the sources, and what to do when a value is
blank — is in **[docs/applets.md](docs/applets.md)**. This section is the summary.

An applet is a JSON file in `$XDG_CONFIG_HOME/g13/applets/`: widgets placed on the screen
and bound to live data. It appears in the menu under its `title` as soon as it is listed in
`enabled` in `visuals.json`.

```json
{
  "name": "demo-stats", "title": "STATS", "interval": 1,
  "widgets": [
    {"type": "text", "x": 3,  "y": 12, "format": "CPU {cpu:.0f}%"},
    {"type": "bar",  "x": 28, "y": 12, "w": 88, "h": 9, "source": "cpu", "max": 100},
    {"type": "line", "x": 1,  "y": 34, "w": 158},
    {"type": "box",  "x": 1,  "y": 22, "w": 157, "h": 10}
  ]
}
```

Widgets: `text` (a `format` template, or `source` on its own), `bar` (`source`, `max`),
`line`, `vline`, `box`.

Sources: `cpu`, `memory`, `load`, `uptime`, `uptime_seconds`, `time`, `time_seconds`,
`date`, `day`, `media_status`, `media_artist`, `media_title`, `media_position`,
`media_duration`, `media_percent`, `profile`, `recording`, `last_key`, `recent_keys`,
`screen_width`, `screen_height`, plus `env:NAME`, `file:PATH` (first line),
`json:PATH#FIELD` and `cmd:SHELL COMMAND` (cached 2 s, 0.4 s timeout), and
`http:<endpoint>/<path>#field` for a web address. An endpoint — its address, headers and token — is
a named entry in `endpoints.json`, so a shared applet carries `http:weather/now#temp` and no
credential.

`format` is a `str.format` template over all of the values at once, so one label can show
several: `"{day} {date} {time}"`. A worked example ships in
`g13-visuals/applets/example-stats.json`.

The daemon publishes what it is drawing to `$XDG_RUNTIME_DIR/g13-screen` and the live
values to `$XDG_RUNTIME_DIR/g13-values.json`, which is how the config tool shows the real
screen in its preview, and where anything else can read the current values from.

### The designer window

*(Part of [docs/applets.md](docs/applets.md), which has the tutorials.)*

**Design…** in the Screen window opens the applet you have selected; **New applet…** starts a new
one from a template. An applet can be edited by hand in a text editor just as well — the window
writes the same file, and nothing about it is hidden in the window.

- **Every change is written to the applet's file as it is made.** There is no Apply button and
  nothing to lose by closing the window.
- **While it is open, that applet is the one on the pad**, so what you are editing is what you are
  looking at.
- **The daemon watches the applet files themselves**, so a save reaches the pad within a second —
  no restart, and nothing to click.
- **The picture is the daemon's own frame**, read back from the driver, not a second renderer's idea
  of the design. If the window and the pad could disagree, the window would be lying.
- **The status line names the final authority**: `g13-applet check <file>` knows the rules the pad
  has, and it is the thing to run before telling anyone an applet is finished. The designer
  deliberately does not carry its own copy of those rules.
- Widgets can be added, removed, duplicated and reordered. The fields offered are the ones the
  selected widget type actually uses, so a text line never offers a bar's width.
- **What it can read** is the second tab: every name an applet may use with a line saying what it
  is, this applet's own sources, and **Read them now**, which runs `g13-applet check --values` and
  shows what each of them reads at that moment. Double-click a name to add it to the selected
  widget's format. The list of built-in names is compared against the daemon's own by a test, so
  the window cannot offer a name that does nothing on the pad.
- **sources** is the applet's own aliases, one `name = spec` per line — this is how an applet says
  where its numbers come from, including `json:` files written by something else.

### The config tool's sources window

*(Part of [docs/applets.md](docs/applets.md): [Stop applets reading something](docs/applets.md#14-stop-applets-reading-something).)*

The **Sources…** button opens what data an applet may read, one switch per kind of source:
`built-in` (cpu, memory, uptime, time, media, profile, keys), `env:`, `file:`, `json:`, `cmd:` and
`http:`.

The named web addresses `http:` refers to live in `~/.config/g13/endpoints.json`, where an address
and its credential sit together:

```json
{
  "home":    {"url": "http://homeassistant.local:8123",
              "token": "…", "timeout": 2},
  "weather": {"url": "https://wttr.in"}
}
```

The **Endpoints** tab of that window is where they are made and edited, with no confirm buttons —
the file is written as you type and the daemon reads it within a second:

- **Add…** names an address; the fields are its **address**, its **token** (sent as
  `Authorization: Bearer …`, hidden behind a **show** tick), a **timeout**, and a tick to accept a
  certificate that does not check out for a local server.
- The line under the fields shows the spec to paste into an applet, e.g.
  `http:home/api/states/sensor.outside#state`.
- **Test** reads the address with `g13-visuals --read`, the same reader the pad uses, so a test that
  passes here is a test that passes on the screen. Anything else would be a second opinion about
  how to fetch a web address, and the one thing worse than no test is a test that disagrees.
- The file holds the token, so it is written **readable only by you**. Any keys the window does not
  offer are kept when it saves, so hand-written `headers` survive being edited here.

```bash
g13-visuals --read 'http:home/api/states/sensor.outside#state'
```
reads one source from a terminal, exactly as an applet would see it, and says why when it is empty.

An applet is a JSON file, and it can be passed around, so this is where a capability is granted —
which is why it is a window and not a config key. Everything starts switched on, so an install
that never opens it behaves exactly as it did before the window existed. A kind that is switched
off reads as an **empty value** in every applet rather than failing, so nothing breaks and nothing
has to be restarted: the value simply stops arriving, and `g13-applet check FILE` is what tells you
which applet was asking for it (`it reads cmd ('cmd:date +%H:%M'), which is switched off in the
sources panel`). Before switching a kind back on for somebody else's applet, that is the check to
run first.

The window also shows a line of what is being read right now (`cpu 12%  memory 43%  time 21:14`),
straight from what the daemon published, so the switches have something visible behind them. From
a terminal the same thing is `g13-visuals --sources`, `--disable-source cmd`, `--enable-source cmd`,
and `--status` says when something is switched off. The file is `$XDG_CONFIG_HOME/g13/sources.json`,
holding a `disabled` list, and the daemon reads it every couple of seconds — a test checks that the
panel's list of kinds and the daemon's are the same list, because a switch the daemon did not know
would silently do nothing.

### The config tool's screen window

The **Screen…** button next to the profile selector opens it:

- a **live preview** of the 160x43 screen, rendered with the driver's own font, with any
  text that lands on ink highlighted in orange - on a one-colour panel that text is
  invisible, so it is shown as a problem rather than left as a trap;
- the **visuals list**: add and remove, reorder, choose which one is showing, and set the
  cycle timing;
- **start, stop and restart** for the daemon, with its state.

Everything applies as it is changed, because the daemon reads `visuals.json` and picks it
up within a second, so there is nothing to confirm.

The tool's screen model mirrors the daemon's, and a test renders the same layout on both
sides and compares the frames byte for byte - a preview that disagrees with the panel would
be worse than no preview at all.

Data sources are named in an applet's JSON: a plain name (`cpu`, `memory`, `time`, ...), or
`env:NAME`, `file:PATH`, `cmd:COMMAND`, `json:PATH#FIELD` and `http:<endpoint>/<path>#field` for
anything else. `http:` reads a web address, using the same `#field` convention as `json:`, and the
endpoint - address, headers, token, timeout - is a named entry in `endpoints.json` rather than
something written into the applet. Everything that can go wrong (no such endpoint, refused, timed
out, an error status, a body that is not JSON, a field that is not there) reads as empty, and an
address that keeps failing is left alone for increasing intervals so an unreachable endpoint cannot
stutter the screen. A whole `http://host/path#field` address is accepted too, for something public
that needs no credential. `json:`
reads one field of a JSON file - dotted for a nested field (`#route.turn`), a number for a
list index (`#waypoints.0`), and with no `#` a file holding a single number or string is the
value. A missing file, a missing field, unreadable JSON or a field that is not a scalar all
read as empty, so whatever produces the file can stop without disturbing the screen.
`json:` and `file:` are cached 0.2 s rather than the 2 s used for the rest, because a value
being watched live should not lag the thing producing it.

An applet fed by a running program can take the screen by itself: `"follow": {"seconds": 20}`
tells the daemon to watch the files behind that applet's own live sources. While one of them is
being written the applet is what is showing, and when the writing stops the screen goes back to
whatever was there before. It is keyed on the program *running*, not on a window being focused,
so alt-tabbing does not make the screen flicker, and a visual you pick by hand while it runs is
respected until the program goes away. Without that line an applet is only shown because
somebody chose it. The Cyberpunk applet is the one that uses it.

**Check an applet before it reaches the pad.** `g13-applet check FILE` (or `--all`) reads the
file, reports a widget type nothing draws, a field that is not the type it should be, a format
string that cannot be read, and a source name that is neither declared nor built in — then draws
it and reports text that lands on ink. Two extras matter when designing one: `--values` prints
what every source the applet asks for reads right now, and `--screen` draws the 160x43 screen as
ASCII art, `#` for ink, `.` where text sits and `!` where the two collide. The drawing and the ink
rule come from the daemon itself, so the tool cannot disagree with the pad, and `make test` checks
that the lists it argues from still match the code.

An applet can give its sources short names, so widgets and formats do not repeat a path -
`"sources": {"ammo": "json:~/hud.json#ammo"}`, then `{"type": "text", "format": "AMMO {ammo}"}`.

Widgets: `text` (with `align`: left, centre or right, and `"scroll": true` for a line longer
than the screen), `bar`, `segments` (a gauge drawn in blocks, `count` and `max`), `line`,
`vline`, `box`, `brackets` (corner marks), `arrow` (a filled arrow at any bearing, 0 up and
clockwise) and `turn` (a corner arrow: straight on, left, right or double back, as four
shapes rather than a slight rotation - at this size twenty degrees is one pixel).

## Record mode (the MR button)

Press **MR** on the pad while the config tool is open:

1. The tool comes to the front and the status line under the keybindings panel says to
   press the pad key you want to change.
2. Press that pad key. It is selected on the keypad, and the status asks for the key it
   should send.
3. Press the key on the keyboard. The binding is written and live within a second, and
   the status line confirms it.
4. **Esc** (or MR again) cancels at any point.

The tool has to have focus to see the key you press, which is why it comes to the
front. Pad presses reach it through the driver's event pipe at
`$XDG_RUNTIME_DIR/g13-events`, so the driver and the tool both need to be running.

While recording, the tool tells the driver to **suspend bindings** (`g13-ctl`), so the
press that selects the key to program does not fire that key's old binding — otherwise
the tool would capture that key as the one to map. The command is refreshed every couple
of seconds; if the tool is closed or crashes mid-recording the pad starts working again
by itself a few seconds later.

Mouse buttons and gamepad inputs cannot be recorded yet: the driver's virtual device
does not advertise those codes, so there is nothing to send.

Live Reload: The driver detects changes to the **currently active** bindings
file and reloads it within a second — no restart needed. Pressing a profile
button re-reads it as well. Changes made to a profile you are not currently on
take effect when you switch to it.

Active profile: The last selected profile is stored in
`~/.config/g13/active-profile` (a plain integer, 0-3) so the driver comes back
on it after a restart or a replug. Delete the file to fall back to profile 0.

![Config Tool Screenshot](docs/ConfigTool.png)

The M1, M2, M3 and MR buttons select the bindings.

### Use the built-in Mapping Set (for external tools)

The driver now includes a fixed default mapping. This means the GUI is not strictly necessary if you prefer other tools. You can map the keys using software like **Input Remapper**.

*(Note: the quick profile change via the four profile buttons only works when using the G13 GUI tool.)*

### Manually create your own Mapping Set

If you don't want to use the GUI App, you can edit the files manually in `~/.config/g13/`.

* **Usage Example:** To map the **G20** key to the letter **T**, find the event code for T (which is 20). Then, in your `bindings-0.properties` file, add or edit the line:
    ```ini
    G20=p,k.20
    ```

### Binding types

| Value | Meaning |
|-------|---------|
| `p,k.<code>` | Pass through a single key. `<code>` is the Linux event code (see `docs/Eventcodes_for_Mapping.pdf`). |
| `m,<macroId>,<repeats>` | Play macro `<macroId>` from `~/.config/g13/macro-<macroId>.properties`; `<repeats>` is `0` or `1`. |
| `mk,<index>` | Send the M key code for `<index>`: `0`=M1 (`KEY_MACRO_PRESET1`), `1`=M2, `2`=M3, `3`=MR (`KEY_MACRO_RECORD_START`). |
| `x` | Do nothing at all. Only meaningful for the M buttons, whose default (no entry) is to switch profile (M1-M3) or send the macro record event (MR). |

A key with no entry at all sends nothing. Removing a line takes effect within a
second if that profile is the active one.

**Overriding the M buttons:** by default M1/M2/M3/MR switch binding profiles. Bind
one of them in a profile file and that binding wins *for as long as that profile is
active*, e.g.:

```ini
G30=mk,1     # while this profile is active, M2 sends KEY_MACRO_PRESET2 instead of
             # switching to bindings-1.properties
```

Handy for passing the M buttons through to games or for layering your own
behaviour on top of the profiles. In the GUI this is the *M Buttons (this profile)*
section, and any key can be given an M code via the *M Key* button type.

### Using the Display (scripting)

You can write text to the display using a simple pipe command:

The driver creates a Named Pipe (FIFO) to receive text for the LCD.

Location: `/run/user/$UID/g13-lcd` (Check `/tmp/g13-lcd` as fallback if `/run` is unavailable).

```bash
# Find your pipe path (usually based on your user ID, e.g., 1000)
PIPE="/run/user/$(id -u)/g13-lcd"

# Send simple text
echo "Hello World!" > $PIPE

# Send multi-line text (CPU/RAM stats)
echo -e "CPU: 50%\nRAM: 4GB" > $PIPE
```

Currently, only one font size is implemented. There is an example script for system monitoring in the `scripts` folder. Feel free to try it out, modify it, or share your own scripts!


### Uninstallation

Remove the driver and everything its install put on your system:

```bash
cd g13-driver/src && make uninstall-user     # the user-local install (Option B)
make uninstall                               # the system-wide install (Option A, as root)
make uninstall-udev                          # the udev rule in /etc (needs sudo)
```

`uninstall-user` stops and disables the three services, then removes the binaries, the SDK
library and header, the service files and the menu entries. **Your configuration in
`~/.config/g13` is kept** — bindings, macros, visuals and applets all survive, so reinstalling
changes nothing you set up. Delete that directory by hand if you want it gone as well.

The Cyberpunk 2077 mod is not part of the driver's own install — it writes inside a game
directory, so it is installed and removed on its own:

```bash
./g13-cet-mod/install.sh --remove
```


## Testing

Everything lives in `tests/` and runs without the device, the daemon or your configuration:
each suite gets its own scratch directories, and the ones that need a screen endpoint use a
regular file where the driver would have a FIFO.

```bash
make test
```

That covers the Logitech LCD SDK (the native library, and the Windows path through the
bridge), the visuals daemon (applets, the menu, the rule that keeps text off filled pixels,
and who owns the buttons), the tool's screen model, and a cross-check that the tool and the
daemon render **the same pixels** - a preview that disagrees with the panel would be worse
than no preview at all.


## What is not done yet

Stated here rather than discovered later:

* **No game has driven the panel under Proton yet.** The Windows half is proven as far as it
  can be without Wine: the library's own code path is built for Linux and tested through the
  bridge, and the DLL's exports are checked, but a real game is untried.
* **One SDK client at a time.** Logitech's LCD Manager rotated between applets; here two
  programs calling `LogiLcdUpdate()` would fight over the screen.
* **No applet designer.** Applets are JSON files you write, and the Screen window previews
  them; there is no point-and-click designer.
* **Mouse buttons and gamepads cannot be recorded** with MR: the driver's virtual device
  advertises neither, so that needs driver work first.


## Notes

* Tested on 64-bit Arch Linux.