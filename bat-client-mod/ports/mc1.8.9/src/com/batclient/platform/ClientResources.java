package com.batclient.platform;

import java.util.function.Consumer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.util.Identifier;

/** Resource hooks for the 1.8 / 1.8.9 adapter. */
public interface ClientResources {
    void reload(Identifier id, Runnable callback);
    void playerLayers(PlayerLayers callback);
    interface PlayerLayers {
        void register(PlayerEntityRenderer renderer,
            Consumer<FeatureRenderer<AbstractClientPlayerEntity>> add);
    }
}
