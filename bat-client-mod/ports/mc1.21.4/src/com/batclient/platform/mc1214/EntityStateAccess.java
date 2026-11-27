package com.batclient.platform.mc1214;
/** Primitive identity only; no entity/world references retained by renderer caches. */
public interface EntityStateAccess {
 int bat$entityId();float bat$tickDelta();void bat$updateIdentity(int id,float delta);
 static net.minecraft.entity.Entity entity(net.minecraft.client.render.entity.state.EntityRenderState state){
  var client=net.minecraft.client.MinecraftClient.getInstance();
  return client.world==null?null:client.world.getEntityById(((EntityStateAccess)state).bat$entityId());
 }
}
