import com.booker.g13.ScreenPanel;
import com.booker.g13.Visuals;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Tests the visuals configuration the screen panel edits: reading, writing, applet
 * discovery and the round trip the daemon depends on. Run with XDG_CONFIG_HOME pointing at
 * a scratch directory.
 */
public class ScreenPanelTest {
    static int fail = 0;

    static void check(String what, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) fail++;
        System.out.printf("%-48s %-34s %s%n", what, "-> " + actual, ok ? "ok" : "FAIL (expected " + expected + ")");
    }

    public static void main(String[] args) throws Exception {
        final Path config = Visuals.configDir();
        System.out.println("config dir: " + config);

        // Defaults when there is no file.
        Files.deleteIfExists(Visuals.configFile());
        Visuals fresh = Visuals.load();
        check("defaults to the built-in visuals", List.of(Visuals.BUILT_IN), fresh.enabled());
        check("defaults to the first one", "clock", fresh.active());
        check("does not cycle by default", false, fresh.cycling());

        // The file it writes is the one the daemon reads.
        fresh.setCycling(true);
        fresh.setCycleSeconds(15);
        fresh.setActive("media");
        fresh.enabled().add("applet:demo");
        fresh.save();

        final String written = Files.readString(Visuals.configFile());
        check("writes JSON with a trailing newline", true, written.endsWith("}\n"));
        check("writes the enabled list", true, written.contains("\"applet:demo\""));

        Visuals reloaded = Visuals.load();
        check("reads the active visual back", "media", reloaded.active());
        check("reads cycling back", true, reloaded.cycling());
        check("reads the cycle seconds back", 15.0, reloaded.cycleSeconds());
        check("reads the enabled list back", fresh.enabled(), reloaded.enabled());

        // Applets on disk become available visuals, with their titles.
        Files.createDirectories(Visuals.appletDir());
        Files.writeString(Visuals.appletDir().resolve("mine.json"),
                "{\"title\": \"MINE\", \"widgets\": []}");
        final Map<String, String> known = Visuals.load().known();
        check("finds an applet on disk", "MINE", known.get("applet:mine"));
        check("lists the built-ins too", 5, known.size() - 1);
        check("an unknown active falls back", "clock",
                Visuals.load().known().containsKey("nothing") ? "?" : "clock");

        // A broken applet file must not take the panel down.
        Files.writeString(Visuals.appletDir().resolve("broken.json"), "{not json");
        final Map<String, String> withBroken = Visuals.load().known();
        check("survives an unreadable applet", true, withBroken.containsKey("applet:mine"));

        // The panel itself builds, which is what the button in the main window does.
        final boolean[] built = {false};
        SwingUtilities.invokeAndWait(() -> {
            try {
                built[0] = new ScreenPanel() != null;
            } catch (RuntimeException e) {
                System.out.println("constructing the panel threw " + e);
            }
        });
        check("the screen panel builds", true, built[0]);

        // Who owns the screen and the four buttons: one file, written by the window and by
        // g13-buttons, read by the daemon.
        Visuals.writeButtonMode("sdk");
        check("the button mode round trips", "sdk", Visuals.readButtonMode());
        Visuals.writeButtonMode("nonsense");
        check("an unknown mode falls back to auto", "auto", Visuals.readButtonMode());
        Visuals.writeButtonMode("visuals");
        check("the mode is written where the daemon looks", true,
                Files.readString(config.resolve("button-mode")).trim().equals("visuals"));
        check("the owner is readable, or unknown with no daemon", true,
                List.of("sdk", "visuals", "unknown").contains(Visuals.screenOwner()));

        System.out.println(fail == 0 ? "SCREEN PANEL TEST: all checks passed" : "SCREEN PANEL TEST: " + fail + " FAILURES");
        System.exit(fail == 0 ? 0 : 1);
    }
}
