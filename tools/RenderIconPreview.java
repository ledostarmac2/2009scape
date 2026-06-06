import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5;
import rt4.Js5Index;
import rt4.Js5ResourceProvider;
import rt4.RawModel;

/**
 * Offline preview of an item inventory icon: decodes the packed model from the cache
 * (model archive 7) and renders it at the RS 2D icon angles (xan2d pitch, yan2d yaw,
 * zan2d roll) so icon orientation can be tuned without launching the client.
 *
 * Usage: RenderIconPreview <cacheDir> <modelId> <xan> <yan> <zan> <zoom> <out.png>
 * Angles are 0..2047 (== 0..360 deg), matching item-def opcodes 5/6/(roll).
 */
public final class RenderIconPreview {
    private static final class DiskProvider extends Js5ResourceProvider {
        private final Cache master, archive;
        private final int archiveId;
        DiskProvider(File dir, int archiveId) throws Exception {
            this.archiveId = archiveId;
            BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
            master = new Cache(255, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0), 1000000);
            archive = new Cache(archiveId, data, new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx" + archiveId), "r", Long.MAX_VALUE), 6000, 0), 1000000);
        }
        public Js5Index fetchIndex() { byte[] i = master.read(archiveId); return i == null ? null : new Js5Index(i, Buffer.crc32(i, i.length)); }
        public void prefetchGroup(int g) {}
        public int getPercentageComplete(int g) { return 100; }
        public byte[] fetchGroup(int g) { return archive.read(g); }
    }

