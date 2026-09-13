#ifndef __CONSTANTS_H__
#define __CONSTANTS_H__

// USB device-specific constants for the Logitech G13
#define G13_INTERFACE 0         // The interface number for the G13.
#define G13_KEY_ENDPOINT 1      // The endpoint for reading key and joystick state.
#define G13_LCD_ENDPOINT 2      // The endpoint for writing to the LCD screen.
#define G13_KEY_READ_TIMEOUT 0  // Timeout for USB interrupt reads.
#define G13_VENDOR_ID 0x046d    // Logitech's Vendor ID.
#define G13_PRODUCT_ID 0xc21c   // The Product ID for the G13.
#define G13_REPORT_SIZE 8       // Size of the input report from the G13 (in bytes).
#define G13_LCD_BUFFER_SIZE 0x3c0 // Size of the buffer for the LCD screen.
#define G13_NUM_KEYS 40         // Total number of logical keys, including stick directions.
#define G13_NUM_PROFILES 3      // Binding profiles: M1, M2, M3. (MR is the record button.)
#define G13_NUM_M_KEYS 4        // The four M buttons: M1, M2, M3, MR.

// Linux event codes sent by the "M key" binding type (mk,<index>) and by profile
// buttons that the active profile overrides. These are the codes the mainline
// kernel emits for the same buttons (drivers/hid/hid-lg-g15.c, g13_keys_for_bits[]).
#define G13_KEYCODE_MACRO_RECORD_START 0x2b0
#define G13_KEYCODE_MACRO_PRESET1      0x2b3
#define G13_KEYCODE_MACRO_PRESET2      0x2b4
#define G13_KEYCODE_MACRO_PRESET3      0x2b5

/**
 * @enum stick_mode_t
 * @brief Defines the operating modes for the G13's joystick.
 */
enum stick_mode_t {
    STICK_KEYS = 0,   // Joystick movement emulates key presses (e.g., W, A, S, D).
    STICK_ABSOLUTE,   // Joystick provides absolute position values (like a gamepad).
    /*STICK_RELATIVE,*/ // A possible future mode for relative mouse movement.
};

/**
 * @enum stick_key_t
 * @brief Defines logical names for the four joystick directions.
 */
enum stick_key_t { STICK_LEFT, STICK_UP, STICK_DOWN, STICK_RIGHT };

/**
 * @enum G13_KEYS
 * @brief Defines the bit offset for each physical key within the G13's 8-byte input report.
 * The keys are grouped by which byte of the report they reside in (starting from byte 3).
 */
enum G13_KEYS {
    /* byte 3 of the report */
    G13_KEY_G1 = 0,
    G13_KEY_G2,
    G13_KEY_G3,
    G13_KEY_G4,

    G13_KEY_G5,
    G13_KEY_G6,
    G13_KEY_G7,
    G13_KEY_G8,

    /* byte 4 of the report */
    G13_KEY_G9,
    G13_KEY_G10,
    G13_KEY_G11,
    G13_KEY_G12,

    G13_KEY_G13,
    G13_KEY_G14,
    G13_KEY_G15,
    G13_KEY_G16,

    /* byte 5 of the report */
    G13_KEY_G17,
    G13_KEY_G18,
    G13_KEY_G19,
    G13_KEY_G20,

    G13_KEY_G21,
    G13_KEY_G22,
    G13_KEY_UNDEF1,        // An undefined/unused bit.
    G13_KEY_LIGHT_STATE,   // State of the backlight.

    /* byte 6 of the report */
    G13_KEY_BD,            // The "Backlight Dimmer" button.
    G13_KEY_L1,            // The L1 display button (legacy profile 0 selector).
    G13_KEY_L2,            // The L2 display button (legacy profile 1 selector).
    G13_KEY_L3,            // The L3 display button (legacy profile 2 selector).
    G13_KEY_L4,            // The L4 display button (legacy profile selector, now unused).
    G13_KEY_M1,            // The M1 profile button (selects bindings-0).
    G13_KEY_M2,            // The M2 profile button (selects bindings-1).
    G13_KEY_M3,            // The M3 profile button (selects bindings-2).

    /* byte 7 of the report */
    G13_KEY_MR,            // The "Macro Record" (MR) button: sends KEY_MACRO_RECORD_START.
    G13_KEY_LEFT,          // Left thumb button.
    G13_KEY_DOWN,          // Down thumb button.
    G13_KEY_TOP,           // Top thumb button (joystick press).

    G13_KEY_UNDEF3,        // An undefined/unused bit.
    G13_KEY_LIGHT,         // Another backlight-related key.
    G13_KEY_LIGHT2,        // Another backlight-related key.
    G13_KEY_MISC_TOGGLE    // A miscellaneous toggle key.
};

#endif