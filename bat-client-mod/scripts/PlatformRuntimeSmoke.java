import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.ServiceLoader;

/** Isolated native test of the protected platform provider; never packaged for players. */
public final class PlatformRuntimeSmoke implements ClientModInitializer {
    private boolean done;
    private int ticks;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(done || client.currentScreen == null || client.getOverlay() != null)return;
            // A fresh disposable profile shows vanilla accessibility onboarding first.
            // Advance only that fixture screen, never a player's real profile.
            if(client.currentScreen.getClass().getSimpleName().contains("AccessibilityOnboarding")){
                client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());ticks=0;return;
            }
            if(++ticks<60)return;
            done=true;
            try {
                Class<?> contract=Class.forName("com.batclient.platform.PlatformEnvironment");
                Object service=Class.forName("com.batclient.platform.Platforms").getMethod("get").invoke(null);
                if(!contract.getMethod("loaderId").invoke(service).equals("fabric"))throw new AssertionError("Wrong loader provider");
                Path game=(Path)contract.getMethod("getGameDir").invoke(service);
                Path config=(Path)contract.getMethod("getConfigDir").invoke(service);
                if(!game.equals(FabricLoader.getInstance().getGameDir()))throw new AssertionError("Game path changed");
                if(!config.equals(FabricLoader.getInstance().getConfigDir()))throw new AssertionError("Config path changed");
                if(!game.toAbsolutePath().normalize().endsWith("port-platform-runtime"))throw new AssertionError("Unsafe test directory");
                if(!Boolean.TRUE.equals(contract.getMethod("isModLoaded",String.class).invoke(service,"batclient")))throw new AssertionError("Installed mod detection failed");
                if(!contract.getMethod("isDevelopmentEnvironment").invoke(service).equals(FabricLoader.getInstance().isDevelopmentEnvironment()))throw new AssertionError("Development flag changed");
                if(ServiceLoader.load(contract).stream().count()!=1)throw new AssertionError("Ambiguous service provider");
                Class<?> hookContract=Class.forName("com.batclient.platform.ClientHooks");
                Object hooks=Class.forName("com.batclient.platform.ClientHookServices").getMethod("get").invoke(null);
                if(!hooks.getClass().getName().equals("com.batclient.platform.fabric.FabricClientHooks"))throw new AssertionError("Wrong event adapter");
                if(ServiceLoader.load(hookContract).stream().count()!=1)throw new AssertionError("Ambiguous event adapter");
                Object screen=Class.forName("com.batclient.BatClientMod").getMethod("getActiveScreen").invoke(null);
                if(screen==null||screen!=client.currentScreen)throw new AssertionError("Protected Bat menu did not initialize; screen="+client.currentScreen.getClass().getName());
                String result="PLATFORM_NATIVE_OK: 10 checks; protected providers, exact directories, mod discovery, development flag, one event adapter, Bat menu";
                Files.writeString(game.resolve("result.txt"),result);System.out.println(result);
            }catch(Throwable error){error.printStackTrace();try{Files.writeString(FabricLoader.getInstance().getGameDir().resolve("result.txt"),"FAILED: "+error);}catch(Exception ignored){}}
            finally{client.scheduleStop();}
        });
    }
}
