import com.booker.g13.Sources;
import com.booker.g13.SourcesPanel;

import java.nio.file.Files;

/**
 * The sources panel's model: what applets may read, and the file the daemon enforces.
 *
 * Headless, with XDG_CONFIG_HOME pointed at a scratch directory by the test runner, so nothing
 * here touches the real configuration. Run with:
 *   env XDG_CONFIG_HOME=/tmp/scratch java -Djava.awt.headless=true -cp <classes>:/path SourcesPanelTest.java
 */
public class SourcesPanelTest {

    private static int failures = 0;

    private static void check(final String what, final Object expected, final Object actual) {
        final boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) {
            failures++;
        }
        System.out.printf("%-58s %-26s %s%n", what, "-> " + actual, ok ? "ok"
                : "FAIL (expected " + expected + ")");
        System.out.flush();
    }

    private static String squashed(final String text) {
        return text.replaceAll("\\s+", "");
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("sources file: " + Sources.configFile());
        Files.deleteIfExists(Sources.configFile());

        // Everything starts switched on: an install that has never opened this window must behave
        // exactly as it did before the panel existed.
        for (final Sources.Kind kind : Sources.KINDS) {
            check(kind.name + " starts switched on", true, Sources.enabled(kind.name));
        }
        check("and nothing is written until something is switched", false,
                Files.exists(Sources.configFile()));

        Sources.setEnabled("cmd", false);
        check("switching one off is written in the shape the daemon reads",
                "{\"disabled\":[\"cmd\"]}", squashed(Files.readString(Sources.configFile())));
        check("and reads back as off", false, Sources.enabled("cmd"));
        check("the others are untouched", true, Sources.enabled("file"));
        check("and the file lists just that one", 1, Sources.disabled().size());

        Sources.setEnabled("json", false);
        check("two switched off, in the order they were switched", true,
                Sources.disabled().toString().equals("[cmd, json]")
                        || Sources.disabled().toString().equals("[json, cmd]"));

        Sources.setEnabled("cmd", true);
        check("switching one back on leaves the other", "[json]", Sources.disabled().toString());

        Sources.setEnabled("json", true);
        check("all on again is an empty list", "{\"disabled\":[]}",
                squashed(Files.readString(Sources.configFile())));

        try {
            Sources.setEnabled("bogus", false);
            check("a kind the daemon does not know is refused", "an exception", "no exception");
        } catch (IllegalArgumentException expected) {
            check("a kind the daemon does not know is refused", true, true);
        }

        check("isKind knows the kinds", true, Sources.isKind("built-in") && Sources.isKind("cmd"));
        check("and not made-up ones", false, Sources.isKind("sql"));

        // The window's live line has to survive there being no daemon at all.
        check("the readings line is never empty", true,
                SourcesPanel.readingsText() != null && !SourcesPanel.readingsText().isEmpty());

        Files.deleteIfExists(Sources.configFile());
        System.out.println(failures == 0 ? "SOURCES PANEL TEST: all checks passed"
                : "SOURCES PANEL TEST: " + failures + " FAILURES");
        System.exit(failures == 0 ? 0 : 1);
    }
}
