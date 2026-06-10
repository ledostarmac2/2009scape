import java.io.File;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.Component;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;
import rt4.JagString;

public final class DumpBankFull {
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

        public void prefetchGroup(int group) {}
        public int getPercentageComplete(int group) { return 100; }
        public byte[] fetchGroup(int group) { return archive.read(group); }
    }

    private static String ops(Component c) {
        if (c.ops == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.ops.length; i++) {
            if (c.ops[i] != null && c.ops[i].toString().length() > 0) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("[").append(i).append("]").append(c.ops[i]);
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        File cache = new File(args[0]);
        int archive = Integer.parseInt(args[1]);
        int group = Integer.parseInt(args[2]);
        Js5 js5 = new Js5(new DiskProvider(cache, archive), false, false);
        int[] ids = js5.getFileIds(group);
        if (ids == null) { System.out.println("no files"); return; }
        System.out.println("archive=" + archive + " group=" + group + " files=" + ids.length);
        for (int file : ids) {
            byte[] bytes = js5.fetchFile(group, file);
            if (bytes == null || bytes.length == 0) continue;
            Component c = new Component();
            try {
                Buffer b = new Buffer(bytes);
                if ((bytes[0] & 0xFF) == 0xFF) c.decodeIf3(b); else c.decodeIf1(b);
            } catch (Throwable t) {
                System.out.println(file + " decode warning " + t);
                continue;
            }
            String opsStr = ops(c);
            String optStr = (c.option != null && c.option.toString().length() > 0) ? c.option.toString() : "";
            System.out.println("f=" + file + " type=" + c.type + " btnT=" + c.buttonType + " cc=" + c.clientCode +
                    " bX=" + c.baseX + " bY=" + c.baseY + " bW=" + c.baseWidth + " bH=" + c.baseHeight +
                    " lay=" + c.overlayer +
                    " hid=" + c.hidden + " spr=" + c.spriteId +
                    (c.text != null && c.text.toString().length() > 0 ? " text='" + c.text + "'" : "") +
                    (optStr.length() > 0 ? " option='" + optStr + "'" : "") +
                    (opsStr.length() > 0 ? " ops=" + opsStr : ""));
        }
    }
}
