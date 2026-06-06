import java.io.File;
import java.util.HashSet;
import java.util.Set;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.Js5Index;

public final class FindItemModelRefs {
    public static void main(String[] args) throws Exception {
        File cacheDir = new File(args[0]);
        Set<Integer> targets = new HashSet<>();
        for (int i = 1; i < args.length; i++) targets.add(Integer.parseInt(args[i]));

        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        byte[] packedIndex = master.read(19);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));
        Cache items = new Cache(19, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx19"), "r", Long.MAX_VALUE), 6000, 0), 1000000);

        int hits = 0;
        for (int group = 0; group < index.groupCapacities.length; group++) {
            if (index.groupCapacities[group] <= 0) continue;
            byte[] packed = items.read(group);
            if (packed == null) continue;
            byte[][] files = readGroupFiles(packed, index, group);
            for (int file = 0; file < files.length; file++) {
                byte[] def = files[file];
                if (def == null) continue;
                int itemId = (group << 8) | file;
                String name = "";
                int pos = 0;
                while (pos < def.length) {
                    int opcode = def[pos++] & 0xFF;
                    if (opcode == 0) break;
                    if (opcode == 2) {
                        int start = pos;
                        while (pos < def.length && def[pos++] != 0) {}
                        name = new String(def, start, pos - start - 1);
                    } else if (isModelOpcode(opcode)) {
                        int model = ((def[pos] & 0xFF) << 8) | (def[pos + 1] & 0xFF);
                        if (targets.contains(model)) {
                            System.out.println("item " + itemId + " " + name + " opcode " + opcode + " model " + model);
                            hits++;
                        }
                        pos += opcode == 23 || opcode == 25 ? wornLen(def, pos) : 2;
                    } else {
                        pos = skip(def, pos, opcode);
                    }
                }
            }
        }
        System.out.println("hits=" + hits);
    }

    private static boolean isModelOpcode(int opcode) {
        return opcode == 1 || opcode == 23 || opcode == 24 || opcode == 25 || opcode == 26 ||
                opcode == 78 || opcode == 79 || opcode == 90 || opcode == 91 || opcode == 92 || opcode == 93;
    }

    private static int wornLen(byte[] def, int pos) {
        if (pos + 2 >= def.length) return 2;
        int next = def[pos + 2] & 0xFF;
        return isLikelyOpcode(next) ? 2 : 3;
    }

    private static boolean isLikelyOpcode(int opcode) {
        return opcode == 0 || opcode == 1 || opcode == 2 || opcode == 4 || opcode == 5 || opcode == 6 ||
                opcode == 7 || opcode == 8 || opcode == 11 || opcode == 12 || opcode == 16 ||
                opcode == 23 || opcode == 24 || opcode == 25 || opcode == 26 || opcode == 40 ||
                opcode == 41 || opcode == 65 || opcode == 78 || opcode == 79 || opcode == 90 ||
                opcode == 91 || opcode == 92 || opcode == 93 || opcode == 95 || opcode == 96 || opcode == 97 ||
                opcode == 98 || opcode == 110 || opcode == 111 || opcode == 112 || opcode == 113 ||
                opcode == 114 || opcode == 115 || opcode == 121 || opcode == 122 || opcode == 125 ||
                opcode == 126 || opcode == 127 || opcode == 128 || opcode == 129 || opcode == 130 ||
                opcode == 132 || opcode == 139 || opcode == 140 || opcode == 145 ||
                opcode == 148 || opcode == 149 || opcode == 249 ||
                (opcode >= 30 && opcode < 40) || (opcode >= 100 && opcode < 110);
    }

    private static int skip(byte[] def, int pos, int opcode) {
        int len;
        if (opcode == 4 || opcode == 5 || opcode == 6 || opcode == 7 || opcode == 8 ||
                opcode == 95 || opcode == 96 || opcode == 97 || opcode == 98 || opcode == 110 || opcode == 111 ||
                opcode == 112 || opcode == 121 || opcode == 122 || opcode == 139 || opcode == 140 ||
                opcode == 145 || opcode == 148 || opcode == 149) {
            len = 2;
        } else if (opcode == 11 || opcode == 16 || opcode == 65) {
            len = 0;
        } else if (opcode == 12) {
            len = 4;
        } else if (opcode == 13 || opcode == 14 || opcode == 27 || opcode == 113 || opcode == 114 || opcode == 115) {
            len = 1;
        } else if ((opcode >= 30 && opcode < 40)) {
            while (pos < def.length && def[pos++] != 0) {}
            return pos;
        } else if (opcode == 40 || opcode == 41) {
            len = 1 + (def[pos] & 0xFF) * 4;
        } else if (opcode == 42) {
            len = 1 + (def[pos] & 0xFF);
        } else if (opcode == 125 || opcode == 126 || opcode == 127 || opcode == 128 || opcode == 129 || opcode == 130) {
            len = 3;
        } else if (opcode == 132) {
            len = 1 + (def[pos] & 0xFF) * 2;
        } else if (opcode >= 100 && opcode < 110) {
            len = 4;
        } else if (opcode == 249) {
            int count = def[pos++] & 0xFF;
            for (int i = 0; i < count; i++) {
                boolean string = def[pos++] != 0;
                pos += 3;
                if (string) while (pos < def.length && def[pos++] != 0) {}
                else pos += 4;
            }
            return pos;
        } else {
            throw new IllegalStateException("opcode " + opcode);
        }
        return pos + len;
    }

    private static byte[][] readGroupFiles(byte[] packed, Js5Index index, int group) throws Exception {
        byte[] uncompressed = Js5Compression.uncompress(stripVersion(packed));
        int count = index.groupSizes[group];
        int capacity = index.groupCapacities[group];
        byte[][] files = new byte[capacity][];
        if (count == 1) {
            files[index.fileIds[group] == null ? 0 : index.fileIds[group][0]] = uncompressed;
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
        for (int i = 0; i < count; i++) logical[i] = new byte[sizes[i]];
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
        for (int i = 0; i < count; i++) files[index.fileIds[group] == null ? i : index.fileIds[group][i]] = logical[i];
        return files;
    }

    private static byte[] stripVersion(byte[] group) {
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }

    private static int readInt(byte[] bytes, int pos) {
        return ((bytes[pos] & 0xFF) << 24) | ((bytes[pos + 1] & 0xFF) << 16) | ((bytes[pos + 2] & 0xFF) << 8) | (bytes[pos + 3] & 0xFF);
    }
}
