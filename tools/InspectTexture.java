import java.io.File;
import java.lang.reflect.Field;
import rt4.Buffer;
import rt4.BufferedFile;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.Texture;

/** Load a 2009 TextureOp definition and print referenced sprite group ids. */
public final class InspectTexture {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: InspectTexture <cacheDir> <textureId> [textureId..]");
            return;
        }
        File cacheDir = new File(args[0]);
        for (int i = 1; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            byte[] raw = read(cacheDir, id);
            if (raw == null) {
                System.out.println("texture " + id + ": MISSING");
                continue;
            }
            Texture tex = new Texture(new Buffer(raw));
            System.out.println("texture " + id + ": bytes=" + raw.length
                    + " spriteIds=" + java.util.Arrays.toString(readIntArray(tex, "anIntArray327"))
                    + " textureIds=" + java.util.Arrays.toString(readIntArray(tex, "anIntArray328")));
        }
    }

    private static int[] readIntArray(Object obj, String fieldName) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return (int[]) f.get(obj);
    }

    private static byte[] read(File cacheDir, int id) throws Exception {
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache cache = new Cache(9, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx9"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
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
