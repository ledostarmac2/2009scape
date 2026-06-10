import java.io.File;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Store;

/** Report OSRS sprite group raw byte length. */
public final class DumpOsrsSpriteRaw {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: DumpOsrsSpriteRaw <osrsCacheDir> <id> [id..]");
            return;
        }
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive archive = store.getIndex(IndexType.SPRITES).getArchive(id);
                if (archive == null) {
                    System.out.println("sprite " + id + ": MISSING");
                    continue;
                }
                byte[] packed = store.getStorage().loadArchive(archive);
                FSFile file = archive.getFiles(packed).findFile(0);
                byte[] raw = file.getContents();
                System.out.println("sprite " + id + ": " + raw.length + " bytes");
            }
        }
    }
}
