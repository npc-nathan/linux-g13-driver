#include <iostream> 
#include <fstream>
#include <vector>
#include <sys/stat.h>
#include <stdio.h>
#include <string.h>
#include <signal.h>
#include <stdlib.h>
#include <unistd.h>
#include <libusb-1.0/libusb.h>
#include <iomanip>
#include <linux/uinput.h>
#include <fcntl.h>
#include <algorithm>
#include <sstream>
#include <istream>
#include <chrono> 
#include <syslog.h> 

#include "Constants.h"
#include "G13.h"
#include "G13Action.h"
#include "PassThroughAction.h"
#include "MacroAction.h"
#include "Output.h"
#include "Font.h"
#include "ConfigPath.h" // NEW: Include Helper

extern volatile sig_atomic_t daemon_keep_running;
const int G13_MAX_MACROS = 200;

std::string trim_string(const std::string& str) {
    const std::string whitespace = " \t\n\r\f\v";
    size_t start = str.find_first_not_of(whitespace);
    if (start == std::string::npos) return "";
    size_t end = str.find_last_not_of(whitespace);
    return str.substr(start, end - start + 1);
}

// Linux event codes for the four M keys, in profile order (M1, M2, M3, MR).
static int mkeyCodeFor(int index) {
    switch (index) {
    case 0: return G13_KEYCODE_MACRO_PRESET1;
    case 1: return G13_KEYCODE_MACRO_PRESET2;
    case 2: return G13_KEYCODE_MACRO_PRESET3;
    case 3: return G13_KEYCODE_MACRO_RECORD_START;
    default: return 0;
    }
}

G13::G13(libusb_device *device) {
    this->device = device;
    this->loaded = 0;
    // Start on the profile that was last selected, so a driver restart does
    // not silently fall back to bindings-0.
    this->bindings = loadStoredProfile();
    this->stick_mode = STICK_KEYS;
    this->last_config_mtime = 0;
    this->last_config_check = std::chrono::steady_clock::now();

    actions.resize(G13_NUM_KEYS);
    for (int i = 0; i < G13_NUM_KEYS; i++) {
        actions[i] = std::make_unique<G13Action>();
    }
    explicit_bindings.assign(G13_NUM_KEYS, 0);

    if (libusb_open(device, &handle) != 0) {
        syslog(LOG_ERR, "Error opening G13 device");
        return;
    }

    if (libusb_kernel_driver_active(handle, 0) == 1) {
        if (libusb_detach_kernel_driver(handle, 0) == 0) {
            syslog(LOG_INFO, "Kernel driver detached");
        }
    }

    if (libusb_claim_interface(handle, 0) < 0) {
        syslog(LOG_ERR, "Cannot Claim Interface");
        return;
    }

    syslog(LOG_INFO, "Initializing G13 display...");
    unsigned char lcd_init_payload[] = { 0x01 };
    libusb_control_transfer(handle,
        (LIBUSB_REQUEST_TYPE_CLASS | LIBUSB_RECIPIENT_INTERFACE), 0x09, 0x0300, 0x0000,
        lcd_init_payload, sizeof(lcd_init_payload), 1000);

    setColor(128, 128, 128);
    clear_lcd_buffer();
    this->loaded = 1;

    init_fifo();
}

G13::~G13() {
    cleanup_fifo(); 
    if (!this->loaded) return;
    libusb_release_interface(this->handle, 0);
    libusb_close(this->handle);
}

void G13::start() {
    if (!this->loaded) return;
    draw_test_pattern();
    loadBindings();
    // Normalise the stored profile if it held a value that no longer exists (for
    // example the old MR slot 3), so the file always matches reality.
    storeProfile(bindings);
    keepGoing = 1;

    while (keepGoing && daemon_keep_running) {
        check_for_config_update();
        check_fifo();

        if (read() == -4) {
            break; 
        }
    }
}

void G13::stop() {
    if (!this->loaded) return;
    keepGoing = 0;
}

