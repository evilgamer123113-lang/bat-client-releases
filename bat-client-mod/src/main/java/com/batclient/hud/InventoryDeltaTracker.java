package com.batclient.hud;
import java.util.*;

/** Net inventory changes, with a silent first snapshot and bounded merged history. */
public final class InventoryDeltaTracker<K> {
    public record Change<K>(K key,int delta,long updated,long expires) { }
    private Map<K,Integer> previous;
    private final LinkedHashMap<K,Change<K>> changes=new LinkedHashMap<>();
    public void reset(){previous=null;changes.clear();}
    public void sample(Map<K,Integer> snapshot,long now,long duration,boolean gains,boolean losses){
        prune(now);
        if(previous!=null){
            Set<K> keys=new HashSet<>(previous.keySet());keys.addAll(snapshot.keySet());
            for(K key:keys){
                int delta=snapshot.getOrDefault(key,0)-previous.getOrDefault(key,0);
                if(delta==0||delta>0&&!gains||delta<0&&!losses)continue;
                var old=changes.remove(key);
                if(old!=null&&now-old.updated()<1500)delta+=old.delta();
                if(delta!=0)changes.put(key,new Change<>(key,delta,now,now+duration));
                while(changes.size()>32)changes.remove(changes.keySet().iterator().next());
            }
        }
        previous=new HashMap<>(snapshot);
    }
    public List<Change<K>> entries(long now){prune(now);List<Change<K>> out=new ArrayList<>(changes.values());Collections.reverse(out);return out;}
    private void prune(long now){changes.values().removeIf(change->change.expires()<=now);}
}
