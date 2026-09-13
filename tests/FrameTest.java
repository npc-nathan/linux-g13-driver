import com.booker.g13.Lcd;
import com.booker.g13.LcdFont;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests the tool's screen model: the font table against the driver's own Font.h, the pixel
 * maths, the protocol parsing. Writes the frame it builds so the Python side can compare
 * the same layout byte for byte.
 */
public class FrameTest {
    static int fail = 0;
    /** The repo root: -Dg13.repo=... when it is run from somewhere else. */
    static final String REPO = System.getProperty("g13.repo", ".");

    static void check(String what, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) fail++;
        System.out.printf("%-46s %-30s %s%n", what, "-> " + actual, ok ? "ok" : "FAIL (expected " + expected + ")");
    }

    static byte[] fontHeader() throws Exception {
        String text = Files.readString(Path.of(REPO + "/g13-driver/src/cpp/Font.h"));
        String body = text.split("\\{", 2)[1].split("\\}", 2)[0];
        Matcher matcher = Pattern.compile("0x([0-9A-Fa-f]{2})").matcher(body);
        List<Byte> values = new ArrayList<>();
        while (matcher.find()) {
            values.add((byte) Integer.parseInt(matcher.group(1), 16));
        }
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = values.get(i);
        }
        return bytes;
    }

    static int ink(Lcd lcd) {
        int count = 0;
        for (int y = 0; y < Lcd.HEIGHT; y++) {
            for (int x = 0; x < Lcd.WIDTH; x++) {
                if (lcd.pixel(x, y)) count++;
            }
        }
        return count;
    }

    public static void main(String[] args) throws Exception {
        // 1. The font the tool draws with is the font the driver draws with.
        Field field = LcdFont.class.getDeclaredField("GLYPHS");
        field.setAccessible(true);
        byte[] java = (byte[]) field.get(null);
        byte[] driver = fontHeader();
        check("font table size matches Font.h", driver.length, java.length);
        check("font bytes identical to Font.h", true, Arrays.equals(driver, java));
        check("one glyph per character", true, driver.length % 5 == 0);

        // 2. Glyph pixels go where the driver puts them: 'T' has a bar along its top row.
        Lcd letter = new Lcd();
        letter.text(0, 0, "T");
        check("'T' has ink across the top", true, letter.pixel(0, 0) && letter.pixel(4, 0));
        check("'T' is 7 rows tall", true, letter.pixel(2, 6) && !letter.pixel(2, 7));
        check("characters advance by six", true, ink(letter) < ink(letterOf("TT")));

        // 3. Bars stay inside their track at every percentage.
        final int trackX = 10;
        final int trackY = 10;
        final int trackWidth = 40;
        final int trackHeight = 9;
        final int interior = trackWidth - 2;

        Lcd emptyBar = new Lcd();
        emptyBar.bar(trackX, trackY, trackWidth, trackHeight, 0);
        check("0% bar is hollow", 0, inkWithin(emptyBar, trackX + 1, trackY + 1,
                trackX + trackWidth - 1, trackY + trackHeight - 1));
        check("0% bar keeps its outline", true, emptyBar.pixel(trackX, trackY)
                && emptyBar.pixel(trackX + trackWidth - 1, trackY + trackHeight - 1));

        Lcd fullBar = new Lcd();
        fullBar.bar(trackX, trackY, trackWidth, trackHeight, 100);
        check("100% bar fills the interior", interior * (trackHeight - 2),
                inkWithin(fullBar, trackX + 1, trackY + 1, trackX + trackWidth - 1, trackY + trackHeight - 1));

        Lcd halfBar = new Lcd();
        halfBar.bar(trackX, trackY, trackWidth, trackHeight, 62);
        final int filled = interior * 62 / 100;
        check("62% fill width", true, halfBar.pixel(trackX + filled, trackY + 1)
                && !halfBar.pixel(trackX + filled + 1, trackY + 1));

        Lcd clampedLow = new Lcd();
        clampedLow.bar(trackX, trackY, trackWidth, trackHeight, -20);
        check("-20% clamps to empty", 0, inkWithin(clampedLow, trackX + 1, trackY + 1,
                trackX + trackWidth - 1, trackY + trackHeight - 1));
        Lcd clampedHigh = new Lcd();
        clampedHigh.bar(trackX, trackY, trackWidth, trackHeight, 300);
        check("300% clamps to full", interior * (trackHeight - 2),
                inkWithin(clampedHigh, trackX + 1, trackY + 1, trackX + trackWidth - 1, trackY + trackHeight - 1));

        // Nothing outside the track, whatever the percentage.
        boolean outside = false;
        for (double percent : new double[]{0, 50, 88, 100, -20, 300}) {
            Lcd probe = new Lcd();
            probe.bar(trackX, trackY, trackWidth, trackHeight, percent);
            if (probe.pixel(trackX + trackWidth, trackY + 2) || probe.pixel(trackX + 2, trackY + trackHeight)) {
                outside = true;
            }
        }
        check("no bar spills outside its track", false, outside);

        // 4. The frame the daemon would draw, written out for the Python comparison.
        Lcd screen = new Lcd();
        screen.rect(0, 0, 160, 1);
        screen.rect(0, 42, 160, 43);
        screen.rect(0, 0, 1, 43);
        screen.rect(159, 0, 160, 43);
        screen.rect(1, 9, 159, 10);
        screen.bar(28, 12, 100, 9, 62);
        screen.bar(28, 22, 100, 9, 45);
        screen.bar(28, 32, 100, 9, 88);
        Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "java-frame.hex"),
                screen.hex());
        check("frame is 960 bytes", 1920, screen.hex().length());

        // 5. Parsing what the daemon publishes.
        List<String> published = List.of("#bitmap " + screen.hex(), "#text 3 12 CPU", "#text 145 12 6%");
        Lcd parsed = Lcd.of(published);
        check("published frame parses", true, parsed != null && parsed.hex().equals(screen.hex()));
        List<Lcd.Placement> placements = Lcd.placements(published);
        check("placements parsed", 2, placements.size());
        check("placement position", 145, placements.get(1).x);
        check("placement right edge", 145 + 12, placements.get(1).right());
        check("no frame means null", null, Lcd.of(List.of("#text 3 12 CPU")));

        // 6. Drawing off the edge is clipped, not fatal.
        Lcd clipped = new Lcd();
        clipped.rect(-5, -5, 500, 500);
        clipped.text(-3, -2, "clipped");
        clipped.bar(-10, -10, 30, 30, 50);
        check("clipping keeps the frame the right size", Lcd.FRAME_BYTES * 2, clipped.hex().length());

        System.out.println(fail == 0 ? "FRAME TEST: all checks passed" : "FRAME TEST: " + fail + " FAILURES");
        System.exit(fail == 0 ? 0 : 1);
    }

    static int inkWithin(Lcd lcd, int x0, int y0, int x1, int y1) {
        int count = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (lcd.pixel(x, y)) count++;
            }
        }
        return count;
    }

    static Lcd letterOf(String text) {
        Lcd lcd = new Lcd();
        lcd.text(0, 0, text);
        return lcd;
    }
}