// --- Live-Reload Implementation ---
void G13::check_for_config_update() {
    // The main loop spins at least once per USB read timeout (~100 ms), so
    // checking the file's mtime on every pass is wasted work. Once a second
    // is far more than enough for interactive config edits.
    const auto now = std::chrono::steady_clock::now();
    if (now - last_config_check < std::chrono::seconds(1)) return;
    last_config_check = now;

    // NEW: Use ConfigPath helper
    std::string filename = ConfigPath::getBindingPath(bindings);
    
    struct stat file_stat;
    if (stat(filename.c_str(), &file_stat) == 0) {
        if (last_config_mtime != 0 && file_stat.st_mtime > last_config_mtime) {
            syslog(LOG_INFO, "Config file change detected. Reloading...");
            loadBindings();
        }
    }

    // The active profile can be changed from outside (the GUI writes the index,
    // scripts can too), so follow it here rather than only at startup.
    const int storedProfile = loadStoredProfile();
    if (storedProfile != bindings) {
        syslog(LOG_INFO, "Active profile changed to %d. Switching...", storedProfile);
        selectProfile(storedProfile);
    }
}

// --- Profile Selection ---
// The active profile index is persisted so the driver comes back on the same
// profile after a restart (and so it can be inspected or scripted).
int G13::loadStoredProfile() {
    std::ifstream file(ConfigPath::getActiveProfilePath());
    int profile = -1;

    if (file.is_open()) {
        file >> profile;
    }

    if (profile < 0 || profile >= G13_NUM_PROFILES) {
        return 0;
    }
    return profile;
}

void G13::storeProfile(int profile) {
    const std::string filename = ConfigPath::getActiveProfilePath();
    std::ofstream file(filename, std::ios::trunc);

    if (!file.is_open()) {
        syslog(LOG_WARNING, "Could not store active profile in %s", filename.c_str());
        return;
    }
    file << profile << "\n";
}

void G13::selectProfile(int profile) {
    if (profile < 0 || profile >= G13_NUM_PROFILES) return;

    bindings = profile;
    storeProfile(profile);
    loadBindings();
}

std::unique_ptr<Macro> G13::loadMacro(int num) {
    // NEW: Use ConfigPath helper
    std::string filename = ConfigPath::getMacroPath(num);
    std::ifstream file(filename);

    if (!file.is_open()) return nullptr;

    auto macro = std::make_unique<Macro>();
    macro->setId(num);
    std::string macro_name, macro_sequence;

    std::string line;
    while (getline(file, line)) {
        std::string trimmed_line = trim_string(line);
        if (!trimmed_line.empty() && trimmed_line[0] != '#') {
            size_t eq_pos = trimmed_line.find('=');
            if (eq_pos != std::string::npos) {
                std::string key = trim_string(trimmed_line.substr(0, eq_pos));
                std::string value = trim_string(trimmed_line.substr(eq_pos + 1));
                if (key == "name") macro_name = value;
                else if (key == "sequence") macro_sequence = value;
            }
        }
    }
    macro->setName(macro_name);
    macro->setSequence(macro_sequence);
    return macro;
}

