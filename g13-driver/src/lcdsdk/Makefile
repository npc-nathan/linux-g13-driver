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

all: liblogitechlcd.so liblogitechlcd-tcp.so lcdsdk-selftest lcdsdk-selftest-tcp

liblogitechlcd.so: LogitechLcd.c LogitechLcd.h
	$(CC) $(CFLAGS) -shared -o $@ LogitechLcd.c

# The same library over the TCP transport, so the Windows path can be driven from a test
# here instead of needing Wine.
liblogitechlcd-tcp.so: LogitechLcd.c LogitechLcd.h
	$(CC) $(CFLAGS) -DG13_TCP_TRANSPORT=1 -shared -o $@ LogitechLcd.c

lcdsdk-selftest: lcdsdk-selftest.c LogitechLcd.c LogitechLcd.h
	$(CC) $(CFLAGS) -o $@ lcdsdk-selftest.c LogitechLcd.c

# The same self-test over the transport the Windows build uses. It is the code the proxy
# DLL runs, so it can be put through its paces here, without Wine.
lcdsdk-selftest-tcp: lcdsdk-selftest.c LogitechLcd.c LogitechLcd.h
	$(CC) $(CFLAGS) -DG13_TCP_TRANSPORT=1 -o $@ lcdsdk-selftest.c LogitechLcd.c

windows: LogitechLcd.c LogitechLcd.h LogitechLcd.def
	@command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1 || { \
	  echo "mingw-w64 is not installed (apt install mingw-w64); skipping the Windows proxy."; \
	  exit 0; }
	x86_64-w64-mingw32-gcc -O2 -Wall -Wextra -shared -o LogitechLcd.dll \
	  LogitechLcd.c -Wl,LogitechLcd.def -lws2_32
	@echo "built LogitechLcd.dll: drop it in the game's prefix and set a DLL override,"
	@echo "and run g13-lcd-bridge so it has something to talk to"

install: all
	mkdir -p $(PREFIX)/lib $(PREFIX)/include $(PREFIX)/bin
	cp liblogitechlcd.so $(PREFIX)/lib/
	cp LogitechLcd.h $(PREFIX)/include/
	cp g13-lcd-bridge $(PREFIX)/bin/
	chmod +x $(PREFIX)/bin/g13-lcd-bridge

clean:
	rm -f liblogitechlcd.so liblogitechlcd-tcp.so lcdsdk-selftest lcdsdk-selftest-tcp LogitechLcd.dll

.PHONY: all install windows clean
