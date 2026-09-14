"""Tests for g13-visuals: the button behaviour, the menu, and the layout rule.

Drives the engine directly with a fake clock - no device, no sockets.
"""
import importlib.machinery
import importlib.util
import json
import math
import os
import glob
import sys
import time

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DAEMON = os.path.join(REPO, "g13-visuals", "g13-visuals")
SCRIPTS = os.path.join(REPO, "g13-driver", "src", "scripts")
sys.path.insert(0, SCRIPTS)

loader = importlib.machinery.SourceFileLoader("g13visuals", DAEMON)
spec = importlib.util.spec_from_loader("g13visuals", loader)
gv = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gv)

fail = 0


def check(what, expected, actual):
    global fail
    ok = expected == actual
    if not ok:
        fail += 1
    print("%-50s %-22s %s" % (what, "-> " + repr(actual), "ok" if ok else "FAIL (expected %r)" % (expected,)))


class FakeClock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now

    def advance(self, seconds):
        self.now += seconds


def engine(clock=None):
    return gv.Engine({"enabled": ["clock", "system", "media"], "active": "clock"},
                     clock=clock or FakeClock())


# --- a tap moves to the next visual, a hold opens the menu ---
clock = FakeClock()
e = engine(clock)
check("starts on the configured visual", "clock", e.active)
e.on_key(gv.MENU_BUTTON, True)
clock.advance(0.1)
e.on_key(gv.MENU_BUTTON, False)
check("short press moves on", "system", e.active)

e.on_key(gv.MENU_BUTTON, True)
clock.advance(0.9)                      # longer than LONG_PRESS_SECONDS
e.on_key(gv.MENU_BUTTON, False)
check("long press opens the menu", True, e.menu_open)
check("menu starts at the top", 0, e.menu_index)

# --- the menu navigates with L2/L3 and selects with L4 ---
e.on_key(gv.BUTTON_DOWN, True)
e.on_key(gv.BUTTON_DOWN, False)
check("down moves the selection", 1, e.menu_index)
e.on_key(gv.BUTTON_UP, True)
e.on_key(gv.BUTTON_UP, False)
check("up moves it back", 0, e.menu_index)

e.on_key(gv.BUTTON_DOWN, True); e.on_key(gv.BUTTON_DOWN, False)
e.on_key(gv.BUTTON_DOWN, True); e.on_key(gv.BUTTON_DOWN, False)
check("selection reaches the third item", 2, e.menu_index)
e.on_key(gv.BUTTON_SELECT, True); e.on_key(gv.BUTTON_SELECT, False)
check("select switches visual", "system", e.active)
check("select closes the menu", False, e.menu_open)

# --- L1 backs out ---
e.on_key(gv.MENU_BUTTON, True); clock.advance(0.9); e.on_key(gv.MENU_BUTTON, False)
check("menu open again", True, e.menu_open)
e.on_key(gv.BUTTON_BACK, True); e.on_key(gv.BUTTON_BACK, False)
check("back closes it", False, e.menu_open)

# --- wrapping, and the auto-cycle toggle ---
e.active = "media"
e.on_key(gv.MENU_BUTTON, True); clock.advance(0.05); e.on_key(gv.MENU_BUTTON, False)
check("cycles back to the first visual", "clock", e.active)

e.on_key(gv.MENU_BUTTON, True); clock.advance(0.9); e.on_key(gv.MENU_BUTTON, False)
e.on_key(gv.BUTTON_SELECT, True); e.on_key(gv.BUTTON_SELECT, False)   # "Auto-cycle: off"
check("auto-cycle toggled on", True, e.cycle)
e.on_key(gv.BUTTON_BACK, True); e.on_key(gv.BUTTON_BACK, False)
clock.advance(11)
e.tick()
check("auto-cycle moved on by itself", "system", e.active)

