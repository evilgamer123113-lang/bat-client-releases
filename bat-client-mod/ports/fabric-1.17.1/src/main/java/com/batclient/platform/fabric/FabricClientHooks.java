package com.batclient.platform.fabric;

import com.batclient.mixin.NofapiGameOptionsAccessor;
import com.batclient.mixin.NofapiKeyBindingAccessor;
import com.batclient.platform.BlockTarget;
import com.batclient.platform.ClientHooks;
import com.batclient.platform.WorldFrame;
import com.batclient.platform.mc1194.DrawContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.item.TooltipData;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;

/** Pure Fabric Loader + Mixin hook implementation (no Fabric API dependency). */
public final class FabricClientHooks implements ClientHooks {
    private static final List<Consumer<MinecraftClient>> END_TICKS = new ArrayList<>();
    private static final List<Consumer<MinecraftClient>> STARTED = new ArrayList<>();
    private static final List<Consumer<MinecraftClient>> STOPPING = new ArrayList<>();
    private static final List<ScreenInit> BEFORE_SCREEN = new ArrayList<>();
    private static final List<HudDraw> HUDS = new ArrayList<>();
    private static final List<Runnable> DISCONNECTED = new ArrayList<>();
    private static final List<Runnable> WORLD_STARTS = new ArrayList<>();
    private static final List<Runnable> WORLD_ENDS = new ArrayList<>();
    private static final List<Consumer<WorldFrame>> AFTER_ENTITIES = new ArrayList<>();
    private static final List<BlockOutline> BLOCK_OUTLINES = new ArrayList<>();
    private static final List<BiConsumer<ItemStack, List<Text>>> ITEM_TOOLTIPS = new ArrayList<>();
    private static final List<Function<TooltipData, TooltipComponent>> TOOLTIP_COMPONENTS = new ArrayList<>();
    private static final Map<Identifier, PayloadEntry> PAYLOADS = new ConcurrentHashMap<>();
    private static final List<KeyBinding> PENDING_KEYS = new ArrayList<>();

    private record PayloadEntry(int maxBytes, BiConsumer<String, MinecraftClient> receiver) {}

    @Override
    public void playPayload(Identifier id, int maxBytes, BiConsumer<String, MinecraftClient> receiver) {
        PAYLOADS.put(id, new PayloadEntry(maxBytes, receiver));
    }

    @Override
    public void disconnected(Runnable callback) {
        DISCONNECTED.add(callback);
    }

    @Override
    public void itemTooltip(BiConsumer<ItemStack, List<Text>> callback) {
        ITEM_TOOLTIPS.add(callback);
    }

    @Override
    public void tooltipComponent(Function<TooltipData, TooltipComponent> factory) {
        TOOLTIP_COMPONENTS.add(factory);
    }

