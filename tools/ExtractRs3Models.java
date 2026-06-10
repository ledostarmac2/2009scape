import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Store;

/**
 * Dumps RS3 model containers via runelite Store (patched IndexData for RS3 flags).
 * Usage: ExtractRs3Models <rs3CacheDir> <outDir> <modelId...>
 */
public final class ExtractRs3Models {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: ExtractRs3Models <rs3CacheDir> <outDir> <modelId...>");
        }
        File cacheDir = new File(args[0]);
        Path outDir = Path.of(args[1]);
        Files.createDirectories(outDir);
        try (Store store = new Store(cacheDir)) {
            store.load();
            for (int i = 2; i < args.length; i++) {
                int modelId = Integer.parseInt(args[i]);
                Path out = outDir.resolve(modelId + ".container");
                if (Files.exists(out)) {
                    System.out.println("model " + modelId + " already extracted");
                    continue;
                }
                Archive archive = store.getIndex(IndexType.MODELS).getArchive(modelId);
                if (archive == null) {
                    throw new IllegalStateException("Missing RS3 model archive " + modelId);
                }
                byte[] packed = store.getStorage().loadArchive(archive);
                FSFile file = archive.getFiles(packed).findFile(0);
                if (file == null) {
                    throw new IllegalStateException("Missing RS3 model file " + modelId);
                }
                Files.write(out, packed);
                try {
                    ModelDefinition model = new ModelLoader().load(modelId, file.getContents());
                    System.out.println("model " + modelId + " vertices=" + model.vertexCount + " faces=" + model.faceCount
                            + " vskin=" + (model.packedVertexGroups != null));
                } catch (RuntimeException e) {
                    System.out.println("model " + modelId + " saved (decode warning: " + e.getClass().getSimpleName() + ")");
                }
            }
        }
    }
}
