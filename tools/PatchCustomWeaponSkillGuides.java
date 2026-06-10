import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.Js5Index;

public final class PatchCustomWeaponSkillGuides {
    private static final int SCRIPT_ARCHIVE = 12;
    private static final Charset CP1252 = Charset.forName("Cp1252");

    private static final class Insn {
        int opcode;
        int intOperand;
        String stringOperand;
    }

    private static final class Script {
        byte[] bytes;
        int trailerStart;
        int instructionCount;
        int intLocals;
        int stringLocals;
        int intArgs;
        int stringArgs;
        List<Insn> instructions = new ArrayList<>();
        List<LinkedHashMap<Integer, Integer>> switches = new ArrayList<>();
    }

    private static final class Row {
        final int level;
        final int itemId;
        final String title;
        final String titleSuffix;
        final String verb;
        final String itemText;
        final String sentenceSuffix;
        int cachedInstructionOffset; // For new rows during patch

        Row(int level, int itemId, String title, String titleSuffix, String verb, String itemText, String sentenceSuffix) {
            this.level = level;
            this.itemId = itemId;
            this.title = title;
            this.titleSuffix = titleSuffix;
            this.verb = verb;
            this.itemText = itemText;
            this.sentenceSuffix = sentenceSuffix;
            this.cachedInstructionOffset = -1;
        }

        int instructionCount() {
            return titleSuffix == null ? 10 : 13;
        }

