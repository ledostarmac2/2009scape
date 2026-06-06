import java.io.File;
import java.nio.charset.StandardCharsets;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;

public final class SearchCacheFiles {
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
        int maxArchive = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        String[] needles = new String[args.length - 2];
        for (int i = 2; i < args.length; i++) {
            needles[i - 2] = args[i];
        }
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cache, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(cache, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        for (int archive = 0; archive <= maxArchive; archive++) {
            if (master.read(archive) == null) {
                continue;
            }
            Js5 js5 = new Js5(new DiskProvider(cache, archive), false, false);
            for (int group = 0; group < js5.capacity(); group++) {
                if (!js5.isGroupReady(group)) {
                    continue;
                }
                int[] files = js5.getFileIds(group);
                try {
                    if (files == null) {
                        byte[] bytes = js5.fetchFile(group);
                        search(archive, group, 0, bytes, needles);
                        continue;
                    }
                    for (int file : files) {
                        byte[] bytes = js5.fetchFile(group, file);
                        search(archive, group, file, bytes, needles);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void search(int archive, int group, int file, byte[] bytes, String[] needles) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        String iso = new String(bytes, StandardCharsets.ISO_8859_1);
        for (String needle : needles) {
            if (iso.contains(needle)) {
                System.out.println("archive=" + archive + " group=" + group + " file=" + file +
                        " needle='" + needle + "' size=" + bytes.length);
                return;
            }
        }
    }
}
