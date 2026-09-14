# =============================================================================
# Universal Makefile for G13 Driver
# =============================================================================

# --- Standard Build Variables (System Install) ---
PREFIX      ?= /usr
BINDIR      ?= $(PREFIX)/bin
DATADIR     ?= $(PREFIX)/share
JAVADIR     ?= $(DATADIR)/java/linux-g13-driver
UDEVDIR     ?= /usr/lib/udev/rules.d
SYSTEMDDIR  ?= /usr/lib/systemd/user

# --- Directories & Paths ---
BUILD_DIR   := ../build
TARGET_NAME := Linux-G13-Driver
TARGET_PATH := ../$(TARGET_NAME)
SCRIPT_DIR  := ./scripts
SERVICE_SRC := ./systemd/g13.service

# Where the pieces go on a system install.
LIBDIR      ?= $(PREFIX)/lib
INCLUDEDIR  ?= $(PREFIX)/include
APPDIR      ?= $(DATADIR)/applications

# GUI Paths
GUI_ROOT    := ../../g13-config-tool
GUI_JAR_SRC := $(GUI_ROOT)/target/Linux-G13-GUI.jar
GUI_JAR_NAME:= Linux-G13-GUI.jar

# Config Paths (Source)
BINDINGS_SRC:= ../bindings/.g13

.PHONY: all dependencies build-driver build-gui build-lcdsdk build-lcdsdk-windows test test-lcdsdk install-udev enable-services clean install install-user uninstall

# =============================================================================
# Main Workflow
# =============================================================================
# "all" is for the human user -> checks deps + builds everything
all: dependencies build-driver build-gui build-lcdsdk
	@echo "Build complete."
	@echo " -> Run 'sudo make install' to install system-wide (Package Mode)."
	@echo " -> Run 'make install-user' to install for current user (Dev Mode)."

# Helper to install packages (requires sudo / user interaction)
dependencies:
	@chmod +x $(SCRIPT_DIR)/install_deps.sh
	@$(SCRIPT_DIR)/install_deps.sh

# STANDALONE TARGET (For AUR/Packaging) - NO dependency on 'dependencies'
build-driver:
	@echo "--- Building C++ Driver ---"
	mkdir -p $(BUILD_DIR)
	cd $(BUILD_DIR) && cmake ../src && make
	cp $(BUILD_DIR)/$(TARGET_NAME) $(TARGET_PATH)

# STANDALONE TARGET (For AUR/Packaging) - NO dependency on 'dependencies'
build-gui:
	@echo "--- Building Java GUI ---"
	cd $(GUI_ROOT) && mvn clean package -DskipTests

# The Logitech LCD SDK, for programs that expect a LogitechLcd.dll: same functions, same
# types, forwarding to this driver. Nothing here needs the device to build or test.
build-lcdsdk:
	@echo "--- Building the Logitech LCD SDK ---"
	$(MAKE) -C lcdsdk

# A proxy LogitechLcd.dll for Wine/Proton, so games with native Logitech LCD support can
# drive the panel. Needs mingw-w64; untested against a real game.
build-lcdsdk-windows:
	$(MAKE) -C lcdsdk windows

test-lcdsdk: build-lcdsdk
	python3 scripts/lcdsdk-test.py

# Every test in the repository, with the scratch directories they need. Nothing here touches
# the live configuration, the running daemon, or the device.
test: build-lcdsdk build-gui
	bash ../../tests/run-all-tests.sh

# The udev rules let the driver claim the device as your user. They live in /etc, which is why
# this is the one step needing sudo - kept out of install-user so the rest runs password-free.
install-udev:
	@echo "--- Installing UDEV rules (sudo required) ---"
	sudo cp udev/99-g13.rules /etc/udev/rules.d/
	sudo udevadm control --reload-rules && sudo udevadm trigger

# The three user services: the pad itself, what draws on its screen, and the Windows bridge.
enable-services:
	systemctl --user daemon-reload
	systemctl --user enable --now g13.service g13-visuals.service g13-lcd-bridge.service
	@echo "Enabled: g13, g13-visuals, g13-lcd-bridge"


