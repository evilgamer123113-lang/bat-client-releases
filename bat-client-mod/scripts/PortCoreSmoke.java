import com.batclient.hud.*;
import com.batclient.platform.*;
import java.nio.file.*;
import java.util.*;

/** Runs against the actual Java-8-compiled shared sources, not copies or Minecraft stubs. */
public final class PortCoreSmoke {
    private static int checks;
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message);checks++; }
    private static final class Environment implements PlatformEnvironment {
        public String loaderId(){return "test";}
        public Path getGameDir(){return Paths.get("build/port-core/runtime");}
        public Path getConfigDir(){return getGameDir().resolve("config");}
        public boolean isModLoaded(String id){return "appleskin".equals(id);}
        public boolean isDevelopmentEnvironment(){return false;}
    }
    public static void main(String[] args) {
        PlatformEnvironment fixture=new Environment();
        check(Platforms.discover(Collections.singletonList(fixture))==fixture,"selected platform preserves identity");
        check(fixture.getConfigDir().equals(Paths.get("build/port-core/runtime/config")),"config path preserved");
        check(fixture.isModLoaded("appleskin")&&!fixture.isModLoaded("other"),"conflict lookup");
        try {Platforms.discover(Collections.<PlatformEnvironment>emptyList());throw new AssertionError("missing provider accepted");}
        catch(IllegalStateException expected){checks++;}
        try {Platforms.discover(Arrays.asList(fixture,fixture));throw new AssertionError("ambiguous loaders accepted");}
        catch(IllegalStateException expected){checks++;}
        check(ArmorWarning.low(100,85,true,15)&&!ArmorWarning.low(100,84,true,15),"armor threshold");
        check(!ArmorWarning.low(0,0,true,15)&&!ArmorWarning.low(100,99,false,15),"armor disabled/non-damageable");
        check(ArmorWarning.low(100,85,true,Float.NaN)&&!ArmorWarning.low(100,40,true,100),"armor bounds");
        check(ArmorWarning.visible(true,0)&&!ArmorWarning.visible(true,700),"flashing");
        check(PotionExpiry.warning(200,false,true,10)&&!PotionExpiry.warning(201,false,true,10),"expiry inclusive");
        check(!PotionExpiry.warning(-1,true,true,10)&&!PotionExpiry.warning(0,false,true,10),"infinite/expired potion");
        check(ItemCounter.count(4,i->i%2==0,i->i+1)==4,"inventory count");
        check(ItemCounter.warning(2,true,2,1)&&!ItemCounter.warning(3,true,2,1),"item warning threshold");
        check(HealthValues.row(10,20).equals("♥♥♥♥♥♡♡♡♡♡"),"health row");
        check(HealthValues.fill(1,20,0)==1&&HealthValues.fill(0,20,0)==0,"half/empty hearts");
        check(HealthValues.health(Float.NaN,20)==0&&HealthValues.max(Float.NaN)==20,"health invalid fallback");
        FrameWorkBudget budget=new FrameWorkBudget();budget.begin(0,2);
        check(budget.take(0)&&budget.take(1_000_000)&&!budget.take(2_000_000),"chunk budget");
        budget.begin(0,Float.NaN);check(budget.take(9_000_000)&&!budget.take(9_000_001),"forward progress and invalid fallback");
        check(ZoomInterpolation.target(true,8)==.125&&ZoomInterpolation.target(false,8)==1,"zoom target");
        check(ZoomInterpolation.scrollLevel(8,100,2,40)<=40,"scroll bound");
        check(ZoomInterpolation.step(1,.125,.02)<1&&ZoomInterpolation.step(1,.125,.02)>.125,"smooth interpolation");
        CombatTracker combat=new CombatTracker();combat.attack(3,2.5,1000);
        combat.confirm(3,1100,3000,true,true);
        check(combat.combo(1100,3000)==1&&combat.reach(1100,3000)==2.5,"confirmed hit");
        combat.reset();check(combat.combo(1100,3000)==0&&Double.isNaN(combat.reach(1100,3000)),"combat reset");
        System.out.println("PORT_CORE_OK: "+checks+" checks (Java 8 API/bytecode)");
    }
}
