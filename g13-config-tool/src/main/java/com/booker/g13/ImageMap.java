package com.booker.g13;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

/**
 * A custom JLabel that displays an image of the G13 keypad and acts as an interactive map.
 * It detects mouse movements and clicks over specific key areas (defined by polygons)
 * and notifies listeners about these events.
 *
 * <p>The photo and the key outlines are drawn through one shared transform. The key
 * polygons in {@link Key} are expressed in the photo's own pixel coordinates, so the
 * transform scales and positions both together and mouse coordinates are mapped back
 * through the same transform for hit testing. (A JLabel icon would be drawn centred at
 * its natural size while the outlines stayed at the component origin, so the two drifted
 * apart as soon as the window was resized.)
 */
public class ImageMap extends JLabel {

	private static final long serialVersionUID = 1L;

	/** The background image of the G13 keypad. Also used as the application icon. */
	public static final ImageIcon G13_KEYPAD = ImageIconHelper.loadEmbeddedImage("/com/booker/g13/images/g13.gif");

	/** The photo itself. Key polygons live in this image's pixel coordinate system. */
	private final Image photo = G13_KEYPAD.getImage();

	/** A list of listeners to be notified of mouse events on keys. */
	private final List<ImageMapListener> listeners = new ArrayList<>();

	// --- Colors used for highlighting keys ---
	private final Color outlineColor = Color.red.darker(); // Color for the key outlines.
	private final Color selectedColor = new Color(0, 255, 0, 128); // Semi-transparent green for selected key.
	private final Color mouseoverColor = new Color(255, 0, 0, 128); // Semi-transparent red for hovered key.

	/** Where the tooltip is anchored, in photo (key) coordinates: below the keypad. */
	private static final double TOOLTIP_ANCHOR_X = 110;
	private static final double TOOLTIP_ANCHOR_Y = 550;
	/** Horizontal distance between the tooltip's label and value columns, in pixels. */
	private static final int TOOLTIP_VALUE_OFFSET = 135;

	private Key selected = null; // The currently clicked/selected key.
	private Key mouseover = null; // The key currently under the mouse cursor.

	/**
	 * Constructs the ImageMap component and initializes its mouse listeners.
	 */
	public ImageMap() {
		// No icon is passed to the label: paintComponent() draws the photo so that it can
		// be scaled in step with the key outlines.
		super();

		// At the natural photo size the map is 1:1, which is what the frame packs to.
		setPreferredSize(new Dimension(photo.getWidth(null), photo.getHeight(null)));

		addMouseMotionListener(new MouseMotionListener() {
			@Override
			public void mouseDragged(MouseEvent e) { /* Not used */ }

			@Override
			public void mouseMoved(MouseEvent e) {
				Key key = keyAt(e.getPoint());
				// Repaint only if the mouseover state changes to avoid unnecessary redraws.
				if (key == null && mouseover == null) {
					return;
				}
				
				if ((key != null && mouseover == null) || (key == null && mouseover != null)) {
					mouseover = key;
					repaint();
					fireMouseover();
					return;
				}
				
				if (key != null && mouseover != null && key.getG13KeyCode() != mouseover.getG13KeyCode()) {
					mouseover = key;
					repaint();
					fireMouseover();
				}
			}
		});
		
		addMouseListener(new MouseListener() {
			@Override
			public void mouseClicked(MouseEvent e) {
				Key key = keyAt(e.getPoint());
				
				// Repaint only if the selection state changes.
				if (key == null && selected == null) {
					return;
				}
				
				if ((key != null && selected == null) || (key == null && selected != null)) {
					selected = key;
					repaint();
					fireSelected();
					return;
				}
				
				if (key != null && selected != null && key.getG13KeyCode() != selected.getG13KeyCode()) {
					selected = key;
					repaint();
					fireSelected();
				}
			}

			// Unused mouse listener methods.
			@Override public void mousePressed(MouseEvent e) { }
			@Override public void mouseReleased(MouseEvent e) { }
			@Override public void mouseEntered(MouseEvent e) { }
			@Override public void mouseExited(MouseEvent e) {
				if (mouseover != null) {
					mouseover = null;
					repaint();
					fireMouseover();
				}
			}
		});
	}
	
	/**
	 * Adds a listener to be notified of events.
	 * @param listener The listener to add.
	 */
	public void addListener(final ImageMapListener listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}
	
	/**
	 * Removes a listener.
	 * @param listener The listener to remove.
	 */
	public void removeListener(final ImageMapListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}
	
	/**
	 * Notifies all registered listeners that a key has been selected.
	 */
	protected void fireSelected() {
		synchronized (listeners) {
			for (final ImageMapListener listener: listeners) {
				listener.selected(selected);
			}
		}
	}
	
	/**
	 * Notifies all registered listeners that the mouse is over a key.
	 */
	protected void fireMouseover() {
		synchronized (listeners) {
			for (final ImageMapListener listener: listeners) {
				listener.mouseover(mouseover);
			}
		}
	}

	/**
	 * Builds the transform from photo (key) coordinates to component coordinates:
	 * a uniform scale that keeps the photo's aspect ratio, centred in the component.
	 * @return The transform, or the identity transform if the component is not sized yet.
	 */
	private AffineTransform keyToComponent() {
		final int w = getWidth(), h = getHeight();
		final int pw = photo.getWidth(null), ph = photo.getHeight(null);
		if (w <= 0 || h <= 0 || pw <= 0 || ph <= 0) {
			return new AffineTransform();
		}

		final double scale = Math.min((double) w / pw, (double) h / ph);
		final AffineTransform tx = new AffineTransform();
		// Scale about the origin, then centre: point -> scale * point + offset.
		tx.translate((w - pw * scale) / 2.0, (h - ph * scale) / 2.0);
		tx.scale(scale, scale);
		return tx;
	}

