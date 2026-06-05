import java.io.File;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;
import rt4.RawModel;

public class InspectItemModels {
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

    public static void main(String[] args) throws Exception {
        File cache = new File(args[0]);
        Js5 items = new Js5(new DiskProvider(cache, 19), false, false);
        Js5 models = new Js5(new DiskProvider(cache, 7), false, false);
        for (int i = 1; i < args.length; i++) {
            int itemId = Integer.parseInt(args[i]);
            byte[] item = items.fetchFile(itemId >>> 8, itemId & 255);
            System.out.println("item " + itemId);
            inspectItem(item, models);
        }
    }

    private static void inspectItem(byte[] item, Js5 models) {
        int pos = 0;
        while (pos < item.length) {
            int opcode = item[pos++] & 0xFF;
            if (opcode == 0) {
                break;
            }
            if (opcode == 1 || opcode == 4 || opcode == 5 || opcode == 6 || opcode == 7 || opcode == 8 ||
                    opcode == 23 || opcode == 24 || opcode == 25 || opcode == 26 || opcode == 90 ||
                    opcode == 91 || opcode == 92 || opcode == 93 || opcode == 95 || opcode == 96 ||
                    opcode == 97 || opcode == 98 || opcode == 110 || opcode == 111 || opcode == 112 ||
                    opcode == 115 || opcode == 121 || opcode == 122) {
                int value = readUnsignedShort(item, pos);
                System.out.println("  opcode " + opcode + " = " + value);
                if (opcode == 1 || opcode == 23 || opcode == 24 || opcode == 25 || opcode == 26) {
                    printModel(models, value);
                }
                pos += 2;
            } else if (opcode == 2 || (opcode >= 30 && opcode < 40)) {
                int start = pos;
                while (pos < item.length && item[pos++] != 0) {
                }
                System.out.println("  opcode " + opcode + " = " + new String(item, start, pos - start - 1));
            } else if (opcode == 11 || opcode == 16 || opcode == 65) {
                System.out.println("  opcode " + opcode);
            } else if (opcode == 12) {
                System.out.println("  opcode 12 = " + readInt(item, pos));
                pos += 4;
            } else if (opcode == 40 || opcode == 41) {
                int count = item[pos++] & 0xFF;
                System.out.println("  opcode " + opcode + " count=" + count);
                pos += count * 4;
            } else if (opcode == 42) {
                int count = item[pos++] & 0xFF;
                System.out.println("  opcode 42 count=" + count);
                pos += count;
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
            } else {
                System.out.println("  opcode " + opcode + " unhandled at " + (pos - 1));
                break;
            }
        }
    }

    private static void printModel(Js5 models, int modelId) {
        try {
            byte[] bytes = models.fetchFile(modelId, 0);
            if (bytes == null) {
                System.out.println("    model " + modelId + " missing");
                return;
            }
            RawModel model = new RawModel(bytes);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < model.vertexCount; i++) {
                minX = Math.min(minX, model.vertexX[i]); maxX = Math.max(maxX, model.vertexX[i]);
                minY = Math.min(minY, model.vertexY[i]); maxY = Math.max(maxY, model.vertexY[i]);
                minZ = Math.min(minZ, model.vertexZ[i]); maxZ = Math.max(maxZ, model.vertexZ[i]);
            }
            System.out.println("    model " + modelId + " vertices=" + model.vertexCount + " triangles=" + model.triangleCount +
                    " bounds x=" + minX + ".." + maxX + " y=" + minY + ".." + maxY + " z=" + minZ + ".." + maxZ);
            System.out.println("    priority=" + (model.priority & 0xFF) +
                    " triangleInfo=" + (model.triangleInfo == null ? "null" : "present") +
                    " trianglePriorities=" + (model.trianglePriorities == null ? "null" : "present") +
                    " triangleAlpha=" + (model.triangleAlpha == null ? "null" : "present") +
                    " triangleTextures=" + (model.triangleTextures == null ? "null" : "present"));
            if (model.vertexBones != null) {
                System.out.print("    vertexBones=");
                printDistribution(model.vertexBones);
                printBoneBounds(model);
            }
            if (model.triangleBones != null) {
                System.out.print("    triangleBones=");
                printDistribution(model.triangleBones);
            }
        } catch (Exception ex) {
            System.out.println("    model " + modelId + " error " + ex.getMessage());
        }
    }

    private static void printDistribution(int[] values) {
        int[] counts = new int[256];
        for (int value : values) {
            if (value >= 0 && value < counts.length) {
                counts[value]++;
            }
        }
        boolean first = true;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                if (!first) {
                    System.out.print(", ");
                }
                System.out.print(i + ":" + counts[i]);
                first = false;
            }
        }
        System.out.println();
    }

    private static void printBoneBounds(RawModel model) {
        int[] minX = new int[256], minY = new int[256], minZ = new int[256];
        int[] maxX = new int[256], maxY = new int[256], maxZ = new int[256];
        int[] counts = new int[256];
        for (int i = 0; i < 256; i++) {
            minX[i] = minY[i] = minZ[i] = Integer.MAX_VALUE;
            maxX[i] = maxY[i] = maxZ[i] = Integer.MIN_VALUE;
        }
        for (int i = 0; i < model.vertexCount; i++) {
            int bone = model.vertexBones[i];
            if (bone < 0 || bone >= counts.length) {
                continue;
            }
            counts[bone]++;
            minX[bone] = Math.min(minX[bone], model.vertexX[i]);
            maxX[bone] = Math.max(maxX[bone], model.vertexX[i]);
            minY[bone] = Math.min(minY[bone], model.vertexY[i]);
            maxY[bone] = Math.max(maxY[bone], model.vertexY[i]);
            minZ[bone] = Math.min(minZ[bone], model.vertexZ[i]);
            maxZ[bone] = Math.max(maxZ[bone], model.vertexZ[i]);
        }
        for (int bone = 0; bone < counts.length; bone++) {
            if (counts[bone] > 0) {
                System.out.println("    bone " + bone + " bounds x=" + minX[bone] + ".." + maxX[bone] +
                        " y=" + minY[bone] + ".." + maxY[bone] + " z=" + minZ[bone] + ".." + maxZ[bone]);
            }
        }
    }

    private static int readUnsignedShort(byte[] bytes, int pos) {
        return ((bytes[pos] & 0xFF) << 8) | (bytes[pos + 1] & 0xFF);
    }

    private static int readInt(byte[] bytes, int pos) {
        return ((bytes[pos] & 0xFF) << 24) | ((bytes[pos + 1] & 0xFF) << 16) | ((bytes[pos + 2] & 0xFF) << 8) | (bytes[pos + 3] & 0xFF);
    }
}
