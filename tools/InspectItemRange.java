import java.io.File;
import rt4.*;
public class InspectItemRange {
    static final int ARCHIVE=19;
    static final class DP extends Js5ResourceProvider {
        final Cache master, archive;
        DP(File dir) throws Exception {
            BufferedFile data=new BufferedFile(new FileOnDisk(new File(dir,"main_file_cache.dat2"),"r",Long.MAX_VALUE),5200,0);
            BufferedFile i255=new BufferedFile(new FileOnDisk(new File(dir,"main_file_cache.idx255"),"r",Long.MAX_VALUE),6000,0);
            BufferedFile i=new BufferedFile(new FileOnDisk(new File(dir,"main_file_cache.idx"+ARCHIVE),"r",Long.MAX_VALUE),6000,0);
            master=new Cache(255,data,i255,1000000); archive=new Cache(ARCHIVE,data,i,1000000);
        }
        public Js5Index fetchIndex(){byte[] x=master.read(ARCHIVE);return x==null?null:new Js5Index(x,Buffer.crc32(x,x.length));}
        public void prefetchGroup(int g){} public int getPercentageComplete(int g){return 100;}
        public byte[] fetchGroup(int g){return archive.read(g);}
    }
    public static void main(String[] a) throws Exception {
        DP p=new DP(new File(a[0])); Js5 js5=new Js5(p,false,false); Js5Index idx=p.fetchIndex();
        int target=14659, g=target>>>8, f=target&255;
        System.out.println("archive19 capacity(groups)="+idx.capacity);
        System.out.println("target 14659 -> group "+g+", file "+f);
        System.out.println("groupSizes["+g+"]="+(g<idx.groupSizes.length?idx.groupSizes[g]:-1));
        int[] fids=js5.getFileIds(g);
        if(fids==null) System.out.println("group "+g+" file ids = dense 0..size-1");
        else { int mx=-1; boolean has=false; for(int x:fids){mx=Math.max(mx,x); if(x==f)has=true;}
            System.out.println("group "+g+" fileCount="+fids.length+" maxFile="+mx+" -> max item="+(g*256+mx)+" ; file "+f+" present? "+has); }
        int maxItem=-1;
        for(int grp=0; grp<idx.capacity; grp++){
            if(grp>=idx.groupSizes.length||idx.groupSizes[grp]==0) continue;
            int[] gf=js5.getFileIds(grp); int gm;
            if(gf==null) gm=idx.groupSizes[grp]-1; else { gm=-1; for(int x:gf) gm=Math.max(gm,x);}
            maxItem=Math.max(maxItem, grp*256+gm);
        }
        System.out.println("MAX CACHED ITEM ID = "+maxItem);
    }
}
