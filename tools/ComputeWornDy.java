import java.io.File;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.models.JagexColor;
import rt4.BufferedFile;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.RawModel;

/** Compute rig-centering wornDy like ImportOsrsItemModels ferocious gloves. */
public final class ComputeWornDy {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: ComputeWornDy <cacheDir> <osrsModelContainer> <osrsModelId> <rigRefId> [extraLift]");
            return;
        }
        File cacheDir = new File(args[0]);
        int osrsId = Integer.parseInt(args[2]);
        int rigRef = Integer.parseInt(args[3]);
        int extraLift = args.length > 4 ? Integer.parseInt(args[4]) : -5;

        ModelDefinition osrs = loadOsrs(args[1], osrsId);
        Bounds osrsBounds = bounds(osrs.vertexX, osrs.vertexY, osrs.vertexZ, osrs.vertexCount);
        Bounds rigBounds = loadRigBounds(cacheDir, rigRef);

        int dy = (rigBounds.minY + rigBounds.maxY - osrsBounds.minY - osrsBounds.maxY) / 2 + extraLift;
        System.out.printf("osrs=%d rig=%d osrsY=%d..%d rigY=%d..%d extraLift=%d wornDy=%d%n",
                osrsId, rigRef, osrsBounds.minY, osrsBounds.maxY, rigBounds.minY, rigBounds.maxY, extraLift, dy);
    }

    private static ModelDefinition loadOsrs(String container, int id) throws Exception {
        byte[] raw = Js5Compression.uncompress(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(container)));
        return new ModelLoader().load(id, raw);
    }

    private static Bounds loadRigBounds(File cacheDir, int id) throws Exception {
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache models = new Cache(7, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx7"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        byte[] packed = models.read(id);
        RawModel m = new RawModel(Js5Compression.uncompress(stripVersion(packed)));
        return bounds(m.vertexX, m.vertexY, m.vertexZ, m.vertexCount);
    }

    private static byte[] stripVersion(byte[] group) {
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}

    private static Bounds bounds(int[] x, int[] y, int[] z, int count) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            minX = Math.min(minX, x[i]); maxX = Math.max(maxX, x[i]);
            minY = Math.min(minY, y[i]); maxY = Math.max(maxY, y[i]);
            minZ = Math.min(minZ, z[i]); maxZ = Math.max(maxZ, z[i]);
        }
        return new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
    }
}
