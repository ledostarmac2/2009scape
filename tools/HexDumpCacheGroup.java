import java.io.File;
import rt4.BufferedFile;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;

/** Hex dump a cache group for quick format inspection. */
public final class HexDumpCacheGroup {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: HexDumpCacheGroup <cacheDir> <archive> <groupId>");
            return;
        }
        File cacheDir = new File(args[0]);
        int archive = Integer.parseInt(args[1]);
        int group = Integer.parseInt(args[2]);
        byte[] raw = read(cacheDir, archive, group);
        if (raw == null) {
            System.out.println("MISSING");
            return;
        }
        System.out.println("archive=" + archive + " group=" + group + " len=" + raw.length);
        for (int i = 0; i < raw.length; i++) {
            if (i % 16 == 0) System.out.printf("%04x: ", i);
            System.out.printf("%02x ", raw[i] & 0xFF);
            if (i % 16 == 15) System.out.println();
        }
        if (raw.length % 16 != 0) System.out.println();
    }

    private static byte[] read(File cacheDir, int archive, int id) throws Exception {
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache cache = new Cache(archive, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + archive), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        byte[] packed = cache.read(id);
        if (packed == null) return null;
        return Js5Compression.uncompress(strip(packed));
    }

    private static byte[] strip(byte[] group) {
        if (group.length < 2) return group;
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }
}
