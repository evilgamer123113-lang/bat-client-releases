import com.batclient.performance.NearestReadyBatch;
import java.util.*;

public final class NearestReadyBatchSmoke {
    private static int checks;
    private static final class Item {
        final int id;final long distance;
        Item(int id,long distance){this.id=id;this.distance=distance;}
    }
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        Random random=new Random(42);
        for(int limit=1;limit<=16;limit++)for(int trial=0;trial<100;trial++){
            List<Item> all=new ArrayList<Item>();NearestReadyBatch<Item> batch=new NearestReadyBatch<Item>(limit);
            for(int i=0;i<trial*7;i++){
                Item value=new Item(i,random.nextInt(100));all.add(value);batch.offer(value,value.distance);
                batch.offer(null,0); // unready lookups must never displace a ready chunk
            }
            Collections.sort(all,Comparator.comparingLong(value->value.distance));
            List<Item> selected=batch.result();
            check(selected.size()==Math.min(limit,all.size()),"bounded batch size");
            for(int i=0;i<selected.size();i++)check(selected.get(i)==all.get(i),"nearest-ready order and stable ties match full-sort reference");
        }
        Map<Integer,Item> queue=new LinkedHashMap<Integer,Item>();
        for(int i=0;i<1000;i++)queue.put(i,new Item(i,1000-i));
        Set<Integer> delivered=new HashSet<Integer>();int rounds=0;
        while(!queue.isEmpty()){
            NearestReadyBatch<Item> next=new NearestReadyBatch<Item>(16);
            for(Item value:queue.values())if(rounds>0||value.id%2==0)next.offer(value,value.distance);
            for(Item value:next.result()){
                check(queue.remove(value.id)==value&&delivered.add(value.id),"each selected chunk is removed and delivered exactly once");
            }
            if(rounds++==0)check(queue.containsKey(999),"unready nearest chunk remains queued");
            check(rounds<=64,"bounded batches keep draining the queue");
        }
        check(delivered.size()==1000,"no chunks lost while draining backlog");
        try{new NearestReadyBatch<Item>(0);throw new AssertionError("zero bound");}catch(IllegalArgumentException expected){}
        try{new NearestReadyBatch<Item>(17);throw new AssertionError("oversized bound");}catch(IllegalArgumentException expected){}
        System.out.println("NEAREST_READY_BATCH_OK: "+checks+" checks; stable nearest-first, bounded memory, readiness and 1000-chunk drain");
    }
}
