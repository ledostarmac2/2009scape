import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.models.JagexColor;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.Js5Index;

public class ImportOsrsItemModels {
    private static final int ITEM_ARCHIVE = 19;
    private static final int MODEL_ARCHIVE = 7;

    private record ImportItem(
            int itemId,
            String name,
            int ground,
            int male0,
            int male1,
            int female0,
            int female1,
            int zoom2d,
            int xan2d,
            int yan2d,
            int zan2d,
            int xOffset2d,
            int yOffset2d) {}

    private static final ImportItem[] ITEMS = {
            new ImportItem(14659, "Twisted bow", 32799, 32674, -1, 39561, -1, 1200, 508, 124, 0, 7, 2),
            new ImportItem(14660, "Abyssal dagger", 29598, 29425, -1, 42619, -1, 760, 472, 1276, 0, -5, 3),
            new ImportItem(14661, "Ghrazi rapier", 35739, 35374, -1, 35369, -1, 1570, 400, 360, 0, 0, -5),
            new ImportItem(14662, "Scythe of vitur", 35742, 35371, -1, 32906, -1, 2160, 584, 12, 0, -1, 2),
            new ImportItem(14663, "Ancestral hat", 32794, 32655, -1, 32663, -1, 980, 208, 220, 0, 0, -18),
            new ImportItem(14664, "Ancestral robe top", 32790, 32657, 32658, 32664, 32665, 1870, 376, 176, 0, 0, -3),
            new ImportItem(14665, "Ancestral robe bottom", 32787, 32653, -1, 32662, -1, 1690, 408, 2024, 0, 5, 7),
    };

