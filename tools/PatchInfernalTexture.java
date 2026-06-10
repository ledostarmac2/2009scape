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
 * Import OSRS infernal cape lava (texture 59) into the game cache.
 *
 * OSRS infernal models reference texture 59 natively. The 2009 client
 * {@code Js5GlTextureProvider} allocates config arrays from archive-26 group-0
 * count (680 in our cache), not a hardcoded 51-slot cap; {@code Rasteriser}
 * passes model texture ids straight through with no remap. Import the OSRS
 * pixels + animation config into slot 59 and keep models on 59 (no fire-cape 40).
 */
public final class PatchInfernalTexture {
    private static final int TEXTURE_ARCHIVE = 9;
    private static final int CONFIG_ARCHIVE = 26;
    private static final int SOURCE_TEXTURE_ID = 59;
    /** Native OSRS infernal lava slot. */
    private static final int TARGET_TEXTURE_ID = 59;
    private static final int REF_TEXTURE_ID = 40;
    /** Procedural infernal lava references archive-8 sprite group 768 (stub sheet). */
    private static final int INFERNAL_SPRITE_ID = 768;
    /** OSRS infernal lava animation (rev233 TextureDefinition). */
    private static final int INFERNAL_ANIM_SPEED = 3;
    private static final int INFERNAL_ANIM_DIRECTION = 1;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: PatchInfernalTexture <gameCacheDir> [osrsCacheDir]");
            return;
        }
        File cacheDir = new File(args[0]);
        File osrsCache = args.length > 1 ? new File(args[1]) : null;

        byte[] nativeTex = read2009Texture(cacheDir, TARGET_TEXTURE_ID);
        byte[] textureBytes = nativeTex != null && is2009TextureOp(nativeTex)
                ? nativeTex
                : infernalTextureOpFallback();
        if (nativeTex != null && !is2009TextureOp(nativeTex)) {
            System.out.println("replacing incompatible slot " + TARGET_TEXTURE_ID + " ("
                    + nativeTex.length + " bytes OSRS rev233) with native 2009 TextureOp ("
                    + textureBytes.length + " bytes)");
            patchTextureGroup(cacheDir, TARGET_TEXTURE_ID, textureBytes);
        } else if (nativeTex == null) {
            patchTextureGroup(cacheDir, TARGET_TEXTURE_ID, textureBytes);
            System.out.println("installed infernal TextureOp into slot " + TARGET_TEXTURE_ID
                    + " (" + textureBytes.length + " bytes)");
        } else {
            System.out.println("preserving native 2009 TextureOp slot " + TARGET_TEXTURE_ID
                    + " (" + nativeTex.length + " bytes)");
        }

        patchInfernalSprite(cacheDir, osrsCache);
        patchTextureConfig(cacheDir);
        System.out.println("texture config slot " + TARGET_TEXTURE_ID + " animated for infernal lava");
    }

    private static void patchInfernalSprite(File cacheDir, File osrsCache) throws Exception {
        byte[] sprite = readOsrsSprite(osrsCache, INFERNAL_SPRITE_ID);
        if (sprite == null) {
            System.out.println("sprite " + INFERNAL_SPRITE_ID + " unavailable; keeping existing archive-8 group");
            return;
        }
        patchTextureGroup(cacheDir, INFERNAL_SPRITE_ID, sprite, 8);
        System.out.println("installed infernal sprite " + INFERNAL_SPRITE_ID + " (" + sprite.length + " bytes)");
    }

    private static byte[] readOsrsSprite(File osrsCache, int id) throws Exception {
        if (osrsCache == null || !osrsCache.isDirectory()) {
            return null;
        }
        BufferedFile data = openData(osrsCache, "r");
        Cache sprites = openArchive(osrsCache, data, 8, "r");
        byte[] packed = sprites.read(id);
        if (packed == null) {
            return null;
        }
        return Js5Compression.uncompress(stripVersion(packed));
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

    private static byte[] readOsrsTexture(File osrsCache, int id) throws Exception {
        if (osrsCache == null || !osrsCache.isDirectory()) {
            return null;
        }
        try (Store store = new Store(osrsCache)) {
            store.load();
            Archive archive = store.getIndex(IndexType.TEXTURES).getArchive(id);
            if (archive == null) {
                return null;
            }
            byte[] packed = store.getStorage().loadArchive(archive);
            FSFile file = archive.getFiles(packed).findFile(0);
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
        patchTextureGroup(cacheDir, groupId, raw, TEXTURE_ARCHIVE);
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
        target.speed = INFERNAL_ANIM_SPEED;
        target.direction = INFERNAL_ANIM_DIRECTION;
        target.materialType = ref.materialType;
        target.byte61 = ref.byte61;
        if (target.averageColor == 0 || target.averageColor == 108) {
            target.averageColor = ref.averageColor != 0 ? ref.averageColor : 6089;
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

    /** 2009 archive-9 textures are TextureOp pipelines; OSRS rev233 blobs must not overwrite them. */
    private static boolean is2009TextureOp(byte[] raw) {
        if (raw == null || raw.length < 12) {
            return false;
        }
        int opCount = raw[0] & 0xFF;
        return opCount >= 1 && opCount <= 8;
    }

    /**
     * Captured native 2009 TextureOp for infernal lava (archive-9 group 59, 44 bytes).
     * Procedural pipeline with no archive-8 sprite dependencies; matches OSRS infernal UVs.
     */
    private static byte[] infernalTextureOpFallback() {
        return new byte[] {
                0x03, 0x00, 0x00, 0x01, 0x00, 0x01, 0x03, 0x01, 0x00, 0x02, 0x08, 0x01, 0x01, 0x00, 0x00, 0x04,
                0x00, 0x00, 0x0f, (byte) 0xff, 0x05, (byte) 0xfa, 0x0f, (byte) 0xff, 0x0a, (byte) 0xd4, 0x0c,
                (byte) 0xed, 0x10, 0x00, 0x06, (byte) 0xb5, 0x01, 0x02, 0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0x00,
                0x00, 0x22, 0x00
        };
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
