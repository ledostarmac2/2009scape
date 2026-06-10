import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;
import rt4.SpriteLoader;
import rt4.SoftwareSprite;
import rt4.Sprite;

public final class RenderSprite {
    private static final class DiskProvider extends Js5ResourceProvider {
        private final Cache master;
        private final Cache archive;
        private final int archiveId;
        DiskProvider(File dir, int archiveId) throws Exception {
            this.archiveId = archiveId;
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            this.master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
            this.archive = new Cache(archiveId, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + archiveId), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        }
        public Js5Index fetchIndex() { byte[] i = master.read(archiveId); return i == null ? null : new Js5Index(i, Buffer.crc32(i, i.length)); }
        public void prefetchGroup(int g) {}
        public int getPercentageComplete(int g) { return 100; }
        public byte[] fetchGroup(int g) { return archive.read(g); }
    }

    public static void main(String[] args) throws Exception {
        File cacheDir = new File(args[0]);
        int archive = Integer.parseInt(args[1]);
        int from = Integer.parseInt(args[2]);
        int to = Integer.parseInt(args[3]);
        String outDir = args.length > 4 ? args[4] : "tools/sprite-out";
        new File(outDir).mkdirs();
        Js5 js5 = new Js5(new DiskProvider(cacheDir, archive), false, false);
        for (int id = from; id <= to; id++) {
            SoftwareSprite[] sprites;
            try { sprites = SpriteLoader.loadSoftwareSprites(id, js5); }
            catch (Throwable t) { continue; }
            if (sprites == null) continue;
            for (int f = 0; f < sprites.length; f++) {
                SoftwareSprite s = sprites[f];
                if (s == null || s.pixels == null || s.width <= 0 || s.height <= 0) continue;
                BufferedImage img = new BufferedImage(s.width, s.height, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < s.height; y++)
                    for (int x = 0; x < s.width; x++) {
                        int p = s.pixels[y * s.width + x];
                        // sprite pixels are 0xRRGGBB with 0 = transparent
                        int a = (p == 0) ? 0 : 0xFF;
                        img.setRGB(x, y, (a << 24) | (p & 0xFFFFFF));
                    }
                String name = outDir + "/spr_" + id + (sprites.length > 1 ? "_" + f : "") + ".png";
                ImageIO.write(img, "png", new File(name));
                System.out.println("id=" + id + " frame=" + f + " " + s.width + "x" + s.height + " -> " + name);
            }
        }
    }
}