void G13::parse_bindings_from_stream(std::istream& stream) {
    // (Logic remains identical to previous version, omitted for brevity but preserved)
    std::string line;
    while (std::getline(stream, line)) {
        std::string trimmed_line = trim_string(line);
        if (trimmed_line.empty() || trimmed_line[0] == '#') continue;
        size_t eq_pos = trimmed_line.find('=');
        if (eq_pos == std::string::npos) continue;
        std::string key = trim_string(trimmed_line.substr(0, eq_pos));
        std::string value = trim_string(trimmed_line.substr(eq_pos + 1));

        if (key == "color") {
            std::stringstream ss(value);
            std::string segment;
            int r, g, b;
            if (std::getline(ss, segment, ',') && (r = std::stoi(segment)) >= 0 &&
                std::getline(ss, segment, ',') && (g = std::stoi(segment)) >= 0 &&
                std::getline(ss, segment, ',') && (b = std::stoi(segment)) >= 0) {
                if (r <= 255 && g <= 255 && b <= 255) setColor(r, g, b);
            }
        }
        else if (!key.empty() && key.rfind("G", 0) == 0) {
            try {
                int gKey = std::stoi(key.substr(1));
                std::stringstream ss(value);
                std::string type;
                if (!std::getline(ss, type, ',')) continue;
                type = trim_string(type);

                if (type == "p") { 
                    std::string keytype_str;
                    if (!std::getline(ss, keytype_str, ',')) continue;
                    keytype_str = trim_string(keytype_str);
                    if (keytype_str.rfind("k.", 0) == 0) {
                        int keycode = std::stoi(keytype_str.substr(2));
                        if (gKey >= 0 && gKey < G13_NUM_KEYS) {
                             actions[gKey] = std::make_unique<PassThroughAction>(keycode);
                             explicit_bindings[gKey] = 1;
                        }
                    }
                }
                else if (type == "mk") {
                    // Emit the Linux code of one of the M buttons instead of a
                    // normal key. Used for "M key" bindings and for overriding a
                    // profile button inside a profile.
                    std::string index_str;
                    if (!std::getline(ss, index_str, ',')) continue;
                    int index = std::stoi(trim_string(index_str));
                    if (index >= 0 && index < G13_NUM_M_KEYS && gKey >= 0 && gKey < G13_NUM_KEYS) {
                        actions[gKey] = std::make_unique<PassThroughAction>(mkeyCodeFor(index));
                        explicit_bindings[gKey] = 1;
                    }
                }
                else if (type == "x") {
                    // Explicitly nothing: used for a profile button that should do
                    // nothing at all instead of switching profile.
                    if (gKey >= 0 && gKey < G13_NUM_KEYS) {
                        explicit_bindings[gKey] = 1;
                    }
                }
                else if (type == "m") { 
                    std::string macroId_str, repeats_str;
                    if (!std::getline(ss, macroId_str, ',') || !std::getline(ss, repeats_str, ',')) continue;
                    int macroId = std::stoi(trim_string(macroId_str));
                    int repeats = std::stoi(trim_string(repeats_str));

                    if (macroId >= 0 && macroId < G13_MAX_MACROS) {
                        auto macro = loadMacro(macroId);
                        if (macro && gKey >= 0 && gKey < G13_NUM_KEYS) {
                            actions[gKey] = std::make_unique<MacroAction>(macro->getSequence());
                            static_cast<MacroAction*>(actions[gKey].get())->setRepeats(repeats);
                            explicit_bindings[gKey] = 1;
                        }
                    }
                }
            } catch (...) {}
        }
    }
}

// Drops every binding of the previously loaded profile. Keys that were held are
// released first, otherwise a live reload could leave them stuck down.
void G13::resetActions() {
    if (actions.size() != G13_NUM_KEYS) return;

    for (int i = 0; i < G13_NUM_KEYS; i++) {
        if (actions[i] && actions[i]->isPressed()) {
            actions[i]->set(0);
        }
        actions[i] = std::make_unique<G13Action>();
        explicit_bindings[i] = 0;
    }

    // MR is the macro record button, so unless the profile binds it, it sends the
    // same event the kernel sends for that button.
    actions[G13_KEY_MR] = std::make_unique<PassThroughAction>(G13_KEYCODE_MACRO_RECORD_START);
}

