import java.io.File;
import rt4.BufferedFile;
import rt4.Cache;
import rt4.FileOnDisk;

/** Check whether sprite groups exist in archive 8. */
public final class CheckSpriteGroups {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: CheckSpriteGroups <cacheDir> <groupId> [groupId..]");
            return;
        }
        File cacheDir = new File(args[0]);
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache sprites = new Cache(8, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx8"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        for (int i = 1; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            byte[] packed = sprites.read(id);
            System.out.println("sprite " + id + ": " + (packed == null ? "MISSING" : packed.length + "b packed"));
        }
    }
}
