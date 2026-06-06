import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.Js5Index;

public final class PatchItemIconCameras {
    private static final int ITEM_ARCHIVE = 19;
    private static final int KEEP_OFFSET = Integer.MIN_VALUE;

    public static void main(String[] args) throws Exception {
        File cacheDir = new File(args.length == 0 ? "game/data/cache" : args[0]);
        int[][] patches = {
                {14422, 1570, 400, 768, 128, KEEP_OFFSET, KEEP_OFFSET},
                {14545, 1570, 400, 768, 128, KEEP_OFFSET, KEEP_OFFSET},
                {14546, 1570, 400, 768, 128, KEEP_OFFSET, KEEP_OFFSET},
                {14656, 1570, 400, 768, 128, KEEP_OFFSET, KEEP_OFFSET},
                {14666, 1570, 400, 768, 128, KEEP_OFFSET, KEEP_OFFSET},
                {14547, 650, 344, 1152, 704, 0, -20}
        };
        for (int[] patch : patches) {
            patchItem(cacheDir, patch[0], patch[1], patch[2], patch[3], patch[4], patch[5], patch[6]);
        }
    }

    private static void patchItem(File cacheDir, int itemId, int zoom, int xan, int yan, int zan, int xOffset, int yOffset) throws Exception {
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "rw", Long.MAX_VALUE), 5200, 0);
        Cache master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx255"), "rw", Long.MAX_VALUE), 6000, 0), 1000000);
        byte[] packedIndex = master.read(ITEM_ARCHIVE);
        int archiveIndexVersion = readTrailingVersion(packedIndex);
        byte[] indexBytes = Js5Compression.uncompress(packedIndex);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));
        int group = itemId >>> 8;
        int file = itemId & 255;
        byte[][] files = readGroupFiles(cacheDir, ITEM_ARCHIVE, group, index);
        if (file >= files.length || files[file] == null) {
            throw new IllegalStateException("Missing item definition " + itemId);
        }
        files[file] = patchDefinition(files[file], zoom, xan, yan, zan, xOffset, yOffset);
        writeMultiFileGroup(cacheDir, data, master, ITEM_ARCHIVE, group, files, index.groupVersions[group], indexBytes, archiveIndexVersion);
        System.out.println("item " + itemId + " camera zoom=" + zoom + " xan=" + xan + " yan=" + yan + " zan=" + zan +
                (xOffset == KEEP_OFFSET ? "" : " xOffset=" + xOffset + " yOffset=" + yOffset));
    }

    private static byte[] patchDefinition(byte[] def, int zoom, int xan, int yan, int zan, int xOffset, int yOffset) {
        byte[] out = def.clone();
        int i = 0;
        int terminator = -1;
        boolean sawZoom = false;
        boolean sawXan = false;
        boolean sawYan = false;
        boolean sawZan = false;
        boolean sawXOffset = false;
        boolean sawYOffset = false;
        while (i < out.length) {
            int opcode = out[i++] & 0xFF;
            if (opcode == 0) {
                terminator = i - 1;
                break;
            }
            if (opcode == 4 || opcode == 5 || opcode == 6 || opcode == 95 ||
                    (opcode == 7 && xOffset != KEEP_OFFSET) || (opcode == 8 && yOffset != KEEP_OFFSET)) {
                int value = opcode == 4 ? zoom : opcode == 5 ? xan : opcode == 6 ? yan :
                        opcode == 7 ? encodeSignedShort(xOffset) : opcode == 8 ? encodeSignedShort(yOffset) : zan;
                writeShort(out, i, value);
                sawZoom |= opcode == 4;
                sawXan |= opcode == 5;
                sawYan |= opcode == 6;
                sawZan |= opcode == 95;
                sawXOffset |= opcode == 7;
                sawYOffset |= opcode == 8;
                i += 2;
            } else {
                i = skipOpcodePayload(out, i, opcode);
            }
        }
        if (!sawZoom || !sawXan || !sawYan) {
            throw new IllegalStateException("Item definition is missing one or more icon camera opcodes");
        }
        if (terminator < 0) {
            throw new IllegalStateException("Item definition has no terminator");
        }
        int extra = (sawZan ? 0 : 3) +
                (xOffset == KEEP_OFFSET || sawXOffset ? 0 : 3) +
                (yOffset == KEEP_OFFSET || sawYOffset ? 0 : 3);
        if (extra == 0) {
            return out;
        }
        byte[] expanded = new byte[out.length + extra];
        System.arraycopy(out, 0, expanded, 0, terminator);
        int pos = terminator;
        if (!sawZan) {
            expanded[pos++] = 95;
            writeShort(expanded, pos, zan);
            pos += 2;
        }
        if (xOffset != KEEP_OFFSET && !sawXOffset) {
            expanded[pos++] = 7;
            writeShort(expanded, pos, encodeSignedShort(xOffset));
            pos += 2;
        }
        if (yOffset != KEEP_OFFSET && !sawYOffset) {
            expanded[pos++] = 8;
            writeShort(expanded, pos, encodeSignedShort(yOffset));
            pos += 2;
        }
        System.arraycopy(out, terminator, expanded, pos, out.length - terminator);
        return expanded;
    }

    private static int encodeSignedShort(int value) {
        return value < 0 ? value + 65536 : value;
    }

    private static int skipOpcodePayload(byte[] def, int i, int opcode) {
        int len;
        if (opcode == 1 || opcode == 4 || opcode == 5 || opcode == 6 || opcode == 7 || opcode == 8 ||
                opcode == 12 || opcode == 23 || opcode == 24 || opcode == 25 || opcode == 26 ||
                opcode == 78 || opcode == 79 || opcode == 90 || opcode == 91 || opcode == 92 || opcode == 93 ||
                opcode == 95 || opcode == 97 || opcode == 98 || opcode == 110 || opcode == 111 || opcode == 112 ||
                opcode == 121 || opcode == 122 || opcode == 148 || opcode == 149) {
            len = opcode == 12 ? 4 : 2;
        } else if (opcode == 11 || opcode == 16 || opcode == 65) {
            len = 0;
        } else if (opcode == 13 || opcode == 14 || opcode == 27 || opcode == 113 || opcode == 114 || opcode == 115) {
            len = 1;
        } else if ((opcode >= 30 && opcode < 40) || opcode == 2) {
            while (i < def.length && def[i++] != 0) {
            }
            return i;
        } else if (opcode == 40 || opcode == 41) {
            len = 1 + (def[i] & 0xFF) * 4;
        } else if (opcode == 125 || opcode == 126 || opcode == 127 || opcode == 128 || opcode == 129 || opcode == 130) {
            len = 3;
        } else if (opcode == 132) {
            len = 1 + (def[i] & 0xFF) * 2;
        } else if (opcode >= 100 && opcode < 110) {
            len = 4;
        } else {
            throw new IllegalStateException("Unsupported item opcode while patching icon camera: " + opcode);
        }
        return i + len;
    }

    private static byte[][] readGroupFiles(File cacheDir, int archiveId, int group, Js5Index index) throws Exception {
        Cache archive = new Cache(archiveId,
                new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0),
                new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + archiveId), "r", Long.MAX_VALUE), 6000, 0),
                1000000);
        byte[] packed = archive.read(group);
        byte[] uncompressed = Js5Compression.uncompress(stripVersion(packed));
        int count = index.groupSizes[group];
        int capacity = index.groupCapacities[group];
        if (index.fileIds[group] != null) {
            for (int fileId : index.fileIds[group]) {
                capacity = Math.max(capacity, fileId + 1);
            }
        }
        byte[][] files = new byte[capacity][];
        if (count == 1) {
            int fileId = index.fileIds[group] == null ? 0 : index.fileIds[group][0];
            files[fileId] = uncompressed;
            return files;
        }
        int chunks = uncompressed[uncompressed.length - 1] & 0xFF;
        int table = uncompressed.length - 1 - chunks * count * 4;
        int[] sizes = new int[count];
        int pos = table;
        for (int chunk = 0; chunk < chunks; chunk++) {
            int cumulative = 0;
            for (int i = 0; i < count; i++) {
                cumulative += readInt(uncompressed, pos);
                pos += 4;
                sizes[i] += cumulative;
            }
        }
        byte[][] logical = new byte[count][];
        for (int i = 0; i < count; i++) {
            logical[i] = new byte[sizes[i]];
        }
        int[] offsets = new int[count];
        pos = table;
        int dataPos = 0;
        for (int chunk = 0; chunk < chunks; chunk++) {
            int cumulative = 0;
            for (int i = 0; i < count; i++) {
                cumulative += readInt(uncompressed, pos);
                pos += 4;
                System.arraycopy(uncompressed, dataPos, logical[i], offsets[i], cumulative);
                offsets[i] += cumulative;
                dataPos += cumulative;
            }
        }
        for (int i = 0; i < count; i++) {
            int fileId = index.fileIds[group] == null ? i : index.fileIds[group][i];
            files[fileId] = logical[i];
        }
        return files;
    }

    private static void writeMultiFileGroup(File cacheDir, BufferedFile data, Cache master, int archiveId, int group, byte[][] filesById, int version, byte[] indexBytes, int archiveIndexVersion) throws Exception {
        byte[] packedNoVersion = wrapUncompressed(packSingleChunk(compact(filesById)));
        byte[] packed = appendVersion(packedNoVersion, version);
        Cache archive = new Cache(archiveId, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + archiveId), "rw", Long.MAX_VALUE), 6000, 0), 1000000);
        if (!archive.write(group, packed.length, packed)) {
            throw new IllegalStateException("Unable to write archive " + archiveId + " group " + group);
        }
        patchGroupChecksum(indexBytes, group, Buffer.crc32(packedNoVersion, packedNoVersion.length));
        byte[] repackedIndex = appendVersion(wrapGzip(indexBytes), archiveIndexVersion);
        if (!master.write(archiveId, repackedIndex.length, repackedIndex)) {
            throw new IllegalStateException("Unable to write archive index " + archiveId);
        }
    }

    private static byte[][] compact(byte[][] filesById) {
        List<byte[]> files = new ArrayList<>();
        for (byte[] file : filesById) {
            if (file != null) {
                files.add(file);
            }
        }
        return files.toArray(new byte[0][]);
    }

    private static byte[] packSingleChunk(byte[][] files) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int previous = 0;
        for (byte[] file : files) {
            out.write(file);
        }
        for (byte[] file : files) {
            int delta = file.length - previous;
            writeInt(out, delta);
            previous = file.length;
        }
        out.write(1);
        return out.toByteArray();
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

    private static byte[] wrapGzip(byte[] data) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(data);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(2);
        writeInt(out, compressed.size());
        writeInt(out, data.length);
        out.write(compressed.toByteArray());
        return out.toByteArray();
    }

    private static byte[] appendVersion(byte[] data, int version) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(data);
        writeShort(out, version);
        return out.toByteArray();
    }

    private static int readTrailingVersion(byte[] data) {
        return data.length < 2 ? 0 : (((data[data.length - 2] & 0xFF) << 8) | (data[data.length - 1] & 0xFF));
    }

    private static int readInt(byte[] bytes, int pos) {
        return ((bytes[pos] & 0xFF) << 24) | ((bytes[pos + 1] & 0xFF) << 16) | ((bytes[pos + 2] & 0xFF) << 8) | (bytes[pos + 3] & 0xFF);
    }

    private static void patchGroupChecksum(byte[] index, int targetGroup, int checksum) {
        int pos = 0;
        int protocol = index[pos++] & 0xFF;
        if (protocol >= 6) {
            pos += 4;
        }
        boolean names = (index[pos++] & 0xFF) != 0;
        int size = ((index[pos++] & 0xFF) << 8) | (index[pos++] & 0xFF);
        int group = 0;
        int targetIndex = -1;
        for (int i = 0; i < size; i++) {
            group += ((index[pos++] & 0xFF) << 8) | (index[pos++] & 0xFF);
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

    private static void writeShort(byte[] out, int pos, int value) {
        out[pos] = (byte) (value >>> 8);
        out[pos + 1] = (byte) value;
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
}
