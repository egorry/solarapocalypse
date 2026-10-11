"""Reads a Cubic Chunks save (region3d) and lists liquid standing directly on air. Usage: cubescan.py <save dir>"""
import gzip, os, struct, sys
from collections import Counter

def nbt(b, i, t):
    if t == 1: return b[i], i + 1
    if t == 2: return struct.unpack_from('>h', b, i)[0], i + 2
    if t == 3: return struct.unpack_from('>i', b, i)[0], i + 4
    if t == 4: return struct.unpack_from('>q', b, i)[0], i + 8
    if t == 5: return struct.unpack_from('>f', b, i)[0], i + 4
    if t == 6: return struct.unpack_from('>d', b, i)[0], i + 8
    if t == 7:
        n = struct.unpack_from('>i', b, i)[0]; return b[i + 4:i + 4 + n], i + 4 + n
    if t == 8:
        n = struct.unpack_from('>H', b, i)[0]; return b[i + 2:i + 2 + n].decode('utf-8', 'replace'), i + 2 + n
    if t == 9:
        et, n = b[i], struct.unpack_from('>i', b, i + 1)[0]; i += 5; out = []
        for _ in range(n):
            v, i = nbt(b, i, et); out.append(v)
        return out, i
    if t == 10:
        out = {}
        while True:
            et = b[i]; i += 1
            if et == 0: return out, i
            n = struct.unpack_from('>H', b, i)[0]; name = b[i + 2:i + 2 + n].decode('utf-8', 'replace'); i += 2 + n
            out[name], i = nbt(b, i, et)
    if t == 11:
        n = struct.unpack_from('>i', b, i)[0]; return list(struct.unpack_from('>%di' % n, b, i + 4)), i + 4 + 4 * n
    if t == 12:
        n = struct.unpack_from('>i', b, i)[0]; return list(struct.unpack_from('>%dq' % n, b, i + 4)), i + 4 + 8 * n
    raise ValueError(t)

def root(data):
    b = gzip.decompress(data)
    assert b[0] == 10
    n = struct.unpack_from('>H', b, 1)[0]
    return nbt(b, 3 + n, 10)[0]

def cubes(save):
    d = os.path.join(save, 'region3d')
    for f in os.listdir(d):
        if not f.endswith('.3dr'): continue
        raw = open(os.path.join(d, f), 'rb').read()
        for k in range(4096):
            v = struct.unpack_from('>I', raw, k * 4)[0]
            off, size = v >> 8, v & 0xFF
            if off == 0 or size == 0: continue
            p = off * 512
            if p + 4 > len(raw): continue
            n = struct.unpack_from('>i', raw, p)[0]
            try:
                yield root(raw[p + 4:p + 4 + n])
            except Exception as e:
                print('bad entry', f, k, e)

def main(save):
    blocks = {}  # (x, y, z) -> (id, meta) for non-air
    loaded = set()
    for c in cubes(save):
        lv = c['Level']
        cx, cy, cz = lv['x'], lv['y'], lv['z']
        loaded.add((cx, cy, cz))
        for s in lv.get('Sections', []):
            bl, da, add = s['Blocks'], s['Data'], s.get('Add')
            for idx in range(4096):
                bid = bl[idx]
                if add: bid |= ((add[idx >> 1] >> ((idx & 1) * 4)) & 15) << 8
                if bid == 0: continue
                meta = (da[idx >> 1] >> ((idx & 1) * 4)) & 15
                ly, lz, lx = idx >> 8, (idx >> 4) & 15, idx & 15
                blocks[(cx * 16 + lx, cy * 16 + ly, cz * 16 + lz)] = (bid, meta)
    print(len(loaded), 'cubes,', len(blocks), 'non-air blocks')
    def known(x, y, z): return (x >> 4, y >> 4, z >> 4) in loaded
    hits = Counter(); examples = {}
    for (x, y, z), (bid, meta) in blocks.items():
        if bid not in (8, 9, 10, 11): continue
        if (x, y - 1, z) in blocks or not known(x, y - 1, z): continue
        kind = ('water' if bid in (8, 9) else 'lava') + (' source' if meta == 0 else ' flowing')
        hits[kind] += 1
        examples.setdefault(kind, []).append((x, y, z))
    print('liquid on air:', dict(hits))
    for kind, ex in examples.items():
        ex.sort(key=lambda p: (p[0] // 8, p[2] // 8, -p[1]))
        print(kind, 'e.g.', ex[:12])
        for (x, y, z) in ex[:3]:
            col = []
            for yy in range(y + 6, y - 30, -1):
                b = blocks.get((x, yy, z), (0, 0)) if known(x, yy, z) else None
                col.append('%d:%s' % (yy, '?' if b is None else '%d/%d' % b))
            print('  column', x, z, ' '.join(col))
    # clusters by 16x16 column
    cl = Counter((x >> 4, z >> 4) for kind in examples for (x, y, z) in examples[kind])
    print('by column (cx, cz):', cl.most_common(15))

main(sys.argv[1])
