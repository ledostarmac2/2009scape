#!/usr/bin/env python3
"""Dependency-free OBJ -> PNG flat-shaded orthographic renderer.

Purpose: visually verify imported OSRS meshes (decode + orientation + color)
without launching the game client. Reads the .obj/.mtl pairs that
ImportOsrsItemModels exports.
"""
import sys, os, math, struct, zlib

def parse_mtl(path):
    cols = {}
    name = None
    if not os.path.exists(path):
        return cols
    for line in open(path):
        p = line.split()
        if not p:
            continue
        if p[0] == 'newmtl':
            name = p[1]
        elif p[0] == 'Kd' and name is not None:
            cols[name] = (float(p[1]), float(p[2]), float(p[3]))
    return cols

def parse_obj(path):
    verts = []
    faces = []   # (i,j,k, color)
    cols = {}
    cur = (0.7, 0.7, 0.7)
    for line in open(path):
        p = line.split()
        if not p:
            continue
        if p[0] == 'mtllib':
            cols = parse_mtl(os.path.join(os.path.dirname(path), p[1]))
        elif p[0] == 'usemtl':
            cur = cols.get(p[1], (0.7, 0.7, 0.7))
        elif p[0] == 'v':
            verts.append((float(p[1]), float(p[2]), float(p[3])))
        elif p[0] == 'f':
            idx = [int(t.split('/')[0]) - 1 for t in p[1:]]
            for n in range(1, len(idx) - 1):
                faces.append((idx[0], idx[n], idx[n + 1], cur))
    return verts, faces

def write_png(path, w, h, rgb):
    def chunk(typ, data):
        c = typ + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw.extend(rgb[y * w * 3:(y + 1) * w * 3])
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')
    open(path, 'wb').write(png)

def render(verts, faces, size, yaw, pitch):
    # rotate
    cy, sy = math.cos(yaw), math.sin(yaw)
    cx, sx = math.cos(pitch), math.sin(pitch)
    pts = []
    for (x, y, z) in verts:
        # yaw about Y
        x2 = x * cy + z * sy
        z2 = -x * sy + z * cy
        # pitch about X
        y2 = y * cx - z2 * sx
        z3 = y * sx + z2 * cx
        pts.append((x2, y2, z3))
    if not pts:
        return None
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    minx, maxx = min(xs), max(xs); miny, maxy = min(ys), max(ys)
    span = max(maxx - minx, maxy - miny, 1e-6)
    margin = 0.1 * size
    scale = (size - 2 * margin) / span
    cxp = (minx + maxx) / 2; cyp = (miny + maxy) / 2
    def proj(p):
        sxp = size / 2 + (p[0] - cxp) * scale
        syp = size / 2 - (p[1] - cyp) * scale  # flip Y for image
        return sxp, syp, p[2]
    img = bytearray([34, 34, 40] * size * size)   # dark bg
    zbuf = [1e18] * (size * size)
    light = (0.4, 0.7, 0.55)
    ln = math.sqrt(sum(c * c for c in light)); light = tuple(c / ln for c in light)
    cull = os.environ.get('CULL', 'none')   # none | cw | ccw
    for (a, b, c, col) in faces:
        pa, pb, pc = proj(pts[a]), proj(pts[b]), proj(pts[c])
        area = (pb[0]-pa[0])*(pc[1]-pa[1]) - (pb[1]-pa[1])*(pc[0]-pa[0])
        if cull == 'cw' and area <= 0:
            continue
        if cull == 'ccw' and area >= 0:
            continue
        # normal in view space
        ux, uy, uz = pts[b][0]-pts[a][0], pts[b][1]-pts[a][1], pts[b][2]-pts[a][2]
        vx, vy, vz = pts[c][0]-pts[a][0], pts[c][1]-pts[a][1], pts[c][2]-pts[a][2]
        nx, ny, nz = uy*vz-uz*vy, uz*vx-ux*vz, ux*vy-uy*vx
        nl = math.sqrt(nx*nx+ny*ny+nz*nz) or 1
        diff = abs((nx*light[0]+ny*light[1]+nz*light[2]) / nl)
        shade = 0.35 + 0.65 * diff
        r = min(255, int(col[0]*255*shade)); g = min(255, int(col[1]*255*shade)); bl = min(255, int(col[2]*255*shade))
        # bounding box raster
        minpx = max(0, int(min(pa[0], pb[0], pc[0]))); maxpx = min(size-1, int(max(pa[0], pb[0], pc[0])))
        minpy = max(0, int(min(pa[1], pb[1], pc[1]))); maxpy = min(size-1, int(max(pa[1], pb[1], pc[1])))
        d = (pb[1]-pc[1])*(pa[0]-pc[0]) + (pc[0]-pb[0])*(pa[1]-pc[1])
        if abs(d) < 1e-9:
            continue
        for py in range(minpy, maxpy+1):
            for px in range(minpx, maxpx+1):
                w0 = ((pb[1]-pc[1])*(px-pc[0]) + (pc[0]-pb[0])*(py-pc[1])) / d
                w1 = ((pc[1]-pa[1])*(px-pc[0]) + (pa[0]-pc[0])*(py-pc[1])) / d
                w2 = 1 - w0 - w1
                if w0 < 0 or w1 < 0 or w2 < 0:
                    continue
                z = w0*pa[2] + w1*pb[2] + w2*pc[2]
                off = py*size + px
                if z < zbuf[off]:
                    zbuf[off] = z
                    o3 = off*3
                    img[o3] = r; img[o3+1] = g; img[o3+2] = bl
    return img

def main():
    objdir = sys.argv[1]
    outdir = sys.argv[2]
    ids = sys.argv[3:]
    os.makedirs(outdir, exist_ok=True)
    size = 240
    for mid in ids:
        verts, faces = parse_obj(os.path.join(objdir, mid + '.obj'))
        views = [(math.radians(30), math.radians(15)), (math.radians(120), math.radians(15))]
        combo = bytearray()
        imgs = [render(verts, faces, size, y, p) for (y, p) in views]
        # stitch horizontally
        W = size * len(imgs)
        out = bytearray([34,34,40]*W*size)
        for vy in range(size):
            for k, im in enumerate(imgs):
                if im is None: continue
                out[(vy*W + k*size)*3:(vy*W + k*size + size)*3] = im[vy*size*3:(vy*size+size)*3]
        write_png(os.path.join(outdir, mid + '.png'), W, size, bytes(out))
        print(f"{mid}: verts={len(verts)} faces={len(faces)} -> {mid}.png")

if __name__ == '__main__':
    main()
