#!/bin/bash
# Build a .deb for this driver from the source in this repository.
#
#     packaging/build-deb.sh [version]
#
# It installs into a staging directory with `make install DESTDIR=...`, writes a control file, and
# hands the result to dpkg-deb. Nothing is copied onto this machine and nothing is installed: the
# .deb is left in packaging/ for you to install or give away as you see fit.
#
# **Read the "Licence and credit" section of the README before giving the result to anybody.** This
# code carries no licence, so a binary built from it may not be redistributed without the permission
# of the original authors. Building it for yourself is fine.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repo="$(dirname "$here")"
version="${1:-0.9.0~beta}"
package="linux-g13-driver"
stage="$here/stage"
output="$here/${package}_${version}_amd64.deb"
architecture="$(dpkg --print-architecture 2>/dev/null || echo amd64)"

if [ -e "$repo/g13-driver/src/Makefile" ]; then :; else
    echo "run this from a checkout of the driver" >&2
    exit 1
fi

echo "=== building ($version, $architecture) ==="
# build-driver and build-gui, not `all`: `all` starts by installing dependencies with sudo, which is
# the wrong thing for a build script whose dependencies are already declared in the control file.
make -C "$repo/g13-driver/src" build-driver build-gui

echo "=== staging into $stage ==="
rm -rf "$stage"
make -C "$repo/g13-driver/src" install DESTDIR="$stage" PREFIX=/usr

# A package's udev rules belong in /lib/udev/rules.d so they are found before local ones.
if [ -d "$stage/etc/udev/rules.d" ]; then
    mkdir -p "$stage/lib/udev/rules.d"
    mv "$stage/etc/udev/rules.d/"*.rules "$stage/lib/udev/rules.d/" 2>/dev/null || true
    rmdir -p "$stage/etc/udev/rules.d" 2>/dev/null || true
fi

size="$(du -sk "$stage" | cut -f1)"
mkdir -p "$stage/DEBIAN"
cat > "$stage/DEBIAN/control" <<CONTROL
Package: $package
Version: $version
Section: utils
Priority: optional
Architecture: $architecture
Installed-Size: $size
Depends: libusb-1.0-0, libgtk-3-0, libayatana-appindicator3-1, default-jre | java17-runtime | openjdk-17-jre, python3, udev
Suggests: evtest
Maintainer: this fork's author
Description: Logitech G13 driver, configuration tool and screen applets
 Keys, profiles, macros and record mode for the Logitech G13, a configuration tool that previews
 the 160x43 screen, and a daemon that draws applets on it from files, commands, web addresses,
 brokers, sockets and mailboxes.
 This build carries no licence: see the README's "Licence and credit" section before
 redistributing it.
CONTROL

cat > "$stage/DEBIAN/postinst" <<'POSTINST'
#!/bin/sh
set -e
udevadm control --reload-rules || true
udevadm trigger || true
echo "The G13 needs its udev rule to have loaded. If the keys do nothing, unplug and replug it,"
echo "then start the services as your own user:  systemctl --user enable --now g13 g13-visuals"
exit 0
POSTINST
chmod 755 "$stage/DEBIAN/postinst"

echo "=== packing ==="
dpkg-deb --build --root-owner-group "$stage" "$output" >/dev/null
rm -rf "$stage"
echo "built: $output"
dpkg-deb --info "$output" | sed -n '1,12p'