void G13::loadBindings() {
    // The file is the whole truth for this profile: drop the previous bindings
    // (including any key the file no longer mentions) before parsing it.
    resetActions();

    // NEW: Use ConfigPath helper
    std::string filename = ConfigPath::getBindingPath(bindings);

    // Update timestamp for Live-Reload
    struct stat file_stat;
    if (stat(filename.c_str(), &file_stat) == 0) {
        last_config_mtime = file_stat.st_mtime;
    }

    std::ifstream file(filename);
    if (!file.is_open()) {
        syslog(LOG_WARNING, "Config file not found: %s. Creating defaults.", filename.c_str());
        
        // --- Create Default File ---
        // If the file doesn't exist, we write the default settings to it
        // so the user has something to edit in ~/.config/g13/
        std::ofstream outfile(filename);
        if (outfile.is_open()) {
             const std::string default_bindings = R"RAW(
# Default G13 Key Bindings
G19=p,k.42
G18=p,k.18
G17=p,k.16
G16=p,k.10
G9=p,k.3
G15=p,k.9
G8=p,k.2
G14=p,k.8
G7=p,k.15
G13=p,k.7
G12=p,k.6
G6=p,k.46
G11=p,k.5
G5=p,k.76
G10=p,k.4
G4=p,k.75
G3=p,k.81
G2=p,k.80
G1=p,k.79
G0=p,k.1
G39=p,k.31
color=0,0,255
G38=p,k.32
G37=p,k.30
G36=p,k.17
G35=p,k.11
G34=p,k.72
G33=p,k.71
G32=p,k.62
G31=p,k.61
G30=p,k.60
G29=p,k.59
G23=p,k.58
G22=p,k.57
G21=p,k.57
G20=p,k.50
)RAW";
            outfile << default_bindings;
            outfile.close();
            
            // Now parse what we just wrote
            std::stringstream ss(default_bindings);
            parse_bindings_from_stream(ss);
        } else {
            syslog(LOG_ERR, "Could not create config file: %s", filename.c_str());
        }
    }
    else {
        syslog(LOG_INFO, "Loading config file: %s", filename.c_str());
        parse_bindings_from_stream(file);
        file.close();
    }
}

void G13::setColor(int red, int green, int blue) {
    unsigned char usb_data[] = { 5, (unsigned char)red, (unsigned char)green, (unsigned char)blue, 0 };
    libusb_control_transfer(handle, LIBUSB_REQUEST_TYPE_CLASS | LIBUSB_RECIPIENT_INTERFACE, 9, 0x307, 0, usb_data, 5, 1000);
}

int G13::read() {
    // (Existing read implementation)
    unsigned char buffer[G13_REPORT_SIZE];
    int size;
    int error = libusb_interrupt_transfer(handle, LIBUSB_ENDPOINT_IN | G13_KEY_ENDPOINT, buffer, G13_REPORT_SIZE, &size, 100);

    if (error == LIBUSB_ERROR_NO_DEVICE) {
        syslog(LOG_ERR, "G13 device disconnected.");
        return -4; 
    }
    
    if (error && error != LIBUSB_ERROR_TIMEOUT) {
        syslog(LOG_ERR, "Error while reading keys: %s", libusb_error_name(error));
        return -1;
    }

    if (size == G13_REPORT_SIZE) {
        parse_joystick(buffer);
        parse_keys(buffer);
        UInput::send_event(EV_SYN, SYN_REPORT, 0);
    }
    return 0;
}

void G13::parse_joystick(unsigned char *buf) {
    // (Existing implementation)
    int stick_x = buf[1];
    int stick_y = buf[2];

    if (stick_mode == STICK_ABSOLUTE) {
        UInput::send_event(EV_ABS, ABS_X, stick_x);
        UInput::send_event(EV_ABS, ABS_Y, stick_y);
    } else if (stick_mode == STICK_KEYS) {
        int pressed[4];
        if (stick_y <= 96) { pressed[0] = 1; pressed[3] = 0; }
        else if (stick_y >= 160) { pressed[0] = 0; pressed[3] = 1; }
        else { pressed[0] = 0; pressed[3] = 0; }

        if (stick_x <= 96) { pressed[1] = 1; pressed[2] = 0; }
        else if (stick_x >= 160) { pressed[1] = 0; pressed[2] = 1; }
        else { pressed[1] = 0; pressed[2] = 0; }

        int codes[4] = {36, 37, 38, 39};
        for (int i = 0; i < 4; i++) {
            if (actions[codes[i]]) actions[codes[i]]->set(pressed[i]);
        }
    }
}

