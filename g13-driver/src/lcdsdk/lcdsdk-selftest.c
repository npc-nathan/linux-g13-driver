/*
 * lcdsdk-selftest.c - exercises the Logitech LCD SDK implementation without a device.
 *
 * Point G13_LCD_DIR at a scratch directory holding a regular file called g13-lcd (the
 * driver's endpoint) and a socket called g13.sock, and this checks the whole surface: the
 * return values, the four text lines, the background bitmap, and the button query. The
 * harness scripts/lcdsdk-test.py sets that up, serves the button events, and then checks
 * the bytes that came out.
 */
#include "LogitechLcd.h"

#include <stdio.h>
#include <string.h>

static int failures = 0;

static void check(const char *what, int ok)
{
    printf("%-54s %s\n", what, ok ? "ok" : "FAIL");
    if (!ok) {
        failures++;
    }
}

static void put(uint8_t *bitmap, int x, int y)
{
    bitmap[y * LOGI_LCD_MONO_WIDTH + x] = 255;
}

int main(void)
{
    static const uint16_t app_name[] = { 'S', 'E', 'L', 'F', 'T', 'E', 'S', 'T', 0 };
    static const uint16_t text_ammo[] = { 'A', 'M', 'M', 'O', ' ', '2', '4', 0 };
    static const uint16_t text_obj[] = { 'D', 'E', 'L', 'I', 'V', 'E', 'R', 0 };
    static const uint16_t text_nav[] = { 'N', 'E', 'X', 'T', ' ', '3', '0', '0', 'm', 0 };
    static const uint16_t text_hp[] = { 'H', 'P', ' ', '9', '8', 0 };
    static uint8_t bitmap[LOGI_LCD_MONO_WIDTH * LOGI_LCD_MONO_HEIGHT];
    int x;

    check("init reports success", LogiLcdInit(app_name, LOGI_LCD_TYPE_MONO));
    check("a monochrome panel is connected", LogiLcdIsConnected(LOGI_LCD_TYPE_MONO));
    check("a colour panel is not connected", !LogiLcdIsConnected(LOGI_LCD_TYPE_COLOR));

    check("all four text lines are accepted",
          LogiLcdMonoSetText(0, text_ammo) && LogiLcdMonoSetText(1, text_obj) &&
          LogiLcdMonoSetText(2, text_nav) && LogiLcdMonoSetText(3, text_hp));
    check("a fifth line is refused", !LogiLcdMonoSetText(4, text_ammo));
    check("a negative line is refused", !LogiLcdMonoSetText(-1, text_ammo));
    check("a plain C string is accepted", G13LcdSetTextA(1, "OBJECTIVE")); 

    check("colour text reports failure",
          !LogiLcdColorSetText(0, text_ammo, 255, 0, 0));
    check("a colour title reports failure", !LogiLcdColorSetTitle(app_name, 1, 2, 3));
    check("a colour background reports failure", !LogiLcdColorSetBackground(bitmap));

    /* A frame with something in every direction: a border and a solid block, so the
     * harness can check pixels on all four edges and in the middle. */
    memset(bitmap, 0, sizeof bitmap);
    for (x = 0; x < LOGI_LCD_MONO_WIDTH; x++) {
        put(bitmap, x, 0);
        put(bitmap, x, LOGI_LCD_MONO_HEIGHT - 1);
    }
    for (int y = 0; y < LOGI_LCD_MONO_HEIGHT; y++) {
        put(bitmap, 0, y);
        put(bitmap, LOGI_LCD_MONO_WIDTH - 1, y);
    }
    for (int y = 20; y < 30; y++) {
        for (x = 20; x < 30; x++) {
            put(bitmap, x, y);
        }
    }
    check("a background is accepted", LogiLcdMonoSetBackground(bitmap));
    check("a null background is refused", !LogiLcdMonoSetBackground(NULL));

    LogiLcdUpdate();

    check("button 0 (L1) is pressed", LogiLcdIsButtonPressed(LOGI_LCD_MONO_BUTTON_0));
    check("button 2 (L3) is pressed", LogiLcdIsButtonPressed(LOGI_LCD_MONO_BUTTON_2));
    check("button 1 (L2) is not pressed", !LogiLcdIsButtonPressed(LOGI_LCD_MONO_BUTTON_1));
    check("button 3 (L4) is not pressed", !LogiLcdIsButtonPressed(LOGI_LCD_MONO_BUTTON_3));
    check("a colour button is not pressed",
          !LogiLcdIsButtonPressed(LOGI_LCD_COLOR_BUTTON_OK));

    G13LcdSetTextA(1, "PLAIN C");
    LogiLcdUpdate();

    LogiLcdShutdown();
    check("shutdown is clean", 1);

    printf("%s\n", failures ? "LCDSDK SELFTEST: FAILURES" : "LCDSDK SELFTEST: all checks passed");
    return failures ? 1 : 0;
}
