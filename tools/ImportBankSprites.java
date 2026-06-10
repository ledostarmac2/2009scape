import java.io.ByteArrayOutputStream;
import java.io.File;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.fs.Store;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;

/**
 * Imports authentic OSRS bank-deposit button icons into the rt4 (530) game cache
 * sprite archive (index 8) as new sprite groups, re-emitting the OSRS palette/index
 * data into the rt4 sprite format (canvas 35x35, icon centered to match the bank's
 * existing bottom-bar icons).
 *
 * Usage: ImportBankSprites <gameCacheDir> <osrsCacheDir> <osrsSpriteId1> <osrsSpriteId2> ...
 * Prints the new rt4 sprite group id assigned to each imported icon.
 */
public final class ImportBankSprites {
    private static final int ARCHIVE = 8;
    private static final int CANVAS = 35;

    public static void main(String[] args) throws Exception {
        File gameCache = new File(args[0]);
        File osrsCache = new File(args[1]);

        // ---- read OSRS sprites ----
        SpriteDefinition[] defs = new SpriteDefinition[args.length - 2];
        try (Store store = new Store(osrsCache)) {
            store.load();
            SpriteManager sm = new SpriteManager(store);
            sm.load();
            for (int i = 2; i < args.length; i++) {
                SpriteDefinition d = sm.findSprite(Integer.parseInt(args[i]), 0);
                if (d == null) throw new IllegalStateException("OSRS sprite " + args[i] + " not found");
                defs[i - 2] = d;
            }
        }

        // ---- read game archive 8 index ----
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(gameCache, "main_file_cache.dat2"), "rw", Long.MAX_VALUE), 5200, 0);
        BufferedFile idx = new BufferedFile(new FileOnDisk(new File(gameCache, "main_file_cache.idx" + ARCHIVE), "rw", Long.MAX_VALUE), 6000, 0);
        BufferedFile idx255 = new BufferedFile(new FileOnDisk(new File(gameCache, "main_file_cache.idx255"), "rw", Long.MAX_VALUE), 6000, 0);
        Cache archiveCache = new Cache(ARCHIVE, data, idx, 1000000);
        Cache master = new Cache(255, data, idx255, 1000000);

        byte[] rawIndex = Js5Compression.uncompress(master.read(ARCHIVE));

        for (SpriteDefinition d : defs) {
            byte[] spriteBytes = encodeRt4Sprite(d);
            int newId = maxGroup(rawIndex) + 1;
            // Single-file group: the group payload IS the file (no chunk trailer).
            byte[] packedNoVer = wrapUncompressed(spriteBytes);
            int version = 1;
            byte[] packed = appendVersion(packedNoVer, version);
            int crc = Buffer.crc32(packedNoVer, packedNoVer.length);
            if (!archiveCache.write(newId, packed.length, packed)) throw new IllegalStateException("write sprite group failed");
            rawIndex = addGroup(rawIndex, newId, new int[]{0}, version, crc);
            System.out.println("OSRS sprite " + d.getId() + " (" + d.getWidth() + "x" + d.getHeight() + ") -> rt4 sprite group " + newId);
        }

        byte[] repacked = wrapUncompressed(rawIndex);
        if (!master.write(ARCHIVE, repacked.length, repacked)) throw new IllegalStateException("write index failed");
        System.out.println("Done. Archive 8 index updated.");
    }

    /** Encode a single-frame rt4 sprite (canvas 35x35, icon centered), re-using the OSRS palette/indices. */
    private static byte[] encodeRt4Sprite(SpriteDefinition d) {
        int w = d.getWidth(), h = d.getHeight();
        byte[] pix = d.pixelIdx;          // inner w*h palette indices, 0 = transparent
        int[] palette = d.palette;        // palette[0] unused
        int palLen = palette.length;      // includes index 0
        int offX = (CANVAS - w) / 2;
        int offY = (CANVAS - h) / 2;

        ByteArrayOutputStream o = new ByteArrayOutputStream();
        // 1) pixel section: flags=0 (horizontal, no separate alpha), then w*h index bytes
        o.write(0);
        for (int i = 0; i < w * h; i++) o.write(pix[i] & 0xFF);
        // 2) palette section: palette[1..palLen-1] as 3-byte RGB
        for (int i = 1; i < palLen; i++) {
            int c = palette[i];
            o.write((c >> 16) & 0xFF); o.write((c >> 8) & 0xFF); o.write(c & 0xFF);
        }
        // 3) canvas width/height, paletteCount-1
        w2(o, CANVAS); w2(o, CANVAS); o.write((palLen - 1) & 0xFF);
        // 4) per-sprite offX, offY, innerW, innerH (count=1)
        w2(o, offX); w2(o, offY); w2(o, w); w2(o, h);
        // 5) sprite count
        w2(o, 1);
        return o.toByteArray();
    }

    private static void w2(ByteArrayOutputStream o, int v) { o.write((v >>> 8) & 0xFF); o.write(v & 0xFF); }

    // ---- JS5 index codec (mirrors AddItemToCache, with append-group support) ----
    private static int rp;
    private static int u8(byte[] b) { return b[rp++] & 0xFF; }
    private static int u16(byte[] b) { return (u8(b) << 8) | u8(b); }
    private static int u32(byte[] b) { return (u8(b) << 24) | (u8(b) << 16) | (u8(b) << 8) | u8(b); }
    private static void p8(ByteArrayOutputStream o, int v) { o.write(v & 0xFF); }
    private static void p16(ByteArrayOutputStream o, int v) { o.write((v >>> 8) & 0xFF); o.write(v & 0xFF); }
    private static void p32(ByteArrayOutputStream o, int v) { o.write((v >>> 24) & 0xFF); o.write((v >>> 16) & 0xFF); o.write((v >>> 8) & 0xFF); o.write(v & 0xFF); }

    private static int maxGroup(byte[] raw) {
        rp = 0;
        int protocol = u8(raw);
        if (protocol >= 6) u32(raw);
        int flags = u8(raw);
        int gc = u16(raw);
        int acc = 0, max = -1;
        for (int i = 0; i < gc; i++) { acc += u16(raw); if (acc > max) max = acc; }
        return max;
    }

    private static int nameHash(String s) { int h = 0; for (int i = 0; i < s.length(); i++) h = h * 31 + s.charAt(i); return h; }

    private static byte[] addGroup(byte[] raw, int newGroup, int[] fileIds, int version, int crc) {
        rp = 0;
        int protocol = u8(raw);
        if (protocol < 5 || protocol > 6) throw new IllegalStateException("unexpected index protocol " + protocol);
        int version0 = (protocol >= 6) ? u32(raw) : 0;
        int flags = u8(raw);
        boolean names = (flags & 1) != 0;
        if ((flags & ~1) != 0) throw new IllegalStateException("index has unsupported flags " + flags);
        int gc = u16(raw);
        int[] gid = new int[gc]; int acc = 0;
        for (int i = 0; i < gc; i++) { acc += u16(raw); gid[i] = acc; }
        int[] gname = new int[gc]; if (names) for (int i = 0; i < gc; i++) gname[i] = u32(raw);
        int[] crcs = new int[gc]; for (int i = 0; i < gc; i++) crcs[i] = u32(raw);
        int[] vers = new int[gc]; for (int i = 0; i < gc; i++) vers[i] = u32(raw);
        int[] fc = new int[gc]; for (int i = 0; i < gc; i++) fc[i] = u16(raw);
        int[][] fid = new int[gc][];
        for (int i = 0; i < gc; i++) { fid[i] = new int[fc[i]]; int x = 0; for (int j = 0; j < fc[i]; j++) { x += u16(raw); fid[i][j] = x; } }
        int[][] fname = new int[gc][];
        if (names) for (int i = 0; i < gc; i++) { fname[i] = new int[fc[i]]; for (int j = 0; j < fc[i]; j++) fname[i][j] = u32(raw); }
        if (rp != raw.length) throw new IllegalStateException("index parse mismatch " + rp + "/" + raw.length);
        for (int i = 0; i < gc; i++) if (gid[i] == newGroup) throw new IllegalStateException("group " + newGroup + " already exists");

        // insert in ascending order
        int pos = gc; for (int i = 0; i < gc; i++) if (gid[i] > newGroup) { pos = i; break; }
        int ngc = gc + 1;
        int[] ngid = ins(gid, pos, newGroup);
        int[] ngname = ins(gname, pos, nameHash("custom_bank_btn_" + newGroup));
        int[] ncrc = ins(crcs, pos, crc);
        int[] nver = ins(vers, pos, version);
        int[] nfc = ins(fc, pos, fileIds.length);
        int[][] nfid = new int[ngc][];
        int[][] nfname = new int[ngc][];
        for (int i = 0, k = 0; i < ngc; i++) {
            if (i == pos) { nfid[i] = fileIds; nfname[i] = new int[fileIds.length]; /* zero file-name hashes */ }
            else { nfid[i] = fid[k]; nfname[i] = fname[k]; k++; }
        }

        ByteArrayOutputStream o = new ByteArrayOutputStream();
        p8(o, protocol); if (protocol >= 6) p32(o, version0); p8(o, flags); p16(o, ngc);
        int pr = 0; for (int i = 0; i < ngc; i++) { p16(o, ngid[i] - pr); pr = ngid[i]; }
        if (names) for (int i = 0; i < ngc; i++) p32(o, ngname[i]);
        for (int i = 0; i < ngc; i++) p32(o, ncrc[i]);
        for (int i = 0; i < ngc; i++) p32(o, nver[i]);
        for (int i = 0; i < ngc; i++) p16(o, nfc[i]);
        for (int i = 0; i < ngc; i++) { int q = 0; for (int j = 0; j < nfid[i].length; j++) { p16(o, nfid[i][j] - q); q = nfid[i][j]; } }
        if (names) for (int i = 0; i < ngc; i++) for (int j = 0; j < nfname[i].length; j++) p32(o, nfname[i][j]);
        return o.toByteArray();
    }

    private static int[] ins(int[] a, int pos, int v) {
        int[] r = new int[a.length + 1];
        System.arraycopy(a, 0, r, 0, pos);
        r[pos] = v;
        System.arraycopy(a, pos, r, pos + 1, a.length - pos);
        return r;
    }

    // ---- group packing (from AddItemToCache) ----
    private static byte[] packSingleChunk(byte[][] files) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (byte[] f : files) data.write(f, 0, f.length);
        int previous = 0;
        for (byte[] f : files) { int length = f.length; writeInt(data, length - previous); previous = length; }
        data.write(1);
        return data.toByteArray();
    }
    private static byte[] wrapUncompressed(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0); writeInt(out, data.length); out.write(data, 0, data.length);
        return out.toByteArray();
    }
    private static byte[] appendVersion(byte[] data, int version) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(data, 0, data.length); out.write((version >>> 8) & 0xFF); out.write(version & 0xFF);
        return out.toByteArray();
    }
    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF); out.write((value >>> 16) & 0xFF); out.write((value >>> 8) & 0xFF); out.write(value & 0xFF);
    }
}
