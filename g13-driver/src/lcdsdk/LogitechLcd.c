/*
 * LogitechLcd.c - the Logitech LCD SDK, forwarding to the G13 driver.
 *
 * The driver takes text lines and frames on a FIFO ($XDG_RUNTIME_DIR/g13-lcd):
 *   #clear, #text <x> <y> <text>, #bitmap <1920 hex chars>
 * and reports key events on a socket ($XDG_RUNTIME_DIR/g13.sock) as "key <code> <0|1>".
 * Everything the SDK can do maps onto those two, so this file is a translation, not a
 * reimplementation of anything.
 *
 * One write per LogiLcdUpdate(): the driver paints whatever it reads in a single read() as
 * one frame, which is what stops a screen built from several lines from flickering.
 */
#define _GNU_SOURCE

#include "LogitechLcd.h"

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <unistd.h>

/* The G13's LCD buttons, from the driver's point of view (kernel key codes). */
#define L_BUTTON_FIRST 25
#define L_BUTTON_COUNT 4

/* Where a text line lands: the SDK's line 0..3 on a 43 pixel high panel. */
#define TEXT_X 3
#define TEXT_FIRST_Y 2
#define TEXT_LINE_HEIGHT 10

/* The driver's framebuffer is 160x48 = 960 bytes; the bottom five rows are behind the bezel
 * and are never seen, so they stay blank. The SDK only knows about the 43 visible rows. */
#define FRAME_HEIGHT 48
#define FRAME_BYTES ((LOGI_LCD_MONO_WIDTH * FRAME_HEIGHT) / 8)

static int lcd_fd = -1;
static int event_fd = -1;
static unsigned buttons_down = 0;
static char lines[LOGI_LCD_MONO_LINES][128];
static bool line_set[LOGI_LCD_MONO_LINES];
static uint8_t background[LOGI_LCD_MONO_WIDTH * LOGI_LCD_MONO_HEIGHT];
static bool background_set = false;

/* ------------------------------------------------------------------ paths */

static const char *runtime_dir(void)
{
    static char fallback[64];
    const char *directory = getenv("G13_LCD_DIR");

    if (directory && *directory) {
        return directory;
    }
    directory = getenv("XDG_RUNTIME_DIR");
    if (directory && *directory) {
        return directory;
    }
    snprintf(fallback, sizeof fallback, "/run/user/%u", (unsigned)getuid());
    return fallback;
}

static int open_lcd(void)
{
    char path[512];

    snprintf(path, sizeof path, "%s/g13-lcd", runtime_dir());
    /* Non-blocking, so a driver that is not running cannot hang the caller; opening a FIFO
     * with no reader fails with ENXIO, which is exactly the answer we want. */
    return open(path, O_WRONLY | O_NONBLOCK);
}

static int open_events(void)
{
    char path[512];
    struct sockaddr_un address;
    int fd;
    size_t length;

    snprintf(path, sizeof path, "%s/g13.sock", runtime_dir());
    length = strlen(path);
    if (length >= sizeof address.sun_path) {
        return -1;
    }

    fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) {
        return -1;
    }

    memset(&address, 0, sizeof address);
    address.sun_family = AF_UNIX;
    memcpy(address.sun_path, path, length + 1);

    if (connect(fd, (struct sockaddr *)&address, sizeof address) != 0) {
        close(fd);
        return -1;
    }
    fcntl(fd, F_SETFL, O_NONBLOCK);
    return fd;
}

/* ------------------------------------------------------------------ buttons */

static void read_events(void)
{
    char buffer[4096];
    ssize_t got;
    char *line;
    char *save = NULL;

    if (event_fd < 0) {
        return;
    }

    while ((got = read(event_fd, buffer, sizeof buffer - 1)) > 0) {
        buffer[got] = '\0';
        for (line = strtok_r(buffer, "\n", &save); line; line = strtok_r(NULL, "\n", &save)) {
            int code = 0;
            int value = 0;
            if (sscanf(line, "key %d %d", &code, &value) != 2) {
                continue;   /* "state ..." and anything else is not ours */
            }
            if (code < L_BUTTON_FIRST || code >= L_BUTTON_FIRST + L_BUTTON_COUNT) {
                continue;
            }
            unsigned bit = 1u << (code - L_BUTTON_FIRST);
            if (value) {
                buttons_down |= bit;
            } else {
                buttons_down &= ~bit;
            }
        }
    }
}

/* ------------------------------------------------------------------ text */

static void wchar_to_ascii(const uint16_t *text, char *out, size_t out_size)
{
    size_t used = 0;

    if (out_size == 0) {
        return;
    }
    if (!text) {
        out[0] = '\0';
        return;
    }

    for (; *text && used + 1 < out_size; text++) {
        uint16_t character = *text;
        /* The driver's font covers ASCII 32..125; anything else is drawn as '?' rather than
         * silently dropped, so text that does not fit is visible as such. */
        out[used++] = (character >= 32 && character <= 125) ? (char)character : '?';
    }
    out[used] = '\0';
}

/* ------------------------------------------------------------------ the SDK */

