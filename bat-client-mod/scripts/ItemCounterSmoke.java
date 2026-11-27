import com.batclient.hud.ItemCounter;
public class ItemCounterSmoke {
    public static void main(String[] args) {
        int[] kinds=new int[37],counts=new int[37];
        kinds[0]=1;counts[0]=1;kinds[9]=1;counts[9]=2;kinds[36]=1;counts[36]=1;
        kinds[1]=2;counts[1]=64;kinds[2]=3;counts[2]=12;
        check(ItemCounter.count(37,i->kinds[i]==1,i->counts[i])==4,"inventory and offhand once");
        check(ItemCounter.count(36,i->kinds[i]==1,i->counts[i])==3,"offhand excluded");
        check(ItemCounter.count(9,i->kinds[i]==1,i->counts[i])==1,"hotbar excludes main inventory");
        check(ItemCounter.count(36,i->kinds[i]==2,i->counts[i])==64,"regular arrows only");
        check(ItemCounter.count(36,i->kinds[i]==2||kinds[i]==3,i->counts[i])==76,"special arrow aggregation");
        check(ItemCounter.count(0,i->true,i->1)==0,"empty inventory");
        check(ItemCounter.warning(0,true,1,1)&&ItemCounter.warning(1,true,1,1),"warning includes zero and boundary");
        check(!ItemCounter.warning(2,true,1,1)&&!ItemCounter.warning(0,false,1,1),"healthy and disabled warnings");
        check(ItemCounter.warning(1,true,Float.NaN,1),"invalid threshold fallback");
        System.out.println("ITEM_COUNTER_OK: 9 checks");
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
