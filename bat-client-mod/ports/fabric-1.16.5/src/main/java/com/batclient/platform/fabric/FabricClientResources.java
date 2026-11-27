package com.batclient.platform.fabric;

import com.batclient.platform.ClientResources;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** Pure Fabric Loader + Mixin resource/layer registration (no Fabric API dependency). */
public final class FabricClientResources implements ClientResources {
    private static final List<PlayerLayers> PLAYER_LAYERS = new ArrayList<>();
    private static final List<ShaderRegistration> SHADERS = new ArrayList<>();
    private static final List<Runnable> RELOADS = new ArrayList<>();

    @Override
    public void playerLayers(PlayerLayers callback) {
        PLAYER_LAYERS.add(callback);
    }

    @Override
    public void shaders(ShaderRegistration callback) {
        SHADERS.add(callback);
    }

    @Override
    public void reload(Identifier id, Runnable callback) {
        RELOADS.add(callback);
    }

    public static void onPlayerLayers(PlayerEntityRenderer renderer, Consumer<FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>> adder) {
        for (PlayerLayers cb : PLAYER_LAYERS) {
            cb.register(renderer, adder);
        }
    }

    public static void onReload() {
        for (Runnable cb : RELOADS) {
            cb.run();
        }
    }
}
