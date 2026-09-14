"""The guide, checked against the code.

A document that drifts is worse than no document: it tells somebody to type a name that does not
exist, and they reasonably conclude the driver is broken. This reads docs/applets.md and insists
that every variable, kind, widget type, widget field and command it names is one the daemon or the
tool actually has - and that everything they have is named in the guide.

Run by tests/run-all-tests.sh, after the daemon and the tool have been built.
"""
import importlib.machinery
import importlib.util
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
DOC = os.path.join(REPO, "docs", "applets.md")
EDITOR = os.path.join(REPO, "g13-config-tool/src/main/java/com/booker/g13/AppletEditor.java")

failures = 0


def check(what, expected, actual):
    global failures
    ok = expected == actual
    if not ok:
        failures += 1
    shown = actual if len(str(actual)) < 90 else "%d entries" % len(actual)
    print("%-58s %-24s %s" % (what, "-> " + str(shown), "ok" if ok else "FAIL (expected %s)" % expected))
    sys.stdout.flush()


def load_daemon():
    loader = importlib.machinery.SourceFileLoader("g13visuals",
                                                  os.path.join(REPO, "g13-visuals", "g13-visuals"))
    spec = importlib.util.spec_from_loader("g13visuals", loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module


def in_the_tree(name):
    """Whether a command the guide names exists: a file of that name, or one the install makes."""
    for root, directories, files in os.walk(REPO):
        directories[:] = [d for d in directories if d not in (".git", "target", "__pycache__")]
        if name in files:
            return True
    # A wrapper the install writes has no source file of its own, so its name appears in the
    # Makefile, a desktop entry or the README instead. That counts as existing.
    for root, directories, files in os.walk(REPO):
        directories[:] = [d for d in directories if d not in (".git", "target", "__pycache__")]
        for entry in files:
            path = os.path.join(root, entry)
            if os.path.getsize(path) > 400_000:
                continue
            try:
                with open(path, encoding="utf-8", errors="ignore") as handle:
                    if name in handle.read():
                        return True
            except OSError:
                continue
    return False


def main():
    if not os.path.exists(DOC):
        print("DOC TEST: there is no docs/applets.md to check")
        return 1

    with open(DOC, encoding="utf-8") as handle:
        doc = handle.read()
    daemon = load_daemon()
    with open(EDITOR, encoding="utf-8") as handle:
        editor = handle.read()

    # Everything the daemon has, the guide must name.
    missing = [name for name in daemon.BUILT_IN_SOURCES if "`%s`" % name not in doc]
    check("every variable is in the guide", [], missing)

    missing = [name for name, _why in daemon.SOURCE_KINDS
               if name != "built-in" and "`%s:" % name not in doc]
    check("every kind of source is in the guide", [], missing)

    types = re.search(r"TYPES = Arrays\.asList\((.*?)\);", editor, re.S)
    widget_types = re.findall(r'"(\w+)"', types.group(1)) if types else []
    missing = [name for name in widget_types if "`%s`" % name not in doc]
    check("every widget type is in the guide", [], missing)

    fields = set()
    for _name, group in re.findall(r'FIELDS\.put\("(\w+)", List\.of\(([^)]*)\)\)', editor):
        fields.update(re.findall(r'"(\w+)"', group))
    # A check that reads nothing passes for the wrong reason, which is how a test stops working
    # without anybody noticing, so the lists it checks are counted first.
    check("the tool's widget list was read at all", True, len(widget_types) > 5)
    check("the tool's field list was read at all", True, len(fields) > 8)
    missing = sorted(field for field in fields if "`%s`" % field not in doc)
    check("every widget field is in the guide", [], missing)

    # And every command the guide tells somebody to type has to exist.
    commands = sorted(set(re.findall(r"`(g13-[a-z][a-z-]+)", doc)))
    check("the guide names some commands at all", True, len(commands) > 4)
    missing = [name for name in commands if not in_the_tree(name)]
    check("every command in the guide exists in the tree", [], missing)

    # The guide's own contents list has to point at real headings, or its links go nowhere.
    anchors = set()
    for heading in re.findall(r"^#{1,3} (.+)$", doc, re.M):
        anchors.add("#" + re.sub(r"[^a-z0-9 -]", "", heading.lower()).replace(" ", "-"))
    broken = [link for link in re.findall(r"\]\(#([^)]+)\)", doc) if "#" + link not in anchors]
    check("every link in the guide goes somewhere", [], broken)

    print("DOC TEST: all checks passed" if failures == 0
          else "DOC TEST: %d FAILURES" % failures)
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
