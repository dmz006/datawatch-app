#!/usr/bin/env python3
"""Build a README slideshow GIF from screenshots (Pillow).

  make-slideshow.py OUT.gif IMG [IMG ...] [--frame-ms 2500] [--max-kb 400]
                    [--width 960]

Frames are scaled to --width (aspect kept; every frame letterboxed onto the
first frame's canvas size), quantised to an adaptive palette, and looped
forever. If the result is larger than --max-kb the width shrinks by 10 %
until it fits (or reaches 240 px). Exit 1 if no input image exists.
"""
import argparse
import io
import os
import sys

from PIL import Image


def render(paths, width, frame_ms):
    frames = []
    canvas = None
    for p in paths:
        im = Image.open(p).convert("RGB")
        h = round(im.height * width / im.width)
        im = im.resize((width, h), Image.LANCZOS)
        if canvas is None:
            canvas = im.size
        if im.size != canvas:
            # Same width; pad/crop height to the first frame (dark background).
            bg = Image.new("RGB", canvas, (16, 16, 20))
            bg.paste(im, (0, 0))
            im = bg
        frames.append(im.quantize(colors=128, method=Image.MEDIANCUT, dither=Image.NONE))
    buf = io.BytesIO()
    frames[0].save(buf, format="GIF", save_all=True, append_images=frames[1:],
                   duration=frame_ms, loop=0, optimize=True, disposal=1)
    return buf.getvalue()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("images", nargs="+")
    ap.add_argument("--frame-ms", type=int, default=2500)
    ap.add_argument("--max-kb", type=int, default=400)
    ap.add_argument("--width", type=int, default=960)
    a = ap.parse_args()

    paths = [p for p in a.images if os.path.isfile(p)]
    if not paths:
        print("make-slideshow: no input images", file=sys.stderr)
        return 1
    width = a.width
    while True:
        data = render(paths, width, a.frame_ms)
        if len(data) <= a.max_kb * 1024 or width <= 240:
            break
        width = int(width * 0.9)
    with open(a.out, "wb") as f:
        f.write(data)
    print(f"{a.out}: {len(paths)} frames, {width}px wide, {len(data) // 1024} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
