/*
 * LogitechLcd.c - the Logitech LCD SDK, forwarding to the G13 driver.
 *
 * The driver takes text lines and frames on a FIFO ($XDG_RUNTIME_DIR/g13-lcd):
 *   #clear, #text <x> <y> <text>, #bitmap <1920 hex chars>
 * and reports key events on a socket ($XDG_RUNTIME_DIR/g13.sock) as "key <code> <0|1>".
 * Everything the SDK can do maps onto those two, so this file is a translation, not a
 * reimplementation of anything.
 *
 * Two transports, one body of code:
 *
 *   native (default on Linux): those two Unix endpoints directly.
 *   TCP (the Windows build, and any Linux build with -DG13_TCP_TRANSPORT=1): one connection
 *   to g13-lcd-bridge on 127.0.0.1, because a Windows program under Wine/Proton cannot use
 *   Unix sockets. The bridge relays both ways, so the TCP build needs one connection where
 *   the native build needs two.
 *
 * The TCP transport builds on Linux on purpose: it is the code the Windows DLL runs, so it
 * can be tested here without Wine (make test-lcdsdk-proxy).
 *
 * One write per LogiLcdUpdate(): the driver paints whatever it reads in a single read() as
 * one frame, which is what stops a screen built from several lines from flickering.
 */
#define _GNU_SOURCE

#include "LogitechLcd.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(_WIN32)
#define G13_WINDOWS 1
#endif

#if !defined(G13_TCP_TRANSPORT)
#if defined(_WIN32)
#define G13_TCP_TRANSPORT 1
#else
#define G13_TCP_TRANSPORT 0
#endif
#endif

#if defined(G13_WINDOWS)
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
typedef SOCKET g13_fd;
#define G13_INVALID INVALID_SOCKET
#else
#include <errno.h>
#include <fcntl.h>
#include <netdb.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <unistd.h>
typedef int g13_fd;
#define G13_INVALID (-1)
#endif

#if G13_TCP_TRANSPORT
/* Sockets on both platforms: send/recv work on Unix sockets too. */
#define g13_send(fd, buffer, length) ((long)send((fd), (char *)(buffer), (int)(length), 0))
#define g13_recv(fd, buffer, length) ((long)recv((fd), (char *)(buffer), (int)(length), 0))
#else
/* read/write, not recv/send: the screen side of the native build is a FIFO, and a FIFO is
 * not a socket. read and write work on both, so the call sites stay free of ifdefs. */
#define g13_send(fd, buffer, length) ((long)write((fd), (buffer), (length)))
#define g13_recv(fd, buffer, length) ((long)read((fd), (buffer), (length)))
#endif

#if defined(G13_WINDOWS)
/* ---------------------------------------------------------------- the probe
 *
 * A note of what happened, written beside this DLL, so that "did the game load us, and what did
 * it ask for" is answerable from outside Wine rather than by guessing at a packed executable's
 * strings. If the file cannot be written it does nothing at all: a probe must never get in the
 * way of the screen, and this one is only ever read by a person running the game.
 */
static void probe_log(const char *what)
{
    char path[MAX_PATH];
    DWORD length = GetModuleFileNameA((HMODULE)(void *)&probe_log, path, sizeof path);
    FILE *log;
    DWORD at;

    if (length == 0 || length >= sizeof path) {
        return;
    }
    for (at = length; at > 0; at--) {
        if (path[at - 1] == '\\' || path[at - 1] == '/') {
            path[at - 1] = '\0';
            break;
        }
    }
    snprintf(path + strlen(path), sizeof path - strlen(path), "\\lcd-probe.log");
    log = fopen(path, "a");
    if (!log) {
        return;
    }
    fprintf(log, "%s\n", what);
    fclose(log);
}

/* Whether the game loads this DLL at all is the first question, so answer it before anybody
 * calls in. The pointer width says which build a game picked up, which is the second question:
 * a 64-bit DLL in a 32-bit game loads and does nothing. */
static void __attribute__((constructor)) probe_loaded(void)
{
    char note[96];
    snprintf(note, sizeof note - 1, "loaded LogitechLcd.dll (%u-bit)",
             (unsigned)(sizeof(void *) * 8));
    probe_log(note);
}
#else
static void probe_log(const char *what)
{
    (void)what;      /* the native build has a terminal to complain on */
}
#endif


