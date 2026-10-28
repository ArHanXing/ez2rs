#!/usr/bin/env python3
"""把 Refined Storage 2 的 GUI 重绘成 AE2 1.21.1 风格（LDLib 硬边面板）。

不是整体调色：外轮廓按连通域拉平成矩形面板，重新画 1px 深描边 + 1px 高光 + 填充；
烘在底图里的槽位、内嵌区（搜索框/列表底）全部识别出来按 AE2 的凹陷件重画；
只有图标/字形/彩色状态色保留原样（做一次 AE2 调色板映射）。

AE2 参考件（.ae2guiext/assets/ldlib/textures/gui/）：
    background.png  16x16   外描边 #413F54 -> 高光 #F2F2F2 -> 填充 #CBCCD4
    slot.png        18x18   #F2F2F2 外圈 -> #9A9FB4 内圈 -> #ADB0C4 填充

用法：
    python3 tools/ae2ify_gui.py                 # 全部界面 + sprite
    python3 tools/ae2ify_gui.py grid storage    # 只处理指定底图
"""

import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / ".rs2src/refinedstorage-common/src/main/resources/assets/refinedstorage/textures/gui"
DST = ROOT / "src/main/resources/resourcepacks/ae2_gui/assets/refinedstorage/textures/gui"

OUTLINE = (65, 63, 84)
HIGHLIGHT = (242, 242, 242)
FILL = (203, 204, 212)
RING = (154, 159, 180)
INSET = (173, 176, 196)
MID = (135, 143, 165)

GRAYS = {
    (0, 0, 0): OUTLINE,
    (55, 55, 55): RING,
    (85, 85, 85): MID,
    (129, 129, 129): RING,
    (139, 139, 139): INSET,
    (198, 198, 198): FILL,
    (219, 219, 219): INSET,
    (255, 255, 255): HIGHLIGHT,
}
TOLERANCE = 6

# 旧图边框用到的颜色：落在边框带里时不粘贴
FRAME_COLORS = {
    (0, 0, 0), (255, 255, 255), (55, 55, 55), (85, 85, 85), (198, 198, 198),
}
SLOT_FILL = (139, 139, 139)
PANEL_FILL = (198, 198, 198)
INSET_FILL = (219, 219, 219)


def map_gray(rgb):
    if rgb in GRAYS:
        return GRAYS[rgb]
    if max(rgb) - min(rgb) <= TOLERANCE:
        best, dist = None, None
        for src, dst in GRAYS.items():
            d = abs(src[0] - rgb[0])
            if dist is None or d < dist:
                best, dist = dst, d
        return best
    return rgb


def draw_panel(image, box):
    x0, y0, x1, y1 = box
    pixels = image.load()
    for y in range(max(y0, 0), min(y1, image.height)):
        for x in range(max(x0, 0), min(x1, image.width)):
            if x == x0 or y == y0 or x == x1 - 1 or y == y1 - 1:
                pixels[x, y] = OUTLINE + (255,)
            elif x == x0 + 1 or y == y0 + 1 or x == x1 - 2 or y == y1 - 2:
                pixels[x, y] = HIGHLIGHT + (255,)
            else:
                pixels[x, y] = FILL + (255,)


def draw_inset(image, box):
    """内嵌区（搜索框 / 列表底）：参考包口径 —— 1px 高光外圈 + 1px 深圈 + 凹陷填充。"""
    x0, y0, x1, y1 = box
    pixels = image.load()
    for y in range(max(y0, 0), min(y1, image.height)):
        for x in range(max(x0, 0), min(x1, image.width)):
            edge = x == x0 or y == y0 or x == x1 - 1 or y == y1 - 1
            inner = x == x0 + 1 or y == y0 + 1 or x == x1 - 2 or y == y1 - 2
            if edge:
                pixels[x, y] = HIGHLIGHT + (255,)
            elif inner:
                pixels[x, y] = RING + (255,)
            else:
                pixels[x, y] = INSET + (255,)


def draw_slot(image, box):
    """槽位：参考包口径 —— 18x18 格里放 16x16 的格子，
    顶边 1px #9A9FB4，其余 #ADB0C4，格间自然留出 2px 面板色。"""
    x0, y0, x1, y1 = box
    pixels = image.load()
    for y in range(y0 + 1, y1 - 1):
        for x in range(x0 + 1, x1 - 1):
            pixels[x, y] = (RING if y == y0 + 1 else INSET) + (255,)


def components(alpha):
    width, height = alpha.size
    px = alpha.load()
    seen = bytearray(width * height)
    boxes = []
    for y in range(height):
        for x in range(width):
            if px[x, y] == 0 or seen[y * width + x]:
                continue
            stack = [(x, y)]
            seen[y * width + x] = 1
            minx = maxx = x
            miny = maxy = y
            count = 0
            while stack:
                cx, cy = stack.pop()
                count += 1
                minx, maxx = min(minx, cx), max(maxx, cx)
                miny, maxy = min(miny, cy), max(maxy, cy)
                for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                    if 0 <= nx < width and 0 <= ny < height:
                        idx = ny * width + nx
                        if not seen[idx] and px[nx, ny] > 0:
                            seen[idx] = 1
                            stack.append((nx, ny))
            if count > 50:
                boxes.append((minx, miny, maxx + 1, maxy + 1))
    return boxes


