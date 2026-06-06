import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Store;

public final class ExtractOsrsModels {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: ExtractOsrsModels <osrsCacheDir> <outDir> <modelId...>");
        }
        File cacheDir = new File(args[0]);
        Path outDir = Path.of(args[1]);
        Files.createDirectories(outDir);
        try (Store store = new Store(cacheDir)) {
            store.load();
            for (int i = 2; i < args.length; i++) {
                int modelId = Integer.parseInt(args[i]);
                Archive archive = store.getIndex(IndexType.MODELS).getArchive(modelId);
                if (archive == null) {
                    throw new IllegalStateException("Missing OSRS model archive " + modelId);
                }
                byte[] packed = store.getStorage().loadArchive(archive);
                FSFile file = archive.getFiles(packed).findFile(0);
                if (file == null) {
                    throw new IllegalStateException("Missing OSRS model file " + modelId);
                }
                ModelDefinition model = new ModelLoader().load(modelId, file.getContents());
                Files.write(outDir.resolve(modelId + ".container"), packed);
                System.out.println("model " + modelId + " vertices=" + model.vertexCount + " faces=" + model.faceCount +
                        " vertexGroups=" + groupSummary(model.packedVertexGroups));
            }
        }
    }

    private static String groupSummary(int[] groups) {
        if (groups == null) {
            return "none";
        }
        Map<Integer, Integer> counts = new TreeMap<>();
        for (int group : groups) {
            counts.merge(group, 1, Integer::sum);
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (out.length() > 0) out.append(',');
            out.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return out.toString();
    }
}
