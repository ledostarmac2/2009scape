import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import rt4.RawModel;

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
            int maleHead,
            int femaleHead,
            int zoom2d,
            int xan2d,
            int yan2d,
            int zan2d,
            int xOffset2d,
            int yOffset2d,
            boolean tradeable) {}

    private static final ImportItem[] ITEMS = {
            // Cameras dumped from the real OSRS cache (DumpOsrsItemCameras, openrs2 rev 2565).
            new ImportItem(14659, "Ferocious gloves", 36141, 36325, -1, 36335, -1, -1, -1, 917, 420, 1082, 0, 0, -1, false),
            new ImportItem(14660, "Neitiznot faceguard", 38897, 38857, -1, 38858, -1, 38873, 38873, 984, 126, 129, 0, -1, 1, false),
            new ImportItem(14661, "Primordial boots", 29397, 29250, -1, 29255, -1, -1, -1, 976, 147, 279, 0, 5, -5, true),
            new ImportItem(14662, "Scythe of vitur", 35742, 35371, -1, 32906, -1, -1, -1, 2160, 584, 12, 0, -1, 2, true),
            new ImportItem(14663, "Ancestral hat", 32794, 32655, -1, 32663, -1, -1, -1, 980, 208, 220, 0, 0, -18, true),
            new ImportItem(14664, "Ancestral robe top", 32790, 32657, 32658, 32664, 32665, -1, -1, 1870, 376, 176, 0, 0, -3, true),
            new ImportItem(14665, "Ancestral robe bottom", 32787, 32653, -1, 32662, -1, -1, -1, 1690, 408, 2024, 0, 5, 7, true),
    };

    private record V(int x, int y, int z, int bone) {}
    private record F(int a, int b, int c, int color, int bone, int priority) {}

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException("Usage: ImportOsrsItemModels <cacheDir> <backupDir> <modelContainerDir> <objOutDir>");
        }
        File cacheDir = new File(args[0]);
        Path backupDir = Path.of(args[1]);
        Path modelDir = Path.of(args[2]);
        Path objOut = Path.of(args[3]);
        Set<Integer> onlyItems = new HashSet<>();
        boolean defOnly = false;
        for (int i = 4; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("def-only")) { defOnly = true; continue; }
            onlyItems.add(Integer.parseInt(args[i]));
        }
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
            if (!onlyItems.isEmpty() && !onlyItems.contains(item.itemId)) {
                continue;
            }
            if (!defOnly) for (int modelId : new int[] { item.ground, item.male0, item.male1, item.female0, item.female1, item.maleHead, item.femaleHead }) {
                if (modelId < 0 || patchedModels.contains(modelId)) {
                    continue;
                }
                ModelDefinition model = loadOsrsModel(modelDir.resolve(modelId + ".container"), modelId);
                exportObj(model, objOut.resolve(modelId + ".obj"), objOut.resolve(modelId + ".mtl"));
                boolean worn = modelId == item.male0 || modelId == item.male1 || modelId == item.female0 || modelId == item.female1 ||
                        modelId == item.maleHead || modelId == item.femaleHead;
                patchSingleFileGroup(cacheDir, data, master, MODEL_ARCHIVE, modelId,
                        encodeOldModel(model, item, worn, loadRigReference(cacheDir, item, modelId)));
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

    private static byte[] encodeOldModel(ModelDefinition model, ImportItem item, boolean worn, RefRig rig) throws Exception {
        List<V> vertices = new ArrayList<>();
        Bounds bounds = Bounds.of(model);
        // Re-center imported worn gloves onto the 2009 reference position. OSRS hand geometry
        // sits higher up the arm than the 2009 skeleton, which floated the gloves above the hands
        // and dragged the held weapon/offhand down. Match the reference model's bounds center.
        int dx = 0, dy = 0, dz = 0;
        boolean glovesSpreadX = worn && rig != null && item.itemId == 14659;
        if (glovesSpreadX) {
            // Gloves: keep the perfect height (lift 5). X is spread onto the reference (wrist)
            // width below, because the OSRS hands sit too narrow so the gloves missed the wrists.
            dy = (rig.bounds().minY + rig.bounds().maxY - bounds.minY - bounds.maxY) / 2 - 5;
        } else if (worn && rig != null && item.itemId == 14660 && (model.id == item.male0 || model.id == item.female0)) {
            // Faceguard helm: lower the base to meet the neck (align bottom edge to reference helm).
            dy = rig.bounds().maxY - bounds.maxY;
        }
        // Cuff clearance (gloves): the OSRS cuff is slimmer than the 2009 forearm, so the arm
        // surface sits flush with / inside the cuff and painter-sorts unstably at the wrist.
        // The 2009 arm tube reaches well below the reference glove's top edge, so the flare
        // must cover the upper half of the glove: full strength (+25%) from the cuff top down
        // past the wrist line, then taper to nothing before the hand so there is no seam.
        // Render priority stays at the Barrows reference value (10) so draw order against the
        // body and other items is untouched.
        double cuffFull = 0, cuffEnd = 0, cuffCxL = 0, cuffCzL = 0, cuffCxR = 0, cuffCzR = 0;
        boolean cuffFlare = glovesSpreadX;
        if (cuffFlare) {
            cuffFull = rig.bounds().minY + 6;   // full flare from glove top to here
            cuffEnd = rig.bounds().minY + 14;   // flare fades to zero by here
            double gcx = (bounds.minX + bounds.maxX) / 2.0;
            double sxL = 0, szL = 0, sxR = 0, szR = 0;
            int nL = 0, nR = 0;
            for (int i = 0; i < model.vertexCount; i++) {
                if (model.vertexY[i] + dy <= cuffEnd) {
                    double vx = gcx + (model.vertexX[i] - gcx) * 1.08;
                    if (model.vertexX[i] < gcx) { sxL += vx; szL += model.vertexZ[i]; nL++; }
                    else { sxR += vx; szR += model.vertexZ[i]; nR++; }
                }
            }
            cuffFlare = nL > 0 && nR > 0;
            if (cuffFlare) {
                cuffCxL = sxL / nL; cuffCzL = szL / nL;
                cuffCxR = sxR / nR; cuffCzR = szR / nR;
            }
        }
        for (int i = 0; i < model.vertexCount; i++) {
            int x;
            int y;
            int z;
            int bx; // pre-translation position for nearest-bone lookup (must match `bounds` space)
            int by;
            int bz;
            if (worn) {
                bx = clamp(model.vertexX[i]);
                by = clamp(model.vertexY[i]);
                bz = clamp(model.vertexZ[i]);
                if (glovesSpreadX) {
                    // spread X outward just a smidge (~8%) about the centre to reach the wrists.
                    double gcx = (bounds.minX + bounds.maxX) / 2.0;
                    double vx = gcx + (model.vertexX[i] - gcx) * 1.08;
                    double vz = model.vertexZ[i];
                    double vy = model.vertexY[i] + dy;
                    if (cuffFlare && vy <= cuffEnd) {
                        double t = vy <= cuffFull ? 1.0 : (cuffEnd - vy) / Math.max(1.0, cuffEnd - cuffFull);
                        double s = 1.0 + 0.25 * t;
                        double cx = model.vertexX[i] < gcx ? cuffCxL : cuffCxR;
                        double cz = model.vertexX[i] < gcx ? cuffCzL : cuffCzR;
                        vx = cx + (vx - cx) * s;
                        vz = cz + (vz - cz) * s;
                    }
                    x = clamp((int) Math.round(vx));
                    z = clamp((int) Math.round(vz));
                } else {
                    x = clamp(model.vertexX[i] + dx);
                    z = clamp(model.vertexZ[i] + dz);
                }
                y = clamp(model.vertexY[i] + dy);
            } else {
                // Inventory/ground model: keep the native OSRS geometry untouched. The 2009
                // icon renderer shares the RS2 sprite math with OSRS, so the original OSRS
                // zoom2d/xan2d/yan2d camera values only line up if the model is neither
                // scaled nor re-oriented. The old axis swap (OSRS Y-up -> icon Z) laid the
                // models on their backs, which no camera angle could undo.
                x = clamp(model.vertexX[i]);
                y = clamp(model.vertexY[i]);
                z = clamp(model.vertexZ[i]);
                bx = x;
                by = y;
                bz = z;
            }
            // Use the real OSRS per-vertex skin (vskin) labels when present. OSRS inherited the
            // RS2 transform-group numbering, so these should line up with the 2009 player skeleton.
            // If a model has no vskin we emit -1 (static, but solid) -- never a hardcoded guess.
            int bone = model.packedVertexGroups != null ? model.packedVertexGroups[i] : rig == null ? -1 : rig.nearestBone(bx, by, bz, bounds);
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
            faces.add(new F(model.faceIndices1[i], model.faceIndices2[i], model.faceIndices3[i], color, -1, -1));
        }
        int priority = worn ? referencePriority(item, model.id) : 0;
        return encodeOldModel(vertices, faces, priority);
    }

    /** Global render priority to match the 2009 reference equipment (read from their models). */
    private static int referencePriority(ImportItem item, int modelId) {
        if (item.itemId == 14659) return 10;                                      // Ferocious gloves: same as the 2009 Barrows gloves reference (cuff faces get 11 per-face)
        if (item.itemId == 14660) {
            if (modelId == item.maleHead || modelId == item.femaleHead) return 4; // head covering
            return 7;                                                             // helm body
        }
        return 0;                                                                 // boots / default
    }

    private record RefRig(int[] x, int[] y, int[] z, int[] bone, Bounds bounds) {
        int nearestBone(int vx, int vy, int vz, Bounds source) {
            double nx = (vx - source.minX) / source.width();
            double ny = (vy - source.minY) / source.height();
            double nz = (vz - source.minZ) / source.depth();
            double best = Double.MAX_VALUE;
            int bestBone = -1;
            for (int i = 0; i < x.length; i++) {
                // Never inherit the root bone (0): worn equipment vertices on the root stay put
                // while the limb animates, producing a jagged jut (the boots' stray red flap).
                if (bone[i] == 0) continue;
                double rx = (x[i] - bounds.minX) / bounds.width();
                double ry = (y[i] - bounds.minY) / bounds.height();
                double rz = (z[i] - bounds.minZ) / bounds.depth();
                double dx = nx - rx;
                double dy = ny - ry;
                double dz = nz - rz;
                double dist = dx * dx + dy * dy + dz * dz;
                if (dist < best) {
                    best = dist;
                    bestBone = bone[i];
                }
            }
            return bestBone;
        }
    }

    private static RefRig loadRigReference(File cacheDir, ImportItem item, int modelId) throws Exception {
        int referenceId = rigReferenceModel(item, modelId);
        if (referenceId < 0) {
            return null;
        }
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        Cache models = new Cache(MODEL_ARCHIVE, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + MODEL_ARCHIVE), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        byte[] packed = models.read(referenceId);
        if (packed == null) {
            throw new IllegalStateException("Missing rig reference model " + referenceId);
        }
        RawModel reference = new RawModel(Js5Compression.uncompress(stripVersion(packed)));
        if (reference.vertexBones == null) {
            throw new IllegalStateException("Rig reference model has no vertex bones: " + referenceId);
        }
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
        return new RefRig(x, y, z, bone, Bounds.of(reference));
    }

    private static int rigReferenceModel(ImportItem item, int modelId) {
        if (item.itemId == 14659) {
            if (modelId == item.male0) return 13307;   // Barrows gloves male
            if (modelId == item.female0) return 13319; // Barrows gloves female
        } else if (item.itemId == 14660) {
            if (modelId == item.male0) return 21873;   // Helm of neitiznot male
            if (modelId == item.female0) return 21906; // Helm of neitiznot female
            if (modelId == item.maleHead) return 39516;
            if (modelId == item.femaleHead) return 38751;
        } else if (item.itemId == 14661) {
            if (modelId == item.male0) return 27738;   // Dragon boots male
            if (modelId == item.female0) return 27754; // Dragon boots female
        }
        return -1;
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

        static Bounds of(RawModel model) {
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

        double centerY() {
            return (minY + maxY) / 2.0;
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
        if (def == null) {
            def = new byte[] { 0 };
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean seen1 = false, seen2 = false, seen4 = false, seen5 = false, seen6 = false, seen7 = false, seen8 = false;
        boolean seen23 = false, seen24 = false, seen25 = false, seen26 = false, seen36 = false, seen65 = false, seen90 = false, seen91 = false, seen95 = false;
        int i = 0;
        while (i < def.length) {
            int opcode = def[i++] & 0xFF;
            if (opcode == 0) {
                break;
            }
            if (opcode == 1) {
                out.write(opcode); writeShort(out, item.ground); i += 2; seen1 = true;
            } else if (opcode == 2) {
                out.write(opcode); writeString(out, item.name); while (def[i++] != 0) {} seen2 = true;
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
                if (shouldWriteWornModels(item)) { out.write(opcode); writeShort(out, item.male0); }
                i += 2; seen23 = true;
            } else if (opcode == 24) {
                if (shouldWriteWornModels(item) && item.male1 >= 0) { out.write(opcode); writeShort(out, item.male1); }
                i += 2; seen24 = true;
            } else if (opcode == 25) {
                if (shouldWriteWornModels(item)) { out.write(opcode); writeShort(out, item.female0); }
                i += 2; seen25 = true;
            } else if (opcode == 26) {
                if (shouldWriteWornModels(item) && item.female1 >= 0) { out.write(opcode); writeShort(out, item.female1); }
                i += 2; seen26 = true;
            } else if (opcode == 36) {
                out.write(opcode); writeString(out, "Wear"); while (def[i++] != 0) {} seen36 = true;
            } else if (opcode == 65) {
                if (item.tradeable) { out.write(opcode); }
                seen65 = true;
            } else if (opcode == 90) {
                if (item.maleHead >= 0) { out.write(opcode); writeShort(out, item.maleHead); }
                i += 2; seen90 = true;
            } else if (opcode == 91) {
                if (item.femaleHead >= 0) { out.write(opcode); writeShort(out, item.femaleHead); }
                i += 2; seen91 = true;
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
        if (!seen2) { out.write(2); writeString(out, item.name); }
        if (!seen4) { out.write(4); writeShort(out, item.zoom2d); }
        if (!seen5) { out.write(5); writeShort(out, item.xan2d); }
        if (!seen6) { out.write(6); writeShort(out, item.yan2d); }
        if (!seen7) { out.write(7); writeShort(out, item.xOffset2d); }
        if (!seen8) { out.write(8); writeShort(out, item.yOffset2d); }
        if (shouldWriteWornModels(item) && !seen36) { out.write(36); writeString(out, "Wear"); }
        if (item.tradeable && !seen65) { out.write(65); }
        if (item.zan2d != 0 && !seen95) { out.write(95); writeShort(out, item.zan2d); }
        if (shouldWriteWornModels(item) && item.male0 >= 0 && !seen23) { out.write(23); writeShort(out, item.male0); }
        if (shouldWriteWornModels(item) && item.male1 >= 0 && !seen24) { out.write(24); writeShort(out, item.male1); }
        if (shouldWriteWornModels(item) && item.female0 >= 0 && !seen25) { out.write(25); writeShort(out, item.female0); }
        if (shouldWriteWornModels(item) && item.female1 >= 0 && !seen26) { out.write(26); writeShort(out, item.female1); }
        if (item.maleHead >= 0 && !seen90) { out.write(90); writeShort(out, item.maleHead); }
        if (item.femaleHead >= 0 && !seen91) { out.write(91); writeShort(out, item.femaleHead); }
        out.write(0);
        return out.toByteArray();
    }

    private static boolean shouldWriteWornModels(ImportItem item) {
        // Equipment rigs via the real OSRS vskin labels read in encodeOldModel. These
        // one-slot items share the same skeleton group labels as their 2009 references.
        return item.male0 >= 0 || item.female0 >= 0;
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

    private static byte[] encodeOldModel(List<V> vertices, List<F> faces, int priority) throws Exception {
        boolean hasVertexBones = vertices.stream().anyMatch(vertex -> vertex.bone >= 0);
        boolean hasTriangleBones = faces.stream().anyMatch(face -> face.bone >= 0);
        boolean hasFacePriorities = faces.stream().anyMatch(face -> face.priority >= 0);
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
        ByteArrayOutputStream trianglePriorities = new ByteArrayOutputStream();
        ByteArrayOutputStream triangleIndices = new ByteArrayOutputStream();
        ByteArrayOutputStream colors = new ByteArrayOutputStream();
        ByteArrayOutputStream triangleBones = new ByteArrayOutputStream();
        int last = 0;
        for (F face : faces) {
            triangleTypes.write(1);
            if (hasFacePriorities) {
                trianglePriorities.write(face.priority >= 0 ? face.priority : priority);
            }
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
        if (hasFacePriorities) {
            // Old-format layout: per-face priorities sit between the face types and the
            // tskins/vskins blocks (see rt4.RawModel.decodeOld), flagged by priority=255.
            out.write(trianglePriorities.toByteArray());
        }
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
        out.write(0);              // textured face count
        out.write(0);              // use face render types
        out.write(hasFacePriorities ? 255 : priority); // 255 = per-face priority array present
        out.write(0);              // use face alpha
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

    private static void writeString(ByteArrayOutputStream out, String value) {
        for (int i = 0; i < value.length(); i++) {
            out.write(value.charAt(i) & 0xFF);
        }
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
