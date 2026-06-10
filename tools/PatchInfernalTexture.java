import java.io.File;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.Js5Index;

/**
 * Enable animated lava on infernal cape texture 59 in the 2009 cache.
 * Prior import wrote triangleTextures=59 on models but archive-26 config had animated=false.
 */
public final class PatchInfernalTexture {
    private static final int TEXTURE_ARCHIVE = 9;
    private static final int CONFIG_ARCHIVE = 26;
    private static final int TEXTURE_ID = 59;
    private static final int REF_TEXTURE_ID = 40; // fire cape lava

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: PatchInfernalTexture <cacheDir>");
            return;
        }
        File cacheDir = new File(args[0]);
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "rw", Long.MAX_VALUE), 5200, 0);
        Cache master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx255"), "rw", Long.MAX_VALUE), 6000, 0), 1000000);

        byte[] packedIndex = master.read(CONFIG_ARCHIVE);
        int archiveIndexVersion = readTrailingVersion(packedIndex);
        byte[] indexBytes = Js5Compression.uncompress(packedIndex);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));

        byte[] configPacked = new Cache(CONFIG_ARCHIVE, data,
                new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + CONFIG_ARCHIVE), "r", Long.MAX_VALUE), 6000, 0), 1000000)
                .read(0);
        byte[] configRaw = Js5Compression.uncompress(stripVersion(configPacked));
        ParseTextureConfigBundle.Info[] infos = ParseTextureConfigBundle.parse(configRaw);
        ParseTextureConfigBundle.Info ref = infos[REF_TEXTURE_ID];
        ParseTextureConfigBundle.Info tex = infos[TEXTURE_ID];
        System.out.println("before id59: enabled=" + tex.enabled + " animated=" + tex.animated
                + " speed=" + tex.speed + " dir=" + tex.direction + " avgColor=" + tex.averageColor);
        System.out.println("ref id40: animated=" + ref.animated + " speed=" + ref.speed
                + " dir=" + ref.direction + " avgColor=" + ref.averageColor);

        tex.enabled = true;
        tex.animated = true;
        tex.opaque = ref.opaque;
        tex.lowDetail = ref.lowDetail;
        tex.flag93 = ref.flag93;
        tex.speed = ref.speed;
        tex.direction = ref.direction;
        tex.materialType = ref.materialType;
        tex.byte61 = ref.byte61;
        if (tex.averageColor == 0 || tex.averageColor == 108) {
            tex.averageColor = ref.averageColor;
        }

        byte[] patched = encodeBundle(infos);
        patchSingleFileGroup(cacheDir, data, master, CONFIG_ARCHIVE, 0, patched, indexBytes, index, archiveIndexVersion);
        System.out.println("after id59: animated=true speed=" + tex.speed + " dir=" + tex.direction
                + " avgColor=" + tex.averageColor);
        System.out.println("patched archive 26 group 0 (" + patched.length + " bytes)");
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

    private static void writeShort(java.io.ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void patchSingleFileGroup(File cacheDir, BufferedFile data, Cache master, int archiveId, int group,
                                             byte[] file, byte[] indexBytes, Js5Index index, int archiveIndexVersion) throws Exception {
        byte[] packedNoVersion = wrapUncompressed(file);
        byte[] packed = appendVersion(packedNoVersion, index.groupVersions[group]);
        Cache archive = new Cache(archiveId, data,
                new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + archiveId), "rw", Long.MAX_VALUE), 6000, 0), 1000000);
        if (!archive.write(group, packed.length, packed)) {
            throw new IllegalStateException("Unable to write archive " + archiveId + " group " + group);
        }
        patchGroupChecksum(indexBytes, group, Buffer.crc32(packedNoVersion, packedNoVersion.length));
        byte[] repackedIndex = appendVersion(wrapGzip(indexBytes), archiveIndexVersion);
        if (!master.write(archiveId, repackedIndex.length, repackedIndex)) {
            throw new IllegalStateException("Unable to write archive index " + archiveId);
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

    private static void patchGroupChecksum(byte[] index, int targetGroup, int checksum) {
        int pos = 0;
        int protocol = index[pos++] & 0xFF;
        if (protocol >= 6) pos += 4;
        boolean names = (index[pos++] & 0xFF) != 0;
        int size = ((index[pos++] & 0xFF) << 8) | (index[pos++] & 0xFF);
        int group = 0;
        int targetIndex = -1;
        for (int i = 0; i < size; i++) {
            group += ((index[pos++] & 0xFF) << 8) | (index[pos++] & 0xFF);
            if (group == targetGroup) targetIndex = i;
        }
        if (targetIndex < 0) throw new IllegalStateException("Group " + targetGroup + " not found");
        if (names) pos += size * 2;
        pos += targetIndex * 4;
        index[pos++] = (byte) (checksum >> 24);
        index[pos++] = (byte) (checksum >> 16);
        index[pos++] = (byte) (checksum >> 8);
        index[pos] = (byte) checksum;
    }
}
