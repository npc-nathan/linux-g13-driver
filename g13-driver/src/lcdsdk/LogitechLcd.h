/*
 * LogitechLcd.h - the Logitech LCD (GamePanel) SDK, implemented here for the G13 on Linux.
 *
 * Games and applets written against Logitech's SDK talk to LogitechLcd.dll, which ships with
 * Logitech Gaming Software on Windows. Nothing equivalent exists on Linux, which is why a
 * panel like the G13's shows nothing from games that do support it. This is that missing
 * piece: the same functions, the same types, the same button bits, forwarding to the G13
 * driver instead of to Logitech's LCD Manager.
 *
 * What the SDK is (and is not): it is a drawing API, not a data API. A program sets up to
 * four lines of text and/or one background image, calls LogiLcdUpdate() to show them, and
 * asks about the four buttons beside the screen. There is no notion of "ammo" or "objective"
 * in it - the program decides what the screen says.
 *
 * Layout mapping (monochrome, 160x43):
 *   LogiLcdMonoSetText(line, text)   -> line 0..3, drawn at x=3, y=2+10*line
 *   LogiLcdMonoSetBackground(bitmap) -> the whole 160x43 field, 8 bits per pixel,
 *                                      a pixel is on when its byte is >= 128
 *   LogiLcdUpdate()                  -> one full frame: background, then the text lines
 *   LogiLcdIsButtonPressed(mask)     -> MONO_BUTTON_0..3 are the G13's L1..L4
 *
 * Colour functions are present and return false: the G13's panel is monochrome. Callers are
 * expected to handle that, exactly as they would if they had asked for a colour screen that
 * is not connected.
 *
 * One client at a time: the driver shows whatever frame arrives last, so two programs both
 * calling LogiLcdUpdate() will fight over the screen. A manager that rotates between them
 * (Logitech's LCD Manager) is not implemented.
 */
#ifndef LOGITECH_LCD_H
#define LOGITECH_LCD_H

#include <stdbool.h>
#include <stdint.h>
#include <wchar.h>

/* The SDK's types and sizes, bit-for-bit. */
#define LOGI_LCD_TYPE_MONO   0x00000001
#define LOGI_LCD_TYPE_COLOR  0x00000002
#define LOGI_LCD_TYPE_EITHER (LOGI_LCD_TYPE_MONO | LOGI_LCD_TYPE_COLOR)

#define LOGI_LCD_MONO_BUTTON_0 0x00000001
#define LOGI_LCD_MONO_BUTTON_1 0x00000002
#define LOGI_LCD_MONO_BUTTON_2 0x00000004
#define LOGI_LCD_MONO_BUTTON_3 0x00000008
#define LOGI_LCD_MONO_BUTTON                                                  \
    (LOGI_LCD_MONO_BUTTON_0 | LOGI_LCD_MONO_BUTTON_1 | LOGI_LCD_MONO_BUTTON_2 | \
     LOGI_LCD_MONO_BUTTON_3)

#define LOGI_LCD_COLOR_BUTTON_LEFT   0x00000100
#define LOGI_LCD_COLOR_BUTTON_RIGHT  0x00000200
#define LOGI_LCD_COLOR_BUTTON_OK     0x00000400
#define LOGI_LCD_COLOR_BUTTON_CANCEL 0x00000800
#define LOGI_LCD_COLOR_BUTTON_UP     0x00001000
#define LOGI_LCD_COLOR_BUTTON_DOWN   0x00002000
#define LOGI_LCD_COLOR_BUTTON_MENU   0x00004000

#define LOGI_LCD_MONO_WIDTH   160
#define LOGI_LCD_MONO_HEIGHT  43
#define LOGI_LCD_COLOR_WIDTH  320
#define LOGI_LCD_COLOR_HEIGHT 240

/* The monochrome text lines the SDK offers. */
#define LOGI_LCD_MONO_LINES 4

#ifdef __cplusplus
extern "C" {
#endif

/* Main functions. */
bool LogiLcdInit(const uint16_t *friendlyName, unsigned lcdType);
bool LogiLcdIsConnected(unsigned lcdType);
bool LogiLcdIsButtonPressed(unsigned button);
void LogiLcdUpdate(void);
void LogiLcdShutdown(void);

/* Monochrome. */
bool LogiLcdMonoSetBackground(const uint8_t *monoBitmap);
bool LogiLcdMonoSetText(int lineNumber, const uint16_t *text);

/* Colour - present for compatibility, always false on this panel. */
bool LogiLcdColorSetBackground(const uint8_t *colorBitmap);
bool LogiLcdColorSetTitle(const uint16_t *text, int red, int green, int blue);
bool LogiLcdColorSetText(int lineNumber, const uint16_t *text, int red, int green, int blue);

/* Not part of the SDK: the same thing as LogiLcdMonoSetText for plain C strings, which is
 * what a Linux program - or our own tools - would rather call. */
bool G13LcdSetTextA(int lineNumber, const char *text);

#ifdef __cplusplus
}
#endif

#endif /* LOGITECH_LCD_H */
