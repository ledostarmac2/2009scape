import core.cache.Cache;
import java.nio.ByteBuffer;
public class DumpReference {
  public static void main(String[] a) throws Throwable {
    Cache.init(a[0]);
    byte[] ref = Cache.generateReferenceData();
    ByteBuffer bb = ByteBuffer.wrap(ref);
    System.out.println("refLen=" + ref.length + " archives=" + (ref.length/8));
    for (int i = 0; i < ref.length/8; i++) {
      int crc = bb.getInt();
      int rev = bb.getInt();
      System.out.println("arch "+i+": crc="+Integer.toUnsignedString(crc)+" rev="+rev);
    }
  }
}
