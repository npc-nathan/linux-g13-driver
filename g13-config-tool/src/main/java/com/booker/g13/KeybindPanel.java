package com.booker.g13;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/**
 * A JPanel for configuring the binding of a single selected key.
 * It allows the user to choose between a passthrough key, a macro, an M key code,
 * or no assignment at all, and to override what the four M buttons do in the
 * profile that is currently open.
 */
public class KeybindPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	// --- UI Components for the Button Type selector ---
	private final JRadioButton noneButton = new JRadioButton("None (sends nothing)");
	private final JRadioButton passthroughButton = new JRadioButton("Pass Through");
	private final JRadioButton macroButton = new JRadioButton("Macro");
	private final JRadioButton mKeyButton = new JRadioButton("M Key event");
	private final JTextField passthroughText = new JTextField();
	private int passthroughCode = 0; // The Linux keycode for the passthrough key.
	private final JComboBox<Properties> macroSelectionBox = new JComboBox<>();
	private final JCheckBox repeatsCheckBox = new JCheckBox("Auto Repeat");
	private final JComboBox<String> mKeySelectionBox = new JComboBox<>();

	// --- UI Components for the M button overrides ---
	/** One row per M button: switch profile (default) or send something instead. */
	private final MButtonRow [] mButtonRows = new MButtonRow[Key.PROFILE_KEY_COUNT];

	// --- UI Components for Screen Color ---
	private final JButton colorChangeButton = new JButton("Click Here To Change");

	// --- State Variables ---
	private int bindingsId = -1; // The ID of the currently loaded binding profile (0-3).
	private Properties bindings; // The properties for the current binding profile.
	private Properties[] macros; // All available macros, for the dropdown list.
	private Key key = null; // The currently selected key being edited.
	/** The macro the selected key is bound to; applied once the macro list is loaded. */
	private int pendingMacroSelection = -1;
	/** Called after a binding is saved, so the keypad view can refresh. */
	private Runnable bindingChangeListener;

	/** A flag to prevent listeners from firing during programmatic data loading. */
	private volatile boolean loadingData = false;

	/**
	 * One row of the "M Buttons" section: what this M button does while the current
	 * profile is active, plus the detail widget for the chosen behaviour.
	 */
	private final class MButtonRow {

		/** Mode indices, in combo box order. */
		static final int MODE_SWITCH_PROFILE = 0;
		static final int MODE_PASS_THROUGH = 1;
		static final int MODE_MACRO = 2;
		static final int MODE_M_CODE = 3;

		private static final String CARD_EMPTY = "empty";
		private static final String CARD_KEY = "key";
		private static final String CARD_MACRO = "macro";
		private static final String CARD_CODE = "code";

		private final int index;
		private final JComboBox<String> modeBox = new JComboBox<>(new String[] {
				"Switch profile", "Pass through a key", "Play macro", "Send M key event" });
		private final JTextField keyField = new JTextField();
		private final JComboBox<Properties> macroBox = new JComboBox<>();
		private final JComboBox<String> codeBox = new JComboBox<>();
		private final JPanel detail = new JPanel(new CardLayout());
		private int capturedKeyCode = 0;
		/** The macro this row should show; applied once the macro list is loaded. */
		private int macroSelection = -1;

		MButtonRow(final int index) {
			this.index = index;

			keyField.setFocusTraversalKeysEnabled(false);
			keyField.setToolTipText("Click here and press the key M" + (index + 1) + " should send");
			for (int i = 0; i < Key.PROFILE_KEY_COUNT; i++) {
				codeBox.addItem(JavaToLinuxKeymapping.mKeyName(i));
			}
			macroBox.setRenderer(new MacroListCellRenderer());

			detail.add(new JLabel(" "), CARD_EMPTY);
			detail.add(keyField, CARD_KEY);
			detail.add(macroBox, CARD_MACRO);
			detail.add(codeBox, CARD_CODE);

			modeBox.addActionListener(e -> {
				showDetailCard();
				save();
			});
			keyField.addKeyListener(new KeyAdapter() {
				@Override
				public void keyReleased(KeyEvent event) {
					if (loadingData) return;
					loadingData = true;
					capturedKeyCode = JavaToLinuxKeymapping.keyEventToCCode(event);
					keyField.setText(JavaToLinuxKeymapping.cKeyCodeToString(capturedKeyCode));
					loadingData = false;
					save();
				}
			});
			macroBox.addActionListener(e -> save());
			codeBox.addActionListener(e -> save());
		}

		private void showDetailCard() {
			final CardLayout layout = (CardLayout) detail.getLayout();
			layout.show(detail, switch (modeBox.getSelectedIndex()) {
				case MODE_PASS_THROUGH -> CARD_KEY;
				case MODE_MACRO -> CARD_MACRO;
				case MODE_M_CODE -> CARD_CODE;
				default -> CARD_EMPTY;
			});
		}

		/** @return The properties key this button writes to, e.g. "G29". */
		private String property() {
			return "G" + (Key.PROFILE_KEY_M1 + index);
		}

		/**
		 * Writes the selected behaviour for this M button into the current profile.
		 * Switching profile is the default, so it stores nothing at all.
		 */
		private void save() {
			if (bindings == null || loadingData) {
				return;
			}

			switch (modeBox.getSelectedIndex()) {
				case MODE_PASS_THROUGH:
					if (capturedKeyCode > 0) {
						bindings.put(property(), "p,k." + capturedKeyCode);
					} else {
						bindings.remove(property());
					}
					break;
				case MODE_MACRO: {
					final int macroNum = macroBox.getSelectedIndex();
					if (macroNum >= 0) {
						macroSelection = macroNum;
						bindings.put(property(), "m," + macroNum + ",0");
					} else {
						bindings.remove(property());
					}
					break;
				}
				case MODE_M_CODE:
					bindings.put(property(), "mk," + codeBox.getSelectedIndex());
					break;
				default:
					bindings.remove(property());
					break;
			}

			saveProfile();
		}

		/**
		 * Fills the row from a binding value of the loaded profile.
		 * @param value The value, or null when the button switches profile.
		 */
		private void load(final String value) {
			loadingData = true;

			capturedKeyCode = 0;
			keyField.setText("");

			if (value == null || value.isBlank()) {
				modeBox.setSelectedIndex(MODE_SWITCH_PROFILE);
			} else {
				final String[] parts = value.split("[,.]");
				try {
					switch (parts[0]) {
						case "p":
							modeBox.setSelectedIndex(MODE_PASS_THROUGH);
							capturedKeyCode = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;
							keyField.setText(JavaToLinuxKeymapping.cKeyCodeToString(capturedKeyCode));
							break;
						case "m":
							modeBox.setSelectedIndex(MODE_MACRO);
							macroSelection = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
							applyMacroSelection();
							break;
						case "mk":
							modeBox.setSelectedIndex(MODE_M_CODE);
							codeBox.setSelectedIndex(parts.length >= 2 ? Integer.parseInt(parts[1]) : 0);
							break;
						default:
							modeBox.setSelectedIndex(MODE_SWITCH_PROFILE);
							break;
					}
				} catch (NumberFormatException e) {
					modeBox.setSelectedIndex(MODE_SWITCH_PROFILE);
				}
			}

			showDetailCard();
			loadingData = false;
		}

		/**
		 * Replaces the macro list offered by this row's macro mode, keeping the
		 * current selection if it still exists.
		 * @param availableMacros All macros, or null.
		 */
		private void setMacroList(final Properties[] availableMacros) {
			loadingData = true;
			final int selected = macroBox.getSelectedIndex();

			macroBox.removeAllItems();
			if (availableMacros != null) {
				for (final Properties p : availableMacros) {
					macroBox.addItem(p);
				}
			}

			if (selected >= 0) {
				macroSelection = selected;
			}
			applyMacroSelection();
			loadingData = false;
		}

		/**
		 * Shows the macro this row is bound to. The profile is loaded before the macro
		 * list exists, so this is called again once the list arrives.
		 */
		private void applyMacroSelection() {
			if (macroBox.getItemCount() == 0) {
				return;
			}
			final int index = macroSelection >= 0 && macroSelection < macroBox.getItemCount() ? macroSelection : 0;
			macroBox.setSelectedIndex(index);
		}
	}

	/**
	 * Constructs the KeybindPanel, setting up its UI and event listeners.
	 */
	public KeybindPanel() {
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createTitledBorder("Keybindings Panel"));

		for (int i = 0; i < mButtonRows.length; i++) {
			mButtonRows[i] = new MButtonRow(i);
		}

		setupUI();
        attachListeners();

		// Initially, no key is selected, so the panel is disabled.
		setSelectedKey(null);
	}

	/**
	 * Creates and arranges all UI components within the panel.
	 */
	private void setupUI() {
		add(createColorPanel(), BorderLayout.NORTH);

		final ButtonGroup buttonGroup = new ButtonGroup();
		buttonGroup.add(noneButton);
		buttonGroup.add(passthroughButton);
		buttonGroup.add(macroButton);
		buttonGroup.add(mKeyButton);

		// Disable focus traversal for the passthrough text field to capture all key events.
		passthroughText.setFocusTraversalKeysEnabled(false);

		for (int i = 0; i < Key.PROFILE_KEY_COUNT; i++) {
			mKeySelectionBox.addItem(JavaToLinuxKeymapping.mKeyName(i));
		}

		final JPanel grid = new JPanel(new GridLayout(0, 2, 5, 5)); // Layout with spacing
		grid.add(noneButton);
		grid.add(new JLabel(" ")); // Spacer
		grid.add(passthroughButton);
		grid.add(passthroughText);
		grid.add(macroButton);
		grid.add(macroSelectionBox);
		grid.add(new JLabel(" ")); // Spacer
		grid.add(repeatsCheckBox);
		grid.add(mKeyButton);
		grid.add(mKeySelectionBox);

		grid.setBorder(BorderFactory.createTitledBorder("Button Type"));

		// Use a custom renderer to display macro names in the combo box.
		macroSelectionBox.setRenderer(new MacroListCellRenderer());

		add(grid, BorderLayout.CENTER);
		add(createMButtonPanel(), BorderLayout.SOUTH);
	}

	/**
	 * Builds the M button section: per profile, each of M1-M4 either switches to its
	 * binding profile (the default) or sends something else instead. Mapping all four
	 * to keys gives four extra keys but nothing on the device switches profiles; the
	 * GUI and the active-profile file still can.
	 * @return The configured panel.
	 */
	private JPanel createMButtonPanel() {
		final JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
		panel.setBorder(BorderFactory.createTitledBorder("M Buttons (this profile)"));

		for (final MButtonRow row : mButtonRows) {
			panel.add(new JLabel(JavaToLinuxKeymapping.mKeyShortName(row.index)));
			panel.add(row.modeBox);
			panel.add(new JLabel(" ")); // Spacer under the button name
			panel.add(row.detail);
		}
		return panel;
	}

	/**
	 * Attaches all necessary event listeners to the UI components.
	 */
	private void attachListeners() {
		// Use lambda expressions for concise listener implementation.
		noneButton.addActionListener(e -> updateComponentStateAndSave());
		macroButton.addActionListener(e -> updateComponentStateAndSave());
		passthroughButton.addActionListener(e -> updateComponentStateAndSave());
		mKeyButton.addActionListener(e -> updateComponentStateAndSave());
		macroSelectionBox.addActionListener(e -> saveBindings());
		repeatsCheckBox.addActionListener(e -> saveBindings());
		mKeySelectionBox.addActionListener(e -> saveBindings());

		passthroughText.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent event) {
				if (loadingData) return;
				loadingData = true; // Prevent re-triggering while updating
				passthroughCode = JavaToLinuxKeymapping.keyEventToCCode(event);
				passthroughText.setText(JavaToLinuxKeymapping.cKeyCodeToString(passthroughCode));
				loadingData = false;
				saveBindings();
			}
		});
	}

	/**
	 * Registers a callback that runs after a binding is saved.
	 * @param listener The callback, or null to clear it.
	 */
	public void setBindingChangeListener(final Runnable listener) {
		this.bindingChangeListener = listener;
	}

    /**
     * A helper method to update the enabled state of components based on the selected
     * button type, and then trigger a save operation.
     */
    private void updateComponentStateAndSave() {
		// Switching to pass-through without a key yet: start from ESC so the field is
		// never left empty.
		if (passthroughButton.isSelected() && passthroughCode <= 0) {
			passthroughCode = 1;
			passthroughText.setText(JavaToLinuxKeymapping.cKeyCodeToString(passthroughCode));
		}
        updateComponentState();
        saveBindings();
    }

	/**
	 * Synchronises the enabled state of the per-type controls with the selected type.
	 */
	private void updateComponentState() {
        passthroughText.setEnabled(passthroughButton.isSelected());
        macroSelectionBox.setEnabled(macroButton.isSelected());
        repeatsCheckBox.setEnabled(macroButton.isSelected());
        mKeySelectionBox.setEnabled(mKeyButton.isSelected());
	}

	/**
	 * Populates the macro selection combo box with the available macros.
	 * @param macros An array of Properties, where each represents a macro.
	 */
	public void setMacros(final Properties[] macros) {
		loadingData = true;
		this.macros = macros;

		macroSelectionBox.removeAllItems();
		for (final Properties properties : macros) {
			macroSelectionBox.addItem(properties);
		}

		// A key bound to a macro may have been shown before the list existed.
		if (pendingMacroSelection >= 0 && macroSelectionBox.getItemCount() > pendingMacroSelection) {
			macroSelectionBox.setSelectedIndex(pendingMacroSelection);
		}

		loadingData = false;

		// The M button rows are profile settings, so they are usable whether or not a
		// key is selected.
		for (final MButtonRow row : mButtonRows) {
			row.setMacroList(macros);
		}
	}

	/**
	 * Loads a specific binding profile into the panel.
	 * @param propertyNum The ID of the binding profile (0-3).
	 * @param bindings The Properties object for the profile.
	 */
	public void setBindings(final int propertyNum, final Properties bindings) {
		loadingData = true;

		this.bindingsId = propertyNum;
		this.bindings = bindings;

		// Parse and set the background color from the properties.
		final String val = bindings.getProperty("color", "255,255,255");
		try {
			String[] parts = val.split(",");
			if (parts.length == 3) {
				int r = Integer.parseInt(parts[0].trim());
				int g = Integer.parseInt(parts[1].trim());
				int b = Integer.parseInt(parts[2].trim());
				colorChangeButton.setBackground(new Color(r, g, b));
			}
		} catch (NumberFormatException e) {
			System.err.println("Invalid color format in properties: " + val);
			colorChangeButton.setBackground(Color.WHITE); // Fallback to white.
		}

		loadingData = false;

		refreshMButtonOverrides();
		setSelectedKey(null); // Reset selection when bindings change.
	}

	/**
	 * Updates the M button rows to match the profile that is loaded.
	 */
	private void refreshMButtonOverrides() {
		for (final MButtonRow row : mButtonRows) {
			row.load(bindings == null ? null : bindings.getProperty(row.property()));
		}
	}

	/**
	 * Updates the panel's UI to reflect the configuration of the given key.
	 * This is the main method for controlling the panel's state.
	 * @param key The Key object to be edited, or null to disable the panel.
	 */
	public void setSelectedKey(final Key key) {
		this.key = key;
		loadingData = true;

		final boolean isKeySelected = (key != null);
		// Enable or disable all controls based on whether a key is selected.
		final JComponent[] all = { colorChangeButton, noneButton, macroButton, macroSelectionBox,
				passthroughButton, passthroughText, repeatsCheckBox, mKeyButton, mKeySelectionBox };
		for (final JComponent c : all) {
			c.setEnabled(isKeySelected);
		}

		if (!isKeySelected) {
			loadingData = false;
			return;
		}

		// Get the binding string for the selected key, e.g. "p,k.1", "m,5,1" or "mk,0".
		// A key that is absent from the file is unassigned, which is not the same as
		// being bound to ESC.
		final String propKey = "G" + key.getG13KeyCode();
		final String val = bindings.getProperty(propKey);

		if (val == null || val.isBlank()) {
			noneButton.setSelected(true);
			updateComponentState();
			loadingData = false;
			return;
		}

		final String[] parts = val.split("[,.]");
		final String type = parts.length > 0 ? parts[0] : "";

		try {
			if ("mk".equals(type)) { // M key code
				mKeyButton.setSelected(true);
				mKeySelectionBox.setSelectedIndex(parts.length >= 2 ? Integer.parseInt(parts[1]) : 0);
			} else if ("m".equals(type)) { // Macro type
				macroButton.setSelected(true);
				int macroNum = (parts.length >= 2) ? Integer.parseInt(parts[1]) : 0;
				pendingMacroSelection = macroNum;
				if (macroSelectionBox.getItemCount() > macroNum) {
					macroSelectionBox.setSelectedIndex(macroNum);
				}
				boolean repeats = (parts.length >= 3) && (Integer.parseInt(parts[2]) != 0);
				repeatsCheckBox.setSelected(repeats);
			} else if ("p".equals(type)) { // Passthrough type
				passthroughButton.setSelected(true);
				passthroughCode = (parts.length >= 3) ? Integer.parseInt(parts[2]) : 1; // Default keycode 1 (ESC).
				passthroughText.setText(JavaToLinuxKeymapping.cKeyCodeToString(passthroughCode));
			} else {
				noneButton.setSelected(true);
			}
		} catch(NumberFormatException | ArrayIndexOutOfBoundsException e) {
			System.err.println("Failed to parse binding property: " + val);
			noneButton.setSelected(true); // Fallback to a safe default.
		}

		updateComponentState();
		loadingData = false;
	}

	/**
	 * Opens a JColorChooser dialog to change the G13's screen color for the current profile.
	 */
	private void changeScreenColor() {
		final Color currentColor = colorChangeButton.getBackground();
		final Color newColor = JColorChooser.showDialog(this, "Choose Screen Color", currentColor);

		if (newColor == null) return; // The user cancelled the dialog.

		// Store color as an "R,G,B" string.
		bindings.setProperty("color", newColor.getRed() + "," + newColor.getGreen() + "," + newColor.getBlue());
		colorChangeButton.setBackground(newColor);

		try {
			Configs.saveBindings(bindingsId, bindings);
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Could not save color setting: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Factory method to create the color selection panel.
	 * @return The configured JPanel for color selection.
	 */
	private JPanel createColorPanel() {
		final JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
		p.setBorder(BorderFactory.createTitledBorder("Screen Color"));
		p.add(colorChangeButton);
		colorChangeButton.addActionListener(e -> changeScreenColor());
		return p;
	}

	/**
	 * Saves the current state of the UI controls as a binding for the selected key.
	 * This method is called whenever a relevant control is changed.
	 */
	private void saveBindings() {
		if (key == null || loadingData) {
			return; // Do nothing if no key is selected or if data is being loaded.
		}

		String prop = "G" + key.getG13KeyCode();
		if (noneButton.isSelected()) {
			// The key is unassigned: it must not send anything.
			bindings.remove(prop);
			key.setMappedValue("Unassigned");
			key.setRepeats("N/A");
		} else if (passthroughButton.isSelected()) {
			// Format the passthrough binding string and save it.
			String val = "p,k." + passthroughCode;
			bindings.put(prop, val);

			// Update the key's display properties for the ImageMap.
			key.setMappedValue(passthroughText.getText().trim());
			key.setRepeats("N/A");
		} else if (macroButton.isSelected()) {
			// Format the macro binding string and save it.
			int macroNum = macroSelectionBox.getSelectedIndex();
			int repeats = repeatsCheckBox.isSelected() ? 1 : 0;

			if (macroNum < 0) {
				// No macro chosen yet: do not write a binding that points at nothing.
				bindings.remove(prop);
				key.setMappedValue("Unassigned");
				key.setRepeats("N/A");
			} else {
				String val = "m," + macroNum + "," + repeats;
				bindings.put(prop, val);

				// Update the key's display properties.
				if (macros != null && macroNum < macros.length) {
					final String macroName = macros[macroNum].getProperty("name", "Unnamed Macro");
					key.setMappedValue("Macro: " + macroName);
					key.setRepeats(repeats == 1 ? "Yes" : "No");
				}
			}
		} else if (mKeyButton.isSelected()) {
			// Send one of the M key codes, exactly as the kernel would.
			final int mKeyIndex = mKeySelectionBox.getSelectedIndex();
			bindings.put(prop, "mk," + mKeyIndex);

			key.setMappedValue("M Key: " + JavaToLinuxKeymapping.mKeyShortName(mKeyIndex));
			key.setRepeats("N/A");
		}

		saveProfile();
	}

	/**
	 * Writes the current profile to disk and refreshes the keypad view.
	 */
	private void saveProfile() {
		try {
			Configs.saveBindings(bindingsId, bindings);
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Can't Save Bindings: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (bindingChangeListener != null) {
			bindingChangeListener.run();
		}
	}
}