void G13::parse_key(int key, unsigned char *byte) {
    // (Existing implementation)
    if (key < 0 || key >= G13_NUM_KEYS) return;

    unsigned char actual_byte = byte[key / 8];
    unsigned char mask = 1 << (key % 8);
    int pressed = actual_byte & mask;

    switch (key) {
    // Profile buttons: M1, M2 and M3 sit on report bits 29-31 and select
    // bindings-0..2. Pressing the key also re-reads the file, so it doubles as a
    // manual reload. A profile that binds the button itself overrides the switch
    // and sends that binding instead.
    case G13_KEY_M1:
    case G13_KEY_M2:
    case G13_KEY_M3:
        if (!explicit_bindings[key] && pressed) {
            selectProfile(key - G13_KEY_M1);
            return;
        }
        break;

    // MR is not a profile button: it is the macro record trigger and is handled by
    // its default action (KEY_MACRO_RECORD_START) unless the profile binds it.

    // Legacy: before the M buttons were wired up, the display buttons (L1-L4,
    // bits 25-28) selected the profiles. L1-L3 keep working as aliases so existing
    // setups do not regress; L4 has no profile to select any more.
    case G13_KEY_L1:
    case G13_KEY_L2:
    case G13_KEY_L3:
        if (!explicit_bindings[key] && pressed) {
            selectProfile(key - G13_KEY_L1);
            return;
        }
        break;

    case G13_KEY_L4:
        return;

    // Stick directions are handled by parse_joystick().
    case 36: case 37: case 38: case 39:
        return;
    }

    if (actions[key]) {
        actions[key]->set(pressed);
    }
}

void G13::parse_keys(unsigned char *buf) {
    // (Existing implementation)
    parse_key(G13_KEY_G1, buf + 3);
    parse_key(G13_KEY_G2, buf + 3);
    parse_key(G13_KEY_G3, buf + 3);
    parse_key(G13_KEY_G4, buf + 3);
    parse_key(G13_KEY_G5, buf + 3);
    parse_key(G13_KEY_G6, buf + 3);
    parse_key(G13_KEY_G7, buf + 3);
    parse_key(G13_KEY_G8, buf + 3);
    parse_key(G13_KEY_G9, buf + 3);
    parse_key(G13_KEY_G10, buf + 3);
    parse_key(G13_KEY_G11, buf + 3);
    parse_key(G13_KEY_G12, buf + 3);
    parse_key(G13_KEY_G13, buf + 3);
    parse_key(G13_KEY_G14, buf + 3);
    parse_key(G13_KEY_G15, buf + 3);
    parse_key(G13_KEY_G16, buf + 3);
    parse_key(G13_KEY_G17, buf + 3);
    parse_key(G13_KEY_G18, buf + 3);
    parse_key(G13_KEY_G19, buf + 3);
    parse_key(G13_KEY_G20, buf + 3);
    parse_key(G13_KEY_G21, buf + 3);
    parse_key(G13_KEY_G22, buf + 3);
    parse_key(G13_KEY_BD, buf + 3);
    parse_key(G13_KEY_L1, buf + 3);
    parse_key(G13_KEY_L2, buf + 3);
    parse_key(G13_KEY_L3, buf + 3);
    parse_key(G13_KEY_L4, buf + 3);
    parse_key(G13_KEY_M1, buf + 3);
    parse_key(G13_KEY_M2, buf + 3);
    parse_key(G13_KEY_M3, buf + 3);
    parse_key(G13_KEY_MR, buf + 3);
    parse_key(G13_KEY_LEFT, buf + 3);
    parse_key(G13_KEY_DOWN, buf + 3);
    parse_key(G13_KEY_TOP, buf + 3);
    parse_key(G13_KEY_LIGHT, buf + 3);
}

void G13::clear_lcd_buffer() {
    memset(this->lcd_buffer, 0, G13_LCD_BUFFER_SIZE);
}

