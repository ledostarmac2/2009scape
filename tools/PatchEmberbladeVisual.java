import java.io.ByteArrayOutputStream;
import java.io.File;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.Js5Index;

public class PatchEmberbladeVisual {
    private static final int ITEM_ARCHIVE = 19;
    private static final int MODEL_ARCHIVE = 7;
    private static int itemId = 14422;
    private static int inventoryModelId = 14422;
    private static int wornModelId = 14423;
    private static int templateItemId = 1289;
    private static String itemName = "Emberblade";
    private static final int MAX_INVENTORY_SOURCE_TRIANGLES = 700;
    private static final int MAX_WORN_SOURCE_TRIANGLES = 300;
    private static final int ROTATION_HALF = 1024;
    private static final int ICON_ZOOM_SWORD = 1570;
    private static final int ICON_XAN_SWORD = 400; // reverted to the "almost there" angle
    private static final int ICON_YAN_SWORD = 360 + ROTATION_HALF;

    private static final class V {
        final int x;
        final int y;
        final int z;

        V(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class F {
        final int a;
        final int b;
        final int c;
        final int color;

        F(int a, int b, int c, int color) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.color = color;
        }
    }

    private static final class ObjV {
        final double x;
        final double y;
        final double z;

        ObjV(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class ObjUV {
        final double u;
        final double v;

        ObjUV(double u, double v) {
            this.u = u;
            this.v = v;
        }
    }

    private static final class ObjFace {
        final int[] v;
        final int[] uv;

        ObjFace(int a, int b, int c, int au, int bu, int cu) {
            this.v = new int[] { a, b, c };
            this.uv = new int[] { au, bu, cu };
        }
    }

    public static void main(String[] args) throws Exception {
        File cacheDir = new File(args[0]);
        Path backupDir = Path.of(args[1]);
        Files.createDirectories(backupDir);
        backup(cacheDir, backupDir, "main_file_cache.dat2");
        backup(cacheDir, backupDir, "main_file_cache.idx7");
        backup(cacheDir, backupDir, "main_file_cache.idx19");
        backup(cacheDir, backupDir, "main_file_cache.idx255");

        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "rw", Long.MAX_VALUE), 5200, 0);
        Cache master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx255"), "rw", Long.MAX_VALUE), 6000, 0), 1000000);

        Path objPath = args.length >= 3 ? Path.of(args[2]) : Path.of("../Custom Items/Emberblade/Model (1).obj");
        String mode = args.length >= 4 ? args[3].toLowerCase() : "all";
        if (args.length >= 9) {
            itemId = Integer.parseInt(args[4]);
            inventoryModelId = Integer.parseInt(args[5]);
            wornModelId = Integer.parseInt(args[6]);
            templateItemId = Integer.parseInt(args[7]);
            itemName = args[8];
        }
        if (mode.equals("all") || mode.equals("model")) {
            patchSingleFileGroup(cacheDir, data, master, MODEL_ARCHIVE, inventoryModelId, buildEmberbladeModel(objPath, true));
            patchSingleFileGroup(cacheDir, data, master, MODEL_ARCHIVE, wornModelId, buildEmberbladeModel(objPath, false));
        }
        if (mode.equals("all") || mode.equals("item")) {
            patchItemDefinition(cacheDir, data, master);
        }
        if (!mode.equals("all") && !mode.equals("model") && !mode.equals("item")) {
            throw new IllegalArgumentException("mode must be all, model, or item");
        }

        System.out.println("Patched " + itemName + " cache mode=" + mode + " for item " + itemId);
    }

    private static void backup(File cacheDir, Path backupDir, String name) throws Exception {
        Path target = backupDir.resolve(name);
        if (!Files.exists(target)) {
            Files.copy(new File(cacheDir, name).toPath(), target);
        }
    }

