import java.io.File;
import net.runelite.cache.ItemManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.fs.Store;

public final class DumpOsrsItems {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            ItemManager itemManager = new ItemManager(store);
            itemManager.load();
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                ItemDefinition item = itemManager.getItem(id);
                System.out.println("item " + id + " " + item.name);
                System.out.println("  inventoryModel=" + item.inventoryModel);
                System.out.println("  maleModel0=" + item.maleModel0 + " maleModel1=" + item.maleModel1 + " maleModel2=" + item.maleModel2);
                System.out.println("  femaleModel0=" + item.femaleModel0 + " femaleModel1=" + item.femaleModel1 + " femaleModel2=" + item.femaleModel2);
                System.out.println("  maleHeadModel=" + item.maleHeadModel + " maleHeadModel2=" + item.maleHeadModel2
                        + " femaleHeadModel=" + item.femaleHeadModel + " femaleHeadModel2=" + item.femaleHeadModel2);
                System.out.println("  maleOffset=" + item.maleOffset + " femaleOffset=" + item.femaleOffset);
                System.out.println("  zoom=" + item.zoom2d + " xan=" + item.xan2d + " yan=" + item.yan2d + " zan=" + item.zan2d
                        + " xoff=" + item.xOffset2d + " yoff=" + item.yOffset2d);
                System.out.println("  cost=" + item.cost + " members=" + item.members + " tradeable=" + item.tradeable
                        + " stackable=" + item.stackable);
                if (item.colorFind != null) {
                    System.out.print("  recolorFrom=");
                    for (short v : item.colorFind) System.out.print((v & 0xFFFF) + ",");
                    System.out.print(" recolorTo=");
                    for (short v : item.colorReplace) System.out.print((v & 0xFFFF) + ",");
                    System.out.println();
                }
                if (item.textureFind != null) {
                    System.out.print("  retextureFrom=");
                    for (short v : item.textureFind) System.out.print((v & 0xFFFF) + ",");
                    System.out.print(" retextureTo=");
                    for (short v : item.textureReplace) System.out.print((v & 0xFFFF) + ",");
                    System.out.println();
                }
            }
        }
    }
}
