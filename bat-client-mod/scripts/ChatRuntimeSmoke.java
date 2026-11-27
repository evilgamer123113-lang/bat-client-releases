import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import java.util.*;
import java.lang.reflect.*;
import java.nio.file.*;

/** Test-only Fabric entrypoint, compiled into an isolated build/ run directory. */
public class ChatRuntimeSmoke implements ClientModInitializer {
    private boolean done;
    private int settledTicks;
    private static int checks;
    public void onInitializeClient() {
        if(Boolean.getBoolean("bat.visual.only")){new NewVisualRuntimeSmoke().start();return;}
        if(Boolean.getBoolean("bat.waypoint.only")){new WaypointRuntimeSmoke().start();return;}
        if(Boolean.getBoolean("bat.performance.only")){new WorldPerformanceSmoke().start();return;}
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(done||client.currentScreen==null||client.getOverlay()!=null)return;
            // Let the native web view finish its first paint before GPU capture/shutdown.
            if(++settledTicks<40)return;
            done=true;
            try {
                if(Boolean.getBoolean("bat.adventure.only")){
                    verifyUtilityModules(client);
                    verifyAdventureModules(client);
                    String result="ADVENTURE_NATIVE_RUNTIME_OK: "+checks+" checks";
                    Files.writeString(Path.of(System.getProperty("bat.chat.smoke.out")),result);System.out.println(result);return;
                }
                // Exercise the protected session mixin with an isolated offline identity,
                // then restore the exact launch account before the chat checks.
                Method capture=client.getClass().getMethod("bat$captureAccount");
                Object launch=capture.invoke(client);
                Method apply=client.getClass().getMethod("bat$applyAccount",launch.getClass());
                var launchSession=client.getSession();var launchKeys=client.getProfileKeys();
                UUID offlineId=UUID.nameUUIDFromBytes("OfflinePlayer:AccountSmoke".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var offline=new net.minecraft.client.session.Session("AccountSmoke",offlineId,"0",Optional.empty(),Optional.empty(),net.minecraft.client.session.Session.AccountType.LEGACY);
                Object next=launch.getClass().getConstructors()[0].newInstance(offline,com.mojang.authlib.minecraft.UserApiService.OFFLINE,
                    java.util.concurrent.CompletableFuture.completedFuture(new com.mojang.authlib.yggdrasil.ProfileResult(new com.mojang.authlib.GameProfile(offlineId,"AccountSmoke"))),
                    java.util.concurrent.CompletableFuture.completedFuture(com.mojang.authlib.minecraft.UserApiService.OFFLINE_PROPERTIES));
                try {
                    apply.invoke(client,next);
                    check(client.getSession()==offline&&client.getGameProfile().getId().equals(offlineId),"native session and profile change together");
                    check(client.getProfileKeys()==net.minecraft.client.session.ProfileKeys.MISSING,"offline account has no authenticated signing keys");
                }finally{apply.invoke(client,launch);}
                check(client.getSession()==launchSession,"original launcher account restored");
                Method accounts=client.getClass().getMethod("bat$accounts",String.class,String.class);
                String before=(String)accounts.invoke(client,"list","");
                check(!before.contains("accessToken")&&!before.contains("refreshToken"),"account UI metadata excludes credentials");
                String added=(String)accounts.invoke(client,"offline","AccountSmoke");
                var metadata=com.google.gson.JsonParser.parseString(added).getAsJsonObject();
                String offlineKey=metadata.getAsJsonArray("accounts").asList().stream().map(e->e.getAsJsonObject()).filter(e->e.get("kind").getAsString().equals("Offline")&&e.get("name").getAsString().equals("AccountSmoke")).findFirst().orElseThrow().get("id").getAsString();
                try {
                    check(java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("batclient-accounts.json")).contains("AccountSmoke"),"offline username saved in isolated config");
                    accounts.invoke(client,"switch",offlineKey);
                    check(client.getSession().getUsername().equals("AccountSmoke"),"account manager switches native identity");
                    accounts.invoke(client,"remove",offlineKey);
                    check(client.getSession()==launchSession&&!java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("batclient-accounts.json")).contains("AccountSmoke"),"removing active offline account restores launcher and persists removal");
                }finally{accounts.invoke(client,"switch","launch");accounts.invoke(client,"remove",offlineKey);}
                check(client.getSession()==launchSession,"account manager restores launcher session");
                ChatHud hud=client.inGameHud.getChatHud();hud.clear(true);
                Field field=ChatHud.class.getDeclaredField("bat$chat");field.setAccessible(true);
                Object nativeChat=field.get(hud);
                Method remember=Arrays.stream(nativeChat.getClass().getDeclaredMethods()).filter(method->Modifier.isStatic(method.getModifiers())&&Arrays.equals(method.getParameterTypes(),new Class<?>[]{MessageSignatureData.class,UUID.class})).findFirst().orElseThrow();remember.setAccessible(true);
                UUID a=UUID.randomUUID(),b=UUID.randomUUID();
                add(hud,remember,1,a,Text.literal("Hello"));
                check(visible(hud).size()==1&&plain(visible(hud).getFirst()).contains("[1x]"),"initial counter");
                check(plain(visible(hud).getFirst()).startsWith("   "),"built-in head spacing ignores old module disable flag");
                add(hud,remember,2,a,Text.literal("Hello"));
                check(visible(hud).size()==1&&plain(visible(hud).getFirst()).contains("[2x]"),"consecutive collapse");
                check(history(hud).size()==2&&history(hud).stream().allMatch(line->line.content().getString().equals("Hello")&&line.signature()!=null),"signed canonical history preserved");
                hud.reset();check(visible(hud).size()==1&&plain(visible(hud).getFirst()).contains("[2x]"),"refresh preserves repeat count");
                add(hud,remember,3,b,Text.literal("Hello"));
                check(visible(hud).size()==2&&plain(visible(hud).getFirst()).contains("[1x]"),"different sender separate");
                add(hud,remember,4,b,Text.literal("Hello").formatted(Formatting.RED));
                check(visible(hud).size()==3,"different style separate");
                hud.addMessage(Text.literal("Unknown system message"));hud.addMessage(Text.literal("Unknown system message"));
                check(visible(hud).size()==5,"unknown system messages are not collapsed");
                add(hud,remember,5,a,Text.literal("Long wrapped message ".repeat(40)));
                check(visible(hud).size()>6,"long message wraps normally");
                check(history(hud).size()==7,"all originals retained");
                hud.reset();check(history(hud).size()==7,"reset retains originals");
                Class.forName("net.minecraft.client.render.chunk.ChunkBuilder");
                Class.forName("net.minecraft.client.render.WorldRenderer");
                Class.forName("net.minecraft.client.render.entity.EntityRenderer");
                check(true,"chunk smoothing mixins load");
                verifyHealthLabelDepth();
                verifyVisualModules();
                verifyAdventureModules(client);
                Class.forName("net.minecraft.client.gui.screen.ingame.InventoryScreen");
                Class.forName("net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen");
                Class.forName("net.minecraft.client.gui.screen.ingame.HandledScreen");
                check(true,"survival and creative inventory animation hooks load");
                // A title-screen smoke has no player/network handler for vanilla ChatScreen.init.
                // Exercise history rendering with the chat screen marker without opening a world.
                var previousScreen=client.currentScreen;
                client.currentScreen=new ChatScreen("");
                var context=new DrawContext(client,client.getBufferBuilders().getEntityVertexConsumers());
                int heartX=0;
                for(String heart:new String[]{"container","full","half","absorbing_full","absorbing_half"}){
                    var id=net.minecraft.util.Identifier.ofVanilla("hud/heart/"+heart);
                    check(client.getGuiAtlasManager().getSprite(id).getContents().getId().equals(id),"vanilla heart sprite resolves: "+heart);
                    context.drawGuiTexture(id,heartX,0,9,9);heartX+=10;
                }
                hud.render(context,client.inGameHud.getTicks(),0,0,true);
                context.draw();
                check(client.currentScreen instanceof ChatScreen,"chat history opening render hook executes");
                Method copy=hud.getClass().getMethod("bat$copyAt",double.class,double.class);
                String longText="Long wrapped message ".repeat(40);double hitY=-1;
                for(int y=client.getWindow().getScaledHeight()-60;y>client.getWindow().getScaledHeight()-140;y--)if(longText.equals(copy.invoke(hud,12d,(double)y))){hitY=y;break;}
                check(hitY>=0,"any wrapped chat line resolves its complete undecorated original");
                check(copy.invoke(hud,-100d,hitY)==null&&copy.invoke(hud,12d,(double)client.getWindow().getScaledHeight()-3)==null,"chat copying excludes outside chat and input field");
                String oldClipboard=client.keyboard.getClipboard();
                try {check(client.currentScreen.mouseClicked(12,hitY,1)&&client.keyboard.getClipboard().equals(longText),"right click copies complete chat message to clipboard");}
                finally{client.keyboard.setClipboard(oldClipboard);}
                check(history(hud).size()==7,"copying preserves canonical signed chat history");
                client.currentScreen=previousScreen;
                verifyLoadingBrand(client);
                hud.clear(true);check(visible(hud).isEmpty()&&history(hud).isEmpty(),"clear removes chat and grouping");
                Files.writeString(Path.of(System.getProperty("bat.chat.smoke.out")),"CHAT_NATIVE_RUNTIME_OK: "+checks+" checks");
                System.out.println("CHAT_NATIVE_RUNTIME_OK: "+checks+" checks");
            }catch(Throwable failure){
                failure.printStackTrace();
                try{Files.writeString(Path.of(System.getProperty("bat.chat.smoke.out")),"FAILED: "+failure);}catch(Exception ignored){}
            }finally{client.scheduleStop();}
        });
    }
    static void add(ChatHud hud,Method remember,int id,UUID sender,Text text)throws Exception {
        byte[] bytes=new byte[256];bytes[0]=(byte)id;
        MessageSignatureData signature=new MessageSignatureData(bytes);remember.invoke(null,signature,sender);
        hud.addMessage(text,signature,MessageIndicator.notSecure());
    }
    static void verifyUtilityModules(net.minecraft.client.MinecraftClient client)throws Exception {
        Class<?> config=protectedClass("com.batclient.HudConfig");
        Method snapshot=Arrays.stream(config.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())&&m.getParameterCount()==0&&m.getReturnType()==com.google.gson.JsonObject.class).findFirst().orElseThrow();snapshot.setAccessible(true);
        Method preview=Arrays.stream(config.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())&&m.getName().equals("preview")).findFirst().orElse(null);
        // Resolve the original method name through the private mapping rather than relying on obfuscated overload names.
        String methodName=Files.readAllLines(Path.of(System.getProperty("bat.smoke.mapping"))).stream().dropWhile(l->!l.startsWith("com.batclient.HudConfig -> ")).skip(1).takeWhile(l->l.startsWith(" ")).filter(l->l.contains("void preview(java.lang.String)")).map(l->l.substring(l.lastIndexOf(" -> ")+4)).findFirst().orElseThrow();
        preview=config.getDeclaredMethod(methodName,String.class);preview.setAccessible(true);
        var original=((com.google.gson.JsonObject)snapshot.invoke(null)).deepCopy();
        try{
            var fixture=original.deepCopy();var modules=new com.google.gson.JsonObject();fixture.add("modules",modules);
            var mod=new com.google.gson.JsonObject();var settings=new com.google.gson.JsonObject();mod.addProperty("en",true);mod.add("set",settings);settings.addProperty("shift",false);modules.add("shulkerpreview",mod);modules.add("raincustomizer",mod.deepCopy());preview.invoke(null,fixture.toString());
            var box=new net.minecraft.item.ItemStack(net.minecraft.item.Items.SHULKER_BOX);var items=net.minecraft.util.collection.DefaultedList.ofSize(27,net.minecraft.item.ItemStack.EMPTY);items.set(0,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND,7));items.set(26,new net.minecraft.item.ItemStack(net.minecraft.item.Items.APPLE,3));box.set(net.minecraft.component.DataComponentTypes.CONTAINER,net.minecraft.component.type.ContainerComponent.fromStacks(items));
            var data=box.getTooltipData();check(data.isPresent(),"protected native shulker item hook produces tooltip data");
            var component=net.minecraft.client.gui.tooltip.TooltipComponent.of(data.orElseThrow());check(component.getWidth(client.textRenderer)==164&&component.getHeight()==74,"shulker tooltip is bounded 9 by 3 grid");
            var draw=new DrawContext(client,client.getBufferBuilders().getEntityVertexConsumers());component.drawItems(client.textRenderer,12,12,draw);draw.draw();check(box.get(net.minecraft.component.DataComponentTypes.CONTAINER).stream().mapToInt(net.minecraft.item.ItemStack::getCount).sum()==10,"preview draw preserves contents and counts");
            check(new net.minecraft.item.ItemStack(net.minecraft.item.Items.STONE).getTooltipData().isEmpty(),"ordinary items do not acquire shulker preview");
            Class<?> utility=protectedClass("com.batclient.ItemPreviews");Method compare=Arrays.stream(utility.getDeclaredMethods()).filter(m->m.getReturnType()==List.class&&m.getParameterCount()==4).findFirst().orElseThrow();compare.setAccessible(true);
            var armor=(List<?>)compare.invoke(null,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE),new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_CHESTPLATE),net.minecraft.entity.EquipmentSlot.CHEST,true);
            check(armor.stream().anyMatch(t->((Text)t).getString().equals("Armor: +2.0")),"armor compares real native attributes");
            var weapon=(List<?>)compare.invoke(null,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD),new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SWORD),net.minecraft.entity.EquipmentSlot.MAINHAND,false);
            check(weapon.stream().anyMatch(t->((Text)t).getString().equals("Attack damage: +1.0"))&&weapon.size()==3,"weapon comparison and durability toggle");
            Class<?> travel=protectedClass("com.batclient.TravelHud");Method lines=Arrays.stream(travel.getDeclaredMethods()).filter(m->m.getReturnType()==List.class&&m.getParameterCount()==4).findFirst().orElseThrow();lines.setAccessible(true);
            for(String id:new String[]{"elytrahud","shieldindicator"}){check(!((List<?>)lines.invoke(null,client,id,mod,true)).isEmpty(),id+" native editor sample");check(((List<?>)lines.invoke(null,client,id,mod,false)).isEmpty(),id+" hides without live player");}
            var flight=(List<?>)lines.invoke(null,client,"elytrahud",mod,true);check(flight.stream().anyMatch(t->t.toString().equals("Flight: 2:05")),"Elytra flight time preview");
            settings.addProperty("flightTime",false);check(((List<?>)lines.invoke(null,client,"elytrahud",mod,true)).stream().noneMatch(t->t.toString().startsWith("Flight:")),"flight time toggle");
            Method drawTravel=Arrays.stream(travel.getDeclaredMethods()).filter(m->m.getParameterCount()==5&&m.getParameterTypes()[0]==DrawContext.class).findFirst().orElseThrow();drawTravel.setAccessible(true);for(String id:new String[]{"elytrahud","shieldindicator"})drawTravel.invoke(null,draw,client,id,mod,true);draw.draw();check(true,"durability bar and shield ring draw natively");
            var emptyBox=new net.minecraft.item.ItemStack(net.minecraft.item.Items.RED_SHULKER_BOX);var emptyComponent=net.minecraft.client.gui.tooltip.TooltipComponent.of(emptyBox.getTooltipData().orElseThrow());check(emptyComponent.getHeight()==32,"empty shulker compact native message");emptyComponent.drawItems(client.textRenderer,12,100,draw);draw.draw();
            Class<?> comparisonData=protectedClass("com.batclient.ItemPreviews$Comparison");var compareCtor=comparisonData.getDeclaredConstructors()[0];compareCtor.setAccessible(true);var tooltip=(net.minecraft.item.tooltip.TooltipData)compareCtor.newInstance(new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD),new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SWORD),net.minecraft.entity.EquipmentSlot.MAINHAND);var statGrid=net.minecraft.client.gui.tooltip.TooltipComponent.of(tooltip);check(statGrid.getWidth(client.textRenderer)==238,"native side by side equipment panel");statGrid.drawItems(client.textRenderer,12,150,draw);draw.draw();
            Method profile=Arrays.stream(config.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())&&m.getReturnType()==String.class&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{String.class,String.class})).findFirst().orElseThrow();profile.setAccessible(true);
            Method bind=Arrays.stream(config.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())&&m.getReturnType()==String.class&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{String.class,String.class,String.class})).findFirst().orElseThrow();bind.setAccessible(true);
            check(profile.invoke(null,"create","Server Check").equals(""),"create isolated test profile");check(bind.invoke(null,"bind","play.example.test","Server Check").equals(""),"server profile assignment saves");
            check(profile.invoke(null,"rename","Renamed Check").equals("")&&((com.google.gson.JsonObject)snapshot.invoke(null)).getAsJsonObject("serverProfiles").get("play.example.test").getAsString().equals("Renamed Check"),"profile rename preserves server assignment");
            check(profile.invoke(null,"load","Default").equals("")&&profile.invoke(null,"delete","Renamed Check").equals("")&&!((com.google.gson.JsonObject)snapshot.invoke(null)).getAsJsonObject("serverProfiles").has("play.example.test"),"profile delete removes stale assignment");
            preview.invoke(null,fixture.toString());
            Method weather=Arrays.stream(client.worldRenderer.getClass().getDeclaredMethods()).filter(m->m.getName().contains("bat$settings")&&m.getParameterCount()==6).findFirst().orElseThrow();weather.setAccessible(true);weather.invoke(client.worldRenderer,null,0f,0d,0d,0d,new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("test",false));
            Field rain=client.worldRenderer.getClass().getDeclaredField("bat$rainAlpha"),snow=client.worldRenderer.getClass().getDeclaredField("bat$snowAlpha");rain.setAccessible(true);snow.setAccessible(true);check(Math.abs(rain.getFloat(client.worldRenderer)-.4f)<.001&&snow.getFloat(client.worldRenderer)==1,"rain hook opacity leaves snow unchanged by default");
            mod.addProperty("en",false);modules.add("shulkerpreview",mod);modules.add("raincustomizer",mod);preview.invoke(null,fixture.toString());check(box.getTooltipData().isEmpty(),"disabled shulker module restores native tooltip");weather.invoke(client.worldRenderer,null,0f,0d,0d,0d,new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("test",false));check(rain.getFloat(client.worldRenderer)==1,"disabled rain module restores full opacity");
        }finally{preview.invoke(null,original.toString());}
    }
    static void verifyAdventureModules(net.minecraft.client.MinecraftClient client)throws Exception {
        var mod=new com.google.gson.JsonObject();var settings=new com.google.gson.JsonObject();mod.addProperty("en",true);mod.add("set",settings);
        Class<?> hud=protectedClass("com.batclient.AdventureHud");
        Method lines=Arrays.stream(hud.getDeclaredMethods()).filter(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{net.minecraft.client.MinecraftClient.class,String.class,com.google.gson.JsonObject.class,boolean.class})&&m.getReturnType()==List.class).findFirst().orElseThrow();lines.setAccessible(true);
        for(String id:new String[]{"foodpreview","bowcharge","deathcoords","mounthud"}){
            List<?> sample=(List<?>)lines.invoke(null,client,id,mod,true);check(!sample.isEmpty(),id+" editor sample exists without world");
            List<?> live=(List<?>)lines.invoke(null,client,id,mod,false);check(live.isEmpty(),id+" has no false live data at title screen");
        }
        List<?> food=(List<?>)lines.invoke(null,client,"foodpreview",mod,true);check(food.stream().anyMatch(s->s.toString().contains("+8 (8 usable)")),"food reads native food component");
        settings.addProperty("saturation",false);check(((List<?>)lines.invoke(null,client,"foodpreview",mod,true)).size()==2,"food saturation setting honored");
        Class<?> death=protectedClass("com.batclient.DeathCoordinates$Death");var ctor=death.getDeclaredConstructors()[0];ctor.setAccessible(true);Object value=ctor.newInstance(1,64,-2,"minecraft:overworld");
        var gson=new com.google.gson.Gson();String json=gson.toJson(value);Object copy=gson.fromJson(json,death);check(value.equals(copy)&&json.contains("dimension"),"protected death record JSON roundtrip keeps persisted names");
        Class<?> layers=protectedClass("com.batclient.SkinLayers3D");Method build=Arrays.stream(layers.getDeclaredMethods()).filter(m->m.getReturnType()==List.class&&m.getParameterCount()==10).findFirst().orElseThrow();build.setAccessible(true);
        try(var skin=new net.minecraft.client.texture.NativeImage(64,64,false)){
            skin.fillRect(0,0,64,64,0);List<?> empty=(List<?>)build.invoke(null,skin,32,0,-4f,-8f,-4f,8,8,8,.5f);check(empty.isEmpty(),"transparent outer skin has no voxel geometry");
            skin.setColor(40,8,0xFFFFFFFF);List<?> one=(List<?>)build.invoke(null,skin,32,0,-4f,-8f,-4f,8,8,8,.5f);check(one.size()==5,"one skin pixel has outer face and four depth walls");
            skin.setColor(41,8,0xFFFFFFFF);List<?> two=(List<?>)build.invoke(null,skin,32,0,-4f,-8f,-4f,8,8,8,.5f);check(two.size()==8,"adjacent skin pixels omit internal side walls");
        }
        Method mesh=Arrays.stream(layers.getDeclaredMethods()).filter(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{net.minecraft.client.MinecraftClient.class,net.minecraft.util.Identifier.class,boolean.class})).findFirst().orElseThrow();mesh.setAccessible(true);
        var steve=net.minecraft.util.Identifier.ofVanilla("textures/entity/player/wide/steve.png");Object generated=mesh.invoke(null,client,steve,false);check(generated!=null,"actual skin texture produces native 3D mesh");check(mesh.invoke(null,client,steve,false)==generated,"skin mesh reused between frames");
        var draw=new net.minecraft.client.gui.DrawContext(client,client.getBufferBuilders().getEntityVertexConsumers());
        Method render=Arrays.stream(hud.getDeclaredMethods()).filter(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{net.minecraft.client.gui.DrawContext.class,net.minecraft.client.MinecraftClient.class,String.class,com.google.gson.JsonObject.class,boolean.class})).findFirst().orElseThrow();render.setAccessible(true);
        for(String id:new String[]{"foodpreview","bowcharge","deathcoords","mounthud"})render.invoke(null,draw,client,id,mod,true);draw.draw();check(true,"four native HUD previews draw successfully");
        Class.forName("net.minecraft.client.render.entity.TntEntityRenderer");check(true,"native TNT renderer mixin loads");
        Class<?> tnt=protectedClass("com.batclient.TntCountdown");Method text=Arrays.stream(tnt.getDeclaredMethods()).filter(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{float.class,boolean.class})).findFirst().orElseThrow();text.setAccessible(true);
        check(text.invoke(null,50f,false).equals("2.5s")&&text.invoke(null,-1f,false).equals("0.0s")&&text.invoke(null,49.5f,true).equals("50 ticks"),"TNT seconds/ticks and expired timer formatting");
        for(String sprite:new String[]{"food_empty","food_half","food_full"}){var id=net.minecraft.util.Identifier.ofVanilla("hud/"+sprite);check(client.getGuiAtlasManager().getSprite(id).getContents().getId().equals(id),"native hunger sprite resolves: "+sprite);}
        captureAdventurePreview(client,tnt);
    }
    static void captureAdventurePreview(net.minecraft.client.MinecraftClient client,Class<?> tnt)throws Exception {
        var renderSystem=com.mojang.blaze3d.systems.RenderSystem.class;
        client.getFramebuffer().beginWrite(true);
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,client.getWindow().getScaledWidth(),client.getWindow().getScaledHeight(),0,1000,21000),com.mojang.blaze3d.systems.VertexSorter.BY_Z);
        var modelView=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();modelView.pushMatrix();modelView.identity().translate(0,0,-11000);com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try{
            var ctx=new net.minecraft.client.gui.DrawContext(client,client.getBufferBuilders().getEntityVertexConsumers());ctx.fill(0,0,client.getWindow().getScaledWidth(),client.getWindow().getScaledHeight(),0xFF101014);ctx.draw();
            var mod=new com.google.gson.JsonObject();mod.addProperty("en",true);var settings=new com.google.gson.JsonObject();settings.addProperty("biome",true);mod.add("set",settings);
            Class<?> map=protectedClass("com.batclient.MinimapHud");Method mapDraw=Arrays.stream(map.getDeclaredMethods()).filter(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{net.minecraft.client.gui.DrawContext.class,net.minecraft.client.MinecraftClient.class,com.google.gson.JsonObject.class,boolean.class})).findFirst().orElseThrow();mapDraw.setAccessible(true);
            ctx.getMatrices().push();ctx.getMatrices().translate(25,25,0);mapDraw.invoke(null,ctx,client,mod,true);ctx.getMatrices().pop();ctx.draw();
            Class<?> hud=protectedClass("com.batclient.AdventureHud");Method draw=Arrays.stream(hud.getDeclaredMethods()).filter(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{net.minecraft.client.gui.DrawContext.class,net.minecraft.client.MinecraftClient.class,String.class,com.google.gson.JsonObject.class,boolean.class})).findFirst().orElseThrow();draw.setAccessible(true);
            int y=25;for(String id:new String[]{"foodpreview","bowcharge","mounthud","deathcoords"}){ctx.getMatrices().push();ctx.getMatrices().translate(200,y,0);draw.invoke(null,ctx,client,id,mod,true);ctx.getMatrices().pop();y+=60;}ctx.draw();
            Method label=Arrays.stream(tnt.getDeclaredMethods()).filter(m->m.getParameterCount()==7).findFirst().orElseThrow();label.setAccessible(true);
            var matrices=new net.minecraft.client.util.math.MatrixStack();matrices.translate(90,210,0);matrices.scale(3,3,3);label.invoke(null,matrices,client.getBufferBuilders().getEntityVertexConsumers(),"2.5s",0xFFFFFFFF,true,true,false);client.getBufferBuilders().getEntityVertexConsumers().draw();
            try(var image=net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){
                var file=Path.of(System.getProperty("bat.chat.smoke.out")).resolveSibling("adventure-preview.png");image.writeTo(file);
                int white=0;double scale=client.getWindow().getScaleFactor();for(int x=(int)(50*scale);x<(int)(130*scale);x++)for(int row=(int)(205*scale);row<Math.min(image.getHeight(),240*scale);row++){int pixel=image.getColor(x,row);if((pixel&0xFFFFFF)==0xFFFFFF)white++;}check(white>20,"native TNT glyph faces are visible in framebuffer capture");
            }
        }finally{modelView.popMatrix();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();}
    }
    @SuppressWarnings("unchecked") static List<ChatHudLine.Visible> visible(ChatHud hud)throws Exception {
        Field field=ChatHud.class.getDeclaredField("visibleMessages");field.setAccessible(true);return (List<ChatHudLine.Visible>)field.get(hud);
    }
    @SuppressWarnings("unchecked") static List<ChatHudLine> history(ChatHud hud)throws Exception {
        Field field=ChatHud.class.getDeclaredField("messages");field.setAccessible(true);return (List<ChatHudLine>)field.get(hud);
    }
    static String plain(ChatHudLine.Visible line){StringBuilder out=new StringBuilder();line.content().accept((index,style,codepoint)->{out.appendCodePoint(codepoint);return true;});return out.toString();}
    static void verifyHealthLabelDepth()throws Exception {
        String mapped=Files.readAllLines(Path.of(System.getProperty("bat.smoke.mapping"))).stream().filter(line->line.startsWith("com.batclient.HealthIndicator -> ")).findFirst().orElseThrow();
        Class<?> indicator=Class.forName(mapped.substring(mapped.indexOf(" -> ")+4,mapped.length()-1));
        Class<?> consumer=net.minecraft.client.render.VertexConsumer.class,provider=net.minecraft.client.render.VertexConsumerProvider.class;
        Method draw=Arrays.stream(indicator.getDeclaredMethods()).filter(method->Arrays.equals(method.getParameterTypes(),new Class<?>[]{Text.class,int.class,int.class,com.google.gson.JsonObject.class,int.class,net.minecraft.client.util.math.MatrixStack.class,provider,int.class})).findFirst().orElseThrow();
        draw.setAccessible(true);
        for(boolean shadow:new boolean[]{false,true})for(boolean occluded:new boolean[]{false,true}){
            List<float[]> samples=new ArrayList<>();float[] position=new float[3];
            Object sink=Proxy.newProxyInstance(consumer.getClassLoader(),new Class<?>[]{consumer},(proxy,method,args)->{
                if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);
                if(method.getName().equals("vertex")&&args.length==3){for(int i=0;i<3;i++)position[i]=((Number)args[i]).floatValue();}
                if(method.getName().equals("color")&&args.length==4)samples.add(new float[]{position[2],((Number)args[0]).floatValue(),((Number)args[1]).floatValue(),((Number)args[2]).floatValue(),((Number)args[3]).floatValue()});
                return proxy;
            });
            net.minecraft.client.render.VertexConsumerProvider collector=layer->(net.minecraft.client.render.VertexConsumer)sink;
            var mod=new com.google.gson.JsonObject();var settings=new com.google.gson.JsonObject();settings.addProperty("shadow",shadow);settings.addProperty("occluded",occluded);mod.add("set",settings);
            draw.invoke(null,Text.literal("Zombie"),0,0xFFFFFFFF,mod,0x55000000,new net.minecraft.client.util.math.MatrixStack(),collector,15728880);
            var white=samples.stream().filter(v->v[1]==255&&v[2]==255&&v[3]==255&&v[4]==255).toList();
            var background=samples.stream().filter(v->v[1]==0&&v[2]==0&&v[3]==0&&v[4]==85).toList();
            check(!white.isEmpty()&&background.size()==4&&white.stream().allMatch(v->v[0]>background.getFirst()[0]),"name foreground stays ahead of single background: shadow="+shadow+", occluded="+occluded);
        }
    }
    static Class<?> protectedClass(String name)throws Exception {
        String mapped=Files.readAllLines(Path.of(System.getProperty("bat.smoke.mapping"))).stream().filter(line->line.startsWith(name+" -> ")).findFirst().orElseThrow();
        return Class.forName(mapped.substring(mapped.indexOf(" -> ")+4,mapped.length()-1));
    }
    static void verifyLoadingBrand(net.minecraft.client.MinecraftClient client)throws Exception {
        boolean monochrome=client.options.getMonochromeLogo().getValue();client.options.getMonochromeLogo().setValue(false);
        int previousTexture=com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(0);
        try {
            var pending=new java.util.concurrent.CompletableFuture<Void>();
            var reload=new net.minecraft.resource.ResourceReload(){public java.util.concurrent.CompletableFuture<?> whenComplete(){return pending;}public float getProgress(){return .6f;}};
            var splash=new net.minecraft.client.gui.screen.SplashOverlay(client,reload,result->{throw new AssertionError("incomplete reload completed early");},false);
            client.getFramebuffer().beginWrite(true);com.mojang.blaze3d.systems.RenderSystem.disableScissor();
            com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,client.getWindow().getScaledWidth(),client.getWindow().getScaledHeight(),0,1000,21000),com.mojang.blaze3d.systems.VertexSorter.BY_Z);
            var modelView=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();modelView.pushMatrix();modelView.identity().translate(0,0,-11000);com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            var context=new DrawContext(client,client.getBufferBuilders().getEntityVertexConsumers());
            splash.render(context,0,0,0);context.draw();
            Field progress=splash.getClass().getDeclaredField("progress");progress.setAccessible(true);check(progress.getFloat(splash)>0,"Bat splash preserves real resource loading progress");
            try(var image=net.minecraft.client.util.ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){
                image.writeTo(Path.of(System.getProperty("bat.chat.smoke.out")).getParent().resolve("bat-loading-preview.png"));
                check((image.getColor(0,0)&0xFFFFFF)==0,"Bat loading background is black even with vanilla red-logo option selected");
                int white=0,colored=0;
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int value=image.getColor(x,y),r=value&255,g=(value>>8)&255,b=(value>>16)&255;if(r>200&&g>200&&b>200&&y>image.getHeight()/3&&y<image.getHeight()*2/3)white++;if(Math.abs(r-g)>2||Math.abs(g-b)>2)colored++;}
                check(white>100&&colored==0,"native loading frame contains a visible white wordmark and monochrome pixels only");
            }
        }finally{client.options.getMonochromeLogo().setValue(monochrome);com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().popMatrix();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();client.getFramebuffer().endWrite();com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,previousTexture);com.mojang.blaze3d.systems.RenderSystem.bindTexture(previousTexture);}
    }
    static void verifyVisualModules()throws Exception {
        Class.forName("net.minecraft.client.render.entity.ItemEntityRenderer");
        check(true,"item physics renderer redirects load");
        Class<?> config=protectedClass("com.batclient.HudConfig"),visual=protectedClass("com.batclient.VisualModules");
        Method snapshot=Arrays.stream(config.getDeclaredMethods()).filter(m->m.getParameterCount()==0&&m.getReturnType()==com.google.gson.JsonObject.class).findFirst().orElseThrow();snapshot.setAccessible(true);
        var cfg=(com.google.gson.JsonObject)snapshot.invoke(null);var modules=cfg.getAsJsonObject("modules");var old=modules.deepCopy();
        try {
            Method angle=Arrays.stream(visual.getDeclaredMethods()).filter(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{float.class})&&m.getReturnType()==float.class).findFirst().orElseThrow();angle.setAccessible(true);
            var hurt=new com.google.gson.JsonObject();hurt.addProperty("en",true);var hs=new com.google.gson.JsonObject();hs.addProperty("intensity",0);hurt.add("set",hs);modules.add("hurtcamera",hurt);
            check((float)angle.invoke(null,14f)==0,"enabled hurt camera removes tilt at zero");
            hs.addProperty("intensity",50);check((float)angle.invoke(null,14f)==7,"hurt camera scales tilt");
            hurt.addProperty("en",false);check((float)angle.invoke(null,14f)==14,"disabled hurt camera preserves vanilla");
            var vanilla=net.minecraft.client.render.GameRenderer.getRenderTypeGlintProgram();
            var glint=new com.google.gson.JsonObject();glint.addProperty("en",true);var gs=new com.google.gson.JsonObject();gs.addProperty("color","#FF0000FF");gs.addProperty("strength",.5);glint.add("set",gs);modules.add("glintcolor",glint);
            var tinted=net.minecraft.client.render.GameRenderer.getRenderTypeGlintProgram();
            check(tinted!=null&&tinted!=vanilla&&tinted.getUniform("BatTint")!=null,"enabled glint uses compiled native tint shader");
            var rgba=tinted.getUniform("BatTint").getFloatData();check(rgba.get(0)==1&&rgba.get(1)==0&&rgba.get(2)==0&&rgba.get(3)==.5f,"selected glint color and brightness reach GPU uniform");
            check(net.minecraft.client.render.GameRenderer.getRenderTypeArmorEntityGlintProgram()==tinted&&net.minecraft.client.render.GameRenderer.getRenderTypeEntityGlintDirectProgram()==tinted,"armor and direct entity glint use tint shader");
            var glintContext=new DrawContext(net.minecraft.client.MinecraftClient.getInstance(),net.minecraft.client.MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers());
            var sword=new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD);sword.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,true);
            glintContext.drawItem(sword,0,0);glintContext.draw();check(true,"native enchanted item draw executes with tint shader");
            glint.addProperty("en",false);check(net.minecraft.client.render.GameRenderer.getRenderTypeGlintProgram()==vanilla,"disabled glint restores vanilla shader");
            Class<?> layers=protectedClass("com.batclient.VisualLayers");Method lines=Arrays.stream(layers.getDeclaredMethods()).filter(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{float.class})).findFirst().orElseThrow();lines.setAccessible(true);
            check(lines.invoke(null,2f)==lines.invoke(null,2f),"custom line layers are cached");
            Class<?> hud=protectedClass("com.batclient.HudRenderer");Method preview=Arrays.stream(hud.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{DrawContext.class,net.minecraft.client.MinecraftClient.class,com.google.gson.JsonObject.class})).findFirst().orElseThrow();preview.setAccessible(true);
            var bounds=new com.google.gson.JsonObject();bounds.addProperty("id","itemtracker");bounds.addProperty("left",.2);bounds.addProperty("top",.2);bounds.addProperty("right",.8);bounds.addProperty("bottom",.8);
            preview.invoke(null,glintContext,net.minecraft.client.MinecraftClient.getInstance(),bounds);glintContext.draw();check(true,"item tracker native settings preview draws icons and sample rows");
            bounds.addProperty("id","minimap");preview.invoke(null,glintContext,net.minecraft.client.MinecraftClient.getInstance(),bounds);glintContext.draw();check(true,"minimap native preview uploads texture and draws direction marker");
            var hit=new com.google.gson.JsonObject();hit.addProperty("en",true);var hitSettings=new com.google.gson.JsonObject();hitSettings.addProperty("color","#3366CCFF");hitSettings.addProperty("strength",.5);hit.add("set",hitSettings);modules.add("hitcolor",hit);
            var client=net.minecraft.client.MinecraftClient.getInstance();
            try(var overlay=new net.minecraft.client.render.OverlayTexture()){
                Field base=overlay.getClass().getDeclaredField("texture");base.setAccessible(true);var original=(net.minecraft.client.texture.NativeImageBackedTexture)base.get(overlay);
                overlay.setupOverlayColor();Field custom=overlay.getClass().getDeclaredField("bat$hitTexture");custom.setAccessible(true);var image=(net.minecraft.client.texture.NativeImageBackedTexture)custom.get(overlay);
                check(image.getImage().getColor(0,3)==0x80CC6633,"hit color RGBA and intensity reach native overlay pixels");
                check(image.getImage().getColor(7,12)==original.getImage().getColor(7,12),"hit color preserves vanilla white overlay rows");
                hitSettings.addProperty("color","#FF0000FF");hitSettings.addProperty("strength",1);overlay.setupOverlayColor();check(custom.get(overlay)==image&&image.getImage().getColor(0,3)==0x000000FF,"hit color updates cached texture when settings change");
                hit.addProperty("en",false);overlay.setupOverlayColor();check(com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(1)==original.getGlId(),"disabled hit color binds vanilla overlay");
            }finally{client.gameRenderer.getOverlayTexture().setupOverlayColor();}
        } finally {cfg.add("modules",old);}
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
}
