package com.batclient.hud;

/** Double-buffered, bounded work queue for a north-up surface map. No world loading. */
public final class TerrainMapRaster {
    public static final int SIZE=128;
    @FunctionalInterface public interface Sampler {int color(int x,int z);}
    public record Frame(int x,int z,int radius,int[] pixels) { }
    private Frame front,work;
    private int cursor;
    public Frame frame(){return front;}
    public boolean building(){return work!=null;}
    public void clear(){front=null;work=null;cursor=0;}
    public void begin(int x,int z,int radius){work=new Frame(x,z,Math.clamp(radius,16,96),new int[SIZE*SIZE]);cursor=0;}
    public boolean step(int budget,Sampler sampler){
        return step(budget,sampler,null);
    }
    public boolean step(int budget,Sampler sampler,FrameWorkBudget timeBudget){
        if(work==null)return false;
        int end=Math.min(SIZE*SIZE,cursor+Math.clamp(budget,1,2048));
        for(;cursor<end;cursor++){
            if(timeBudget!=null&&!timeBudget.take(System.nanoTime()))break;
            long started=timeBudget==null?0:System.nanoTime();
            int x=cursor%SIZE,z=cursor/SIZE;
            int wx=(int)Math.floor(work.x()-work.radius()+(x+.5)*work.radius()*2/SIZE);
            int wz=(int)Math.floor(work.z()-work.radius()+(z+.5)*work.radius()*2/SIZE);
            int color=sampler.color(wx,wz);
            // Transparent rounded corners remain independent of the module plate.
            int edgeX=Math.min(x,SIZE-1-x),edgeZ=Math.min(z,SIZE-1-z);
            if(edgeX<3&&edgeZ<3&&(3-edgeX)*(3-edgeX)+(3-edgeZ)*(3-edgeZ)>9)color=0;
            work.pixels()[cursor]=color;
            if(timeBudget!=null)timeBudget.completed(System.nanoTime()-started);
        }
        if(cursor<SIZE*SIZE)return false;
        front=work;work=null;return true;
    }
    public static int abgr(int argb){return (argb&0xFF00FF00)|((argb&255)<<16)|((argb>>>16)&255);}
}
