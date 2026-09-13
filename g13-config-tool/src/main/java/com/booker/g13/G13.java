package com.booker.g13;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * The main class for the G13 Configuration application.
 * It builds the main UI, orchestrates the different panels, and handles loading
 * and mapping of key bindings.
 */
public class G13 extends JPanel {

	private static final long serialVersionUID = 1L;
	
	/**
	 * The application version, retrieved from the JAR's manifest file. Defaults to "Development".
	 */
	public static final String VERSION = G13.class.getPackage().getImplementationVersion() != null 
			? G13.class.getPackage().getImplementationVersion() 
			: "Development";
	
	/**
	 * The maximum number of macros that can be configured.
	 */
	private static final int MAX_MACROS = 200;
	
	// UI Components
	private final ImageMap g13Label = new ImageMap(); // The interactive G13 keypad image.
	private final KeybindPanel keybindPanel = new KeybindPanel(); // Panel for editing key bindings.
	private final MacroEditorPanel macroEditorPanel = new MacroEditorPanel(); // Panel for editing macros.
	
	// Data storage
	private final Properties[] keyBindings = new Properties[4]; // Holds the 4 binding profiles (M1, M2, M3, MR).
	private final Properties[] macros = new Properties[MAX_MACROS]; // Holds all configured macros.
	/** The profile currently shown on the keypad, for refreshing labels after edits. */
	private int currentProfile = 0;
	/** One toggle per profile: loads it here and activates it on the device. */
	private final JPanel profilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private final JToggleButton[] profileButtons = new JToggleButton[Key.PROFILE_COUNT];

	/** One line of feedback: what record mode is waiting for, or what was recorded. */
	private final JLabel statusLabel = new JLabel("Ready");

	/** The G13 key code the MR button reports. */
	private static final int MR_KEY_CODE = 32;

	/** How long to wait before re-reading the active profile, in milliseconds. */
	private static final int PROFILE_POLL_MS = 1000;

	/** What record mode is waiting for. */
	private enum RecordState {
		/** Not recording. */
		IDLE,
		/** Waiting for the pad key to program. */
		PAD_KEY,
		/** Waiting for the key on the keyboard to map it to. */
		TARGET_KEY
	}

	private RecordState recordState = RecordState.IDLE;
	/** The pad key chosen in record mode, while waiting for the target key. */
	private Key recordKey;
	
	/**
	 * Constructor for the main G13 panel.
	 * Initializes layout, loads configuration, and sets up UI components and listeners.
	 */
	public G13() {
		setLayout(new BorderLayout());
		
		// Load all configurations and initialize the UI.
		loadConfiguration();
		
		g13Label.addListener(new ImageMapListener() {
			@Override
			public void selected(Key key) {
				// Every key is selected for editing here, the M buttons included; the
				// Profile selector is what changes which profile is loaded and active.
				keybindPanel.setSelectedKey(key);
			}

			@Override
			public void mouseover(Key key) {
				// Currently unused, but preserved for future functionality.
			}			
		});
		
		// --- UI Assembly ---
		final JPanel p = new JPanel(new BorderLayout());
		p.setBorder(BorderFactory.createTitledBorder("G13 Keypad"));
		p.add(g13Label, BorderLayout.CENTER);
		add(p, BorderLayout.CENTER);
		
		final JPanel rightPanel = new JPanel(new BorderLayout());
		final JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(createProfilePanel(), BorderLayout.NORTH);
		topPanel.add(keybindPanel, BorderLayout.CENTER);
		statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		topPanel.add(statusLabel, BorderLayout.SOUTH);
		rightPanel.add(topPanel, BorderLayout.NORTH);
		rightPanel.add(macroEditorPanel, BorderLayout.CENTER);
		add(rightPanel, BorderLayout.EAST);
		
		// Provide the macro data to the panels that need it.
		keybindPanel.setMacros(macros);
		macroEditorPanel.setMacros(macros);

		// Keep the keypad's key tooltips in step with edits made in the panel.
		keybindPanel.setBindingChangeListener(() -> refreshKeyLabels(currentProfile));

		// Open on the profile the driver is actually using.
		showProfile(Configs.loadActiveProfile());

		// React to presses on the pad, which is what makes record mode possible.
		new Events(this::onPadKey);

		// Capture the key to map while record mode is waiting for it. AWT only sees
		// keys that arrive while this application has focus, which is why record mode
		// raises the window.
		KeyboardFocusManager.getCurrentKeyboardFocusManager()
				.addKeyEventDispatcher(this::captureRecordedKey);

		// Follow profile changes made on the pad (M1-M3 switch on the device) while
		// this window is open, so the panel shows the profile the pad is using.
		new Timer(PROFILE_POLL_MS, e -> {
			final int active = Configs.loadActiveProfile();
			if (active != currentProfile) {
				showProfile(active);
			}
		}).start();
	}

