import java.io.File;
import java.security.MessageDigest;
import rt4.BufferedFile;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Compression;

/** Compare texture group bytes and texture-config entries between caches. */
public final class CompareTextures {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: CompareTextures <cacheA> <cacheB> <id> [id..]");
            return;
        }
        File a = new File(args[0]);
        File b = new File(args[1]);
        for (int i = 2; i < args.length; i++) {
            int id = Integer.parseInt(args[i]);
            byte[] ta = readTexture(a, id);
            byte[] tb = readTexture(b, id);
            System.out.println("texture " + id + ":");
            System.out.println("  A=" + label(a.getName(), ta));
            System.out.println("  B=" + label(b.getName(), tb));
            System.out.println("  same=" + (ta != null && tb != null && md5(ta).equals(md5(tb))));
            byte[] ca = readConfig(a, id);
            byte[] cb = readConfig(b, id);
            System.out.println("  configA=" + label("cfg", ca));
            System.out.println("  configB=" + label("cfg", cb));
            System.out.println("  configSame=" + (ca != null && cb != null && md5(ca).equals(md5(cb))));
        }
    }

    private static String label(String who, byte[] data) throws Exception {
        if (data == null) return who + ":MISSING";
        return who + ":" + data.length + "b md5=" + md5(data);
    }

    private static byte[] readTexture(File cacheDir, int id) throws Exception {
        Cache textures = open(cacheDir, 9);
        byte[] packed = textures.read(id);
        if (packed == null) return null;
        return Js5Compression.uncompress(strip(packed));
    }

    private static byte[] readConfig(File cacheDir, int id) throws Exception {
        Cache configs = open(cacheDir, 26);
        byte[] packed = configs.read(id);
        if (packed == null) return null;
        return Js5Compression.uncompress(strip(packed));
    }

    private static Cache open(File cacheDir, int archive) throws Exception {
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        return new Cache(archive, data, new BufferedFile(new FileOnDisk(new File(cacheDir, "main_file_cache.idx" + archive), "r", Long.MAX_VALUE), 6000, 0), 1000000);
    }

    private static byte[] strip(byte[] group) {
        if (group.length < 2) return group;
        byte[] out = new byte[group.length - 2];
        System.arraycopy(group, 0, out, 0, out.length);
        return out;
    }

    private static String md5(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
