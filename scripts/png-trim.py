"""纯 Python PNG 自动裁白边（无需第三方库）。
用法: python trim_png.py <in.png> <out.png> [阈值]
"""
import sys, zlib, struct


def read_png(path):
    f = open(path, 'rb').read()
    assert f[:8] == b'\x89PNG\r\n\x1a\n', 'not png'
    i = 8
    w = h = bd = ct = None
    idat = b''
    while i < len(f):
        ln = struct.unpack('>I', f[i:i + 4])[0]
        typ = f[i + 4:i + 8]
        data = f[i + 8:i + 8 + ln]
        i += 12 + ln
        if typ == b'IHDR':
            w, h, bd, ct = struct.unpack('>IIBB', data[:10])
        elif typ == b'IDAT':
            idat += data
        elif typ == b'IEND':
            break
    assert bd == 8, 'only 8-bit supported'
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    raw = zlib.decompress(idat)
    stride = w * ch
    rows = []
    prev = bytearray(stride)
    pos = 0
    for _ in range(h):
        ft = raw[pos]; pos += 1
        line = bytearray(raw[pos:pos + stride]); pos += stride
        if ft == 1:
            for x in range(ch, stride):
                line[x] = (line[x] + line[x - ch]) & 255
        elif ft == 2:
            for x in range(stride):
                line[x] = (line[x] + prev[x]) & 255
        elif ft == 3:
            for x in range(stride):
                a = line[x - ch] if x >= ch else 0
                line[x] = (line[x] + ((a + prev[x]) >> 1)) & 255
        elif ft == 4:
            for x in range(stride):
                a = line[x - ch] if x >= ch else 0
                b = prev[x]
                c = prev[x - ch] if x >= ch else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        rows.append(bytes(line))
        prev = line
    return w, h, ch, ct, rows


def is_blank_row(row, ch, thr):
    for x in range(0, len(row) - ch + 1, ch):
        r, g, b = row[x], row[x + 1], row[x + 2]
        if r < thr or g < thr or b < thr:
            return False
    return True


def write_png(path, w, h, rows):
    raw = bytearray()
    for r in rows:
        raw.append(0)
        raw += r
    comp = zlib.compress(bytes(raw), 9)

    def chunk(typ, data):
        c = struct.pack('>I', len(data)) + typ + data
        return c + struct.pack('>I', zlib.crc32(typ + data) & 0xffffffff)

    out = b'\x89PNG\r\n\x1a\n'
    out += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
    out += chunk(b'IDAT', comp)
    out += chunk(b'IEND', b'')
    open(path, 'wb').write(out)


def main():
    src, dst = sys.argv[1], sys.argv[2]
    thr = int(sys.argv[3]) if len(sys.argv) > 3 else 248
    w, h, ch, ct, rows = read_png(src)

    # 统一转 RGB 行
    rgb = []
    for row in rows:
        if ct == 6:
            rgb.append(bytes(b for x in range(0, len(row), 4) for b in row[x:x + 3]))
        elif ct == 2:
            rgb.append(row)
        else:
            raise SystemExit('unsupported color type ' + str(ct))

    top = 0
    while top < h and is_blank_row(rgb[top], 3, thr):
        top += 1
    bot = h - 1
    while bot > top and is_blank_row(rgb[bot], 3, thr):
        bot -= 1
    left = 0
    while left < w and all(rgb[y][left * 3] >= thr and rgb[y][left * 3 + 1] >= thr
                           and rgb[y][left * 3 + 2] >= thr for y in range(top, bot + 1)):
        left += 1
    right = w - 1
    while right > left and all(rgb[y][right * 3] >= thr and rgb[y][right * 3 + 1] >= thr
                               and rgb[y][right * 3 + 2] >= thr for y in range(top, bot + 1)):
        right -= 1

    # 补一圈白边
    pad = 16
    top = max(0, top - pad); bot = min(h - 1, bot + pad)
    left = max(0, left - pad); right = min(w - 1, right + pad)
    cropped = [rgb[y][left * 3:(right + 1) * 3] for y in range(top, bot + 1)]
    nw = right - left + 1
    nh = len(cropped)
    write_png(dst, nw, nh, cropped)
    print(f'{src}: {w}x{h} -> {dst}: {nw}x{nh} (top={top} bottom={bot})')


if __name__ == '__main__':
    main()