    @Override
    public KeyBinding registerKeyBinding(KeyBinding binding) {
        PENDING_KEYS.add(binding);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            flushPendingKeys(client);
        }
        return binding;
    }

    public static void flushPendingKeys(MinecraftClient client) {
        if (PENDING_KEYS.isEmpty() || client.options == null) return;
        var acc = (NofapiGameOptionsAccessor) client.options;
        KeyBinding[] current = acc.bat$getKeysAll();
        List<KeyBinding> list = new ArrayList<>(List.of(current));
        for (KeyBinding kb : PENDING_KEYS) {
            if (!list.contains(kb)) list.add(kb);
        }
        PENDING_KEYS.clear();
        acc.bat$setKeysAll(list.toArray(new KeyBinding[0]));
        KeyBinding.updateKeysByCode();
    }

    @Override
    public InputUtil.Key getBoundKey(KeyBinding binding) {
        return ((NofapiKeyBindingAccessor) binding).bat$getBoundKey();
    }

    @Override
    public void endTick(Consumer<MinecraftClient> callback) {
        END_TICKS.add(callback);
    }

    @Override
    public void started(Consumer<MinecraftClient> callback) {
        STARTED.add(callback);
    }

    @Override
    public void stopping(Consumer<MinecraftClient> callback) {
        STOPPING.add(callback);
    }

    @Override
    public void beforeScreen(ScreenInit callback) {
        BEFORE_SCREEN.add(callback);
    }

    @Override
    public void hud(HudDraw callback) {
        HUDS.add(callback);
    }

    @Override
    public void worldStart(Runnable callback) {
        WORLD_STARTS.add(callback);
    }

    @Override
    public void worldEnd(Runnable callback) {
        WORLD_ENDS.add(callback);
    }

    @Override
    public void afterEntities(Consumer<WorldFrame> callback) {
        AFTER_ENTITIES.add(callback);
    }

    @Override
    public void blockOutline(BlockOutline callback) {
        BLOCK_OUTLINES.add(callback);
    }

    public static void onStarted(MinecraftClient client) {
        flushPendingKeys(client);
        for (var cb : List.copyOf(STARTED)) cb.accept(client);
    }

    public static void onStopping(MinecraftClient client) {
        for (var cb : List.copyOf(STOPPING)) cb.accept(client);
    }

    public static void onEndTick(MinecraftClient client) {
        for (var cb : List.copyOf(END_TICKS)) cb.accept(client);
    }

    public static void onDisconnect() {
        for (var cb : DISCONNECTED) cb.run();
    }

    public static void onBeforeScreen(MinecraftClient client, Screen screen, int width, int height) {
        for (var cb : BEFORE_SCREEN) cb.before(client, screen, width, height);
    }

    public static void onHud(MatrixStack matrices) {
        DrawContext ctx = new DrawContext(matrices);
        for (var cb : HUDS) cb.draw(ctx);
    }

    public static void onWorldStart() {
        for (var cb : WORLD_STARTS) cb.run();
    }

    public static void onWorldEnd() {
        for (var cb : WORLD_ENDS) cb.run();
    }

    public static void onAfterEntities(MatrixStack matrices, Camera camera, VertexConsumerProvider consumers,
                                       Frustum frustum, ClientWorld world, Matrix4f projectionMatrix, float tickDelta) {
        if (AFTER_ENTITIES.isEmpty()) return;
        WorldFrame frame = new WorldView(matrices, camera, consumers, frustum, world, projectionMatrix, tickDelta);
        for (var cb : AFTER_ENTITIES) cb.accept(frame);
    }

    public static boolean onBlockOutline(MatrixStack matrices, Camera camera, VertexConsumerProvider consumers,
                                         Frustum frustum, ClientWorld world, Matrix4f projectionMatrix, float tickDelta,
                                         BlockState state, BlockPos pos, Entity entity) {
        if (BLOCK_OUTLINES.isEmpty()) return true;
        WorldFrame frame = new WorldView(matrices, camera, consumers, frustum, world, projectionMatrix, tickDelta);
        BlockTarget target = new TargetView(state, pos, entity);
        for (var cb : BLOCK_OUTLINES) {
            if (!cb.draw(frame, target)) return false;
        }
        return true;
    }

    public static void onItemTooltip(ItemStack stack, List<Text> lines) {
        for (var cb : ITEM_TOOLTIPS) cb.accept(stack, lines);
    }

    public static TooltipComponent onTooltipComponent(TooltipData data) {
        for (var factory : TOOLTIP_COMPONENTS) {
            TooltipComponent result = factory.apply(data);
            if (result != null) return result;
        }
        return null;
    }

    public static boolean onPayload(Identifier channel, net.minecraft.network.PacketByteBuf buf, MinecraftClient client) {
        PayloadEntry entry = PAYLOADS.get(channel);
        if (entry == null) return false;
        String json = buf.readString(entry.maxBytes);
        client.execute(() -> entry.receiver.accept(json, client));
        return true;
    }

    private record WorldView(MatrixStack matrixStack, Camera camera, VertexConsumerProvider consumers,
                             Frustum frustum, ClientWorld world, Matrix4f projectionMatrix,
                             float tickDelta) implements WorldFrame {}

    private record TargetView(BlockState blockState, BlockPos blockPos, Entity entity) implements BlockTarget {}
}
