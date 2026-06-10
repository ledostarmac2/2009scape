import java.io.File;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.definitions.loaders.TextureLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Store;

/** Hex-dump OSRS texture archives and parse rev233 defs. */
public final class DumpOsrsTextureRaw {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: DumpOsrsTextureRaw <osrsCacheDir> <id> [id..]");
            return;
        }
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            TextureLoader loader = new TextureLoader();
            loader.setRev233(true);
            for (int i = 1; i < args.length; i++) {
                int id = Integer.parseInt(args[i]);
                Archive archive = store.getIndex(IndexType.TEXTURES).getArchive(0);
                if (archive == null) {
                    System.out.println("texture " + id + ": MISSING index");
                    continue;
                }
                byte[] packed = store.getStorage().loadArchive(archive);
                FSFile file = archive.getFiles(packed).findFile(id);
                byte[] raw = file.getContents();
                System.out.println("texture " + id + ": " + raw.length + " bytes");
                hex(raw);
                TextureDefinition def = loader.load(id, raw);
                System.out.println("  rev233 sprites=" + java.util.Arrays.toString(def.getFileIds())
                        + " missingColor=" + def.getMissingColor()
                        + " field1778=" + def.field1778
                        + " speed=" + def.getAnimationSpeed()
                        + " dir=" + def.getAnimationDirection());
            }
        }
    }

    private static void hex(byte[] raw) {
        for (int i = 0; i < raw.length; i++) {
            if (i % 16 == 0) System.out.printf("  %04x: ", i);
            System.out.printf("%02x ", raw[i] & 0xFF);
            if (i % 16 == 15) System.out.println();
        }
        if (raw.length % 16 != 0) System.out.println();
    }
}