static void set_nonblocking(g13_fd fd)
{
#if defined(G13_WINDOWS)
    u_long on = 1;
    ioctlsocket(fd, FIONBIO, &on);
#else
    fcntl(fd, F_SETFL, fcntl(fd, F_GETFL, 0) | O_NONBLOCK);
#endif
}

static void close_fd(g13_fd fd)
{
#if defined(G13_WINDOWS)
    closesocket(fd);
#else
    close(fd);
#endif
}

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

/* Where the bridge listens (G13_LCD_TCP=host:port overrides). */
#define BRIDGE_DEFAULT_PORT "51513"

static g13_fd screen_fd = G13_INVALID;
static g13_fd event_fd = G13_INVALID;
static unsigned buttons_down = 0;
/* Native builds reach the driver directly, so a screen that opened is a screen that is
 * there. The TCP build hears about it from the bridge ("state driver <0|1>"), because a
 * bridge with no driver behind it would otherwise look like a working panel. */
static bool driver_present = true;
static char lines[LOGI_LCD_MONO_LINES][128];
static bool line_set[LOGI_LCD_MONO_LINES];
static uint8_t background[LOGI_LCD_MONO_WIDTH * LOGI_LCD_MONO_HEIGHT];
static bool background_set = false;

/* ------------------------------------------------------------------ the driver's directory */

#if !G13_TCP_TRANSPORT

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

/* A file the visuals daemon watches: while it is fresh, an SDK client owns the screen and
 * the pad's four LCD buttons. Touched on every frame, deleted on shutdown, and treated as
 * gone when stale, so a client that is killed outright cannot leave the pad deaf. */
static void mark_client_alive(bool alive)
{
    char path[512];

    snprintf(path, sizeof path, "%s/g13-sdk-client", runtime_dir());
    if (alive) {
        FILE *handle = fopen(path, "w");
        if (handle) {
            fputc('\n', handle);
            fclose(handle);
        }
    } else {
        remove(path);
    }
}

#endif

/* ------------------------------------------------------------------ transports */

#if G13_TCP_TRANSPORT

/* One connection to the bridge, which relays frames one way and button events the other. */
static g13_fd open_screen(void)
{
    static bool started = false;
    const char *setting = getenv("G13_LCD_TCP");
    char host[128] = "127.0.0.1";
    char port[16] = BRIDGE_DEFAULT_PORT;
    struct addrinfo hints;
    struct addrinfo *found = NULL;
    g13_fd fd = G13_INVALID;

#if defined(G13_WINDOWS)
    if (!started) {
        WSADATA data;
        if (WSAStartup(MAKEWORD(2, 2), &data) != 0) {
            return G13_INVALID;
        }
        started = true;
    }
#else
    (void)started;
#endif

    if (setting && *setting) {
        const char *colon = strrchr(setting, ':');
        if (colon && (size_t)(colon - setting) < sizeof host) {
            memcpy(host, setting, (size_t)(colon - setting));
            host[colon - setting] = '\0';
            snprintf(port, sizeof port, "%s", colon + 1);
        } else {
            snprintf(host, sizeof host, "%s", setting);
        }
    }

    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    if (getaddrinfo(host, port, &hints, &found) != 0 || !found) {
        return G13_INVALID;
    }

    fd = socket(found->ai_family, found->ai_socktype, found->ai_protocol);
    if (fd != G13_INVALID && connect(fd, found->ai_addr, (int)found->ai_addrlen) != 0) {
        char note[128];
        close_fd(fd);
        fd = G13_INVALID;
        snprintf(note, sizeof note - 1, "cannot reach g13-lcd-bridge on %s:%s", host, port);
        probe_log(note);
    }
    if (fd != G13_INVALID) {
        set_nonblocking(fd);
    }
    freeaddrinfo(found);
    return fd;
}

static g13_fd open_events(void)
{
    return screen_fd;   /* The bridge sends button events back down the same connection. */
}

#else

static g13_fd open_screen(void)
{
    char path[512];

    snprintf(path, sizeof path, "%s/g13-lcd", runtime_dir());
    /* Non-blocking, so a driver that is not running cannot hang the caller; opening a FIFO
     * with no reader fails with ENXIO, which is exactly the answer we want. */
    return open(path, O_WRONLY | O_NONBLOCK);
}

