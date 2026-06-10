import java.io.File;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Store;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.Js5Index;

/**
 * Remap infernal cape lava for the 2009 client texture limit (valid ids 0-50).
 *
 * OSRS infernal models reference texture 59, which is outside the 2009 client
 * rasterizer limit (valid ids 0-50). Fire cape lava already renders from slot 40.
 * This tool copies OSRS texture 59 into archive-9 slot {@link #TARGET_TEXTURE_ID}
 * and enables its animated config in archive-26. Model remapping is handled by
 * ImportOsrsItemBatch (textureRemap 59->40 in manifest).
 */
public final class PatchInfernalTexture {
    private static final int TEXTURE_ARCHIVE = 9;
    private static final int CONFIG_ARCHIVE = 26;
    private static final int SOURCE_TEXTURE_ID = 59;
    /** Fire-cape lava slot; proven to animate in the 2009 client. */
    private static final int TARGET_TEXTURE_ID = 40;
    private static final int REF_TEXTURE_ID = 40;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: PatchInfernalTexture <gameCacheDir> [osrsCacheDir]");
            return;
        }
        File cacheDir = new File(args[0]);
        File osrsCache = args.length > 1 ? new File(args[1]) : null;

        byte[] textureBytes = readOsrsTexture(osrsCache, SOURCE_TEXTURE_ID);
        if (textureBytes == null) {
            textureBytes = read2009Texture(cacheDir, SOURCE_TEXTURE_ID);
        }
        if (textureBytes == null) {
            throw new IllegalStateException("Missing infernal lava texture " + SOURCE_TEXTURE_ID
                    + " in OSRS cache and game cache");
        }
        byte[] nativeTarget = read2009Texture(cacheDir, TARGET_TEXTURE_ID);
        if (nativeTarget != null && nativeTarget.length > textureBytes.length) {
            System.out.println("keeping native texture " + TARGET_TEXTURE_ID + " ("
                    + nativeTarget.length + " bytes; OSRS source is " + textureBytes.length + " bytes)");
        } else {
            patchTextureGroup(cacheDir, TARGET_TEXTURE_ID, textureBytes);
            System.out.println("texture " + SOURCE_TEXTURE_ID + " -> " + TARGET_TEXTURE_ID
                    + " written (" + textureBytes.length + " bytes)");
        }

        patchTextureConfig(cacheDir);
        System.out.println("texture config slot " + TARGET_TEXTURE_ID + " animated for infernal lava");
    }

    private static byte[] readOsrsTexture(File osrsCache, int id) throws Exception {
        if (osrsCache == null || !osrsCache.isDirectory()) {
            return null;
        }
        try (Store store = new Store(osrsCache)) {
            store.load();
            Archive archive = store.getIndex(IndexType.TEXTURES).getArchive(0);
            if (archive == null) {
                return null;
            }
            byte[] packed = store.getStorage().loadArchive(archive);
            FSFile file = archive.getFiles(packed).findFile(id);
            if (file == null || file.getContents() == null || file.getContents().length == 0) {
                return null;
            }
            System.out.println("loaded OSRS texture " + id + " (" + file.getContents().length + " bytes)");
            return file.getContents();
        } catch (Exception ex) {
            System.out.println("OSRS texture " + id + " unavailable: " + ex.getMessage());
            return null;
        }
    }

    private static byte[] read2009Texture(File cacheDir, int id) throws Exception {
        BufferedFile data = openData(cacheDir, "r");
        Cache textures = openArchive(cacheDir, data, TEXTURE_ARCHIVE, "r");
        byte[] packed = textures.read(id);
        if (packed == null) {
            return null;
        }
        byte[] raw = Js5Compression.uncompress(stripVersion(packed));
        System.out.println("loaded game texture " + id + " (" + raw.length + " bytes)");
        return raw;
    }

    private static void patchTextureGroup(File cacheDir, int groupId, byte[] raw) throws Exception {
        BufferedFile data = openData(cacheDir, "rw");
        Cache master = openArchive(cacheDir, data, 255, "rw");
        Cache textures = openArchive(cacheDir, data, TEXTURE_ARCHIVE, "rw");

        byte[] packedIndex = master.read(TEXTURE_ARCHIVE);
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
        patchSingleFileGroup(cacheDir, data, master, TEXTURE_ARCHIVE, groupId, raw, indexBytes, index, archiveIndexVersion, version);
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
        ParseTextureConfigBundle.Info source = SOURCE_TEXTURE_ID < infos.length ? infos[SOURCE_TEXTURE_ID] : null;
        ParseTextureConfigBundle.Info target = infos[TARGET_TEXTURE_ID];
        System.out.println("before id" + TARGET_TEXTURE_ID + ": enabled=" + target.enabled
                + " animated=" + target.animated + " avgColor=" + target.averageColor);
        if (source != null) {
            System.out.println("source id" + SOURCE_TEXTURE_ID + ": enabled=" + source.enabled
                    + " animated=" + source.animated + " avgColor=" + source.averageColor);
        }
        System.out.println("ref id" + REF_TEXTURE_ID + ": animated=" + ref.animated
                + " speed=" + ref.speed + " dir=" + ref.direction + " avgColor=" + ref.averageColor);

        target.enabled = true;
        target.animated = true;
        target.opaque = source != null && source.enabled ? source.opaque : ref.opaque;
        target.lowDetail = source != null && source.enabled ? source.lowDetail : ref.lowDetail;
        target.flag93 = source != null && source.enabled ? source.flag93 : ref.flag93;
        target.speed = source != null && source.enabled ? source.speed : ref.speed;
        target.direction = source != null && source.enabled ? source.direction : ref.direction;
        target.materialType = source != null && source.enabled ? source.materialType : ref.materialType;
        target.byte61 = source != null && source.enabled ? source.byte61 : ref.byte61;
        if (source != null && source.enabled && source.averageColor != 0) {
            target.averageColor = source.averageColor;
        } else if (target.averageColor == 0 || target.averageColor == 108) {
            target.averageColor = ref.averageColor;
        }

        byte[] patched = encodeBundle(infos);
        patchSingleFileGroup(cacheDir, data, master, CONFIG_ARCHIVE, 0, patched, indexBytes, index, archiveIndexVersion, 0);
        System.out.println("after id" + TARGET_TEXTURE_ID + ": animated=true speed=" + target.speed
                + " dir=" + target.direction + " avgColor=" + target.averageColor);
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

    private static void writeShort(java.io.ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
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
