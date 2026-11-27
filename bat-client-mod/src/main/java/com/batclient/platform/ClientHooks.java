package com.batclient.platform;

import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Loader-neutral hooks for the 1.21.1 game adapter. Not a cross-version Minecraft API. */
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
    void itemTooltip(java.util.function.BiConsumer<net.minecraft.item.ItemStack,java.util.List<net.minecraft.text.Text>> callback);
    void tooltipComponent(java.util.function.Function<net.minecraft.item.tooltip.TooltipData,net.minecraft.client.gui.tooltip.TooltipComponent> factory);
    <T extends net.minecraft.network.packet.CustomPayload> void playPayload(net.minecraft.network.packet.CustomPayload.Id<T> id,
        net.minecraft.network.codec.PacketCodec<net.minecraft.network.RegistryByteBuf,T> codec,int maxBytes,
        java.util.function.BiConsumer<T,MinecraftClient> receiver);
    interface BlockOutline {boolean draw(WorldFrame frame,BlockTarget target);}
    interface ScreenInit {void before(MinecraftClient client,Screen screen,int width,int height);}
    interface HudDraw {void draw(DrawContext context);}
}
