import com.batclient.HudConfig;
import com.google.gson.JsonObject;

public class ProfilePersistenceSmoke {
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    static void action(String action,String name) { String error=HudConfig.profileAction(action,name);check(error.isEmpty(),action+": "+error); }
    public static void main(String[] args) throws Exception {
        HudConfig.update("{\"modules\":{\"iteminfo\":{\"en\":true,\"x\":12,\"y\":22,\"scale\":1,\"set\":{\"shadow\":true}}}}");
        action("list","");
        check(HudConfig.module(HudConfig.snapshot(),"iteminfo").get("x").getAsInt()==12,"first profile preserves old layout");
        action("create","PvP");
        HudConfig.updateModule("iteminfo","{\"x\":41,\"y\":52,\"scale\":1.7,\"set\":{\"shadow\":false,\"enchantments\":false}}",true);
        HudConfig.updateModule("clock","{\"en\":true,\"set\":{\"source\":\"Minecraft\",\"format\":\"12-hour\"}}",true);
        check(HudConfig.updateBinding("iteminfo","key.mouse.4"),"mouse key saves");
        HudConfig.updateModule("zoom","{\"en\":true,\"set\":{\"key\":\"key.keyboard.v\",\"level\":7}}",true);
        action("create","Survival");
        HudConfig.updateModule("iteminfo","{\"en\":false,\"x\":88}",true);
        check(HudConfig.updateBinding("iteminfo","key.keyboard.b"),"alternate profile key saves");
        action("load","Default");
        check(HudConfig.module(HudConfig.snapshot(),"iteminfo").get("x").getAsInt()==12,"default layout restored");
        check(!HudConfig.snapshot().getAsJsonObject("bindings").has("iteminfo"),"bindings isolated between profiles");
        action("load","PvP");
        JsonObject item=HudConfig.module(HudConfig.snapshot(),"iteminfo");
        check(item.get("x").getAsInt()==41 && item.get("y").getAsInt()==52,"position auto saved");
        check(Math.abs(HudConfig.scale(item)-1.7)<.001 && !HudConfig.bool(item,"shadow",true),"size/settings auto saved");
        check(HudConfig.snapshot().getAsJsonObject("bindings").get("iteminfo").getAsString().equals("key.mouse.4"),"mouse binding restored");
        check(HudConfig.str(HudConfig.module(HudConfig.snapshot(),"zoom"),"key","").equals("key.keyboard.v"),"zoom key restored");
        check(HudConfig.str(HudConfig.module(HudConfig.snapshot(),"clock"),"source","").equals("Minecraft"),"new module options restored");
        check(!HudConfig.profileAction("create","Survival").isEmpty(),"duplicate names rejected");
        check(!HudConfig.profileAction("delete","PvP").isEmpty(),"cannot delete active profile");
        action("rename","PvP <test>");
        action("delete","Survival");
        HudConfig.flush();
        var field=HudConfig.class.getDeclaredField("memory");field.setAccessible(true);field.set(null,null);
        check(HudConfig.snapshot().get("activeProfile").getAsString().equals("PvP <test>"),"active profile survives restart");
        check(HudConfig.snapshot().getAsJsonObject("profiles").size()==2,"profile operations survive restart");
        check(HudConfig.enabled(HudConfig.module(HudConfig.snapshot(),"iteminfo")),"module enabled state survives restart");
        check(HudConfig.snapshot().getAsJsonObject("profiles").getAsJsonObject("Default").getAsJsonObject("modules").getAsJsonObject("iteminfo").get("x").getAsInt()==12,"profiles do not alias");
        System.out.println("PROFILES_DISK_ROUNDTRIP_OK: migration, auto-save, layout/settings/key isolation, rename/delete and restart");
    }
}