# --- button presses show up on the pad visual ---
e.on_key(5, True)
e.on_key(5, False)
check("recent presses are a rolling window", "G5", e.recent[-1])
check("the window is six long", 6, len(e.recent))

# --- the driver's state reaches the visual ---
e.on_state("recording", "1")
check("recording state kept", True, e.recording)
e.on_state("profile", "2")
check("profile state kept", 2, e.profile)

# --- the layout rule: text must land on blank pixels, or it is invisible ---
# The rule itself lives in the daemon, so the tool, the config tool's preview and this suite all
# work from one implementation.
def assert_text_on_blank_pixels(engine, label):
    check("layout: " + label, [], gv.text_problems(engine.tick()))


for name in ("clock", "system", "media", "pad", "custom"):
    e.active = name
    e.menu_open = False
    assert_text_on_blank_pixels(e, name + " visual")

e.menu_open = True
assert_text_on_blank_pixels(e, "menu")

# --- config round trip ---
os.environ["XDG_CONFIG_HOME"] = "/tmp/visualstest"
gv.write_config(e.snapshot())
check("config written and read back", e.snapshot(), gv.read_config())

# --- data sources for designed applets ---
os.environ["XDG_CONFIG_HOME"] = "/tmp/visualstest"
values = gv.Values()
check("cpu is a number", True, isinstance(values.raw("cpu"), float))
check("memory is a percentage", True, 0 <= values.raw("memory") <= 100)
check("time looks like a time", True, len(values.raw("time")) == 5 and values.raw("time")[2] == ":")
check("day is a short name", True, isinstance(values.raw("day"), str) and 2 <= len(values.raw("day")) <= 4)
check("env source", "hello", (os.environ.update({"G13TEST": "hello"}) or values.raw("env:G13TEST")))
check("unknown source is empty", "", values.raw("nothing-like-this"))

with open("/tmp/visualstest/somefile.txt", "w") as handle:
    handle.write("first line\nsecond line\n")
check("file source reads the first line", "first line", values.raw("file:/tmp/visualstest/somefile.txt"))
check("cmd source runs a command", "ok", values.raw("cmd:echo ok"))
check("a failing cmd source is empty", "", values.raw("cmd:exit 3"))

# --- a designed applet renders its widgets against live data ---
definition = {
    "name": "stats",
    "title": "STATS",
    "interval": 1,
    "widgets": [
        {"type": "text", "x": 3, "y": 12, "format": "CPU {cpu:.0f}%"},
        {"type": "bar", "x": 28, "y": 12, "w": 88, "h": 9, "source": "cpu", "max": 100},
        {"type": "box", "x": 1, "y": 22, "w": 157, "h": 10},
        {"type": "text", "x": 4, "y": 24, "format": "MEM {memory:.0f}%  {day}"},
    ],
}
applet = gv.LayoutVisual(definition, values)
check("applet name is prefixed", "applet:stats", applet.name)
check("applet asks for its sources", ["cpu", "day", "memory"], applet.sources())

screen = gv.Screen()
applet.render(screen, {})
lines = screen.lines()
check("applet draws a frame", True, lines[0].startswith("#bitmap "))
check("applet draws its title", True, any(line.endswith(" STATS") and line.startswith("#text 3 2") for line in lines))
check("applet formats values", True, any(line.startswith("#text 3 12 CPU ") for line in lines))
check("applet draws its box", True, screen.rows[22].count(1) > 100)

# A live value that has not arrived yet - the game is not running - reads as blank rather than
# "?", which is kept for a format that cannot be read at all.
blank_definition = {
    "name": "blank", "interval": 1,
    "widgets": [
        {"type": "text", "x": 3, "y": 12, "format": "AMMO {ammo:>3}  {objective}"},
        {"type": "text", "x": 3, "y": 20, "format": "{ammo:>Z}"},
    ],
}
blank_screen = gv.Screen()
gv.LayoutVisual(blank_definition, gv.Values()).render(blank_screen, {})
blank_lines = [message for _x, _y, message in blank_screen.texts]
check("a missing live value reads as blank", "AMMO",
      next((line.rstrip() for line in blank_lines if line.startswith("AMMO")), "no AMMO line"))