    private record V(int x, int y, int z, int bone) {}
    private record F(int a, int b, int c, int color, int bone) {}

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException("Usage: ImportOsrsItemModels <cacheDir> <backupDir> <modelContainerDir> <objOutDir>");
        }
        File cacheDir = new File(args[0]);
        Path backupDir = Path.of(args[1]);
        Path modelDir = Path.of(args[2]);
        Path objOut = Path.of(args[3]);
        Files.createDirectories(backupDir);
        Files.createDirectories(objOut);
        backup(cacheDir, backupDir, "main_file_cache.dat2");
        backup(cacheDir, backupDir, "main_file_cache.idx7");
        backup(cacheDir, backupDir, "main_file_cache.idx19");
        backup(cacheDir, backupDir, "main_file_cache.idx255");

        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "rw", Long.MAX_VALUE), 5200, 0);
        Cache master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx255"), "rw", Long.MAX_VALUE), 6000, 0), 1000000);

        List<Integer> patchedModels = new ArrayList<>();
        for (ImportItem item : ITEMS) {
            for (int modelId : new int[] { item.ground, item.male0, item.male1, item.female0, item.female1 }) {
                if (modelId < 0 || patchedModels.contains(modelId)) {
                    continue;
                }
                ModelDefinition model = loadOsrsModel(modelDir.resolve(modelId + ".container"), modelId);
                exportObj(model, objOut.resolve(modelId + ".obj"), objOut.resolve(modelId + ".mtl"));
                boolean worn = modelId == item.male0 || modelId == item.male1 || modelId == item.female0 || modelId == item.female1;
                patchSingleFileGroup(cacheDir, data, master, MODEL_ARCHIVE, modelId, encodeOldModel(model, item, worn));
                patchedModels.add(modelId);
                System.out.println("Patched model " + modelId + " vertices=" + model.vertexCount + " faces=" + model.faceCount
                        + " worn=" + worn + " vskin=" + (model.packedVertexGroups != null));
            }
            patchItemDefinition(cacheDir, data, master, item);
            System.out.println("Patched item " + item.itemId + " " + item.name);
        }
    }

    private static ModelDefinition loadOsrsModel(Path container, int modelId) throws Exception {
        byte[] packed = Files.readAllBytes(container);
        byte[] raw = Js5Compression.uncompress(packed);
        ModelDefinition model = new ModelLoader().load(modelId, raw);
        if (model.vertexCount == 0 || model.faceCount == 0) {
            throw new IllegalStateException("Decoded empty OSRS model " + modelId + " from " + container);
        }
        return model;
    }

    private static void exportObj(ModelDefinition model, Path objPath, Path mtlPath) throws Exception {
        Files.createDirectories(objPath.getParent());
        try (PrintWriter obj = new PrintWriter(Files.newBufferedWriter(objPath));
             PrintWriter mtl = new PrintWriter(Files.newBufferedWriter(mtlPath))) {
            obj.println("mtllib " + mtlPath.getFileName());
            obj.println("o osrs_model_" + model.id);
            for (int i = 0; i < model.vertexCount; i++) {
                obj.println("v " + model.vertexX[i] + " " + (-model.vertexY[i]) + " " + (-model.vertexZ[i]));
            }
            for (int i = 0; i < model.faceCount; i++) {
                obj.println("usemtl m" + materialColor(model, i));
                obj.println("f " + (model.faceIndices1[i] + 1) + " " + (model.faceIndices2[i] + 1) + " " + (model.faceIndices3[i] + 1));
            }

            boolean[] written = new boolean[65536];
            for (int i = 0; i < model.faceCount; i++) {
                int color = materialColor(model, i) & 0xFFFF;
                if (written[color]) {
                    continue;
                }
                written[color] = true;
                int rgb = JagexColor.HSLtoRGB((short) color, JagexColor.BRIGHTNESS_MIN);
                mtl.println("newmtl m" + color);
                mtl.println("Kd " + (((rgb >> 16) & 0xFF) / 255.0) + " " + (((rgb >> 8) & 0xFF) / 255.0) + " " + ((rgb & 0xFF) / 255.0));
            }
        }
    }

    private static byte[] encodeOldModel(ModelDefinition model, ImportItem item, boolean worn) throws Exception {
        List<V> vertices = new ArrayList<>();
        Bounds bounds = Bounds.of(model);
        for (int i = 0; i < model.vertexCount; i++) {
            int x;
            int y;
            int z;
            if (worn) {
                x = clamp(model.vertexX[i]);
                y = clamp(model.vertexY[i]);
                z = clamp(model.vertexZ[i]);
            } else {
                x = clamp((int) Math.round((model.vertexX[i] - bounds.centerX()) * (46.0 / bounds.width())));
                y = clamp((int) Math.round((model.vertexZ[i] - bounds.centerZ()) * (18.0 / bounds.depth())));
                z = clamp((int) Math.round(38.0 - (model.vertexY[i] - bounds.minY) * (76.0 / bounds.height())));
            }
            // Use the real OSRS per-vertex skin (vskin) labels when present. OSRS inherited the
            // RS2 transform-group numbering, so these should line up with the 2009 player skeleton.
            // If a model has no vskin we emit -1 (static, but solid) -- never a hardcoded guess.
            int bone = model.packedVertexGroups != null ? model.packedVertexGroups[i] : -1;
            vertices.add(new V(x, y, z, bone));
        }
        List<F> faces = new ArrayList<>();
        for (int i = 0; i < model.faceCount; i++) {
            int color = materialColor(model, i);
            // Single, natural winding. OSRS and the 2009 client share the RS2 winding
            // convention, so each face is emitted once and renders solid. The previous
            // double-winding hack added a reversed copy of every face, which z-fights the
            // real face and punches holes -- that was the "see-through character".
            // Triangle skins (tskin) are intentionally omitted here: the old code mis-read
            // packedTransparencyVertexGroups (face alpha groups) as skeleton skins. Worn
            // deformation is driven by the per-vertex skins (vskin) written below.
            faces.add(new F(model.faceIndices1[i], model.faceIndices2[i], model.faceIndices3[i], color, -1));
        }
        return encodeOldModel(vertices, faces);
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        static Bounds of(ModelDefinition model) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < model.vertexCount; i++) {
                minX = Math.min(minX, model.vertexX[i]);
                maxX = Math.max(maxX, model.vertexX[i]);
                minY = Math.min(minY, model.vertexY[i]);
                maxY = Math.max(maxY, model.vertexY[i]);
                minZ = Math.min(minZ, model.vertexZ[i]);
                maxZ = Math.max(maxZ, model.vertexZ[i]);
            }
            return new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
        }

        double centerX() {
            return (minX + maxX) / 2.0;
        }

        double centerZ() {
            return (minZ + maxZ) / 2.0;
        }

        double width() {
            return Math.max(1.0, maxX - minX);
        }

        double height() {
            return Math.max(1.0, maxY - minY);
        }

        double depth() {
            return Math.max(1.0, maxZ - minZ);
        }
    }

    private static int materialColor(ModelDefinition model, int face) {
        if (model.faceColors != null) {
            int color = model.faceColors[face] & 0xFFFF;
            if (color != 127) {
                return color;
            }
        }
        if (model.faceTextures != null && model.faceTextures[face] >= 0) {
            int texture = model.faceTextures[face] & 0xFFFF;
            return hsl((texture * 37) % 360, 38, 52);
        }
        return hsl(35, 20, 58);
    }

    private static int clamp(int value) {
        return Math.max(-8192, Math.min(8191, value));
    }

    private static void patchItemDefinition(File cacheDir, BufferedFile data, Cache master, ImportItem item) throws Exception {
        int group = item.itemId >>> 8;
        int file = item.itemId & 255;
        byte[] packedIndex = master.read(ITEM_ARCHIVE);
        int archiveIndexVersion = readTrailingVersion(packedIndex);
        byte[] indexBytes = Js5Compression.uncompress(packedIndex);
        Js5Index index = new Js5Index(packedIndex, Buffer.crc32(packedIndex, packedIndex.length));
        byte[][] files = readGroupFiles(cacheDir, ITEM_ARCHIVE, group, index);
        files[file] = rewriteItemDefinition(files[file], item);
        writeMultiFileGroup(cacheDir, data, master, ITEM_ARCHIVE, group, files, index.groupVersions[group], indexBytes, archiveIndexVersion);
    }

    private static byte[] rewriteItemDefinition(byte[] def, ImportItem item) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean seen1 = false, seen4 = false, seen5 = false, seen6 = false, seen7 = false, seen8 = false, seen23 = false, seen24 = false, seen25 = false, seen26 = false, seen95 = false;
        int i = 0;
        while (i < def.length) {
            int opcode = def[i++] & 0xFF;
            if (opcode == 0) {
                break;
            }
            if (opcode == 1) {
                out.write(opcode); writeShort(out, item.ground); i += 2; seen1 = true;
            } else if (opcode == 4) {
                out.write(opcode); writeShort(out, item.zoom2d); i += 2; seen4 = true;
            } else if (opcode == 5) {
                out.write(opcode); writeShort(out, item.xan2d); i += 2; seen5 = true;
            } else if (opcode == 6) {
                out.write(opcode); writeShort(out, item.yan2d); i += 2; seen6 = true;
            } else if (opcode == 7) {
                out.write(opcode); writeShort(out, item.xOffset2d); i += 2; seen7 = true;
            } else if (opcode == 8) {
                out.write(opcode); writeShort(out, item.yOffset2d); i += 2; seen8 = true;
            } else if (opcode == 23) {
                if (shouldWriteWornModels(item)) { out.write(opcode); writeShort(out, item.male0); out.write(0); }
                i += wornModelOpcodeLength(def, i); seen23 = true;
            } else if (opcode == 24) {
                if (shouldWriteWornModels(item) && item.male1 >= 0) { out.write(opcode); writeShort(out, item.male1); }
                i += 2; seen24 = true;
            } else if (opcode == 25) {
                if (shouldWriteWornModels(item)) { out.write(opcode); writeShort(out, item.female0); out.write(0); }
                i += wornModelOpcodeLength(def, i); seen25 = true;
            } else if (opcode == 26) {
                if (shouldWriteWornModels(item) && item.female1 >= 0) { out.write(opcode); writeShort(out, item.female1); }
                i += 2; seen26 = true;
            } else if (opcode == 95) {
                if (item.zan2d != 0) { out.write(opcode); writeShort(out, item.zan2d); }
                i += 2; seen95 = true;
            } else if (opcode == 125 || opcode == 126) {
                i += 3;
            } else if (opcode == 40 || opcode == 41) {
                int count = def[i] & 0xFF;
                i += 1 + count * 4;
            } else {
                out.write(opcode);
                i = copyOpcodePayload(def, i, opcode, out);
            }
        }
        if (!seen1) { out.write(1); writeShort(out, item.ground); }
        if (!seen4) { out.write(4); writeShort(out, item.zoom2d); }
        if (!seen5) { out.write(5); writeShort(out, item.xan2d); }
        if (!seen6) { out.write(6); writeShort(out, item.yan2d); }
        if (!seen7) { out.write(7); writeShort(out, item.xOffset2d); }
        if (!seen8) { out.write(8); writeShort(out, item.yOffset2d); }
        if (item.zan2d != 0 && !seen95) { out.write(95); writeShort(out, item.zan2d); }
        if (shouldWriteWornModels(item) && item.male0 >= 0 && !seen23) { out.write(23); writeShort(out, item.male0); out.write(0); }
        if (shouldWriteWornModels(item) && item.male1 >= 0 && !seen24) { out.write(24); writeShort(out, item.male1); }
        if (shouldWriteWornModels(item) && item.female0 >= 0 && !seen25) { out.write(25); writeShort(out, item.female0); out.write(0); }
        if (shouldWriteWornModels(item) && item.female1 >= 0 && !seen26) { out.write(26); writeShort(out, item.female1); }
        out.write(0);
        return out.toByteArray();
    }

    private static boolean shouldWriteWornModels(ImportItem item) {
        // All seven imported items now write worn models (weapons 14659-14662 + ancestral
        // armor 14663-14665). Armor rigs via the real OSRS vskin labels read in encodeOldModel.
        return item.itemId >= 14659 && item.itemId <= 14665;
    }

    private static int wornModelOpcodeLength(byte[] def, int payloadStart) {
        if (payloadStart + 2 >= def.length) {
            return 2;
        }
        int maybeNextOpcode = def[payloadStart + 2] & 0xFF;
        return isLikelyItemOpcode(maybeNextOpcode) ? 2 : 3;
    }

    private static boolean isLikelyItemOpcode(int opcode) {
        return opcode == 0 || opcode == 1 || opcode == 2 || opcode == 4 || opcode == 5 || opcode == 6 ||
                opcode == 7 || opcode == 8 || opcode == 11 || opcode == 12 || opcode == 16 ||
                opcode == 23 || opcode == 24 || opcode == 25 || opcode == 26 || opcode == 40 ||
                opcode == 41 || opcode == 65 || opcode == 90 || opcode == 91 || opcode == 92 ||
                opcode == 93 || opcode == 95 || opcode == 96 || opcode == 97 || opcode == 98 ||
                opcode == 110 || opcode == 111 || opcode == 112 || opcode == 115 || opcode == 121 ||
                opcode == 122 || opcode == 125 || opcode == 126 || opcode == 127 || opcode == 128 ||
                opcode == 129 || opcode == 130 || opcode == 132 || (opcode >= 30 && opcode < 40) ||
                (opcode >= 100 && opcode < 110);
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
            for (int n = 0; n < count; n++) {
                cumulative += readInt(uncompressed, pos);
                pos += 4;
                sizes[n] += cumulative;
            }
        }
        byte[][] logical = new byte[count][];
        for (int n = 0; n < count; n++) {
            logical[n] = new byte[sizes[n]];
        }
        int[] offsets = new int[count];
        pos = table;
        int dataPos = 0;
        for (int chunk = 0; chunk < chunks; chunk++) {
            int cumulative = 0;
            for (int n = 0; n < count; n++) {
                cumulative += readInt(uncompressed, pos);
                pos += 4;
                System.arraycopy(uncompressed, dataPos, logical[n], offsets[n], cumulative);
                offsets[n] += cumulative;
                dataPos += cumulative;
            }
        }
        for (int n = 0; n < count; n++) {
            int fileId = index.fileIds[group] == null ? n : index.fileIds[group][n];
            files[fileId] = logical[n];
        }
        return files;
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

    private static byte[] encodeOldModel(List<V> vertices, List<F> faces) throws Exception {
        boolean hasVertexBones = vertices.stream().anyMatch(vertex -> vertex.bone >= 0);
        boolean hasTriangleBones = faces.stream().anyMatch(face -> face.bone >= 0);
        ByteArrayOutputStream vertexFlags = new ByteArrayOutputStream();
        ByteArrayOutputStream xData = new ByteArrayOutputStream();
        ByteArrayOutputStream yData = new ByteArrayOutputStream();
        ByteArrayOutputStream zData = new ByteArrayOutputStream();
        ByteArrayOutputStream vertexBones = new ByteArrayOutputStream();
        int px = 0, py = 0, pz = 0;
        for (V vert : vertices) {
            int flags = 0;
            int dx = vert.x - px, dy = vert.y - py, dz = vert.z - pz;
            if (dx != 0) { flags |= 1; writeSmart(xData, dx); }
            if (dy != 0) { flags |= 2; writeSmart(yData, dy); }
            if (dz != 0) { flags |= 4; writeSmart(zData, dz); }
            vertexFlags.write(flags);
            if (hasVertexBones) {
                vertexBones.write(Math.max(0, Math.min(255, vert.bone)));
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
            if (hasTriangleBones) {
                triangleBones.write(Math.max(0, Math.min(255, face.bone)));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(vertexFlags.toByteArray());
        out.write(triangleTypes.toByteArray());
        if (hasTriangleBones) {
            out.write(triangleBones.toByteArray());
        }
        if (hasVertexBones) {
            out.write(vertexBones.toByteArray());
        }
        out.write(triangleIndices.toByteArray());
        out.write(colors.toByteArray());
        out.write(xData.toByteArray());
        out.write(yData.toByteArray());
        out.write(zData.toByteArray());
        writeShort(out, vertices.size());
        writeShort(out, faces.size());
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(hasTriangleBones ? 1 : 0);
        out.write(hasVertexBones ? 1 : 0);
        writeShort(out, xData.size());
        writeShort(out, yData.size());
        writeShort(out, zData.size());
        writeShort(out, triangleIndices.size());
        return out.toByteArray();
    }

    private static int copyOpcodePayload(byte[] def, int i, int opcode, ByteArrayOutputStream out) {
        int len;
        if (opcode == 4 || opcode == 5 || opcode == 6 || opcode == 7 || opcode == 8 ||
                opcode == 11 || opcode == 12 || opcode == 16 || opcode == 65 || opcode == 78 ||
                opcode == 79 || opcode == 90 || opcode == 91 || opcode == 92 || opcode == 93 ||
                opcode == 95 || opcode == 96 || opcode == 97 || opcode == 98 || opcode == 110 ||
                opcode == 111 || opcode == 112 || opcode == 115 || opcode == 121 || opcode == 122 ||
                opcode == 125 || opcode == 126 || opcode == 127 || opcode == 128 || opcode == 129 ||
                opcode == 130 || opcode == 132) {
            switch (opcode) {
                case 11, 16, 65 -> len = 0;
                case 12 -> len = 4;
                case 125, 126, 127, 128, 129, 130 -> len = 3;
                case 132 -> len = 1 + 2 * (def[i] & 0xFF);
                default -> len = 2;
            }
            out.write(def, i, len);
            return i + len;
        } else if (opcode == 2 || (opcode >= 30 && opcode < 35) || (opcode >= 35 && opcode < 40)) {
            int start = i;
            while (def[i++] != 0) {}
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
                    while (def[i++] != 0) {}
                } else {
                    i += 4;
                }
            }
            out.write(def, start, i - start);
            return i;
        }
        throw new IllegalArgumentException("Unsupported item opcode " + opcode);
    }

    private static void backup(File cacheDir, Path backupDir, String name) throws Exception {
        Path target = backupDir.resolve(name);
        if (!Files.exists(target)) {
            Files.copy(new File(cacheDir, name).toPath(), target);
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
        out.write((version >>> 8) & 0xFF);
        out.write(version & 0xFF);
        return out.toByteArray();
    }

    private static int readInt(byte[] bytes, int pos) {
        return ((bytes[pos] & 0xFF) << 24) | ((bytes[pos + 1] & 0xFF) << 16) | ((bytes[pos + 2] & 0xFF) << 8) | (bytes[pos + 3] & 0xFF);
    }

    private static int readTrailingVersion(byte[] data) {
        if (data.length < 2) {
            return 0;
        }
        return ((data[data.length - 2] & 0xFF) << 8) | (data[data.length - 1] & 0xFF);
    }

    private static void writeSmart(ByteArrayOutputStream out, int value) {
        if (value >= -64 && value < 64) {
            out.write(value + 64);
        } else {
            writeShort(out, value + 49152);
        }
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

    private static int hsl(int h, int s, int l) {
        int hue = Math.max(0, Math.min(63, h * 64 / 360));
        int sat = Math.max(0, Math.min(7, s * 8 / 100));
        int light = Math.max(0, Math.min(127, l * 128 / 100));
        return (hue << 10) | (sat << 7) | light;
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
