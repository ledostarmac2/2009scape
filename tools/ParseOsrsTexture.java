import java.io.File;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.loaders.TextureLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Store;
import rt4.BufferedFile;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;

/** Parse OSRS (rev233) and 2009 texture group bytes side by side. */
public final class ParseOsrsTexture {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: ParseOsrsTexture <gameCacheDir> <id> [id..]");
            return;
        }
        File cacheDir = new File(args[0]);
        for (int i = 1; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            byte[] game = read2009(cacheDir, id);
            System.out.println("texture " + id + " game=" + (game == null ? "MISSING" : game.length + "b"));
            if (game != null) {
                tryOsrsLoader(id, game, "game");
            }
        }
        File osrs = new File(cacheDir.getParentFile().getParentFile(), "import/openrs2-osrs-2565-disk/cache");
        if (!osrs.isDirectory()) {
            osrs = new File("data/import/openrs2-osrs-2565-disk/cache");
        }
        if (osrs.isDirectory()) {
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                byte[] raw = readOsrs(osrs, id);
                System.out.println("texture " + id + " osrs=" + (raw == null ? "MISSING" : raw.length + "b"));
                if (raw != null) {
                    tryOsrsLoader(id, raw, "osrs");
                }
            }
        }
    }

    private static void tryOsrsLoader(int id, byte[] raw, String label) {
        try {
            TextureLoader loader = new TextureLoader();
            loader.setRev233(true);
            TextureDefinition def = loader.load(id, raw);
            System.out.println("  " + label + " rev233: sprites=" + java.util.Arrays.toString(def.getFileIds())
                    + " missingColor=" + def.getMissingColor()
                    + " animDir=" + def.getAnimationDirection()
                    + " animSpeed=" + def.getAnimationSpeed());
        } catch (Exception ex) {
            System.out.println("  " + label + " rev233 parse failed: " + ex.getMessage());
        }
        try {
            TextureLoader loader = new TextureLoader();
            loader.setRev233(false);
            TextureDefinition def = loader.load(id, raw);
            System.out.println("  " + label + " legacy: sprites=" + java.util.Arrays.toString(def.getFileIds())
                    + " count=" + def.getFileIds().length);
        } catch (Exception ex) {
            System.out.println("  " + label + " legacy parse failed: " + ex.getMessage());
        }
    }

    private static byte[] read2009(File cacheDir, int id) throws Exception {
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache cache = new Cache(9, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx9"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        byte[] packed = cache.read(id);
        if (packed == null) return null;
        return Js5Compression.uncompress(strip(packed));
    }

    private static byte[] readOsrs(File cacheDir, int id) throws Exception {
        try (Store store = new Store(cacheDir)) {
            store.load();
            Archive archive = store.getIndex(IndexType.TEXTURES).getArchive(id);
            if (archive == null) return null;
            byte[] packed = store.getStorage().loadArchive(archive);
            FSFile file = archive.getFiles(packed).findFile(0);
            return file == null ? null : file.getContents();
        }
    }

    private static byte[] strip(byte[] group) {
        if (group.length < 2) return group;
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }
}
