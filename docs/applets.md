# Applets, the designer, sources and variables

Everything the pad can show beyond the built-in visuals, written as tasks: what you want to do,
the steps, what you should see, and what to do when it is wrong.

You do not have to write JSON. The **designer** edits an applet's file for you, and it writes
every change as you make it — there is no Apply button and nothing to lose by closing the window.
But an applet *is* a JSON file, so this guide also shows the file, and you can edit one by hand
with any text editor. Both ways are first-class.

**Contents**

- Part 1 — Tutorials
  1. [Show something from this machine](#1-show-something-from-this-machine)
  2. [Give an applet a source of its own](#2-give-an-applet-a-source-of-its-own)
  3. [Show a value a program writes](#3-show-a-value-a-program-writes)
  4. [Show one number out of a file that has more in it](#4-show-one-number-out-of-a-file-that-has-more-in-it)
  5. [Show the time, the date, the music, the profile](#5-show-the-time-the-date-the-music-the-profile)
  6. [Show what a shell command says](#6-show-what-a-shell-command-says)
  7. [Show a web address](#7-show-a-web-address)
  8. [Show something only while a program is running](#8-show-something-only-while-a-program-is-running)
  9. [Lay it out so it can be read](#9-lay-it-out-so-it-can-be-read)
  10. [Change an applet that comes with the driver](#10-change-an-applet-that-comes-with-the-driver)
  11. [Write an applet by hand](#11-write-an-applet-by-hand)
  12. [Install somebody else's applet](#12-install-somebody-elses-applet)
  13. [Check an applet before you trust it](#13-check-an-applet-before-you-trust-it)
  14. [Stop applets reading something](#14-stop-applets-reading-something)
  15. [Add a web address and its token](#15-add-a-web-address-and-its-token)
  16. [A value is blank — find out why](#16-a-value-is-blank--find-out-why)
  17. [Show something a program can only print](#17-show-something-a-program-can-only-print)
  18. [Show what an MQTT topic says](#18-show-what-an-mqtt-topic-says)
  19. [Show what a web socket says](#19-show-what-a-web-socket-says)
  20. [Show what a mailbox says](#20-show-what-a-mailbox-says)
- Part 2 — Reference
  - [The applet file](#the-applet-file)
  - [The variables](#the-variables)
  - [The kinds of source](#the-kinds-of-source)
  - [The widgets and their fields](#the-widgets-and-their-fields)
  - [Formats: the bit inside { and }](#formats-the-bit-inside--and-)
  - [The commands](#the-commands)
  - [Where everything lives](#where-everything-lives)

---

# Part 1 — Tutorials

## 1. Show something from this machine

**What you want:** the pad showing how busy the processor is.

**Steps**

1. Open the config tool → **Screen…** → **New applet…**, and give it a name: lowercase letters,
   digits and hyphens, say `first`. The designer opens, and the pad switches to that applet while
   the window is open — you are editing what you can see.
2. It starts with one text widget. In **the selected widget** tab, set:

   | field | value |
   |---|---|
   | `x` | `3` |
   | `y` | `12` |
   | `format` | `CPU {cpu:.0f}%` |

   Everything is saved as you type. The pad shows `CPU 12%`, and the title bar of the applet is the
   `title` field at the top of the window.

3. Open the **what it can read** tab: every name you may use is listed, with what it is. Double-click
   one to add it to the selected widget.

**The same thing as a file** — `~/.config/g13/applets/first.json`:

```json
{
  "name": "first",
  "title": "FIRST",
  "border": true,
  "interval": 1,
  "sources": {},
  "widgets": [
    {"type": "text", "x": 3, "y": 12, "format": "CPU {cpu:.0f}%"},
    {"type": "text", "x": 3, "y": 20, "format": "MEM {memory:.0f}%"},
    {"type": "text", "x": 3, "y": 30, "format": "{day} {date} {time}"}
  ]
}
```

**You should see** — `g13-applet check ~/.config/g13/applets/first.json --values`:

```
first.json: ok
  what it reads now:
    cpu              cpu                                                      0.0
    date             date                                                     '14/09/2026'
    day              day                                                      'Mon'
    memory           memory                                                   49.123830435828744
    time             time                                                     '12:35'
```

`--values` is the window's **Read them now** button: it prints what each name the applet asks for
reads at this moment.

**If it is wrong**

- The pad still shows the old thing: the applet has to be in the pad's list. **Screen…** →
  select it in **Available** → **Add →**.
- The value reads `0.0` or looks odd: that is the real reading, taken once a second.
- Nothing at all: the applet is the one *showing*, but the pad also cycles through the others if
  cycling is on. See **Show now** in the Screen window.

## 2. Give an applet a source of its own

**What you want:** to name a value once and use it in several widgets, and to read something the
built-in list does not cover.

**Steps**

1. In the designer, open the **what it can read** tab.
2. The box at the bottom holds this applet's own sources, one `name = spec` per line. Type:

   ```
   cores = cmd:nproc
   me = env:USER
   ```

3. Those names now appear in the list above, and can be used in any widget: `{cores}`, `{me}`.

**The same thing as a file** — the applet's `sources` block:

```json
"sources": {
  "cores": "cmd:nproc",
  "me":    "env:USER"
}
```

A source is **not** registered anywhere: it is a name the applet chooses, kept in the applet's own
file, so an applet you pass to somebody else carries its names with it. What the *Sources* window
controls is which **kinds** of source an applet may read at all (shape `kind:thing`) — see
[Stop applets reading something](#14-stop-applets-reading-something).

**You should see** — check --values:

```
    cores            cmd:nproc                                                '12'
    me               env:USER                                                 'nathan'
```

**If it is wrong**

- `... is not an alias, not built in, and has no kind env/file/json/cmd/http` — a name in a
  `format` that is not one of the variables and not one of your own sources. Either add it as a
  source or fix the spelling.
- An `env:` source is blank: the daemon is a user *service*, so it sees systemd's environment, not
  your shell's. `systemctl --user set-environment G13_HOME=/somewhere`, then restart it. This is
  the one kind that is not a file, so the daemon cannot notice a change on its own.

## 3. Show a value a program writes

**What you want:** a game, a script or a service to put numbers on the pad. This is how the
Cyberpunk 2077 HUD works.

**Steps**

1. Whatever produces the values writes a small JSON file, somewhere it can write:

   ```bash
   echo '{"health": 165, "ammo": 42, "objective": "Call Evelyn."}' > /tmp/hud.json
   ```

2. In the designer's sources box, one line per value:

   ```
   health = json:/tmp/hud.json#health
   ammo = json:/tmp/hud.json#ammo
   objective = json:/tmp/hud.json#objective
   ```

3. Use them: a text widget with `AMMO {ammo}`, another with `{objective}`, and a **segments**
   widget with `source` = `health`, `max` = `200`, `count` = `10` for a block gauge.

`#field` walks into the JSON: `#hud.ammo` for a nested object, `#route.0` for the first item of a
list. With no `#` the whole file is the value, if it is a single number, string or boolean.

**You should see**

```
game.json: ok
  what it reads now:
    ammo             json:/tmp/guidescratch/hud.json#ammo                     42
    health           json:/tmp/guidescratch/hud.json#health                   165
    objective        json:/tmp/guidescratch/hud.json#objective                'Call Evelyn.'
```

**If it is wrong**

- Blank, and the file is definitely written: the field name. `json:` is exact — `#Health` is not
  `#health`.
- Blank and the file is *not* written yet: correct. A missing file, unreadable JSON, a missing
  field and a field of the wrong type all read as **empty**, so the screen keeps drawing instead of
  breaking. That is also how an applet behaves while the game is not running.
- Lagging behind: `json:` is re-read every 0.2 s, which is as live as anything here gets.

## 4. Show one number out of a file that has more in it

**What you want:** the system load, which lives in `/proc/loadavg`.

**The mistake everyone makes first** — `file:` gives the file's first **line**, not its first word:

```
"load": "file:/proc/loadavg"
```

```
load-wrong.json: 2 problems
    text off the visible screen: 'load 0.74 0.58 0.68 4/3050 994880' runs to x=201
    'load 0.74 0.58 0.68 4/3050 994880' sits on 7 filled pixels, from (159, 12) to (159, 18)
```

Two problems, both real: the line runs off the 160-pixel screen, and it lands on the frame. The
checker tells you both before the pad does.

**The fix** — take the one number you want with a command:

```
"load": "cmd:cut -d' ' -f1 /proc/loadavg"
```

```
machine.json: ok
  what it reads now:
    cores            cmd:nproc                                                '12'
    load             cmd:cut -d' ' -f1 /proc/loadavg                          '0.86'
    me               env:USER                                                 'nathan'
```

**If it is wrong**

- The applet does not appear on the pad at all, and the daemon's log says
  `skipping applet ... JSONDecodeError`: the file is not valid JSON. A shell command with quotes in
  it is the usual cause — inside JSON, prefer **single** quotes in the command:
  `"cmd:cut -d' ' -f1 /proc/loadavg"`. Run `g13-applet check FILE` and it tells you the line.
- The value is the whole line: use `cmd:` as above, or `file:` on a file that holds one value.

## 5. Show the time, the date, the music, the profile

**What you want:** the obvious things, without any setup.

**Steps:** use the names directly. `{time}`, `{date}`, `{day}`, `{uptime}`, `{media_title}`,
`{profile}`, `{recording}`, `{last_key}`… The full list, with what each one is, is in
[The variables](#the-variables), and the designer lists them in **what it can read**.

**You should see** the value, or an empty space when there is nothing to report — `{media_title}`
with no player running is blank rather than an error.

## 6. Show what a shell command says

**What you want:** something a command can tell you: cores, a disk's free space, a git branch.

**Steps:** add a source:

```
cores = cmd:nproc
disk  = cmd:df -h / | tail -1 | cut -d' ' -f13
```

**Rules worth knowing**

- The command runs with a **0.4 s** timeout and is re-read at most every **2 s**, so it is not the
  right tool for anything fast-moving. For that, have the program write a JSON file and use `json:`
  (0.2 s).
- It runs as the daemon, so it sees the daemon's `PATH` and environment, not your login shell's.
- Its output is used as it comes, trimmed. A command that fails or prints nothing reads as empty.

**If it is wrong:** run the command in a terminal first. Then
`g13-visuals --read "cmd:nproc"` reads it exactly as the pad would.

## 7. Show a web address

**What you want:** a value from a Home Assistant, a router, a weather API — anything with an HTTP
address. The address and its token go in one place; the applet only names it.

**Steps**

1. **Sources…** → **Endpoints** tab → **Add…** → name it `ha`.
2. Fill in the fields: **address** `http://homeassistant.local:8123`, **token** (for Home
   Assistant: your profile → *Long-lived access tokens* → Create). The token is sent as
   `Authorization: Bearer …` on every request. For a local server with a self-signed certificate,
   tick the box for that.
3. Type the path you want to read in the **read** box, say `api/states/sensor.outside#state`, and
   press **Test**. It reads it with `g13-visuals --read`, the same reader the pad uses.
4. The line under the fields shows the spec to paste into an applet:

   ```
   temp = http:ha/api/states/sensor.outside#state
   ```

**You should see**

```
weather.json: ok
  what it reads now:
    temp             http:home/state.json#sensor.temperature                  21.4
```

and from a terminal, the same thing:

```bash
$ g13-visuals --read 'http:home/state.json#sensor.temperature'
  http:home/state.json#sensor.temperature
  -> 21.4
```

**Why it is shaped like this:** a token in the endpoint, never in the applet. A spec is
`http:<endpoint>/<path>#<field>`, so an applet you share carries `http:ha/...` and no credential.
The file holding the token is written readable only by you.

**If it is wrong**

- `no endpoint called 'ha' ...` — the name in the spec and the name in the Endpoints tab differ.
- Empty, and Test works: the kind `http` may be switched off in **Kinds**. A switched-off kind reads
  as empty in every applet rather than failing.
- Empty and slow: the wait is deliberately short (0.5 s by default, or the endpoint's own
  `timeout`), because the daemon draws the screen on one thread. An address that keeps failing is
  left alone for increasing intervals, so an unreachable server cannot make the pad stutter.
- A whole address with no endpoint works too, for something public: `http://wttr.in/?format=%t`.

## 8. Show something only while a program is running

**What you want:** the game's HUD while the game runs, and the clock back afterwards — without
pressing anything.

**Steps:** add to the applet's file:

```json
"follow": {"seconds": 20}
```

**What happens:** while any file behind that applet's live sources is being written, the applet is
what shows. When the writing stops, the screen goes back to what it was after that many seconds.
The trigger is the file changing, not which window has focus, so alt-tabbing away does not flicker
it off.

**If it is wrong**

- It does not appear when the game starts: the applet has to be in the pad's list (**Add →**).
- It appears but never leaves: the writer is still writing (a heartbeat in the file is enough), or
  `seconds` is much larger than you meant.

## 9. Lay it out so it can be read

**The rule:** the LCD has one colour of ink. **Text drawn over filled pixels is invisible.** Every
widget's text has to sit on blank pixels, and the tools enforce it everywhere — the preview marks
text-on-ink in **orange**, and `g13-applet check` counts the pixels:

```
wrong.json: 1 problem
    'over the box' sits on 504 filled pixels, from (30, 12) to (101, 18)
```

**Steps**

- The screen is **160 × 43** visible (a 160 × 48 framebuffer; the bottom rows are not on the glass).
  Row 0 and the last row belong to the frame when `border` is on, and the title sits on row 2.
- Put text at `y` = 12, 20, 30 …, and bars/boxes where no text is.
- `align` = `right` pins text to the right edge using its real rendered width, so a value can grow
  without you knowing how wide it will be. `centre` does the same for the middle.
- Watch the preview in the designer: orange means it will be unreadable on the pad.

**If it is wrong:** run `g13-applet check FILE --screen` and look. `#` is ink, `.` is text, `!` is
text landing on ink.

## 10. Change an applet that comes with the driver

**Steps:** **Screen…** → pick it in **Available** (they are the `applet:` entries) → **Design…** →
change it. Your edits land in `~/.config/g13/applets/`, never in the repository.

The two that ship are `demo-stats` (the worked example: CPU, memory, uptime) and `cp2077-hud`.
Note that the *file* name and the applet's own `name` can differ — the shipped example's file is
`example-stats.json` and it calls itself `demo-stats`, which is why the Screen window shows both.

## 11. Write an applet by hand

**Steps:** a JSON file in `~/.config/g13/applets/` whose name ends `.json`, with a `name`, a
`title`, and `widgets`. See [The applet file](#the-applet-file) for every key. The daemon notices
the file within a second — no restart — and it appears in the Screen window's **Available** list.

**You should see** `g13-visuals --list` including it:

```
    applet:first           FIRST                    on
```

**If it is wrong:** `g13-applet check FILE`. It reports structural problems, layout problems, and a
source you have switched off, and it never leaves you guessing.

## 12. Install somebody else's applet

**Steps**

1. Copy the `.json` file into `~/.config/g13/applets/`.
2. **Check it first** — an applet is a JSON file, and reading it is all it takes to know what it
   does:

   ```bash
   g13-applet check /path/to/it.json --values
   ```

   `--values` lists every source it wants and what each reads now. An applet reading
   `cmd:curl ... | sh` or a file you would rather it did not have is visible right there.
3. **Screen…** → **Available** → **Add →**.

**What the Sources window is for:** it is the permission model. An applet may only read the kinds
you have switched on, so a kind you have turned off reads as empty for every applet, including
somebody else's. If you are unsure about an applet, that is the switch to reach for while you read
it.

## 13. Check an applet before you trust it

```bash
g13-applet check FILE              # problems, or "ok"
g13-applet check FILE --values     # plus what each source reads right now
g13-applet check FILE --screen     # plus the drawn screen: # ink, . text, ! text on ink
g13-applet check FILE --json       # the same problems, for a script
g13-applet check --all             # every applet in the applets directory
```

Exit codes: **0** clean, **1** problems, **2** it could not run (a missing file, a bad argument).
`--json` reports exactly the same problems as the human output — the layout half included — so a
build script cannot be told a broken applet is fine.

## 14. Stop applets reading something

**Steps:** **Sources…** → **Kinds** → untick. That is the whole interface. Everything starts
switched **on**, so an install that never opens this window behaves as it always did.

A switched-off kind reads as an **empty value** in every applet, so nothing breaks and nothing has
to be restarted — the value simply stops arriving, and the pad keeps drawing. `g13-applet check`
names the applet that wanted it:

```
it reads cmd ('cmd:date +%H:%M'), which is switched off in the sources panel
```

**From a terminal:** `g13-visuals --sources` lists every kind and whether it is on;
`g13-visuals --disable-source cmd` and `--enable-source cmd` do the same as the ticks.

## 15. Add a web address and its token

**Steps:** **Sources…** → **Endpoints** → **Add…** → a name → **address**, **token**, **timeout**,
and the certificate tick if a local server needs it → **Test**.

The file is `~/.config/g13/endpoints.json`, written readable only by you, and re-read by the daemon
within a second. An endpoint is a name, its address and its credential in one place:

```json
{
  "ha":     {"url": "http://homeassistant.local:8123", "token": "…"},
  "router": {"url": "https://192.168.1.1", "insecure": true},
  "wttr":   {"url": "https://wttr.in"}
}
```

Keys the window does not offer — `headers`, say — are kept when it saves, so editing here cannot
lose them. Anything that can go wrong (no such endpoint, refused, timed out, an error status, a body
that is not JSON, a field that is not there) reads as empty rather than raising an error into the
screen.

## 16. A value is blank — find out why

In this order, and each step tells you more than the last:

1. **`g13-applet check FILE --values`** — every source the applet asks for, and what it reads now.
   This answers most of it, and names a switched-off kind and a name that is not an alias.
2. **`g13-visuals --read 'SPEC'`** — one source, read the way the pad reads it, with a reason when
   it is empty (an endpoint that is not in `endpoints.json`, a switched-off kind).
3. **`g13-visuals --status`** — is the daemon running, what is showing, who owns the screen.
4. **The Screen window** — is the applet in the list, and is it the one showing?
5. **The daemon's log** — the place an applet that cannot be read is named:

   ```bash
   journalctl --user -u g13-visuals -f
   ```

| what you see | what it means |
|---|---|
| a blank value, everything else fine | the source is legitimately empty: no game running, no media player, a missing file |
| `is not an alias, not built in, and has no kind ...` | a name in a `format` that nothing provides — add it as a source |
| `which is switched off in the sources panel` | the **Kinds** tab; tick it back on |
| `no endpoint called 'x'` | the endpoint's name in the Endpoints tab differs from the spec |
| `skipping applet ... JSONDecodeError` | the file is not valid JSON — usually a quote inside a `cmd:` spec; `check` gives the line |
| nothing changes when you edit the file | the applet is not in the pad's list, or the pad is showing another visual |
| the applet is missing from the screen list | a file not ending in `.json`, or invalid JSON (the log says which) |

---

## 17. Show something a program can only print

A game script or a shell loop cannot always write you a file of fields, but it can **print** a line.
`regex:` reads a pattern out of a text file and gives the **last** match, so a file that grows keeps
showing the newest reading. This is how a game that can only log gets onto the pad.

1. Make a file the way such a program would:

   ```bash
   printf 'hp=80\nammo=12\nhp=63\n' > /tmp/game.log
   ```

2. Read one field out of it:

   ```bash
   $ g13-visuals --read 'regex:/tmp/game.log#hp=(\d+)'
     regex:/tmp/game.log#hp=(\d+)
     -> '63'
   ```

   `63`, not `80` — the last match wins. **Single quotes matter**: without them the shell eats the
   backslash and the pattern stops matching.

3. In an applet: add the source, then use it like any other value.

   ```json
   "sources": { "hp": "regex:/tmp/game.log#hp=(\d+)" },
   "widgets": [ { "type": "text", "x": 3, "y": 12, "format": "HP: {hp}" } ]
   ```

The value is the pattern's first capture group, or the whole match when it has no group. Only the
last 64 KB is read, so a log can grow without limit. If the file is missing, the pattern is invalid,
or nothing matches, the value is **empty** and the screen keeps drawing — `g13-applet check --values`
says which of those it was.

## 18. Show what an MQTT topic says

If a device, a home-automation system or a script publishes to a broker, `mqtt:` shows the **last
message** on a topic — no polling, no script, and it works for a broker on the other side of the house.

1. Put the broker in `~/.config/g13/endpoints.json`, with any username and password **next to it**:

   ```json
   { "home": { "url": "mqtt://homeassistant.local:1883", "user": "g13", "token": "…" } }
   ```

   `mqtts://` is the same thing over TLS.

2. Read a topic, then ask for a field if the payload is JSON:

   ```bash
   $ g13-visuals --read 'mqtt:home/sensors/kitchen#temperature'
     mqtt:home/sensors/kitchen#temperature
     -> '21.5'
   ```

3. Use it in an applet: `temp = mqtt:home/sensors/kitchen#temperature`, then `format: "{temp}C"`.

The first reading after a topic is first asked for is blank for a fraction of a second: applets
subscribe lazily, so only topics something actually uses are subscribed to. Nothing here ever waits
for the broker — a broker that is down leaves the value blank and the screen keeps drawing, which is
also why the pad never stutters because a sensor went away.

## 19. Show what a web socket says

Some things have no HTTP endpoint and no broker: they open a web socket and push. That is the only
way to reach obs-websocket, Home Assistant's event stream, or most live feeds.

1. Put the address in `~/.config/g13/endpoints.json`. Two optional fields do the work a web socket
   protocol would otherwise do for you: `subscribe` is sent as soon as the socket opens, and `ping`
   every few seconds if the server expects to hear from you.

   ```json
   {
     "obs": {
       "url": "ws://127.0.0.1:4455",
       "subscribe": "{\"op\": 1, \"d\": {\"rpcVersion\": 1, \"authentication\": \"…\"}}",
       "ping": ""
     }
   }
   ```

   `wss://` is the same over TLS. Anything secret belongs here, never in an applet.

2. Read a field of the last message; with no `#field` you get the whole message:

   ```bash
   $ g13-visuals --read 'ws:obs#d.settings.fps'
     ws:obs#d.settings.fps
     -> 60
   ```

3. In an applet: `fps = ws:obs#d.settings.fps`, then `format: "{fps} fps"`.

Like MQTT, the socket is opened when an applet first asks for it and the last message is kept, so a
value appears a fraction of a second after it arrives, and a server that is down leaves the value
blank instead of holding up the screen.

## 20. Show what a mailbox says

`imap:` reads a mailbox directly — no mail client installed, nothing marked read.

1. Put the server, the login and the password in `~/.config/g13/endpoints.json`. A password made for
   this purpose (an app password) is better than your account password:

   ```json
   { "mail": { "url": "imaps://imap.purelymail.com:993", "user": "me@example.com", "token": "…" } }
   ```

   `imaps://` is TLS; plain `imap://` is only for a server you reach over something already private.

2. Read a count, or the newest message:

   ```bash
   $ g13-visuals --read 'imap:mail/INBOX#unread'
     imap:mail/INBOX#unread
     -> 7
   $ g13-visuals --read 'imap:mail/INBOX#line'
     imap:mail/INBOX#line
     -> 'Alice Example - Hello world'
   ```

   Fields: `unread`, `total`, and `from`, `subject`, `date` or `line` for the newest message. A
   MIME-encoded subject is decoded, so an accent or a name in another script looks right.

3. In an applet: `mail = imap:mail/INBOX#unread`, then `format: "mail {mail}"`.

Two things worth knowing: the mailbox is opened **read-only**, so nothing here ever marks a message
as read, and the answer is refreshed once a **minute** rather than every second — a mail server is
not a file and should not be asked that often. Until the endpoint is configured, or if the server
cannot be reached, the value is blank.

# Part 2 — Reference

## The applet file

One JSON object. Every key is optional except `name` and `widgets`.

| key | what it is |
|---|---|
| `name` | the applet's name, used as `applet:<name>` and shown in the Screen window |
| `title` | the text on the top row, at most 25 characters; uppercased by convention |
| `border` | draw the frame and the title (default true) |
| `interval` | how often to redraw, in seconds (default 1; 0.2 is the floor) |
| `follow` | `{"seconds": 20}` — take the screen while the applet's live sources are being written, and give it back this many seconds after they stop |
| `sources` | this applet's own names: `{"ammo": "json:/tmp/hud.json#ammo"}` |
| `widgets` | the things drawn, in order (see the widget table) |

The file's name and the applet's `name` can differ. The Screen window shows both when they do.

## The variables

The names that need no setup. They are also listed in the designer's **what it can read** tab, and
a test compares that list with the daemon's, so the two cannot drift apart.

| name | what it reads | example |
|---|---|---|
| `cpu` | processor busy, as a percentage | `12.4` |
| `memory` | memory in use, as a percentage | `49.1` |
| `load` | the one-minute load average | `0.86` |
| `uptime` | time since boot, as hours and minutes | `200h13m` |
| `uptime_seconds` | the same in seconds | `720780` |
| `time` | the time | `21:14` |
| `time_seconds` | the time with seconds | `21:14:07` |
| `date` | today's date | `14/09/2026` |
| `day` | the day of the week | `Mon` |
| `media_status` | `Playing`, `Paused`, `Stopped` | `Playing` |
| `media_artist` | who is playing | `The Prodigy` |
| `media_title` | what is playing | `Firestarter` |
| `media_position` | how far into the track | `1:07` |
| `media_duration` | how long the track is | `4:42` |
| `media_percent` | how far through, as a number | `23.7` |
| `profile` | the selected profile | `M1` |
| `recording` | is record mode on | `yes` |
| `last_key` | the last key pressed, as an evdev code | `30` |
| `recent_keys` | the last few keys | `30 31 32` |
| `screen_width` | the screen's width in pixels | `160` |
| `screen_height` | the visible height | `43` |

Everything else comes from a source of your own — see the kinds below.

## The kinds of source

A spec is `kind:thing`, or one of the variable names above. Which kinds an applet may use is what
the **Kinds** tab switches.

| kind | what it reads | example | how often |
|---|---|---|---|
| *(none)* | one of the variables | `cpu` | 2 s |
| `env:` | an environment variable of the daemon | `env:USER` | 2 s |
| `file:` | the first **line** of a file | `file:/proc/loadavg` | 0.2 s |
| `json:` | one field of a JSON file | `json:/tmp/hud.json#ammo` | 0.2 s |
| `cmd:` | a shell command's output | `cmd:nproc` | 2 s, 0.4 s to run |

A `cmd:` that ends with a **non-zero** exit status reads as empty, so a one-liner that ends with a
test, a grep or a loop whose last iteration failed shows nothing even though it printed the right
thing. End it with `; true` (or `; :`) and it will. It also has 0.4 seconds: fine for `nproc`, fatal
for anything that touches the network - that is what `http:`, `mqtt:` and `ws:` are for.
| `http:` | a value from a web address | `http:ha/api/states/sensor.x#state` | 2 s, 0.5 s to fetch |
| `regex:` | the last match of a pattern in a text file | `regex:/tmp/game.log#hp=(\d+)` | 0.2 s |
| `mqtt:` | the last message on a topic | `mqtt:home/sensors/kitchen#state` | instant (pushed) |
| `ws:` | the last message from a web socket | `ws:obs#d.settings` | instant (pushed) |
| `imap:` | a mailbox's unread count, or the newest sender | `imap:mail/INBOX#unread` | 60 s |

`json:` and `http:` walk into their data with `#field`: `#hud.ammo` for a nested object, `#route.0`
for the first item of a list. Anything missing or unreadable reads as **empty** — never an error on
the screen.

`regex:` takes `#<pattern>`, not a field: the pattern is a regular expression and the value is the
last match in the file, which is what makes a log that grows show the newest reading.

## The widgets and their fields

Every widget has `type`, `x` and `y`. These are the fields each type also uses.

| type | fields | what it draws |
|---|---|---|
| `text` | `format`, `source`, `align`, `margin`, `scroll`, `scroll_width`, `scroll_speed` | a line of text; `format` is a template over all the values |
| `bar` | `source`, `max`, `w`, `h` | a filled bar for one value |
| `segments` | `source`, `max`, `w`, `h`, `count` | the same, in blocks |
| `brackets` | `w`, `h`, `len`, `thick` | corner marks |
| `arrow` | `source`, `r` | a filled arrow at a bearing: 0 is up, 90 right |
| `turn` | `source`, `size` | an arrow bent left or right |
| `line` | `w` | a horizontal rule |
| `vline` | `h` | a vertical rule |
| `box` | `w`, `h` | an outline |

`align` is `left`, `centre` or `right` — computed from the rendered text, so a value can be pinned
to the right edge without the design knowing how wide it will be. `scroll` (with `scroll_width`
and `scroll_speed`) scrolls a `format` that is longer than the space it has; anything longer than
the screen must be inside a `box` with `scroll` on it.

## Formats: the bit inside { and }

`format` is a Python `str.format` template over every value at once, so one line can show several:

| you write | you get | why |
|---|---|---|
| `CPU {cpu:.0f}%` | `CPU 12%` | `.0f` rounds to a whole number |
| `{memory:>3.0f}%` | ` 49%` | `>3` pads to three characters, right-aligned |
| `{day} {date}` | `Mon 14/09/2026` | two values, one line |
| `AMMO {ammo:>3}` | `AMMO  42` | a width, so the line does not jump as the value grows |
| `{uptime}` | `200h13m` | already formatted by the daemon |

A value that has not arrived is empty, so `AMMO {ammo}` with nothing there reads `AMMO `. A
`format` that cannot be read at all shows `?`, which is deliberate: it means *your template is
wrong*, not *the data is missing*.

## The commands

### `g13-applet` — check a design before the pad shows it

| command | what it does |
|---|---|
| `check FILE [FILE …]` | reports problems: structure, layout, and a source you have switched off |
| `check FILE --values` | also prints what each source the applet asks for reads right now |
| `check FILE --screen` | also draws the screen: `#` ink, `.` text, `!` text on ink |
| `check FILE --json` | the same problems as JSON, for a build script |
| `check --all` | every applet in the applets directory |

Exit codes: **0** clean, **1** problems, **2** could not run.

### `g13-visuals` — the screen's visuals

| command | what it does |
|---|---|
| `--list` | what can be shown, and what is switched on |
| `--select NAME` | show this one now, switching it on if it is off |
| `--enable NAME` / `--disable NAME` | add or remove it from the ones the pad cycles through |
| `--status` | what is showing, who owns the screen, whether the daemon is running |
| `--sources` | what data applets may read, and which kinds are switched off |
| `--enable-source KIND` / `--disable-source KIND` | the **Kinds** tab, from a terminal |
| `--read SPEC` | read one source exactly as an applet would see it |

`NAME` is a visual (`clock`, `system`, `media`, `pad`, `custom`), an applet (`applet:cp2077-hud` or
just `cp2077-hud`), a title as it appears on the pad, or a file name.

### `g13-service`, `g13-gui`, `g13-lcd`, `g13-watch`, `g13-buttons`

See the README: **Controlling the Driver**, **The LCD screen as a display**, and
**The event bus**.

## Where everything lives

| path | what it is |
|---|---|
| `~/.config/g13/applets/*.json` | your applets — one file each, edited by the designer or by hand |
| `~/.config/g13/visuals.json` | what the pad shows, in order, and which is showing |
| `~/.config/g13/sources.json` | which kinds of source are switched off |
| `~/.config/g13/endpoints.json` | named web addresses and their tokens (readable only by you) |
| `~/.config/g13/button-mode` | who owns the screen and the four buttons: `auto`, `visuals`, `sdk` |
| `$XDG_RUNTIME_DIR/g13-screen` | the frame the daemon last drew, which is what the previews show |
| `$XDG_RUNTIME_DIR/g13-values.json` | the values the daemon publishes for other programs |

Every one of those files is read within about a second of being written, so nothing here needs a
restart: edit, save, look at the pad.
