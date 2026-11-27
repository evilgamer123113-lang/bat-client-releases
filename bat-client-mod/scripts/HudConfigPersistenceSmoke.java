import com.batclient.HudConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

public class HudConfigPersistenceSmoke {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        String[] ids = {"fps", "cps", "coords", "keystrokes", "potions", "ping", "armor", "durability",
                "crosshair", "target", "togglesprint", "scoreboard", "bossbar", "blockindicator", "iteminfo", "compass", "clock", "speedometer", "resourcepacks", "combo", "reach", "inventory", "totems", "arrows"};
        HudConfig.update("{\"modules\":{}}");
        for (String id : ids) {
            check(HudConfig.updateModule(id, "{\"en\":true,\"anchor\":\"BOTTOM_RIGHT\",\"offset_x\":-30,\"set\":{\"color\":\"#123456FF\",\"shadow\":false}}", true), "initial save " + id);
            check(HudConfig.updateModule(id, "{\"x\":23.5,\"y\":41.25,\"scale\":1.8,\"set\":{\"scale\":1.8}}", false), "preview " + id);
        }
        check(HudConfig.updateModule("zoom", "{\"en\":true,\"set\":{\"key\":\"key.keyboard.v\",\"level\":8}}", true), "zoom save");
        check(HudConfig.updateModule("totems", "{\"set\":{\"warningCount\":2,\"offhand\":false,\"hideZero\":true}}", true), "totem settings save");
        check(HudConfig.updateModule("arrows", "{\"set\":{\"warningCount\":32,\"specialArrows\":false,\"scope\":\"Hotbar\"}}", true), "arrow settings save");
        check(HudConfig.updateModule("blockindicator", "{\"set\":{\"range\":24,\"showBreaking\":false,\"showId\":false,\"border\":true,\"borderThickness\":3}}", true), "block indicator options save");
        HudConfig.flush();
        var memory = HudConfig.class.getDeclaredField("memory");
        memory.setAccessible(true); memory.set(null, null);
        var totems=HudConfig.module(HudConfig.snapshot(),"totems");
        var arrows=HudConfig.module(HudConfig.snapshot(),"arrows");
        check(HudConfig.setNumber(totems,"warningCount",0)==2&&!HudConfig.bool(totems,"offhand",true)&&HudConfig.bool(totems,"hideZero",false),"totem settings reload");
        check(HudConfig.setNumber(arrows,"warningCount",0)==32&&!HudConfig.bool(arrows,"specialArrows",true)&&HudConfig.str(arrows,"scope","").equals("Hotbar"),"arrow settings reload");
        for (String id : ids) {
            JsonObject module = HudConfig.module(HudConfig.snapshot(), id);
            check(module.get("x").getAsDouble() == 23.5 && module.get("y").getAsDouble() == 41.25, "position reload " + id);
            check(module.get("scale").getAsDouble() == 1.8, "size reload " + id);
            check(!module.has("anchor") && !module.has("offset_x"), "stale anchor removed " + id);
            check(HudConfig.str(module, "color", "").equals("#123456FF") && !HudConfig.bool(module, "shadow", true), "settings reload " + id);
        }
        check(HudConfig.setNumber(HudConfig.module(HudConfig.snapshot(), "zoom"), "level", 0) == 8, "unrelated module preserved");
        Path file = Path.of(System.getProperty("bat.test.config"), "batclient-hud.json");
        JsonObject block = HudConfig.module(HudConfig.snapshot(), "blockindicator");
        check(HudConfig.setNumber(block, "range", 0) == 24 && !HudConfig.bool(block, "showBreaking", true) && !HudConfig.bool(block, "showId", true), "block indicator options reload");
        check(HudConfig.bool(block, "border", false) && HudConfig.setNumber(block, "borderThickness", 0) == 3, "block indicator border reload");
        check(JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("modules").size() == ids.length + 1, "disk JSON readable");
        System.out.println("HUD_CONFIG_DISK_OK: all " + ids.length + " positions, sizes and settings reload; interrupted previews flush; unrelated modules preserved.");
    }
}
