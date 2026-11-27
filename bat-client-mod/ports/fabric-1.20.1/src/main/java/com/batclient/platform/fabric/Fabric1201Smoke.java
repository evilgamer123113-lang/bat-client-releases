package com.batclient.platform.fabric;

import com.batclient.BatClientMod;
import com.batclient.platform.ClientHookServices;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;

/** Internal project only: real native world/menu fixture, never a release entrypoint. */
public final class Fabric1201Smoke implements ClientModInitializer,java.util.function.Consumer<MinecraftClient> {
    private int ticks,worldFrames;
    private boolean loading,done,rawMouse;
    private int frameLimit;
    private long began;
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("batclient.internalSmoke"))return;
        ClientLifecycleEvents.CLIENT_STARTED.register(client->{
            if(Boolean.getBoolean("batclient.productionSmoke")){
                var loader=net.fabricmc.loader.api.FabricLoader.getInstance();
                if(loader.isDevelopmentEnvironment()||!loader.getMappingResolver().getCurrentRuntimeNamespace().equals("intermediary"))
                    throw new AssertionError("Expected actual Fabric production intermediary runtime");
                System.out.println("BAT_FABRIC_1201_PRODUCTION_NAMESPACE_OK: intermediary, development=false");
            }
            if(Boolean.getBoolean("batclient.compatAgent")){
                try{Class.forName("com.batclient.cosmetics.AgentMain",false,ClassLoader.getSystemClassLoader());}
                catch(ClassNotFoundException error){throw new AssertionError("Actual cosmetics premain absent",error);}
                if(java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().anyMatch(gc->gc.getName().contains("G1")))
                    throw new AssertionError("Explicit custom SerialGC was replaced");
                System.out.println("BAT_FABRIC_1201_AGENT_CUSTOM_JVM_OK: actual premain and selected SerialGC");
            }
            if(Boolean.getBoolean("batclient.protectedSmoke")){
                String source=BatClientMod.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
                if(!source.contains("protected-runs"))throw new AssertionError("Readable sources masked protected Fabric port: "+source);
                for(Class<?> dependency:new Class<?>[]{io.github.nilsen84.ultralight.Ultralight.class,kotlin.jvm.internal.Intrinsics.class}){
                    String location=dependency.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
                    if(location.contains("modules-2"))throw new AssertionError("Loose runtime masked nested Fabric dependency: "+location);
                }
                System.out.println("BAT_FABRIC_1201_PROTECTED_NESTED_RUNTIME_OK");
            }
            began=System.nanoTime();
            rawMouse=client.options.getRawMouseInput().getValue();
            frameLimit=client.options.getMaxFps().getValue();
            ClientHookServices.get().afterEntities(frame->worldFrames++);
            ClientHookServices.get().endTick(this);
        });
    }
    @Override public void accept(MinecraftClient client){
        if(done)return;
        try{
            if(System.nanoTime()-began>180_000_000_000L)throw new AssertionError("Internal Fabric world/menu timeout");
            if(!loading){
                if(client.getOverlay()!=null||client.currentScreen==null)return;
                if(client.currentScreen instanceof net.minecraft.client.gui.screen.AccessibilityOnboardingScreen){
                    client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());ticks=0;return;
                }
                if(++ticks<60)return;
                if(BatClientMod.getActiveScreen()!=client.currentScreen||com.batclient.web.BatClientShaders.bgraPositionTextureShader==null)
                    throw new AssertionError("Bat Fabric menu/shader missing: screen="+client.currentScreen.getClass().getName()+", active="+(BatClientMod.getActiveScreen()==client.currentScreen)+", shader="+(com.batclient.web.BatClientShaders.bgraPositionTextureShader!=null));
                System.out.println("BAT_FABRIC_1201_NATIVE_MENU_OK");
                verifyNativeAccounts(client);
                client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(4);
                loading=true;ticks=0;began=System.nanoTime();
                var info=new net.minecraft.world.level.LevelInfo("Bat Fabric Internal Test",net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.GameRules(),net.minecraft.resource.DataConfiguration.SAFE_MODE);
                client.createIntegratedServerLoader().createAndStart("BatFabricInternal-"+System.currentTimeMillis(),info,new net.minecraft.world.gen.GeneratorOptions(12345L,false,false),registry->registry.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder(),client.currentScreen);
                return;
            }
            if(client.world==null||client.player==null)return;
            if(client.currentScreen!=null&&ticks<90)client.setScreen(null);
            if(ticks==30)verifyBackportedFeatures(client);
            if(++ticks==90)BatClientMod.openWaypoints(client);
            if(ticks<120)return;
            if(worldFrames<10||BatClientMod.cachedOverlay()==null||client.currentScreen==null)throw new AssertionError("Fabric world/HUD/waypoint menu missing");
            if(client.options.getRawMouseInput().getValue()!=rawMouse||client.options.getMaxFps().getValue()!=frameLimit)throw new AssertionError("Saved input/FPS preference changed");
            finish(client,"BAT_FABRIC_1201_INTERNAL_WORLD_OK: "+worldFrames+" frames; world/HUD/waypoint menu; NOT release certification");
        }catch(Throwable error){error.printStackTrace();finish(client,"FAILED: "+error);}
    }
    private static void verifyNativeAccounts(MinecraftClient client){
        var access=(com.batclient.ClientAccountAccess)client;
        var original=access.bat$captureAccount();
        var uuid=java.util.UUID.nameUUIDFromBytes("OfflinePlayer:BatAccountProbe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var session=new net.minecraft.client.util.Session("BatAccountProbe",uuid.toString(),"0",java.util.Optional.empty(),java.util.Optional.empty(),net.minecraft.client.util.Session.AccountType.LEGACY);
        var profile=new com.mojang.authlib.GameProfile(uuid,"BatAccountProbe");
        profile.getProperties().put("bat-native-probe",new com.mojang.authlib.properties.Property("bat-native-probe","preserved"));
        var probe=new com.batclient.accounts.AccountSession(session,com.mojang.authlib.minecraft.UserApiService.OFFLINE,profile);
        profile.getProperties().clear();
        if(!probe.profile().getProperties().containsKey("bat-native-probe"))throw new AssertionError("Account profile was not copied");
        try{
            access.bat$applyAccount(probe);
            if(!client.getSession().getUsername().equals("BatAccountProbe")||!client.getSessionProperties().containsKey("bat-native-probe"))throw new AssertionError("Native account session/property switch failed");
        }finally{access.bat$applyAccount(original);}
        if(!client.getSession().getUsername().equals(original.session().getUsername())||client.getSessionProperties().size()!=original.profile().getProperties().size())throw new AssertionError("Original account restore failed");
        System.out.println("BAT_1201_NATIVE_ACCOUNT_SWITCH_RESTORE_OK: synthetic offline session/property ownership, original restored");
    }
    private static String firstVisibleChat(net.minecraft.client.gui.hud.ChatHud chat)throws Exception {
        for(var field:net.minecraft.client.gui.hud.ChatHud.class.getDeclaredFields()){
            if(!java.util.List.class.isAssignableFrom(field.getType()))continue;
            field.setAccessible(true);var lines=(java.util.List<?>)field.get(chat);
            if(lines.isEmpty()||!(lines.get(0) instanceof net.minecraft.client.gui.hud.ChatHudLine.Visible line))continue;
            var text=new StringBuilder();line.content().accept((index,style,cp)->{text.appendCodePoint(cp);return true;});return text.toString();
        }
        throw new AssertionError("Visible chat missing");
    }
    private static void verifyBackportedFeatures(MinecraftClient client)throws Exception {
        var chat=client.inGameHud.getChatHud();
        chat.addMessage(net.minecraft.text.Text.literal("Bat system message stays unattributed"));
        chat.addMessage(net.minecraft.text.Text.literal("Bat system message stays unattributed"));
        if(firstVisibleChat(chat).contains("[2x]"))throw new AssertionError("Unattributed system messages merged");
        var message=net.minecraft.text.Text.literal("<"+client.player.getGameProfile().getName()+"> Bat native chat check");
        chat.addMessage(message.copy());
        chat.addMessage(message.copy());
        if(!firstVisibleChat(chat).contains("[2x]"))throw new AssertionError("Duplicate chat did not merge");
        chat.reset();
        if(!firstVisibleChat(chat).contains("[2x]"))throw new AssertionError("Chat history did not survive refresh");
        var food=com.batclient.AdventureHud.lines(client,"foodpreview",new com.google.gson.JsonObject(),true);
        if(food.stream().noneMatch(s->s.contains("Hunger: +8"))||food.stream().noneMatch(s->s.contains("Saturation: +12.8")))throw new AssertionError("Legacy food values changed: "+food);
        var comparison=com.batclient.ItemPreviews.compare(new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE),new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_CHESTPLATE),net.minecraft.entity.EquipmentSlot.CHEST,false);
        if(comparison.stream().noneMatch(t->t.getString().contains("Armor: +2.0")))throw new AssertionError("Legacy equipment attributes missing");
        var original=com.batclient.HudConfig.json();
        try{
            var cfg=com.batclient.HudConfig.snapshot().deepCopy();var module=new com.google.gson.JsonObject();module.addProperty("en",true);var settings=new com.google.gson.JsonObject();settings.addProperty("shift",false);module.add("set",settings);cfg.getAsJsonObject("modules").add("shulkerpreview",module);com.batclient.HudConfig.preview(cfg.toString());
            var box=new net.minecraft.item.ItemStack(net.minecraft.item.Items.SHULKER_BOX);var contents=net.minecraft.util.collection.DefaultedList.ofSize(27,net.minecraft.item.ItemStack.EMPTY);contents.set(4,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND,7));var nbt=new net.minecraft.nbt.NbtCompound();net.minecraft.inventory.Inventories.writeNbt(nbt,contents);box.setSubNbt("BlockEntityTag",nbt);
            var preview=(com.batclient.ItemPreviews.Contents)com.batclient.ItemPreviews.contents(box).orElseThrow();if(preview.items().get(4).getCount()!=7||!preview.items().get(4).isOf(net.minecraft.item.Items.DIAMOND))throw new AssertionError("Shulker NBT contents changed");
        }finally{com.batclient.HudConfig.preview(original);}
        System.out.println("BAT_FABRIC_1201_LEGACY_FEATURES_OK: chat repeat/history, food saturation, equipment attributes, shulker NBT");
    }
    private void finish(MinecraftClient client,String result){
        done=true;System.out.println(result);
        try{java.nio.file.Files.writeString(client.runDirectory.toPath().resolve("result.txt"),result);}catch(Exception error){error.printStackTrace();}
        client.scheduleStop();
    }
}
