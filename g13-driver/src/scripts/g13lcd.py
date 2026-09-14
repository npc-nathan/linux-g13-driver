"""Drive the G13's LCD screen: 160x43 visible pixels, one bit each.

The driver turns whatever you send into a frame and pushes it to the device, so this is
usable as a plain display: text anywhere on the screen, or a raw bitmap for bars, graphs
and icons.

    import g13lcd
    g13lcd.clear()
    g13lcd.at(0, 0, "HP 120/120")
    g13lcd.frame(g13lcd.pixels_to_frame(rows))

Compose screens with blank(), draw_rect() and draw_bar() rather than by hand: doing the
pixel arithmetic ad hoc is how a fill ends up drawn outside its track.

The `g13-lcd` command line tool uses this module; so does the visuals daemon.
"""
import os

WIDTH = 160
HEIGHT = 48

# The panel is 160x43: the top 43 rows of the buffer are the visible screen and rows
# 43-47 are off the glass, so content has to stay inside y 0-42 or it is simply not
# there. (Same geometry as the other Logitech LCDs of that era, and what the upstream
# test pattern draws its frame in.)
VISIBLE_WIDTH = 160
VISIBLE_HEIGHT = 43
#: The driver's font: 5x7 glyphs on a 6 pixel advance, 7 rows tall.
CHAR_WIDTH = 6
TEXT_HEIGHT = 7

FRAME_BYTES = WIDTH * HEIGHT // 8  # 960


def lcd_path():
    """The driver's LCD pipe for this session."""
    runtime = os.environ.get("XDG_RUNTIME_DIR")
    if runtime:
        return os.path.join(runtime, "g13-lcd")
    return "/tmp/g13-lcd"


def send(lines, path=None):
    """Sends lines to the driver. Raises OSError if the driver is not running."""
    target = path or lcd_path()
    with open(target, "w") as pipe:
        for line in lines:
            pipe.write(str(line) + "\n")
        pipe.flush()


def text(*lines, path=None):
    """Shows lines of text from the top of the screen, clearing what was there."""
    send(list(lines), path=path)


def at(x, y, message, path=None):
    """Draws one line of text at a position, leaving the rest of the screen alone."""
    send(["#text %d %d %s" % (int(x), int(y), message)], path=path)


def clear(path=None):
    """Blanks the screen."""
    send(["#clear"], path=path)


def frame(data, path=None):
    """Pushes a 960 byte frame buffer."""
    if len(data) != FRAME_BYTES:
        raise ValueError("a frame is %d bytes, got %d" % (FRAME_BYTES, len(data)))
    send(["#bitmap " + data.hex()], path=path)


def pixels_to_frame(rows):
    """Turns a list of rows of 0/1 (or True/False) into a frame buffer.

    Rows may be shorter than the screen; the rest stays blank.
    """
    buffer = bytearray(FRAME_BYTES)
    for y, row in enumerate(rows):
        if y >= HEIGHT:
            break
        for x, pixel in enumerate(row):
            if x >= WIDTH:
                break
            if pixel:
                buffer[x + (y // 8) * WIDTH] |= 1 << (y % 8)
    return bytes(buffer)


def _pbm_header(data):
    """Returns (width, height, offset-of-pixel-data) for a PBM file."""
    position = 2  # past the magic number
    values = []

    while len(values) < 2:
        while position < len(data) and data[position:position + 1].isspace():
            position += 1
        if data[position:position + 1] == b"#":
            while position < len(data) and data[position:position + 1] != b"\n":
                position += 1
            continue
        start = position
        while position < len(data) and not data[position:position + 1].isspace():
            position += 1
        values.append(int(data[start:position]))

    # A single whitespace character separates the header from the pixels; be generous
    # and treat a CR LF as that one separator rather than as pixel data.
    if data[position:position + 2] == b"\r\n":
        position += 2
    elif data[position:position + 1].isspace():
        position += 1

    return values[0], values[1], position


def blank(width=WIDTH, height=HEIGHT):
    """A frame of rows, all blank, for composing a screen."""
    return [[0] * width for _ in range(height)]


def draw_rect(rows, x0, y0, x1, y1, value=1):
    """Sets a rectangle of pixels, clipped to the frame."""
    if not rows:
        return
    width = len(rows[0])
    for y in range(max(0, y0), min(len(rows), y1)):
        for x in range(max(0, x0), min(width, x1)):
            rows[y][x] = value


def draw_bar(rows, x0, y0, width, height, percent):
    """Draws a bar: a hollow track with a fill proportional to percent.

    The fill is computed from the track's interior and never leaves it, whatever
    percent says (values outside 0-100 are clamped). This exists because doing the
    arithmetic by hand is how a fill ends up drawn over the value text next to it.
    """
    percent = max(0, min(100, int(percent)))
    draw_rect(rows, x0, y0, x0 + width, y0 + height)                # track outline
    draw_rect(rows, x0 + 1, y0 + 1, x0 + width - 1, y0 + height - 1, 0)  # hollow it
    interior = max(0, width - 2)
    filled = interior * percent // 100
    if filled:
        draw_rect(rows, x0 + 1, y0 + 1, x0 + 1 + filled, y0 + height - 1)


def read_pbm(path):
    """Reads a PBM (P1 text or P4 binary) into rows of 0/1. 1 means "pixel on"."""
    with open(path, "rb") as handle:
        data = handle.read()

    if data[:2] == b"P4":
        width, height, position = _pbm_header(data)
        stride = (width + 7) // 8
        rows = []
        for y in range(height):
            row = []
            for x in range(width):
                byte = data[position + y * stride + x // 8]
                row.append((byte >> (7 - (x % 8))) & 1)
            rows.append(row)
        return rows

    if data[:2] == b"P1":
        width, height, position = _pbm_header(data)
        numbers = []
        for line in data[position:].split(b"\n"):
            line = line.split(b"#", 1)[0]
            numbers.extend(int(token) for token in line.split() if token.isdigit())
        return [numbers[y * width:(y + 1) * width] for y in range(height)]

    raise ValueError("%s is not a PBM (P1 or P4) file" % path)


def fit(rows, width=VISIBLE_WIDTH, height=VISIBLE_HEIGHT):
    """Scales rows to fit the visible part of the screen, keeping the shape, centred."""
    source_height = len(rows)
    source_width = max((len(row) for row in rows), default=0)
    if not source_width or not source_height:
        return []

    scale = min(width / source_width, height / source_height)
    target_width = max(1, min(width, int(source_width * scale)))
    target_height = max(1, min(height, int(source_height * scale)))
    offset_x = (width - target_width) // 2
    offset_y = (height - target_height) // 2

    scaled = []
    for y in range(offset_y):
        scaled.append([0] * width)
    for y in range(target_height):
        source_y = min(source_height - 1, int(y / scale))
        row = [0] * offset_x
        for x in range(target_width):
            source_x = min(source_width - 1, int(x / scale))
            row.append(1 if rows[source_y][source_x] else 0)
        row.extend([0] * (width - len(row)))
        scaled.append(row)
    while len(scaled) < height:
        scaled.append([0] * width)
    return scaled


