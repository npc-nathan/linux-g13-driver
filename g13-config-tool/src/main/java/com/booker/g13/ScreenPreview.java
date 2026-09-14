package com.booker.g13;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;

/**
 * The 160x43 screen, magnified, painted from the driver's own protocol lines.
 *
 * Shared by the Screen window and the applet designer so there is one picture of the panel in the
 * config tool: whatever produces the lines - the running daemon, or an applet being edited - this
 * turns them into the same image, including where text has landed on ink.
 */
public class ScreenPreview extends JComponent {

    private static final long serialVersionUID = 1L;

    /** How much the screen is magnified. */
    public static final int SCALE = 3;

    private transient Lcd lcd;
    private transient boolean[][] overlaps;
    private String note = "waiting for a screen";

    /** Builds the preview. */
    public ScreenPreview() {
        setPreferredSize(new Dimension(Lcd.WIDTH * SCALE + 2, Lcd.VISIBLE_HEIGHT * SCALE + 2));
        setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
    }

    /**
     * Shows a screen, from the lines the driver would be given.
     * @param lines The protocol: a `#bitmap` frame and its `#text` lines.
     * @return true when something was drawn.
     */
    public boolean show(final List<String> lines) {
        final Lcd base = Lcd.of(lines);
        if (base == null) {
            showMessage("nothing to draw in that screen");
            return false;
        }

        final boolean[][] hits = new boolean[Lcd.WIDTH][Lcd.HEIGHT];
        for (final Lcd.Placement placement : Lcd.placements(lines)) {
            // A line that runs past the bottom of the panel is clipped, whatever row its pixels
            // land on, so this is judged once for the whole block rather than per pixel.
            final boolean belowThePanel = placement.y + LcdFont.HEIGHT > Lcd.VISIBLE_HEIGHT;

            for (int index = 0; index < placement.text.length(); index++) {
                final char character = placement.text.charAt(index);
                for (int column = 0; column < 5; column++) {
                    for (int row = 0; row < LcdFont.HEIGHT; row++) {
                        if (!LcdFont.pixel(character, column, row)) {
                            continue;
                        }
                        final int x = placement.x + index * LcdFont.ADVANCE + column;
                        final int y = placement.y + row;
                        if (x < 0 || x >= Lcd.WIDTH || y < 0 || y >= Lcd.HEIGHT) {
                            continue;
                        }
                        if (base.pixel(x, y) || belowThePanel) {
                            // Over ink, or off the bottom of the panel: invisible either way.
                            hits[x][y] = true;
                        }
                    }
                }
            }
            base.text(placement.x, placement.y, placement.text);
        }

        this.lcd = base;
        this.overlaps = hits;
        repaint();
        return true;
    }

    /**
     * Says something instead of drawing, when there is no screen to show.
     * @param message The message.
     */
    public void showMessage(final String message) {
        this.lcd = null;
        this.overlaps = null;
        this.note = message;
        repaint();
    }

    /**
     * Where text has landed on ink, for a caller that wants to say so in words.
     * @return the grid, or null when nothing is drawn.
     */
    public boolean[][] overlaps() {
        return overlaps;
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Graphics2D g = (Graphics2D) graphics;
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());

        if (lcd == null) {
            g.setColor(Color.GRAY);
            g.drawString(note, 8, 20);
            return;
        }

        final BufferedImage image = new BufferedImage(Lcd.WIDTH, Lcd.VISIBLE_HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < Lcd.VISIBLE_HEIGHT; y++) {
            for (int x = 0; x < Lcd.WIDTH; x++) {
                Color colour = lcd.pixel(x, y) ? Color.WHITE : Color.BLACK;
                if (overlaps != null && overlaps[x][y]) {
                    // Text drawn over ink: on one-colour hardware that text is invisible, so the
                    // picture says so loudly.
                    colour = Color.ORANGE;
                }
                image.setRGB(x, y, colour.getRGB());
            }
        }

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, 1, 1, Lcd.WIDTH * SCALE, Lcd.VISIBLE_HEIGHT * SCALE, null);
    }
}