check("a format that cannot be read still says so", "?",
      next((line for line in blank_lines if line.strip() == "?"), "no ? line"))

# --- an applet fed by a running program can take the screen, and give it back ---------------

follow_definition = {
    "name": "game", "title": "GAME", "interval": 1, "follow": {"seconds": 20},
    "sources": {"hud": "json:/tmp/visualstest/hud.json#health"},
    "widgets": [{"type": "text", "x": 3, "y": 12, "format": "{hud}"}],
}
follow_visual = gv.LayoutVisual(follow_definition, values)
follow_engine = gv.Engine({"enabled": ["clock", "applet:game"], "active": "clock"},
                          visuals={"clock": gv.VISUALS_BY_NAME["clock"],
                                   "applet:game": follow_visual},
                          values=values)

check("an applet without 'follow' never takes the screen", 0.0,
      gv.LayoutVisual({"name": "plain", "widgets": []}, values).follow_seconds())
check("'follow': true uses the default", gv.FOLLOW_SECONDS,
      gv.LayoutVisual({"name": "plain", "follow": True, "widgets": []}, values).follow_seconds())
check("'follow' takes a number of seconds", 5.0,
      gv.LayoutVisual({"name": "plain", "follow": {"seconds": 5}, "widgets": []},
                      values).follow_seconds())

writing = lambda spec, now: 1.0     # the game is being written to right now
stopped = lambda spec, now: 900.0   # nothing has written to it for a while

follower = gv.Follower()
check("a fresh applet takes the screen", "applet:game",
      follower.wanted(follow_engine, 100.0, writing))
follow_engine.switch_to("applet:game")
check("and it stays until the writing stops", None,
      follower.wanted(follow_engine, 101.0, writing))
check("then the screen goes back to what was there", "clock",
      follower.wanted(follow_engine, 102.0, stopped))

# Choosing something else while it is running is respected - it saves a button press, it does
# not fight you - and following picks up again next time the program starts.
follower = gv.Follower()
follower.wanted(follow_engine, 100.0, writing)
follow_engine.switch_to("applet:game")
follower.user_chose("clock")
follow_engine.switch_to("clock")
check("a choice made while the program runs is respected", None,
      follower.wanted(follow_engine, 101.0, writing))
follower.wanted(follow_engine, 102.0, stopped)          # the game goes away
check("and following starts again when it comes back", "applet:game",
      follower.wanted(follow_engine, 103.0, writing))

check("a data source with no file behind it has no age", None, gv.source_age("cpu"))
check("an applet's own history is what decides", 0 <= gv.source_age(
    "file:/tmp/visualstest/somefile.txt") < 600, True)
# The run loop's clock is monotonic; mixing it with a file's wall-clock mtime once reported
# every file as billions of seconds young, so nothing ever went stale and the screen was never
# handed back. The age has to stay small whichever clock is handed in.
check("a file written just now is young against either clock", True,
      gv.source_age("file:/tmp/visualstest/somefile.txt", time.monotonic()) < 60)
check("and against no clock at all", True,
      gv.source_age("file:/tmp/visualstest/somefile.txt") < 60)

# --- the lists `g13-applet check` argues from have to match what the daemon really does ------
# It tells authors about a widget type nothing draws, or a source name that is not one, so those
# lists drifting from the code would make the tool lie in both directions.
for kind in gv.WIDGET_TYPES:
    failure = None
    try:
        definition = {"name": "widget-" + kind, "interval": 1,
                      "widgets": [{"type": kind, "x": 30, "y": 14, "w": 40, "h": 6, "count": 6,
                                   "len": 3, "thick": 1, "r": 5, "size": 9, "source": "cpu",
                                   "max": 100, "format": "x {cpu:.0f}"}]}
        screen = gv.Screen()
        gv.LayoutVisual(definition, values).render(screen, {})
    except Exception as error:
        failure = "%s: %s" % (type(error).__name__, error)
    check("the tool's widget type %r really draws" % kind, None, failure)

