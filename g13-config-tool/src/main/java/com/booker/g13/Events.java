package com.booker.g13;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.SwingUtilities;

/**
 * Reads the key press pipe the driver publishes, so the panel can react to presses
 * on the physical pad.
 *
 * The driver writes one line per physical key change to
 * {@code $XDG_RUNTIME_DIR/g13-events}: {@code key <code> <0|1>}. Nothing is read
 * while the driver is not running; the reader waits and reopens the pipe when it
 * comes back.
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

    private Path fifoPath() {
        final String runtime = System.getenv("XDG_RUNTIME_DIR");
        if (runtime != null && !runtime.isBlank()) {
            return Paths.get(runtime, "g13-events");
        }
        return Paths.get("/tmp", "g13-events");
    }

    private void readLoop() {
        while (running) {
            // Opened read-write so it never blocks waiting for the driver: a FIFO
            // opened for reading only waits for a writer to appear.
            try (RandomAccessFile pipe = new RandomAccessFile(fifoPath().toFile(), "rw")) {
                String line;
                while (running && (line = pipe.readLine()) != null) {
                    handle(line);
                }
            } catch (IOException e) {
                // Driver not running yet, or it restarted and recreated the pipe.
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
