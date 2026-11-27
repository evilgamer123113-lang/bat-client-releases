package com.batclient.platform;

import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.Identifier;

/** Resource hooks for the 1.21.1 adapter; register before initial resource loading. */
public interface ClientResources {
    void shaders(ShaderRegistration callback);
    void reload(Identifier id,Runnable callback);
    void playerLayers(PlayerLayers callback);
    interface PlayerLayers {
        void register(net.minecraft.client.render.entity.PlayerEntityRenderer renderer,
            Consumer<net.minecraft.client.render.entity.feature.FeatureRenderer<net.minecraft.client.network.AbstractClientPlayerEntity,
                net.minecraft.client.render.entity.model.PlayerEntityModel<net.minecraft.client.network.AbstractClientPlayerEntity>>> add);
    }
    interface ShaderRegistration {void register(ShaderContext context) throws IOException;}
    interface ShaderContext {
        void register(Identifier id,VertexFormat format,Consumer<ShaderProgram> loaded) throws IOException;
    }
}