for name in gv.BUILT_IN_SOURCES:
    failure = None
    try:
        values.resolve(name)
    except Exception as error:
        failure = "%s: %s" % (type(error).__name__, error)
    check("the tool's built-in source %r really resolves" % name, None, failure)

# --- designed applets are found on disk and show up as visuals ---
os.makedirs("/tmp/visualstest/g13/applets", exist_ok=True)
with open("/tmp/visualstest/g13/applets/uptime.json", "w") as handle:
    json.dump({"title": "UP", "widgets": [
        {"type": "text", "x": 3, "y": 12, "format": "UP {uptime}"}]}, handle)

found = gv.load_applets(values)
check("applet found on disk", True, "applet:uptime" in found)
check("applet title from its file", "UP", found["applet:uptime"].title)

visuals = gv.registry(values)
check("registry has built-ins and applets", True,
      all(name in visuals for name in ("clock", "system", "applet:uptime")))

applet_engine = gv.Engine({"enabled": ["applet:uptime"], "active": "applet:uptime"},
                          visuals=visuals, values=values)
lines = applet_engine.tick()
check("engine renders a designed applet", True, any("UP " in line for line in lines[1:]))
check("menu names the applet", "UP", applet_engine.menu_items()[1][1])

# --- the layout rule applies to designed applets too ---
os.environ["XDG_CONFIG_HOME"] = "/tmp/visualstest"
applet_engine.menu_open = False
assert_text_on_blank_pixels(applet_engine, "designed applet")

# --- alignment: a value can be pinned to the right edge without knowing its width ---
aligned = gv.LayoutVisual({
    "name": "aligned", "title": "A", "border": False,
    "widgets": [
        {"type": "text", "x": 0, "y": 4, "align": "right", "margin": 3, "format": "{cpu:.0f}%"},
        {"type": "text", "x": 0, "y": 20, "align": "centre", "format": "MID"},
    ],
}, values)
aligned_screen = gv.Screen()
aligned.render(aligned_screen, {})
right = [item for item in aligned_screen.texts if item[1] == 4][0]
middle = [item for item in aligned_screen.texts if item[1] == 20][0]
check("right aligned text ends at the margin",
      gv.g13lcd.VISIBLE_WIDTH - 3, right[0] + len(right[2]) * 6)
check("centred text is centred", gv.centred(middle[2]), middle[0])

# --- every shipped applet must obey the layout rules ---
# This is the check that would have caught the demo applet's label running into its own
# bar, and its bottom line sitting on the border: the rules were checked on definitions
# written inside this test, never on the file that ships.
import glob
import json as json_module

shipped = sorted(glob.glob(os.path.join(REPO, "g13-visuals", "applets", "*.json")))
check("there are shipped applets to check", True, len(shipped) >= 1)
for path in shipped:
    definition = json_module.load(open(path))
    definition.setdefault("name", os.path.basename(path)[:-5])
    visual = gv.LayoutVisual(definition, values)
    one = gv.Engine({"enabled": ["only"], "active": "only"},
                    visuals={"only": visual}, values=values)
    assert_text_on_blank_pixels(one, "shipped applet %s" % definition["name"])

# --- a game's own data: json: sources, aliases, and the widgets a HUD wants ---
import tempfile

scratch = tempfile.mkdtemp(prefix="g13-hud-")
hud_path = os.path.join(scratch, "hud.json")
with open(hud_path, "w") as handle:
    json.dump({"ammo": 24, "health": 86.5, "turn": -35, "distance": 240,
               "objective": "Deliver the package to Vex", "off": False,
               "nested": {"route": {"corner": 90}}, "waypoints": ["a", "b"]}, handle)

