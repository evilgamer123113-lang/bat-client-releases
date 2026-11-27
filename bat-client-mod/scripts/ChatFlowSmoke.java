import com.batclient.hud.ChatFlow;
public class ChatFlowSmoke {
    public static void main(String[] args) {
        check(ChatFlow.repeats("hello","hello","a","a",40,20,10),"matching sender/text");
        check(!ChatFlow.repeats("hello","hello","b","a",40,20,10),"different sender");
        check(!ChatFlow.repeats("hello","hi","a","a",40,20,10),"different text");
        check(!ChatFlow.repeats("hello","hello",null,null,40,20,10),"unknown sender never grouped");
        check(!ChatFlow.repeats("hello","hello","a","a",221,20,10),"repeat window expires");
        check(!ChatFlow.repeats("hello","hello","a","a",19,20,10),"tick rollback resets");
        check(ChatFlow.progress(0,200)==0&&ChatFlow.progress(200,200)==1,"animation boundaries");
        check(ChatFlow.progress(100,200)>.5&&ChatFlow.progress(100,200)<1,"eased entrance");
        check(ChatFlow.progress(-1,200)==0&&ChatFlow.progress(999,Float.NaN)==1,"bounded animation");
        System.out.println("CHAT_FLOW_OK: 9 checks");
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
