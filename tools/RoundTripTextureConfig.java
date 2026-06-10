import java.io.File;
import rt4.BufferedFile;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;

public final class RoundTripTextureConfig {
    public static void main(String[] args) throws Exception {
        File cacheDir = new File(args[0]);
        byte[] raw = read(cacheDir, 26, 0);
        ParseTextureConfigBundle.Info[] infos = ParseTextureConfigBundle.parse(raw);
        infos[59].animated = true;
        infos[59].direction = 255;
        infos[59].lowDetail = false;
        infos[59].averageColor = infos[40].averageColor;
        byte[] encoded = PatchInfernalCape.encodeBundle(infos);
        System.out.println("orig=" + raw.length + " encoded=" + encoded.length + " same=" + java.util.Arrays.equals(raw, encoded));
        try {
            ParseTextureConfigBundle.parse(encoded);
            System.out.println("reparse ok id59 animated=" + ParseTextureConfigBundle.parse(encoded)[59].animated);
        } catch (Exception ex) {
            System.out.println("reparse fail: " + ex);
        }
        if (!java.util.Arrays.equals(raw, encoded)) {
            int diff = 0;
            for (int i = 0; i < Math.min(raw.length, encoded.length); i++) {
                if (raw[i] != encoded[i]) diff++;
            }
            System.out.println("diff bytes=" + diff);
        }
    }

    private static byte[] read(File cacheDir, int archive, int id) throws Exception {
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache cache = new Cache(archive, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + archive), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        byte[] packed = cache.read(id);
        return Js5Compression.uncompress(strip(packed));
    }

    private static byte[] strip(byte[] group) {
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }
}
