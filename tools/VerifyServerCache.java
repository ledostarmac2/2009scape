import core.cache.Cache;

public class VerifyServerCache {
    public static void main(String[] args) throws Throwable {
        Cache.init(args[0]);
        byte[] reference = Cache.generateReferenceData();
        System.out.println("server cache ok; reference bytes=" + reference.length);
    }
}
