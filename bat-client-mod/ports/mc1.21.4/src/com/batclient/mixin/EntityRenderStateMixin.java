package com.batclient.mixin;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.*;
@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements com.batclient.platform.mc1214.EntityStateAccess {
 @Unique private int bat$id=-1;@Unique private float bat$delta;
 public int bat$entityId(){return bat$id;}public float bat$tickDelta(){return bat$delta;}
 public void bat$updateIdentity(int id,float delta){bat$id=id;bat$delta=delta;}
}