    public static void main(String[] args) throws Exception {
        File cache = new File(args[0]);
        int modelId = Integer.parseInt(args[1]);
        int xan = Integer.parseInt(args[2]);
        int yan = Integer.parseInt(args[3]);
        int zan = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        int zoom = args.length > 5 ? Integer.parseInt(args[5]) : 0; // 0 => auto-fit
        String out = args.length > 6 ? args[6] : "icon.png";

        Js5 models = new Js5(new DiskProvider(cache, 7), false, false);
        RawModel m = new RawModel(models.fetchFile(modelId, 0));

        // Rotate each vertex. RS applies (in the icon path) roll Z, then pitch X, then yaw Y.
        double rx = xan * Math.PI * 2 / 2048.0;
        double ry = yan * Math.PI * 2 / 2048.0;
        double rz = zan * Math.PI * 2 / 2048.0;
        double[] px = new double[m.vertexCount], py = new double[m.vertexCount], pz = new double[m.vertexCount];
        for (int i = 0; i < m.vertexCount; i++) {
            double x = m.vertexX[i], y = m.vertexY[i], z = m.vertexZ[i];
            // roll about Z
            double x1 = x * Math.cos(rz) - y * Math.sin(rz);
            double y1 = x * Math.sin(rz) + y * Math.cos(rz);
            double z1 = z;
            // pitch about X
            double y2 = y1 * Math.cos(rx) - z1 * Math.sin(rx);
            double z2 = y1 * Math.sin(rx) + z1 * Math.cos(rx);
            double x2 = x1;
            // yaw about Y
            double x3 = x2 * Math.cos(ry) + z2 * Math.sin(ry);
            double z3 = -x2 * Math.sin(ry) + z2 * Math.cos(ry);
            double y3 = y2;
            px[i] = x3; py[i] = y3; pz[i] = z3;
        }

        int size = 64; // RS inventory icon is 32x32; render 64 for clarity
        // auto-fit scale
        double minx = 1e9, maxx = -1e9, miny = 1e9, maxy = -1e9;
        for (int i = 0; i < m.vertexCount; i++) { minx = Math.min(minx, px[i]); maxx = Math.max(maxx, px[i]); miny = Math.min(miny, py[i]); maxy = Math.max(maxy, py[i]); }
        double span = Math.max(maxx - minx, maxy - miny);
        double scale = zoom > 0 ? (size / 2.0) / (zoom / 8.0) : (size - 6) / Math.max(1, span);
        double cx = (minx + maxx) / 2, cy = (miny + maxy) / 2;

        int[] img = new int[size * size];
        Arrays.fill(img, 0x202028);
        double[] zbuf = new double[size * size];
        Arrays.fill(zbuf, 1e18);
        double[] light = { 0.4, -0.7, 0.6 };
        double ln = Math.sqrt(light[0]*light[0] + light[1]*light[1] + light[2]*light[2]);
        for (int t = 0; t < m.triangleCount; t++) {
            int a = m.triangleVertexA[t], b = m.triangleVertexB[t], c = m.triangleVertexC[t];
            double ax = size/2 + (px[a]-cx)*scale, ay = size/2 - (py[a]-cy)*scale;
            double bx = size/2 + (px[b]-cx)*scale, by = size/2 - (py[b]-cy)*scale;
            double cxp = size/2 + (px[c]-cx)*scale, cyp = size/2 - (py[c]-cy)*scale;
            // normal (in view space) for shading
            double ux = px[b]-px[a], uy = py[b]-py[a], uz = pz[b]-pz[a];
            double vx = px[c]-px[a], vy = py[c]-py[a], vz = pz[c]-pz[a];
            double nx = uy*vz - uz*vy, ny = uz*vx - ux*vz, nz = ux*vy - uy*vx;
            double nl = Math.sqrt(nx*nx + ny*ny + nz*nz); if (nl == 0) nl = 1;
            double diff = Math.abs((nx*light[0] + ny*light[1] + nz*light[2]) / (nl * ln));
            int shade = (int) (60 + 195 * diff);
            int col = (shade << 16) | (shade << 8) | shade;
            int minpx = (int) Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cxp))));
            int maxpx = (int) Math.min(size - 1, Math.ceil(Math.max(ax, Math.max(bx, cxp))));
            int minpy = (int) Math.max(0, Math.floor(Math.min(ay, Math.min(by, cyp))));
            int maxpy = (int) Math.min(size - 1, Math.ceil(Math.max(ay, Math.max(by, cyp))));
            double d = (by - cyp) * (ax - cxp) + (cxp - bx) * (ay - cyp);
            if (Math.abs(d) < 1e-9) continue;
            double zavg = (pz[a] + pz[b] + pz[c]) / 3.0;
            for (int yy = minpy; yy <= maxpy; yy++) for (int xx = minpx; xx <= maxpx; xx++) {
                double w0 = ((by - cyp) * (xx - cxp) + (cxp - bx) * (yy - cyp)) / d;
                double w1 = ((cyp - ay) * (xx - cxp) + (ax - cxp) * (yy - cyp)) / d;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                int off = yy * size + xx;
                if (zavg < zbuf[off]) { zbuf[off] = zavg; img[off] = col; }
            }
        }
        writePng(out, size, size, img);
        System.out.println("rendered model " + modelId + " xan=" + xan + " yan=" + yan + " zan=" + zan + " -> " + out);
    }

    private static void writePng(String path, int w, int h, int[] argb) throws Exception {
        byte[] raw = new byte[(w * 3 + 1) * h];
        int p = 0;
        for (int y = 0; y < h; y++) { raw[p++] = 0; for (int x = 0; x < w; x++) { int c = argb[y*w+x]; raw[p++]=(byte)(c>>16); raw[p++]=(byte)(c>>8); raw[p++]=(byte)c; } }
        java.io.ByteArrayOutputStream comp = new java.io.ByteArrayOutputStream();
        java.util.zip.Deflater df = new java.util.zip.Deflater(9); df.setInput(raw); df.finish();
        byte[] buf = new byte[8192]; while (!df.finished()) { int n = df.deflate(buf); comp.write(buf, 0, n); }
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        png.write(new byte[]{(byte)0x89,'P','N','G','\r','\n',0x1a,'\n'});
        chunk(png, "IHDR", ihdr(w, h));
        chunk(png, "IDAT", comp.toByteArray());
        chunk(png, "IEND", new byte[0]);
        try (FileOutputStream fo = new FileOutputStream(path)) { fo.write(png.toByteArray()); }
    }
    private static byte[] ihdr(int w, int h) { return new byte[]{(byte)(w>>24),(byte)(w>>16),(byte)(w>>8),(byte)w,(byte)(h>>24),(byte)(h>>16),(byte)(h>>8),(byte)h,8,2,0,0,0}; }
    private static void chunk(java.io.ByteArrayOutputStream o, String type, byte[] data) throws Exception {
        byte[] t = type.getBytes("US-ASCII");
        o.write(new byte[]{(byte)(data.length>>24),(byte)(data.length>>16),(byte)(data.length>>8),(byte)data.length});
        java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(t); crc.update(data);
        o.write(t); o.write(data);
        long v = crc.getValue();
        o.write(new byte[]{(byte)(v>>24),(byte)(v>>16),(byte)(v>>8),(byte)v});
    }
}
