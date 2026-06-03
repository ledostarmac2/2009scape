import java.io.File;
import java.io.FileOutputStream;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;

public class DumpJs5 {
    private static final class DiskProvider extends Js5ResourceProvider {
        private final Cache master;
        private final Cache archive;
        private final int archiveId;

        DiskProvider(File dir, int archiveId) throws Exception {
            this.archiveId = archiveId;
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            BufferedFile idx255 = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0);
            BufferedFile idx = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + archiveId), "r", Long.MAX_VALUE), 6000, 0);
            this.master = new Cache(255, data, idx255, 1000000);
            this.archive = new Cache(archiveId, data, idx, 1000000);
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
        File cacheDir = new File(args[0]);
        int archiveId = Integer.parseInt(args[1]);
        File outDir = new File(args[2]);
        outDir.mkdirs();
        Js5 js5 = new Js5(new DiskProvider(cacheDir, archiveId), false, false);
        int groups = js5.capacity();
        int written = 0;
        for (int group = 0; group < groups; group++) {
            int[] fileIds;
            try {
                fileIds = js5.getFileIds(group);
            } catch (Throwable ignored) {
                continue;
            }
            if (fileIds == null || fileIds.length == 0) {
                continue;
            }
            for (int fileId : fileIds) {
                byte[] data;
                try {
                    data = js5.fetchFile(group, fileId);
                } catch (Throwable t) {
                    continue;
                }
                if (data == null) {
                    continue;
                }
                File groupDir = new File(outDir, Integer.toString(group));
                groupDir.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(new File(groupDir, fileId + ".bin"))) {
                    fos.write(data);
                }
                written++;
            }
        }
        System.out.println("archive=" + archiveId + " groups=" + groups + " files=" + written);
    }
}