	/**
	 * Converts a point in component coordinates to photo (key) coordinates, so that
	 * hit testing works at any window size.
	 * @param point The point in component coordinates.
	 * @return The equivalent point in key coordinates.
	 */
	private Point2D toKeySpace(final Point point) {
		try {
			return keyToComponent().createInverse().transform(point, null);
		} catch (NoninvertibleTransformException e) {
			return point;
		}
	}

	/**
	 * Finds the key under a point given in component coordinates.
	 * @param point The point in component coordinates.
	 * @return The key at that point, or null.
	 */
	Key keyAt(final Point point) {
		final Point2D keyPoint = toKeySpace(point);
		return Key.getKeyAt((int) Math.round(keyPoint.getX()), (int) Math.round(keyPoint.getY()));
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		final Graphics2D g2d = (Graphics2D) g.create();
        try {
        	g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        	g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        	// Photo and outlines share one transform, so they can never drift apart.
        	final AffineTransform tx = keyToComponent();
        	g2d.drawImage(photo, tx, null);

            g2d.transform(tx);
            // Paint highlights and outlines in order.
            paintSelected(g2d);
            paintMouseover(g2d);
            paintKeyOutlines(g2d);

            // The tooltip is drawn unscaled, in component coordinates, so it stays legible.
            g2d.setTransform(new AffineTransform());
            if (mouseover != null) {
                drawTooltip(g2d, mouseover);
            }
        } finally {
            g2d.dispose(); // Always dispose of the created graphics context.
        }
	}

	/**
	 * Paints the highlight for the currently selected key.
	 * @param g The graphics context to draw on.
	 */
	void paintSelected(final Graphics2D g) {
		if (selected == null) return;
		
		g.setColor(selectedColor);
		g.fill(selected.getShape());
		g.setColor(selectedColor.darker());
		g.draw(selected.getShape());
	}
	
	/**
	 * Paints the highlight and tooltip for the key currently under the mouse.
	 * @param g The graphics context to draw on.
	 */
	void paintMouseover(final Graphics2D g) {
		if (mouseover == null) return;
		
		g.setColor(mouseoverColor);
        g.fill(mouseover.getShape());
		g.setColor(mouseoverColor.darker());
		g.draw(mouseover.getShape());
	}

    /**
     * Draws a tooltip-like box with details about the hovered key.
     * @param g The graphics context to draw on, in component coordinates.
     * @param key The key to display information for.
     */
    private void drawTooltip(Graphics2D g, Key key) {
        String[][] lines;
        // The M buttons show what they do in the profile being edited.
        if (Key.isMKey(key.getG13KeyCode())) {
            final String mapped = key.getMappedValue();
            final boolean disabled = "Disabled".equals(mapped);
            final boolean bound = mapped != null && !mapped.isBlank()
                    && !disabled && !"Unassigned".equals(mapped) && !"Unknown".equals(mapped);
            final boolean profileButton = Key.isProfileButton(key.getG13KeyCode());
            lines = new String[][]{
                {"G13 Key", Key.profileKeyName(key.getG13KeyCode())},
                {"Configuration", profileButton
                        ? "bindings-" + Key.profileIndexFor(key.getG13KeyCode()) + ".properties"
                        : "macro record (KEY_MACRO_RECORD_START)"},
                {disabled ? "Disabled in this profile"
                        : (bound ? (profileButton && mapped.startsWith("M Key")
                                        ? "Switches profile, sends: " + mapped
                                        : (profileButton ? "Sends: " + mapped + " (no switch)"
                                                : "Sends: " + mapped))
                                : (profileButton ? "Switches to this profile (default)"
                                        : "Sends the macro record event (default)")), ""},
            };
        } else if (Key.isLegacyProfileKey(key.getG13KeyCode())) {
            lines = new String[][]{
                {"G13 Key", Key.profileKeyName(key.getG13KeyCode())},
                {"Display button", key.getG13KeyCode() == Key.PROFILE_KEY_LEGACY_L1 + Key.M_KEY_COUNT - 1
                        ? "Unused"
                        : "Legacy profile selector"},
                {"", ""},
            };
        } else {
            lines = new String[][]{
                {"G13 Key", "G" + key.getG13KeyCode()},
                {"Mapped Value",   key.getMappedValue()},
                {"Repeats",        key.getRepeats()},
            };
        }

        // Anchor the block below the keypad, converting from key to component coordinates.
        final Point2D anchor = keyToComponent().transform(new Point2D.Double(TOOLTIP_ANCHOR_X, TOOLTIP_ANCHOR_Y), null);
		final int x0 = (int) Math.round(anchor.getX());
		final int x1 = x0 + TOOLTIP_VALUE_OFFSET;
		int y = (int) Math.round(anchor.getY());
        g.setFont(getFont().deriveFont(Font.BOLD));

		for (final String [] line: lines) {
			g.setColor(mouseoverColor.darker());
			g.drawString(line[0], x0, y);
			g.setColor(mouseoverColor.brighter());
			g.drawString(line[1], x1, y);
			y += 2 * getFont().getSize(); // Move to the next line.
		}
    }

	/**
	 * Draws a subtle outline around all defined keys.
	 * @param g The graphics context to draw on.
	 */
	private void paintKeyOutlines(Graphics2D g) {
		g.setColor(outlineColor);
		for (final Key key: Key.getAllMasks()) {
			g.draw(key.getShape());
		}
	}
}
