package com.booker.g13;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The screen as the driver sees it: a 160x48 one bit framebuffer of which the top 43 rows
 * are visible (the panel is 160x43, so anything below row 42 is off the glass).
 *
 * This mirrors g13lcd.py on the daemon side - same layout, same drawing helpers, same line
 * protocol - so what the tool previews is what the panel shows. FrameTest renders the same
 * layout on both sides and compares the bytes, which is what keeps them honest.
 */
public class Lcd {

    /** Framebuffer width in pixels. */
    public static final int WIDTH = 160;

    /** Framebuffer height in pixels: rows 43-47 are not on the glass. */
    public static final int HEIGHT = 48;

    /** How many rows of the framebuffer the panel actually shows. */
    public static final int VISIBLE_HEIGHT = 43;

    /** Size of one frame in bytes. */
    public static final int FRAME_BYTES = WIDTH * HEIGHT / 8;

    private final byte[] frame = new byte[FRAME_BYTES];

    /** One line of text placed on the screen. */
    public static class Placement {
        /** Left edge. */
        public final int x;
        /** Top edge. */
        public final int y;
        /** The text. */
        public final String text;

        Placement(final int x, final int y, final String text) {
            this.x = x;
            this.y = y;
            this.text = text;
        }

        /**
         * @return The right edge of the text, exclusive.
         */
        public int right() {
            return x + text.length() * LcdFont.ADVANCE;
        }
    }

    /** Blanks the frame. */
    public void clear() {
        java.util.Arrays.fill(frame, (byte) 0);
    }

    /**
     * @param x Column, 0-159.
     * @param y Row, 0-47.
     * @param on true for ink.
     */
    public void setPixel(final int x, final int y, final boolean on) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
            return;
        }
        final int index = x + (y / 8) * WIDTH;
        final int bit = y % 8;
        if (on) {
            frame[index] |= (byte) (1 << bit);
        } else {
            frame[index] &= (byte) ~(1 << bit);
        }
    }

    /**
     * @param x Column.
     * @param y Row.
     * @return true if that pixel has ink.
     */
    public boolean pixel(final int x, final int y) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
            return false;
        }
        return (frame[x + (y / 8) * WIDTH] & (1 << (y % 8))) != 0;
    }

    /**
     * Fills a rectangle, clipped to the frame.
     * @param x0 Left, inclusive.
     * @param y0 Top, inclusive.
     * @param x1 Right, exclusive.
     * @param y1 Bottom, exclusive.
     */
    public void rect(final int x0, final int y0, final int x1, final int y1) {
        fill(x0, y0, x1, y1, true);
    }

    /**
     * Fills or clears a rectangle, clipped to the frame.
     * @param x0 Left, inclusive.
     * @param y0 Top, inclusive.
     * @param x1 Right, exclusive.
     * @param y1 Bottom, exclusive.
     * @param on true to draw, false to erase.
     */
    public void fill(final int x0, final int y0, final int x1, final int y1, final boolean on) {
        for (int y = Math.max(0, y0); y < Math.min(HEIGHT, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(WIDTH, x1); x++) {
                setPixel(x, y, on);
            }
        }
    }

    /**
     * Draws a bar: a hollow track with a fill proportional to the value.
     *
     * The fill is derived from the track's interior, so it cannot spill over whatever is
     * placed next to it, and out-of-range percentages clamp. Same maths as draw_bar in
     * g13lcd.py.
     *
     * @param x Left edge of the track.
     * @param y Top edge of the track.
     * @param width Track width.
     * @param height Track height.
     * @param percent How full, 0-100.
     */
    public void bar(final int x, final int y, final int width, final int height, final double percent) {
        final double clamped = Math.max(0, Math.min(100, percent));
        rect(x, y, x + width, y + height);
        fill(x + 1, y + 1, x + width - 1, y + height - 1, false);
        final int interior = Math.max(0, width - 2);
        final int filled = (int) (interior * clamped / 100);
        if (filled > 0) {
            rect(x + 1, y + 1, x + 1 + filled, y + height - 1);
        }
    }

    /**
     * Draws text with the driver's own font.
     * @param x Left edge.
     * @param y Top edge.
     * @param message The text; unknown characters draw as spaces.
     */
    public void text(final int x, final int y, final String message) {
        int cursor = x;
        for (int index = 0; index < message.length(); index++) {
            final char character = message.charAt(index);
            for (int column = 0; column < 5; column++) {
                for (int row = 0; row < LcdFont.HEIGHT; row++) {
                    if (LcdFont.pixel(character, column, row)) {
                        setPixel(cursor + column, y + row, true);
                    }
                }
            }
            cursor += LcdFont.ADVANCE;
        }
    }

    /**
     * @return The frame as 1920 hex digits, the form the driver's #bitmap command takes.
     */
    public String hex() {
        final StringBuilder text = new StringBuilder(FRAME_BYTES * 2);
        for (final byte value : frame) {
            text.append(String.format("%02x", value & 0xFF));
        }
        return text.toString();
    }

    /**
     * @param hex 1920 hex digits.
     * @return A frame with those pixels.
     */
    public static Lcd fromHex(final String hex) {
        final Lcd lcd = new Lcd();
        if (hex == null || hex.length() != FRAME_BYTES * 2) {
            return lcd;
        }
        for (int index = 0; index < FRAME_BYTES; index++) {
            lcd.frame[index] = (byte) Integer.parseInt(hex.substring(index * 2, index * 2 + 2), 16);
        }
        return lcd;
    }

    /**
     * Reads the lines the daemon publishes and returns the screen they describe.
     * @param lines The protocol lines: a #bitmap frame plus any #text placements.
     * @return The frame, or null when there is no frame in them.
     */
    public static Lcd of(final List<String> lines) {
        for (final String line : lines) {
            if (line.startsWith("#bitmap ")) {
                return fromHex(line.substring("#bitmap ".length()).trim());
            }
        }
        return null;
    }

    /**
     * @param lines The protocol lines.
     * @return Every #text placement in them.
     */
    public static List<Placement> placements(final List<String> lines) {
        final List<Placement> placements = new ArrayList<>();
        for (final String line : lines) {
            if (!line.startsWith("#text ")) {
                continue;
            }
            final String[] parts = line.split(" ", 4);
            if (parts.length < 4) {
                continue;
            }
            try {
                placements.add(new Placement(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        parts[3]));
            } catch (NumberFormatException e) {
                // Malformed line: ignore it.
            }
        }
        return placements;
    }

    /**
     * The screen as an image, with ink drawn in the given colour.
     * @param ink The colour of a lit pixel.
     * @param background The colour behind it.
     * @param visibleOnly true to show only the rows the panel shows.
     * @return The image.
     */
    public BufferedImage image(final java.awt.Color ink, final java.awt.Color background,
            final boolean visibleOnly) {
        final int height = visibleOnly ? VISIBLE_HEIGHT : HEIGHT;
        final BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < WIDTH; x++) {
                image.setRGB(x, y, (pixel(x, y) ? ink : background).getRGB());
            }
        }
        return image;
    }
}
