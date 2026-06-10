import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.zip.GZIPOutputStream;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import rt4.BufferedFile;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;
import rt4.RawModel;

/**
 * Batch OSRS -> 2009scape item importer, driven by a generated plan file
 * (tools/osrs-import/plan.txt, produced by tools/osrs_import_pipeline.py from
 * tools/osrs-import/manifest.json). For every plan row it:
 *
 *   1. Converts each referenced OSRS model (data/import/osrs-model-groups/<id>.container)
 *      into the 2009 "old" model format and writes it to model archive 7, CREATING the
 *      group when the id does not exist in the 2009 cache (post-530 OSRS model ids).
 *      - Inventory/ground models are imported untouched (identity), so the authentic
 *        OSRS zoom2d/xan2d/yan2d icon camera lines up pixel-perfect.
 *      - Worn models keep their native OSRS vskin labels when present; otherwise they
 *        are rigged nearest-bone against a native 2009 reference model of the same slot.
 *   2. Writes the item definition to archive 19 (name, models, icon camera, Wear option,
 *      tradeable flag), CREATING the definition file inside its group when the item id
 *      is new (index file-list reserialization, AddItemToCache approach). Weapons with a
 *      configTemplate in the plan inherit the native item's client wield/attack opcodes
 *      (render_anim etc.) from that template, matching the custom-item patcher approach.
 *
 * The full pipeline (extraction, server configs, previews, docs) is described in
 * docs/osrs-item-import.md.
 *
 * Usage: ImportOsrsItemBatch <cacheDir> <backupDir> <modelContainerDir> <planFile>
 */
public class ImportOsrsItemBatch {
    private static final int ITEM_ARCHIVE = 19;
    private static final int MODEL_ARCHIVE = 7;

    private record PlanItem(
            int itemId, String name,
            int ground, int male0, int male1, int female0, int female1, int maleHead, int femaleHead,
            int zoom2d, int xan2d, int yan2d, int zan2d, int xOffset2d, int yOffset2d,
            boolean tradeable, int priority, int headPriority,
            int rigM0, int rigF0, int rigMHead, int rigFHead, int wornDy,
            boolean equippable, int facePriority, int configTemplate) {}

