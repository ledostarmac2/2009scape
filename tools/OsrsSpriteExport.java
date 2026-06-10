import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.fs.Store;

public final class OsrsSpriteExport {
    public static void main(String[] args) throws Exception {
        File osrsCache = new File(args[0]);
        String outDir = args[1];
        new File(outDir).mkdirs();
        try (Store store = new Store(osrsCache)) {
            store.load();
            SpriteManager sm = new SpriteManager(store);
            sm.load();
            for (int i = 2; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                SpriteDefinition def = sm.findSprite(id, 0);
                if (def == null) { System.out.println("id=" + id + " NOT FOUND"); continue; }
                BufferedImage img = sm.getSpriteImage(def);
                File out = new File(outDir, "osrs_spr_" + id + ".png");
                ImageIO.write(img, "png", out);
                System.out.println("id=" + id + " " + def.getWidth() + "x" + def.getHeight()
                        + " maxW=" + def.getMaxWidth() + " maxH=" + def.getMaxHeight()
                        + " offX=" + def.getOffsetX() + " offY=" + def.getOffsetY()
                        + " -> " + out);
            }
        }
    }
}
