import java.io.File;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;
import rt4.RawModel;

public class VerifyEmberbladeVisual {
    private static final class DiskProvider extends Js5ResourceProvider {
        private final Cache master;
        private final Cache archive;
        private final int archiveId;

        DiskProvider(File dir, int archiveId) throws Exception {
            this.archiveId = archiveId;
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            this.master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
            this.archive = new Cache(archiveId, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + archiveId), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        }

        public Js5Index fetchIndex() {
            byte[] index = master.read(archiveId);
            return index == null ? null : new Js5Index(index, Buffer.crc32(index, index.length));
        }

        public void prefetchGroup(int group) {
        }

        public int getPercentageComplete(int group) {
            return 100;
        }

        public byte[] fetchGroup(int group) {
            return archive.read(group);
        }
    }

    public static void main(String[] args) throws Exception {
        File cache = new File(args[0]);
        Js5 models = new Js5(new DiskProvider(cache, 7), false, false);
        byte[] modelBytes = models.fetchFile(14422, 0);
        RawModel model = new RawModel(modelBytes);
        System.out.println("model bytes=" + modelBytes.length + " vertices=" + model.vertexCount + " triangles=" + model.triangleCount);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < model.vertexCount; i++) {
            minX = Math.min(minX, model.vertexX[i]); maxX = Math.max(maxX, model.vertexX[i]);
            minY = Math.min(minY, model.vertexY[i]); maxY = Math.max(maxY, model.vertexY[i]);
            minZ = Math.min(minZ, model.vertexZ[i]); maxZ = Math.max(maxZ, model.vertexZ[i]);
        }
        System.out.println("bounds x=" + minX + ".." + maxX + " y=" + minY + ".." + maxY + " z=" + minZ + ".." + maxZ);

        Js5 items = new Js5(new DiskProvider(cache, 19), false, false);
        byte[] itemBytes = items.fetchFile(56, 86);
        System.out.print("item def=");
        for (int i = 0; i < itemBytes.length; i++) {
            System.out.printf("%02X", itemBytes[i] & 0xFF);
            if (i + 1 < itemBytes.length) System.out.print(" ");
        }
        System.out.println();
    }
}
