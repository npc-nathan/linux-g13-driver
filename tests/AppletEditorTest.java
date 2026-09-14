import com.booker.g13.AppletEditor;
import com.booker.g13.Json;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * The applet designer's model and its JSON, without a display.
 *
 * Headless, with XDG_CONFIG_HOME pointed at a scratch directory by the test runner. What matters
 * here is that what is written is what the daemon and `g13-applet check` will read: real JSON, the
 * applet's fields in a readable order, and no widget field invented out of nothing.
 *
 * Run with:
 *   env XDG_CONFIG_HOME=/tmp/scratch java -Djava.awt.headless=true -cp <classes>:/path AppletEditorTest.java
 */
public class AppletEditorTest {

    private static int failures = 0;

    private static void check(final String what, final Object expected, final Object actual) {
        final boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) {
            failures++;
        }
        System.out.printf("%-60s %-24s %s%n", what, "-> " + actual, ok ? "ok"
                : "FAIL (expected " + expected + ")");
        System.out.flush();
    }

    /** Reads back what was written, as the daemon would. */
    private static Map<String, Object> reread(final String name) throws Exception {
        return Json.parseObject(Files.readString(AppletEditor.path(name)));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> widgetsOf(final Map<String, Object> definition) {
        return (List<Map<String, Object>>) (List<?>) definition.get("widgets");
    }

    public static void main(final String[] args) throws Exception {
        // --- what it writes has to be JSON the other side can read ---------------------------
        check("a round trip through the writer and reader", "kept",
                Json.parseObject(Json.write(Json.parseObject("{\"a\": \"kept\"}"))).get("a"));
        check("numbers stay numbers", 12.0, Json.parseObject("{\"n\": 12}").get("n"));
        check("whole numbers are written without .0", true,
                Json.write(Json.parseObject("{\"n\": 12.0}")).contains("\"n\": 12"));
        check("null, true and false survive", "{\"a\":null,\"b\":true,\"c\":false}",
                Json.write(Json.parseObject("{\"a\":null,\"b\":true,\"c\":false}"))
                        .replaceAll("\\s+", ""));
        final Map<String, Object> nested = Json.parseObject(Json.write(Json.parseObject(
                "{\"widgets\":[{\"type\":\"text\",\"format\":\"CPU 42%\"}]}")));
        check("nested objects and lists survive", "CPU 42%",
                widgetsOf(nested).get(0).get("format"));
        check("a quote inside a string survives", "say \"hi\"",
                Json.parseObject(Json.write(Json.parseObject("{\"s\": \"say \\\"hi\\\"\"}")))
                        .get("s"));
        try {
            Json.parse("{not json");
            check("rubbish is refused", "an exception", "no exception");
        } catch (Json.BadJson expected) {
            check("rubbish is refused", true, true);
        }

        // --- a new applet ---------------------------------------------------------------------
        AppletEditor fresh = AppletEditor.forNew("designer-test");
        check("a new one has a widget to start from", 1, fresh.widgets().size());
        check("with a readable title", "DESIGNER-TEST", fresh.title());
        fresh.save();
        check("and it is written", true, Files.exists(AppletEditor.path("designer-test")));
        Map<String, Object> saved = reread("designer-test");
        check("the name is in the file", "designer-test", saved.get("name"));
        check("and its one widget", 1, widgetsOf(saved).size());

        // --- editing fields -------------------------------------------------------------------
        fresh.setTitle("EDITED");
        fresh.setInterval(2.5);
        fresh.setBorder(false);
        fresh.setFollowSeconds(30);
        fresh.save();
        saved = reread("designer-test");
        check("the title is kept", "EDITED", saved.get("title"));
        check("the interval is kept", 2.5, saved.get("interval"));
        check("the border is kept", false, saved.get("border"));
        check("follow seconds are written as the daemon reads them", 30.0,
                ((Map<?, ?>) saved.get("follow")).get("seconds"));
        fresh.setFollowSeconds(0);
        fresh.save();
        check("and follow can be taken away again", false,
                reread("designer-test").containsKey("follow"));

        // --- widgets --------------------------------------------------------------------------
        final int bar = fresh.addWidget("bar");
        check("a bar comes with the fields a bar uses", true,
                fresh.widgets().get(bar).containsKey("max") && fresh.widgets().get(bar).containsKey("w"));
        check("and not the ones a text line uses", false, fresh.widgets().get(bar).containsKey("format"));

        final int text = fresh.addWidget("text");
        check("a text line comes with a format", true,
                fresh.widgets().get(text).containsKey("format"));
        fresh.set(text, "x", 40.0);
        fresh.set(text, "align", "right");
        check("a field can be set", 40.0, fresh.widgets().get(text).get("x"));
        check("and a field can be cleared", false,
                clearAndCheck(fresh, text, "align"));

        final int copy = fresh.duplicateWidget(text);
        check("a copy goes right below the original", true, copy == text + 1);
        check("and is moved down so the two are not on top of each other", 21.0,
                fresh.widgets().get(copy).get("y"));
        check("the widget count after adding and copying", 4, fresh.widgets().size());

        final int moved = fresh.moveWidget(copy, -1);
        check("a widget can be moved up the order", text, moved);
        check("and moving past the start stays put", 0, fresh.moveWidget(0, -1));

        fresh.removeWidget(0);
        check("a widget can be removed", 3, fresh.widgets().size());
        fresh.save();
        check("and the file agrees after all of that", 3,
                widgetsOf(reread("designer-test")).size());

        // --- names ----------------------------------------------------------------------------
        check("a safe name is accepted", true, AppletEditor.validName("night-city-2"));
        check("a name with a slash is not", false, AppletEditor.validName("../../etc/passwd"));
        check("nor one with spaces", false, AppletEditor.validName("my applet"));
        check("existing applets can be listed", true,
                AppletEditor.names().contains("designer-test"));

        // --- reading back something that already exists ---------------------------------------
        final AppletEditor again = AppletEditor.load("designer-test");
        check("an applet can be loaded again", 3, again.widgets().size());
        check("with its title", "EDITED", again.title());
        check("and its summaries are readable", true,
                again.summary(0).startsWith("bar") || again.summary(0).startsWith("text"));

        try {
            AppletEditor.load("no-such-applet-here");
            check("loading a file that is not there fails", "an exception", "no exception");
        } catch (java.io.IOException expected) {
            check("loading a file that is not there fails", true, true);
        }

        again.delete();
        check("an applet can be deleted", false, Files.exists(AppletEditor.path("designer-test")));

        // --- the source aliases, which is how a game applet says where its numbers come from ---
        final AppletEditor withSources = AppletEditor.forNew("aliases-test");
        withSources.sources().put("ammo", "json:/tmp/hud.json#ammo");
        withSources.save();
        check("an alias is written", "json:/tmp/hud.json#ammo",
                ((Map<?, ?>) reread("aliases-test").get("sources")).get("ammo"));
        withSources.delete();

        // --- what it writes has to be something the pad would accept, not merely files that parse
        // A designed applet is left behind on purpose: the test runner runs `g13-applet check` over
        // this directory straight afterwards, which is the one authority on whether a design works.
        final AppletEditor cross = AppletEditor.forNew("designer-cross-check");
        cross.setTitle("DESIGN");
        cross.setInterval(1.0);
        final int gauge = cross.addWidget("bar");
        cross.set(gauge, "y", 30.0);
        cross.set(gauge, "max", 100.0);
        cross.save();
        check("a designed applet is left for the checker", true,
                Files.exists(AppletEditor.path("designer-cross-check")));

        System.out.println(failures == 0 ? "APPLET EDITOR TEST: all checks passed"
                : "APPLET EDITOR TEST: " + failures + " FAILURES");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static boolean clearAndCheck(final AppletEditor editor, final int index,
                                         final String field) {
        editor.set(index, field, "");
        return editor.widgets().get(index).containsKey(field);
    }
}
