"""Renders the same layout as FrameTest.java and compares the frames byte for byte.

This is what makes the tool's preview trustworthy: if the Java screen model and the
daemon's Python screen model disagree about a single pixel, this fails.
"""
import importlib.util
import os
import sys
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(REPO, "g13-driver", "src", "scripts"))
import g13lcd

rows = g13lcd.blank()
g13lcd.draw_rect(rows, 0, 0, 160, 1)
g13lcd.draw_rect(rows, 0, 42, 160, 43)
g13lcd.draw_rect(rows, 0, 0, 1, 43)
g13lcd.draw_rect(rows, 159, 0, 160, 43)
g13lcd.draw_rect(rows, 1, 9, 159, 10)
g13lcd.draw_bar(rows, 28, 12, 100, 9, 62)
g13lcd.draw_bar(rows, 28, 22, 100, 9, 45)
g13lcd.draw_bar(rows, 28, 32, 100, 9, 88)
python_frame = g13lcd.pixels_to_frame(rows).hex()

java_frame = open(os.path.join(tempfile.gettempdir(), "java-frame.hex")).read().strip()

print("python frame: %d hex digits" % len(python_frame))
print("java   frame: %d hex digits" % len(java_frame))

if python_frame == java_frame:
    print("CROSS-CHECK: the tool and the daemon render the same pixels")
    sys.exit(0)

differences = [index for index in range(0, min(len(python_frame), len(java_frame)))
               if python_frame[index] != java_frame[index]]
print("CROSS-CHECK FAILED: %d hex digits differ, first at %s"
      % (len(differences), differences[:8]))
# Where is that in pixels?
for index in differences[:5]:
    byte_index = index // 2
    x = byte_index % 160
    y = (byte_index // 160) * 8 + (int(python_frame[index], 16) ^ int(java_frame[index], 16)).bit_length() - 1
    print("  byte %d -> pixel (%d, %d): python %s, java %s"
          % (byte_index, x, y, python_frame[index], java_frame[index]))
sys.exit(1)
