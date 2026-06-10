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
import rt4.Js5Index;

/**
 * Atomic infernal-cape lava fix: replicate the working fire-cape sprite-sheet pattern.
 *
 * Fire cape (texture 40): 23-byte TextureOpSprite pipeline + archive-8 sprite 485 (128x128 sheet).
 * Infernal OSRS (texture 59): rev233 sprite 318 (128x128 animated lava sheet), avgColor=3875.
 * Animation driver matches fire cape (speed=0 dir=255) so the cloned TextureOp scrolls correctly.
 *
 * Prior patches failed because they kept OSRS speed/dir (1/1) with a fire-cape TextureOp pipeline,
 * imported raw OSRS sprite bytes (format 0) instead of rt4-encoded sheets, or used sprite 768 stub.
 */
public final class PatchInfernalCape {
    private static final int TEXTURE_ARCHIVE = 9;
    private static final int SPRITE_ARCHIVE = 8;
    private static final int CONFIG_ARCHIVE = 26;
    private static final int TARGET_TEXTURE_ID = 59;
    private static final int REF_TEXTURE_ID = 40;
    /** OSRS infernal lava animated sprite sheet (128x128). */
    private static final int OSRS_LAVA_SPRITE_ID = 318;
    /** Dedicated game-cache slot (avoids clobbering native sprite 318). */
    private static final int GAME_LAVA_SPRITE_ID = 768;
    /** OSRS infernal lava average HSL; animation speed/dir copied from fire cape 40. */
    private static final int INFERNAL_AVG_COLOR = 3875;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: PatchInfernalCape <gameCacheDir> [osrsCacheDir]");
            return;
        }
        File cacheDir = new File(args[0]);
        File osrsCache = args.length > 1 ? new File(args[1]) : null;

        byte[] fireCapeTex = read2009Texture(cacheDir, REF_TEXTURE_ID);
        if (fireCapeTex == null) {
            throw new IllegalStateException("reference texture " + REF_TEXTURE_ID + " missing");
        }
        byte[] lavaTex = buildSpriteTextureOp(fireCapeTex, GAME_LAVA_SPRITE_ID);
        patchTextureGroup(cacheDir, TARGET_TEXTURE_ID, lavaTex, TEXTURE_ARCHIVE);
        System.out.println("installed infernal TextureOp slot " + TARGET_TEXTURE_ID
                + " (" + lavaTex.length + " bytes, sprite " + GAME_LAVA_SPRITE_ID + ")");

        patchInfernalSprite(cacheDir, osrsCache);
        patchTextureConfig(cacheDir);
        System.out.println("infernal lava texture " + TARGET_TEXTURE_ID + " ready (sprite-sheet like fire cape "
                + REF_TEXTURE_ID + ")");
    }

    /**
     * Clone fire-cape TextureOp pipeline and swap the TextureOpSprite group id.
     * Fire cape bytes end with ... 01 e5 ... (sprite 485); infernal uses 03 00 (sprite 768).
     */
    static byte[] buildSpriteTextureOp(byte[] fireCapeTemplate, int spriteGroupId) {
        byte[] out = fireCapeTemplate.clone();
        int hi = (spriteGroupId >> 8) & 0xFF;
        int lo = spriteGroupId & 0xFF;
        boolean patched = false;
        for (int i = 0; i < out.length - 1; i++) {
            if (out[i] == 0x00 && out[i + 1] == 0x01 && i + 3 < out.length
                    && out[i + 2] == 0x01 && out[i + 3] == (byte) 0xe5) {
                out[i + 2] = (byte) hi;
                out[i + 3] = (byte) lo;
                patched = true;
                break;
            }
        }
        if (!patched) {
            return spriteTextureOpFallback(spriteGroupId);
        }
        return out;
    }

    /** Fire-cape-shaped TextureOp with explicit sprite id (23 bytes). */
    private static byte[] spriteTextureOpFallback(int spriteGroupId) {
        int hi = (spriteGroupId >> 8) & 0xFF;
        int lo = spriteGroupId & 0xFF;
        return new byte[] {
                0x02, 0x00, 0x27, 0x01, 0x01, 0x00, (byte) hi, (byte) lo,
                0x01, 0x00, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x01, 0x01, 0x00, 0x00, (byte) 0xff, 0x00, 0x00
        };
    }

    private static void patchInfernalSprite(File cacheDir, File osrsCache) throws Exception {
        byte[] sprite = encodeOsrsLavaSprite(osrsCache, OSRS_LAVA_SPRITE_ID);
        if (sprite == null) {
            throw new IllegalStateException("OSRS sprite " + OSRS_LAVA_SPRITE_ID + " unavailable");
        }
        patchTextureGroup(cacheDir, GAME_LAVA_SPRITE_ID, sprite, SPRITE_ARCHIVE);
        System.out.println("imported OSRS sprite " + OSRS_LAVA_SPRITE_ID + " -> rt4 sprite "
                + GAME_LAVA_SPRITE_ID + " (" + sprite.length + " bytes, canvas like fire cape 485)");
    }

    /** Decode OSRS sprite 318 and re-emit in native rt4 archive-8 format (like fire cape 485). */
    private static byte[] encodeOsrsLavaSprite(File osrsCache, int spriteId) throws Exception {
        if (osrsCache == null || !osrsCache.isDirectory()) {
            return null;
        }
        try (Store store = new Store(osrsCache)) {
            store.load();
            SpriteManager sprites = new SpriteManager(store);
            sprites.load();
            SpriteDefinition def = sprites.findSprite(spriteId, 0);
            if (def == null) {
                return null;
            }
            return encodeRt4SpriteSheet(def);
        }
    }

    /** Encode a single-frame rt4 sprite sheet on its native canvas (128x128 lava tiles). */
    private static byte[] encodeRt4SpriteSheet(SpriteDefinition d) {
        int w = d.getWidth();
        int h = d.getHeight();
        int canvasW = d.getMaxWidth() > 0 ? d.getMaxWidth() : w;
        int canvasH = d.getMaxHeight() > 0 ? d.getMaxHeight() : h;
        byte[] pix = d.pixelIdx;
        int[] palette = d.palette;
        int palLen = palette.length;
        int offX = d.getOffsetX();
        int offY = d.getOffsetY();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        for (int i = 0; i < w * h; i++) {
            out.write(pix[i] & 0xFF);
        }
        for (int i = 1; i < palLen; i++) {
            int c = palette[i];
            out.write((c >> 16) & 0xFF);
            out.write((c >> 8) & 0xFF);
            out.write(c & 0xFF);
        }
        writeShort(out, canvasW);
        writeShort(out, canvasH);
        out.write((palLen - 1) & 0xFF);
        writeShort(out, offX);
        writeShort(out, offY);
        writeShort(out, w);
        writeShort(out, h);
        writeShort(out, 1);
        return out.toByteArray();
    }

    private static byte[] read2009Texture(File cacheDir, int id) throws Exception {
        BufferedFile data = openData(cacheDir, "r");
        Cache textures = openArchive(cacheDir, data, TEXTURE_ARCHIVE, "r");
        byte[] packed = textures.read(id);
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

        byte[] patched = PatchInfernalTexture.encodeBundle(infos);
        patchSingleFileGroup(cacheDir, data, master, CONFIG_ARCHIVE, 0, patched, indexBytes, index, archiveIndexVersion, 0);
        System.out.println("after id" + TARGET_TEXTURE_ID + ": animated=true speed=" + target.speed
                + " dir=" + target.direction + " avgColor=" + target.averageColor);
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
