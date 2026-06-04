import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Adds a BRAND-NEW item definition to archive 19 by expanding its group (the
 * capability the OSRS import batch needs, since every import is a new id above
 * the current cache max). The new item's visual is copied from an existing item
 * and renamed (placeholder model) — real OSRS model conversion is a separate,
 * still-blocked task.
 *
 * Usage: AddItemToCache <cacheDir> <backupDir> <newItemId> <sourceVisualItemId> <newName>
 */
public class AddItemToCache {
    private static final int ARCHIVE = 19;

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
        Path backupDir = Path.of(args[1]);
        int newItemId = Integer.parseInt(args[2]);
        int sourceItemId = Integer.parseInt(args[3]);
        String newName = args[4];

        Files.createDirectories(backupDir);
        backup(cacheDir, backupDir, "main_file_cache.dat2");
        backup(cacheDir, backupDir, "main_file_cache.idx19");
        backup(cacheDir, backupDir, "main_file_cache.idx255");

        DiskProvider provider = new DiskProvider(cacheDir);
        Js5 js5 = new Js5(provider, false, false);
        Js5Index index = provider.fetchIndex();
        int group = newItemId >>> 8;
        int newFile = newItemId & 255;
        int groupSize = index.groupSizes[group];
        int[] fileIds = js5.getFileIds(group);
        for (int x : fileIds) if (x == newFile) throw new IllegalStateException("item " + newItemId + " already exists in cache");

        byte[][] files = new byte[groupSize + 1][];
        int[] newFileIds = new int[groupSize + 1];
        for (int k = 0; k < groupSize; k++) { files[k] = js5.fetchFile(group, fileIds[k]); newFileIds[k] = fileIds[k]; }
        byte[] source = js5.fetchFile(sourceItemId >>> 8, sourceItemId & 255);
        files[groupSize] = renameItem(source, newName);   // new file id is largest -> append keeps ascending order
        newFileIds[groupSize] = newFile;

        byte[] unpackedGroup = packSingleChunk(files);
        byte[] packedGroupWithoutVersion = wrapUncompressed(unpackedGroup);
        int newVersion = index.groupVersions[group] + 1;
        byte[] packedGroup = appendVersion(packedGroupWithoutVersion, newVersion);
        int newCrc = Buffer.crc32(packedGroupWithoutVersion, packedGroupWithoutVersion.length);

        byte[] rawIndex = Js5Compression.uncompress(provider.master.read(ARCHIVE));
        byte[] newRawIndex = reserialize(rawIndex, group, newFileIds, newVersion, newCrc);

