package com.booker.g13;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Sends commands to the driver over its event socket.
 *
 * The driver suspends binding playback while the tool is recording, so the press that
 * picks the key to program does not also fire that key's old binding.
 *
 * Writes happen on a separate thread: connecting and writing must never block the Swing
 * thread.
 */
public class Control {

    /** How many commands can be waiting. Only the newest ones matter. */
    private static final int QUEUE_SIZE = 4;

    /** Command that asks the driver to suspend bindings while recording / to resume. */
    public static final String RECORD_ON = "record 1";
    public static final String RECORD_OFF = "record 0";

    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(QUEUE_SIZE);

    /** Starts the writer thread. */
    public Control() {
        final Thread thread = new Thread(this::writeLoop, "g13-control");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Queues a command. If the driver is not keeping up the oldest command is dropped:
     * a stale "resume" is worthless, the newest state is what counts.
     * @param command The command line.
     */
    public void send(final String command) {
        if (!queue.offer(command)) {
            queue.poll();
            queue.offer(command);
        }
    }

    private Path socketPath() {
        final String runtime = System.getenv("XDG_RUNTIME_DIR");
        if (runtime != null && !runtime.isBlank()) {
            return Paths.get(runtime, "g13.sock");
        }
        return Paths.get("/tmp", "g13.sock");
    }

    private void writeLoop() {
        while (true) {
            final String command;
            try {
                command = queue.take();
            } catch (InterruptedException e) {
                return;
            }

            try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                channel.connect(UnixDomainSocketAddress.of(socketPath()));
                channel.write(ByteBuffer.wrap((command + "\n").getBytes(StandardCharsets.UTF_8)));
            } catch (IOException e) {
                // Driver not running, or it restarted. The next command tries again, and
                // the driver's own timeout covers the gap.
            }
        }
    }
}
