import java.awt.image.BufferedImage;
import java.io.File;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Inv;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;
import rt4.ObjType;
import rt4.ObjTypeList;
import rt4.Rasteriser;
import rt4.RawModel;
import rt4.SoftwareSprite;
import rt4.Sprite;

/** Batch-verify imported item defs + inventory models + icon renderability. */
public final class VerifyImportedItemIcons {
    private static final class DiskProvider extends Js5ResourceProvider {
        private final Cache master;
        private final Cache archive;
        private final int archiveId;

        DiskProvider(File dir, int archiveId) throws Exception {
            this.archiveId = archiveId;
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
            archive = new Cache(archiveId, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + archiveId), "r", Long.MAX_VALUE), 6000, 0), 1000000);
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

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: VerifyImportedItemIcons <cacheDir> <itemId>...");
        }
        File cacheDir = new File(args[0]);
        Js5 items = new Js5(new DiskProvider(cacheDir, 19), false, false);
        Js5 models = new Js5(new DiskProvider(cacheDir, 7), false, false);
        ObjTypeList.init(items, null, models);
        Rasteriser.setBrightness(0.8F);

        System.out.println("id|name|invModel|modelVerts|modelFaces|iconPixels|status");
        for (int i = 1; i < args.length; i++) {
            int itemId = Integer.parseInt(args[i]);
            ObjType type = ObjTypeList.get(itemId);
            byte[] item = items.fetchFile(itemId >>> 8, itemId & 255);
            int modelId = readInventoryModel(item);
            int verts = 0;
            int faces = 0;
            String modelStatus = "missing";
            try {
                byte[] bytes = models.fetchFile(modelId, 0);
                if (bytes != null) {
                    RawModel model = new RawModel(bytes);
                    verts = model.vertexCount;
                    faces = model.triangleCount;
                    modelStatus = verts > 0 && faces > 0 ? "ok" : "empty";
                }
            } catch (Exception ex) {
                modelStatus = "error";
            }
            int pixels = renderPixels(itemId);
            String status;
            if (modelId <= 0 || "missing".equals(modelStatus) || "empty".equals(modelStatus)) {
                status = "BAD_MODEL";
            } else if (pixels < 40) {
                status = "BAD_ICON";
            } else {
                status = "OK";
            }
            System.out.println(itemId + "|" + type.name + "|" + modelId + "|" + verts + "|" + faces + "|" + pixels + "|" + status);
        }
    }

    private static int readInventoryModel(byte[] item) {
        if (item == null) {
            return 0;
        }
        int pos = 0;
        while (pos < item.length) {
            int opcode = item[pos++] & 0xFF;
            if (opcode == 0) {
                break;
            }
            if (opcode == 1 || opcode == 4 || opcode == 5 || opcode == 6 || opcode == 7 || opcode == 8) {
                if (opcode == 1) {
                    return readUnsignedShort(item, pos);
                }
                pos += 2;
            } else if (opcode == 44) {
                return readInt(item, pos);
            } else if (opcode == 2 || (opcode >= 30 && opcode < 40)) {
                while (pos < item.length && item[pos++] != 0) {
                }
            } else if (opcode == 11 || opcode == 15 || opcode == 16 || opcode == 65) {
            } else if (opcode == 13 || opcode == 14 || opcode == 27 || opcode == 113 || opcode == 114 || opcode == 115) {
                pos++;
            } else if (opcode == 12) {
                pos += 4;
            } else if (opcode == 40 || opcode == 41) {
                int count = item[pos++] & 0xFF;
                pos += count * 4;
            } else if (opcode == 42) {
                pos += 1 + (item[pos] & 0xFF);
            } else if (opcode >= 100 && opcode < 110) {
                pos += 4;
            } else if (opcode == 249) {
                int count = item[pos++] & 0xFF;
                for (int n = 0; n < count; n++) {
                    boolean string = item[pos++] != 0;
                    pos += 3;
                    if (string) {
                        while (pos < item.length && item[pos++] != 0) {
                        }
                    } else {
                        pos += 4;
                    }
                }
            } else if (opcode == 1 || opcode == 4 || opcode == 5 || opcode == 6 || opcode == 7 || opcode == 8
                    || opcode == 23 || opcode == 24 || opcode == 25 || opcode == 26 || opcode == 90 || opcode == 91
                    || opcode == 92 || opcode == 93 || opcode == 94 || opcode == 95 || opcode == 96 || opcode == 97
                    || opcode == 98 || opcode == 110 || opcode == 111 || opcode == 112 || opcode == 121 || opcode == 122
                    || opcode == 139 || opcode == 140 || opcode == 148 || opcode == 149) {
                pos += 2;
            } else {
                break;
            }
        }
        return 0;
    }

    private static int readUnsignedShort(byte[] bytes, int pos) {
        return ((bytes[pos] & 0xFF) << 8) | (bytes[pos + 1] & 0xFF);
    }

    private static int readInt(byte[] bytes, int pos) {
        return ((bytes[pos] & 0xFF) << 24) | ((bytes[pos + 1] & 0xFF) << 16) | ((bytes[pos + 2] & 0xFF) << 8) | (bytes[pos + 3] & 0xFF);
    }

    private static int renderPixels(int itemId) {
        try {
            Sprite sprite = Inv.renderObjectSprite(0, false, itemId, false, 2, 1, false);
            if (!(sprite instanceof SoftwareSprite ss)) {
                return 0;
            }
            int count = 0;
            int[] pixels = ss.pixels;
            if (pixels == null) {
                return 0;
            }
            for (int pixel : pixels) {
                if (pixel != 0) {
                    count++;
                }
            }
            return count;
        } catch (Exception ex) {
            return 0;
        }
    }
}
