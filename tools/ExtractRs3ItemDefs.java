import com.displee.cache.CacheLibrary;
import com.displee.cache.index.Index;
import com.displee.cache.index.archive.Archive;
import com.displee.cache.index.archive.file.File;
import java.nio.charset.StandardCharsets;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.io.InputStream;

/**
 * RS3 item def extractor (Displee cache access + RS3 opcode/pattern decoder).
 * Usage: ExtractRs3ItemDefs <cacheDir> <wikiId|Expected name>...
 */
public final class ExtractRs3ItemDefs {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: ExtractRs3ItemDefs <cacheDir> <wikiId|name>...");
        }
        System.out.println("#rs3Id|name|ground|male0|male1|female0|female1|maleHead|femaleHead|zoom2d|xan2d|yan2d|zan2d|xOffset2d|yOffset2d|maleOffset|femaleOffset|stackable|tradeable");
        ItemLoader loader = new ItemLoader();
        try (CacheLibrary lib = CacheLibrary.create(args[0])) {
            for (int i = 1; i < args.length; i++) {
                String[] token = args[i].split("\\|", 2);
                int wikiId = Integer.parseInt(token[0]);
                String expectedName = token.length > 1 ? token[1].trim() : "";
                ItemDefinition def = findItem(lib, loader, wikiId, expectedName);
                if (def == null) {
                    throw new IllegalStateException("RS3 item " + wikiId
                            + (expectedName.isEmpty() ? "" : " (" + expectedName + ")") + " not found");
                }
                System.out.println(wikiId + "|" + def.name + "|" + def.inventoryModel + "|"
                        + def.maleModel0 + "|" + def.maleModel1 + "|"
                        + def.femaleModel0 + "|" + def.femaleModel1 + "|"
                        + def.maleHeadModel + "|" + def.femaleHeadModel + "|"
                        + def.zoom2d + "|" + def.xan2d + "|" + def.yan2d + "|" + def.zan2d + "|"
                        + def.xOffset2d + "|" + def.yOffset2d + "|"
                        + def.maleOffset + "|" + def.femaleOffset + "|"
                        + def.stackable + "|" + def.tradeable);
            }
        }
    }

    private static ItemDefinition findItem(CacheLibrary lib, ItemLoader loader, int wikiId, String expectedName) {
        byte[] needle = expectedName.toLowerCase().getBytes(StandardCharsets.UTF_8);
        ItemDefinition best = null;
        for (int idx = 0; idx < lib.getIndices().length; idx++) {
            if (!lib.exists(idx)) continue;
            Index index = lib.index(idx);
            for (int aid : index.archiveIds()) {
                Archive archive;
                try {
                    archive = index.archive(aid, false);
                } catch (Exception e) {
                    continue;
                }
                if (archive == null) continue;
                File[] files = archive.files();
                if (files == null) continue;
                for (File file : files) {
                    byte[] data = file.getData();
                    if (data == null || data.length < 8) continue;
                    if (file.getId() != wikiId && (expectedName.isEmpty() || !containsIgnoreCase(data, needle))) {
                        continue;
                    }
                    ItemDefinition pattern = extractByPatterns(wikiId, data, expectedName);
                    if (pattern != null && nameMatches(pattern.name, expectedName, file.getId() == wikiId)) {
                        if (isBetter(pattern, best)) best = pattern;
                    }
                    ItemDefinition rs3 = parseRs3(wikiId, data);
                    if (rs3 != null && nameMatches(rs3.name, expectedName, file.getId() == wikiId)) {
                        if (isBetter(rs3, best)) best = rs3;
                    }
                    ItemDefinition loaded = tryLoad(loader, wikiId, data);
                    if (loaded != null && nameMatches(loaded.name, expectedName, file.getId() == wikiId) && isValid(loaded)) {
                        if (isBetter(loaded, best)) best = loaded;
                    }
                }
            }
        }
        return best;
    }

    /** Pattern scan for RS3 idx=19 configs where opcode streams are non-classic. */
    /** ItemLoader defaults zoom2d=2000; treat 0 as unset so opcode 4 can populate it. */
    private static ItemDefinition freshDef(int id) {
        ItemDefinition def = new ItemDefinition(id);
        def.zoom2d = 0;
        return def;
    }

    private static ItemDefinition extractByPatterns(int id, byte[] data, String expectedName) {
        String name = findBestName(data, expectedName);
        if (name == null) return null;

        ItemDefinition def = freshDef(id);
        def.name = name;
        def.tradeable = true;
        int namePos = indexOfString(data, name);

        // Classic inventory model near top: opcode 1 + u16
        for (int i = 0; i < Math.min(24, data.length - 3); i++) {
            if ((data[i] & 0xFF) == 1 && (data[i + 1] & 0xFF) != 0x80) {
                int model = u16(data, i + 1);
                if (plausibleModel(model)) { def.inventoryModel = model; break; }
            }
        }
        // RS3 smart inventory model closest before item name
        int bestInvDist = Integer.MAX_VALUE;
        for (int i = 0; i < data.length - 4; i++) {
            if ((data[i] & 0xFF) == 1 && (data[i + 1] & 0xFF) == 0x80) {
                int model = u16(data, i + 3);
                if (!plausibleModel(model)) continue;
                int dist = namePos >= 0 ? namePos - i : i;
                if (dist >= 0 && dist < bestInvDist) {
                    bestInvDist = dist;
                    def.inventoryModel = model;
                }
            }
        }

        for (int i = 0; i < data.length - 4; i++) {
            int op = data[i] & 0xFF;
            if ((op == 23 || op == 17) && (data[i + 1] & 0xFF) == 0x80) {
                int model = u16(data, i + 3);
                if (plausibleModel(model) && def.maleModel0 <= 0) def.maleModel0 = model;
            } else if ((op == 25 || op == 19) && (data[i + 1] & 0xFF) == 0x80) {
                int model = u16(data, i + 3);
                if (plausibleModel(model) && def.femaleModel0 <= 0) def.femaleModel0 = model;
            } else if (op == 23 && (data[i + 1] & 0xFF) != 0x80) {
                int model = u16(data, i + 1);
                if (plausibleModel(model) && def.maleModel0 <= 0) def.maleModel0 = model;
            } else if (op == 25 && (data[i + 1] & 0xFF) != 0x80) {
                int model = u16(data, i + 1);
                if (plausibleModel(model) && def.femaleModel0 <= 0) def.femaleModel0 = model;
            }
        }

        for (int i = 0; i < data.length - 3; i++) {
            int op = data[i] & 0xFF;
            if (op == 4) {
                int zoom = u16(data, i + 1);
                if (plausibleZoom(zoom) && def.zoom2d <= 0) def.zoom2d = zoom;
            } else if (op == 5) {
                int xan = u16(data, i + 1);
                if (plausibleAngle(xan) && def.xan2d <= 0) def.xan2d = xan;
            } else if (op == 6) {
                int yan = u16(data, i + 1);
                if (plausibleAngle(yan) && def.yan2d <= 0) def.yan2d = yan;
            } else if (op == 7 && def.xOffset2d == 0) {
                def.xOffset2d = toSignedShort(u16(data, i + 1));
            } else if (op == 8 && def.yOffset2d == 0) {
                def.yOffset2d = toSignedShort(u16(data, i + 1));
            } else if (op == 95 && def.zan2d <= 0) {
                int zan = u16(data, i + 1);
                if (plausibleAngle(zan)) def.zan2d = zan;
            }
        }
        if (def.zoom2d <= 0) def.zoom2d = 1200;
        return isValid(def) ? def : null;
    }

    private static int indexOfString(byte[] data, String text) {
        byte[] needle = text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i <= data.length - needle.length; i++) {
            if (containsAt(data, i, text)) return i;
        }
        return -1;
    }

    private static boolean plausibleModel(int model) {
        return model > 100 && model < 70000;
    }

    private static boolean plausibleZoom(int zoom) {
        return zoom >= 400 && zoom <= 4000;
    }

    private static boolean plausibleAngle(int angle) {
        return angle <= 4000 || angle >= 60000;
    }

    private static String findBestName(byte[] data, String expectedName) {
        String best = null;
        int bestScore = -1;
        for (int i = 0; i < data.length - 2; i++) {
            if ((data[i] & 0xFF) != 2) continue;
            String s = readCString(data, i + 1);
            if (s == null || s.length() < 3) continue;
            int score = scoreName(s, expectedName);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private static int scoreName(String found, String expected) {
        if (expected == null || expected.isEmpty()) return found.length();
        String f = found.toLowerCase();
        String e = expected.toLowerCase();
        if (f.contains("unlocked")) return -1;
        if (f.equals(e)) return 1000;
        if (f.startsWith(e + " (")) return 950;
        if (f.startsWith(e)) return 900 - (f.length() - e.length());
        if (e.endsWith(" gloves") && f.contains(e.substring(0, e.length() - 7))) return 850;
        if (e.contains("korasi") && f.contains("korasi")) return 800;
        return -1;
    }

    private static String readCString(byte[] data, int start) {
        int i = start;
        while (i < data.length && data[i] != 0) {
            int c = data[i] & 0xFF;
            if (c < 32 || c > 126) return null;
            i++;
        }
        return i > start ? new String(data, start, i - start, StandardCharsets.UTF_8) : null;
    }

    private static boolean containsAt(byte[] data, int pos, String text) {
        byte[] needle = text.getBytes(StandardCharsets.UTF_8);
        if (pos + needle.length > data.length) return false;
        for (int j = 0; j < needle.length; j++) {
            if (Character.toLowerCase((char) (data[pos + j] & 0xFF))
                    != Character.toLowerCase(text.charAt(j))) {
                return false;
            }
        }
        return true;
    }

    private static ItemDefinition parseRs3(int id, byte[] data) {
        ItemDefinition def = freshDef(id);
        InputStream stream = new InputStream(data);
        try {
            while (stream.getOffset() < data.length) {
                int opcode = stream.readUnsignedByte();
                if (opcode == 0) continue;
                if (!decodeRs3Opcode(opcode, def, stream, data)) return null;
            }
        } catch (RuntimeException e) {
            return null;
        }
        return isValid(def) ? def : null;
    }

    private static boolean decodeRs3Opcode(int opcode, ItemDefinition def, InputStream stream, byte[] data) {
        switch (opcode) {
            case 1 -> {
                if ((peekByte(stream, data) & 0xFF) == 0x80) {
                    int model = readRs3ModelRef(stream);
                    if (def.inventoryModel <= 0) def.inventoryModel = model;
                } else {
                    int model = stream.readUnsignedShort();
                    if (def.inventoryModel <= 0) def.inventoryModel = model;
                }
            }
            case 2 -> def.name = stream.readString();
            case 3 -> def.examine = stream.readString();
            case 4 -> { if (def.zoom2d <= 0) def.zoom2d = stream.readUnsignedShort(); else stream.readUnsignedShort(); }
            case 5 -> { if (def.xan2d <= 0) def.xan2d = stream.readUnsignedShort(); else stream.readUnsignedShort(); }
            case 6 -> { if (def.yan2d <= 0) def.yan2d = stream.readUnsignedShort(); else stream.readUnsignedShort(); }
            case 7 -> {
                if (def.xOffset2d == 0) def.xOffset2d = toSignedShort(stream.readUnsignedShort());
                else stream.readUnsignedShort();
            }
            case 8 -> {
                if (def.yOffset2d == 0) def.yOffset2d = toSignedShort(stream.readUnsignedShort());
                else stream.readUnsignedShort();
            }
            case 11 -> def.stackable = 1;
            case 12 -> def.cost = stream.readInt();
            case 13 -> stream.readByte();
            case 14 -> stream.readByte();
            case 16 -> def.members = true;
            case 23 -> assignMale(stream, data, def);
            case 24 -> {
                if (isStringOpcode(stream, data)) stream.readString();
                else if ((peekByte(stream, data) & 0xFF) == 0x80) def.maleModel1 = readRs3ModelRef(stream);
                else stream.readUnsignedShort();
            }
            case 25 -> assignFemale(stream, data, def);
            case 26 -> stream.readUnsignedShort();
            case 27 -> {
                if (isStringOpcode(stream, data)) stream.readString();
                else stream.readByte();
            }
            case 28 -> skipRecolors(stream);
            case 30, 31, 32, 33, 34 -> stream.readString();
            case 35, 36, 37, 38, 39 -> def.interfaceOptions[opcode - 35] = stream.readString();
            case 40, 41 -> {
                int count = stream.readUnsignedByte();
                for (int n = 0; n < count; n++) { stream.readUnsignedShort(); stream.readUnsignedShort(); }
            }
            case 42 -> stream.readByte();
            case 43, 249 -> stream.readParams();
            case 44 -> def.inventoryModel = stream.readInt();
            case 45, 46, 47, 48, 49, 50, 51, 52, 53, 54 -> skipIntModel(stream);
            case 65 -> def.geTradeable = true;
            case 75 -> stream.readShort();
            case 78, 79, 90, 91, 92, 93, 94 -> stream.readUnsignedShort();
            case 95 -> {
                if (def.zan2d <= 0) def.zan2d = stream.readUnsignedShort();
                else stream.readUnsignedShort();
            }
            case 97, 98, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109 -> {
                stream.readUnsignedShort(); stream.readUnsignedShort();
            }
            case 110, 111, 112 -> stream.readUnsignedShort();
            case 113, 114 -> stream.readByte();
            case 115 -> stream.readUnsignedByte();
            case 139, 140, 148, 149 -> stream.readUnsignedShort();
            case 10, 17, 19 -> {
                int model = readRs3ModelRef(stream);
                if (opcode == 17 && def.maleModel0 <= 0) def.maleModel0 = model;
                else if (opcode == 19 && def.femaleModel0 <= 0) def.femaleModel0 = model;
            }
            default -> {
                if (opcode >= 0x0c && opcode <= 0x0f) skipRs3HeaderChunk(opcode, stream);
                else if (opcode == 0x61 || opcode == 0x67 || opcode == 0x7e) { stream.readUnsignedByte(); stream.readInt(); }
                else if (opcode == 0xcc) stream.readUnsignedByte();
                else if (opcode == 0xf9) skipF9Block(stream, data);
                else if (opcode == 0x0a) stream.readUnsignedShort();
                else return false;
            }
        }
        return stream.getOffset() <= data.length;
    }

    private static void assignMale(InputStream stream, byte[] data, ItemDefinition def) {
        if ((peekByte(stream, data) & 0xFF) == 0x80) {
            if (def.maleModel0 <= 0) def.maleModel0 = readRs3ModelRef(stream);
            else readRs3ModelRef(stream);
        } else {
            if (def.maleModel0 <= 0) def.maleModel0 = stream.readUnsignedShort();
            else stream.readUnsignedShort();
            if (stream.getOffset() < data.length) stream.readUnsignedByte();
        }
    }

    private static void assignFemale(InputStream stream, byte[] data, ItemDefinition def) {
        if (isStringOpcode(stream, data)) { stream.readString(); return; }
        if ((peekByte(stream, data) & 0xFF) == 0x80) {
            if (def.femaleModel0 <= 0) def.femaleModel0 = readRs3ModelRef(stream);
            else readRs3ModelRef(stream);
        } else {
            if (def.femaleModel0 <= 0) def.femaleModel0 = stream.readUnsignedShort();
            else stream.readUnsignedShort();
            if (stream.getOffset() < data.length) stream.readUnsignedByte();
        }
    }

    private static void skipIntModel(InputStream stream) {
        stream.readInt();
        if (stream.remaining() > 0) {
            int peek = stream.peek() & 0xFF;
            if (peek < 0x80) stream.readUnsignedByte();
        }
    }

    private static int readRs3ModelRef(InputStream stream) {
        if ((stream.peek() & 0xFF) == 0x80) {
            stream.readUnsignedByte();
            stream.readUnsignedByte();
            return stream.readUnsignedShort();
        }
        return stream.readUnsignedShortSmartMinusOne();
    }

    private static void skipRecolors(InputStream stream) {
        int count = stream.readUnsignedByte();
        for (int n = 0; n < count; n++) {
            stream.readUnsignedByte();
            stream.readUnsignedShort();
            stream.readUnsignedByte();
        }
    }

    private static void skipRs3HeaderChunk(int opcode, InputStream stream) {
        switch (opcode) {
            case 0x0c -> {
                int sub = stream.readUnsignedByte();
                if (sub == 0x0c) {
                    stream.readUnsignedByte();
                    stream.readString();
                    stream.readString();
                } else if (sub == 0x00) stream.readUnsignedShort();
                else stream.readUnsignedByte();
            }
            case 0x0d -> { stream.readUnsignedByte(); stream.readUnsignedByte(); stream.readUnsignedByte(); }
            default -> stream.readUnsignedByte();
        }
    }

    private static void skipF9Block(InputStream stream, byte[] data) {
        int sub = stream.readUnsignedByte();
        if (sub == 0x08) { stream.readUnsignedByte(); stream.readInt(); }
        else if (sub == 0x0e) {
            stream.readUnsignedByte();
            while (stream.getOffset() < data.length) {
                int b = stream.readUnsignedByte();
                if (b == 0x0b) break;
                if (b == 0x02) stream.readInt();
                else { stream.readUnsignedByte(); stream.readInt(); }
            }
        } else if (sub == 0x05 || sub == 0x06) {
            stream.readUnsignedByte();
            stream.readUnsignedByte();
            stream.readString();
        } else stream.readUnsignedByte();
    }

    private static int peekByte(InputStream stream, byte[] data) {
        int off = stream.getOffset();
        return off < data.length ? data[off] & 0xFF : -1;
    }

    private static boolean isStringOpcode(InputStream stream, byte[] data) {
        int off = stream.getOffset();
        if (off >= data.length) return false;
        int b = data[off] & 0xFF;
        return b >= 0x20 && b < 0x7f;
    }

    private static int u16(byte[] d, int i) {
        return ((d[i] & 0xFF) << 8) | (d[i + 1] & 0xFF);
    }

    private static int toSignedShort(int value) {
        return value > 32767 ? value - 65536 : value;
    }

    private static boolean nameMatches(String found, String expected, boolean idMatch) {
        if (found == null || found.isEmpty()) return idMatch && expected.isEmpty();
        if (expected == null || expected.isEmpty()) return idMatch;
        return scoreName(found, expected) >= 800;
    }

    private static ItemDefinition tryLoad(ItemLoader loader, int id, byte[] data) {
        try { return loader.load(id, data); } catch (RuntimeException e) { return null; }
    }

    private static boolean containsIgnoreCase(byte[] data, byte[] needleLower) {
        if (needleLower.length == 0) return false;
        outer:
        for (int i = 0; i <= data.length - needleLower.length; i++) {
            for (int j = 0; j < needleLower.length; j++) {
                if (Character.toLowerCase((char) (data[i + j] & 0xFF)) != needleLower[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static boolean isValid(ItemDefinition def) {
        if (def.name == null || def.name.isEmpty() || def.inventoryModel <= 0) return false;
        if (def.inventoryModel > 70000 || def.inventoryModel == 32769) return false;
        for (char c : def.name.toCharArray()) {
            if (c < 32 || c > 126) return false;
        }
        return def.name.length() >= 3;
    }

    private static boolean isBetter(ItemDefinition c, ItemDefinition cur) {
        if (!isValid(c)) return false;
        if (cur == null) return true;
        return score(c) > score(cur);
    }

    private static int score(ItemDefinition def) {
        int s = def.inventoryModel;
        if (def.maleModel0 > 0) s += 1000;
        if (def.femaleModel0 > 0) s += 1000;
        if (def.zoom2d > 0 && def.zoom2d != 2000) s += 200;
        if (def.maleModel0 > 0 && def.maleModel0 < 70000) s += 500;
        if (!def.name.contains("(")) s += 50;
        if (def.name.toLowerCase().contains("unlocked")) s -= 5000;
        if (scoreName(def.name, def.name) >= 950) s += 300;
        return s;
    }
}
