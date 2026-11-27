import com.batclient.hud.TrailHistory;

public final class TrailHistorySmoke {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    public static void main(String[] args){
        var h=new TrailHistory();check(h.points("A",0,60000,64).isEmpty(),"empty session");
        check(h.sample("A",0,64,0,0,60000,64,1),"initial point");check(!h.sample("A",.2,64,0,100,60000,64,1),"stationary jitter does not grow trail");
        h.sample("A",1,64,0,200,60000,64,1);h.sample("A",2,64,0,400,60000,64,1);var a=h.points("A",400,60000,64);check(a.size()==3,"spacing records walked points");check(a.get(0).breakBefore()&&!a.get(1).breakBefore(),"first point starts route, next connects");check(TrailHistory.latestRoute(a).size()==3,"continuous section available for retrace");
        h.sample("B",0,70,0,500,60000,64,1);check(h.points("B",500,60000,64).getFirst().breakBefore(),"dimension starts separate route");check(h.points("A",500,60000,64).size()==3,"other dimension retains original points");
        h.sample("A",3,64,0,600,60000,64,1);check(TrailHistory.latestRoute(h.points("A",600,60000,64)).size()==1,"context return does not bridge portal gap");
        h.sample("A",100,64,0,700,60000,64,1);h.sample("A",101,64,0,800,60000,64,1);a=h.points("A",800,60000,64);check(a.get(a.size()-2).breakBefore(),"large teleport creates break");check(TrailHistory.latestRoute(a).size()==2,"retrace stops at last teleport boundary");
        h.interrupt();h.sample("A",101,64,0,900,60000,64,1);check(h.points("A",900,60000,64).getLast().breakBefore(),"excluded movement interrupts even at same position");
        int size=h.points("A",900,60000,64).size();check(!h.sample("A",Double.NaN,0,0,1000,60000,64,1),"invalid coordinates rejected");check(!h.sample("",0,0,0,1000,60000,64,1),"empty context rejected");check(h.points("A",1000,60000,64).size()==size,"rejected sample preserves trail");
        var bounded=new TrailHistory();for(int i=0;i<200;i++)bounded.sample("walk",i,64,0,i,60000,64,1);var p=bounded.points("walk",200,60000,64);check(p.size()==64&&p.getFirst().x()==136,"point budget evicts oldest only");check(TrailHistory.latestRoute(p).size()==64,"pruned route starts at retained beginning");check(bounded.points("walk",200,60000,32).size()==32,"lowered cap takes effect");check(bounded.points("walk",60200,60000,64).isEmpty(),"time expiry removes stale breadcrumbs");
        for(int i=0;i<9;i++)bounded.sample("world"+i,0,64,0,i,60000,64,1);check(bounded.points("world0",10,60000,64).isEmpty(),"world cache is bounded");check(bounded.points("world8",10,60000,64).size()==1,"latest world kept");bounded.clear("world8");check(bounded.points("world8",10,60000,64).isEmpty(),"clear only selected context");check(bounded.points("world7",10,60000,64).size()==1,"clear leaves other context intact");bounded.clearAll();check(bounded.points("world7",10,60000,64).isEmpty(),"disconnect clears session");
        System.out.println("TRAIL_HISTORY_OK: "+checks+" checks");
    }
}
