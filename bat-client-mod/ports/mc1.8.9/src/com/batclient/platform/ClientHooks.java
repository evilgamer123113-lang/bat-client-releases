package com.batclient.platform;

import com.batclient.platform.mc1194.DrawContext;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Loader-neutral hooks for the 1.8 / 1.8.9 adapter. */
public interface ClientHooks {
    KeyBinding registerKeyBinding(KeyBinding binding);
    InputUtil.Key getBoundKey(KeyBinding binding);
    void endTick(Consumer<MinecraftClient> callback);
    void started(Consumer<MinecraftClient> callback);
    void stopping(Consumer<MinecraftClient> callback);
    void beforeScreen(ScreenInit callback);
    void hud(HudDraw callback);
    void worldStart(Runnable callback);
    void worldEnd(Runnable callback);
    void afterEntities(Consumer<WorldFrame> callback);
    void blockOutline(BlockOutline callback);
    void disconnected(Runnable callback);
    void itemTooltip(BiConsumer<ItemStack, List<Text>> callback);
    void playPayload(Identifier id, int maxBytes, BiConsumer<String, MinecraftClient> receiver);

    interface BlockOutline { boolean draw(WorldFrame frame, BlockTarget target); }
    interface ScreenInit { void before(MinecraftClient client, Screen screen, int width, int height); }
    interface HudDraw { void draw(DrawContext context); }
}
