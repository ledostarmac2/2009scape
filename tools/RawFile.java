import java.io.File;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;

public final class RawFile {
    private static final class DiskProvider extends Js5ResourceProvider {
        private final Cache master, archive; private final int archiveId;
        DiskProvider(File dir, int archiveId) throws Exception {
            this.archiveId = archiveId;
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            this.master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
            this.archive = new Cache(archiveId, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + archiveId), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        }
        public Js5Index fetchIndex() { byte[] i = master.read(archiveId); return i == null ? null : new Js5Index(i, Buffer.crc32(i, i.length)); }
        public void prefetchGroup(int g) {} public int getPercentageComplete(int g){return 100;} public byte[] fetchGroup(int g){return archive.read(g);}
    }
    public static void main(String[] args) throws Exception {
        File cacheDir = new File(args[0]);
        int archive = Integer.parseInt(args[1]);
        int group = Integer.parseInt(args[2]);
        int file = Integer.parseInt(args[3]);
        Js5 js5 = new Js5(new DiskProvider(cacheDir, archive), false, false);
        byte[] b = js5.fetchFile(group, file);
        if (b == null) { System.out.println("null"); return; }
        System.out.println("len=" + b.length);
        StringBuilder hex = new StringBuilder(), asc = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            hex.append(String.format("%02X ", v));
            asc.append(v >= 32 && v < 127 ? (char) v : '.');
            if (i % 16 == 15) { System.out.printf("%04X  %-48s |%s|%n", i - 15, hex, asc); hex.setLength(0); asc.setLength(0); }
        }
        if (hex.length() > 0) System.out.printf("%04X  %-48s |%s|%n", (b.length / 16) * 16, hex, asc);
    }
}
