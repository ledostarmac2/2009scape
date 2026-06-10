import com.displee.cache.CacheLibrary;
import com.displee.cache.index.Index;
import com.displee.cache.index.archive.Archive;
import com.displee.cache.index.archive.file.File;
import java.nio.charset.StandardCharsets;

public final class ProbeDispleeCache {
    public static void main(String[] args) throws Exception {
        byte[] needle = args[1].getBytes(StandardCharsets.UTF_8);
        try (CacheLibrary lib = CacheLibrary.create(args[0])) {
            for (int idx = 0; idx < lib.getIndices().length; idx++) {
                if (!lib.exists(idx)) continue;
                Index index = lib.index(idx);
                for (int aid : index.archiveIds()) {
                    Archive archive = index.archive(aid, false);
                    if (archive == null) continue;
                    File[] files = archive.files();
                    if (files == null) continue;
                    for (File file : files) {
                        byte[] data = file.getData();
                        if (data == null || data.length < needle.length) continue;
                        if (contains(data, needle)) {
                            System.out.println("raw hit idx=" + idx + " arch=" + aid + " file=" + file.getId()
                                    + " bytes=" + data.length);
                        }
                    }
                }
            }
        }
    }

    private static boolean contains(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
