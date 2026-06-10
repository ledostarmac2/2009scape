import java.io.File;
import net.runelite.cache.ItemManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.fs.Store;

public class DumpOsrsItemCameras {
    public static void main(String[] args) throws Exception {
        int[] ids;
        if (args.length > 1) {
            ids = new int[args.length - 1];
            for (int i = 1; i < args.length; i++) {
                ids[i - 1] = Integer.parseInt(args[i]);
            }
        } else {
            ids = new int[] {20997, 13265, 22324, 22325, 21018, 21021, 21024};
        }
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            ItemManager itemManager = new ItemManager(store);
            itemManager.load();
            for (int id : ids) {
                ItemDefinition item = itemManager.getItem(id);
                System.out.println(id + "," + item.name + "," + item.zoom2d + "," + item.xan2d + "," + item.yan2d + "," + item.zan2d + "," + item.xOffset2d + "," + item.yOffset2d + "," + item.maleOffset + "," + item.femaleOffset);
            }
        }
    }
}