hud_values = gv.Values(clock=FakeClock())
check("json: a field", 24, hud_values.raw("json:%s#ammo" % hud_path))
check("json: a nested field", 90, hud_values.raw("json:%s#nested.route.corner" % hud_path))
check("json: a list index", "b", hud_values.raw("json:%s#waypoints.1" % hud_path))
check("json: false is a value", False, hud_values.raw("json:%s#off" % hud_path))
check("json: a missing field reads empty", "", hud_values.raw("json:%s#nothing" % hud_path))
check("json: a missing file reads empty", "",
      hud_values.raw("json:%s#ammo" % os.path.join(scratch, "gone.json")))
check("json: a whole object is not a value", "", hud_values.raw("json:%s#nested" % hud_path))

broken_path = os.path.join(scratch, "halfway.json")
with open(broken_path, "w") as handle:
    handle.write("{ not json")
check("json: unreadable JSON reads empty", "", hud_values.raw("json:%s#ammo" % broken_path))

scalar_path = os.path.join(scratch, "number.json")
with open(scalar_path, "w") as handle:
    handle.write("5")
check("json: a whole-file number", 5, hud_values.raw("json:%s" % scalar_path))


def render_applet(definition, values_for_applet=None):
    screen = gv.Screen()
    visual = gv.LayoutVisual(definition, values_for_applet or hud_values)
    visual.render(screen, {})
    return visual, screen


