import java.io.File;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;

/** Parse archive-26 group-0 texture config bundle like Js5GlTextureProvider. */
public final class ParseTextureConfigBundle {
    static final class Info {
        boolean enabled;
        boolean animated;
        boolean opaque;
        boolean lowDetail;
        boolean flag93;
        int speed;
        int direction;
        int materialType;
        int byte61;
        int averageColor;
    }

    public static void main(String[] args) throws Exception {
        File cacheDir = new File(args[0]);
        byte[] raw = read(cacheDir, 26, 0);
        if (raw == null) {
            System.out.println("archive 26 group 0: MISSING");
            return;
        }
        Info[] infos = parse(raw);
        System.out.println("textureConfigCount=" + infos.length);
        for (int i = 1; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            if (id < 0 || id >= infos.length) {
                System.out.println("id " + id + ": out of range");
                continue;
            }
            Info t = infos[id];
            System.out.println("id " + id + ": enabled=" + t.enabled
                    + " animated=" + t.animated
                    + " opaque=" + t.opaque
                    + " lowDetail=" + t.lowDetail
                    + " flag93=" + t.flag93
                    + " speed=" + t.speed
                    + " dir=" + t.direction
                    + " material=" + t.materialType
                    + " byte61=" + t.byte61
                    + " avgColor=" + t.averageColor);
        }
    }

    static Info[] parse(byte[] raw) {
        Buffer buf = new Buffer(raw);
        int count = buf.g2();
        Info[] out = new Info[count];
        for (int i = 0; i < count; i++) out[i] = new Info();
        for (int i = 0; i < count; i++) out[i].enabled = buf.g1() == 1;
        for (int i = 0; i < count; i++) if (out[i].enabled) out[i].animated = buf.g1() == 1;
        for (int i = 0; i < count; i++) if (out[i].enabled) out[i].opaque = buf.g1() == 1;
        for (int i = 0; i < count; i++) if (out[i].enabled) out[i].lowDetail = buf.g1() == 1;
        for (int i = 0; i < count; i++) if (out[i].enabled) out[i].flag93 = buf.g1() == 1;
        for (int i = 0; i < count; i++) if (out[i].enabled) out[i].speed = buf.g1b() & 0xFF;
        for (int i = 0; i < count; i++) if (out[i].enabled) out[i].direction = buf.g1b() & 0xFF;
        for (int i = 0; i < count; i++) if (out[i].enabled) out[i].materialType = buf.g1b() & 0xFF;
        for (int i = 0; i < count; i++) if (out[i].enabled) out[i].byte61 = buf.g1b() & 0xFF;
        for (int i = 0; i < count; i++) if (out[i].enabled) out[i].averageColor = buf.g2();
        return out;
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
