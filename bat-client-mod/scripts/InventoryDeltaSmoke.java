import com.batclient.hud.InventoryDeltaTracker;
import java.util.*;
public class InventoryDeltaSmoke {
    private static int checks;
    public static void main(String[] args){
        var tracker=new InventoryDeltaTracker<String>();
        tracker.sample(Map.of("stone",32),0,4000,true,true);check(tracker.entries(0).isEmpty());
        tracker.sample(Map.of("stone",64,"diamond",1),250,4000,true,true);check(tracker.entries(250).size()==2&&delta(tracker,"stone",250)==32);
        tracker.sample(Map.of("stone",60,"diamond",1),500,4000,true,true);check(delta(tracker,"stone",500)==28);
        tracker.sample(Map.of("stone",32,"diamond",1),750,4000,true,true);check(tracker.entries(750).stream().noneMatch(c->c.key().equals("stone")));
        tracker.sample(Map.of("stone",31),3000,4000,true,true);check(delta(tracker,"stone",3000)==-1&&delta(tracker,"diamond",3000)==-1);
        check(tracker.entries(7000).isEmpty());
        tracker.reset();tracker.sample(Map.of("stone",20),0,4000,true,true);check(tracker.entries(0).isEmpty());
        tracker.sample(Map.of("stone",10),250,4000,true,false);check(tracker.entries(250).isEmpty());
        tracker.sample(Map.of("stone",15),500,4000,true,false);check(delta(tracker,"stone",500)==5);
        tracker.reset();tracker.sample(Map.of(),0,4000,true,true);var many=new HashMap<String,Integer>();for(int i=0;i<100;i++)many.put("item"+i,1);tracker.sample(many,250,4000,true,true);check(tracker.entries(250).size()==32);
        System.out.println("INVENTORY_DELTA_OK: "+checks+" checks");
    }
    private static int delta(InventoryDeltaTracker<String> tracker,String key,long now){return tracker.entries(now).stream().filter(c->c.key().equals(key)).findFirst().orElseThrow().delta();}
    private static void check(boolean condition){if(!condition)throw new AssertionError("inventory check "+checks);checks++;}
}
