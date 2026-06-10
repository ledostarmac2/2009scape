import java.io.File;
import net.runelite.cache.ItemManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.fs.Store;

/**
 * Dumps everything the import pipeline needs from real OSRS item definitions:
 * name, all model ids (ground/worn/chathead), the authentic 2D icon camera, and
 * flags. One pipe-separated line per item on stdout (fields in header order).
 *
 * Part of the OSRS item import pipeline -- see docs/osrs-item-import.md.
 *
 * Usage: ExtractOsrsItemDefs <osrsCacheDir> <itemId...>
 */
public final class ExtractOsrsItemDefs {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: ExtractOsrsItemDefs <osrsCacheDir> <itemId...>");
        }
        System.out.println("#osrsId|name|ground|male0|male1|female0|female1|maleHead|femaleHead|zoom2d|xan2d|yan2d|zan2d|xOffset2d|yOffset2d|maleOffset|femaleOffset|stackable|tradeable");
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            ItemManager items = new ItemManager(store);
            items.load();
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                ItemDefinition def = items.getItem(id);
                if (def == null) {
                    throw new IllegalStateException("OSRS item " + id + " not found in cache");
                }
                System.out.println(id + "|" + def.name + "|" + def.inventoryModel + "|"
                        + def.maleModel0 + "|" + def.maleModel1 + "|"
                        + def.femaleModel0 + "|" + def.femaleModel1 + "|"
                        + def.maleHeadModel + "|" + def.femaleHeadModel + "|"
                        + def.zoom2d + "|" + def.xan2d + "|" + def.yan2d + "|" + def.zan2d + "|"
                        + def.xOffset2d + "|" + def.yOffset2d + "|"
                        + def.maleOffset + "|" + def.femaleOffset + "|"
                        + def.stackable + "|" + def.tradeable);
            }
        }
    }
}
