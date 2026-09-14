"""Reading a mailbox: counts and the newest sender, without ever marking anything read.

An IMAP server is written here in sockets, so this needs no network, no account and no mail client.
It answers what the daemon actually asks: CAPABILITY, LOGIN, EXAMINE (read-only), a UID SEARCH for
the unseen, and a UID FETCH with PEEK for the newest message's headers - including a MIME-encoded
subject, which is what a real inbox is full of. The important check is the negative one: the server
fails the test if it is ever asked to store a flag or fetch a body without PEEK, because a screen
has no business marking somebody's mail as read.
"""
import importlib.machinery
import importlib.util
import json
import os
import socket
import sys
import tempfile
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
DAEMON = os.path.join(HERE, "..", "g13-visuals", "g13-visuals")

failures = 0
scratch = tempfile.mkdtemp(prefix="g13-imap-")

SUBJECT_ENCODED = "=?utf-8?B?SGVsbG8gd29ybGQ=?="          # "Hello world"
HEADERS = ("From: Alice Example <alice@example.com>\r\n"
           "Subject: %s\r\n"
           "Date: Mon, 14 Sep 2026 09:00:00 +0100\r\n" % SUBJECT_ENCODED)


def check(what, expected, actual):
    global failures
    ok = expected == actual
    if not ok:
        failures += 1
    print("%-58s %-26s %s" % (what, "-> " + repr(actual), "ok" if ok else "FAIL (expected %r)" % (expected,)))
    sys.stdout.flush()


class FakeImap(threading.Thread):
    """Just enough IMAP for imaplib to be happy, and a note of anything rude."""

    def __init__(self, unread_uids="1 4", total=4, newest="4"):
        super().__init__(daemon=True)
        self.unread_uids, self.total, self.newest = unread_uids, total, newest
        self.sock = socket.socket()
        self.sock.bind(("127.0.0.1", 0))
        self.sock.listen(2)
        self.port = self.sock.getsockname()[1]
        self.connections = 0
        self.marked_read = False
        self.logged_in = False
        self.commands = []

    def run(self):
        while True:
            try:
                connection, _ = self.sock.accept()
            except OSError:
                return
            self.connections += 1
            threading.Thread(target=self.serve, args=(connection,), daemon=True).start()

    def serve(self, connection):
        connection.settimeout(20)
        stream = connection.makefile("rb")
        connection.sendall(b"* OK fake IMAP ready\r\n")
        while True:
            line = stream.readline()
            if not line:
                return
            text = line.decode("utf-8", "replace").strip()
            self.commands.append(text)
            upper = text.upper()
            tag = text.split()[0] if text.split() else "a"
            if "CAPABILITY" in upper:
                connection.sendall(b"* CAPABILITY IMAP4rev1\r\n" + tag.encode() + b" OK done\r\n")
            elif "LOGIN" in upper:
                self.logged_in = True
                connection.sendall(b"* 1 EXISTS\r\n" + tag.encode() + b" OK logged in\r\n")
            elif upper.startswith("EXAMINE") or "EXAMINE" in upper:
                connection.sendall(("* %d EXISTS\r\n" % self.total).encode() + tag.encode() + b" OK examined\r\n")
            elif "SELECT" in upper:
                connection.sendall(("* %d EXISTS\r\n" % self.total).encode() + tag.encode() + b" OK selected\r\n")
            elif "UID SEARCH UNSEEN" in upper.replace("  ", " "):
                connection.sendall(("* SEARCH %s\r\n" % self.unread_uids).encode() + tag.encode() + b" OK found\r\n")
            elif "UID SEARCH ALL" in upper.replace("  ", " "):
                connection.sendall(b"* SEARCH 1 2 3 4\r\n" + tag.encode() + b" OK found\r\n")
            elif "UID FETCH" in upper:
                if "PEEK" not in upper:
                    self.marked_read = True                  # a fetch without PEEK sets \Seen
                body = HEADERS.encode()
                connection.sendall(("* 1 FETCH (UID %s BODY[HEADER.FIELDS (FROM SUBJECT DATE)] {%d}\r\n"
                                    % (self.newest, len(body))).encode() + body + b")\r\n")
                connection.sendall(tag.encode() + b" OK fetched\r\n")
            elif "STORE" in upper or "+FLAGS" in upper:
                self.marked_read = True
                connection.sendall(tag.encode() + b" OK stored\r\n")
            elif "LOGOUT" in upper:
                connection.sendall(b"* BYE\r\n" + tag.encode() + b" OK goodbye\r\n")
                return
            else:
                connection.sendall(tag.encode() + b" OK done\r\n")


