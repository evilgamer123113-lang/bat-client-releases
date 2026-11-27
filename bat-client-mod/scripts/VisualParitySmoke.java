package com.batclient.platform.fabric;

import com.batclient.BatClientMod;
import com.batclient.web.WebScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.function.Consumer;

/** Captures actual game framebuffers in disposable internal runtimes. */
public final class VisualParitySmoke implements ClientModInitializer {
    private record Step(String name, Consumer<MinecraftClient> open) {}
    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private int ticks;
    private int pending;
    private long overlayPaintedAt;
    private boolean done;
    private boolean hudReady;
    private Step active;
    private net.minecraft.client.gui.screen.Screen title;
    private long started = System.nanoTime();

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("batclient.visualParity")) return;
        steps.add(new Step("main-menu", client -> {}));
        steps.add(new Step("title-mod-toggles", client -> script(client, "openPanel('mods')")));
        steps.add(new Step("settings", client -> script(client, "openPanel('settings')")));
        steps.add(new Step("cosmetics", client -> script(client, "openPanel('cosmetics')")));
        addWorldScreens();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void addWorldScreens() {
        steps.add(new Step("world-list", game -> game.setScreen(new net.minecraft.client.gui.screen.world.SelectWorldScreen(title))));
        steps.add(new Step("server-list", game -> game.setScreen(new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(title))));
        steps.add(new Step("vanilla-options", game -> game.setScreen(new net.minecraft.client.gui.screen.option.OptionsScreen(title, game.options))));
        steps.add(new Step("world-hud", this::createWorld));
        steps.add(new Step("pause-menu", game -> game.setScreen(new GameMenuScreen(true))));
        steps.add(new Step("pause-mod-manager", game -> script(game, "openPanel('mods')")));
        steps.add(new Step("game-overlay", game -> {
            game.setScreen(null);
            BatClientMod.openWaypoints(game);
        }));
        steps.add(new Step("mod-manager", game -> script(game, "openPanel('mods')")));
    }

    private void addModuleScreens(MinecraftClient client) {
        String ids = script(client, "MODULES.filter(m=>m.settings&&m.settings.length).map(m=>m.id).join(',')");
        if (ids == null || ids.isBlank()) throw new AssertionError("No module settings found");
        for (String id : ids.replace("\"", "").split(",")) {
            if (!id.matches("[a-zA-Z0-9_-]+")) throw new AssertionError("Invalid module id " + id);
            if (id.equals("durability") || id.equals("fps")) {
                steps.add(new Step("module-" + id, game -> script(game, "openModSettings('" + id + "');if(document.getElementById('ms-pv')&&document.getElementById('ms-preview-wrap').classList.contains('hidden'))document.getElementById('ms-pv').click();if(document.getElementById('ms-preview-wrap'))document.getElementById('ms-preview-wrap').scrollIntoView();")));
            } else {
                steps.add(new Step("module-" + id, game -> script(game, "openModSettings('" + id + "')")));
            }
        }
        steps.add(new Step("waypoints", game -> script(game, "openWaypointManager(false)")));
        steps.add(new Step("hud-editor", game -> script(game, "closePanel(false);toggleHudEditor(true)")));
    }

    private void createWorld(MinecraftClient client) {
        client.options.pauseOnLostFocus = false;
        client.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
        client.options.getViewDistance().setValue(4);
        var info = new net.minecraft.world.level.LevelInfo("Visual parity", net.minecraft.world.GameMode.CREATIVE, false,
            net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.GameRules(), net.minecraft.resource.DataConfiguration.SAFE_MODE);
        client.createIntegratedServerLoader().createAndStart("VisualParity-" + System.currentTimeMillis(), info,
            new net.minecraft.world.gen.GeneratorOptions(12345L, false, false),
            registry->registry.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder(), title);
    }

    private static String script(MinecraftClient client, String javascript) {
        if (!(client.currentScreen instanceof WebScreen screen) || screen.window() == null)
            throw new IllegalStateException("Expected a loaded Bat screen");
        return screen.window().view().evaluateScript(javascript);
    }

    private void tick(MinecraftClient client) {
        if (done) return;
        try {
            if (System.nanoTime() - started > 180_000_000_000L) {
                String screen = client.currentScreen == null ? "none" : client.currentScreen.getTitle().getString();
                throw new AssertionError("Visual capture timed out at " + (active == null ? "startup" : active.name())
                    + " with screen " + screen);
            }
            if (client.getOverlay() != null) return;
            if (client.currentScreen instanceof net.minecraft.client.gui.screen.AccessibilityOnboardingScreen) {
                client.setScreen(new TitleScreen());
                ticks = 0;
                return;
            }
            if (active == null && ticks < 60 && !(client.currentScreen instanceof WebScreen)) return;
            if (active != null && active.name().equals("world-hud")) {
                if (client.world == null || client.player == null || client.currentScreen != null) {
                    ticks = 0;
                    return;
                }
                if (!hudReady) {
                    configureHud();
                    hudReady = true;
                    ticks = 0;
                }
            }
            if (++ticks < (active == null ? 60 : active.name().equals("pause-menu") ? 35 : active.name().equals("world-hud") ? 45 : 14)) return;
            if (pending != 0) return;
            if (active != null && active.name().equals("game-overlay")) {
                if (!(client.currentScreen instanceof WebScreen screen) || screen.window() == null
                        || !screen.window().hasPainted()) return;
                long now = System.nanoTime();
                if (overlayPaintedAt == 0) overlayPaintedAt = now;
                if (now - overlayPaintedAt < 400_000_000L) return;
            }
            if (active != null) {
                if (active.name().equals("main-menu")) title = client.currentScreen;
                if (active.name().endsWith("mod-manager")) {
                    String populated = script(client, "Number(!!document.querySelector('#mod-grid:not(.hidden) .mod-card'))");
                    if (!"1".equals(populated)) throw new AssertionError("Mod Manager grid is missing: " + active.name());
                }
                if (active.name().startsWith("module-")) {
                    String expected = active.name().substring("module-".length());
                    String selected = script(client, "msMod ? msMod.id : ''");
                    if (!expected.equals(selected == null ? "" : selected.replace("\"", "")))
                        throw new AssertionError("Incorrect module settings panel for " + expected);
                }
                capture(client, active.name());
                if (active.name().equals("mod-manager")) addModuleScreens(client);
            }
            active = steps.poll();
            if (active == null) {
                if (pending != 0) {
                    steps.add(new Step("complete", game -> {}));
                    return;
                }
                finish(client, "BAT_VISUAL_PARITY_CAPTURE_OK");
                return;
            }
            if (active.name().equals("complete")) {
                finish(client, "BAT_VISUAL_PARITY_CAPTURE_OK");
                return;
            }
            active.open().accept(client);
            ticks = 0;
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(client, "FAILED: " + failure);
        }
    }

    private static void configureHud() {
        String[] modules = {"fps", "coords", "cps", "keystrokes", "ping", "crosshair", "minimap", "totems", "waypoints", "blockoutline", "hitboxes", "lootbeams", "returntrail", "nametags"};
        int[] rows = {5, 16, 46, 55, 5, 0, 5, 120, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i < modules.length; i++) {
            String patch = rows[i] > 0 ? ("{\"en\":true,\"x\":4,\"y\":" + rows[i] + "}") : "{\"en\":true}";
            if (!com.batclient.HudConfig.updateModule(modules[i], patch, false))
                throw new AssertionError("Could not enable HUD module " + modules[i]);
        }
        com.batclient.Waypoints.action("save", "{\"name\":\"Parity Base\",\"x\":8,\"y\":6,\"z\":8,\"color\":\"#3B82F6\",\"beam\":true,\"tracked\":true}");
    }

    private void capture(MinecraftClient client, String name) throws Exception {
        Path directory = client.runDirectory.toPath().resolve("visual-parity");
        Files.createDirectories(directory);
        pending++;
        writeScreenshot(client, directory.resolve(name + ".png"));
    }

    private void writeScreenshot(MinecraftClient client, Path file) {
        save(client, file, ScreenshotRecorder.takeScreenshot(client.getFramebuffer()));
    }

    private void save(MinecraftClient client, Path file, net.minecraft.client.texture.NativeImage image) {
        try (image) {
            image.writeTo(file);
        } catch (Exception failure) {
            finish(client, "FAILED: " + failure);
        } finally {
            pending--;
        }
    }

    private void finish(MinecraftClient client, String result) {
        done = true;
        try {
            Files.writeString(client.runDirectory.toPath().resolve("result.txt"), result);
        } catch (Exception failure) {
            failure.printStackTrace();
        }
        client.scheduleStop();
    }
}
