import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Compression;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;

public class RestoreArmorWornFromBackup {
    private static final int ITEM_ARCHIVE = 19;
    private static final int[] ITEM_IDS = {14663, 14664, 14665};
    private static final int[] INVENTORY_MODELS = {32794, 32790, 32787};

    private static final class DiskProvider extends Js5ResourceProvider {
        private final Cache master;
        private final Cache archive;

        DiskProvider(File dir) throws Exception {
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            this.master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
            this.archive = new Cache(ITEM_ARCHIVE, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx19"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        }

        public Js5Index fetchIndex() {
            byte[] index = master.read(ITEM_ARCHIVE);
            return index == null ? null : new Js5Index(index, Buffer.crc32(index, index.length));
        }

        public void prefetchGroup(int group) {}
        public int getPercentageComplete(int group) { return 100; }
        public byte[] fetchGroup(int group) { return archive.read(group); }
    }

    public static void main(String[] args) throws Exception {
        File currentCache = new File(args[0]);
        File backupCache = new File(args[1]);
        Js5 backupItems = new Js5(new DiskProvider(backupCache), false, false);

        BufferedFile data = new BufferedFile(new FileOnDisk(new File(currentCache, "main_file_cache.dat2"), "rw", Long.MAX_VALUE), 5200, 0);
        Cache master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(currentCache, "main_file_cache.idx255"), "rw", Long.MAX_VALUE), 6000, 0), 1000000);