static g13_fd open_events(void)
{
    char path[512];
    struct sockaddr_un address;
    g13_fd fd;
    size_t length;

    snprintf(path, sizeof path, "%s/g13.sock", runtime_dir());
    length = strlen(path);
    if (length >= sizeof address.sun_path) {
        return G13_INVALID;
    }

    fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) {
        return G13_INVALID;
    }

    memset(&address, 0, sizeof address);
    address.sun_family = AF_UNIX;
    memcpy(address.sun_path, path, length + 1);

    if (connect(fd, (struct sockaddr *)&address, sizeof address) != 0) {
        close_fd(fd);
        return G13_INVALID;
    }
    set_nonblocking(fd);
    return fd;
}

#endif

/* ------------------------------------------------------------------ buttons */

static void read_events(void)
{
    char buffer[4096];
    long got;
    char *line;
    char *save = NULL;

    if (event_fd == G13_INVALID) {
        return;
    }

    while ((got = g13_recv(event_fd, buffer, sizeof buffer - 1)) > 0) {
        buffer[got] = '\0';
        for (line = strtok_r(buffer, "\n", &save); line; line = strtok_r(NULL, "\n", &save)) {
            int code = 0;
            int value = 0;
            int present = 0;

            if (sscanf(line, "state driver %d", &present) == 1) {
                driver_present = present != 0;
                continue;
            }
            if (sscanf(line, "key %d %d", &code, &value) != 2) {
                continue;   /* other "state ..." lines are not ours either */
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
    char asked[64];
    char note[160];

    asked[0] = '\0';
    if (friendlyName) {
        wchar_to_ascii(friendlyName, asked, sizeof asked);
    }
    snprintf(note, sizeof note - 1, "LogiLcdInit(name=\"%s\", type=%u)", asked, lcdType);
    probe_log(note);

    char name[128];

    (void)lcdType;   /* MONO and EITHER both end up on this panel; COLOR cannot. */

    wchar_to_ascii(friendlyName, name, sizeof name);

    if (screen_fd == G13_INVALID) {
        screen_fd = open_screen();
    }
    if (event_fd == G13_INVALID) {
        event_fd = open_events();   /* Buttons are a bonus: the screen matters more. */
    }

    if (screen_fd != G13_INVALID) {
#if !G13_TCP_TRANSPORT
        mark_client_alive(true);
#endif
        return true;
    }
    return false;
}

bool LogiLcdIsConnected(unsigned lcdType)
{
    if (lcdType & LOGI_LCD_TYPE_MONO) {
        if (screen_fd == G13_INVALID) {
            screen_fd = open_screen();   /* The driver may have started since Init. */
            if (screen_fd != G13_INVALID) {
                event_fd = open_events();
            }
        }
        if (screen_fd == G13_INVALID) {
            return false;
        }
        read_events();          /* the TCP build hears "state driver" on this connection */
        return driver_present;
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
    /* The first frame is the one that matters: it says the game got as far as drawing. */
    static int reported = 0;
    if (reported++ == 0) {
        char note[256];
        snprintf(note, sizeof note - 1, "LogiLcdUpdate: first frame, line 0 = \"%.60s\"",
                 line_set[0] ? lines[0] : "(nothing)");
        probe_log(note);
    }

    static const char hex[] = "0123456789abcdef";
    char frame[8 + FRAME_BYTES * 2 + 2];
    char output[4096];
    size_t used = 0;
    int line;
    int y;

    if (screen_fd == G13_INVALID) {
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
    if (g13_send(screen_fd, output, used) < 0) {
        /* The far end went away: start again on the next update rather than writing forever
         * into a dead connection. */
        close_fd(screen_fd);
        screen_fd = G13_INVALID;
        if (event_fd != G13_INVALID && event_fd != screen_fd) {
            close_fd(event_fd);
        }
        event_fd = G13_INVALID;
        return;
    }

#if !G13_TCP_TRANSPORT
    mark_client_alive(true);   /* heartbeat: the daemon treats a stale file as gone */
#endif
}

void LogiLcdShutdown(void)
{
    if (screen_fd != G13_INVALID) {
        close_fd(screen_fd);
#if !G13_TCP_TRANSPORT
        mark_client_alive(false);
#endif
    }
    if (event_fd != G13_INVALID && event_fd != screen_fd) {
        close_fd(event_fd);
    }
    screen_fd = G13_INVALID;
    event_fd = G13_INVALID;
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
        snprintf(lines[lineNumber], sizeof lines[lineNumber], "%s", text);
    }
    line_set[lineNumber] = true;
    return true;
}