def lit(frame, x, y):
    """Whether one pixel is set, in the driver's frame layout: column-major vertical bytes."""
    return bool(frame[x + (y // 8) * 160] >> (y % 8) & 1)


def blocks_on_row(frame, y):
    """The number of separate runs of ink along one row - i.e. gauge blocks."""
    runs, inside = 0, False
    for x in range(160):
        here = lit(frame, x, y)
        if here and not inside:
            runs += 1
        inside = here
    return runs


hud_applet = {
    "name": "hud", "title": "HUD", "border": False, "interval": 0.2,
    "sources": {"ammo": "json:%s#ammo" % hud_path, "health": "json:%s#health" % hud_path},
    "widgets": [
        {"type": "text", "x": 3, "y": 12, "format": "AMMO {ammo}"},
        {"type": "segments", "x": 3, "y": 20, "w": 60, "h": 5, "count": 12,
         "source": "health", "max": 100},
    ],
}
hud_visual, hud_screen = render_applet(hud_applet)
check("alias: a format can use the short name", "AMMO 24", hud_screen.texts[0][2])
check("alias: the specs are read, not the names", ["json:%s#ammo" % hud_path,
                                                   "json:%s#health" % hud_path],
      hud_visual.sources())
check("alias: an unknown name is left alone", "nope", hud_visual.spec_of("nope"))
check("applet: the interval comes from the file", 0.2, hud_visual.interval)


def segments_frame(percent, count=12, width=60):
    screen = gv.Screen()
    screen.segments(10, 10, width, 5, count, percent)
    return gv.g13lcd.pixels_to_frame(screen.rows)


check("segments: 50% lights half the blocks", 6, blocks_on_row(segments_frame(50), 12))
check("segments: nothing lit at 0%", 0, blocks_on_row(segments_frame(0), 12))
check("segments: every block at 100%", 12, blocks_on_row(segments_frame(100), 12))
check("segments: clamps below zero", 0, blocks_on_row(segments_frame(-20), 12))
check("segments: clamps above 100", 12, blocks_on_row(segments_frame(300), 12))
check("segments: nothing to the left of the gauge", False, any(lit(segments_frame(100), x, 12)
                                                              for x in range(10)))
check("segments: nothing to the right of the gauge", False, any(lit(segments_frame(100), x, 12)
                                                                for x in range(70, 160)))


def arrow_frame(angle):
    screen = gv.Screen()
    screen.arrow(80, 21, 8, angle)
    return gv.g13lcd.pixels_to_frame(screen.rows)


def arrow_reach(angle):
    """How far the arrow's ink reaches towards the way it points, and behind it.

    A triangle's centroid sits near its middle, so direction has to be read from the tip.
    """
    frame = arrow_frame(angle)
    theta = math.radians(angle)
    forward, back = math.sin(theta), -math.cos(theta)
    along = [((x - 80) * forward) + ((y - 21) * back)
             for y in range(gv.g13lcd.VISIBLE_HEIGHT)
             for x in range(gv.g13lcd.VISIBLE_WIDTH) if lit(frame, x, y)]
    return max(along), -min(along)


check("arrow: 0 degrees reaches up, not down", True,
      arrow_reach(0)[0] > 7 and arrow_reach(0)[1] < 5.5)
check("arrow: 0 degrees leaves the space below clear", False, lit(arrow_frame(0), 80, 29))
check("arrow: 90 degrees reaches right, not left", True,
      arrow_reach(90)[0] > 7 and arrow_reach(90)[1] < 5.5)
check("arrow: 90 degrees leaves the space to the left clear", False, lit(arrow_frame(90), 73, 21))
check("arrow: 180 degrees reaches down, not up", True,
      arrow_reach(180)[0] > 7 and arrow_reach(180)[1] < 5.5)
check("arrow: 270 degrees reaches left, not right", True,
      arrow_reach(270)[0] > 7 and arrow_reach(270)[1] < 5.5)
check("arrow: it stays the same size whichever way it points", True,
      len({sum(1 for y in range(gv.g13lcd.VISIBLE_HEIGHT)
               for x in range(gv.g13lcd.VISIBLE_WIDTH) if lit(arrow_frame(a), x, y))
           for a in (0, 90, 180, 270)}) == 1)


def turn_frame(angle):
    screen = gv.Screen()
    screen.turn(10, 10, angle, 11)
    return gv.g13lcd.pixels_to_frame(screen.rows)


check("turn: straight on", True, lit(turn_frame(10), 15, 14))
check("turn: a right turn rises up the right", True, lit(turn_frame(90), 17, 14))
check("turn: a right turn is empty at the top left", False, lit(turn_frame(90), 12, 14))
check("turn: a left turn rises up the left", True, lit(turn_frame(-90), 12, 14))
check("turn: left and right differ", True, turn_frame(-90) != turn_frame(90))
check("turn: a double back is its own shape", True,
      turn_frame(180) not in (turn_frame(0), turn_frame(90), turn_frame(-90)))
check("turn: a small error stays straight on", True, turn_frame(15) == turn_frame(10))
check("turn: no direction at all is straight on", True, turn_frame(0) == turn_frame(10))


def brackets_frame():
    screen = gv.Screen()
    screen.brackets(10, 10, 30, 20, 5, 2)
    return gv.g13lcd.pixels_to_frame(screen.rows)


check("brackets: the top-left corner is drawn", True, lit(brackets_frame(), 10, 10))
check("brackets: the bottom-right corner is drawn", True, lit(brackets_frame(), 39, 29))
check("brackets: the edge between them is not", False, lit(brackets_frame(), 25, 10))
check("brackets: the inside is clear", False, lit(brackets_frame(), 25, 20))

long_line = "Deliver the package to Vex"
check("scroll: a line that fits is left alone", "SHORT",
      gv.scrolling("SHORT", {"scroll_width": 25}, now=1000))
check("scroll: the window is the requested width", 12,
      len(gv.scrolling(long_line, {"scroll_width": 12}, now=0)))
check("scroll: the window moves with time", True,
      gv.scrolling(long_line, {"scroll_width": 12, "scroll_speed": 3}, now=0)
      != gv.scrolling(long_line, {"scroll_width": 12, "scroll_speed": 3}, now=3))
check("scroll: it wraps round to the start", True,
      gv.scrolling(long_line, {"scroll_width": 12, "scroll_speed": 1}, now=0)
      == gv.scrolling(long_line, {"scroll_width": 12, "scroll_speed": 1},
                      now=len(long_line) + 3))

# --- a config change made anywhere else must take effect (the config tool's "Show now") ---
running = gv.Engine({"enabled": ["clock", "system"], "active": "clock"}, clock=FakeClock())
check("config: an unchanged file leaves the screen alone", False,
      gv.config_supersedes(running, gv.Engine({"enabled": ["clock", "system"],
                                               "active": "clock"}, clock=FakeClock())))
check("config: a different active visual is adopted", True,
      gv.config_supersedes(running, gv.Engine({"enabled": ["clock", "system"],
                                               "active": "system"}, clock=FakeClock())))
check("config: a reordered list is adopted", True,
      gv.config_supersedes(running, gv.Engine({"enabled": ["system", "clock"],
                                               "active": "clock"}, clock=FakeClock())))

# --- who owns the screen and the four buttons (an SDK client, or the visuals) ---
# The harness pins XDG_CONFIG_HOME to a scratch directory that outlives a run, so start from
# no file at all rather than from whatever the last run left behind.
try:
    (gv.config_dir() / gv.BUTTON_MODE_FILE).unlink()
except OSError:
    pass
check("mode: with no file at all the answer is auto", "auto", gv.button_mode())
gv.set_button_mode("sdk")
check("mode: it round trips through the file", "sdk", gv.button_mode())
gv.set_button_mode("nonsense")
check("mode: nonsense falls back to auto", "auto", gv.button_mode())
gv.set_button_mode("auto")

check("who owns it: auto with a client present is the client", True, gv.should_pause("auto", True))
check("who owns it: auto with no client is the visuals", False, gv.should_pause("auto", False))
check("who owns it: visuals keeps it even with a client there", False,
      gv.should_pause("visuals", True))
check("who owns it: sdk keeps it even with no client", True, gv.should_pause("sdk", False))

# The presence file, with the staleness rule that stops a crashed client leaving the pad deaf.
import tempfile as _tempfile

runtime = _tempfile.mkdtemp(prefix="g13-runtime-")
check("presence: no file means no client", False, gv.sdk_client_present(runtime=runtime))
with open(os.path.join(runtime, gv.SDK_CLIENT_FILE), "w") as handle:
    handle.write("\n")
check("presence: a fresh file means a client is there", True,
      gv.sdk_client_present(runtime=runtime, now=time.time()))
check("presence: a stale file means it died without saying goodbye", False,
      gv.sdk_client_present(runtime=runtime, now=time.time() + gv.SDK_CLIENT_FRESH_SECONDS + 1))
check("state: it says who has the screen", {"button_mode": "sdk", "sdk_client": True,
                                            "screen_owner": "sdk"},
      gv.screen_state("sdk", True, True))

# --- and the check itself must fail on a broken layout, or it proves nothing ---
broken = gv.LayoutVisual({
    "name": "broken", "title": "BROKEN", "border": False,
    "widgets": [
        {"type": "text", "x": 3, "y": 12, "format": "CPU 42% and more text"},   # runs under the bar
        {"type": "bar", "x": 28, "y": 12, "w": 100, "h": 9, "source": "cpu", "max": 100},
        {"type": "text", "x": 3, "y": 38, "format": "this line is too low"},    # off the screen
    ],
}, values)
broken_engine = gv.Engine({"enabled": ["only"], "active": "only"},
                          visuals={"only": broken}, values=values)

before = fail
assert_text_on_blank_pixels(broken_engine, "deliberately broken layout")
check("the layout check catches a broken applet", True, fail > before)
fail = before  # Expected failure: the point was to see it detected.

print("VISUALS TEST: all checks passed" if fail == 0 else "VISUALS TEST: %d FAILURES" % fail)