        byte[] packedIndex = master.read(ITEM_ARCHIVE);
        int archiveIndexVersion = readTrailingVersion(packedIndex);
        byte[] indexBytes = Js5Compression.uncompress(packedIndex);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));

        for (int n = 0; n < ITEM_IDS.length; n++) {
            int itemId = ITEM_IDS[n];
            int group = itemId >>> 8;
            int file = itemId & 255;
            byte[][] files = readGroupFiles(currentCache, group, index);
            byte[] backupDef = backupItems.fetchFile(group, file);
            files[file] = replaceInventoryModel(backupDef, INVENTORY_MODELS[n]);
            writeMultiFileGroup(currentCache, data, master, group, files, index.groupVersions[group], indexBytes, archiveIndexVersion);
            System.out.println("Restored worn definition for " + itemId + ", inventory model=" + INVENTORY_MODELS[n]);
        }
    }

    private static byte[] replaceInventoryModel(byte[] def, int inventoryModel) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        boolean seen1 = false;
        while (i < def.length) {
            int opcode = def[i++] & 0xFF;
            if (opcode == 0) {
                break;
            }
            if (opcode == 1) {
                out.write(opcode);
                writeShort(out, inventoryModel);
                i += 2;
                out.write(def, i, def.length - i);
                return out.toByteArray();
            }
            out.write(opcode);
            i = copyOpcodePayload(def, i, opcode, out);
        }
        if (!seen1) {
            out.write(1);
            writeShort(out, inventoryModel);
        }
        out.write(0);
        return out.toByteArray();
    }

    private static byte[][] readGroupFiles(File cacheDir, int group, Js5Index index) throws Exception {
        Cache archive = new Cache(ITEM_ARCHIVE,
                new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0),
                new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx19"), "r", Long.MAX_VALUE), 6000, 0),
                1000000);
        byte[] packed = archive.read(group);
        byte[] uncompressed = Js5Compression.uncompress(stripVersion(packed));
        int count = index.groupSizes[group];
        byte[][] files = new byte[index.groupCapacities[group]][];
        int chunks = uncompressed[uncompressed.length - 1] & 0xFF;
        int table = uncompressed.length - 1 - chunks * count * 4;
        int[] sizes = new int[count];
        int pos = table;
        for (int chunk = 0; chunk < chunks; chunk++) {
            int cumulative = 0;
            for (int j = 0; j < count; j++) {
                cumulative += readInt(uncompressed, pos);
                pos += 4;
                sizes[j] += cumulative;
            }
        }
        byte[][] logical = new byte[count][];
        for (int j = 0; j < count; j++) logical[j] = new byte[sizes[j]];
        int[] offsets = new int[count];
        pos = table;
        int dataPos = 0;
        for (int chunk = 0; chunk < chunks; chunk++) {
            int cumulative = 0;
            for (int j = 0; j < count; j++) {
                cumulative += readInt(uncompressed, pos);
                pos += 4;
                System.arraycopy(uncompressed, dataPos, logical[j], offsets[j], cumulative);
                offsets[j] += cumulative;
                dataPos += cumulative;
            }
        }
        for (int j = 0; j < count; j++) {
            int fileId = index.fileIds[group] == null ? j : index.fileIds[group][j];
            files[fileId] = logical[j];
        }
        return files;
    }

    private static void writeMultiFileGroup(File cacheDir, BufferedFile data, Cache master, int group, byte[][] filesById, int version, byte[] indexBytes, int archiveIndexVersion) throws Exception {
        byte[] packedNoVersion = wrapUncompressed(packSingleChunk(compact(filesById)));
        byte[] packed = appendVersion(packedNoVersion, version);
        Cache archive = new Cache(ITEM_ARCHIVE, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx19"), "rw", Long.MAX_VALUE), 6000, 0), 1000000);
        if (!archive.write(group, packed.length, packed)) throw new IllegalStateException("Unable to write group " + group);
        patchGroupChecksum(indexBytes, group, Buffer.crc32(packedNoVersion, packedNoVersion.length));
        byte[] repackedIndex = appendVersion(wrapUncompressed(indexBytes), archiveIndexVersion);
        if (!master.write(ITEM_ARCHIVE, repackedIndex.length, repackedIndex)) throw new IllegalStateException("Unable to write item index");
    }

    private static int copyOpcodePayload(byte[] def, int i, int opcode, ByteArrayOutputStream out) {
        int len;
        if (opcode == 4 || opcode == 5 || opcode == 6 || opcode == 7 || opcode == 8 ||
                opcode == 11 || opcode == 12 || opcode == 16 || opcode == 24 || opcode == 26 ||
                opcode == 65 || opcode == 78 || opcode == 79 || opcode == 90 || opcode == 91 ||
                opcode == 92 || opcode == 93 || opcode == 95 || opcode == 96 || opcode == 97 ||
                opcode == 98 || opcode == 110 || opcode == 111 || opcode == 112 || opcode == 115 ||
                opcode == 121 || opcode == 122 || opcode == 125 || opcode == 126 || opcode == 127 ||
                opcode == 128 || opcode == 129 || opcode == 130 || opcode == 132) {
            switch (opcode) {
                case 11, 16, 65 -> len = 0;
                case 12 -> len = 4;
                case 125, 126, 127, 128, 129, 130 -> len = 3;
                case 132 -> len = 1 + 2 * (def[i] & 0xFF);
                default -> len = 2;
            }
        } else if (opcode == 23 || opcode == 25) {
            len = 3;
        } else if (opcode == 2 || (opcode >= 30 && opcode < 40)) {
            int start = i;
            while (def[i++] != 0) {}
            out.write(def, start, i - start);
            return i;
        } else if (opcode == 40 || opcode == 41) {
            len = 1 + (def[i] & 0xFF) * 4;
        } else if (opcode == 42) {
            len = 1 + (def[i] & 0xFF);
        } else if (opcode >= 100 && opcode < 110) {
            len = 4;
        } else {
            throw new IllegalArgumentException("Unsupported opcode " + opcode);
        }
        out.write(def, i, len);
        return i + len;
    }

    private static byte[][] compact(byte[][] filesById) {
        java.util.List<byte[]> files = new java.util.ArrayList<>();
        for (byte[] file : filesById) if (file != null) files.add(file);
        return files.toArray(new byte[0][]);
    }

    private static byte[] packSingleChunk(byte[][] files) throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (byte[] file : files) data.write(file);
        int previous = 0;
        for (byte[] file : files) {
            writeInt(data, file.length - previous);
            previous = file.length;
        }
        data.write(1);
        return data.toByteArray();
    }

    private static byte[] stripVersion(byte[] group) {
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }

    private static byte[] wrapUncompressed(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        writeInt(out, data.length);
        out.write(data);
        return out.toByteArray();
    }

    private static byte[] appendVersion(byte[] data, int version) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(data);
        out.write((version >>> 8) & 0xFF);
        out.write(version & 0xFF);
        return out.toByteArray();
    }

    private static int readInt(byte[] bytes, int pos) {
        return ((bytes[pos] & 0xFF) << 24) | ((bytes[pos + 1] & 0xFF) << 16) | ((bytes[pos + 2] & 0xFF) << 8) | (bytes[pos + 3] & 0xFF);
    }

    private static int readTrailingVersion(byte[] data) {
        return data.length < 2 ? 0 : ((data[data.length - 2] & 0xFF) << 8) | (data[data.length - 1] & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void patchGroupChecksum(byte[] index, int targetGroup, int checksum) {
        int pos = 0;
        int protocol = index[pos++] & 0xFF;
        if (protocol >= 6) pos += 4;
        boolean names = (index[pos++] & 0xFF) != 0;
        int size = ((index[pos++] & 0xFF) << 8) | (index[pos++] & 0xFF);
        int group = 0;
        int targetIndex = -1;
        for (int i = 0; i < size; i++) {
            group += ((index[pos++] & 0xFF) << 8) | (index[pos++] & 0xFF);
            if (group == targetGroup) targetIndex = i;
        }
        if (names) pos += size * 4;
        int checksumOffset = pos + targetIndex * 4;
        index[checksumOffset] = (byte) (checksum >>> 24);
        index[checksumOffset + 1] = (byte) (checksum >>> 16);
        index[checksumOffset + 2] = (byte) (checksum >>> 8);
        index[checksumOffset + 3] = (byte) checksum;
    }
}
