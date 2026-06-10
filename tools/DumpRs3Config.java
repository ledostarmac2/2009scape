import com.displee.cache.CacheLibrary;
import com.displee.cache.index.Index;
import com.displee.cache.index.archive.Archive;
import com.displee.cache.index.archive.file.File;
import java.nio.charset.StandardCharsets;

/** Dump RS3 config bytes: DumpRs3Config <cacheDir> <idx> <arch> <file> */
public final class DumpRs3Config {
    public static void main(String[] args) throws Exception {
        int idx = Integer.parseInt(args[1]);
        int arch = Integer.parseInt(args[2]);
        int fid = Integer.parseInt(args[3]);
        try (CacheLibrary lib = CacheLibrary.create(args[0])) {
            Index index = lib.index(idx);
            Archive archive = index.archive(arch, false);
            File file = archive.file(fid);
            byte[] data = file.getData();
            System.out.println("idx=" + idx + " arch=" + arch + " file=" + fid + " len=" + data.length);
            System.out.println("hex: " + hex(data, Math.min(120, data.length)));
            System.out.println("parsedName: " + parseName(data));
            System.out.println("manual: " + manual(data));
        }
    }

    private static String parseName(byte[] data) {
        int i = 0;
        while (i < data.length) {
            int op = data[i++] & 0xFF;
            if (op == 0) break;
            if (op == 2) {
                int s = i;
                while (i < data.length && data[i] != 0) i++;
                return new String(data, s, i - s, StandardCharsets.UTF_8);
            }
            i = skip(data, i, op);
            if (i < 0) return null;
        }
        return null;
    }

    private static String manual(byte[] data) {
        int inv = -1, m0 = -1, f0 = -1, zoom = -1;
        String name = null;
        int i = 0;
        while (i < data.length) {
            int op = data[i++] & 0xFF;
            if (op == 0) break;
            switch (op) {
                case 1 -> { inv = u16(data, i); i += 2; }
                case 2 -> { name = str(data, i); i = skipStr(data, i); }
                case 4 -> { zoom = u16(data, i); i += 2; }
                case 23 -> { m0 = u16(data, i); i += 2; }
                case 24 -> { f0 = u16(data, i); i += 2; }
                default -> { i = skip(data, i, op); if (i < 0) return "fail op=" + op; }
            }
        }
        return "name=" + name + " inv=" + inv + " m0=" + m0 + " f0=" + f0 + " zoom=" + zoom;
    }

    private static int skip(byte[] d, int i, int op) {
        switch (op) {
            case 3, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39 -> { return skipStr(d, i); }
            case 4, 5, 6, 7, 8, 23, 24, 25, 26, 78, 79, 90, 91, 92, 93, 94, 95, 110, 111, 112, 113, 114, 115, 139, 140, 148, 149 -> { return i + 2; }
            case 11, 13, 16, 65 -> { return i; }
            case 12 -> { return i + 4; }
            case 44, 46, 47, 49, 50, 51, 52, 53, 54 -> { return i + 4; }
            case 45, 48 -> { return i + 5; }
            case 42 -> { return i + 1 + (d[i] & 0xFF); }
            case 43, 249 -> { return skipParams(d, i); }
            case 40, 41 -> { return i + 1 + (d[i] & 0xFF) * 4; }
            case 75 -> { return i + 3; }
            default -> {
                if (op >= 100 && op < 110) return i + 4;
                return -1;
            }
        }
    }

    private static int u16(byte[] d, int i) { return ((d[i] & 0xFF) << 8) | (d[i + 1] & 0xFF); }
    private static String str(byte[] d, int i) {
        int s = i; while (i < d.length && d[i] != 0) i++;
        return new String(d, s, i - s, StandardCharsets.UTF_8);
    }
    private static int skipStr(byte[] d, int i) { while (i < d.length && d[i++] != 0) {} return i; }
    private static int skipParams(byte[] d, int i) {
        int count = d[i++] & 0xFF;
        for (int n = 0; n < count; n++) {
            boolean string = d[i++] != 0; i += 3;
            if (string) i = skipStr(d, i); else i += 4;
        }
        return i;
    }
    private static String hex(byte[] d, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(String.format("%02x ", d[i]));
        return sb.toString();
    }
}
