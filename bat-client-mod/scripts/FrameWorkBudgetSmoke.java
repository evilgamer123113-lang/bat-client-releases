import com.batclient.hud.FrameWorkBudget;
public class FrameWorkBudgetSmoke {
    public static void main(String[] args){
        FrameWorkBudget budget=new FrameWorkBudget();budget.begin(0,2);
        check(budget.take(0)&&budget.take(1_000_000),"work below budget");
        check(!budget.take(2_000_000)&&!budget.take(5_000_000),"defer excess work");
        budget.begin(10_000_000,2);check(budget.take(15_000_000)&&!budget.take(15_000_001),"at least one task makes progress");
        budget.begin(20_000_000,Float.NaN);check(budget.take(20_000_000)&&!budget.take(22_000_000),"invalid setting fallback");
        // Identical upload workload: an elapsed-only loop starts a second 1.4ms task
        // inside a 2ms slice. Cost-aware admission carries it to the next slice.
        budget=new FrameWorkBudget();int completed=0,slices=0;long worst=0;
        while(completed<100){
            budget.begin(0,2);long clock=0;slices++;
            while(completed<100&&budget.take(clock)){
                clock+=1_400_000;budget.completed(1_400_000);completed++;
            }
            worst=Math.max(worst,clock);
            check(slices<=100,"minimum progress prevents starvation");
        }
        check(completed==100&&worst==1_400_000,"all uploads drain without a predictable second-task overshoot");
        budget.begin(0,.35f);check(budget.take(99_000_000),"expensive history cannot suppress first job");
        budget.completed(0);budget.completed(-1);
        budget.begin(0,2);check(budget.take(0)&&!budget.take(1_400_000),"invalid task durations do not erase cost history");
        for(int i=0;i<40;i++)budget.completed(50_000);
        budget.begin(0,2);check(budget.take(0)&&budget.take(100_000),"cheaper tasks recover multi-job throughput");
        budget.completed(Long.MAX_VALUE);budget.begin(0,.35f);
        check(budget.take(0)&&!budget.take(1),"exceptionally slow upload still permits bounded progress");
        System.out.println("FRAME_WORK_BUDGET_OK: deadline admission, recovery, invalid timing and 100-job drain");
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
