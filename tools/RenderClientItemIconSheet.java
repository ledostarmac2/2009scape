import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Inv;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;
import rt4.ObjType;
import rt4.ObjTypeList;
import rt4.Rasteriser;
import rt4.Js5GlTextureProvider;
import rt4.SoftwareSprite;
import rt4.Sprite;
import rt4.TextureProvider;

public final class RenderClientItemIconSheet {
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

    private static final class Camera {
        final int zoom;
        final int x;
        final int y;
        final int z;
        final int xOffset;
        final int yOffset;
        final String label;

        Camera(int zoom, int x, int y, int z, int xOffset, int yOffset) {
            this.zoom = zoom;
            this.x = x;
            this.y = y;
            this.z = z;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.label = x + "/" + y + "/" + z + (xOffset == Integer.MIN_VALUE ? "" : " " + xOffset + "," + yOffset);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.err.println("Usage: RenderClientItemIconSheet <cacheDir> <itemId> <zoom> <xAngleCsv> <yAngleCsv> <zAngleCsv> <out.png> [xOffset] [yOffset]");
            System.exit(1);
        }
        File cacheDir = new File(args[0]);
        int itemId = Integer.parseInt(args[1]);
        int zoom = Integer.parseInt(args[2]);
        int[] xs = parseCsv(args[3]);
        int[] ys = parseCsv(args[4]);
        int[] zs = parseCsv(args[5]);
        String out = args.length >= 7 ? args[6] : "tools/client-icon-sheet.png";
        int[] xOffsets = args.length >= 8 ? parseCsv(args[7]) : new int[]{Integer.MIN_VALUE};
        int[] yOffsets = args.length >= 9 ? parseCsv(args[8]) : new int[]{Integer.MIN_VALUE};

        Js5 items = new Js5(new DiskProvider(cacheDir, 19), false, false);
        Js5 models = new Js5(new DiskProvider(cacheDir, 7), false, false);
        Js5 textures = new Js5(new DiskProvider(cacheDir, 9), false, false);
        Js5 textureConfigs = new Js5(new DiskProvider(cacheDir, 26), false, false);
        Js5 sprites = new Js5(new DiskProvider(cacheDir, 8), false, false);
        ObjTypeList.init(items, null, models);
        TextureProvider textureProvider = new Js5GlTextureProvider(textures, textureConfigs, sprites, 20, true);
        Rasteriser.unpackTextures(textureProvider);
        Rasteriser.setBrightness(0.8F);

        List<Camera> cameras = new ArrayList<>();
        for (int z : zs) {
            for (int x : xs) {
                for (int y : ys) {
                    for (int xOffset : xOffsets) {
                        for (int yOffset : yOffsets) {
                            cameras.add(new Camera(zoom, x, y, z, xOffset, yOffset));
                        }
                    }
                }
            }
        }

        int scale = 4;
        int cellW = 158;
        int cellH = 158;
        int cols = Math.min(8, cameras.size());
        int rows = (cameras.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * cellW, rows * cellH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
        g.setColor(new Color(0x1f1f25));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());

        ObjType type = ObjTypeList.get(itemId);
        int originalZoom = type.zoom2d;
        int originalX = type.xAngle2D;
        int originalY = type.yAngle2D;
        int originalZ = type.zAngle2D;
        int originalXOff = type.xOffset2D;
        int originalYOff = type.yOffset2D;
        try {
            for (int i = 0; i < cameras.size(); i++) {
                Camera camera = cameras.get(i);
                type.zoom2d = camera.zoom;
                type.xAngle2D = camera.x;
                type.yAngle2D = camera.y;
                type.zAngle2D = camera.z;
                type.xOffset2D = camera.xOffset == Integer.MIN_VALUE ? originalXOff : camera.xOffset;
                type.yOffset2D = camera.yOffset == Integer.MIN_VALUE ? originalYOff : camera.yOffset;
                Sprite sprite = Inv.renderObjectSprite(0, false, itemId, false, 2, 1, false);
                if (!(sprite instanceof SoftwareSprite)) {
                    throw new IllegalStateException("Expected SoftwareSprite but got " + sprite);
                }
                BufferedImage icon = toImage((SoftwareSprite) sprite);
                int col = i % cols;
                int row = i / cols;
                int x = col * cellW;
                int y = row * cellH;
                g.setColor(new Color(0x665b48));
                g.fillRect(x + 7, y + 7, 36 * scale, 32 * scale);
                g.drawImage(icon, x + 7, y + 7, 36 * scale, 32 * scale, null);
                g.setColor(Color.WHITE);
                g.drawString(camera.label, x + 7, y + 150);
            }
        } finally {
            type.zoom2d = originalZoom;
            type.xAngle2D = originalX;
            type.yAngle2D = originalY;
            type.zAngle2D = originalZ;
            type.xOffset2D = originalXOff;
            type.yOffset2D = originalYOff;
            g.dispose();
        }

        ImageIO.write(sheet, "png", new File(out));
        System.out.println("rendered item " + itemId + " candidates -> " + out);
    }

    private static int[] parseCsv(String csv) {
        String[] parts = csv.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Integer.parseInt(parts[i].trim());
        }
        return out;
    }

    private static BufferedImage toImage(SoftwareSprite sprite) {
        BufferedImage image = new BufferedImage(sprite.width, sprite.height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < sprite.height; y++) {
            for (int x = 0; x < sprite.width; x++) {
                int rgb = sprite.pixels[y * sprite.width + x];
                image.setRGB(x, y, rgb == 0 ? 0 : (0xFF000000 | rgb));
            }
        }
        return image;
    }
}