void G13::set_pixel(int x, int y, bool on) {
    if (x < 0 || x >= 160 || y < 0 || y >= 48) return;
    int index = x + (y / 8) * 160;
    int bit = y % 8;
    if (on) this->lcd_buffer[index] |= (1 << bit);
    else this->lcd_buffer[index] &= ~(1 << bit);
}

void G13::write_lcd() {
    // (Existing implementation)
    if (!this->loaded) return;

    unsigned char transfer_buffer[992];
    memset(transfer_buffer, 0, sizeof(transfer_buffer));
    transfer_buffer[0] = 0x03; 

    memcpy(transfer_buffer + 32, this->lcd_buffer, G13_LCD_BUFFER_SIZE);

    int actual_length;
    int error = libusb_interrupt_transfer(
        this->handle, 
        G13_LCD_ENDPOINT | LIBUSB_ENDPOINT_OUT, 
        transfer_buffer, 
        sizeof(transfer_buffer), 
        &actual_length, 
        1000 
    );

    if (error) {
        syslog(LOG_ERR, "LCD Write Error: %s", libusb_error_name(error));
    }
}

void G13::draw_test_pattern() {
    clear_lcd_buffer();
    for(int x=0; x<160; x++) { set_pixel(x, 0, true); set_pixel(x, 42, true); }
    for(int y=0; y<43; y++) { set_pixel(0, y, true); set_pixel(159, y, true); }
    write_text(10, 5,  "   LINUX G13 PROJECT");
    write_text(10, 15, "  POWER of OPENSOURCE");
    write_lcd();
    syslog(LOG_INFO, "LCD Test Pattern sent.");
}

void G13::write_char(int x, int y, char c) {
    // (Existing implementation)
    if (c < 32 || c > 127) c = 32; 
    int font_index = (c - 32) * 5;
    for (int col = 0; col < 5; col++) {
        uint8_t line = font_5x7[font_index + col];
        for (int row = 0; row < 7; row++) {
            if (line & (1 << row)) {
                set_pixel(x + col, y + row, true);
            }
        }
    }
}

void G13::write_text(int x, int y, const std::string& text) {
    int cursor_x = x;
    for (char c : text) {
        write_char(cursor_x, y, c);
        cursor_x += 6; 
    }
}

void G13::init_fifo() {
    // NEW: Use ConfigPath helper
    fifo_path = ConfigPath::getFifoPath();
    fifo_fd = -1;

    unlink(fifo_path.c_str());

    if (mkfifo(fifo_path.c_str(), 0666) != 0) {
        syslog(LOG_ERR, "Failed to create FIFO at %s: %s", fifo_path.c_str(), strerror(errno));
        return;
    }
    
    chmod(fifo_path.c_str(), 0666);

    fifo_fd = ::open(fifo_path.c_str(), O_RDWR | O_NONBLOCK);
    
    if (fifo_fd < 0) {
        syslog(LOG_ERR, "Failed to open FIFO: %s", strerror(errno));
    } else {
        syslog(LOG_INFO, "LCD Pipe created at %s", fifo_path.c_str());
    }
}

void G13::cleanup_fifo() {
    if (fifo_fd >= 0) {
        ::close(fifo_fd);
        fifo_fd = -1;
    }
    unlink(fifo_path.c_str());
}

void G13::check_fifo() {
    if (fifo_fd < 0) return;

    char buffer[4096];
    ssize_t bytesRead = ::read(fifo_fd, buffer, sizeof(buffer) - 1);

    if (bytesRead > 0) {
        buffer[bytesRead] = '\0'; 
        std::string input(buffer);
        if (!input.empty() && input.back() == '\n') {
            input.pop_back();
        }

        clear_lcd_buffer();
        
        std::stringstream ss(input);
        std::string line;
        int y = 0;
        int line_height = 8; 

        while (std::getline(ss, line)) {
            if (y + 7 > 48) break; 
            write_text(2, y, line); 
            y += line_height;
        }

        write_lcd();
    }
}