import com.batclient.hud.TerrainMapRaster;
import com.batclient.hud.FrameWorkBudget;
import java.util.concurrent.atomic.AtomicInteger;
public class TerrainMapSmoke {
    private static int checks;
    public static void main(String[] args){
        var map=new TerrainMapRaster();var calls=new AtomicInteger();
        map.begin(-16,32,48);check(map.building()&&map.frame()==null);
        check(!map.step(256,(x,z)->{calls.incrementAndGet();checkBounds(x,z);return 0xFF123456;}));check(calls.get()==256&&map.frame()==null);
        for(int i=1;i<64;i++)map.step(256,(x,z)->0xFF123456);
        check(!map.building()&&map.frame()!=null&&map.frame().pixels().length==16384);
        check(map.frame().pixels()[0]==0&&map.frame().pixels()[64*128+64]==0xFF123456);
        check(map.frame().x()==-16&&map.frame().z()==32&&map.frame().radius()==48);
        var old=map.frame();map.begin(100,100,999);check(map.frame()==old);
        calls.set(0);map.step(99999,(x,z)->{calls.incrementAndGet();return 0xFF000000;});check(calls.get()==2048&&map.frame()==old);
        while(!map.step(512,(x,z)->0xFF000000)) { }
        check(map.frame()!=old&&map.frame().radius()==96&&old.pixels()[64*128+64]==0xFF123456);
        check(TerrainMapRaster.abgr(0xAA123456)==0xAA563412);
        map.clear();check(map.frame()==null&&!map.building());
        map.begin(0,0,32);calls.set(0);
        var timeBudget=new FrameWorkBudget();
        timeBudget.begin(System.nanoTime()-10_000_000L,.1f);
        check(!map.step(2048,(x,z)->{calls.incrementAndGet();return 0xFF123456;},timeBudget));
        check(calls.get()==1&&map.building()&&map.frame()==null);
        while(!map.step(2048,(x,z)->{calls.incrementAndGet();return 0xFF123456;})) { }
        check(calls.get()==16384&&map.frame()!=null);
        check(map.frame().pixels()[64*128+64]==0xFF123456);
        System.out.println("TERRAIN_MAP_OK: "+checks+" checks");
    }
    private static void checkBounds(int x,int z){if(x< -64||x>=32||z< -16||z>=80)throw new AssertionError("sample escaped radius");}
    private static void check(boolean value){if(!value)throw new AssertionError("terrain check "+checks);checks++;}
}
