import com.batclient.performance.AdaptiveChunkBudget;
import com.batclient.performance.RendererCompatibility;
import com.batclient.hud.FrameWorkBudget;
import java.util.ArrayDeque;
import java.util.Queue;

public final class AdaptiveChunkBudgetSmoke {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static void main(String[] args){
        AdaptiveChunkBudget budget=new AdaptiveChunkBudget();
        check(RendererCompatibility.useVanillaChunkHooks(id->false),"vanilla scheduler enabled");
        for(String owner:new String[]{"sodium","embeddium","rubidium","optifabric"})
            check(!RendererCompatibility.useVanillaChunkHooks(owner::equals),"renderer ownership: "+owner);
        check(RendererCompatibility.useVanillaChunkHooks("iris"::equals),"shader-only mod does not disable vanilla scheduler");
        float idle=budget.allowance(true,1,120);
        for(int i=0;i<100;i++)budget.observe(1_000_000);
        float fast=budget.allowance(true,1,120);
        check(fast>idle&&fast<=2.25f,"headroom gradually restores bounded upload budget");
        budget.observe(60_000_000);
        float stressed=budget.allowance(true,1,120);
        check(stressed<fast&&stressed>=.35f,"slow frames reduce chunk allowance quickly");
        check(budget.allowance(false,1,120)>=.15f,"small rebuild allowance stays positive");
        for(int i=0;i<200;i++)budget.observe(1_000_000);
        long now=1_000_000_000L;budget.combat(now);
        check(budget.fighting(now)&&!budget.fighting(now+3_000_000_000L),"bounded combat window");
        check(budget.allowance(true,now,60)<=.75f&&budget.allowance(false,now,60)<=.25f,"combat bounds expensive chunk work");
        check(budget.allowance(true,now+4_000_000_000L,60)>budget.allowance(true,now,60),"recovery after combat");
        check(budget.allowance(true,now+4_000_000_000L,240)<=budget.allowance(true,now+4_000_000_000L,60),"high frame targets have smaller allowance");
        float before=budget.allowance(true,1,120);budget.observe(-1);budget.observe(0);
        check(before==budget.allowance(true,1,120),"invalid measurements ignored");
        budget.reset();check(!budget.fighting(now)&&budget.allowance(true,1,120)==idle,"world-change reset");
        for(int i=0;i<100;i++)budget.observe(1_000_000);
        for(int i=0;i<30;i++)budget.observeFrame(8_000_000);
        float clear=budget.allowance(true,1,120);
        budget.observeFrame(300_000_000);
        check(budget.allowance(true,1,120)<clear,"presentation stall reduces uploads even with cheap world rendering");
        check(budget.allowance(true,1,120)>=.35f,"presentation pressure still permits forward progress");
        for(int i=0;i<30;i++)budget.observeFrame(8_000_000);
        check(budget.allowance(true,1,120)==clear,"isolated stall does not leave terrain permanently throttled");
        float stable=budget.allowance(true,1,120);budget.observeFrame(0);budget.observeFrame(-1);
        check(budget.allowance(true,1,120)==stable,"invalid frame observations ignored");
        budget.observeFrame(Long.MAX_VALUE);budget.clearFramePressure();
        check(budget.allowance(true,1,120)==clear,"focus/menu transition clears prior presentation pressure");
        AdaptiveChunkBudget pending=new AdaptiveChunkBudget();
        for(int i=0;i<200;i++){pending.observe(1_000_000);pending.observeFrame(50_000_000);}
        long queuedAt=10_000_000_000L;
        float noQueue=pending.allowance(true,queuedAt,120);
        pending.pendingUploads(queuedAt,true);
        check(pending.allowance(true,queuedAt,120)==noQueue,"fresh backlog does not create an immediate burst");
        float catchUp=pending.allowance(true,queuedAt+1_000_000_000L,120);
        check(catchUp>noQueue&&catchUp<=2.25f,"persistent pending uploads recover bounded render headroom");
        check(pending.allowance(false,queuedAt+1_000_000_000L,120)<=.75f,"upload backlog does not expand synchronous rebuild work");
        pending.combat(queuedAt);
        check(pending.allowance(true,queuedAt+1_000_000_000L,120)<=.75f,"combat bound wins over backlog recovery");
        pending.pendingUploads(queuedAt+4_000_000_000L,false);
        check(pending.allowance(true,queuedAt+4_000_000_000L,120)==noQueue,"empty upload queue immediately clears catch-up pressure");
        pending.pendingUploads(queuedAt+5_000_000_000L,true);
        for(int i=0;i<200;i++)pending.observe(100_000_000);
        check(pending.allowance(true,queuedAt+6_000_000_000L,120)<=.375f,"backlog never borrows time from expensive world rendering");
        pending.reset();
        check(pending.allowance(true,queuedAt+7_000_000_000L,120)==idle,"world reset clears backlog age");
        Queue<Integer> queue=new ArrayDeque<Integer>();for(int i=0;i<100;i++)queue.add(i);
        FrameWorkBudget slice=new FrameWorkBudget();int uploaded=0,frames=0;
        while(!queue.isEmpty()){
            long clock=0;slice.begin(clock,.35f);frames++;
            while(slice.take(clock)){Integer value=queue.poll();if(value==null)break;check(value==uploaded,"queue order preserved");uploaded++;clock+=200_000;}
        }
        check(uploaded==100&&frames>1,"deferred uploads eventually drain without dropping entries");
        slice.begin(0,.15f);check(slice.take(10_000_000)&&!slice.take(10_000_001),"one expensive task can always make progress");
        System.out.println("ADAPTIVE_CHUNK_BUDGET_OK: "+checks+" checks");
    }
}