# =============================================================================
# System-Wide Installation (Root / Package Manager)
# Ships the service file to /usr/lib/systemd/user but does NOT enable it.
# =============================================================================
install:
	@echo "--- Installing System-Wide to $(DESTDIR)$(PREFIX) ---"
	
	# Binaries & Jar
	install -d $(DESTDIR)$(BINDIR)
	install -m 755 $(TARGET_PATH) $(DESTDIR)$(BINDIR)/linux-g13-driver
	install -d $(DESTDIR)$(JAVADIR)
	install -m 644 $(GUI_JAR_SRC) $(DESTDIR)$(JAVADIR)/$(GUI_JAR_NAME)
	
	# Wrapper
	echo '#!/bin/sh' > g13-gui
	echo 'java -jar $(JAVADIR)/$(GUI_JAR_NAME) "$$@"' >> g13-gui
	install -m 755 g13-gui $(DESTDIR)$(BINDIR)/g13-gui
	rm g13-gui
	
	# UDEV
	install -d $(DESTDIR)$(UDEVDIR)
	install -m 644 udev/99-g13.rules $(DESTDIR)$(UDEVDIR)/99-g13.rules
	
	# Debug helper
	install -m 755 scripts/g13-keywatch $(DESTDIR)$(BINDIR)/g13-keywatch
	install -m 755 scripts/g13-lcd $(DESTDIR)$(BINDIR)/g13-lcd
	install -m 755 scripts/g13-watch $(DESTDIR)$(BINDIR)/g13-watch
	install -m 644 scripts/g13lcd.py $(DESTDIR)$(BINDIR)/g13lcd.py
	install -m 755 ../../g13-visuals/g13-visuals $(DESTDIR)$(BINDIR)/g13-visuals
	sed 's|%h/.local/bin|$(BINDIR)|g' ../../g13-visuals/systemd/g13-visuals.service > g13-visuals.service
	install -d $(DESTDIR)$(SYSTEMDDIR)
	install -m 644 g13-visuals.service $(DESTDIR)$(SYSTEMDDIR)/g13-visuals.service
	rm g13-visuals.service
	
	# Systemd (Copy static file)
	install -d $(DESTDIR)$(SYSTEMDDIR)
	install -m 644 $(SERVICE_SRC) $(DESTDIR)$(SYSTEMDDIR)/g13.service

	# The service manager, and the menu entries that use it
	install -m 755 scripts/g13-service $(DESTDIR)$(BINDIR)/g13-service
	install -m 755 scripts/g13-buttons $(DESTDIR)$(BINDIR)/g13-buttons
	install -m 755 scripts/g13-applet $(DESTDIR)$(BINDIR)/g13-applet
	install -m 755 lcdsdk/g13-lcd-bridge $(DESTDIR)$(BINDIR)/g13-lcd-bridge
	install -d $(DESTDIR)$(APPDIR)
	for entry in ../../desktop/*.desktop; do \
		sed 's|@BINDIR@|$(BINDIR)|g' "$$entry" > "$(DESTDIR)$(APPDIR)/$$(basename "$$entry")"; \
	done

	# The Logitech LCD SDK: the library, the header, and the bridge's service
	$(MAKE) -C lcdsdk
	install -d $(DESTDIR)$(LIBDIR) $(DESTDIR)$(INCLUDEDIR)
	install -m 644 lcdsdk/liblogitechlcd.so $(DESTDIR)$(LIBDIR)/liblogitechlcd.so
	install -m 644 lcdsdk/LogitechLcd.h $(DESTDIR)$(INCLUDEDIR)/LogitechLcd.h
	sed 's|@BINDIR@|$(BINDIR)|g' ../../g13-visuals/systemd/g13-lcd-bridge.service.in > g13-lcd-bridge.service
	install -m 644 g13-lcd-bridge.service $(DESTDIR)$(SYSTEMDDIR)/g13-lcd-bridge.service
	rm g13-lcd-bridge.service
	
	@echo "System install done."
	@echo "To enable the services, the user must run: systemctl --user enable --now g13 g13-visuals g13-lcd-bridge"

# =============================================================================
# User-Local Installation
# Modifies the service file to point to HOME and starts it.
# =============================================================================
USER_INSTALL_DIR := $(HOME)/.local/bin
USER_CONFIG_DIR  := $(HOME)/.config/g13
USER_SYSTEMD_DIR := $(HOME)/.config/systemd/user
USER_APPS_DIR    := $(HOME)/.local/share/applications

install-user:
	@echo "--- Installing for Current User ($(USER)) ---"
	mkdir -p $(USER_INSTALL_DIR)
	mkdir -p $(USER_CONFIG_DIR)
	mkdir -p $(USER_SYSTEMD_DIR)
	
	# Copy Files
	# A plain cp over the running binary fails with "Text file busy". Copy beside it and
	# rename: that is atomic, and the running process keeps its old copy until the service
	# is restarted below.
	cp $(TARGET_PATH) $(USER_INSTALL_DIR)/linux-g13-driver.new
	mv -f $(USER_INSTALL_DIR)/linux-g13-driver.new $(USER_INSTALL_DIR)/linux-g13-driver
	cp $(GUI_JAR_SRC) $(USER_INSTALL_DIR)/$(GUI_JAR_NAME)
	cp scripts/g13-keywatch $(USER_INSTALL_DIR)/g13-keywatch
	cp scripts/g13-lcd $(USER_INSTALL_DIR)/g13-lcd
	cp scripts/g13-watch $(USER_INSTALL_DIR)/g13-watch
	cp scripts/g13-buttons $(USER_INSTALL_DIR)/g13-buttons
	cp scripts/g13-applet $(USER_INSTALL_DIR)/g13-applet
	cp scripts/g13-lcd-game-check $(USER_INSTALL_DIR)/g13-lcd-game-check
	cp scripts/g13lcd.py $(USER_INSTALL_DIR)/g13lcd.py
	chmod +x $(USER_INSTALL_DIR)/g13-keywatch $(USER_INSTALL_DIR)/g13-lcd $(USER_INSTALL_DIR)/g13-watch $(USER_INSTALL_DIR)/g13-buttons $(USER_INSTALL_DIR)/g13-applet $(USER_INSTALL_DIR)/g13-lcd-game-check
	
	# Screen visuals (applets and menu)
	cp ../../g13-visuals/g13-visuals $(USER_INSTALL_DIR)/g13-visuals
	chmod +x $(USER_INSTALL_DIR)/g13-visuals
	install -d $(USER_SYSTEMD_DIR)
	install -m 644 ../../g13-visuals/systemd/g13-visuals.service $(USER_SYSTEMD_DIR)/g13-visuals.service

	# The Logitech LCD SDK: anything that calls LogitechLcd.dll finds this instead
	$(MAKE) -C lcdsdk
	$(MAKE) -C lcdsdk install PREFIX=$(HOME)/.local

	# The Windows bridge: the relay a LogitechLcd.dll under Wine/Proton talks to, and its
	# service, so it is there whether or not a game is being played.
	cp lcdsdk/g13-lcd-bridge $(USER_INSTALL_DIR)/g13-lcd-bridge
	chmod +x $(USER_INSTALL_DIR)/g13-lcd-bridge
	install -d $(USER_SYSTEMD_DIR)
	sed 's|@BINDIR@|$(USER_INSTALL_DIR)|g' ../../g13-visuals/systemd/g13-lcd-bridge.service.in > $(USER_SYSTEMD_DIR)/g13-lcd-bridge.service

	# The service manager, and the menu entries that use it
	cp scripts/g13-service $(USER_INSTALL_DIR)/g13-service
	chmod +x $(USER_INSTALL_DIR)/g13-service
	install -d $(USER_APPS_DIR)
	for entry in ../../desktop/*.desktop; do \
		sed 's|@BINDIR@|$(USER_INSTALL_DIR)|g' "$$entry" > "$(USER_APPS_DIR)/$$(basename "$$entry")"; \
	done
	-command -v update-desktop-database >/dev/null && update-desktop-database $(USER_APPS_DIR)
	
	# Wrapper
	echo '#!/bin/sh' > $(USER_INSTALL_DIR)/g13-gui
	echo 'java -jar $(USER_INSTALL_DIR)/$(GUI_JAR_NAME) "$$@"' >> $(USER_INSTALL_DIR)/g13-gui
	chmod +x $(USER_INSTALL_DIR)/g13-gui
	
	# Default Configs
	if [ -z "$$(ls -A $(USER_CONFIG_DIR)/*.properties 2>/dev/null)" ]; then \
		echo "Copying default configs..."; \
		cp $(BINDINGS_SRC)/*.properties $(USER_CONFIG_DIR)/; \
	fi
	
	# Systemd: Transform static file to use User Path
	@echo "Configuring Systemd Service..."
	sed 's|/usr/bin/linux-g13-driver|$(USER_INSTALL_DIR)/linux-g13-driver|g' $(SERVICE_SRC) > $(USER_SYSTEMD_DIR)/g13.service
	
	# Enable and start all three: the pad, what draws on its screen, and the Windows bridge
	@echo "Enabling and starting the services..."
	systemctl --user daemon-reload
	systemctl --user enable g13.service g13-visuals.service g13-lcd-bridge.service
	-systemctl --user restart g13.service
	-systemctl --user restart g13-visuals.service
	-systemctl --user restart g13-lcd-bridge.service
	
	# UDEV rules live in /etc and need root, so they are their own target: everything else
	# here runs as you, with no password.
	@echo "UDEV rules are separate: run 'make install-udev' once (needs sudo)."
	
	@echo "====================================================="
	@echo " Installation Complete!"
	@echo " GUI Tool: $(USER_INSTALL_DIR)/g13-gui"
	@echo " Services: g13, g13-visuals, g13-lcd-bridge"
	@echo " Enable all: make enable-services  (or 'g13-service start all')"
	@echo " Logs: journalctl --user -u g13 -f"
	@echo "====================================================="

clean-build:
	rm -rf $(BUILD_DIR)

clean: clean-build
	rm -f $(TARGET_PATH)
	cd $(GUI_ROOT) && mvn clean


# =============================================================================
# Uninstallation
# The mirror of the two install targets above: every path they write is removed
# here. Your configuration in $(USER_CONFIG_DIR) is kept, so reinstalling does
# not lose bindings, macros, visuals or applets.
# =============================================================================

# Everything both install targets put in a bin directory.
INSTALLED_BINS := linux-g13-driver g13-gui $(GUI_JAR_NAME) g13-keywatch g13-lcd g13-watch \
                  g13-buttons g13-applet g13-lcd-game-check g13lcd.py g13-visuals g13-lcd-bridge g13-service
INSTALLED_UNITS := g13.service g13-visuals.service g13-lcd-bridge.service

uninstall-user:
	@echo "--- Removing the user-local install ---"
	-systemctl --user disable --now $(INSTALLED_UNITS)
	for name in $(INSTALLED_BINS); do rm -f "$(USER_INSTALL_DIR)/$$name"; done
	rm -f $(HOME)/.local/lib/liblogitechlcd.so
	rm -f $(HOME)/.local/include/LogitechLcd.h
	for unit in $(INSTALLED_UNITS); do rm -f "$(USER_SYSTEMD_DIR)/$$unit"; done
	for entry in ../../desktop/*.desktop; do \
		rm -f "$(USER_APPS_DIR)/$$(basename "$$entry")"; \
	done
	-systemctl --user daemon-reload
	-command -v update-desktop-database >/dev/null && update-desktop-database $(USER_APPS_DIR)
	@echo "Removed: binaries, SDK library and header, service files, menu entries."
	@echo "Kept:    $(USER_CONFIG_DIR) (bindings, macros, visuals, applets)."

uninstall-udev:
	@echo "--- Removing the udev rule (sudo required) ---"
	sudo rm -f /etc/udev/rules.d/99-g13.rules
	sudo udevadm control --reload-rules && sudo udevadm trigger

uninstall:
	@echo "--- Removing the system install from $(DESTDIR)$(PREFIX) ---"
	for name in $(INSTALLED_BINS); do rm -f "$(DESTDIR)$(BINDIR)/$$name"; done
	rm -f $(DESTDIR)$(JAVADIR)/$(GUI_JAR_NAME)
	rm -f $(DESTDIR)$(LIBDIR)/liblogitechlcd.so
	rm -f $(DESTDIR)$(INCLUDEDIR)/LogitechLcd.h
	rm -f $(DESTDIR)$(UDEVDIR)/99-g13.rules
	for unit in $(INSTALLED_UNITS); do rm -f "$(DESTDIR)$(SYSTEMDDIR)/$$unit"; done
	for entry in ../../desktop/*.desktop; do \
		rm -f "$(DESTDIR)$(APPDIR)/$$(basename "$$entry")"; \
	done
	@echo "Removed. Disable the services if they were enabled:"
	@echo "  systemctl --user disable $(INSTALLED_UNITS)"