    private static void patchItemDefinition(File cacheDir, BufferedFile data, Cache master) throws Exception {
        int group = itemId >>> 8;
        int file = itemId & 255;
        byte[] packedIndex = master.read(ITEM_ARCHIVE);
        int archiveIndexVersion = readTrailingVersion(packedIndex);
        byte[] indexBytes = Js5Compression.uncompress(packedIndex);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));
        byte[][] files = readGroupFiles(cacheDir, ITEM_ARCHIVE, group, index);
        byte[] template = readItemDefinition(cacheDir, templateItemId);
        files[file] = buildItemDefinition(template);
        writeMultiFileGroup(cacheDir, data, master, ITEM_ARCHIVE, group, files, index.groupVersions[group], indexBytes, archiveIndexVersion);
    }

    private static byte[] readItemDefinition(File cacheDir, int itemId) throws Exception {
        byte[] packedIndex = new Cache(255,
                new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0),
                new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0),
                1000000).read(ITEM_ARCHIVE);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));
        byte[][] files = readGroupFiles(cacheDir, ITEM_ARCHIVE, itemId >>> 8, index);
        return files[itemId & 255];
    }

    private static byte[][] readGroupFiles(File cacheDir, int archiveId, int group, Js5Index index) throws Exception {
        Cache archive = new Cache(archiveId,
                new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0),
                new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + archiveId), "r", Long.MAX_VALUE), 6000, 0),
                1000000);
        byte[] packed = archive.read(group);
        byte[] uncompressed = Js5Compression.uncompress(stripVersion(packed));
        int count = index.groupSizes[group];
        byte[][] files = new byte[index.groupCapacities[group]][];
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

    private static byte[] stripVersion(byte[] group) {
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }

    private static int readInt(byte[] bytes, int pos) {
        return ((bytes[pos] & 0xFF) << 24) | ((bytes[pos + 1] & 0xFF) << 16) | ((bytes[pos + 2] & 0xFF) << 8) | (bytes[pos + 3] & 0xFF);
    }

    private static void patchSingleFileGroup(File cacheDir, BufferedFile data, Cache master, int archiveId, int group, byte[] file) throws Exception {
        byte[] packedIndex = master.read(archiveId);
        int archiveIndexVersion = readTrailingVersion(packedIndex);
        byte[] indexBytes = Js5Compression.uncompress(packedIndex);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));
        byte[] packedNoVersion = wrapUncompressed(file);
        byte[] packed = appendVersion(packedNoVersion, index.groupVersions[group]);
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

    private static int readTrailingVersion(byte[] data) {
        if (data.length < 2) {
            return 0;
        }
        return ((data[data.length - 2] & 0xFF) << 8) | (data[data.length - 1] & 0xFF);
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

    private static byte[] buildItemDefinition(byte[] template) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while (i < template.length) {
            int opcode = template[i++] & 0xFF;
            if (opcode == 0) {
                break;
            }
            if (opcode == 1) {
                out.write(opcode);
                writeShort(out, inventoryModelId);
                i += 2;
            } else if (opcode == 23 || opcode == 24 || opcode == 25 || opcode == 26) {
                out.write(opcode);
                writeShort(out, wornModelId);
                i += 2;
            } else if (opcode == 2) {
                out.write(opcode);
                writeString(out, itemName);
                while (i < template.length && template[i++] != 0) {
                }
            } else if (opcode == 4) {
                out.write(opcode);
                writeShort(out, ICON_ZOOM_SWORD);
                i += 2;
            } else if (opcode == 5) {
                out.write(opcode);
                writeShort(out, ICON_XAN_SWORD);
                i += 2;
            } else if (opcode == 6) {
                out.write(opcode);
                writeShort(out, ICON_YAN_SWORD);
                i += 2;
            } else if (opcode == 40 || opcode == 41) {
                int count = template[i] & 0xFF;
                i += 1 + count * 4;
            } else {
                out.write(opcode);
                i = copyOpcodePayload(template, i, opcode, out);
            }
        }
        out.write(0);
        return out.toByteArray();
    }

    private static int copyOpcodePayload(byte[] def, int i, int opcode, ByteArrayOutputStream out) {
        int len;
        if (opcode == 4 || opcode == 5 || opcode == 6 || opcode == 7 || opcode == 8 ||
                opcode == 11 || opcode == 12 || opcode == 16 || opcode == 65 || opcode == 90 ||
                opcode == 91 || opcode == 92 || opcode == 93 || opcode == 95 || opcode == 96 ||
                opcode == 97 || opcode == 98 || opcode == 110 || opcode == 111 || opcode == 112 ||
                opcode == 113 || opcode == 114 || opcode == 115 || opcode == 121 || opcode == 122 ||
                opcode == 125 || opcode == 126 ||
                opcode == 127 || opcode == 128 || opcode == 129 || opcode == 130 || opcode == 132) {
            switch (opcode) {
                case 11:
                case 16:
                case 65:
                    len = 0;
                    break;
                case 113:
                case 114:
                    len = 1;
                    break;
                case 12:
                    len = 4;
                    break;
                case 125:
                case 126:
                case 127:
                case 128:
                case 129:
                case 130:
                    len = 3;
                    break;
                case 132:
                    len = 1 + 2 * (def[i] & 0xFF);
                    break;
                default:
                    len = 2;
                    break;
            }
            out.write(def, i, len);
            return i + len;
        } else if ((opcode >= 30 && opcode < 35) || (opcode >= 35 && opcode < 40)) {
            int start = i;
            while (def[i++] != 0) {
            }
            out.write(def, start, i - start);
            return i;
        } else if (opcode == 42) {
            len = 1 + (def[i] & 0xFF);
            out.write(def, i, len);
            return i + len;
        } else if (opcode >= 100 && opcode < 110) {
            len = 4;
            out.write(def, i, len);
            return i + len;
        } else if (opcode == 249) {
            int start = i;
            int count = def[i++] & 0xFF;
            for (int n = 0; n < count; n++) {
                boolean string = def[i++] != 0;
                i += 3;
                if (string) {
                    while (def[i++] != 0) {
                    }
                } else {
                    i += 4;
                }
            }
            out.write(def, start, i - start);
            return i;
        }
        throw new IllegalArgumentException("Unsupported item opcode " + opcode);
    }

    private static byte[] buildEmberbladeModel(Path objPath, boolean inventoryModel) throws Exception {
        if (Files.exists(objPath)) {
            return encodeObjModel(objPath, inventoryModel);
        }

        List<V> v = new ArrayList<>();
        List<F> f = new ArrayList<>();
        int silver = hsl(210, 35, 82);
        int darkSilver = hsl(210, 25, 48);
        int gold = hsl(43, 90, 58);
        int darkGold = hsl(38, 80, 36);
        int brown = hsl(25, 65, 26);
        int ember = hsl(18, 95, 52);

        addDiamondBlade(v, f, -115, 850, 78, 18, silver, darkSilver);
        addBox(v, f, -185, -250, -22, 185, -170, 22, gold, darkGold);
        addBox(v, f, -28, -575, -24, 28, -245, 24, brown, brown);
        addDiamondPommel(v, f, -660, -565, 58, gold, darkGold);
        addGem(v, f, -188, 38, 18, ember);

        return encodeOldModel(v, f, false);
    }

    private static byte[] encodeObjModel(Path objPath, boolean inventoryModel) throws Exception {
        List<ObjV> sourceVertices = new ArrayList<>();
        List<ObjUV> sourceUvs = new ArrayList<>();
        List<ObjFace> sourceFaces = new ArrayList<>();
        String mtlName = null;
        List<String> objLines = Files.readAllLines(objPath);
        for (String raw : objLines) {
            String line = raw.trim();
            if (line.startsWith("v ")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 4) {
                    sourceVertices.add(new ObjV(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
                }
            } else if (line.startsWith("vt ")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    sourceUvs.add(new ObjUV(Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
                }
            } else if (line.startsWith("mtllib ")) {
                mtlName = line.substring("mtllib ".length()).trim();
            } else if (line.startsWith("f ")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 4) {
                    int[] first = parseObjVertex(parts[1], sourceVertices.size(), sourceUvs.size());
                    int[] previous = parseObjVertex(parts[2], sourceVertices.size(), sourceUvs.size());
                    for (int i = 3; i < parts.length; i++) {
                        int[] next = parseObjVertex(parts[i], sourceVertices.size(), sourceUvs.size());
                        sourceFaces.add(new ObjFace(first[0], previous[0], next[0], first[1], previous[1], next[1]));
                        previous = next;
                    }
                }
            }
        }
        if (sourceVertices.isEmpty() || sourceFaces.isEmpty()) {
            throw new IllegalArgumentException("OBJ did not contain vertices/faces: " + objPath);
        }

        int faceLimit = inventoryModel ? MAX_INVENTORY_SOURCE_TRIANGLES : MAX_WORN_SOURCE_TRIANGLES;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (ObjV vert : sourceVertices) {
            minX = Math.min(minX, vert.x); maxX = Math.max(maxX, vert.x);
            minY = Math.min(minY, vert.y); maxY = Math.max(maxY, vert.y);
            minZ = Math.min(minZ, vert.z); maxZ = Math.max(maxZ, vert.z);
        }
        double height = Math.max(0.001, maxY - minY);

        if (sourceFaces.size() > faceLimit) {
            final double sourceMinY = minY;
            final double sourceHeight = height;
            sourceFaces.sort(Comparator.<ObjFace>comparingDouble(face -> faceImportance(sourceVertices, face, sourceMinY, sourceHeight)).reversed());
            sourceFaces = new ArrayList<>(sourceFaces.subList(0, faceLimit));
        }

        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double width = Math.max(0.001, maxX - minX);
        double depth = Math.max(0.001, maxZ - minZ);

        List<V> vertices = new ArrayList<>();
        for (ObjV vert : sourceVertices) {
            int x;
            int y;
            int z;
            double yT = (vert.y - minY) / height;
            double ringScale = isRingBand(yT) ? 1.28 : 1.0;
            double ringDepthScale = isRingBand(yT) ? 1.7 : 1.0;
            double sourceX = centerX + (vert.x - centerX) * ringScale;
            double sourceZ = centerZ + (vert.z - centerZ) * ringDepthScale;
            if (inventoryModel) {
                x = clampModelCoord((int) Math.round((sourceX - centerX) * (47.0 / width) - 10.5));
                y = clampModelCoord((int) Math.round((sourceZ - centerZ) * (6.0 / depth) - 4.0));
                z = clampModelCoord((int) Math.round(58.0 - (vert.y - minY) * (112.0 / height)));
            } else {
                x = clampModelCoord((int) Math.round((sourceX - centerX) * (13.0 / width) - 31.5));
                y = clampModelCoord((int) Math.round((sourceZ - centerZ) * (26.0 / depth) - 85.0));
                z = clampModelCoord((int) Math.round(18.0 - (vert.y - minY) * (130.0 / height)));
            }
            vertices.add(new V(x, y, z));
        }

        int blade = hsl(210, 20, 78);
        int darkBlade = hsl(210, 18, 54);
        int edge = hsl(205, 12, 90);
        int guard = hsl(43, 90, 58);
        int darkGuard = hsl(38, 78, 35);
        int grip = hsl(22, 50, 25);
        int ember = hsl(18, 86, 50);
        BufferedImage texture = loadDiffuseTexture(objPath, mtlName);
        List<F> faces = new ArrayList<>();
        for (int i = 0; i < sourceFaces.size(); i++) {
            ObjFace face = sourceFaces.get(i);
            double averageY = (sourceVertices.get(face.v[0]).y + sourceVertices.get(face.v[1]).y + sourceVertices.get(face.v[2]).y) / 3.0;
            double yT = (averageY - minY) / height;
            double normalZ = faceNormalZ(sourceVertices, face.v);
            int color = texture == null ? -1 : sampleTextureColor(texture, sourceUvs, face);
            if (color < 0) {
                if (isRingBand(yT)) {
                    color = i % 4 == 0 ? ember : (i % 2 == 0 ? guard : darkGuard);
                } else if (averageY < minY + height * 0.24) {
                    color = i % 2 == 0 ? grip : darkGuard;
                } else if (averageY < minY + height * 0.36) {
                    color = i % 3 == 0 ? ember : (i % 2 == 0 ? guard : darkGuard);
                } else {
                    color = i % 5 == 0 ? edge : (normalZ >= 0 ? blade : darkBlade);
                }
            } else {
                color = shadeColor(color, normalZ >= 0 ? 1.08 : 0.82);
            }
            faces.add(new F(face.v[0], face.v[1], face.v[2], color));
            faces.add(new F(face.v[2], face.v[1], face.v[0], color));
        }

        System.out.println("Loaded " + objPath + " as " + vertices.size() + " vertices / " + faces.size() + " double-sided triangles for " + (inventoryModel ? "inventory" : "worn") + " model");
        return encodeOldModel(vertices, faces, !inventoryModel);
    }

    private static BufferedImage loadDiffuseTexture(Path objPath, String mtlName) {
        if (mtlName == null || mtlName.isEmpty()) {
            return null;
        }
        Path mtlPath = objPath.getParent() == null ? Path.of(mtlName) : objPath.getParent().resolve(mtlName);
        if (!Files.exists(mtlPath)) {
            return null;
        }
        try {
            for (String raw : Files.readAllLines(mtlPath)) {
                String line = raw.trim();
                if (line.startsWith("map_Kd ")) {
                    String textureName = line.substring("map_Kd ".length()).trim();
                    Path texturePath = mtlPath.getParent() == null ? Path.of(textureName) : mtlPath.getParent().resolve(textureName);
                    if (Files.exists(texturePath)) {
                        BufferedImage image = ImageIO.read(texturePath.toFile());
                        if (image != null) {
                            System.out.println("Loaded diffuse texture " + texturePath);
                        }
                        return image;
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("Unable to load diffuse texture for " + objPath + ": " + ex.getMessage());
        }
        return null;
    }

    private static int sampleTextureColor(BufferedImage texture, List<ObjUV> uvs, ObjFace face) {
        double r = 0;
        double g = 0;
        double b = 0;
        int samples = 0;
        for (int i = 0; i < 3; i++) {
            int uvIndex = face.uv[i];
            if (uvIndex < 0 || uvIndex >= uvs.size()) {
                continue;
            }
            ObjUV uv = uvs.get(uvIndex);
            int x = Math.max(0, Math.min(texture.getWidth() - 1, (int) Math.round(uv.u * (texture.getWidth() - 1))));
            int y = Math.max(0, Math.min(texture.getHeight() - 1, (int) Math.round((1.0 - uv.v) * (texture.getHeight() - 1))));
            int rgb = texture.getRGB(x, y);
            r += (rgb >> 16) & 0xFF;
            g += (rgb >> 8) & 0xFF;
            b += rgb & 0xFF;
            samples++;
        }
        if (samples == 0) {
            return -1;
        }
        int rgb = ((int) Math.round(r / samples) << 16) | ((int) Math.round(g / samples) << 8) | (int) Math.round(b / samples);
        return rgbToHsl(rgb);
    }

    private static int shadeColor(int color, double factor) {
        int hue = (color >> 10) & 63;
        int sat = (color >> 7) & 7;
        int light = color & 127;
        light = Math.max(0, Math.min(127, (int) Math.round(light * factor)));
        return (hue << 10) | (sat << 7) | light;
    }

    private static int rgbToHsl(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double h;
        double s;
        double l = (max + min) / 2.0;
        if (max == min) {
            h = 0;
            s = 0;
        } else {
            double d = max - min;
            s = l > 0.5 ? d / (2.0 - max - min) : d / (max + min);
            if (max == r) {
                h = (g - b) / d + (g < b ? 6.0 : 0.0);
            } else if (max == g) {
                h = (b - r) / d + 2.0;
            } else {
                h = (r - g) / d + 4.0;
            }
            h *= 60.0;
        }
        return hsl((int) Math.round(h), (int) Math.round(s * 100.0), (int) Math.round(l * 100.0));
    }

    private static int parseObjIndex(String token, int vertexCount) {
        String first = token.split("/")[0];
        int value = Integer.parseInt(first);
        return value < 0 ? vertexCount + value : value - 1;
    }

    private static int[] parseObjVertex(String token, int vertexCount, int uvCount) {
        String[] parts = token.split("/");
        int vertexIndex = parseObjIndex(parts[0], vertexCount);
        int uvIndex = -1;
        if (parts.length >= 2 && !parts[1].isEmpty()) {
            uvIndex = parseObjIndex(parts[1], uvCount);
        }
        return new int[] { vertexIndex, uvIndex };
    }

    private static int clampModelCoord(int value) {
        return Math.max(-8192, Math.min(8191, value));
    }

    private static double faceArea(List<ObjV> vertices, int[] face) {
        ObjV a = vertices.get(face[0]);
        ObjV b = vertices.get(face[1]);
        ObjV c = vertices.get(face[2]);
        double ux = b.x - a.x, uy = b.y - a.y, uz = b.z - a.z;
        double vx = c.x - a.x, vy = c.y - a.y, vz = c.z - a.z;
        double cx = uy * vz - uz * vy;
        double cy = uz * vx - ux * vz;
        double cz = ux * vy - uy * vx;
        return Math.sqrt(cx * cx + cy * cy + cz * cz) / 2.0;
    }

    private static double faceImportance(List<ObjV> vertices, ObjFace face, double minY, double height) {
        double averageY = (vertices.get(face.v[0]).y + vertices.get(face.v[1]).y + vertices.get(face.v[2]).y) / 3.0;
        double yT = (averageY - minY) / Math.max(0.001, height);
        double weight;
        if (isRingBand(yT)) {
            weight = 4.0;
        } else if (yT < 0.18) {
            weight = 1.35;
        } else if (yT < 0.5) {
            weight = 1.65;
        } else {
            weight = 1.0;
        }
        return faceArea(vertices, face.v) * weight;
    }

    private static boolean isRingBand(double yT) {
        return yT >= 0.13 && yT <= 0.37;
    }

    private static double faceNormalZ(List<ObjV> vertices, int[] face) {
        ObjV a = vertices.get(face[0]);
        ObjV b = vertices.get(face[1]);
        ObjV c = vertices.get(face[2]);
        double ux = b.x - a.x, uy = b.y - a.y;
        double vx = c.x - a.x, vy = c.y - a.y;
        return ux * vy - uy * vx;
    }

    private static void addDiamondBlade(List<V> v, List<F> f, int baseY, int tipY, int width, int thick, int c1, int c2) {
        int base = v.size();
        v.add(new V(-width, baseY, 0));
        v.add(new V(0, baseY, thick));
        v.add(new V(width, baseY, 0));
        v.add(new V(0, baseY, -thick));
        v.add(new V(0, tipY, 0));
        tri(f, base, 0, 1, 4, c1);
        tri(f, base, 1, 2, 4, c1);
        tri(f, base, 2, 3, 4, c2);
        tri(f, base, 3, 0, 4, c2);
        tri(f, base, 0, 3, 2, c2);
        tri(f, base, 0, 2, 1, c1);
    }

    private static void addBox(List<V> v, List<F> f, int x1, int y1, int z1, int x2, int y2, int z2, int c1, int c2) {
        int b = v.size();
        v.add(new V(x1, y1, z1)); v.add(new V(x2, y1, z1)); v.add(new V(x2, y2, z1)); v.add(new V(x1, y2, z1));
        v.add(new V(x1, y1, z2)); v.add(new V(x2, y1, z2)); v.add(new V(x2, y2, z2)); v.add(new V(x1, y2, z2));
        int[][] faces = {{0,1,2},{0,2,3},{4,6,5},{4,7,6},{0,4,5},{0,5,1},{1,5,6},{1,6,2},{2,6,7},{2,7,3},{3,7,4},{3,4,0}};
        for (int i = 0; i < faces.length; i++) tri(f, b, faces[i][0], faces[i][1], faces[i][2], i % 2 == 0 ? c1 : c2);
    }

    private static void addDiamondPommel(List<V> v, List<F> f, int y1, int y2, int size, int c1, int c2) {
        int b = v.size();
        v.add(new V(0, y1, 0));
        v.add(new V(-size, (y1 + y2) / 2, 0));
        v.add(new V(0, (y1 + y2) / 2, size / 2));
        v.add(new V(size, (y1 + y2) / 2, 0));
        v.add(new V(0, (y1 + y2) / 2, -size / 2));
        v.add(new V(0, y2, 0));
        int[][] faces = {{0,1,2},{0,2,3},{0,3,4},{0,4,1},{5,2,1},{5,3,2},{5,4,3},{5,1,4}};
        for (int i = 0; i < faces.length; i++) tri(f, b, faces[i][0], faces[i][1], faces[i][2], i % 2 == 0 ? c1 : c2);
    }

    private static void addGem(List<V> v, List<F> f, int y, int size, int z, int color) {
        int b = v.size();
        v.add(new V(0, y + size, z + 6));
        v.add(new V(-size, y, z + 6));
        v.add(new V(0, y - size, z + 6));
        v.add(new V(size, y, z + 6));
        v.add(new V(0, y, z + 22));
        tri(f, b, 0,1,4,color); tri(f, b,1,2,4,color); tri(f, b,2,3,4,color); tri(f, b,3,0,4,color);
    }

    private static void tri(List<F> f, int b, int a, int c, int d, int color) {
        f.add(new F(b + a, b + c, b + d, color));
    }

    private static byte[] encodeOldModel(List<V> vertices, List<F> faces, boolean skinnedForWeaponHand) throws Exception {
        ByteArrayOutputStream vertexFlags = new ByteArrayOutputStream();
        ByteArrayOutputStream xData = new ByteArrayOutputStream();
        ByteArrayOutputStream yData = new ByteArrayOutputStream();
        ByteArrayOutputStream zData = new ByteArrayOutputStream();
        ByteArrayOutputStream vertexBones = new ByteArrayOutputStream();
        boolean[] secondaryWeaponBones = skinnedForWeaponHand ? selectSecondaryWeaponBones(vertices) : null;
        int px = 0, py = 0, pz = 0;
        for (int i = 0; i < vertices.size(); i++) {
            V vert = vertices.get(i);
            int flags = 0;
            int dx = vert.x - px, dy = vert.y - py, dz = vert.z - pz;
            if (dx != 0) { flags |= 1; writeSmart(xData, dx); }
            if (dy != 0) { flags |= 2; writeSmart(yData, dy); }
            if (dz != 0) { flags |= 4; writeSmart(zData, dz); }
            vertexFlags.write(flags);
            if (skinnedForWeaponHand) {
                vertexBones.write(secondaryWeaponBones[i] ? 200 : 50);
            }
            px = vert.x; py = vert.y; pz = vert.z;
        }

        ByteArrayOutputStream triangleTypes = new ByteArrayOutputStream();
        ByteArrayOutputStream triangleIndices = new ByteArrayOutputStream();
        ByteArrayOutputStream colors = new ByteArrayOutputStream();
        ByteArrayOutputStream triangleBones = new ByteArrayOutputStream();
        int last = 0;
        for (F face : faces) {
            triangleTypes.write(1);
            writeSmart(triangleIndices, face.a - last);
            writeSmart(triangleIndices, face.b - face.a);
            writeSmart(triangleIndices, face.c - face.b);
            last = face.c;
            writeShort(colors, face.color);
            if (skinnedForWeaponHand) {
                triangleBones.write(29);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(vertexFlags.toByteArray());
        out.write(triangleTypes.toByteArray());
        if (skinnedForWeaponHand) {
            out.write(triangleBones.toByteArray());
            out.write(vertexBones.toByteArray());
        }
        out.write(triangleIndices.toByteArray());
        out.write(colors.toByteArray());
        out.write(xData.toByteArray());
        out.write(yData.toByteArray());
        out.write(zData.toByteArray());

        writeShort(out, vertices.size());
        writeShort(out, faces.size());
        out.write(0); // textured count
        out.write(0); // triangle info
        out.write(skinnedForWeaponHand ? 0 : 10); // equipped model should depth-sort behind NPCs/scenery
        out.write(0); // alpha
        out.write(skinnedForWeaponHand ? 1 : 0); // triangle bones
        out.write(skinnedForWeaponHand ? 1 : 0); // vertex bones
        writeShort(out, xData.size());
        writeShort(out, yData.size());
        writeShort(out, zData.size());
        writeShort(out, triangleIndices.size());
        return out.toByteArray();
    }

    private static boolean[] selectSecondaryWeaponBones(List<V> vertices) {
        boolean[] selected = new boolean[vertices.size()];
        int targetCount = Math.min(8, vertices.size());
        for (int n = 0; n < targetCount; n++) {
            int bestIndex = -1;
            double bestScore = Double.POSITIVE_INFINITY;
            for (int i = 0; i < vertices.size(); i++) {
                if (selected[i]) {
                    continue;
                }
                V vert = vertices.get(i);
                double dx = vert.x + 31.5;
                double dy = vert.y + 84.5;
                double dz = vert.z + 1.0;
                double score = dx * dx + dy * dy + dz * dz;
                if (score < bestScore) {
                    bestScore = score;
                    bestIndex = i;
                }
            }
            if (bestIndex >= 0) {
                selected[bestIndex] = true;
            }
        }
        return selected;
    }

    private static void writeSmart(ByteArrayOutputStream out, int value) {
        if (value >= -64 && value < 64) {
            out.write(value + 64);
        } else {
            writeShort(out, value + 49152);
        }
    }

    private static int hsl(int h, int s, int l) {
        int hue = Math.max(0, Math.min(63, h * 64 / 360));
        int sat = Math.max(0, Math.min(7, s * 8 / 100));
        int light = Math.max(0, Math.min(127, l * 128 / 100));
        return (hue << 10) | (sat << 7) | light;
    }

    private static byte[] packSingleChunk(byte[][] files) throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (byte[] file : files) data.write(file == null ? new byte[0] : file);
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
        out.write((version >>> 8) & 0xFF);
        out.write(version & 0xFF);
        return out.toByteArray();
    }

    private static void writeString(ByteArrayOutputStream out, String value) throws Exception {
        out.write(value.getBytes("Cp1252"));
        out.write(0);
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
        if (targetIndex < 0) throw new IllegalArgumentException("Group not found in index: " + targetGroup);
        if (names) pos += size * 4;
        int checksumOffset = pos + targetIndex * 4;
        index[checksumOffset] = (byte) (checksum >>> 24);
        index[checksumOffset + 1] = (byte) (checksum >>> 16);
        index[checksumOffset + 2] = (byte) (checksum >>> 8);
        index[checksumOffset + 3] = (byte) checksum;
    }
}
