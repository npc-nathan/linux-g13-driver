---
name: Bug report
about: Something does not work
labels: bug
---

**What happened**

**What you expected instead**

**Does it happen every time, or sometimes?**

**Your setup**

- Driver version / commit:
- Distribution and version:
- Python version (`python3 --version`):
- Java and Maven, if the configuration tool is involved:
- Which G13 revision, if the keys or screen are involved:

**The output of these two commands**

```
g13-visuals --status
g13-visuals --sources
```

**If an applet is involved**

```
g13-applet check --values ~/.config/g13/applets/<name>.json
```

That last one prints what each source reads right now, which is usually the whole answer: a blank
value is almost always a source that cannot read anything, not a broken screen.

**Anything else**

Anything from `journalctl --user -u g13 -u g13-visuals -n 50`.
