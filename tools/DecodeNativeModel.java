import java.io.File;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.RawModel;

/** Compare Runelite decode of native old-format models. */
public final class DecodeNativeModel {
    public static void main(String[] args) throws Exception {
        File cacheDir = new File(args[0]);
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache models = new Cache(7, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx7"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        for (int i = 1; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            byte[] packed = models.read(id);
            byte[] raw = Js5Compression.uncompress(strip(packed));
            ModelDefinition m = new ModelLoader().load(id, raw);
            RawModel rm = new RawModel(raw);
            System.out.println(id + " RL faces=" + m.faceCount + " texFaces=" + m.numTextureFaces
                    + " faceTextures=" + (m.faceTextures == null ? "null" : "yes")
                    + " textureCoords=" + (m.textureCoords == null ? "null" : "yes")
                    + " faceRenderTypes=" + (m.faceRenderTypes == null ? "null" : "yes"));
            System.out.println("  Raw texturedCount=" + rm.texturedCount
                    + " triangleTextures=" + (rm.triangleTextures == null ? "null" : "yes")
                    + " triangleInfo=" + (rm.triangleInfo == null ? "null" : "yes")
                    + " triangleTextureIndex=" + (rm.triangleTextureIndex == null ? "null" : "yes"));
            if (m.faceTextures != null) {
                int tex = 0;
                for (int f = 0; f < m.faceCount; f++) {
                    if (m.faceTextures[f] >= 0) tex++;
                }
                System.out.println("  texturedFaces=" + tex);
            }
            if (m.numTextureFaces > 0) {
                System.out.print("  texIndices1=");
                for (int t = 0; t < m.numTextureFaces; t++) {
                    System.out.print(m.texIndices1[t] + "," + m.texIndices2[t] + "," + m.texIndices3[t] + " ");
                }
                System.out.println();
            }
        }
    }

    private static byte[] strip(byte[] group) {
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }
}
