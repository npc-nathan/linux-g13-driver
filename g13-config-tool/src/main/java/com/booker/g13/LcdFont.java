package com.booker.g13;

/**
 * The 5x7 font the driver draws the LCD with, so the tool can render exactly what the
 * panel shows instead of approximating it with a system font.
 *
 * Copied from g13-driver/src/cpp/Font.h (ASCII 32..127, five bytes per character, each
 * byte a column with the top row in bit 0). FontTest asserts this table still matches that
 * file, so the two cannot drift apart.
 */
public final class LcdFont {

    /** Width of one character cell, including the one pixel gap. */
    public static final int ADVANCE = 6;

    /** Height of a glyph. */
    public static final int HEIGHT = 7;

    /** Five bytes per character, starting at ASCII 32. */
    private static final byte[] GLYPHS = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,   // '?' (32)
            (byte) 0x00, (byte) 0x00, (byte) 0x5F, (byte) 0x00, (byte) 0x00,   // '!' (33)
            (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x07, (byte) 0x00,   // '?' (34)
            (byte) 0x14, (byte) 0x7F, (byte) 0x14, (byte) 0x7F, (byte) 0x14,   // '#' (35)
            (byte) 0x24, (byte) 0x2A, (byte) 0x7F, (byte) 0x2A, (byte) 0x12,   // '$' (36)
            (byte) 0x23, (byte) 0x13, (byte) 0x08, (byte) 0x64, (byte) 0x62,   // '%' (37)
            (byte) 0x36, (byte) 0x49, (byte) 0x55, (byte) 0x22, (byte) 0x50,   // '&' (38)
            (byte) 0x00, (byte) 0x05, (byte) 0x03, (byte) 0x00, (byte) 0x00,   // ''' (39)
            (byte) 0x00, (byte) 0x1C, (byte) 0x22, (byte) 0x41, (byte) 0x00,   // '(' (40)
            (byte) 0x00, (byte) 0x41, (byte) 0x22, (byte) 0x1C, (byte) 0x00,   // ')' (41)
            (byte) 0x14, (byte) 0x08, (byte) 0x3E, (byte) 0x08, (byte) 0x14,   // '*' (42)
            (byte) 0x08, (byte) 0x08, (byte) 0x3E, (byte) 0x08, (byte) 0x08,   // '+' (43)
            (byte) 0x00, (byte) 0x50, (byte) 0x30, (byte) 0x00, (byte) 0x00,   // ',' (44)
            (byte) 0x08, (byte) 0x08, (byte) 0x08, (byte) 0x08, (byte) 0x08,   // '-' (45)
            (byte) 0x00, (byte) 0x60, (byte) 0x60, (byte) 0x00, (byte) 0x00,   // '.' (46)
            (byte) 0x20, (byte) 0x10, (byte) 0x08, (byte) 0x04, (byte) 0x02,   // '/' (47)
            (byte) 0x3E, (byte) 0x51, (byte) 0x49, (byte) 0x45, (byte) 0x3E,   // '0' (48)
            (byte) 0x00, (byte) 0x42, (byte) 0x7F, (byte) 0x40, (byte) 0x00,   // '1' (49)
            (byte) 0x42, (byte) 0x61, (byte) 0x51, (byte) 0x49, (byte) 0x46,   // '2' (50)
            (byte) 0x21, (byte) 0x41, (byte) 0x45, (byte) 0x4B, (byte) 0x31,   // '3' (51)
            (byte) 0x18, (byte) 0x14, (byte) 0x12, (byte) 0x7F, (byte) 0x10,   // '4' (52)
            (byte) 0x27, (byte) 0x45, (byte) 0x45, (byte) 0x45, (byte) 0x39,   // '5' (53)
            (byte) 0x3C, (byte) 0x4A, (byte) 0x49, (byte) 0x49, (byte) 0x30,   // '6' (54)
            (byte) 0x01, (byte) 0x71, (byte) 0x09, (byte) 0x05, (byte) 0x03,   // '7' (55)
            (byte) 0x36, (byte) 0x49, (byte) 0x49, (byte) 0x49, (byte) 0x36,   // '8' (56)
            (byte) 0x06, (byte) 0x49, (byte) 0x49, (byte) 0x29, (byte) 0x1E,   // '9' (57)
            (byte) 0x00, (byte) 0x36, (byte) 0x36, (byte) 0x00, (byte) 0x00,   // ':' (58)
            (byte) 0x00, (byte) 0x56, (byte) 0x36, (byte) 0x00, (byte) 0x00,   // ';' (59)
            (byte) 0x08, (byte) 0x14, (byte) 0x22, (byte) 0x41, (byte) 0x00,   // '<' (60)
            (byte) 0x14, (byte) 0x14, (byte) 0x14, (byte) 0x14, (byte) 0x14,   // '=' (61)
            (byte) 0x00, (byte) 0x41, (byte) 0x22, (byte) 0x14, (byte) 0x08,   // '>' (62)
            (byte) 0x02, (byte) 0x01, (byte) 0x51, (byte) 0x09, (byte) 0x06,   // '?' (63)
            (byte) 0x32, (byte) 0x49, (byte) 0x79, (byte) 0x41, (byte) 0x3E,   // '@' (64)
            (byte) 0x7E, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x7E,   // 'A' (65)
            (byte) 0x7F, (byte) 0x49, (byte) 0x49, (byte) 0x49, (byte) 0x36,   // 'B' (66)
            (byte) 0x3E, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x22,   // 'C' (67)
            (byte) 0x7F, (byte) 0x41, (byte) 0x41, (byte) 0x22, (byte) 0x1C,   // 'D' (68)
            (byte) 0x7F, (byte) 0x49, (byte) 0x49, (byte) 0x49, (byte) 0x41,   // 'E' (69)
            (byte) 0x7F, (byte) 0x09, (byte) 0x09, (byte) 0x09, (byte) 0x01,   // 'F' (70)
            (byte) 0x3E, (byte) 0x41, (byte) 0x49, (byte) 0x49, (byte) 0x7A,   // 'G' (71)
            (byte) 0x7F, (byte) 0x08, (byte) 0x08, (byte) 0x08, (byte) 0x7F,   // 'H' (72)
            (byte) 0x00, (byte) 0x41, (byte) 0x7F, (byte) 0x41, (byte) 0x00,   // 'I' (73)
            (byte) 0x20, (byte) 0x40, (byte) 0x41, (byte) 0x3F, (byte) 0x01,   // 'J' (74)
            (byte) 0x7F, (byte) 0x08, (byte) 0x14, (byte) 0x22, (byte) 0x41,   // 'K' (75)
            (byte) 0x7F, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,   // 'L' (76)
            (byte) 0x7F, (byte) 0x02, (byte) 0x0C, (byte) 0x02, (byte) 0x7F,   // 'M' (77)
            (byte) 0x7F, (byte) 0x04, (byte) 0x08, (byte) 0x10, (byte) 0x7F,   // 'N' (78)
            (byte) 0x3E, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x3E,   // 'O' (79)
            (byte) 0x7F, (byte) 0x09, (byte) 0x09, (byte) 0x09, (byte) 0x06,   // 'P' (80)
            (byte) 0x3E, (byte) 0x41, (byte) 0x51, (byte) 0x21, (byte) 0x5E,   // 'Q' (81)
            (byte) 0x7F, (byte) 0x09, (byte) 0x19, (byte) 0x29, (byte) 0x46,   // 'R' (82)
            (byte) 0x46, (byte) 0x49, (byte) 0x49, (byte) 0x49, (byte) 0x31,   // 'S' (83)
            (byte) 0x01, (byte) 0x01, (byte) 0x7F, (byte) 0x01, (byte) 0x01,   // 'T' (84)
            (byte) 0x3F, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x3F,   // 'U' (85)
            (byte) 0x1F, (byte) 0x20, (byte) 0x40, (byte) 0x20, (byte) 0x1F,   // 'V' (86)
            (byte) 0x3F, (byte) 0x40, (byte) 0x38, (byte) 0x40, (byte) 0x3F,   // 'W' (87)
            (byte) 0x63, (byte) 0x14, (byte) 0x08, (byte) 0x14, (byte) 0x63,   // 'X' (88)
            (byte) 0x07, (byte) 0x08, (byte) 0x70, (byte) 0x08, (byte) 0x07,   // 'Y' (89)
            (byte) 0x61, (byte) 0x51, (byte) 0x49, (byte) 0x45, (byte) 0x43,   // 'Z' (90)
            (byte) 0x00, (byte) 0x7F, (byte) 0x41, (byte) 0x41, (byte) 0x00,   // '[' (91)
            (byte) 0x02, (byte) 0x04, (byte) 0x08, (byte) 0x10, (byte) 0x20,   // '?' (92)
            (byte) 0x00, (byte) 0x41, (byte) 0x41, (byte) 0x7F, (byte) 0x00,   // ']' (93)
            (byte) 0x04, (byte) 0x02, (byte) 0x01, (byte) 0x02, (byte) 0x04,   // '^' (94)
            (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40, (byte) 0x40,   // '_' (95)
            (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x04, (byte) 0x00,   // '`' (96)
            (byte) 0x20, (byte) 0x54, (byte) 0x54, (byte) 0x54, (byte) 0x78,   // 'a' (97)
            (byte) 0x7F, (byte) 0x48, (byte) 0x44, (byte) 0x44, (byte) 0x38,   // 'b' (98)
            (byte) 0x38, (byte) 0x44, (byte) 0x44, (byte) 0x44, (byte) 0x20,   // 'c' (99)
            (byte) 0x38, (byte) 0x44, (byte) 0x44, (byte) 0x48, (byte) 0x7F,   // 'd' (100)
            (byte) 0x38, (byte) 0x54, (byte) 0x54, (byte) 0x54, (byte) 0x18,   // 'e' (101)
            (byte) 0x08, (byte) 0x7E, (byte) 0x09, (byte) 0x01, (byte) 0x02,   // 'f' (102)
            (byte) 0x0C, (byte) 0x52, (byte) 0x52, (byte) 0x52, (byte) 0x3E,   // 'g' (103)
            (byte) 0x7F, (byte) 0x08, (byte) 0x04, (byte) 0x04, (byte) 0x78,   // 'h' (104)
            (byte) 0x00, (byte) 0x44, (byte) 0x7D, (byte) 0x40, (byte) 0x00,   // 'i' (105)
            (byte) 0x20, (byte) 0x40, (byte) 0x44, (byte) 0x3D, (byte) 0x00,   // 'j' (106)
            (byte) 0x7F, (byte) 0x10, (byte) 0x28, (byte) 0x44, (byte) 0x00,   // 'k' (107)
            (byte) 0x00, (byte) 0x41, (byte) 0x7F, (byte) 0x40, (byte) 0x00,   // 'l' (108)
            (byte) 0x7C, (byte) 0x04, (byte) 0x18, (byte) 0x04, (byte) 0x78,   // 'm' (109)
            (byte) 0x7C, (byte) 0x08, (byte) 0x04, (byte) 0x04, (byte) 0x78,   // 'n' (110)
            (byte) 0x38, (byte) 0x44, (byte) 0x44, (byte) 0x44, (byte) 0x38,   // 'o' (111)
            (byte) 0x7C, (byte) 0x14, (byte) 0x14, (byte) 0x14, (byte) 0x08,   // 'p' (112)
            (byte) 0x08, (byte) 0x14, (byte) 0x14, (byte) 0x18, (byte) 0x7C,   // 'q' (113)
            (byte) 0x7C, (byte) 0x08, (byte) 0x04, (byte) 0x04, (byte) 0x08,   // 'r' (114)
            (byte) 0x48, (byte) 0x54, (byte) 0x54, (byte) 0x54, (byte) 0x20,   // 's' (115)
            (byte) 0x04, (byte) 0x3F, (byte) 0x44, (byte) 0x40, (byte) 0x20,   // 't' (116)
            (byte) 0x3C, (byte) 0x40, (byte) 0x40, (byte) 0x20, (byte) 0x7C,   // 'u' (117)
            (byte) 0x1C, (byte) 0x20, (byte) 0x40, (byte) 0x20, (byte) 0x1C,   // 'v' (118)
            (byte) 0x3C, (byte) 0x40, (byte) 0x30, (byte) 0x40, (byte) 0x3C,   // 'w' (119)
            (byte) 0x44, (byte) 0x28, (byte) 0x10, (byte) 0x28, (byte) 0x44,   // 'x' (120)
            (byte) 0x0C, (byte) 0x50, (byte) 0x50, (byte) 0x50, (byte) 0x3C,   // 'y' (121)
            (byte) 0x44, (byte) 0x64, (byte) 0x54, (byte) 0x4C, (byte) 0x44,   // 'z' (122)
            (byte) 0x00, (byte) 0x08, (byte) 0x36, (byte) 0x41, (byte) 0x00,   // '{' (123)
            (byte) 0x00, (byte) 0x00, (byte) 0x7F, (byte) 0x00, (byte) 0x00,   // '|' (124)
            (byte) 0x00, (byte) 0x41, (byte) 0x36, (byte) 0x08, (byte) 0x00,   // '}' (125)
    };

    private LcdFont() {
    }

    /**
     * @param character The character to look up.
     * @return The five column bytes for it.
     */
    public static byte[] glyph(final char character) {
        final int index = ((character < 32 || character > 127) ? 32 : character) - 32;
        final byte[] columns = new byte[5];
        for (int column = 0; column < 5; column++) {
            columns[column] = GLYPHS[index * 5 + column];
        }
        return columns;
    }

    /**
     * @param character The character to test.
     * @param column The column within the glyph, 0-4.
     * @param row The row within the glyph, 0-6.
     * @return true if that pixel is ink.
     */
    public static boolean pixel(final char character, final int column, final int row) {
        return (glyph(character)[column] & (1 << row)) != 0;
    }
}
