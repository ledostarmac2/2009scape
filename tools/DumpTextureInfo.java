import java.io.File;
import java.security.MessageDigest;
import net.runelite.cache.IndexType;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Store;
import rt4.BufferedFile;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;

/** Dump texture bytes/config from OSRS store or 2009 flat cache. */
public final class DumpTextureInfo {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: DumpTextureInfo <osrs|2009> <cacheDir> <id> [id..]");
            return;
        }
        String mode = args[0];
        File cache = new File(args[1]);
        for (int i = 2; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            if ("osrs".equals(mode)) {
                dumpOsrs(cache, id);
            } else {
                dump2009(cache, id);
            }
        }
    }

    private static void dumpOsrs(File cacheDir, int id) throws Exception {
        try (Store store = new Store(cacheDir)) {
            store.load();
            TextureManager tm = new TextureManager(store);
            tm.load();
            TextureDefinition def = tm.findTexture(id);
            if (def == null) {
                System.out.println("OSRS texture def " + id + ": MISSING");
                return;
            }
            System.out.println("OSRS texture def " + id + ": speed=" + def.animationSpeed
                    + " dir=" + def.animationDirection + " files=" + java.util.Arrays.toString(def.getFileIds()));
            Archive archive = store.getIndex(IndexType.TEXTURES).getArchive(0);
            byte[] packed = store.getStorage().loadArchive(archive);
            FSFile file = archive.getFiles(packed).findFile(id);
            if (file == null) {
                System.out.println("  raw file: MISSING");
            } else {
                System.out.println("  raw file: " + file.getContents().length + "b md5=" + md5(file.getContents()));
            }
        }
    }

    private static void dump2009(File cacheDir, int id) throws Exception {
        byte[] tex = read(cacheDir, 9, id);
        byte[] cfg = read(cacheDir, 26, id);
        System.out.println("2009 texture " + id + ": tex=" + label(tex) + " cfg=" + label(cfg));
    }

    private static byte[] read(File cacheDir, int archive, int id) throws Exception {
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache cache = new Cache(archive, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + archive), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        byte[] packed = cache.read(id);
        if (packed == null) return null;
        return Js5Compression.uncompress(strip(packed));
    }

    private static String label(byte[] raw) throws Exception {
        if (raw == null) return "MISSING";
        return raw.length + "b md5=" + md5(raw);
    }

    private static byte[] strip(byte[] group) {
        if (group.length < 2) return group;
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }

    private static String md5(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
