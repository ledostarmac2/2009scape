import java.io.File;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.Js5Index;

/**
 * Copies client script groups from a source cache into the live server cache when
 * the server cache index references a group but has no on-disk payload yet.
 */
public final class CopyMissingScriptGroups {
    private static final int SCRIPT_ARCHIVE = 12;

    public static void main(String[] args) throws Exception {
        File targetDir = new File(args[0]);
        File sourceDir = new File(args[1]);
        int[] groups = new int[args.length - 2];
        for (int i = 2; i < args.length; i++) {
            groups[i - 2] = Integer.parseInt(args[i]);
        }

        BufferedFile targetData = open(targetDir, "rw");
        BufferedFile targetIdx255 = openIdx(targetDir, 255, "rw");
        BufferedFile targetIdx = openIdx(targetDir, SCRIPT_ARCHIVE, "rw");
        Cache targetMaster = new Cache(255, targetData, targetIdx255, 1000000);
        Cache targetArchive = new Cache(SCRIPT_ARCHIVE, targetData, targetIdx, 1000000);

        BufferedFile sourceData = open(sourceDir, "r");
        BufferedFile sourceIdx = openIdx(sourceDir, SCRIPT_ARCHIVE, "r");
        Cache sourceArchive = new Cache(SCRIPT_ARCHIVE, sourceData, sourceIdx, 1000000);

        byte[] packedIndex = targetMaster.read(SCRIPT_ARCHIVE);
        int archiveIndexVersion = readTrailingVersion(packedIndex);
        byte[] indexBytes = Js5Compression.uncompress(packedIndex);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));

        for (int group : groups) {
            if (targetArchive.read(group) != null) {
                System.out.println("group " + group + ": already present in target cache");
                continue;
            }
            byte[] packed = sourceArchive.read(group);
            if (packed == null) {
                throw new IllegalStateException("group " + group + " missing in source cache " + sourceDir);
            }
            byte[] packedNoVersion = stripVersion(packed);
            if (!targetArchive.write(group, packed.length, packed)) {
                throw new IllegalStateException("unable to write script group " + group);
            }
            patchGroupChecksum(indexBytes, group, Buffer.crc32(packedNoVersion, packedNoVersion.length));
            System.out.println("group " + group + ": copied from " + sourceDir);
        }

        byte[] repackedIndex = appendVersion(wrapGzip(indexBytes), archiveIndexVersion);
        if (!targetMaster.write(SCRIPT_ARCHIVE, repackedIndex.length, repackedIndex)) {
            throw new IllegalStateException("unable to write script archive index");
        }
    }

    private static BufferedFile open(File dir, String mode) throws Exception {
        return new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), mode, Long.MAX_VALUE), 5200, 0);
    }

    private static BufferedFile openIdx(File dir, int archive, String mode) throws Exception {
        return new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + archive), mode, Long.MAX_VALUE), 6000, 0);
    }

    private static byte[] stripVersion(byte[] group) {
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }

    private static int readTrailingVersion(byte[] data) {
        return data.length < 2 ? 0 : ((data[data.length - 2] & 0xFF) << 8) | (data[data.length - 1] & 0xFF);
    }

    private static byte[] wrapGzip(byte[] data) throws Exception {
        java.io.ByteArrayOutputStream compressed = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(compressed)) {
            gzip.write(data);
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(2);
        writeInt(out, compressed.size());
        writeInt(out, data.length);
        out.write(compressed.toByteArray());
        return out.toByteArray();
    }

    private static byte[] appendVersion(byte[] data, int version) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(data);
        out.write((version >>> 8) & 0xFF);
        out.write(version & 0xFF);
        return out.toByteArray();
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

    private static void writeInt(java.io.ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
