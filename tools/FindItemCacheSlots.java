import java.io.File;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;

public final class FindItemCacheSlots {
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
        int start = Integer.parseInt(args[1]);
        int end = Integer.parseInt(args[2]);
        DiskProvider provider = new DiskProvider(cache, 19);
        Js5Index index = provider.fetchIndex();
        Js5 items = new Js5(provider, false, false);
        for (int id = start; id <= end; id++) {
            int group = id >>> 8;
            int file = id & 255;
            if (group >= index.groupCapacities.length || file >= index.groupCapacities[group]) {
                continue;
            }
            if (!hasFile(index, group, file)) {
                continue;
            }
            byte[] item = items.fetchFile(group, file);
            System.out.println(id + "\t" + (item == null ? "null" : "present") + "\t" + nameOf(item));
        }
    }

    private static boolean hasFile(Js5Index index, int group, int file) {
        if (index.fileIds[group] == null) {
            return file < index.groupSizes[group];
        }
        for (int fileId : index.fileIds[group]) {
            if (fileId == file) {
                return true;
            }
        }
        return false;
    }

    private static String nameOf(byte[] item) {
        if (item == null) {
            return "";
        }
        int pos = 0;
        while (pos < item.length) {
            int opcode = item[pos++] & 0xFF;
            if (opcode == 0) {
                return "";
            }
            if (opcode == 2) {
                int start = pos;
                while (pos < item.length && item[pos++] != 0) {
                }
                return new String(item, start, pos - start - 1);
            }
            pos = skip(item, pos, opcode);
        }
        return "";
    }

    private static int skip(byte[] def, int i, int opcode) {
        if (opcode == 3 || opcode == 9 || (opcode >= 30 && opcode < 40)) {
            while (i < def.length && def[i++] != 0) {
            }
            return i;
        }
        if (opcode == 11 || opcode == 15 || opcode == 16 || opcode == 65) {
            return i;
        }
        if (opcode == 12 || opcode == 44 || opcode == 46 || opcode == 47 || opcode == 49 ||
                opcode == 50 || opcode == 51 || opcode == 52 || opcode == 53 || opcode == 54 ||
                (opcode >= 100 && opcode < 110)) {
            return i + 4;
        }
        if (opcode == 13 || opcode == 14 || opcode == 27 || opcode == 113 || opcode == 114 ||
                opcode == 115) {
            return i + 1;
        }
        if (opcode == 23 || opcode == 25 || opcode == 45 || opcode == 48) {
            return i + 3;
        }
        if (opcode == 40 || opcode == 41) {
            int count = def[i] & 0xFF;
            return i + 1 + count * 4;
        }
        if (opcode == 42) {
            return i + 1 + (def[i] & 0xFF);
        }
        if (opcode == 43) {
            i++;
            while (i < def.length && (def[i++] & 0xFF) != 0) {
                while (i < def.length && def[i++] != 0) {
                }
            }
            return i;
        }
        if (opcode == 249) {
            int count = def[i++] & 0xFF;
            for (int n = 0; n < count; n++) {
                boolean string = def[i++] != 0;
                i += 3;
                if (string) {
                    while (i < def.length && def[i++] != 0) {
                    }
                } else {
                    i += 4;
                }
            }
            return i;
        }
        if (opcode == 132) {
            return i + 1 + 2 * (def[i] & 0xFF);
        }
        return i + 2;
    }
}
