import com.batclient.performance.FastCubicSampler;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Compare against the real unmodified Minecraft sampler in a plain JVM (no Mixin launcher). */
public final class CubicSamplerSmoke {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static Vec3d color(int x,int y,int z){return new Vec3d(Math.sin(x*.37+y),Math.cos(z*.21-x),((x*31L+y*17L+z*7L)&255)/255d);}
    public static void main(String[] args){
        Random random=new Random(81246);List<String> oldCalls=new ArrayList<>(),newCalls=new ArrayList<>();
        CubicSampler.sampleColor(new Vec3d(-.25,1.5,7.75),(x,y,z)->{oldCalls.add(x+","+y+","+z);return color(x,y,z);});
        FastCubicSampler.sample(new Vec3d(-.25,1.5,7.75),(x,y,z)->{newCalls.add(x+","+y+","+z);return color(x,y,z);});
        check(oldCalls.size()==216&&newCalls.equals(oldCalls),"identical callback count, order and coordinates");
        for(int i=0;i<1024;i++){
            Vec3d position=new Vec3d(random.nextDouble()*6000-3000,random.nextDouble()*512-128,random.nextDouble()*6000-3000);
            Vec3d expected=CubicSampler.sampleColor(position,CubicSamplerSmoke::color);
            Vec3d actual=FastCubicSampler.sample(position,CubicSamplerSmoke::color);
            check(Double.doubleToLongBits(expected.x)==Double.doubleToLongBits(actual.x)
                &&Double.doubleToLongBits(expected.y)==Double.doubleToLongBits(actual.y)
                &&Double.doubleToLongBits(expected.z)==Double.doubleToLongBits(actual.z),"bit-exact RGB at "+position);
        }
        Vec3d fixed=new Vec3d(.3,.6,.9),at=new Vec3d(1.25,-8.75,10.5);
        Vec3d uniform=FastCubicSampler.sample(at,(x,y,z)->fixed);
        check(Math.abs(uniform.x-fixed.x)<1e-12&&Math.abs(uniform.y-fixed.y)<1e-12&&Math.abs(uniform.z-fixed.z)<1e-12,"uniform color preserved");
        var bean=java.lang.management.ManagementFactory.getThreadMXBean();
        if(bean instanceof com.sun.management.ThreadMXBean allocation&&allocation.isThreadAllocatedMemorySupported()){
            allocation.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();
            CubicSampler.RgbFetcher fetcher=(x,y,z)->fixed;
            for(int i=0;i<2000;i++){CubicSampler.sampleColor(at,fetcher);FastCubicSampler.sample(at,fetcher);}
            long start=allocation.getThreadAllocatedBytes(thread);
            for(int i=0;i<2000;i++)CubicSampler.sampleColor(at,fetcher);
            long vanilla=allocation.getThreadAllocatedBytes(thread)-start;
            start=allocation.getThreadAllocatedBytes(thread);
            for(int i=0;i<2000;i++)FastCubicSampler.sample(at,fetcher);
            long optimized=allocation.getThreadAllocatedBytes(thread)-start;
            System.out.println("CUBIC_ALLOCATION_DIAGNOSTIC: vanilla="+(vanilla/2000)+" optimized="+(optimized/2000)+" bytes/call (fixed test fetcher; not an FPS benchmark)");
            check(optimized<=vanilla,"allocation regression");
        }
        System.out.println("CUBIC_SAMPLER_OK: "+checks+" checks against real Minecraft 1.21.1");
    }
}