	/**
	 * Handles a physical key change reported by the driver.
	 * @param g13KeyCode The G13 key code.
	 * @param pressed true for a press, false for a release.
	 */
	private void onPadKey(final int g13KeyCode, final boolean pressed) {
		if (!pressed) {
			return;
		}

		if (g13KeyCode == MR_KEY_CODE) {
			if (recordState == RecordState.IDLE) {
				startRecording();
			} else {
				cancelRecording();
			}
			return;
		}

		if (recordState != RecordState.PAD_KEY || Key.isMKey(g13KeyCode)) {
			// M1-M3 already switch profile on the device, and the poll above follows
			// it, so there is nothing to record for them.
			return;
		}

		final Key key = Key.getKeyFor(g13KeyCode);
		if (key == null) {
			return;
		}

		recordKey = key;
		keybindPanel.setSelectedKey(key);
		recordState = RecordState.TARGET_KEY;
		setStatus("Recording: press the key to map " + Key.profileKeyName(g13KeyCode)
				+ " to (Esc cancels)");
	}

	/** Arms record mode: the next pad key pressed is the one being programmed. */
	private void startRecording() {
		recordState = RecordState.PAD_KEY;
		recordKey = null;
		setStatus("Recording: press the pad key to program (Esc or MR cancels)");
		raiseWindow();
	}

	private void cancelRecording() {
		recordState = RecordState.IDLE;
		recordKey = null;
		setStatus("Recording cancelled");
	}

	/** Brings this window forward: AWT cannot see a key press without focus. */
	private void raiseWindow() {
		final Window window = SwingUtilities.getWindowAncestor(this);
		if (window == null) {
			return;
		}
		window.setVisible(true);
		window.toFront();
		window.requestFocus();
	}

	/**
	 * Captures the key record mode is waiting for.
	 * @param event The key event.
	 * @return true to consume the event, so it does not reach the rest of the UI.
	 */
	private boolean captureRecordedKey(final KeyEvent event) {
		if (recordState != RecordState.TARGET_KEY || event.getID() != KeyEvent.KEY_RELEASED) {
			return false;
		}

		if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
			cancelRecording();
			return true;
		}

		final int code = JavaToLinuxKeymapping.keyEventToCCode(event);
		if (code <= 0) {
			return false;
		}

		final String keyName = Key.profileKeyName(recordKey.getG13KeyCode());
		keybindPanel.recordPassthrough(recordKey, code);

