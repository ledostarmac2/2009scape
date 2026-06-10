import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import rt4.Js5Compression;

/** Dump OSRS model face colors/textures from extracted containers. */
public final class DumpOsrsModelColors {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        for (int i = 1; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            Path container = dir.resolve(id + ".container");
            if (!Files.exists(container)) {
                System.out.println(id + ": missing " + container);
                continue;
            }
            byte[] raw = Js5Compression.uncompress(Files.readAllBytes(container));
            ModelDefinition m = new ModelLoader().load(id, raw);
            System.out.println(id + ": vertices=" + m.vertexCount + " faces=" + m.faceCount
                    + " numTextureFaces=" + m.numTextureFaces
                    + " faceTextures=" + (m.faceTextures == null ? "null" : "present")
                    + " textureCoords=" + (m.textureCoords == null ? "null" : "present"));
            Map<Integer, Integer> colors = new HashMap<>();
            Map<Integer, Integer> textures = new HashMap<>();
            int textured = 0;
            for (int f = 0; f < m.faceCount; f++) {
                int c = m.faceColors[f] & 0xFFFF;
                colors.merge(c, 1, Integer::sum);
                if (m.faceTextures != null && m.faceTextures[f] >= 0) {
                    textured++;
                    textures.merge(m.faceTextures[f] & 0xFFFF, 1, Integer::sum);
                }
            }
            System.out.println("  colors=" + format(colors));
            if (!textures.isEmpty()) {
                System.out.println("  textures=" + format(textures) + " texturedFaces=" + textured);
            }
            if (m.textureCoords != null) {
                Map<Integer, Integer> coords = new HashMap<>();
                for (byte b : m.textureCoords) {
                    coords.merge(b & 0xFF, 1, Integer::sum);
                }
                System.out.println("  textureCoords=" + format(coords));
            }
            if (m.numTextureFaces > 0 && m.texIndices1 != null) {
                System.out.print("  texIndices=");
                for (int t = 0; t < m.numTextureFaces; t++) {
                    System.out.print(m.texIndices1[t] + "," + m.texIndices2[t] + "," + m.texIndices3[t] + " ");
                }
                System.out.println();
            }
        }
    }

    private static String format(Map<Integer, Integer> map) {
        StringBuilder sb = new StringBuilder("{");
        map.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            if (sb.length() > 1) sb.append(", ");
            sb.append(e.getKey()).append(':').append(e.getValue());
        });
        return sb.append('}').toString();
    }
}
