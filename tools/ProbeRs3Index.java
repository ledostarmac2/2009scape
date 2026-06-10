import java.io.File;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

public final class ProbeRs3Index {
    public static void main(String[] args) throws Exception {
        try (Store store = new Store(new File(args[0]))) {
            store.load();
            System.out.println("indexes loaded=" + store.getIndexes().size());
            for (Index idx : store.getIndexes()) {
                System.out.println(" index " + idx.getId() + " archives=" + idx.getArchives().size());
            }
            Index configs = store.getIndex(IndexType.CONFIGS);
            System.out.println("configs archives=" + configs.getArchives().size());
            for (Archive a : configs.getArchives()) {
                System.out.println("  cfg archive " + a.getArchiveId() + " files=" + a.getFileData().length);
            }
            Index models = store.getIndex(IndexType.MODELS);
            System.out.println("models index id=" + models.getId() + " archives=" + models.getArchives().size());
            if (models.getArchives().size() > 0 && models.getArchives().size() <= 5) {
                for (Archive a : models.getArchives()) {
                    System.out.println("  mdl archive " + a.getArchiveId());
                }
            }
        }
    }
}
