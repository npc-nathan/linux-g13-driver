#ifndef __G13_H__
#define __G13_H__

#include <string>
#include <vector>
#include <memory>
#include <map>
#include <istream>
#include <chrono>
#include <libusb-1.0/libusb.h>
#include <time.h> // For time_t

#include "Constants.h"
#include "G13Action.h"
#include "Macro.h"

class G13 {
private:
    std::vector<std::unique_ptr<G13Action>> actions;

    libusb_device        *device;       
    libusb_device_handle *handle;        
    int                   uinput_file;   

    int                   loaded;        
    volatile int          keepGoing;     

    stick_mode_t          stick_mode;    
    int                   stick_keys[4];  
    int                   bindings;       // Active profile index (0..G13_NUM_PROFILES-1).

    // 1 for keys the active profile assigns itself. Profile buttons that are
    // assigned by the profile send that binding instead of switching profile.
    std::vector<unsigned char> explicit_bindings;

    // The "mk" index each key is bound to, or -1 when it is not bound to an M key
    // code. Set by the active profile.
    std::vector<int> mkey_binding;

    // Reporters used to send the M button codes. They live outside the profile so a
    // profile reload cannot swallow the release of a press they started.
    std::unique_ptr<G13Action> mbutton_reporter[G13_NUM_M_KEYS];

    // The "mk" index a key's press reported, or -1. Physical state, not config, so
    // it survives profile reloads.
    std::vector<int> mbutton_down;

    unsigned char lcd_buffer[G13_LCD_BUFFER_SIZE];

    // Feature: Live-Reload
    time_t last_config_mtime;
    std::chrono::steady_clock::time_point last_config_check;
    void check_for_config_update();

    // --- Private Methods ---
    std::unique_ptr<Macro> loadMacro(int id);
    void parse_bindings_from_stream(std::istream& stream);
    void resetActions();
    int  read();
    void parse_joystick(unsigned char *buf);
    void parse_key(int key, unsigned char *byte);
    void parse_keys(unsigned char *buf);

    // Profile selection (bindings-<index>.properties)
    void selectProfile(int profile);
    int  loadStoredProfile();
    void storeProfile(int profile);

    // FIFO / Pipe for external input
    int fifo_fd;             // File Descriptor for the pipe
    std::string fifo_path;   // Path to pipe (default: /tmp/g13-lcd)

    void init_fifo();        // Create pipe
    void check_fifo();       // Read pipe data
    void cleanup_fifo();     // Remove pipe
    /**
     * Applies one "#" command from the LCD pipe. Returns true if the screen changed.
     * @param line The command line, e.g. "#text 4 8 hello".
     */
    bool handle_lcd_command(const std::string& line);

    // FIFO the config tool reads to implement record mode ("press MR, then the key
    // to program"). One line per physical key change: "key <code> <0|1>".
    int event_fifo_fd;
    std::string event_fifo_path;
    void init_event_fifo();
    void cleanup_event_fifo();
    void write_event(int key, int pressed);

    // Control pipe: the config tool says when it is recording, so the pad stops
    // playing bindings while a key is being programmed - otherwise the press that
    // selects the key to program also fires that key's old binding, and the tool
    // captures that instead of the key the user pressed.
    int ctl_fifo_fd;
    std::string ctl_fifo_path;
    void init_ctl_fifo();
    void cleanup_ctl_fifo();
    void check_ctl_fifo();
    void setRecording(bool on);
    void releaseAllActions();
    bool recording;
    std::chrono::steady_clock::time_point record_until;

    // Physical state of every key, so only changes are reported.
    std::vector<unsigned char> raw_keys;


public:
    G13(libusb_device *device);
    ~G13();

    void start();
    void stop();
    void loadBindings();
    void setColor(int r, int g, int b);

    // --- LCD ---
    void clear_lcd_buffer();
    void set_pixel(int x, int y, bool on);
    void write_lcd();
    void draw_test_pattern();
    void write_char(int x, int y, char c);
    void write_text(int x, int y, const std::string& text);
};

#endif