import java.io.File;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5GlTextureProvider;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;
import rt4.TextureProvider;

/** Sample texture pixel stats at multiple animation phases. */
public final class DumpTexturePixels {
    private static final class DiskProvider extends Js5ResourceProvider {
        private final Cache master;
        private final Cache archive;
        private final int archiveId;

        DiskProvider(File dir, int archiveId) throws Exception {
            this.archiveId = archiveId;
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
            archive = new Cache(archiveId, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + archiveId), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        }

        public Js5Index fetchIndex() {
            byte[] index = master.read(archiveId);
            return index == null ? null : new Js5Index(index, Buffer.crc32(index, index.length));
        }

        public void prefetchGroup(int group) {
        }

        public int getPercentageComplete(int group) {
            return 100;
        }

        public byte[] fetchGroup(int group) {
            return archive.read(group);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: DumpTexturePixels <cacheDir> <id> [id..]");
            return;
        }
        File cacheDir = new File(args[0]);
        Js5 textures = new Js5(new DiskProvider(cacheDir, 9), false, false);
        Js5 textureConfigs = new Js5(new DiskProvider(cacheDir, 26), false, false);
        Js5 sprites = new Js5(new DiskProvider(cacheDir, 8), false, false);
        TextureProvider provider = new Js5GlTextureProvider(textures, textureConfigs, sprites, 20, false);
        for (int i = 1; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            System.out.println("texture " + id + ":");
            for (float phase : new float[] { 0f, 0.25f, 0.5f, 0.75f, 1f }) {
                int[] px = provider.method3232(id, phase);
                if (px == null) {
                    System.out.println("  phase=" + phase + " pixels=null");
                    continue;
                }
                int nonZero = 0, min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                long r = 0, g = 0, b = 0;
                for (int v : px) {
                    if (v != 0) nonZero++;
                    if (v < min) min = v;
                    if (v > max) max = v;
                    r += (v >> 16) & 0xFF;
                    g += (v >> 8) & 0xFF;
                    b += v & 0xFF;
                }
                int n = px.length;
                System.out.printf("  phase=%.2f nonZero=%d min=0x%08x max=0x%08x avgRgb=(%d,%d,%d)%n",
                        phase, nonZero, min, max, r / n, g / n, b / n);
            }
        }
    }
}
