package com.batclient.mixin;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderer.class)
public class EntityStateCaptureMixin {
 @Inject(method="updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V",at=@At("TAIL"))
 private void bat$identity(Entity entity,EntityRenderState state,float delta,CallbackInfo ci){((com.batclient.platform.mc1214.EntityStateAccess)state).bat$updateIdentity(entity.getId(),delta);}
}
