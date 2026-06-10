import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Compression;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;

/**
 * Adds "Deposit inventory" and "Deposit worn items" buttons (+ their icon overlays)
 * to the bank interface (archive 3, group 762) by cloning the existing
 * Deposit-beast-of-burden button (file 18) and its icon (file 19), then patching
 * baseX, the option/tooltip text, and (for icons) the sprite id.
 *
 * New child component ids:
 *   103 = deposit-inventory button   104 = deposit-inventory icon  (sprite 1709)
 *   105 = deposit-equipment button   106 = deposit-equipment icon  (sprite 1710)
 *
 * Usage: AddBankButtons <gameCacheDir> <invSpriteId> <equipSpriteId>
 */
public final class AddBankButtons {
    private static final int ARCHIVE = 3;
    private static final int GROUP = 762;
    private static final int BTN_TEMPLATE = 18;   // BoB deposit button
    private static final int ICON_TEMPLATE = 19;  // BoB icon
    private static final int X_INV = 332;
    private static final int X_EQUIP = 369;

    private static final class DiskProvider extends Js5ResourceProvider {
        final Cache master, archive;
        DiskProvider(File dir) throws Exception {
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            BufferedFile i255 = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0);
            BufferedFile i = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + ARCHIVE), "r", Long.MAX_VALUE), 6000, 0);
            master = new Cache(255, data, i255, 1000000);
            archive = new Cache(ARCHIVE, data, i, 1000000);
        }
        public Js5Index fetchIndex() { byte[] x = master.read(ARCHIVE); return x == null ? null : new Js5Index(x, Buffer.crc32(x, x.length)); }
        public void prefetchGroup(int g) {}
        public int getPercentageComplete(int g) { return 100; }
        public byte[] fetchGroup(int g) { return archive.read(g); }
    }

    public static void main(String[] args) throws Exception {
        File cacheDir = new File(args[0]);
        int invSprite = Integer.parseInt(args[1]);
        int equipSprite = Integer.parseInt(args[2]);

        DiskProvider provider = new DiskProvider(cacheDir);
        Js5 js5 = new Js5(provider, false, false);
        Js5Index index = provider.fetchIndex();
        int[] fileIds = js5.getFileIds(GROUP);
        int groupSize = fileIds.length;
        for (int x : fileIds) if (x >= 103) throw new IllegalStateException("group already has file " + x + " (already patched?)");

        byte[] btn = js5.fetchFile(GROUP, BTN_TEMPLATE);
        byte[] icon = js5.fetchFile(GROUP, ICON_TEMPLATE);

        byte[] f103 = patchButton(btn, X_INV, "Deposit inventory", "Deposit your entire inventory into your bank");
        byte[] f104 = patchIcon(icon, X_INV, invSprite);
        byte[] f105 = patchButton(btn, X_EQUIP, "Deposit worn items", "Deposit your worn equipment into your bank");
        byte[] f106 = patchIcon(icon, X_EQUIP, equipSprite);

        // Assemble ascending file list: existing + 103..106
        int n = groupSize + 4;
        byte[][] files = new byte[n][];
        int[] newFileIds = new int[n];
        for (int k = 0; k < groupSize; k++) { files[k] = js5.fetchFile(GROUP, fileIds[k]); newFileIds[k] = fileIds[k]; }
        files[groupSize] = f103;     newFileIds[groupSize] = 103;
        files[groupSize + 1] = f104; newFileIds[groupSize + 1] = 104;
        files[groupSize + 2] = f105; newFileIds[groupSize + 2] = 105;
        files[groupSize + 3] = f106; newFileIds[groupSize + 3] = 106;

        byte[] unpackedGroup = packMultiChunk(files);
        byte[] packedNoVer = wrapUncompressed(unpackedGroup);
        int newVersion = index.groupVersions[GROUP] + 1;
        byte[] packed = appendVersion(packedNoVer, newVersion);
        int newCrc = Buffer.crc32(packedNoVer, packedNoVer.length);

        byte[] rawIndex = Js5Compression.uncompress(provider.master.read(ARCHIVE));
        byte[] newRawIndex = reserialize(rawIndex, GROUP, newFileIds, newVersion, newCrc);

        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "rw", Long.MAX_VALUE), 5200, 0);
        BufferedFile idx = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + ARCHIVE), "rw", Long.MAX_VALUE), 6000, 0);
        Cache archiveCache = new Cache(ARCHIVE, data, idx, 1000000);
        if (!archiveCache.write(GROUP, packed.length, packed)) throw new IllegalStateException("write group failed");
        BufferedFile idx255 = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx255"), "rw", Long.MAX_VALUE), 6000, 0);
        Cache master = new Cache(255, data, idx255, 1000000);
        byte[] repacked = wrapUncompressed(newRawIndex);
        if (!master.write(ARCHIVE, repacked.length, repacked)) throw new IllegalStateException("write index failed");

        System.out.println("Added bank buttons 103-106 to group 762. Files: " + groupSize + " -> " + n
                + " (inv sprite " + invSprite + ", equip sprite " + equipSprite + ").");
    }

    private static byte[] patchButton(byte[] src, int baseX, String option, String tooltip) {
        byte[] b = Arrays.copyOf(src, src.length);
        b[4] = (byte) ((baseX >> 8) & 0xFF); b[5] = (byte) (baseX & 0xFF);
        b = replaceCString(b, "Deposit beast of burden inventory", option);
        b = replaceCString(b, "Empty your beast of burden's inventory into your bank", tooltip);
        return b;
    }

    private static byte[] patchIcon(byte[] src, int baseX, int spriteId) {
        byte[] b = Arrays.copyOf(src, src.length);
        b[4] = (byte) ((baseX >> 8) & 0xFF); b[5] = (byte) (baseX & 0xFF);
        // spriteId is a g4 at offset 0x13..0x16
        b[0x13] = (byte) ((spriteId >>> 24) & 0xFF);
        b[0x14] = (byte) ((spriteId >>> 16) & 0xFF);
        b[0x15] = (byte) ((spriteId >>> 8) & 0xFF);
        b[0x16] = (byte) (spriteId & 0xFF);
        return b;
    }

    /** Replace one occurrence of a NUL-terminated ASCII string; safe because if3 decodes sequentially. */
    private static byte[] replaceCString(byte[] data, String oldStr, String newStr) {
        byte[] needle = oldStr.getBytes(StandardCharsets.US_ASCII);
        int at = indexOf(data, needle);
        if (at < 0) throw new IllegalStateException("string not found: '" + oldStr + "'");
        if (at + needle.length >= data.length || data[at + needle.length] != 0)
            throw new IllegalStateException("string not NUL-terminated: '" + oldStr + "'");
        byte[] rep = newStr.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(data, 0, at);
        o.write(rep, 0, rep.length);
        o.write(data, at + needle.length, data.length - (at + needle.length)); // includes the NUL and everything after
        return o.toByteArray();
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    // ---- index codec + group packing (from AddItemToCache) ----
    private static int rp;
    private static int u8(byte[] b) { return b[rp++] & 0xFF; }
    private static int u16(byte[] b) { return (u8(b) << 8) | u8(b); }
    private static int u32(byte[] b) { return (u8(b) << 24) | (u8(b) << 16) | (u8(b) << 8) | u8(b); }
    private static void w8(ByteArrayOutputStream o, int v) { o.write(v & 0xFF); }
    private static void w16(ByteArrayOutputStream o, int v) { o.write((v >>> 8) & 0xFF); o.write(v & 0xFF); }
    private static void w32(ByteArrayOutputStream o, int v) { o.write((v >>> 24) & 0xFF); o.write((v >>> 16) & 0xFF); o.write((v >>> 8) & 0xFF); o.write(v & 0xFF); }

    private static byte[] reserialize(byte[] raw, int targetGroup, int[] newFileIds, int newVersion, int newCrc) {
        rp = 0;
        int protocol = u8(raw);
        int version = (protocol >= 6) ? u32(raw) : 0;
        int flags = u8(raw);
        boolean names = (flags & 1) != 0;
        int gc = u16(raw);
        int[] gid = new int[gc]; int acc = 0; int ti = -1;
        for (int i = 0; i < gc; i++) { acc += u16(raw); gid[i] = acc; if (acc == targetGroup) ti = i; }
        if (ti < 0) throw new IllegalStateException("group not in index");
        int[] gname = new int[gc]; if (names) for (int i = 0; i < gc; i++) gname[i] = u32(raw);
        int[] crc = new int[gc]; for (int i = 0; i < gc; i++) crc[i] = u32(raw);
        int[] ver = new int[gc]; for (int i = 0; i < gc; i++) ver[i] = u32(raw);
        int[] fc = new int[gc]; for (int i = 0; i < gc; i++) fc[i] = u16(raw);
        int[][] fid = new int[gc][];
        for (int i = 0; i < gc; i++) { fid[i] = new int[fc[i]]; int x = 0; for (int j = 0; j < fc[i]; j++) { x += u16(raw); fid[i][j] = x; } }
        int[][] fname = new int[gc][];
        if (names) for (int i = 0; i < gc; i++) { fname[i] = new int[fc[i]]; for (int j = 0; j < fc[i]; j++) fname[i][j] = u32(raw); }
        if (rp != raw.length) throw new IllegalStateException("index parse mismatch " + rp + "/" + raw.length);

        crc[ti] = newCrc; ver[ti] = newVersion;
        int oldFc = fc[ti];
        fc[ti] = newFileIds.length; fid[ti] = newFileIds;
        if (names) { int[] nf = new int[newFileIds.length]; System.arraycopy(fname[ti], 0, nf, 0, Math.min(oldFc, nf.length)); fname[ti] = nf; }

        ByteArrayOutputStream o = new ByteArrayOutputStream();
        w8(o, protocol); if (protocol >= 6) w32(o, version); w8(o, flags); w16(o, gc);
        int pr = 0; for (int i = 0; i < gc; i++) { w16(o, gid[i] - pr); pr = gid[i]; }
        if (names) for (int i = 0; i < gc; i++) w32(o, gname[i]);
        for (int i = 0; i < gc; i++) w32(o, crc[i]);
        for (int i = 0; i < gc; i++) w32(o, ver[i]);
        for (int i = 0; i < gc; i++) w16(o, fc[i]);
        for (int i = 0; i < gc; i++) { int q = 0; for (int j = 0; j < fc[i]; j++) { w16(o, fid[i][j] - q); q = fid[i][j]; } }
        if (names) for (int i = 0; i < gc; i++) for (int j = 0; j < fc[i]; j++) w32(o, fname[i][j]);
        return o.toByteArray();
    }

    private static byte[] packMultiChunk(byte[][] files) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (byte[] f : files) data.write(f, 0, f.length);
        int previous = 0;
        for (byte[] f : files) { int length = f.length; writeInt(data, length - previous); previous = length; }
        data.write(1); // one stripe
        return data.toByteArray();
    }
    private static byte[] wrapUncompressed(byte[] d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0); writeInt(out, d.length); out.write(d, 0, d.length);
        return out.toByteArray();
    }
    private static byte[] appendVersion(byte[] d, int version) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(d, 0, d.length); out.write((version >>> 8) & 0xFF); out.write(version & 0xFF);
        return out.toByteArray();
    }
    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF); out.write((v >>> 16) & 0xFF); out.write((v >>> 8) & 0xFF); out.write(v & 0xFF);
    }
}
