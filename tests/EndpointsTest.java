import com.booker.g13.Installed;
import com.booker.g13.Sources;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The endpoints editor's model: the named web addresses a {@code http:} source may use.
 *
 * The point of these is the credential: a token lives here, an applet carries only the endpoint's
 * name, and the file is written readable only by its owner. Run headless, with XDG_CONFIG_HOME
 * pointed at a scratch directory by the test runner.
 */
public class EndpointsTest {

    private static int failures = 0;

    private static void check(final String what, final Object expected, final Object actual) {
        final boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) {
            failures++;
        }
        System.out.printf("%-58s %-22s %s%n", what, "-> " + actual, ok ? "ok"
                : "FAIL (expected " + expected + ")");
        System.out.flush();
    }

    public static void main(final String[] args) throws Exception {
        if (Files.exists(Sources.endpointsFile())) {
            Files.delete(Sources.endpointsFile());
        }
        check("with no file there are no addresses", 0, Sources.endpoints().size());

        final Map<String, Sources.Endpoint> wanted = new LinkedHashMap<>();
        final Sources.Endpoint home = new Sources.Endpoint();
        home.url = "http://127.0.0.1:8123";
        home.token = "S3CRET";
        home.timeout = 2;
        wanted.put("home", home);
        Sources.saveEndpoints(wanted);

        check("it is written", true, Files.exists(Sources.endpointsFile()));
        Map<String, Sources.Endpoint> read = Sources.endpoints();
        check("and read back", 1, read.size());
        check("with its address", "http://127.0.0.1:8123", read.get("home").url);
        check("its token", "S3CRET", read.get("home").token);
        check("its timeout", 2.0, read.get("home").timeout);

        // A token lives in that file, so only its owner may read it.
        Object permissions = null;
        try {
            permissions = Files.getPosixFilePermissions(Sources.endpointsFile());
        } catch (Exception unsupported) {
            // Not a POSIX filesystem; the check below reports what it can.
        }
        check("nobody else can read the file", true,
                permissions != null && !permissions.toString().contains("OTHERS_READ"));

        // A key the window does not offer (headers) must survive being edited in the window.
        Files.writeString(Sources.endpointsFile(),
                "{\n  \"home\": {\n    \"url\": \"http://127.0.0.1:8123\",\n"
                + "    \"token\": \"S3CRET\",\n    \"headers\": {\"X-Test\": \"yes\"},\n"
                + "    \"insecure\": true\n  }\n}\n");
        final Map<String, Sources.Endpoint> again = Sources.endpoints();
        check("a checkbox comes back", true, again.get("home").insecure);
        check("and the address still does", "http://127.0.0.1:8123", again.get("home").url);
        Sources.saveEndpoints(again);
        check("headers the window never shows are kept", true,
                Files.readString(Sources.endpointsFile()).contains("X-Test"));

        // A name is a key in a JSON file and part of a spec, so it has to be safe.
        check("a plain name is fine", true, Sources.validEndpointName("home-2"));
        check("a space is not", false, Sources.validEndpointName("has space"));
        check("a slash is not", false, Sources.validEndpointName("a/b"));
        final Map<String, Sources.Endpoint> bad = new LinkedHashMap<>();
        bad.put("has space", new Sources.Endpoint());
        try {
            Sources.saveEndpoints(bad);
            check("and writing one is refused", "an exception", "it was written");
        } catch (RuntimeException expected) {
            check("and writing one is refused", true, true);
        }

        // The commands the windows run, rather than a second copy of what they do.
        check("the checker is installed or absent, never a broken path", true,
                Installed.command("g13-applet") == null
                        || Installed.command("g13-applet").canExecute());
        check("a missing command explains itself", true,
                Installed.run(null, "--read", "x").contains("not installed"));

        Files.deleteIfExists(Sources.endpointsFile());
        System.out.println(failures == 0 ? "ENDPOINTS TEST: all checks passed"
                : "ENDPOINTS TEST: " + failures + " FAILURES");
        System.exit(failures == 0 ? 0 : 1);
    }
}
