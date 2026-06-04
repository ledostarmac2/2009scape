import java.io.File;
import rt4.BufferedFile;
import rt4.Buffer;
import rt4.Cache;
import rt4.FileOnDisk;
import rt4.Js5Index;

public class InspectJs5Archives {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        BufferedFile data = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.dat2"), "r", Long.MAX_VALUE), 5200, 0);
        BufferedFile idx255 = new BufferedFile(new FileOnDisk(new File(dir, "main_file_cache.idx255"), "r", Long.MAX_VALUE), 6000, 0);
        Cache master = new Cache(255, data, idx255, 1000000);
        for (int archive = 0; archive < 30; archive++) {
            byte[] index = master.read(archive);
            if (index == null) {
                continue;
            }
            Js5Index js5Index = new Js5Index(index, Buffer.crc32(index, index.length));
            int maxGroup = js5Index.capacity;
            int groups = js5Index.groupIds == null ? 0 : js5Index.groupIds.length;
            int group56Cap = maxGroup > 56 ? js5Index.groupCapacities[56] : -1;
            System.out.println("archive=" + archive + " capacity=" + maxGroup + " groups=" + groups + " group56Cap=" + group56Cap);
        }
    }
}
