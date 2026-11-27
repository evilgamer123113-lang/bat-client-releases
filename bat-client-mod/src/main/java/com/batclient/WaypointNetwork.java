package com.batclient;

import com.google.gson.JsonParser;
import com.batclient.platform.ClientHookServices;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Bat Client v1 plugin-message receiver. Only the connected Minecraft server can supply these messages. */
public final class WaypointNetwork {
    public record Payload(String json) implements CustomPayload {
        public static final Id<Payload> ID=new Id<>(Identifier.of("batclient","waypoints"));
        public static final PacketCodec<RegistryByteBuf,Payload> CODEC=PacketCodec.of((value,buf)->buf.writeString(value.json,8192),buf->new Payload(buf.readString(8192)));
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static long window;private static int count;
    private WaypointNetwork(){}
    public static void init(){
        ClientHookServices.get().playPayload(Payload.ID,Payload.CODEC,32773,(payload,client)->{
            long now=System.nanoTime();if(now-window>1_000_000_000L){window=now;count=0;}if(++count>256)return;
            try{Waypoints.receive(JsonParser.parseString(payload.json()).getAsJsonObject(),client);}catch(RuntimeException ignored){/* Invalid remote messages cannot mutate personal data or interrupt gameplay. */}
        });
    }
}