        // Write group data, then the rebuilt index.
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "rw", Long.MAX_VALUE), 5200, 0);
        BufferedFile idx = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + ARCHIVE), "rw", Long.MAX_VALUE), 6000, 0);
        Cache archiveCache = new Cache(ARCHIVE, data, idx, 1000000);
        if (!archiveCache.write(group, packedGroup.length, packedGroup)) throw new IllegalStateException("write group failed");
        BufferedFile idx255 = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx255"), "rw", Long.MAX_VALUE), 6000, 0);
        Cache master = new Cache(255, data, idx255, 1000000);
        byte[] repacked = wrapUncompressed(newRawIndex);
        if (!master.write(ARCHIVE, repacked.length, repacked)) throw new IllegalStateException("write index failed");

        System.out.println("Added item " + newItemId + " (group " + group + ", file " + newFile + "), visual from " + sourceItemId + ", name '" + newName + "'. Group now has " + (groupSize + 1) + " files.");
    }

    // ---- JS5 archive-19 index codec (validated byte-identical by IndexRoundTrip) ----
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
        if (names) throw new IllegalStateException("index uses file names; not supported");
        int gc = u16(raw);
        int[] gid = new int[gc]; int acc = 0; int ti = -1;
        for (int i = 0; i < gc; i++) { acc += u16(raw); gid[i] = acc; if (acc == targetGroup) ti = i; }
        if (ti < 0) throw new IllegalStateException("group not in index");
        int[] crc = new int[gc]; for (int i = 0; i < gc; i++) crc[i] = u32(raw);
        int[] ver = new int[gc]; for (int i = 0; i < gc; i++) ver[i] = u32(raw);
        int[] fc = new int[gc]; for (int i = 0; i < gc; i++) fc[i] = u16(raw);
        int[][] fid = new int[gc][];
        for (int i = 0; i < gc; i++) { fid[i] = new int[fc[i]]; int x = 0; for (int j = 0; j < fc[i]; j++) { x += u16(raw); fid[i][j] = x; } }
        if (rp != raw.length) throw new IllegalStateException("index parse mismatch " + rp + "/" + raw.length);
        // apply modification to the target group
        crc[ti] = newCrc; ver[ti] = newVersion; fc[ti] = newFileIds.length; fid[ti] = newFileIds;
        // emit
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        w8(o, protocol); if (protocol >= 6) w32(o, version); w8(o, flags); w16(o, gc);
        int pr = 0; for (int i = 0; i < gc; i++) { w16(o, gid[i] - pr); pr = gid[i]; }
        for (int i = 0; i < gc; i++) w32(o, crc[i]);
        for (int i = 0; i < gc; i++) w32(o, ver[i]);
        for (int i = 0; i < gc; i++) w16(o, fc[i]);
        for (int i = 0; i < gc; i++) { int q = 0; for (int j = 0; j < fc[i]; j++) { w16(o, fid[i][j] - q); q = fid[i][j]; } }
        return o.toByteArray();
    }

    // ---- item-def opcode rename + group packing (ported from PatchItemCache) ----
    private static void backup(File cacheDir, Path backupDir, String name) throws Exception {
        Path t = backupDir.resolve(name);
        if (!Files.exists(t)) Files.copy(new File(cacheDir, name).toPath(), t);
    }
    private static byte[] renameItem(byte[] def, String name) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while (i < def.length) {
            int opcode = def[i++] & 0xFF; out.write(opcode);
            if (opcode == 0) break;
            if (opcode == 2) {
                out.write(name.getBytes("Cp1252")); out.write(0);
                while (i < def.length && def[i++] != 0) {}
                continue;
            }
            i = copyOpcodePayload(def, i, opcode, out);
        }
        return out.toByteArray();
    }
    private static int copyOpcodePayload(byte[] def, int i, int opcode, ByteArrayOutputStream out) {
        int len;
        if (opcode == 1 || opcode == 4 || opcode == 5 || opcode == 6 || opcode == 7 || opcode == 8 ||
                opcode == 11 || opcode == 12 || opcode == 16 || opcode == 23 || opcode == 24 ||
                opcode == 25 || opcode == 26 || opcode == 78 || opcode == 79 || opcode == 90 ||
                opcode == 91 || opcode == 92 || opcode == 93 || opcode == 95 || opcode == 96 ||
                opcode == 97 || opcode == 98 || opcode == 110 || opcode == 111 || opcode == 112 ||
                opcode == 115 || opcode == 121 || opcode == 122 || opcode == 125 || opcode == 126 ||
                opcode == 127 || opcode == 128 || opcode == 129 || opcode == 130 || opcode == 132 ||
                opcode == 65) {
            len = switch (opcode) {
                case 11, 16, 65 -> 0;
                case 12 -> 4;
                case 125, 126 -> 3;
                case 127, 128, 129, 130 -> 3;
                case 132 -> 1 + 2 * (def[i] & 0xFF);
                default -> 2;
            };
        } else if ((opcode >= 30 && opcode < 35) || (opcode >= 35 && opcode < 40)) {
            int start = i;
            while (def[i++] != 0) {}
            out.write(def, start, i - start);
            return i;
        } else if (opcode == 40 || opcode == 41) {
            int count = def[i] & 0xFF; len = 1 + count * 4;
        } else if (opcode == 42) {
            int count = def[i] & 0xFF; len = 1 + count;
        } else if (opcode >= 100 && opcode < 110) {
            len = 4;
        } else if (opcode == 249) {
            int count = def[i] & 0xFF; int p = i + 1;
            for (int n = 0; n < count; n++) { boolean s = def[p++] != 0; p += 3; if (s) { while (def[p++] != 0) {} } else { p += 4; } }
            len = p - i;
        } else {
            throw new IllegalArgumentException("Unsupported opcode " + opcode);
        }
        out.write(def, i, len);
        return i + len;
    }
    private static byte[] packSingleChunk(byte[][] files) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (byte[] f : files) data.write(f == null ? new byte[0] : f, 0, f == null ? 0 : f.length);
        int previous = 0;
        for (byte[] f : files) { int length = f == null ? 0 : f.length; writeInt(data, length - previous); previous = length; }
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
