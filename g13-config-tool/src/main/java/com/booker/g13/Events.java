package com.booker.g13;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.SwingUtilities;

/**
 * Reads the key events the driver publishes, so the panel can react to presses on the
 * physical pad.
 *
 * Events come from the driver's Unix socket ({@code $XDG_RUNTIME_DIR/g13.sock}), one
 * line per physical key change: {@code key <code> <0|1>}. A socket rather than a pipe
 * because several programs need these events at once - a pipe splits its stream between
 * readers, so a second consumer silently steals half of them. Nothing is read while the
 * driver is not running; the reader waits and reconnects when it comes back.
 */
public class Events {

    /** Notified on the Swing thread for every physical key change. */
    public interface Listener {
        /**
         * @param g13KeyCode The G13 key code (0-39).
         * @param pressed true for a press, false for a release.
         */
        void keyChanged(int g13KeyCode, boolean pressed);
    }

    /** How long to wait before reopening the pipe, in milliseconds. */
    private static final int RETRY_DELAY_MS = 2000;

    private final Listener listener;
    private volatile boolean running = true;

    /**
     * Starts reading in the background. The thread is a daemon, so it never keeps the
     * application alive on its own.
     * @param listener Where key changes are delivered.
     */
    public Events(final Listener listener) {
        this.listener = listener;

        final Thread thread = new Thread(this::readLoop, "g13-events");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops the reader. */
    public void stop() {
        running = false;
    }

    private Path socketPath() {
        final String runtime = System.getenv("XDG_RUNTIME_DIR");
        if (runtime != null && !runtime.isBlank()) {
            return Paths.get(runtime, "g13.sock");
        }
        return Paths.get("/tmp", "g13.sock");
    }

    private void readLoop() {
        while (running) {
            // A blocking channel: read() waits for the next event, and returns -1 when
            // the driver goes away, which is what sends us round to reconnect.
            try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                channel.connect(UnixDomainSocketAddress.of(socketPath()));

                final ByteBuffer buffer = ByteBuffer.allocate(4096);
                final StringBuilder pending = new StringBuilder();

                while (running) {
                    buffer.clear();
                    final int read = channel.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    if (read == 0) {
                        continue;
                    }

                    buffer.flip();
                    pending.append(StandardCharsets.UTF_8.decode(buffer));

                    int newline;
                    while ((newline = pending.indexOf("\n")) >= 0) {
                        final String line = pending.substring(0, newline);
                        pending.delete(0, newline + 1);
                        handle(line);
                    }
                }
            } catch (IOException e) {
                // Driver not running yet, or it restarted and recreated the socket.
            }

            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void handle(final String line) {
        final String[] parts = line.trim().split("\\s+");
        if (parts.length != 3 || !"key".equals(parts[0])) {
            return;
        }

        try {
            final int code = Integer.parseInt(parts[1]);
            final boolean pressed = !"0".equals(parts[2]);
            SwingUtilities.invokeLater(() -> listener.keyChanged(code, pressed));
        } catch (NumberFormatException e) {
            // Ignore anything malformed.
        }
    }
}
