package com.booker.g13;

import java.io.File;

/**
 * Where the other parts of the driver are, if they are installed.
 *
 * The config tool talks to the daemon and to the checker by running them, rather than by copying
 * what they do: a window that fetched a web address its own way could disagree with the pad about
 * what a source reads, and that is the disagreement worth avoiding.
 */
public final class Installed {

    /** Where a user-local install puts things. */
    private static final String[] DIRECTORIES = {
        System.getProperty("user.home", "") + "/.local/bin",
        "/usr/local/bin",
        "/usr/bin",
    };

    private Installed() {
    }

    /**
     * The installed command of that name.
     * @param name The program, e.g. "g13-applet".
     * @return it, or null when it is not installed where we look.
     */
    public static File command(final String name) {
        for (final String directory : DIRECTORIES) {
            final File file = new File(directory, name);
            if (file.canExecute()) {
                return file;
            }
        }
        return null;
    }

    /**
     * Runs one and returns everything it printed.
     * @param command The program.
     * @param arguments Its arguments.
     * @return the output, errors included, or an explanation of why it did not run.
     */
    public static String run(final File command, final String... arguments) {
        if (command == null) {
            return "not installed - 'make install-user' puts it in ~/.local/bin";
        }
        try {
            final java.util.List<String> line = new java.util.ArrayList<>();
            line.add(command.getAbsolutePath());
            line.addAll(java.util.Arrays.asList(arguments));
            final Process process = new ProcessBuilder(line).redirectErrorStream(true).start();
            // Quick by design: these read one file and a handful of sources, so running them in
            // place keeps the window simple rather than growing a worker and a callback.
            final String output = new String(process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            process.waitFor();
            return output.isEmpty() ? "(nothing was printed)" : output;
        } catch (java.io.IOException | InterruptedException failed) {
            return "could not run " + command.getName() + ": " + failed.getMessage();
        }
    }
}