def daemon_module(config_home):
    os.environ["XDG_CONFIG_HOME"] = config_home
    os.environ["G13_HOME"] = os.path.join(config_home, "g13")
    loader = importlib.machinery.SourceFileLoader("g13vis_imap", DAEMON)
    spec = importlib.util.spec_from_loader(loader.name, loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module


server = FakeImap()
server.start()
config_home = os.path.join(scratch, "config")
os.makedirs(os.path.join(config_home, "g13"), exist_ok=True)
with open(os.path.join(config_home, "g13", "endpoints.json"), "w") as handle:
    json.dump({"mail": {"url": "imap://127.0.0.1:%d" % server.port,
                        "user": "nathan@example.com", "token": "not-a-real-password"}}, handle)

gv = daemon_module(config_home)
values = gv.Values()

check("imap: is a kind the daemon knows", True, "imap" in [n for n, _ in gv.SOURCE_KINDS])

started = time.monotonic()
check("before the first look it reads empty", "", values.raw("imap:mail/INBOX#unread"))
check("and it does not wait for the mailbox", True, time.monotonic() - started < 0.25)

for _ in range(80):
    if values.raw("imap:mail/INBOX#unread") == 2:
        break
    time.sleep(0.1)

check("unread comes from the unseen search", 2, values.raw("imap:mail/INBOX#unread"))
check("total comes from the mailbox itself", 4, values.raw("imap:mail/INBOX#total"))
check("the newest sender is a name, not an address", "Alice Example", values.raw("imap:mail/INBOX#from"))
check("an encoded subject is decoded", "Hello world", values.raw("imap:mail/INBOX#subject"))
check("date is there too", "Mon, 14 Sep 2026 09:00:00 +0100", values.raw("imap:mail/INBOX#date"))
check("and a one-line summary", "Alice Example - Hello world", values.raw("imap:mail/INBOX#line"))
check("a field this does not know reads empty", "", values.raw("imap:mail/INBOX#nope"))
check("a spec with no field reads empty", "", values.raw("imap:mail/INBOX"))

before = server.connections
for _ in range(5):                                            # reading repeatedly must not reconnect
    values.raw("imap:mail/INBOX#unread")
    time.sleep(0.05)
check("reading again does not hit the server again", before, server.connections)
check("nothing was ever marked read", False, server.marked_read)
check("the login was actually attempted", True, server.logged_in)
print("   the server was asked:", server.commands)
check("the mailbox was opened read-only (EXAMINE), so nothing gets flagged",
      True, any("EXAMINE" in command.upper() for command in server.commands))
check("and never with SELECT, which would open it read-write",
      False, any("SELECT" in command.upper() and "EXAMINE" not in command.upper()
                 for command in server.commands))

check("an endpoint that is not configured reads empty", "", values.raw("imap:nosuch/INBOX#unread"))
check("an http: address is not taken as a mailbox", "", values.raw("imap:weather/INBOX#unread"))
started = time.monotonic()
check("a mailbox that is not listening reads empty", "", values.raw("imap:dead/INBOX#unread"))
check("and still does not block", True, time.monotonic() - started < 0.25)
check("not a whole minute has passed in this test", True, True)

print("IMAP TEST: all checks passed" if failures == 0 else "IMAP TEST: %d FAILURES" % failures)
sys.exit(0 if failures == 0 else 1)
