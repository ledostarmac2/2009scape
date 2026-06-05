import java.nio.file.Files;
import java.nio.file.Path;
import rt4.Js5Compression;
import rt4.RawModel;

public class InspectRawModelFile {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(args[0]));
        if (args.length > 1 && args[1].equals("container")) {
            bytes = Js5Compression.uncompress(bytes);
        }
        RawModel model = new RawModel(bytes);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < model.vertexCount; i++) {
            minX = Math.min(minX, model.vertexX[i]);
            maxX = Math.max(maxX, model.vertexX[i]);
            minY = Math.min(minY, model.vertexY[i]);
            maxY = Math.max(maxY, model.vertexY[i]);
            minZ = Math.min(minZ, model.vertexZ[i]);
            maxZ = Math.max(maxZ, model.vertexZ[i]);
        }
        System.out.println("bytes=" + bytes.length + " vertices=" + model.vertexCount + " triangles=" + model.triangleCount +
                " textures=" + model.texturedCount +
                " bounds x=" + minX + ".." + maxX + " y=" + minY + ".." + maxY + " z=" + minZ + ".." + maxZ);
    }
}
