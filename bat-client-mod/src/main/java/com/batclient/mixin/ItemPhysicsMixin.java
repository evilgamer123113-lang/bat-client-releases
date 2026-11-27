package com.batclient.mixin;

import com.batclient.VisualModules;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsMixin {
    @Redirect(method="render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"))
    private void bat$height(MatrixStack matrices,float x,float y,float z,ItemEntity entity,float yaw,float delta,MatrixStack original,VertexConsumerProvider vertices,int light){matrices.translate(x,VisualModules.itemHeight(entity,y),z);}
    @Redirect(method="render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V"))
    private void bat$rotate(MatrixStack matrices,Quaternionf rotation,ItemEntity entity,float yaw,float delta,MatrixStack original,VertexConsumerProvider vertices,int light){VisualModules.rotateItem(matrices,rotation,entity,delta);}
}
