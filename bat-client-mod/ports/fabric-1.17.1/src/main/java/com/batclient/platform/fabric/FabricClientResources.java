package com.batclient.platform.fabric;

import com.batclient.platform.ClientResources;
import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Shader;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;

/** Pure Fabric Loader + Mixin resource/shader/layer registration (no Fabric API dependency). */
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
        for (var cb : PLAYER_LAYERS) {
            cb.register(renderer, adder);
        }
    }

    public static void onRegisterShaders(ResourceFactory factory, List<Pair<Shader, Consumer<Shader>>> list) throws IOException {
        ShaderContext context = (id, format, loaded) -> {
            ResourceFactory wrapped = loc -> {
                if ("minecraft".equals(loc.getNamespace()) && loc.getPath().contains(id.getPath())) {
                    String path = "/assets/" + id.getNamespace() + "/" + loc.getPath();
                    java.io.InputStream stream = FabricClientResources.class.getResourceAsStream(path);
                    if (stream == null) {
                        stream = FabricClientResources.class.getResourceAsStream(path.replace("shaders/program/", "shaders/core/"));
                    }
                    if (stream != null) {
                        return new net.minecraft.resource.ResourceImpl(id.getNamespace(), new Identifier(id.getNamespace(), loc.getPath()), stream, null);
                    }
                }
                return factory.getResource(loc);
            };
            Shader shader = new Shader(wrapped, id.getPath(), format);
            list.add(Pair.of(shader, loaded));
        };
        for (var cb : SHADERS) {
            cb.register(context);
        }
    }

    public static void onReload() {
        for (var cb : RELOADS) cb.run();
    }
}
