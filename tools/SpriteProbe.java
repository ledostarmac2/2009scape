import java.io.File;
import rt4.*;
public final class SpriteProbe {
    static final class DP extends Js5ResourceProvider {
        final Cache m,a; final int id;
        DP(File d,int ar) throws Exception { id=ar;
            BufferedFile data=new BufferedFile(new FileOnDisk(new File(d,"main_file_cache.dat2"),"r",Long.MAX_VALUE),5200,0);
            m=new Cache(255,data,new BufferedFile(new FileOnDisk(new File(d,"main_file_cache.idx255"),"r",Long.MAX_VALUE),6000,0),1000000);
            a=new Cache(ar,data,new BufferedFile(new FileOnDisk(new File(d,"main_file_cache.idx"+ar),"r",Long.MAX_VALUE),6000,0),1000000);}
        public Js5Index fetchIndex(){byte[] x=m.read(id);return x==null?null:new Js5Index(x,Buffer.crc32(x,x.length));}
        public void prefetchGroup(int g){} public int getPercentageComplete(int g){return 100;} public byte[] fetchGroup(int g){return a.read(g);} }
    public static void main(String[] args) throws Exception {
        Js5 js5=new Js5(new DP(new File(args[0]),8),false,false);
        for(int i=1;i<args.length;i++){int id=Integer.parseInt(args[i]);
            SoftwareSprite[] s=SpriteLoader.loadSoftwareSprites(id,js5);
            System.out.println("sprite "+id+": frames="+SpriteLoader.frames+" canvasW="+SpriteLoader.width+" canvasH="+SpriteLoader.height
                +" innerW="+java.util.Arrays.toString(SpriteLoader.innerWidths)+" innerH="+java.util.Arrays.toString(SpriteLoader.innerHeights)
                +" offX="+java.util.Arrays.toString(SpriteLoader.xOffsets)+" offY="+java.util.Arrays.toString(SpriteLoader.yOffsets));}
    }
}
