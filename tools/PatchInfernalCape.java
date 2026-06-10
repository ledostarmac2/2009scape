import java.io.File;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.Js5Index;

/**
 * Single infernal-cape lava patch: procedural TextureOp slot 59 + animated config.
 *
 * OSRS infernal models reference texture 59. The 2009 client renders archive-9
 * TextureOp pipelines only; OSRS rev233 texture blobs and archive-8 sprite sheets
 * (fire-cape pattern with sprite 768) caused client hangs when worn in-game even
 * though isolated {@code TestTextureProvider} calls succeeded.
 *
 * Stable path: 44-byte native procedural TextureOp (no archive-8 sprite dependency),
 * fire-cape animation driver (speed=0 dir=255), infernal avgColor 3875.
 */
public final class PatchInfernalCape {
    private static final int TEXTURE_ARCHIVE = 9;
    private static final int CONFIG_ARCHIVE = 26;
    private static final int TARGET_TEXTURE_ID = 59;
    private static final int REF_TEXTURE_ID = 40;
    private static final int INFERNAL_AVG_COLOR = 3875;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: PatchInfernalCape <gameCacheDir>");
            return;
        }
        File cacheDir = new File(args[0]);

        byte[] textureBytes = infernalTextureOp();
        patchTextureGroup(cacheDir, TARGET_TEXTURE_ID, textureBytes, TEXTURE_ARCHIVE);
        System.out.println("installed infernal TextureOp slot " + TARGET_TEXTURE_ID
                + " (" + textureBytes.length + " bytes, procedural lava)");

        patchTextureConfig(cacheDir);
        System.out.println("infernal lava texture " + TARGET_TEXTURE_ID
                + " ready (procedural, animated config like fire cape " + REF_TEXTURE_ID + ")");
    }

    /**
     * Native 2009 procedural TextureOp for infernal lava (archive-9 group 59).
     * No archive-8 sprite references; avoids client hangs from malformed sprite 768.
     */
    static byte[] infernalTextureOp() {
        return new byte[] {
                0x03, 0x00, 0x00, 0x01, 0x00, 0x01, 0x03, 0x01, 0x00, 0x02, 0x08, 0x01, 0x01, 0x00, 0x00, 0x04,
                0x00, 0x00, 0x0f, (byte) 0xff, 0x05, (byte) 0xfa, 0x0f, (byte) 0xff, 0x0a, (byte) 0xd4, 0x0c,
                (byte) 0xed, 0x10, 0x00, 0x06, (byte) 0xb5, 0x01, 0x02, 0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0x00,
                0x00, 0x22, 0x00
        };
    }

    static byte[] encodeBundle(ParseTextureConfigBundle.Info[] infos) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(infos.length * 16 + 64);
        writeShort(out, infos.length);
        for (ParseTextureConfigBundle.Info info : infos) out.write(info.enabled ? 1 : 0);
        for (ParseTextureConfigBundle.Info info : infos) if (info.enabled) out.write(info.animated ? 1 : 0);
        for (ParseTextureConfigBundle.Info info : infos) if (info.enabled) out.write(info.opaque ? 1 : 0);
        for (ParseTextureConfigBundle.Info info : infos) if (info.enabled) out.write(info.lowDetail ? 1 : 0);
        for (ParseTextureConfigBundle.Info info : infos) if (info.enabled) out.write(info.flag93 ? 1 : 0);
        for (ParseTextureConfigBundle.Info info : infos) if (info.enabled) out.write((byte) info.speed);
        for (ParseTextureConfigBundle.Info info : infos) if (info.enabled) out.write((byte) info.direction);
        for (ParseTextureConfigBundle.Info info : infos) if (info.enabled) out.write((byte) info.materialType);
        for (ParseTextureConfigBundle.Info info : infos) if (info.enabled) out.write((byte) info.byte61);
        for (ParseTextureConfigBundle.Info info : infos) if (info.enabled) writeShort(out, info.averageColor);
        return out.toByteArray();
    }

    private static void patchTextureConfig(File cacheDir) throws Exception {
        BufferedFile data = openData(cacheDir, "rw");
        Cache master = openArchive(cacheDir, data, 255, "rw");

        byte[] packedIndex = master.read(CONFIG_ARCHIVE);
        int archiveIndexVersion = readTrailingVersion(packedIndex);
        byte[] indexBytes = Js5Compression.uncompress(packedIndex);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));

        byte[] configPacked = openArchive(cacheDir, data, CONFIG_ARCHIVE, "r").read(0);
        byte[] configRaw = Js5Compression.uncompress(stripVersion(configPacked));
        ParseTextureConfigBundle.Info[] infos = ParseTextureConfigBundle.parse(configRaw);
        if (TARGET_TEXTURE_ID >= infos.length) {
            throw new IllegalStateException("texture config slot " + TARGET_TEXTURE_ID + " missing (count="
                    + infos.length + ")");
        }

        ParseTextureConfigBundle.Info ref = infos[REF_TEXTURE_ID];
        ParseTextureConfigBundle.Info target = infos[TARGET_TEXTURE_ID];
        System.out.println("before id" + TARGET_TEXTURE_ID + ": animated=" + target.animated
                + " speed=" + target.speed + " dir=" + target.direction + " avgColor=" + target.averageColor);
        System.out.println("ref id" + REF_TEXTURE_ID + ": animated=" + ref.animated
                + " speed=" + ref.speed + " dir=" + ref.direction + " avgColor=" + ref.averageColor);

        target.enabled = true;
        target.animated = true;
        target.opaque = ref.opaque;
        target.lowDetail = ref.lowDetail;
        target.flag93 = ref.flag93;
        target.speed = ref.speed;
        target.direction = ref.direction;
        target.materialType = ref.materialType;
        target.byte61 = ref.byte61;
        target.averageColor = INFERNAL_AVG_COLOR;

        byte[] patched = encodeBundle(infos);
        patchSingleFileGroup(cacheDir, data, master, CONFIG_ARCHIVE, 0, patched, indexBytes, index, archiveIndexVersion, 0);
        System.out.println("after id" + TARGET_TEXTURE_ID + ": animated=true speed=" + target.speed
                + " dir=" + target.direction + " avgColor=" + target.averageColor);
    }

    private static void patchTextureGroup(File cacheDir, int groupId, byte[] raw, int archiveId) throws Exception {
        BufferedFile data = openData(cacheDir, "rw");
        Cache master = openArchive(cacheDir, data, 255, "rw");
        Cache archive = openArchive(cacheDir, data, archiveId, "rw");

        byte[] packedIndex = master.read(archiveId);
        int archiveIndexVersion = readTrailingVersion(packedIndex);
        byte[] indexBytes = Js5Compression.uncompress(packedIndex);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));

        int version = 1;
        for (int g = 0; g < index.size; g++) {
            if (index.groupIds[g] == groupId) {
                version = index.groupVersions[g];
                break;
            }
        }
        patchSingleFileGroup(cacheDir, data, master, archiveId, groupId, raw, indexBytes, index, archiveIndexVersion, version);
    }

    private static BufferedFile openData(File cacheDir, String mode) throws Exception {
        return new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), mode, Long.MAX_VALUE), 5200, 0);
    }

    private static Cache openArchive(File cacheDir, BufferedFile data, int archiveId, String mode) throws Exception {
        return new Cache(archiveId, data,
                new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + archiveId), mode, Long.MAX_VALUE), 6000, 0),
                1000000);
    }

    private static void patchSingleFileGroup(File cacheDir, BufferedFile data, Cache master, int archiveId, int group,
                                             byte[] file, byte[] indexBytes, Js5Index index, int archiveIndexVersion,
                                             int version) throws Exception {
        byte[] packedNoVersion = wrapUncompressed(file);
        int slot = -1;
        for (int g = 0; g < index.size; g++) {
            if (index.groupIds[g] == group) {
                slot = g;
                version = index.groupVersions[g];
                break;
            }
        }
        byte[] packed = appendVersion(packedNoVersion, version);
        Cache archive = openArchive(cacheDir, data, archiveId, "rw");
        if (!archive.write(group, packed.length, packed)) {
            throw new IllegalStateException("Unable to write archive " + archiveId + " group " + group);
        }
        if (slot >= 0) {
            patchGroupChecksum(indexBytes, slot, Buffer.crc32(packedNoVersion, packedNoVersion.length));
            byte[] repackedIndex = appendVersion(wrapGzip(indexBytes), archiveIndexVersion);
            if (!master.write(archiveId, repackedIndex.length, repackedIndex)) {
                throw new IllegalStateException("Unable to write archive index " + archiveId);
            }
        }
    }

    private static int readTrailingVersion(byte[] packed) {
        return packed.length >= 2 ? ((packed[packed.length - 2] & 0xFF) << 8) | (packed[packed.length - 1] & 0xFF) : 0;
    }

    private static byte[] stripVersion(byte[] packed) {
        byte[] out = new byte[packed.length - 2];
        System.arraycopy(packed, 0, out, 0, out.length);
        return out;
    }

    private static byte[] wrapUncompressed(byte[] data) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(0);
        writeInt(out, data.length);
        out.write(data);
        return out.toByteArray();
    }

    private static byte[] wrapGzip(byte[] data) throws Exception {
        java.io.ByteArrayOutputStream compressed = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(compressed)) {
            gzip.write(data);
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(2);
        writeInt(out, compressed.size());
        writeInt(out, data.length);
        out.write(compressed.toByteArray());
        return out.toByteArray();
    }

    private static byte[] appendVersion(byte[] body, int version) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(body);
        writeShort(out, version);
        return out.toByteArray();
    }

    private static void writeInt(java.io.ByteArrayOutputStream out, int value) {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeShort(java.io.ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void patchGroupChecksum(byte[] index, int targetIndex, int checksum) {
        int pos = 0;
        int protocol = index[pos++] & 0xFF;
        if (protocol >= 6) pos += 4;
        boolean names = (index[pos++] & 0xFF) != 0;
        int size = ((index[pos++] & 0xFF) << 8) | (index[pos++] & 0xFF);
        int group = 0;
        for (int i = 0; i < size; i++) {
            group += ((index[pos++] & 0xFF) << 8) | (index[pos++] & 0xFF);
        }
        if (names) pos += size * 2;
        pos += targetIndex * 4;
        index[pos++] = (byte) (checksum >> 24);
        index[pos++] = (byte) (checksum >> 16);
        index[pos++] = (byte) (checksum >> 8);
        index[pos] = (byte) checksum;
    }
}
