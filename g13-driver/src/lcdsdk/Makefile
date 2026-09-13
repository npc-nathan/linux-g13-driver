# The Logitech LCD SDK, implemented against the G13 driver.
#
#   make            build liblogitechlcd.so and the self-test
#   make install    put the library and header in ~/.local (or PREFIX=...)
#   make windows    build a LogitechLcd.dll proxy for Wine/Proton, if mingw-w64 is installed
#
# The Windows proxy exists so that games with native Logitech LCD support can drive the G13
# under Proton. It has not been run against a game yet - it needs mingw-w64 to build, and a
# game to prove.

CC ?= cc
CFLAGS ?= -O2 -Wall -Wextra -fPIC
PREFIX ?= $(HOME)/.local

all: liblogitechlcd.so lcdsdk-selftest

liblogitechlcd.so: LogitechLcd.c LogitechLcd.h
	$(CC) $(CFLAGS) -shared -o $@ LogitechLcd.c

lcdsdk-selftest: lcdsdk-selftest.c LogitechLcd.c LogitechLcd.h
	$(CC) $(CFLAGS) -o $@ lcdsdk-selftest.c LogitechLcd.c

windows: LogitechLcd.c LogitechLcd.h
	@command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1 || { \
	  echo "mingw-w64 is not installed (apt install mingw-w64); skipping the Windows proxy."; \
	  exit 0; }
	x86_64-w64-mingw32-gcc -O2 -Wall -Wextra -shared -o LogitechLcd.dll LogitechLcd.c \
	  -Wl,--kill-at -lws2_32
	@echo "built LogitechLcd.dll - drop it in the game's prefix and set a DLL override"

install: all
	mkdir -p $(PREFIX)/lib $(PREFIX)/include
	cp liblogitechlcd.so $(PREFIX)/lib/
	cp LogitechLcd.h $(PREFIX)/include/

clean:
	rm -f liblogitechlcd.so lcdsdk-selftest LogitechLcd.dll

.PHONY: all install windows clean
