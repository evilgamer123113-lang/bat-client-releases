package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientHooks;
import com.batclient.platform.mc189.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class NofapiWorldRendererMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow private ClientWorld world;

    @Inject(method = "renderEntities", at = @At("RETURN"))
    private void bat$onAfterEntities(Entity camera, CameraView cameraView, float tickDelta, CallbackInfo ci) {
        if (camera == null || world == null) return;
        double x = camera.prevX + (camera.x - camera.prevX) * tickDelta;
        double y = camera.prevY + (camera.y - camera.prevY) * tickDelta;
        double z = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
        FabricClientHooks.onAfterEntities(new MatrixStack(), new Vec3d(x, y, z), world, tickDelta);
    }

    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void bat$onBlockOutline(PlayerEntity player, BlockHitResult hit, int execute, float tickDelta, CallbackInfo ci) {
        if (hit == null || hit.type != BlockHitResult.Type.BLOCK || world == null) return;
        BlockPos pos = hit.getBlockPos();
        if (pos == null) return;
        BlockState state = world.getBlockState(pos);
        Entity camera = client != null ? client.getCameraEntity() : player;
        double x = camera != null ? camera.prevX + (camera.x - camera.prevX) * tickDelta : 0;
        double y = camera != null ? camera.prevY + (camera.y - camera.prevY) * tickDelta : 0;
        double z = camera != null ? camera.prevZ + (camera.z - camera.prevZ) * tickDelta : 0;
        if (!FabricClientHooks.onBlockOutline(new MatrixStack(), new Vec3d(x, y, z), world, tickDelta, state, pos, camera)) {
            ci.cancel();
        }
    }
}
