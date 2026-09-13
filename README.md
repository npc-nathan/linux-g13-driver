# G13 Linux Driver & GUI (Modernized Fork)

This is a modernized fork of the G13 driver for Linux.
The original project is over 10 years old. This fork has been refactored to use modern C++ standards for the driver and modern Java standards (Java 17 with Maven) for the configuration GUI.

## Features

* **Modern C++ Driver:** The core driver has been updated for better performance and compatibility.
* **Java GUI:** The configuration utility is built with Java 17 and Maven, ensuring it runs on modern systems.
* **Flexible Configuration:** Offers multiple ways to configure your G13: via the user-friendly GUI, manual file editing, or using the driver's fixed mapping with external tools.

## Requirements

### Base Requirements

You need to install the following packages via your package manager:

* `make`
* `cmake`
* `gtk3` / `gtk3-devel`
* `libusb-1.0-0` (on some distros named `libusb-1.0-0-dev` or `libusb1-devel`)
* `libappindicator-gtk3` (or `libayatana-appindicator3-dev` on Debian/Ubuntu 22.04+)
* `Java 17` or higher
* `python-psutil` (for the monitor script)

### Automated Dependency Installation

Alternatively, all needed dependencies can be installed via the `install_deps.sh` script located in the scripts folder.

```bash
cd src/scripts
chmod +x install_deps.sh
./install_deps.sh
```

## Build & Installation

1.  Open a terminal and navigate to the project directory.
2.  Build the driver:

    ```bash
    make all
    ```

The installation process will clean up automatically after finishing.

## Choose your Installation Method

### Option A: System-Wide Installation (Standard)
This is the recommended method for standard usage. It installs binaries to /usr/bin and resources to /usr/share/.

```bash
sudo make install
```
Note: As per standard Linux security practices, the installation does not auto-start user services. You must enable the driver for your user manually once:

```bash
systemctl --user enable --now g13
systemctl --user start g13
```

#### Option B: User-Local Installation (Developer Mode)
This method installs everything to your home directory (~/.local/bin). It is intended for development, testing, or users without root access. Automatically creates and starts the Systemd service.

```bash
make install-user
```
Driver: Installed to ~/.local/bin/linux-g13-driver

Service: Automatically enabled and started immediately.

Note on Permissions: Both methods install a UDEV rule (/etc/udev/rules.d/99-g13.rules) to allow access to the G13 without sudo. You might need to unplug and replug your device once after installation if it's not detected immediately.

## How to use the Driver and GUI

### Run the driver

### Controlling the Driver
The driver runs in the background via Systemd.

Check Status:

```bash
systemctl --user status g13
```

View Logs:

```bash
journalctl --user -u g13 -f
```

Restart Driver:

```bash
systemctl --user restart g13
```


### Use the Config Tool

After starting the driver, you will see a new icon in your system tray/taskbar. This allows you to open the config menu or quit the driver.

Alternatively, run it from the terminal:

```bash
g13-gui
```

This will bring up the UI.

Profiles: pick one with the **Profile** selector at the top of the panel (**M1, M2,
M3**). That loads `bindings-N.properties` for editing *and* activates it on the
device — the selector writes the same `~/.config/g13/active-profile` file the driver
watches, so the pad switches over within a second. **MR is not a profile**: it is the
macro record button and sends `KEY_MACRO_RECORD_START`, unless the profile binds it.
Pressing any of the M buttons on the pad selects it for editing like any other key.

Button types: a selected key can be **None**, **Pass Through**, **Macro** or
**M Key event** (one of the M button codes). For the M buttons there is one extra
option, labelled for the button: **Switch profile (default)** on M1-M3 and
**Macro record (default)** on MR. Those are the driver's own defaults, and they are
what you get when the profile contains no entry for that button. Choosing *None* for
an M button stores `x`, an explicit "do nothing", because simply removing the entry
would fall back to the default behaviour.

Save: Changes are saved automatically to `~/.config/g13/bindings-*.properties`.

Live Reload: The driver detects changes to the **currently active** bindings
file and reloads it within a second — no restart needed. Pressing a profile
button re-reads it as well. Changes made to a profile you are not currently on
take effect when you switch to it.

Active profile: The last selected profile is stored in
`~/.config/g13/active-profile` (a plain integer, 0-3) so the driver comes back
on it after a restart or a replug. Delete the file to fall back to profile 0.

![Config Tool Screenshot](docs/ConfigTool.png)

The M1, M2, M3 and MR buttons select the bindings.

### Use the built-in Mapping Set (for external tools)

The driver now includes a fixed default mapping. This means the GUI is not strictly necessary if you prefer other tools. You can map the keys using software like **Input Remapper**.

*(Note: the quick profile change via the four profile buttons only works when using the G13 GUI tool.)*

### Manually create your own Mapping Set

If you don't want to use the GUI App, you can edit the files manually in `~/.config/g13/`.

* **Usage Example:** To map the **G20** key to the letter **T**, find the event code for T (which is 20). Then, in your `bindings-0.properties` file, add or edit the line:
    ```ini
    G20=p,k.20
    ```

### Binding types

| Value | Meaning |
|-------|---------|
| `p,k.<code>` | Pass through a single key. `<code>` is the Linux event code (see `docs/Eventcodes_for_Mapping.pdf`). |
| `m,<macroId>,<repeats>` | Play macro `<macroId>` from `~/.config/g13/macro-<macroId>.properties`; `<repeats>` is `0` or `1`. |
| `mk,<index>` | Send the M key code for `<index>`: `0`=M1 (`KEY_MACRO_PRESET1`), `1`=M2, `2`=M3, `3`=MR (`KEY_MACRO_RECORD_START`). |
| `x` | Do nothing at all. Only meaningful for the M buttons, whose default (no entry) is to switch profile (M1-M3) or send the macro record event (MR). |

A key with no entry at all sends nothing. Removing a line takes effect within a
second if that profile is the active one.

**Overriding the M buttons:** by default M1/M2/M3/MR switch binding profiles. Bind
one of them in a profile file and that binding wins *for as long as that profile is
active*, e.g.:

```ini
G30=mk,1     # while this profile is active, M2 sends KEY_MACRO_PRESET2 instead of
             # switching to bindings-1.properties
```

Handy for passing the M buttons through to games or for layering your own
behaviour on top of the profiles. In the GUI this is the *M Buttons (this profile)*
section, and any key can be given an M code via the *M Key* button type.

### Using the Display (scripting)

You can write text to the display using a simple pipe command:

The driver creates a Named Pipe (FIFO) to receive text for the LCD.

Location: `/run/user/$UID/g13-lcd` (Check `/tmp/g13-lcd` as fallback if `/run` is unavailable).

```bash
# Find your pipe path (usually based on your user ID, e.g., 1000)
PIPE="/run/user/$(id -u)/g13-lcd"

# Send simple text
echo "Hello World!" > $PIPE

# Send multi-line text (CPU/RAM stats)
echo -e "CPU: 50%\nRAM: 4GB" > $PIPE
```

Currently, only one font size is implemented. There is an example script for system monitoring in the `scripts` folder. Feel free to try it out, modify it, or share your own scripts!


### Uninstallation

To remove the driver and all installed files:

```bash
make uninstall
```

(Note: This removes the binaries, UDEV rules, and service files, but keeps your configuration in ~/.config/g13 to prevent data loss.)


## Notes

* Tested on 64-bit Arch Linux.