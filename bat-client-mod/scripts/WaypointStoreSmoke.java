import com.batclient.WaypointStore;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public final class WaypointStoreSmoke {
    static int checks;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static JsonObject data(String name,int x,String dimension){var j=new JsonObject();j.addProperty("name",name);j.addProperty("x",x);j.addProperty("y",64);j.addProperty("z",-120);j.addProperty("dimension",dimension);j.addProperty("group","Bases");j.addProperty("color","#75CFFF");return j;}
    static void reject(Throwing work,String message)throws Exception{try{work.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){checks++;}}
    interface Throwing{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory(Path.of("build"),"waypoint-store-test-");Path file=root.resolve("waypoints.json");var store=new WaypointStore(file);
        check(store.list("A").isEmpty()&&!store.readOnly(),"empty first run");
        var home=store.save("A",data("<Home & Mine>",10,"minecraft:overworld"),"minecraft:overworld");
        var nether=store.save("A",data("Portal",20,"minecraft:the_nether"),"minecraft:overworld");store.save("B",data("Other server",30,"minecraft:overworld"),"minecraft:overworld");
        check(store.list("A").size()==2&&store.list("B").size()==1,"worlds isolated");
        check(store.list("A").stream().map(WaypointStore.Entry::dimension).distinct().count()==2,"dimensions persisted independently");
        var update=home.json();update.addProperty("name","Updated home");update.addProperty("x",42);var edited=store.save("A",update,"minecraft:overworld");check(edited.id().equals(home.id())&&edited.created()==home.created()&&store.list("A").size()==2,"editing retains identity/date");
        store.flag("A",home.id(),"favorite",true);store.flag("A",home.id(),"visible",false);store.flag("A",home.id(),"tracked",true);store.flag("A",nether.id(),"tracked",true);
        check(store.list("A").stream().filter(WaypointStore.Entry::tracked).count()==1&&store.list("A").stream().filter(WaypointStore.Entry::tracked).findFirst().orElseThrow().id().equals(nether.id()),"single tracked target per world");
        check(store.list("A").stream().filter(e->e.id().equals(home.id())).anyMatch(e->e.favorite()&&!e.visible()),"flag updates preserve unrelated fields");
        store.group("A","Bases",true);check(store.list("A").stream().allMatch(WaypointStore.Entry::visible),"group visibility batch");
        check(store.list("B").getFirst().visible(),"group operation does not affect other world");
        reject(()->store.save("B",home.json(),"minecraft:overworld"),"cannot edit another world waypoint");
        reject(()->store.remove("B",home.id()),"cannot delete another world waypoint");
        byte[] before=Files.readAllBytes(file);int count=store.list("A").size();
        var bad=data("Bad",0,"minecraft:overworld");bad.addProperty("x",1.5);reject(()->store.save("A",bad,"minecraft:overworld"),"fractional coordinate rejected");
        bad.addProperty("x",30000000);reject(()->store.save("A",bad,"minecraft:overworld"),"outside world border rejected");
        bad.addProperty("x",0);bad.addProperty("name","\u00a7cHidden");reject(()->store.save("A",bad,"minecraft:overworld"),"format control rejected");
        bad.addProperty("name","Bad");bad.addProperty("dimension","invalid dimension");reject(()->store.save("A",bad,"minecraft:overworld"),"invalid dimension rejected");
        bad.addProperty("dimension","minecraft:overworld");bad.addProperty("color","url(evil)");reject(()->store.save("A",bad,"minecraft:overworld"),"invalid color rejected");
        check(count==store.list("A").size()&&Arrays.equals(before,Files.readAllBytes(file)),"rejected transactions preserve memory and disk");
        check(before.length>3&&!(before[0]==(byte)0xEF&&before[1]==(byte)0xBB&&before[2]==(byte)0xBF),"UTF-8 without BOM");
        var reloaded=new WaypointStore(file);check(reloaded.list("A").equals(store.list("A"))&&reloaded.list("B").equals(store.list("B")),"all preferences survive reload");
        check(Files.exists(file.resolveSibling("waypoints.json.bak"))&&!Files.exists(file.resolveSibling("waypoints.json.tmp")),"backup maintained and no incomplete temp");
        Files.writeString(file,"corrupted");var recovered=new WaypointStore(file);check(!recovered.readOnly()&&!recovered.list("A").isEmpty()&&recovered.notice().contains("Recovered"),"valid backup recovers damaged primary");
        Files.writeString(file.resolveSibling("waypoints.json.bak"),"also corrupted");var protectedStore=new WaypointStore(file);check(protectedStore.readOnly(),"unreadable files become read only");reject(()->protectedStore.save("A",data("New",1,"minecraft:overworld"),"minecraft:overworld"),"corrupt data never silently overwritten");check(Files.readString(file).equals("corrupted"),"damaged original preserved");
        Path blocker=root.resolve("file-not-directory");Files.writeString(blocker,"block");var failing=new WaypointStore(blocker.resolve("points.json"));try{failing.save("A",data("No write",0,"minecraft:overworld"),"minecraft:overworld");throw new AssertionError("write should fail");}catch(java.io.IOException expected){check(failing.list("A").isEmpty(),"failed write does not change snapshot");}
        var rootJson=new JsonObject();rootJson.addProperty("version",1);var list=new JsonArray();for(int i=0;i<256;i++){var item=home.json();item.addProperty("id",UUID.randomUUID().toString());list.add(item);}rootJson.add("waypoints",list);Path full=root.resolve("full.json");Files.writeString(full,rootJson.toString());var limited=new WaypointStore(full);reject(()->limited.save("A",data("Excess",0,"minecraft:overworld"),"minecraft:overworld"),"per-world cap enforced");check(limited.list("A").size()==256,"limit keeps existing locations");
        Path featureFile=root.resolve("features.json");var features=new WaypointStore(featureFile);var custom=data("Rainbow",10,"minecraft:overworld");custom.addProperty("chroma",true);custom.addProperty("beam",false);custom.addProperty("opacity",50);custom.addProperty("expiresAt",System.currentTimeMillis()+60000);
        var rainbow=features.save("A",custom,"minecraft:overworld");var again=new WaypointStore(featureFile).list("A").getFirst();check(again.chroma()&&!again.beam()&&again.opacity()==50&&again.expiresAt()==rainbow.expiresAt(),"chroma beam opacity expiry survive restart");
        custom.remove("id");custom.addProperty("name","Expired");custom.addProperty("expiresAt",1);features.save("A",custom,"minecraft:overworld");check(features.list("A").size()==1,"expired temporary marker excluded from lists");
        custom.addProperty("expiresAt",0);custom.addProperty("opacity",101);reject(()->features.save("A",custom,"minecraft:overworld"),"opacity bounds enforced");
        var server=new com.batclient.ServerWaypointStore();String worldId=UUID.randomUUID().toString(),remote=UUID.randomUUID().toString();var msg=new JsonObject();msg.addProperty("version",1);msg.addProperty("op","world");msg.addProperty("worldId",worldId);server.accept(msg,"A","minecraft:overworld");msg.addProperty("op","upsert");msg.addProperty("id",remote);msg.add("waypoint",data("Spawn",100,"minecraft:overworld"));msg.addProperty("durationSeconds",60);server.accept(msg,"A","minecraft:overworld");check(server.list().size()==1&&server.list().getFirst().id().equals("server:"+remote),"server namespace distinct from personal IDs");
        server.visible("server:"+remote,false);server.track("server:"+remote,true);check(!server.list().getFirst().visible()&&server.list().getFirst().tracked(),"server markers can be hidden and tracked locally");
        msg.getAsJsonObject("waypoint").addProperty("name","Updated spawn");server.accept(msg,"A","minecraft:overworld");check(!server.list().getFirst().visible()&&server.list().getFirst().name().equals("Updated spawn"),"remote update preserves local hidden state");
        msg.addProperty("durationSeconds",-1);reject(()->server.accept(msg,"A","minecraft:overworld"),"negative remote TTL rejected");msg.addProperty("durationSeconds",1.5);reject(()->server.accept(msg,"A","minecraft:overworld"),"fractional remote TTL rejected");msg.addProperty("durationSeconds",0);
        msg.getAsJsonObject("waypoint").addProperty("dimension","minecraft:the_nether");reject(()->server.accept(msg,"A","minecraft:overworld"),"server cannot show marker in wrong dimension");msg.getAsJsonObject("waypoint").addProperty("dimension","minecraft:overworld");msg.addProperty("worldId",UUID.randomUUID().toString());reject(()->server.accept(msg,"A","minecraft:overworld"),"old or unselected server world rejected");
        msg.addProperty("op","world");server.accept(msg,"A","minecraft:overworld");check(server.list().isEmpty(),"changing server world clears prior markers");check(features.list("A").size()==1,"server operations leave personal storage intact");
        msg.addProperty("op","upsert");msg.addProperty("durationSeconds",0);for(int i=0;i<64;i++){msg.addProperty("id",UUID.randomUUID().toString());server.accept(msg,"A","minecraft:overworld");}msg.addProperty("id",UUID.randomUUID().toString());reject(()->server.accept(msg,"A","minecraft:overworld"),"server marker cap enforced");check(server.list().size()==64,"cap preserves existing session markers");server.clear();check(server.list().isEmpty(),"disconnect cleanup clears server markers");
        System.out.println("WAYPOINT_STORE_OK: "+checks+" checks");
    }
}
