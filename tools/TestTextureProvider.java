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

/** Verify texture ids load and are marked available in the 2009 client. */
public final class TestTextureProvider {
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
            System.out.println("Usage: TestTextureProvider <cacheDir> <id> [id..]");
            return;
        }
        File cacheDir = new File(args[0]);
        Js5 textures = new Js5(new DiskProvider(cacheDir, 9), false, false);
        Js5 textureConfigs = new Js5(new DiskProvider(cacheDir, 26), false, false);
        Js5 sprites = new Js5(new DiskProvider(cacheDir, 8), false, false);
        TextureProvider provider = new Js5GlTextureProvider(textures, textureConfigs, sprites, 20, false);
        for (int i = 1; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            boolean available = provider.method3236(id);
            int avg = provider.getAverageColor(id);
            boolean opaque = provider.isOpaque(id);
            boolean low = provider.isLowDetail(id);
            int[] pixels = provider.method3232(id, 1.0f);
            System.out.println("id " + id + ": available=" + available
                    + " avgColor=" + avg
                    + " opaque=" + opaque
                    + " lowDetail=" + low
                    + " pixels=" + (pixels == null ? "null" : pixels.length));
        }
    }
}
