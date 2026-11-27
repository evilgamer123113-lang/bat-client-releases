package com.batclient.mixin;

import java.util.*;
import java.util.function.BooleanSupplier;
import com.batclient.performance.NearestReadyBatch;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pre-batch-protocol local transport: bounded ready-only scheduling, never generates chunks. */
@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class LocalChunkBatchMixin {
    @Shadow @Final private ServerWorld world;
    @Shadow public abstract List<ServerPlayerEntity> getPlayersWatchingChunk(ChunkPos pos,boolean edge);
    @Shadow protected abstract void sendChunkDataPackets(ServerPlayerEntity player,MutableObject<ChunkDataS2CPacket> packet,WorldChunk chunk);
    @Unique private final Map<ServerPlayerEntity,LinkedHashMap<Long,WorldChunk>> bat$pending=new IdentityHashMap<>();
    @Unique private boolean bat$draining;
    @Inject(method="sendChunkDataPackets",at=@At("HEAD"),cancellable=true)
    private void bat$enqueue(ServerPlayerEntity player,MutableObject<ChunkDataS2CPacket> packet,WorldChunk chunk,CallbackInfo ci){
        if(bat$draining||!(player.networkHandler.getConnectionAddress() instanceof io.netty.channel.local.LocalAddress)||player.getWorld()!=world)return;
        var queue=bat$pending.computeIfAbsent(player,key->new LinkedHashMap<>());
        // Overflow falls back to vanilla delivery; no chunk is lost or held without a bound.
        if(queue.size()>=512&&!queue.containsKey(chunk.getPos().toLong()))return;
        queue.put(chunk.getPos().toLong(),chunk);ci.cancel();
    }
    @Inject(method="sendWatchPackets",at=@At("HEAD"))
    private void bat$unwatch(ServerPlayerEntity player,ChunkPos pos,MutableObject<ChunkDataS2CPacket> packet,boolean previous,boolean current,CallbackInfo ci){
        if(!current){var queue=bat$pending.get(player);if(queue!=null)queue.remove(pos.toLong());}
    }
    @Inject(method="tick",at=@At("TAIL"))
    private void bat$flush(BooleanSupplier shouldKeepTicking,CallbackInfo ci){
        long started=System.nanoTime();int sent=0;
        var players=bat$pending.entrySet().iterator();
        while(players.hasNext()){
            var entry=players.next();var player=entry.getKey();var queue=entry.getValue();
            if(player.isRemoved()||player.getWorld()!=world||!player.networkHandler.isConnectionOpen()){players.remove();continue;}
            var center=player.getChunkPos();var nearest=new NearestReadyBatch<WorldChunk>(8);
            var chunks=queue.values().iterator();
            while(chunks.hasNext()){
                var chunk=chunks.next();
                if(!getPlayersWatchingChunk(chunk.getPos(),false).contains(player)){chunks.remove();continue;}
                long dx=(long)center.x-chunk.getPos().x,dz=(long)center.z-chunk.getPos().z;
                nearest.offer(chunk,dx*dx+dz*dz);
            }
            bat$draining=true;
            try{for(var chunk:nearest.result()){
                if(sent>=8||sent>0&&System.nanoTime()-started>=2_000_000L)break;
                sendChunkDataPackets(player,new MutableObject<>(),chunk);queue.remove(chunk.getPos().toLong());sent++;
            }}finally{bat$draining=false;}
            if(queue.isEmpty())players.remove();
            if(sent>=8||sent>0&&System.nanoTime()-started>=2_000_000L)break;
        }
    }
}
