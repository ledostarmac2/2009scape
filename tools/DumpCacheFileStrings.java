import java.io.File;
import java.nio.charset.Charset;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;

public final class DumpCacheFileStrings {
    private static final Charset CP1252 = Charset.forName("Cp1252");

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
        int archive = Integer.parseInt(args[1]);
        int group = Integer.parseInt(args[2]);
        int file = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        Js5 js5 = new Js5(new DiskProvider(cache, archive), false, false);
        byte[] bytes = js5.fetchFile(group, file);
        System.out.println("archive=" + archive + " group=" + group + " file=" + file + " size=" + bytes.length);
        int start = -1;
        for (int i = 0; i <= bytes.length; i++) {
            int b = i < bytes.length ? bytes[i] & 0xFF : 0;
            boolean printable = b >= 32 && b <= 126;
            if (printable && start < 0) {
                start = i;
            }
            if ((!printable || i == bytes.length) && start >= 0) {
                int len = i - start;
                if (len >= 3) {
                    String value = new String(bytes, start, len, CP1252);
                    System.out.println(start + ":" + value);
                }
                start = -1;
            }
        }
    }
}
