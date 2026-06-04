import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import rt4.*;
public class IndexRoundTrip {
    static final int ARCHIVE=19;
    static int pos;
    static int u8(byte[]b){return b[pos++]&0xFF;}
    static int u16(byte[]b){return (u8(b)<<8)|u8(b);}
    static int u32(byte[]b){return (u8(b)<<24)|(u8(b)<<16)|(u8(b)<<8)|u8(b);}
    static void w8(ByteArrayOutputStream o,int v){o.write(v&0xFF);}
    static void w16(ByteArrayOutputStream o,int v){o.write((v>>>8)&0xFF);o.write(v&0xFF);}
    static void w32(ByteArrayOutputStream o,int v){o.write((v>>>24)&0xFF);o.write((v>>>16)&0xFF);o.write((v>>>8)&0xFF);o.write(v&0xFF);}
    public static void main(String[] a) throws Exception {
        File dir=new File(a[0]);
        BufferedFile data=new BufferedFile(new FileOnDisk(new File(dir,"main_file_cache.dat2"),"r",Long.MAX_VALUE),5200,0);
        BufferedFile i255=new BufferedFile(new FileOnDisk(new File(dir,"main_file_cache.idx255"),"r",Long.MAX_VALUE),6000,0);
        Cache master=new Cache(255,data,i255,1000000);
        byte[] raw=Js5Compression.uncompress(master.read(ARCHIVE));
        // parse
        pos=0;
        int protocol=u8(raw); int version=(protocol>=6)?u32(raw):0; int flags=u8(raw); boolean names=(flags&1)!=0;
        int gc=u16(raw); int[] gid=new int[gc]; int acc=0; for(int i=0;i<gc;i++){acc+=u16(raw);gid[i]=acc;}
        int[] gnh=new int[gc]; if(names) for(int i=0;i<gc;i++) gnh[i]=u32(raw);
        int[] crc=new int[gc]; for(int i=0;i<gc;i++) crc[i]=u32(raw);
        int[] ver=new int[gc]; for(int i=0;i<gc;i++) ver[i]=u32(raw);
        int[] fc=new int[gc]; for(int i=0;i<gc;i++) fc[i]=u16(raw);
        int[][] fid=new int[gc][]; for(int i=0;i<gc;i++){fid[i]=new int[fc[i]];int x=0;for(int j=0;j<fc[i];j++){x+=u16(raw);fid[i][j]=x;}}
        int[][] fnh=null; if(names){fnh=new int[gc][];for(int i=0;i<gc;i++){fnh[i]=new int[fc[i]];for(int j=0;j<fc[i];j++)fnh[i][j]=u32(raw);}}
        System.out.println("protocol="+protocol+" version="+version+" flags="+flags+" names="+names+" groupCount="+gc+" consumed="+pos+"/"+raw.length);
        // re-emit unchanged
        ByteArrayOutputStream o=new ByteArrayOutputStream();
        w8(o,protocol); if(protocol>=6) w32(o,version); w8(o,flags); w16(o,gc);
        int pr=0; for(int i=0;i<gc;i++){w16(o,gid[i]-pr);pr=gid[i];}
        if(names) for(int i=0;i<gc;i++) w32(o,gnh[i]);
        for(int i=0;i<gc;i++) w32(o,crc[i]);
        for(int i=0;i<gc;i++) w32(o,ver[i]);
        for(int i=0;i<gc;i++) w16(o,fc[i]);
        for(int i=0;i<gc;i++){int q=0;for(int j=0;j<fc[i];j++){w16(o,fid[i][j]-q);q=fid[i][j];}}
        if(names) for(int i=0;i<gc;i++) for(int j=0;j<fc[i];j++) w32(o,fnh[i][j]);
        byte[] out=o.toByteArray();
        System.out.println("round-trip identical: "+Arrays.equals(raw,out)+" (orig "+raw.length+", mine "+out.length+")");
        if(!Arrays.equals(raw,out)) for(int i=0;i<Math.min(raw.length,out.length);i++) if(raw[i]!=out[i]){System.out.println("first diff at "+i);break;}
    }
}
