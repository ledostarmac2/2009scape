import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;
import rt4.RawModel;

/** Dump triangle color/texture distribution for model comparison. */
public final class DumpModelColors {
    private static final class DP extends Js5ResourceProvider {
        private final Cache master, archive;
        private final int archiveId;

        DP(File dir, int archiveId) throws Exception {
            this.archiveId = archiveId;
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
            archive = new Cache(archiveId, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + archiveId), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        }

        public Js5Index fetchIndex() {
            byte[] i = master.read(archiveId);
            return i == null ? null : new Js5Index(i, Buffer.crc32(i, i.length));
        }

        public void prefetchGroup(int g) {}

        public int getPercentageComplete(int g) {
            return 100;
        }

        public byte[] fetchGroup(int g) {
            return archive.read(g);
        }
    }

    public static void main(String[] args) throws Exception {
        File cache = new File(args[0]);
        Js5 models = new Js5(new DP(cache, 7), false, false);
        for (int i = 1; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            byte[] file = models.fetchFile(id, 0);
            if (file == null) {
                System.out.println(id + ": MISSING");
                continue;
            }
            RawModel m = new RawModel(file);
            System.out.println(id + ": vertices=" + m.vertexCount + " triangles=" + m.triangleCount
                    + " texturedCount=" + m.texturedCount
                    + " triangleInfo=" + (m.triangleInfo == null ? "null" : "present")
                    + " triangleTextures=" + (m.triangleTextures == null ? "null" : "present")
                    + " triangleTextureIndex=" + (m.triangleTextureIndex == null ? "null" : "present"));
            Map<Integer, Integer> colors = new HashMap<>();
            Map<Integer, Integer> textures = new HashMap<>();
            for (int f = 0; f < m.triangleCount; f++) {
                int c = m.triangleColors[f] & 0xFFFF;
                colors.merge(c, 1, Integer::sum);
                if (m.triangleTextures != null) {
                    int t = m.triangleTextures[f] & 0xFFFF;
                    if (t != 0xFFFF) {
                        textures.merge(t, 1, Integer::sum);
                    }
                }
            }
            System.out.println("  colors=" + formatDist(colors));
            if (!textures.isEmpty()) {
                System.out.println("  textures=" + formatDist(textures));
            }
            if (m.triangleTextureIndex != null) {
                int[] idx = new int[256];
                for (byte b : m.triangleTextureIndex) {
                    idx[b & 0xFF]++;
                }
                System.out.println("  textureIndexDist=" + Arrays.toString(shorten(idx)));
            }
        }
    }

    private static String formatDist(Map<Integer, Integer> map) {
        StringBuilder sb = new StringBuilder("{");
        map.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            if (sb.length() > 1) sb.append(", ");
            sb.append(e.getKey()).append(':').append(e.getValue());
        });
        return sb.append('}').toString();
    }

    private static int[] shorten(int[] a) {
        int end = a.length;
        while (end > 0 && a[end - 1] == 0) end--;
        return Arrays.copyOf(a, Math.max(1, end));
    }
}