        byte[] encode() throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            opInt(out, 0, level);
            opInt(out, 0, itemId);
            opString(out, title);
            if (titleSuffix != null) {
                opString(out, "<br>");
                opString(out, titleSuffix);
                opInt(out, 37, 3);
            }
            opString(out, verb);
            opString(out, "<col=000080>");
            opString(out, itemText);
            opString(out, "</col>");
            opString(out, sentenceSuffix);
            opInt(out, 37, 5);
            opByte(out, 21, 0);
            return out.toByteArray();
        }
    }

    public static void main(String[] args) throws Exception {
        File cacheDir = new File(args.length == 0 ? "game/data/cache" : args[0]);
        patch(cacheDir, 982, 10, new Row[] {
                simple(78, 14422, "Members: Emberblade", "Emberblade"),
                simple(82, 14545, "Members: Frostmourne", "Frostmourne"),
                simple(90, 14656, "Members: Master Sword", "Master Sword"),
                new Row(91, 14546, "Members: Ledostar's Edge", " (with 70 Prayer)",
                        "Members now have the Attack level required to wield ", "Ledostar's Edge",
                        ". (They also need level 70 Prayer.)"),
                new Row(70, 14547, "Members: Ledostar's Sting", " (with 70 Defence and 70 Prayer)",
                        "Members now have the Attack level required to wield ", "Ledostar's Sting",
                        ". (They also need level 70 Defence and level 70 Prayer.)"),
                simple(99, 14666, "Members: Infinity Blade", "Infinity Blade"),
                simple(78, 14687, "Members: Dragon hunter lance", "Dragon hunter lance"),
                simple(70, 14688, "Members: Zamorakian hasta", "Zamorakian hasta"),
                simple(82, 14689, "Members: Osmumten's fang", "Osmumten's fang"),
                new Row(60, 14690, "Members: Dragon warhammer", " (with 60 Strength)",
                        "Members now have the Attack level required to wield ", "Dragon warhammer",
                        ". (They also need level 60 Strength.)"),
                new Row(60, 14703, "Members: Dragon pickaxe", " (with 61 Mining)",
                        "Members now have the Attack level required to wield ", "Dragon pickaxe",
                        ". (They also need level 61 Mining.)"),
                new Row(75, 14704, "Members: Staff of light", " (with 75 Magic)",
                        "Members now have the Attack level required to wield ", "Staff of light",
                        ". (They also need level 75 Magic.)"),
                new Row(75, 14705, "Members: Voidwaker", " (with 75 Magic)",
                        "Members now have the Attack level required to wield ", "Voidwaker",
                        ". (They also need level 75 Magic.)"),
                new Row(70, 14691, "Members: Armadyl crossbow", null,
                        "Members now have the Ranged level required to wield ", "Armadyl crossbow", "."),
                new Row(75, 14692, "Members: Toxic blowpipe", null,
                        "Members now have the Ranged level required to wield ", "Toxic blowpipe", ".")
        });
        patch(cacheDir, 978, 10, new Row[] {
                new Row(75, 14661, "Members: Primordial boots", " (with 75 Attack and 75 Strength)",
                        "Members now have the Defence level required to wear ", "Primordial boots",
                        ". (They also need level 75 Attack and level 75 Strength.)"),
                new Row(75, 14695, "Members: Pegasian boots", " (with 75 Ranged)",
                        "Members now have the Defence level required to wear ", "Pegasian boots",
                        ". (They also need level 75 Ranged.)"),
                new Row(75, 14696, "Members: Eternal boots", " (with 75 Magic)",
                        "Members now have the Defence level required to wear ", "Eternal boots",
                        ". (They also need level 75 Magic.)"),
                new Row(75, 14697, "Members: Guardian boots", " (with 75 Strength)",
                        "Members now have the Defence level required to wear ", "Guardian boots",
                        ". (They also need level 75 Strength.)"),
                simpleWearDefence(1, 14734, "Members: Infernal cape", "Infernal cape")
        });
        patch(cacheDir, 978, 11, new Row[] {
                new Row(70, 14547, "Members: Ledostar's Sting", " (with 70 Attack and 70 Prayer)",
                        "Members now have the Defence level required to wield ", "Ledostar's Sting",
                        ". (They also need level 70 Attack and level 70 Prayer.)"),
                new Row(70, 14686, "Members: Avernic defender", " (with 70 Attack)",
                        "Members now have the Defence level required to wield ", "Avernic defender",
                        ". (They also need level 70 Attack.)"),
                new Row(60, 14702, "Members: Dragon defender", " (with 60 Attack)",
                        "Members now have the Defence level required to wield ", "Dragon defender",
                        ". (They also need level 60 Attack.)")
        });
        patch(cacheDir, 978, 2, new Row[] {
                simpleWearDefence(70, 14660, "Members: Neitiznot faceguard", "Neitiznot faceguard"),
                new Row(1, 14698, "Members: Basilisk jaw", null,
                        "Members can now use ", "Basilisk jaws", " when creating Neitiznot faceguards.")
        });
        patch(cacheDir, 978, 12, new Row[] {
                new Row(80, 14659, "Members: Ferocious gloves", " (with 80 Attack)",
                        "Members now have the Defence level required to wear ", "Ferocious gloves",
                        ". (They also need level 80 Attack.)")
        });
        patch(cacheDir, 996, 8, new Row[] {
                simpleMagicWear(70, 14694, "Members: Occult necklace", "Occult necklace"),
                simpleMagicWear(50, 14693, "Members: Tome of fire", "Tome of fire"),
                simpleMagicWear(75, 14699, "Members: Imbued saradomin cape", "Imbued saradomin cape"),
                simpleMagicWear(75, 14700, "Members: Imbued guthix cape", "Imbued guthix cape"),
                simpleMagicWear(75, 14701, "Members: Imbued zamorak cape", "Imbued zamorak cape"),
                new Row(1, 14684, "Members: Brimstone ring", null,
                        "Members can now wear ", "Brimstone rings", ".")
        });
        patch(cacheDir, 1004, 1, new Row[] {
                new Row(70, 14546, "Members: Ledostar's Edge", " (with 91 Attack)",
                        "Members now have the Prayer level required to wield ", "Ledostar's Edge",
                        ". (They also need level 91 Attack.)"),
                new Row(70, 14547, "Members: Ledostar's Sting", " (with 70 Attack and 70 Defence)",
                        "Members now have the Prayer level required to wield ", "Ledostar's Sting",
                        ". (They also need level 70 Attack and level 70 Defence.)"),
                simplePrayerWear(75, 14676, "Members: Amulet of torture", "Amulet of torture"),
                simplePrayerWear(75, 14677, "Members: Amulet of torture (or)", "Amulet of torture (or)"),
                simplePrayerWear(75, 14678, "Members: Necklace of anguish", "Necklace of anguish"),
                simplePrayerWear(75, 14679, "Members: Necklace of anguish (or)", "Necklace of anguish (or)"),
                simplePrayerWear(75, 14680, "Members: Tormented bracelet", "Tormented bracelet"),
                simplePrayerWear(75, 14681, "Members: Tormented bracelet (or)", "Tormented bracelet (or)"),
                simplePrayerWear(75, 14682, "Members: Ring of suffering", "Ring of suffering"),
                simplePrayerWear(75, 14683, "Members: Ring of suffering (i)", "Ring of suffering (i)"),
                new Row(1, 14685, "Members: Lightbearer", null,
                        "Members can now wear ", "Lightbearers", ".")
        });
    }

    private static Row simple(int level, int itemId, String title, String itemText) {
        return new Row(level, itemId, title, null,
                "Members now have the Attack level required to wield ", itemText, ".");
    }

    private static Row simpleWearDefence(int level, int itemId, String title, String itemText) {
        return new Row(level, itemId, title, null,
                "Members now have the Defence level required to wear ", itemText, ".");
    }

    private static Row simpleMagicWear(int level, int itemId, String title, String itemText) {
        return new Row(level, itemId, title, null,
                "Members now have the Magic level required to wear ", itemText, ".");
    }

    private static Row simplePrayerWear(int level, int itemId, String title, String itemText) {
        return new Row(level, itemId, title, null,
                "Members now have the Prayer level required to wear ", itemText, ".");
    }

    private static void patch(File cacheDir, int group, int categoryKey, Row[] rows) throws Exception {
        byte[] bytes = readSingleFile(cacheDir, group);
        Script script = decode(bytes);
        int switchInsn = categorySwitchInstruction(script, categoryKey);
        int tableId = script.instructions.get(switchInsn).intOperand;
        LinkedHashMap<Integer, Integer> table = script.switches.get(tableId);

        ByteArrayOutputStream additions = new ByteArrayOutputStream();
        int addedInstructions = 0;
        String existing = new String(bytes, CP1252);
        
        // Collect and sort new rows by level
        List<Row> rowsToAdd = new ArrayList<>();
        for (Row row : rows) {
            if (!existing.contains(row.title)) {
                rowsToAdd.add(row);
            } else {
                System.out.println("group " + group + ": keeping existing row " + row.title);
            }
        }
        
        rowsToAdd.sort(Comparator.comparingInt(row -> row.level));

        Map<Integer, Integer> addedLevelsByOffset = new HashMap<>();
        int nextKey = table.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        for (Row row : rowsToAdd) {
            int rowStart = script.instructionCount + addedInstructions;
            int offset = rowStart - (switchInsn + 1);
            table.put(nextKey++, offset);
            addedLevelsByOffset.put(offset, row.level);
            additions.write(row.encode());
            addedInstructions += row.instructionCount();
            System.out.println("group " + group + ": added row " + row.title + " at level " + row.level);
        }

        resortCategoryByLevel(script, switchInsn, addedLevelsByOffset);
        System.out.println("group " + group + ": resorted category " + categoryKey + " rows by level");

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(bytes, 0, script.trailerStart);
        body.write(additions.toByteArray());
        byte[] patched = encodeScript(script, body.toByteArray(), script.instructionCount + addedInstructions);
        writeSingleFile(cacheDir, group, patched);
    }

    private static void resortCategoryByLevel(Script script, int switchInsn, Map<Integer, Integer> addedLevelsByOffset) {
        int tableId = script.instructions.get(switchInsn).intOperand;
        LinkedHashMap<Integer, Integer> table = script.switches.get(tableId);
        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(table.entrySet());
        entries.sort((a, b) -> {
            int levelA = levelForOffset(script, switchInsn, a.getValue(), addedLevelsByOffset);
            int levelB = levelForOffset(script, switchInsn, b.getValue(), addedLevelsByOffset);
            if (levelA != levelB) {
                return Integer.compare(levelA, levelB);
            }
            return Integer.compare(a.getKey(), b.getKey());
        });
        LinkedHashMap<Integer, Integer> sorted = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            sorted.put(i, entries.get(i).getValue());
        }
        script.switches.set(tableId, sorted);
    }

    private static int levelForOffset(Script script, int switchInsn, int offset, Map<Integer, Integer> addedLevelsByOffset) {
        Integer added = addedLevelsByOffset.get(offset);
        if (added != null) {
            return added;
        }
        return rowLevel(script, switchInsn, offset);
    }

    private static int rowLevel(Script script, int switchInsn, int offset) {
        int insnIdx = switchInsn + 1 + offset;
        Insn insn = script.instructions.get(insnIdx);
        if (insn.opcode != 0) {
            throw new IllegalStateException("Expected level int at instruction " + insnIdx + ", got opcode " + insn.opcode);
        }
        return insn.intOperand;
    }

    private static int categorySwitchInstruction(Script script, int categoryKey) {
        if (script.instructions.size() < 3 || script.instructions.get(1).opcode != 51) {
            throw new IllegalStateException("Unexpected skill guide script header");
        }
        int switchTable = script.instructions.get(1).intOperand;
        Integer jump = script.switches.get(switchTable).get(categoryKey);
        if (jump == null) {
            throw new IllegalArgumentException("Category key " + categoryKey + " not found");
        }
        int target = 1 + 1 + jump;
        if (target + 1 >= script.instructions.size() || script.instructions.get(target + 1).opcode != 51) {
            throw new IllegalStateException("Unexpected category block target for key " + categoryKey + ": " + target);
        }
        return target + 1;
    }

    private static Script decode(byte[] bytes) {
        Script script = new Script();
        script.bytes = bytes;
        int trailerLen = u2(bytes, bytes.length - 2);
        script.trailerStart = bytes.length - trailerLen - 12 - 2;
        int pos = script.trailerStart;
        script.instructionCount = i4(bytes, pos);
        pos += 4;
        script.intLocals = u2(bytes, pos);
        pos += 2;
        script.stringLocals = u2(bytes, pos);
        pos += 2;
        script.intArgs = u2(bytes, pos);
        pos += 2;
        script.stringArgs = u2(bytes, pos);
        pos += 2;
        int switchCount = u1(bytes, pos++);
        for (int s = 0; s < switchCount; s++) {
            int entries = u2(bytes, pos);
            pos += 2;
            LinkedHashMap<Integer, Integer> table = new LinkedHashMap<>();
            for (int i = 0; i < entries; i++) {
                table.put(i4(bytes, pos), i4(bytes, pos + 4));
                pos += 8;
            }
            script.switches.add(table);
        }

        pos = bytes[0] == 0 ? 1 : nextStringEnd(bytes, 0);
        while (pos < script.trailerStart) {
            Insn insn = new Insn();
            insn.opcode = u2(bytes, pos);
            pos += 2;
            if (insn.opcode == 3) {
                int start = pos;
                pos = nextStringEnd(bytes, pos);
                insn.stringOperand = new String(bytes, start, pos - start - 1, CP1252);
            } else if (insn.opcode >= 100 || insn.opcode == 21 || insn.opcode == 38 || insn.opcode == 39) {
                insn.intOperand = u1(bytes, pos++);
            } else {
                insn.intOperand = i4(bytes, pos);
                pos += 4;
            }
            script.instructions.add(insn);
        }
        return script;
    }

    private static byte[] encodeScript(Script script, byte[] body, int instructionCount) throws Exception {
        ByteArrayOutputStream switches = new ByteArrayOutputStream();
        switches.write(script.switches.size());
        for (LinkedHashMap<Integer, Integer> table : script.switches) {
            writeShort(switches, table.size());
            for (Map.Entry<Integer, Integer> entry : table.entrySet()) {
                writeInt(switches, entry.getKey());
                writeInt(switches, entry.getValue());
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(body);
        writeInt(out, instructionCount);
        writeShort(out, script.intLocals);
        writeShort(out, script.stringLocals);
        writeShort(out, script.intArgs);
        writeShort(out, script.stringArgs);
        out.write(switches.toByteArray());
        writeShort(out, switches.size());
        return out.toByteArray();
    }

    private static byte[] readSingleFile(File cacheDir, int group) throws Exception {
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        Cache archive = new Cache(SCRIPT_ARCHIVE, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + SCRIPT_ARCHIVE), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        byte[] packedIndex = master.read(SCRIPT_ARCHIVE);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));
        if (index.groupSizes[group] != 1) {
            throw new IllegalArgumentException("Expected archive " + SCRIPT_ARCHIVE + " group " + group + " to contain one file");
        }
        byte[] packed = archive.read(group);
        if (packed == null) {
            throw new IllegalStateException("Script archive group " + group + " is missing from cache " + cacheDir.getPath()
                    + ". Run install-skill-guides.bat to copy it from game-test/data/cache.");
        }
        return Js5Compression.uncompress(stripVersion(packed));
    }

    private static void writeSingleFile(File cacheDir, int group, byte[] file) throws Exception {
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "rw", Long.MAX_VALUE), 5200, 0);
        Cache master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx255"), "rw", Long.MAX_VALUE), 6000, 0), 1000000);
        byte[] packedIndex = master.read(SCRIPT_ARCHIVE);
        int archiveIndexVersion = readTrailingVersion(packedIndex);
        byte[] indexBytes = Js5Compression.uncompress(packedIndex);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));
        byte[] packedNoVersion = wrapUncompressed(file);
        byte[] packed = appendVersion(packedNoVersion, index.groupVersions[group]);
        Cache archive = new Cache(SCRIPT_ARCHIVE, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + SCRIPT_ARCHIVE), "rw", Long.MAX_VALUE), 6000, 0), 1000000);
        if (!archive.write(group, packed.length, packed)) {
            throw new IllegalStateException("Unable to write archive " + SCRIPT_ARCHIVE + " group " + group);
        }
        patchGroupChecksum(indexBytes, group, Buffer.crc32(packedNoVersion, packedNoVersion.length));
        byte[] repackedIndex = appendVersion(wrapGzip(indexBytes), archiveIndexVersion);
        if (!master.write(SCRIPT_ARCHIVE, repackedIndex.length, repackedIndex)) {
            throw new IllegalStateException("Unable to write archive index " + SCRIPT_ARCHIVE);
        }
    }

    private static int nextStringEnd(byte[] bytes, int pos) {
        while (bytes[pos++] != 0) {
        }
        return pos;
    }

    private static byte[] stripVersion(byte[] group) {
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }

    private static int readTrailingVersion(byte[] data) {
        return data.length < 2 ? 0 : u2(data, data.length - 2);
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

    private static void patchGroupChecksum(byte[] index, int targetGroup, int checksum) {
        int pos = 0;
        int protocol = index[pos++] & 0xFF;
        if (protocol >= 6) {
            pos += 4;
        }
        boolean names = (index[pos++] & 0xFF) != 0;
        int size = u2(index, pos);
        pos += 2;
        int group = 0;
        int targetIndex = -1;
        for (int i = 0; i < size; i++) {
            group += u2(index, pos);
            pos += 2;
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

    private static void opString(ByteArrayOutputStream out, String value) {
        writeShort(out, 3);
        out.writeBytes(value.getBytes(CP1252));
        out.write(0);
    }

    private static void opInt(ByteArrayOutputStream out, int opcode, int value) {
        writeShort(out, opcode);
        writeInt(out, value);
    }

    private static void opByte(ByteArrayOutputStream out, int opcode, int value) {
        writeShort(out, opcode);
        out.write(value & 0xFF);
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
