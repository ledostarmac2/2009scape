import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;

public final class DumpClientScript {
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

    private static final class Insn {
        int index;
        int offset;
        int opcode;
        int intOperand;
        String stringOperand;
    }

    public static void main(String[] args) throws Exception {
        File cache = new File(args[0]);
        int group = Integer.parseInt(args[1]);
        String needle = args.length > 2 ? args[2] : "";
        int context = args.length > 3 ? Integer.parseInt(args[3]) : 16;

        Js5 js5 = new Js5(new DiskProvider(cache, 12), false, false);
        byte[] bytes = js5.fetchFile(group, 0);
        Decoded decoded = decode(bytes);
        System.out.println("group=" + group + " size=" + bytes.length + " instructions=" + decoded.instructions.size() +
                " trailerStart=" + decoded.trailerStart + " name=" + decoded.name);
        System.out.println("locals int=" + decoded.intLocals + " string=" + decoded.stringLocals +
                " args int=" + decoded.intArgs + " string=" + decoded.stringArgs);
        for (int i = 0; i < decoded.switchTables.size(); i++) {
            System.out.println("switch " + i + ": " + decoded.switchTables.get(i));
        }
        if (needle.isEmpty()) {
            for (Insn insn : decoded.instructions) {
                print(insn);
            }
            return;
        }
        for (int i = 0; i < decoded.instructions.size(); i++) {
            Insn insn = decoded.instructions.get(i);
            if (insn.stringOperand != null && insn.stringOperand.toLowerCase().contains(needle.toLowerCase())) {
                int from = Math.max(0, i - context);
                int to = Math.min(decoded.instructions.size(), i + context + 1);
                System.out.println("--- match @" + i + " \"" + insn.stringOperand + "\" ---");
                for (int j = from; j < to; j++) {
                    print(decoded.instructions.get(j));
                }
            }
        }
    }

    private static void print(Insn insn) {
        if (insn.stringOperand != null) {
            System.out.printf("%05d @%05d op=%d str=\"%s\"%n", insn.index, insn.offset, insn.opcode, insn.stringOperand);
        } else {
            System.out.printf("%05d @%05d op=%d int=%d%n", insn.index, insn.offset, insn.opcode, insn.intOperand);
        }
    }

    private static final class Decoded {
        String name;
        int trailerStart;
        int intLocals;
        int stringLocals;
        int intArgs;
        int stringArgs;
        List<Insn> instructions;
        List<String> switchTables;
    }

    private static Decoded decode(byte[] bytes) {
        int trailerLen = u2(bytes, bytes.length - 2);
        int trailerStart = bytes.length - trailerLen - 12 - 2;
        int pos = trailerStart;
        int instructionCount = i4(bytes, pos);
        pos += 4;

        Decoded decoded = new Decoded();
        decoded.trailerStart = trailerStart;
        decoded.intLocals = u2(bytes, pos);
        pos += 2;
        decoded.stringLocals = u2(bytes, pos);
        pos += 2;
        decoded.intArgs = u2(bytes, pos);
        pos += 2;
        decoded.stringArgs = u2(bytes, pos);
        pos += 2;
        int switchCount = u1(bytes, pos++);
        decoded.switchTables = new ArrayList<>(switchCount);
        for (int s = 0; s < switchCount; s++) {
            int entries = u2(bytes, pos);
            pos += 2;
            StringBuilder table = new StringBuilder();
            for (int i = 0; i < entries; i++) {
                int key = i4(bytes, pos);
                int jump = i4(bytes, pos + 4);
                pos += 8;
                if (i > 0) {
                    table.append(", ");
                }
                table.append(key).append("->").append(jump);
            }
            decoded.switchTables.add(table.toString());
        }

        pos = 0;
        if (bytes[pos] == 0) {
            decoded.name = "";
            pos++;
        } else {
            int start = pos;
            while (bytes[pos++] != 0) {
            }
            decoded.name = new String(bytes, start, pos - start - 1, CP1252);
        }

        decoded.instructions = new ArrayList<>(instructionCount);
        int index = 0;
        while (pos < trailerStart) {
            Insn insn = new Insn();
            insn.index = index++;
            insn.offset = pos;
            insn.opcode = u2(bytes, pos);
            pos += 2;
            if (insn.opcode == 3) {
                int start = pos;
                while (bytes[pos++] != 0) {
                }
                insn.stringOperand = new String(bytes, start, pos - start - 1, CP1252);
            } else if (insn.opcode >= 100 || insn.opcode == 21 || insn.opcode == 38 || insn.opcode == 39) {
                insn.intOperand = u1(bytes, pos++);
            } else {
                insn.intOperand = i4(bytes, pos);
                pos += 4;
            }
            decoded.instructions.add(insn);
        }
        return decoded;
    }

    private static int u1(byte[] bytes, int pos) {
        return bytes[pos] & 0xFF;
    }

    private static int u2(byte[] bytes, int pos) {
        return ((bytes[pos] & 0xFF) << 8) | (bytes[pos + 1] & 0xFF);
    }

    private static int i4(byte[] bytes, int pos) {
        return ((bytes[pos] & 0xFF) << 24) | ((bytes[pos + 1] & 0xFF) << 16) |
                ((bytes[pos + 2] & 0xFF) << 8) | (bytes[pos + 3] & 0xFF);
    }
}
