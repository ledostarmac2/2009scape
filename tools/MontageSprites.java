import java.io.File;
import java.awt.*;
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

public final class MontageSprites {
    private static final class DiskProvider extends Js5ResourceProvider {
        private final Cache master, archive; private final int archiveId;
        DiskProvider(File dir, int archiveId) throws Exception {
            this.archiveId = archiveId;
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            this.master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
            this.archive = new Cache(archiveId, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + archiveId), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        }
        public Js5Index fetchIndex() { byte[] i = master.read(archiveId); return i == null ? null : new Js5Index(i, Buffer.crc32(i, i.length)); }
        public void prefetchGroup(int g) {} public int getPercentageComplete(int g){return 100;} public byte[] fetchGroup(int g){return archive.read(g);}
    }

    public static void main(String[] args) throws Exception {
        File cacheDir = new File(args[0]);
        int from = Integer.parseInt(args[1]);
        int to = Integer.parseInt(args[2]);
        int scale = 6;
        int cell = 150, cols = 6;
        int n = to - from + 1;
        int rows = (n + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(90, 90, 100)); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        Js5 js5 = new Js5(new DiskProvider(cacheDir, 8), false, false);
        int idx = 0;
        for (int id = from; id <= to; id++, idx++) {
            int cx = (idx % cols) * cell, cy = (idx / cols) * cell;
            g.setColor(Color.YELLOW);
            g.drawString("" + id, cx + 4, cy + 16);
            SoftwareSprite[] sprites;
            try { sprites = SpriteLoader.loadSoftwareSprites(id, js5); } catch (Throwable t) { continue; }
            if (sprites == null || sprites.length == 0 || sprites[0] == null) continue;
            SoftwareSprite s = sprites[0];
            if (s.pixels == null || s.width <= 0 || s.height <= 0) continue;
            BufferedImage img = new BufferedImage(s.width, s.height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < s.height; y++)
                for (int x = 0; x < s.width; x++) {
                    int p = s.pixels[y * s.width + x];
                    int a = (p == 0) ? 0 : 0xFF;
                    img.setRGB(x, y, (a << 24) | (p & 0xFFFFFF));
                }
            int dw = s.width * scale, dh = s.height * scale;
            g.drawImage(img, cx + (cell - dw) / 2, cy + 20 + (cell - 20 - dh) / 2, dw, dh, null);
        }
        g.dispose();
        File out = new File("tools/sprite-out/montage_" + from + "_" + to + ".png");
        ImageIO.write(sheet, "png", out);
        System.out.println("wrote " + out);
    }
}