bool LogiLcdInit(const uint16_t *friendlyName, unsigned lcdType)
{
    char name[128];

    (void)lcdType;   /* MONO and EITHER both end up on this panel; COLOR cannot. */

    wchar_to_ascii(friendlyName, name, sizeof name);

    if (lcd_fd < 0) {
        lcd_fd = open_lcd();
    }
    if (event_fd < 0) {
        event_fd = open_events();   /* Buttons are a bonus: the screen matters more. */
    }

    return lcd_fd >= 0;
}

bool LogiLcdIsConnected(unsigned lcdType)
{
    if (lcdType & LOGI_LCD_TYPE_MONO) {
        if (lcd_fd < 0) {
            lcd_fd = open_lcd();   /* The driver may have started since Init. */
        }
        return lcd_fd >= 0;
    }
    return false;   /* No colour panel. */
}

bool LogiLcdIsButtonPressed(unsigned button)
{
    read_events();

    if (button & LOGI_LCD_MONO_BUTTON) {
        return (buttons_down & button & LOGI_LCD_MONO_BUTTON) != 0;
    }
    return false;
}

bool LogiLcdMonoSetText(int lineNumber, const uint16_t *text)
{
    if (lineNumber < 0 || lineNumber >= LOGI_LCD_MONO_LINES) {
        return false;
    }
    wchar_to_ascii(text, lines[lineNumber], sizeof lines[lineNumber]);
    line_set[lineNumber] = true;
    return true;
}

bool LogiLcdMonoSetBackground(const uint8_t *monoBitmap)
{
    if (!monoBitmap) {
        return false;
    }
    memcpy(background, monoBitmap, sizeof background);
    background_set = true;
    return true;
}

void LogiLcdUpdate(void)
{
    static const char hex[] = "0123456789abcdef";
    char frame[8 + FRAME_BYTES * 2 + 2];
    char output[4096];
    size_t used = 0;
    int line;
    int y;
    ssize_t offset;

    if (lcd_fd < 0) {
        return;
    }

    /* The background is the whole field, 8 bits per pixel, on at >= 128. The driver's frame
     * is one bit per pixel, 960 bytes, indexed as x + (row * 160): the x axis varies fastest
     * within the hex string, and byte row r holds the eight rows starting at r * 8, top row
     * of those in bit 0. Sending them the other way round turns the picture into confetti. */
    memset(&frame, 0, sizeof frame);
    memcpy(frame, "#bitmap ", 8);
    used = 8;
    for (int row = 0; row < FRAME_HEIGHT / 8; row++) {
        for (int x = 0; x < LOGI_LCD_MONO_WIDTH; x++) {
            unsigned char bits = 0;
            for (int bit = 0; bit < 8; bit++) {
                const int pixel_y = row * 8 + bit;
                if (pixel_y >= LOGI_LCD_MONO_HEIGHT) {
                    break;                       /* past the visible part of the panel */
                }
                if (background_set &&
                    background[pixel_y * LOGI_LCD_MONO_WIDTH + x] >= 128) {
                    bits |= (unsigned char)(1u << bit);
                }
            }
            frame[used++] = hex[(bits >> 4) & 0xf];
            frame[used++] = hex[bits & 0xf];
        }
    }
    frame[used++] = '\n';
    frame[used] = '\0';

    memcpy(output, frame, used);
    for (line = 0; line < LOGI_LCD_MONO_LINES; line++) {
        if (!line_set[line]) {
            continue;
        }
        y = TEXT_FIRST_Y + line * TEXT_LINE_HEIGHT;
        used += (size_t)snprintf(output + used, sizeof output - used, "#text %d %d %s\n",
                                TEXT_X, y, lines[line]);
    }

    /* One write, so the driver paints one frame. A full pipe (driver busy, nobody reading)
     * drops this frame rather than blocking the caller - a game must never stall on a
     * second screen. */
    offset = write(lcd_fd, output, used);
    if (offset < 0 && errno == EPIPE) {
        close(lcd_fd);
        lcd_fd = -1;
    }
}

void LogiLcdShutdown(void)
{
    if (lcd_fd >= 0) {
        close(lcd_fd);
        lcd_fd = -1;
    }
    if (event_fd >= 0) {
        close(event_fd);
        event_fd = -1;
    }
    buttons_down = 0;
    background_set = false;
    memset(line_set, 0, sizeof line_set);
}

/* Colour: the SDK's own answers, for a panel that is not there. */
bool LogiLcdColorSetBackground(const uint8_t *colorBitmap)
{
    (void)colorBitmap;
    return false;
}

bool LogiLcdColorSetTitle(const uint16_t *text, int red, int green, int blue)
{
    (void)text;
    (void)red;
    (void)green;
    (void)blue;
    return false;
}

bool LogiLcdColorSetText(int lineNumber, const uint16_t *text, int red, int green, int blue)
{
    (void)lineNumber;
    (void)text;
    (void)red;
    (void)green;
    (void)blue;
    return false;
}

/* Our addition, for plain C callers. */
bool G13LcdSetTextA(int lineNumber, const char *text)
{
    if (lineNumber < 0 || lineNumber >= LOGI_LCD_MONO_LINES) {
        return false;
    }
    if (!text) {
        lines[lineNumber][0] = '\0';
    } else {
        strncpy(lines[lineNumber], text, sizeof lines[lineNumber] - 1);
        lines[lineNumber][sizeof lines[lineNumber] - 1] = '\0';
    }
    line_set[lineNumber] = true;
    return true;
}
