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

# Both architectures, because the game decides: Dragon Age: Origins (2009) is 32-bit and can only
# load a 32-bit DLL, while anything modern is 64-bit. The name a game loads is LogitechLcd.dll
# either way, so the 32-bit build takes that name - it is what the LCD-era games ask for - and the
# 64-bit one is built beside it, to be renamed on the way into a modern game's folder.
WINDOWS_FLAGS := -O2 -Wall -Wextra -shared

windows: LogitechLcd.c LogitechLcd.h LogitechLcd.def
	@command -v i686-w64-mingw32-gcc >/dev/null 2>&1 || { \
	  echo "mingw-w64 is not installed (apt install mingw-w64); skipping the Windows proxy."; \
	  exit 0; }
	i686-w64-mingw32-gcc $(WINDOWS_FLAGS) -o LogitechLcd.dll \
	  LogitechLcd.c -Wl,LogitechLcd.def -lws2_32
	@command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1 && \
	  x86_64-w64-mingw32-gcc $(WINDOWS_FLAGS) -o LogitechLcd.x64.dll \
	    LogitechLcd.c -Wl,LogitechLcd.def -lws2_32 || true
	@echo "built LogitechLcd.dll (32-bit) and LogitechLcd.x64.dll (64-bit)."
	@echo "Copy the one matching the game into its folder as LogitechLcd.dll, keep"
	@echo "g13-lcd-bridge running, and read lcd-probe.log beside it if nothing appears."

install: all
	mkdir -p $(PREFIX)/lib $(PREFIX)/include $(PREFIX)/bin
	cp liblogitechlcd.so $(PREFIX)/lib/
	cp LogitechLcd.h $(PREFIX)/include/
	cp g13-lcd-bridge $(PREFIX)/bin/
	chmod +x $(PREFIX)/bin/g13-lcd-bridge

clean:
	rm -f liblogitechlcd.so liblogitechlcd-tcp.so lcdsdk-selftest lcdsdk-selftest-tcp LogitechLcd.dll LogitechLcd.x64.dll

.PHONY: all install windows clean
