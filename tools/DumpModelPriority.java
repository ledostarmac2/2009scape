import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;
import rt4.RawModel;

/** Dump render-priority-related fields of models so we can match imported worn models to references. */
public final class DumpModelPriority {
    private static final class DP extends Js5ResourceProvider {
        private final Cache master, archive; private final int archiveId;
        DP(File dir, int archiveId) throws Exception {
            this.archiveId = archiveId;
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
            archive = new Cache(archiveId, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + archiveId), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        }
        public Js5Index fetchIndex() { byte[] i = master.read(archiveId); return i == null ? null : new Js5Index(i, Buffer.crc32(i, i.length)); }
        public void prefetchGroup(int g) {}
        public int getPercentageComplete(int g) { return 100; }
        public byte[] fetchGroup(int g) { return archive.read(g); }
    }

    public static void main(String[] args) throws Exception {
        File cache = new File(args[0]);
        Js5 models = new Js5(new DP(cache, 7), false, false);
        for (int i = 1; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            byte[] file = models.fetchFile(id, 0);
            if (file == null) { System.out.println(id + ": MISSING"); continue; }
            RawModel m = new RawModel(file);
            StringBuilder sb = new StringBuilder(id + ": ");
            for (Field f : RawModel.class.getDeclaredFields()) {
                String n = f.getName().toLowerCase();
                if (n.contains("prior") || n.contains("label") || n.contains("skin") || n.contains("bone")) {
                    f.setAccessible(true);
                    Object v = f.get(m);
                    String s = v == null ? "null" : v instanceof byte[] ? Arrays.toString(shorten((byte[]) v)) : v instanceof int[] ? Arrays.toString(shortenI((int[]) v)) : String.valueOf(v);
                    sb.append(f.getName()).append('=').append(s).append("  ");
                }
            }
            System.out.println(sb);
        }
    }
    private static byte[] shorten(byte[] a) { return a.length <= 16 ? a : Arrays.copyOf(a, 16); }
    private static int[] shortenI(int[] a) { return a.length <= 16 ? a : Arrays.copyOf(a, 16); }
}