    private record V(int x, int y, int z, int bone) {}
    private record F(int a, int b, int c, int color, int priority, int textureId, int textureCoord) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: ImportOsrsItemBatch <cacheDir> <backupDir> <modelContainerDir> <planFile>");
        }
        File cacheDir = new File(args[0]);
        Path backupDir = Path.of(args[1]);
        Path modelDir = Path.of(args[2]);
        List<PlanItem> items = readPlan(Path.of(args[3]));
        Map<Integer, Map<Integer, Integer>> recolors = loadRecolors(Path.of(args[3]).getParent().resolve("recolor.json"));
        boolean rs3Source = modelDir.toString().replace('\\', '/').contains("rs3-model-groups");

        Files.createDirectories(backupDir);
        for (String f : new String[] {"main_file_cache.dat2", "main_file_cache.idx7", "main_file_cache.idx19", "main_file_cache.idx255"}) {
            Path target = backupDir.resolve(f);
            if (!Files.exists(target)) Files.copy(new File(cacheDir, f).toPath(), target);
        }

        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "rw", Long.MAX_VALUE), 5200, 0);
        Cache master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx255"), "rw", Long.MAX_VALUE), 6000, 0), 1000000);
        Cache modelArchive = new Cache(MODEL_ARCHIVE, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + MODEL_ARCHIVE), "rw", Long.MAX_VALUE), 6000, 0), 1000000);
        Cache itemArchive = new Cache(ITEM_ARCHIVE, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + ITEM_ARCHIVE), "rw", Long.MAX_VALUE), 6000, 0), 1000000);

        // ---- pass 1: models (archive 7) ----
        byte[] packedModelIndex = master.read(MODEL_ARCHIVE);
        int modelIndexTrailer = readTrailingVersion(packedModelIndex);
        IndexData modelIndex = IndexData.parse(Js5Compression.uncompress(packedModelIndex));
        Map<Integer, byte[]> encodedModels = new LinkedHashMap<>();
        for (PlanItem item : items) {
            Map<Integer, Integer> recolor = recolors.get(item.itemId);
            collectGroundModel(encodedModels, modelDir, cacheDir, item, recolor, rs3Source);
            collectModel(encodedModels, modelDir, cacheDir, item, item.male0, true, item.priority, item.rigM0, recolor, rs3Source);
            collectModel(encodedModels, modelDir, cacheDir, item, item.male1, true, item.priority, item.rigM0, recolor, rs3Source);
            collectModel(encodedModels, modelDir, cacheDir, item, item.female0, true, item.priority, item.rigF0, recolor, rs3Source);
            collectModel(encodedModels, modelDir, cacheDir, item, item.female1, true, item.priority, item.rigF0, recolor, rs3Source);
            collectModel(encodedModels, modelDir, cacheDir, item, item.maleHead, true, item.headPriority, item.rigMHead, recolor, rs3Source);
            collectModel(encodedModels, modelDir, cacheDir, item, item.femaleHead, true, item.headPriority, item.rigFHead, recolor, rs3Source);
        }
        for (Map.Entry<Integer, byte[]> entry : encodedModels.entrySet()) {
            int group = entry.getKey();
            int slot = modelIndex.find(group);
            int version;
            if (slot < 0) {
                version = 1;
                modelIndex.insertGroup(group, version, new int[] {0});
                slot = modelIndex.find(group);
            } else {
                version = modelIndex.versions[slot];
            }
            byte[] packedNoVersion = wrapUncompressed(entry.getValue());
            modelIndex.crcs[slot] = rt4.Buffer.crc32(packedNoVersion, packedNoVersion.length);
            byte[] packed = appendVersion(packedNoVersion, version);
            if (!modelArchive.write(group, packed.length, packed)) {
                throw new IllegalStateException("write model group " + group + " failed");
            }
            System.out.println("model " + group + " written (" + entry.getValue().length + " bytes, version " + version + ")");
        }
        byte[] newModelIndex = appendVersion(wrapGzip(modelIndex.emit()), modelIndexTrailer);
        if (!master.write(MODEL_ARCHIVE, newModelIndex.length, newModelIndex)) {
            throw new IllegalStateException("write model archive index failed");
        }

        // ---- pass 2: item definitions (archive 19) ----
        byte[] packedItemIndex = master.read(ITEM_ARCHIVE);
        int itemIndexTrailer = readTrailingVersion(packedItemIndex);
        IndexData itemIndex = IndexData.parse(Js5Compression.uncompress(packedItemIndex));
        // group items by archive group so each group is rebuilt once
        Map<Integer, List<PlanItem>> byGroup = new LinkedHashMap<>();
        for (PlanItem item : items) {
            byGroup.computeIfAbsent(item.itemId >>> 8, g -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<Integer, List<PlanItem>> groupEntry : byGroup.entrySet()) {
            int group = groupEntry.getKey();
            int slot = itemIndex.find(group);
            if (slot < 0) {
                throw new IllegalStateException("item group " + group + " missing from archive 19 (unexpected: ids should stay below the cache item ceiling)");
            }
            byte[][] files = readGroupFiles(itemArchive, itemIndex, group);
            boolean structural = false;
            for (PlanItem item : groupEntry.getValue()) {
                int file = item.itemId & 255;
                if (file >= files.length) {
                    byte[][] grown = new byte[file + 1][];
                    System.arraycopy(files, 0, grown, 0, files.length);
                    files = grown;
                }
                if (files[file] == null) structural = true;
                files[file] = rewriteItemDefinition(files[file], item, itemArchive, itemIndex);
                System.out.println("item " + item.itemId + " '" + item.name + "' def " + (structural ? "created" : "updated"));
            }
            int[] newFileIds = presentFileIds(files);
            int version = itemIndex.versions[slot] + (structural ? 1 : 0);
            byte[] packedNoVersion = wrapUncompressed(packSingleChunk(files));
            itemIndex.crcs[slot] = rt4.Buffer.crc32(packedNoVersion, packedNoVersion.length);
            itemIndex.versions[slot] = version;
            itemIndex.fileIds[slot] = newFileIds;
            byte[] packed = appendVersion(packedNoVersion, version);
            if (!itemArchive.write(group, packed.length, packed)) {
                throw new IllegalStateException("write item group " + group + " failed");
            }
        }
        byte[] newItemIndex = appendVersion(wrapGzip(itemIndex.emit()), itemIndexTrailer);
        if (!master.write(ITEM_ARCHIVE, newItemIndex.length, newItemIndex)) {
            throw new IllegalStateException("write item archive index failed");
        }
        System.out.println("Imported " + items.size() + " items, " + encodedModels.size() + " models.");
    }

    // ------------------------------------------------------------------ plan

    private static List<PlanItem> readPlan(Path planFile) throws Exception {
        List<PlanItem> items = new ArrayList<>();
        for (String line : Files.readAllLines(planFile, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\|", -1);
            if (p.length != 26) {
                throw new IllegalArgumentException("Bad plan row (" + p.length + " fields): " + line);
            }
            items.add(new PlanItem(
                    Integer.parseInt(p[0]), p[1],
                    Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]),
                    Integer.parseInt(p[5]), Integer.parseInt(p[6]), Integer.parseInt(p[7]), Integer.parseInt(p[8]),
                    Integer.parseInt(p[9]),
                    normalizeIconAngle(Integer.parseInt(p[10])),
                    normalizeIconAngle(Integer.parseInt(p[11])),
                    normalizeIconAngle(Integer.parseInt(p[12])),
                    Integer.parseInt(p[13]), Integer.parseInt(p[14]),
                    Boolean.parseBoolean(p[15]), Integer.parseInt(p[16]), Integer.parseInt(p[17]),
                    Integer.parseInt(p[18]), Integer.parseInt(p[19]), Integer.parseInt(p[20]), Integer.parseInt(p[21]),
                    Integer.parseInt(p[22]), Boolean.parseBoolean(p[23]), Integer.parseInt(p[24]),
                    Integer.parseInt(p[25])));
        }
        if (items.isEmpty()) throw new IllegalStateException("Empty plan: " + planFile);
        return items;
    }

    // ---------------------------------------------------------------- models

    private static Map<Integer, Map<Integer, Integer>> loadRecolors(Path recolorFile) throws Exception {
        Map<Integer, Map<Integer, Integer>> out = new HashMap<>();
        if (!Files.exists(recolorFile)) {
            return out;
        }
        String json = Files.readString(recolorFile, StandardCharsets.UTF_8).trim();
        if (json.isEmpty() || json.equals("{}")) {
            return out;
        }
        Map<String, Map<String, Integer>> raw = new Gson().fromJson(json,
                new TypeToken<Map<String, Map<String, Integer>>>() {}.getType());
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, Map<String, Integer>> entry : raw.entrySet()) {
            Map<Integer, Integer> map = new HashMap<>();
            for (Map.Entry<String, Integer> color : entry.getValue().entrySet()) {
                map.put(Integer.parseInt(color.getKey()), color.getValue());
            }
            out.put(Integer.parseInt(entry.getKey()), map);
        }
        return out;
    }

    private static void collectGroundModel(Map<Integer, byte[]> out, Path modelDir, File cacheDir, PlanItem item,
                                           Map<Integer, Integer> recolor, boolean rs3Source) throws Exception {
        if (item.ground < 0 || out.containsKey(item.ground)) {
            return;
        }
        try {
            collectModel(out, modelDir, cacheDir, item, item.ground, false, 0, -1, recolor, rs3Source);
        } catch (IllegalStateException ex) {
            if (item.male0 < 0 || item.male0 == item.ground) {
                throw ex;
            }
            System.out.println("ground model " + item.ground + " unusable for item " + item.itemId
                    + " (" + ex.getMessage() + "); using worn model " + item.male0 + " for inventory");
            ModelDefinition source = loadOsrsModel(modelDir.resolve(item.male0 + ".container"), item.male0);
            out.put(item.ground, encodeOldModel(source, false, 0, null, 0, -1, true, recolor, rs3Source));
        }
    }

    private static void collectModel(Map<Integer, byte[]> out, Path modelDir, File cacheDir, PlanItem item,
                                     int modelId, boolean worn, int priority, int rigRef,
                                     Map<Integer, Integer> recolor, boolean rs3Source)
            throws Exception {
        if (modelId < 0 || out.containsKey(modelId)) return;
        Path container = modelDir.resolve(modelId + ".container");
        if (!Files.exists(container) && modelExistsInCache(cacheDir, modelId)) {
            System.out.println("model " + modelId + " skipped (using existing native cache model)");
            return;
        }
        ModelDefinition model = loadOsrsModel(container, modelId);
        RefRig rig = (worn && rigRef >= 0) ? loadRigReference(cacheDir, rigRef) : null;
        boolean useNativeVskin = !rs3Source && model.packedVertexGroups != null;
        out.put(modelId, encodeOldModel(model, worn, priority, rig, worn ? item.wornDy : 0,
                worn ? item.facePriority : -1, !useNativeVskin, recolor, rs3Source));
        System.out.println("model " + modelId + " encoded for item " + item.itemId
                + " worn=" + worn + " vskin=" + useNativeVskin
                + (rig != null ? " rigRef=" + rigRef : "")
                + (rs3Source && worn ? " rs3Rigged" : ""));
    }

    private static ModelDefinition loadOsrsModel(Path container, int modelId) throws Exception {
        if (!Files.exists(container)) {
            throw new IllegalStateException("Missing model container " + container + " -- run the extract step first");
        }
        byte[] packed = Files.readAllBytes(container);
        byte[] raw = Js5Compression.uncompress(packed);
        ModelDefinition model;
        try {
            model = new ModelLoader().load(modelId, raw);
        } catch (RuntimeException ex) {
            try {
                model = new net.runelite.cache.definitions.loaders.TolerantModelLoader().loadTolerant(modelId, raw);
            } catch (RuntimeException ex2) {
                throw new IllegalStateException("Failed to decode model " + modelId + " from " + container, ex2);
            }
        }
        if (model.vertexCount == 0 || model.faceCount == 0) {
            throw new IllegalStateException("Decoded empty OSRS model " + modelId + " from " + container);
        }
        return model;
    }

    private static byte[] encodeOldModel(ModelDefinition model, boolean worn, int priority, RefRig rig, int wornDy,
                                         int facePriority, boolean rigVertices, Map<Integer, Integer> recolor,
                                         boolean rs3Source) throws Exception {
        int[] vx = model.vertexX;
        int[] vy = model.vertexY;
        int[] vz = model.vertexZ;
        if (rs3Source && worn) {
            double scale = rs3FitScale(vx, vy, vz, model.vertexCount);
            if (scale < 1.0) {
                vx = scaleCoords(vx, scale);
                vy = scaleCoords(vy, scale);
                vz = scaleCoords(vz, scale);
                System.out.println("model " + model.id + " scaled by " + String.format("%.4f", scale) + " to fit 2009 range");
            }
        } else if (rs3Source && !worn) {
            double scale = rs3IconFitScale(vx, vy, vz, model.vertexCount);
            if (scale < 1.0) {
                vx = scaleCoords(vx, scale);
                vy = scaleCoords(vy, scale);
                vz = scaleCoords(vz, scale);
                System.out.println("model " + model.id + " inventory scaled by "
                        + String.format("%.4f", scale) + " for icon fit");
            }
        }
        Bounds bounds = Bounds.of(model.vertexCount, vx, vy, vz);
        boolean nativeVskin = !rs3Source && model.packedVertexGroups != null;
        List<V> vertices = new ArrayList<>();
        for (int i = 0; i < model.vertexCount; i++) {
            // Identity import: OSRS and the 2009 client share the RS2 model space, so both
            // icon cameras and worn attachment line up without scaling or axis swaps.
            int x = clamp(vx[i]);
            int y = clamp(vy[i] + (worn ? wornDy : 0));
            int z = clamp(vz[i]);
            int bone = -1;
            if (worn) {
                bone = nativeVskin ? model.packedVertexGroups[i]
                        : !rigVertices || rig == null ? -1 : rig.nearestBone(vx[i], vy[i], vz[i], bounds);
            }
            vertices.add(new V(x, y, z, bone));
        }
        List<F> faces = new ArrayList<>();
        for (int i = 0; i < model.faceCount; i++) {
            int a = model.faceIndices1[i];
            int b = model.faceIndices2[i];
            int c = model.faceIndices3[i];
            if (!faceVertexValid(a, b, c, model.vertexCount)) {
                continue;
            }
            int facePrio = -1;
            if (worn) {
                if (facePriority >= 0) {
                    facePrio = facePriority;
                } else {
                    int cx = (vx[a] + vx[b] + vx[c]) / 3;
                    int cy = (vy[a] + vy[b] + vy[c]) / 3;
                    int cz = (vz[a] + vz[b] + vz[c]) / 3;
                    if (rig != null) {
                        facePrio = rig.nearestFacePriority(cx, cy, cz, bounds);
                    } else if (!rs3Source && model.faceRenderPriorities != null) {
                        facePrio = model.faceRenderPriorities[i] & 0xFF;
                    }
                }
            }
            int color;
            int textureId = -1;
            int textureCoord = -1;
            if (model.faceTextures != null && model.faceTextures[i] >= 0) {
                textureId = model.faceTextures[i] & 0xFFFF;
                textureCoord = model.textureCoords != null && model.textureCoords[i] >= 0
                        ? model.textureCoords[i] & 0xFF : 0;
                color = textureId;
            } else {
                color = materialColor(model, i, recolor);
            }
            faces.add(new F(a, b, c, color, facePrio, textureId, textureCoord));
        }
        if (faces.isEmpty()) {
            throw new IllegalStateException("No valid faces after decode for model " + model.id);
        }
        return encodeOldModel(vertices, faces, worn ? priority : 0, model);
    }

    private static boolean faceVertexValid(int a, int b, int c, int vertexCount) {
        return a >= 0 && b >= 0 && c >= 0 && a < vertexCount && b < vertexCount && c < vertexCount;
    }

    private record RefRig(int[] x, int[] y, int[] z, int[] bone, Bounds bounds,
                          double[][] triCentroids, int[] triPriorities) {
        int nearestBone(int vx, int vy, int vz, Bounds source) {
            double nx = (vx - source.minX()) / source.width();
            double ny = (vy - source.minY()) / source.height();
            double nz = (vz - source.minZ()) / source.depth();
            double best = Double.MAX_VALUE;
            int bestBone = -1;
            for (int i = 0; i < x.length; i++) {
                // Never inherit the root bone (0): vertices on the root stay put while the
                // limb animates, producing jagged artifacts.
                if (bone[i] == 0) continue;
                double rx = (x[i] - bounds.minX()) / bounds.width();
                double ry = (y[i] - bounds.minY()) / bounds.height();
                double rz = (z[i] - bounds.minZ()) / bounds.depth();
                double dist = (nx - rx) * (nx - rx) + (ny - ry) * (ny - ry) + (nz - rz) * (nz - rz);
                if (dist < best) {
                    best = dist;
                    bestBone = bone[i];
                }
            }
            return bestBone;
        }

        int nearestFacePriority(int vx, int vy, int vz, Bounds source) {
            if (triCentroids == null) {
                return -1;
            }
            double nx = (vx - source.minX()) / source.width();
            double ny = (vy - source.minY()) / source.height();
            double nz = (vz - source.minZ()) / source.depth();
            double best = Double.MAX_VALUE;
            int bestPriority = -1;
            for (int t = 0; t < triCentroids.length; t++) {
                double dx = nx - triCentroids[t][0];
                double dy = ny - triCentroids[t][1];
                double dz = nz - triCentroids[t][2];
                double dist = dx * dx + dy * dy + dz * dz;
                if (dist < best) {
                    best = dist;
                    bestPriority = triPriorities[t];
                }
            }
            return bestPriority;
        }
    }

    private static RefRig loadRigReference(File cacheDir, int referenceId) throws Exception {
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache models = new Cache(MODEL_ARCHIVE, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + MODEL_ARCHIVE), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        byte[] packed = models.read(referenceId);
        if (packed == null) throw new IllegalStateException("Missing rig reference model " + referenceId);
        RawModel reference = new RawModel(Js5Compression.uncompress(stripVersion(packed)));
        if (reference.vertexBones == null) throw new IllegalStateException("Rig reference model has no vertex bones: " + referenceId);
        int[] x = new int[reference.vertexCount];
        int[] y = new int[reference.vertexCount];
        int[] z = new int[reference.vertexCount];
        int[] bone = new int[reference.vertexCount];
        for (int i = 0; i < reference.vertexCount; i++) {
            x[i] = reference.vertexX[i];
            y[i] = reference.vertexY[i];
            z[i] = reference.vertexZ[i];
            bone[i] = reference.vertexBones[i] & 0xFF;
        }
        Bounds refBounds = Bounds.of(reference);
        double[][] triCentroids = null;
        int[] triPriorities = null;
        if (reference.trianglePriorities != null) {
            triCentroids = new double[reference.triangleCount][3];
            triPriorities = new int[reference.triangleCount];
            for (int t = 0; t < reference.triangleCount; t++) {
                int va = reference.triangleVertexA[t];
                int vb = reference.triangleVertexB[t];
                int vc = reference.triangleVertexC[t];
                int cx = (reference.vertexX[va] + reference.vertexX[vb] + reference.vertexX[vc]) / 3;
                int cy = (reference.vertexY[va] + reference.vertexY[vb] + reference.vertexY[vc]) / 3;
                int cz = (reference.vertexZ[va] + reference.vertexZ[vb] + reference.vertexZ[vc]) / 3;
                triCentroids[t][0] = (cx - refBounds.minX()) / refBounds.width();
                triCentroids[t][1] = (cy - refBounds.minY()) / refBounds.height();
                triCentroids[t][2] = (cz - refBounds.minZ()) / refBounds.depth();
                triPriorities[t] = reference.trianglePriorities[t] & 0xFF;
            }
        }
        return new RefRig(x, y, z, bone, refBounds, triCentroids, triPriorities);
    }

    private static boolean modelExistsInCache(File cacheDir, int modelId) throws Exception {
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache models = new Cache(MODEL_ARCHIVE, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + MODEL_ARCHIVE), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        return models.read(modelId) != null;
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        static Bounds of(ModelDefinition m) {
            return of(m.vertexCount, m.vertexX, m.vertexY, m.vertexZ);
        }
        static Bounds of(int count, int[] xs, int[] ys, int[] zs) {
            int[] r = range(count, xs, ys, zs);
            return new Bounds(r[0], r[1], r[2], r[3], r[4], r[5]);
        }
        static Bounds of(RawModel m) {
            int[] r = range(m.vertexCount, m.vertexX, m.vertexY, m.vertexZ);
            return new Bounds(r[0], r[1], r[2], r[3], r[4], r[5]);
        }
        private static int[] range(int count, int[] xs, int[] ys, int[] zs) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < count; i++) {
                minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
                minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]);
                minZ = Math.min(minZ, zs[i]); maxZ = Math.max(maxZ, zs[i]);
            }
            return new int[] {minX, maxX, minY, maxY, minZ, maxZ};
        }
        double width() { return Math.max(1.0, maxX - minX); }
        double height() { return Math.max(1.0, maxY - minY); }
        double depth() { return Math.max(1.0, maxZ - minZ); }
    }

    private static int materialColor(ModelDefinition model, int face, Map<Integer, Integer> recolor) {
        if (model.faceColors != null) {
            int color = model.faceColors[face] & 0xFFFF;
            if (recolor != null) {
                Integer mapped = recolor.get(color);
                if (mapped != null) {
                    return mapped;
                }
            }
            if (color != 127) return color;
        }
        if (model.faceTextures != null && model.faceTextures[face] >= 0) {
            int texture = model.faceTextures[face] & 0xFFFF;
            int approx = hsl((texture * 37) % 360, 38, 52);
            if (recolor != null) {
                Integer mapped = recolor.get(127);
                if (mapped != null) {
                    return mapped;
                }
            }
            return approx;
        }
        if (recolor != null) {
            Integer mapped = recolor.get(127);
            if (mapped != null) {
                return mapped;
            }
        }
        return hsl(35, 20, 58);
    }

    private static int clamp(int value) {
        return Math.max(-8192, Math.min(8191, value));
    }

    /** RS3 worn models can exceed the 2009 old-format coordinate range; shrink uniformly to fit. */
    private static double rs3FitScale(int[] xs, int[] ys, int[] zs, int count) {
        int maxAbs = 0;
        for (int i = 0; i < count; i++) {
            maxAbs = Math.max(maxAbs, Math.abs(xs[i]));
            maxAbs = Math.max(maxAbs, Math.abs(ys[i]));
            maxAbs = Math.max(maxAbs, Math.abs(zs[i]));
        }
        if (maxAbs <= 8191) {
            return 1.0;
        }
        return 8191.0 / maxAbs;
    }

    /** RS3 inventory models are often authored oversized for the 2009 icon renderer. */
    private static double rs3IconFitScale(int[] xs, int[] ys, int[] zs, int count) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]);
            minZ = Math.min(minZ, zs[i]); maxZ = Math.max(maxZ, zs[i]);
        }
        int span = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        if (span <= 180) {
            return 1.0;
        }
        return 180.0 / span;
    }

    private static int[] scaleCoords(int[] src, double scale) {
        int[] out = new int[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = (int) Math.round(src[i] * scale);
        }
        return out;
    }

    /** Client icon camera uses MathUtils.cos/sin tables sized 2048 (0..2047). */
    private static int normalizeIconAngle(int angle) {
        return angle & 2047;
    }

    private static int encodeSignedShort(int value) {
        return value < 0 ? value + 65536 : value;
    }

    /** RS3 exports large signed offsets that push icons off-screen in the 2009 client. */
    private static int normalizeIconOffset(int offset) {
        int signed = offset > 32767 ? offset - 65536 : offset;
        if (signed < -128 || signed > 128) {
            return 0;
        }
        return signed;
    }

    private static byte[] encodeOldModel(List<V> vertices, List<F> faces, int priority,
                                         ModelDefinition textureSource) throws Exception {
        boolean hasVertexBones = vertices.stream().anyMatch(v -> v.bone >= 0);
        boolean hasFacePriorities = faces.stream().anyMatch(face -> face.priority >= 0);
        boolean isTextured = faces.stream().anyMatch(face -> face.textureId >= 0);
        int textureCount = textureSource != null ? textureSource.numTextureFaces : 0;
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
            if (hasVertexBones) vertexBones.write(Math.max(0, Math.min(255, vert.bone)));
            px = vert.x; py = vert.y; pz = vert.z;
        }
        ByteArrayOutputStream triangleTypes = new ByteArrayOutputStream();
        ByteArrayOutputStream trianglePriorities = new ByteArrayOutputStream();
        ByteArrayOutputStream faceTextureFlags = new ByteArrayOutputStream();
        ByteArrayOutputStream triangleIndices = new ByteArrayOutputStream();
        ByteArrayOutputStream colors = new ByteArrayOutputStream();
        int last = 0;
        for (F face : faces) {
            triangleTypes.write(1);
            if (hasFacePriorities) {
                trianglePriorities.write(face.priority >= 0 ? face.priority : priority);
            }
            if (isTextured) {
                faceTextureFlags.write(face.textureId >= 0 ? (2 | (face.textureCoord << 2)) : 0);
            }
            writeSmart(triangleIndices, face.a - last);
            writeSmart(triangleIndices, face.b - face.a);
            writeSmart(triangleIndices, face.c - face.b);
            last = face.c;
            writeShort(colors, face.color);
        }
        ByteArrayOutputStream textureIndices = new ByteArrayOutputStream();
        if (textureCount > 0 && textureSource != null && textureSource.texIndices1 != null) {
            for (int t = 0; t < textureCount; t++) {
                writeShort(textureIndices, textureSource.texIndices1[t]);
                writeShort(textureIndices, textureSource.texIndices2[t]);
                writeShort(textureIndices, textureSource.texIndices3[t]);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(vertexFlags.toByteArray());
        out.write(triangleTypes.toByteArray());
        if (hasFacePriorities) out.write(trianglePriorities.toByteArray());
        if (isTextured) out.write(faceTextureFlags.toByteArray());
        if (hasVertexBones) out.write(vertexBones.toByteArray());
        out.write(triangleIndices.toByteArray());
        out.write(colors.toByteArray());
        if (textureCount > 0) out.write(textureIndices.toByteArray());
        out.write(xData.toByteArray());
        out.write(yData.toByteArray());
        out.write(zData.toByteArray());
        writeShort(out, vertices.size());
        writeShort(out, faces.size());
        out.write(textureCount);
        out.write(isTextured ? 1 : 0);
        out.write(hasFacePriorities ? 255 : priority);
        out.write(0);              // use face alpha
        out.write(0);              // use packed transparency vertex groups
        out.write(hasVertexBones ? 1 : 0);
        writeShort(out, xData.size());
        writeShort(out, yData.size());
        writeShort(out, zData.size());
        writeShort(out, triangleIndices.size());
        return out.toByteArray();
    }

    // ------------------------------------------------------------- item defs

    private static byte[] rewriteItemDefinition(byte[] def, PlanItem item, Cache itemArchive,
                                                IndexData itemIndex) throws Exception {
        // Weapons with a configTemplate inherit animation / interface opcodes from the native
        // template item (render_anim etc.) but never its model ids or recolors — those always
        // come from the plan / OSRS extraction.
        byte[] source = def;
        if (item.configTemplate >= 0) {
            byte[] template = readItemDefinition(itemArchive, itemIndex, item.configTemplate);
            if (template != null) {
                source = template;
            }
        }
        if (source == null) source = new byte[] { 0 };
        boolean worn = item.male0 >= 0 || item.female0 >= 0;
        boolean wearable = worn || item.equippable;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean seen1 = false, seen2 = false, seen4 = false, seen5 = false, seen6 = false, seen7 = false, seen8 = false;
        boolean seen23 = false, seen24 = false, seen25 = false, seen26 = false, seen36 = false, seen65 = false, seen90 = false, seen91 = false, seen95 = false;
        int i = 0;
        while (i < source.length) {
            int opcode = source[i++] & 0xFF;
            if (opcode == 0) break;
            if (opcode == 1) { out.write(opcode); writeShort(out, item.ground); i += 2; seen1 = true; }
            else if (opcode == 2) { out.write(opcode); writeString(out, item.name); while (source[i++] != 0) {} seen2 = true; }
            else if (opcode == 4) { out.write(opcode); writeShort(out, item.zoom2d); i += 2; seen4 = true; }
            else if (opcode == 5) { out.write(opcode); writeShort(out, item.xan2d); i += 2; seen5 = true; }
            else if (opcode == 6) { out.write(opcode); writeShort(out, item.yan2d); i += 2; seen6 = true; }
            else if (opcode == 7) {
                out.write(opcode); writeShort(out, encodeSignedShort(normalizeIconOffset(item.xOffset2d)));
                i += 2; seen7 = true;
            }
            else if (opcode == 8) {
                out.write(opcode); writeShort(out, encodeSignedShort(normalizeIconOffset(item.yOffset2d)));
                i += 2; seen8 = true;
            }
            else if (opcode == 23) { if (worn) { out.write(opcode); writeShort(out, item.male0); } i += 2; seen23 = true; }
            else if (opcode == 24) { if (worn && item.male1 >= 0) { out.write(opcode); writeShort(out, item.male1); } i += 2; seen24 = true; }
            else if (opcode == 25) { if (worn) { out.write(opcode); writeShort(out, item.female0); } i += 2; seen25 = true; }
            else if (opcode == 26) { if (worn && item.female1 >= 0) { out.write(opcode); writeShort(out, item.female1); } i += 2; seen26 = true; }
            else if (opcode == 36) { out.write(opcode); writeString(out, "Wear"); while (source[i++] != 0) {} seen36 = true; }
            else if (opcode == 65) { if (item.tradeable) out.write(opcode); seen65 = true; }
            else if (opcode == 90) { if (item.maleHead >= 0) { out.write(opcode); writeShort(out, item.maleHead); } i += 2; seen90 = true; }
            else if (opcode == 91) { if (item.femaleHead >= 0) { out.write(opcode); writeShort(out, item.femaleHead); } i += 2; seen91 = true; }
            else if (opcode == 95) {
                if (item.zan2d != 0) { out.write(opcode); writeShort(out, item.zan2d); }
                i += 2; seen95 = true;
            }
            else if (opcode == 44) { out.write(opcode); writeInt(out, item.ground); i += 4; seen1 = true; }
            else if (opcode == 45) { if (worn) { out.write(opcode); writeInt(out, item.male0); out.write(0); } i += 5; seen23 = true; }
            else if (opcode == 46) { if (worn && item.male1 >= 0) { out.write(opcode); writeInt(out, item.male1); } i += 4; seen24 = true; }
            else if (opcode == 47) { i += 4; }
            else if (opcode == 48) { if (worn) { out.write(opcode); writeInt(out, item.female0); out.write(0); } i += 5; seen25 = true; }
            else if (opcode == 49) { if (worn && item.female1 >= 0) { out.write(opcode); writeInt(out, item.female1); } i += 4; seen26 = true; }
            else if (opcode == 50) { i += 4; }
            else if (opcode == 51) { if (item.maleHead >= 0) { out.write(opcode); writeInt(out, item.maleHead); } i += 4; seen90 = true; }
            else if (opcode == 52) { i += 4; }
            else if (opcode == 53) { if (item.femaleHead >= 0) { out.write(opcode); writeInt(out, item.femaleHead); } i += 4; seen91 = true; }
            else if (opcode == 54) { i += 4; }
            else if (opcode == 139 || opcode == 140 || opcode == 148 || opcode == 149) { i += 2; }
            else if (opcode == 125 || opcode == 126) { i += 3; }
            else if (opcode == 40 || opcode == 41) { int count = source[i] & 0xFF; i += 1 + count * 4; }
            else { out.write(opcode); i = copyOpcodePayload(source, i, opcode, out); }
        }
        if (!seen1) { out.write(1); writeShort(out, item.ground); }
        if (!seen2) { out.write(2); writeString(out, item.name); }
        if (!seen4) { out.write(4); writeShort(out, item.zoom2d); }
        if (!seen5) { out.write(5); writeShort(out, item.xan2d); }
        if (!seen6) { out.write(6); writeShort(out, item.yan2d); }
        if (!seen7) { out.write(7); writeShort(out, encodeSignedShort(normalizeIconOffset(item.xOffset2d))); }
        if (!seen8) { out.write(8); writeShort(out, encodeSignedShort(normalizeIconOffset(item.yOffset2d))); }
        if (wearable && !seen36) { out.write(36); writeString(out, "Wear"); }
        if (item.tradeable && !seen65) { out.write(65); }
        if (item.zan2d != 0 && !seen95) { out.write(95); writeShort(out, item.zan2d); }
        if (worn && item.male0 >= 0 && !seen23) { out.write(23); writeShort(out, item.male0); }
        if (worn && item.male1 >= 0 && !seen24) { out.write(24); writeShort(out, item.male1); }
        if (worn && item.female0 >= 0 && !seen25) { out.write(25); writeShort(out, item.female0); }
        if (worn && item.female1 >= 0 && !seen26) { out.write(26); writeShort(out, item.female1); }
        if (item.maleHead >= 0 && !seen90) { out.write(90); writeShort(out, item.maleHead); }
        if (item.femaleHead >= 0 && !seen91) { out.write(91); writeShort(out, item.femaleHead); }
        out.write(0);
        return out.toByteArray();
    }

    private static byte[] readItemDefinition(Cache itemArchive, IndexData itemIndex, int itemId) throws Exception {
        int group = itemId >>> 8;
        int file = itemId & 255;
        int slot = itemIndex.find(group);
        if (slot < 0) return null;
        byte[][] files = readGroupFiles(itemArchive, itemIndex, group);
        if (file >= files.length || files[file] == null) return null;
        return files[file];
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
        } else if (opcode == 2 || (opcode >= 30 && opcode < 40)) {
            int start = i;
            while (def[i++] != 0) {}
            out.write(def, start, i - start);
            return i;
        } else if (opcode == 42) {
            len = 1 + (def[i] & 0xFF);
            out.write(def, i, len);
            return i + len;
        } else if (opcode >= 100 && opcode < 110) {
            out.write(def, i, 4);
            return i + 4;
        } else if (opcode == 44 || opcode == 46 || opcode == 47 || opcode == 49 || opcode == 50
                || opcode == 51 || opcode == 52 || opcode == 53 || opcode == 54) {
            out.write(def, i, 4);
            return i + 4;
        } else if (opcode == 45 || opcode == 48) {
            out.write(def, i, 5);
            return i + 5;
        } else if (opcode == 75) {
            out.write(def, i, 2);
            return i + 2;
        } else if (opcode == 94) {
            out.write(def, i, 2);
            return i + 2;
        } else if (opcode == 113 || opcode == 114) {
            out.write(def, i, 1);
            return i + 1;
        } else if (opcode == 139 || opcode == 140 || opcode == 148 || opcode == 149) {
            out.write(def, i, 2);
            return i + 2;
        } else if (opcode == 249) {
            int start = i;
            int count = def[i++] & 0xFF;
            for (int n = 0; n < count; n++) {
                boolean string = def[i++] != 0;
                i += 3;
                if (string) { while (def[i++] != 0) {} } else { i += 4; }
            }
            out.write(def, start, i - start);
            return i;
        }
        // RS3-era opcodes we don't rewrite: copy remainder of definition unchanged
        System.err.println("WARNING: pass-through unknown item opcode " + opcode + " at offset " + i);
        out.write(def, i, def.length - i);
        return def.length;
    }

    // -------------------------------------------------------- group plumbing

    private static byte[][] readGroupFiles(Cache archive, IndexData index, int group) throws Exception {
        int slot = index.find(group);
        int[] ids = index.fileIds[slot];
        int capacity = ids.length == 0 ? 0 : ids[ids.length - 1] + 1;
        byte[][] files = new byte[capacity][];
        byte[] packed = archive.read(group);
        if (packed == null) return files;
        byte[] uncompressed = Js5Compression.uncompress(stripVersion(packed));
        int count = ids.length;
        if (count == 1) {
            files[ids[0]] = uncompressed;
            return files;
        }
        int chunks = uncompressed[uncompressed.length - 1] & 0xFF;
        int table = uncompressed.length - 1 - chunks * count * 4;
        byte[][] logical = new byte[count][];
        int pos = table;
        int[] sizes = new int[count];
        for (int chunk = 0; chunk < chunks; chunk++) {
            int cumulative = 0;
            for (int n = 0; n < count; n++) {
                cumulative += readInt(uncompressed, pos);
                pos += 4;
                sizes[n] += cumulative;
            }
        }
        for (int n = 0; n < count; n++) logical[n] = new byte[sizes[n]];
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
        for (int n = 0; n < count; n++) files[ids[n]] = logical[n];
        return files;
    }

    private static int[] presentFileIds(byte[][] files) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < files.length; i++) if (files[i] != null) ids.add(i);
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) out[i] = ids.get(i);
        return out;
    }

    private static byte[] packSingleChunk(byte[][] filesById) {
        List<byte[]> files = new ArrayList<>();
        for (byte[] f : filesById) if (f != null) files.add(f);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (byte[] f : files) data.write(f, 0, f.length);
        int previous = 0;
        for (byte[] f : files) {
            writeInt(data, f.length - previous);
            previous = f.length;
        }
        data.write(1);
        return data.toByteArray();
    }

    /**
     * Full JS5 archive index codec (same wire format AddItemToCache validated
     * byte-identical via IndexRoundTrip), extended with group insertion so brand-new
     * model ids can be added to archive 7.
     */
    private static final class IndexData {
        int protocol, version, flags;
        int[] groupIds, groupNames, crcs, versions;
        int[][] fileIds, fileNames;

        static IndexData parse(byte[] raw) {
            IndexData d = new IndexData();
            int[] p = {0};
            d.protocol = u8(raw, p);
            d.version = d.protocol >= 6 ? u32(raw, p) : 0;
            d.flags = u8(raw, p);
            boolean names = (d.flags & 1) != 0;
            int gc = u16(raw, p);
            d.groupIds = new int[gc];
            int acc = 0;
            for (int i = 0; i < gc; i++) { acc += u16(raw, p); d.groupIds[i] = acc; }
            d.groupNames = new int[gc];
            if (names) for (int i = 0; i < gc; i++) d.groupNames[i] = u32(raw, p);
            d.crcs = new int[gc];
            for (int i = 0; i < gc; i++) d.crcs[i] = u32(raw, p);
            d.versions = new int[gc];
            for (int i = 0; i < gc; i++) d.versions[i] = u32(raw, p);
            int[] fc = new int[gc];
            for (int i = 0; i < gc; i++) fc[i] = u16(raw, p);
            d.fileIds = new int[gc][];
            for (int i = 0; i < gc; i++) {
                d.fileIds[i] = new int[fc[i]];
                int x = 0;
                for (int j = 0; j < fc[i]; j++) { x += u16(raw, p); d.fileIds[i][j] = x; }
            }
            d.fileNames = new int[gc][];
            if (names) {
                for (int i = 0; i < gc; i++) {
                    d.fileNames[i] = new int[fc[i]];
                    for (int j = 0; j < fc[i]; j++) d.fileNames[i][j] = u32(raw, p);
                }
            }
            if (p[0] != raw.length) throw new IllegalStateException("index parse mismatch " + p[0] + "/" + raw.length);
            return d;
        }

        int find(int group) {
            for (int i = 0; i < groupIds.length; i++) if (groupIds[i] == group) return i;
            return -1;
        }

        void insertGroup(int group, int version, int[] files) {
            int at = 0;
            while (at < groupIds.length && groupIds[at] < group) at++;
            groupIds = insert(groupIds, at, group);
            groupNames = insert(groupNames, at, 0);
            crcs = insert(crcs, at, 0);
            versions = insert(versions, at, version);
            fileIds = insert(fileIds, at, files);
            fileNames = insert(fileNames, at, new int[files.length]);
        }

        byte[] emit() {
            boolean names = (flags & 1) != 0;
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            o.write(protocol);
            if (protocol >= 6) writeInt(o, version);
            o.write(flags);
            writeShortTo(o, groupIds.length);
            int prev = 0;
            for (int g : groupIds) { writeShortTo(o, g - prev); prev = g; }
            if (names) for (int n : groupNames) writeInt(o, n);
            for (int c : crcs) writeInt(o, c);
            for (int v : versions) writeInt(o, v);
            for (int[] f : fileIds) writeShortTo(o, f.length);
            for (int[] f : fileIds) {
                int q = 0;
                for (int id : f) { writeShortTo(o, id - q); q = id; }
            }
            if (names) for (int[] f : fileNames) for (int n : f) writeInt(o, n);
            return o.toByteArray();
        }

        private static int[] insert(int[] a, int at, int v) {
            int[] out = new int[a.length + 1];
            System.arraycopy(a, 0, out, 0, at);
            out[at] = v;
            System.arraycopy(a, at, out, at + 1, a.length - at);
            return out;
        }

        private static int[][] insert(int[][] a, int at, int[] v) {
            int[][] out = new int[a.length + 1][];
            System.arraycopy(a, 0, out, 0, at);
            out[at] = v;
            System.arraycopy(a, at, out, at + 1, a.length - at);
            return out;
        }

        private static int u8(byte[] b, int[] p) { return b[p[0]++] & 0xFF; }
        private static int u16(byte[] b, int[] p) { return (u8(b, p) << 8) | u8(b, p); }
        private static int u32(byte[] b, int[] p) { return (u8(b, p) << 24) | (u8(b, p) << 16) | (u8(b, p) << 8) | u8(b, p); }
        private static void writeShortTo(ByteArrayOutputStream o, int v) { o.write((v >>> 8) & 0xFF); o.write(v & 0xFF); }
    }

    // ----------------------------------------------------------------- bytes

    private static byte[] stripVersion(byte[] group) {
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }

    private static byte[] wrapUncompressed(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        writeInt(out, data.length);
        out.write(data, 0, data.length);
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
        out.write(compressed.toByteArray(), 0, compressed.size());
        return out.toByteArray();
    }

    private static byte[] appendVersion(byte[] data, int version) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(data, 0, data.length);
        out.write((version >>> 8) & 0xFF);
        out.write(version & 0xFF);
        return out.toByteArray();
    }

    private static int readTrailingVersion(byte[] data) {
        if (data.length < 2) return 0;
        return ((data[data.length - 2] & 0xFF) << 8) | (data[data.length - 1] & 0xFF);
    }

    private static int readInt(byte[] bytes, int pos) {
        return ((bytes[pos] & 0xFF) << 24) | ((bytes[pos + 1] & 0xFF) << 16) | ((bytes[pos + 2] & 0xFF) << 8) | (bytes[pos + 3] & 0xFF);
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

    private static void writeString(ByteArrayOutputStream out, String value) {
        for (int i = 0; i < value.length(); i++) out.write(value.charAt(i) & 0xFF);
        out.write(0);
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
}
