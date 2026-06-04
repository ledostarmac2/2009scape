import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Compression;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;

public class PatchItemCache {
    private static final int ARCHIVE = 19;
    private static final int ITEM_ID = 14422;
    private static final int SOURCE_VISUAL_ITEM_ID = 4587;

    private static final class DiskProvider extends Js5ResourceProvider {
        private final Cache master;
        private final Cache archive;

        DiskProvider(File dir) throws Exception {
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            BufferedFile idx255 = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0);
            BufferedFile idx = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + ARCHIVE), "r", Long.MAX_VALUE), 6000, 0);
            this.master = new Cache(255, data, idx255, 1000000);
            this.archive = new Cache(ARCHIVE, data, idx, 1000000);
        }

        public Js5Index fetchIndex() {
            byte[] index = master.read(ARCHIVE);
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
        Path backupDir = Path.of(args[1]);
        Files.createDirectories(backupDir);
        backup(cacheDir, backupDir, "main_file_cache.dat2");
        backup(cacheDir, backupDir, "main_file_cache.idx19");
        backup(cacheDir, backupDir, "main_file_cache.idx255");

        DiskProvider provider = new DiskProvider(cacheDir);
        Js5 js5 = new Js5(provider, false, false);
        Js5Index index = provider.fetchIndex();
        int group = ITEM_ID >>> 8;
        int file = ITEM_ID & 255;

        int groupSize = index.groupSizes[group];
        byte[][] files = new byte[groupSize][];
        int[] fileIds = js5.getFileIds(group);
        for (int logical = 0; logical < groupSize; logical++) {
            int fileId = fileIds == null ? logical : fileIds[logical];
            files[logical] = js5.fetchFile(group, fileId);
        }

        byte[] source = js5.fetchFile(SOURCE_VISUAL_ITEM_ID >>> 8, SOURCE_VISUAL_ITEM_ID & 255);
        boolean replaced = false;
        for (int logical = 0; logical < groupSize; logical++) {
            int fileId = fileIds == null ? logical : fileIds[logical];
            if (fileId == file) {
                files[logical] = renameItem(source, "Emberblade");
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            throw new IllegalStateException("Target file " + file + " not present in group " + group);
        }

        byte[] unpackedGroup = packSingleChunk(files);
        byte[] packedGroupWithoutVersion = wrapUncompressed(unpackedGroup);
        int version = index.groupVersions[group];
        byte[] packedGroup = appendVersion(packedGroupWithoutVersion, version);

        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "rw", Long.MAX_VALUE), 5200, 0);
        BufferedFile idx = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + ARCHIVE), "rw", Long.MAX_VALUE), 6000, 0);
        Cache archiveCache = new Cache(ARCHIVE, data, idx, 1000000);
        if (!archiveCache.write(group, packedGroup.length, packedGroup)) {
            throw new IllegalStateException("Unable to write archive group " + group);
        }

        BufferedFile idx255 = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx255"), "rw", Long.MAX_VALUE), 6000, 0);
        Cache master = new Cache(255, data, idx255, 1000000);
        byte[] masterIndexPacked = master.read(ARCHIVE);
        byte[] masterIndex = Js5Compression.uncompress(masterIndexPacked);
        patchGroupChecksum(masterIndex, group, Buffer.crc32(packedGroupWithoutVersion, packedGroupWithoutVersion.length));
        byte[] repackedMasterIndex = wrapUncompressed(masterIndex);
        if (!master.write(ARCHIVE, repackedMasterIndex.length, repackedMasterIndex)) {
            throw new IllegalStateException("Unable to write master index for archive " + ARCHIVE);
        }

        System.out.println("Patched item " + ITEM_ID + " cache def at archive " + ARCHIVE + ", group " + group + ", file " + file);
    }

    private static void backup(File cacheDir, Path backupDir, String name) throws Exception {
        Path target = backupDir.resolve(name);
        if (!Files.exists(target)) {
            Files.copy(new File(cacheDir, name).toPath(), target);
        }
    }

    private static byte[] renameItem(byte[] def, String name) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while (i < def.length) {
            int opcode = def[i++] & 0xFF;
            out.write(opcode);
            if (opcode == 0) {
                break;
            }
            if (opcode == 2) {
                out.write(name.getBytes("Cp1252"));
                out.write(0);
                while (i < def.length && def[i++] != 0) {
                    // Skip old null-terminated name.
                }
                continue;
            }
            i = copyOpcodePayload(def, i, opcode, out);
        }
        return out.toByteArray();
    }

    private static int copyOpcodePayload(byte[] def, int i, int opcode, ByteArrayOutputStream out) {
        int len;
        if (opcode == 1 || opcode == 4 || opcode == 5 || opcode == 6 || opcode == 7 || opcode == 8 ||
                opcode == 11 || opcode == 12 || opcode == 16 || opcode == 23 || opcode == 24 ||
                opcode == 25 || opcode == 26 || opcode == 78 || opcode == 79 || opcode == 90 ||
                opcode == 91 || opcode == 92 || opcode == 93 || opcode == 95 || opcode == 96 ||
                opcode == 97 || opcode == 98 || opcode == 110 || opcode == 111 || opcode == 112 ||
                opcode == 115 || opcode == 121 || opcode == 122 || opcode == 125 || opcode == 126 ||
                opcode == 127 || opcode == 128 || opcode == 129 || opcode == 130 || opcode == 132 ||
                opcode == 65) {
            len = switch (opcode) {
                case 11, 16, 65 -> 0;
                case 12 -> 4;
                case 125, 126 -> 3;
                case 127, 128, 129, 130 -> 3;
                case 132 -> 1 + 2 * (def[i] & 0xFF);
                default -> 2;
            };
        } else if ((opcode >= 30 && opcode < 35) || (opcode >= 35 && opcode < 40)) {
            int start = i;
            while (def[i++] != 0) {
                // null-terminated option string
            }
            out.write(def, start, i - start);
            return i;
        } else if (opcode == 40 || opcode == 41) {
            int count = def[i] & 0xFF;
            len = 1 + count * 4;
        } else if (opcode == 42) {
            int count = def[i] & 0xFF;
            len = 1 + count;
        } else if (opcode >= 100 && opcode < 110) {
            len = 4;
        } else if (opcode == 249) {
            int count = def[i] & 0xFF;
            int pos = i + 1;
            for (int n = 0; n < count; n++) {
                boolean string = def[pos++] != 0;
                pos += 3;
                if (string) {
                    while (def[pos++] != 0) {
                    }
                } else {
                    pos += 4;
                }
            }
            len = pos - i;
        } else {
            throw new IllegalArgumentException("Unsupported opcode " + opcode);
        }
        out.write(def, i, len);
        return i + len;
    }

    private static byte[] packSingleChunk(byte[][] files) throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (byte[] file : files) {
            data.write(file == null ? new byte[0] : file);
        }
        int previous = 0;
        for (byte[] file : files) {
            int length = file == null ? 0 : file.length;
            writeInt(data, length - previous);
            previous = length;
        }
        data.write(1);
        return data.toByteArray();
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

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void patchGroupChecksum(byte[] index, int targetGroup, int checksum) {
        int pos = 0;
        int protocol = index[pos++] & 0xFF;
        if (protocol >= 6) {
            pos += 4;
        }
        boolean names = (index[pos++] & 0xFF) != 0;
        int size = ((index[pos++] & 0xFF) << 8) | (index[pos++] & 0xFF);
        int[] groups = new int[size];
        int group = 0;
        int targetIndex = -1;
        for (int i = 0; i < size; i++) {
            group += ((index[pos++] & 0xFF) << 8) | (index[pos++] & 0xFF);
            groups[i] = group;
            if (group == targetGroup) {
                targetIndex = i;
            }
        }
        if (targetIndex < 0) {
            throw new IllegalArgumentException("Group not found in index: " + targetGroup);
        }
        if (names) {
            pos += size * 4;
        }
        int checksumOffset = pos + targetIndex * 4;
        index[checksumOffset] = (byte) (checksum >>> 24);
        index[checksumOffset + 1] = (byte) (checksum >>> 16);
        index[checksumOffset + 2] = (byte) (checksum >>> 8);
        index[checksumOffset + 3] = (byte) checksum;
    }
}