		recordState = RecordState.IDLE;
		recordKey = null;
		setStatus("Recorded: " + keyName + " now sends " + JavaToLinuxKeymapping.cKeyCodeToString(code));
		return true;
	}

	private void setStatus(final String text) {
		statusLabel.setText(text);
	}

	/**
	 * Builds the profile selector: the single control that loads one of the four
	 * binding profiles and makes the device switch to it.
	 * @return The configured panel.
	 */
	private JPanel createProfilePanel() {
		profilePanel.setBorder(BorderFactory.createTitledBorder("Profile"));
		final ButtonGroup group = new ButtonGroup();

		for (int i = 0; i < profileButtons.length; i++) {
			final int profile = i;
			final JToggleButton button = new JToggleButton(JavaToLinuxKeymapping.mKeyShortName(i));
			button.setToolTipText("Load bindings-" + i + ".properties and activate it on the device");
			button.addActionListener(e -> activateProfile(profile));

			group.add(button);
			profileButtons[i] = button;
			profilePanel.add(button);
		}
		return profilePanel;
	}

	/**
	 * Shows a profile on the keypad without touching the device.
	 * @param profile The profile index (0-3).
	 */
	private void showProfile(int profile) {
		if (profile < 0 || profile >= keyBindings.length) {
			return;
		}

		mapBindings(profile);
		profileButtons[profile].setSelected(true);
	}

	/**
	 * Loads a profile for editing and makes the driver switch to it, by writing the
	 * same file the driver reads when a profile button is pressed.
	 * @param profile The profile index (0-3).
	 */
	private void activateProfile(int profile) {
		showProfile(profile);

		try {
			Configs.saveActiveProfile(profile);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Could not activate the profile: " + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Loads all key binding profiles and macros from configuration files.
	 * In case of an error, it displays a dialog to the user.
	 */
	private void loadConfiguration() {
		try {
			// Load the 4 binding profiles.
			for (int i = 0; i < keyBindings.length; i++) {
				keyBindings[i] = Configs.loadBindings(i);
			}
			
			// Load all possible macros.
			for (int i = 0; i < macros.length; i++) {
				macros[i] = Configs.loadMacro(i);
			}
			
			// Apply the first binding profile (M1) by default.
			mapBindings(0);
		}
		catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Failed to load configuration:\n" + e.getMessage(), "Configuration Error", JOptionPane.ERROR_MESSAGE);
            // The application could exit here as it's not usable without configuration.
            // System.exit(1);
		}
	}
		
	/**
	 * Applies a specific binding profile to the keypad UI.
	 * This method updates the visual representation of each key on the ImageMap
	 * to show what it is currently mapped to.
	 * @param bindingNum The index of the binding profile to apply (0-3).
	 */
	private void mapBindings(int bindingNum) {
		currentProfile = bindingNum;
		keybindPanel.setSelectedKey(null); // Deselect any key.
		keybindPanel.setBindings(bindingNum, keyBindings[bindingNum]);
		refreshKeyLabels(bindingNum);
	}

	/**
	 * Updates the per-key labels of the keypad view from a profile. Separate from
	 * {@link #mapBindings(int)} so that edits can refresh the keypad without
	 * resetting the key that is currently being edited.
	 * @param bindingNum The index of the binding profile to display (0-3).
	 */
	private void refreshKeyLabels(int bindingNum) {
		// Iterate through all possible G-keys to update their display text.
		for (int i = 0; i < 40; i++) { 
			final Key k = Key.getKeyFor(i);
			if (k == null) continue;

			String property = "G" + i;
			String val = keyBindings[bindingNum].getProperty(property);
			
			// Set default display values.
			k.setMappedValue("Unassigned");
			k.setRepeats("N/A");
			
			if (val != null && !val.isBlank()) {
				// The value string is parsed to determine the binding type and value.
				// Format: "p,k.keycode" for passthrough, "m,macroNum,repeats" for macro,
				// "mk,index" for an M key code.
				String[] parts = val.split("[,.]");
				if (parts.length < 2) continue; // Ignore invalid format.

				final String type = parts[0];
				
				try {
					if ("p".equals(type)) { // Passthrough key
						if (parts.length >= 3) {
							int keycode = Integer.parseInt(parts[2]);
							k.setMappedValue(JavaToLinuxKeymapping.cKeyCodeToString(keycode));
						}
					} else if ("m".equals(type)) { // Macro
						if (parts.length >= 3) {
							int macroNum = Integer.parseInt(parts[1]);
							if (macroNum >= 0 && macroNum < macros.length) {
								final String macroName = macros[macroNum].getProperty("name", "Unnamed Macro");
								boolean repeats = Integer.parseInt(parts[2]) != 0;
								k.setMappedValue("Macro: " + macroName);
								k.setRepeats(repeats ? "Yes" : "No");
							}
						}
					} else if ("mk".equals(type)) { // M key code
						int mKeyIndex = Integer.parseInt(parts[1]);
						k.setMappedValue("M Key: " + JavaToLinuxKeymapping.mKeyShortName(mKeyIndex));
					} else if ("x".equals(type)) { // Explicitly nothing
						k.setMappedValue("Disabled");
					}
				} catch (NumberFormatException e) {
					// Handle cases where the number in the property is malformed.
					System.err.println("Could not parse binding: " + val);
					k.setMappedValue("Parse Error");
				}
			}
		}
	}
	
	/**
	 * The main entry point for the application.
	 * @param args Command line arguments (not used).
	 */
	public static void main(String[] args) {
		// Ensure all UI operations are performed on the Event Dispatch Thread (EDT).
        SwingUtilities.invokeLater(() -> {
            try {
                // Set a modern look and feel for the UI.
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            final JFrame frame = new JFrame("G13 Configuration Tool, Version " + VERSION);
            frame.setIconImage(ImageMap.G13_KEYPAD.getImage());
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            final G13 g13 = new G13();	
            frame.getContentPane().add(g13, BorderLayout.CENTER);
            
            frame.pack(); // Size the frame to fit its contents.
            frame.setLocationRelativeTo(null); // Center the frame on the screen.
            frame.setVisible(true);
        });
	}
}