def find_slots(image):
    px = image.load()
    width, height = image.size
    slots = []
    for y in range(1, height - 17):
        for x in range(1, width - 17):
            if px[x, y][:3] != SLOT_FILL or px[x, y][3] == 0:
                continue
            if px[x - 1, y - 1][:3] not in ((55, 55, 55), (85, 85, 85)):
                continue
            ok = True
            for yy in range(y, y + 16):
                for xx in range(x, x + 16):
                    if px[xx, yy][:3] != SLOT_FILL or px[xx, yy][3] == 0:
                        ok = False
                        break
                if not ok:
                    break
            if ok:
                slots.append((x - 1, y - 1, x + 17, y + 17))
    unique = []
    for box in slots:
        if not any(abs(box[0] - u[0]) < 9 and abs(box[1] - u[1]) < 9 for u in unique):
            unique.append(box)
    return unique


def find_insets(image):
    px = image.load()
    width, height = image.size
    seen = bytearray(width * height)
    boxes = []
    for y in range(height):
        for x in range(width):
            if px[x, y][:3] != INSET_FILL or px[x, y][3] == 0 or seen[y * width + x]:
                continue
            stack = [(x, y)]
            seen[y * width + x] = 1
            minx = maxx = x
            miny = maxy = y
            count = 0
            while stack:
                cx, cy = stack.pop()
                count += 1
                minx, maxx = min(minx, cx), max(maxx, cx)
                miny, maxy = min(miny, cy), max(maxy, cy)
                for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                    if 0 <= nx < width and 0 <= ny < height:
                        idx = ny * width + nx
                        if not seen[idx] and px[nx, ny][:3] == INSET_FILL and px[nx, ny][3] > 0:
                            seen[idx] = 1
                            stack.append((nx, ny))
            w, h = maxx - minx + 1, maxy - miny + 1
            if count > 120 and count > 0.75 * w * h and w > 8 and h > 8:
                boxes.append((minx, miny, maxx + 1, maxy + 1))
    return boxes


def convert_screen(image):
    width, height = image.size
    out = Image.new("RGBA", image.size, (0, 0, 0, 0))
    boxes = components(image.getchannel("A"))
    # 整块都是 #DBDBDB 的组件（列表行、预览底）本身就是一个内嵌区，按内嵌件画
    inset_boxes = []
    total = {}
    for box in boxes:
        area = (box[2] - box[0]) * (box[3] - box[1])
        count = 0
        for yy in range(box[1], box[3]):
            for xx in range(box[0], box[2]):
                if image.getpixel((xx, yy))[:3] == INSET_FILL and image.getpixel((xx, yy))[3] > 0:
                    count += 1
        if count > 0.5 * area:
            inset_boxes.append(box)
    for box in boxes:
        if box in inset_boxes:
            draw_inset(out, box)
        else:
            draw_panel(out, box)
    slots = find_slots(image)
    insets = find_insets(image)
    for box in insets:
        draw_inset(out, box)
    for box in slots:
        draw_slot(out, box)

    def covered(x, y):
        for bx in insets + slots + inset_boxes:
            if bx[0] <= x < bx[2] and bx[1] <= y < bx[3]:
                return True
        return False

    def in_frame_band(x, y):
        """面板边框带。旧图的边框是 1px 黑 + 2~3px 白（还带圆角斜切），
        这圈里的"框线颜色"一律不粘贴，否则方角会被旧像素重新盖成圆角。"""
        for bx in boxes:
            if bx[0] <= x < bx[2] and bx[1] <= y < bx[3]:
                if x < bx[0] + 4 or y < bx[1] + 4 or x >= bx[2] - 4 or y >= bx[3] - 4:
                    return True
        return False

    src = image.load()
    dst = out.load()
    for y in range(height):
        for x in range(width):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            rgb = (r, g, b)
            if covered(x, y) or rgb in (PANEL_FILL, INSET_FILL):
                continue
            if in_frame_band(x, y) and rgb in FRAME_COLORS:
                continue
            dst[x, y] = map_gray(rgb) + (a,)
    return out


def convert_sprite(image, rel):
    if Path(rel).name in ("slot.png", "large_slot.png"):
        out = Image.new("RGBA", image.size, (0, 0, 0, 0))
        draw_inset(out, (0, 0, image.size[0], image.size[1]))
        return out
    if image.size[0] >= 18 and image.size[1] >= 18 and find_slots(image):
        return convert_screen(image)
    out = image.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a != 0:
                px[x, y] = map_gray((r, g, b)) + (a,)
    return out


def main() -> int:
    wanted = set(sys.argv[1:])
    if not SRC.is_dir():
        print(f"找不到 RS2 贴图目录：{SRC}", file=sys.stderr)
        return 1
    screens = sprites = 0
    for png in sorted(SRC.rglob("*.png")):
        rel = png.relative_to(SRC)
        stem = rel.with_suffix("").as_posix()
        if wanted and stem not in wanted and rel.stem not in wanted:
            continue
        image = Image.open(png).convert("RGBA")
        result = convert_sprite(image, rel.as_posix()) if rel.parts[0] == "sprites" else convert_screen(image)
        target = DST / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        result.save(target)
        if rel.parts[0] == "sprites":
            sprites += 1
        else:
            screens += 1
    print(f"界面 {screens} 张、sprite {sprites} 张 -> {DST}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
