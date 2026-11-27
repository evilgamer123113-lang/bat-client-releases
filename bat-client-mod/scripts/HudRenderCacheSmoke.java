import com.batclient.HudConfig;
import com.batclient.hud.HudLayoutEngine;
import com.batclient.hud.layout.HudComponentState;
import java.util.List;

public final class HudRenderCacheSmoke {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static HudComponentState fps(List<HudComponentState> states){return states.stream().filter(s->s.id.equals("fps")).findFirst().orElseThrow();}
    public static void main(String[] args){
        HudConfig.update("{\"modules\":{\"fps\":{\"en\":true,\"x\":10,\"y\":20,\"scale\":1}}}");
        var first=HudLayoutEngine.loadRenderStates(1000,500);
        check(HudLayoutEngine.loadRenderStates(1000,500)==first,"unchanged frames reuse layout");
        check(fps(first).offsetX==100&&fps(first).offsetY==100,"initial positions");
        long revision=HudConfig.revision();
        HudConfig.updateModule("fps","{\"x\":30,\"scale\":2}",false);
        check(HudConfig.revision()>revision,"preview updates revision");
        var preview=HudLayoutEngine.loadRenderStates(1000,500);
        check(preview!=first&&fps(preview).offsetX==300&&fps(preview).scale==2,"drag/scale preview invalidates cache");
        var resized=HudLayoutEngine.loadRenderStates(500,500);
        check(resized!=preview&&fps(resized).offsetX==150,"window resize invalidates cache");
        var editor=HudLayoutEngine.loadStates(500,500);fps(editor).offsetX=1;
        check(fps(HudLayoutEngine.loadRenderStates(500,500)).offsetX==150,"editor has isolated mutable layout");
        HudConfig.preview("{\"modules\":{\"fps\":{\"en\":false,\"x\":40}}}");
        check(!fps(HudLayoutEngine.loadRenderStates(500,500)).enabled,"profile/full preview invalidates enabled state");
        HudConfig.updateModule("fps","{\"en\":true,\"x\":50}",true);
        check(fps(HudLayoutEngine.loadRenderStates(500,500)).offsetX==250,"persisted updates invalidate cache");
        System.out.println("HUD_RENDER_CACHE_OK: "+checks+" checks");
    }
}
