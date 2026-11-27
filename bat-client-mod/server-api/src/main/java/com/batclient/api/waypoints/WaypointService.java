package com.batclient.api.waypoints;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** Main-thread Bukkit service; server markers exist only for the current player connection. */
public final class WaypointService {
    public static final String CHANNEL="batclient:waypoints";
    private final BatWaypointsPlugin plugin;
    private record Marker(UUID id,UUID world,JsonObject data,long expires){}
    private final Map<UUID,Map<UUID,Marker>> markers=new HashMap<>();
    WaypointService(BatWaypointsPlugin plugin){this.plugin=plugin;}
    private void main(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Use the Bukkit server thread for waypoint operations");}
    public WaypointBuilder createWaypointBuilder(int x,int y,int z){return new WaypointBuilder(x,y,z);}
    public UUID createWaypoint(Player player,WaypointBuilder builder){
        main();if(!player.isOnline())throw new IllegalArgumentException("Player is offline");var points=markers.computeIfAbsent(player.getUniqueId(),key->new LinkedHashMap<>());if(points.size()>=64)throw new IllegalStateException("64 server markers per player maximum");
        var data=new JsonObject();data.addProperty("x",builder.x);data.addProperty("y",builder.y);data.addProperty("z",builder.z);data.addProperty("name",builder.name);data.addProperty("group",builder.group);data.addProperty("color",builder.color);data.addProperty("opacity",builder.opacity);data.addProperty("beam",builder.beam);data.addProperty("chroma",builder.chroma);
        UUID id=UUID.randomUUID(),world=builder.worldId==null?player.getWorld().getUID():builder.worldId;var marker=new Marker(id,world,data,builder.duration==0?0:System.currentTimeMillis()+builder.duration*1000L);points.put(id,marker);sendWorld(player);sendMarker(player,marker);return id;
    }
    public void destroyWaypoint(Player player,UUID id){main();var points=markers.get(player.getUniqueId());if(points==null)return;var marker=points.remove(id);if(marker!=null&&marker.world.equals(player.getWorld().getUID())){var message=base("remove");message.addProperty("worldId",marker.world.toString());message.addProperty("id",id.toString());send(player,message);}}
    public void destroyWaypoints(Player player,Collection<UUID> ids){main();List.copyOf(ids).forEach(id->destroyWaypoint(player,id));}
    public void destroyAllWaypoints(Player player){main();markers.remove(player.getUniqueId());send(player,base("clear"));}
    public void synchronize(Player player){main();if(!player.isOnline())return;send(player,base("clear"));sendWorld(player);var points=markers.get(player.getUniqueId());if(points!=null)points.values().forEach(m->sendMarker(player,m));}
    void quit(Player player){markers.remove(player.getUniqueId());}
    void expire(){main();for(var player:Bukkit.getOnlinePlayers()){var points=markers.get(player.getUniqueId());if(points!=null)new ArrayList<>(points.values()).stream().filter(m->m.expires>0&&m.expires<=System.currentTimeMillis()).forEach(m->destroyWaypoint(player,m.id));}}
    private JsonObject base(String op){var message=new JsonObject();message.addProperty("version",1);message.addProperty("op",op);return message;}
    private void sendWorld(Player player){var message=base("world");message.addProperty("worldId",player.getWorld().getUID().toString());send(player,message);}
    private void sendMarker(Player player,Marker marker){if(!marker.world.equals(player.getWorld().getUID()))return;long remaining=marker.expires==0?0:(marker.expires-System.currentTimeMillis()+999)/1000;if(marker.expires>0&&remaining<=0)return;var data=marker.data.deepCopy();data.addProperty("dimension",player.getWorld().getKey().toString());var message=base("upsert");message.addProperty("worldId",marker.world.toString());message.addProperty("id",marker.id.toString());message.addProperty("durationSeconds",remaining);message.add("waypoint",data);send(player,message);}
    private void send(Player player,JsonObject message){
        if(!player.isOnline()||!player.getListeningPluginChannels().contains(CHANNEL))return;
        byte[] json=message.toString().getBytes(StandardCharsets.UTF_8);if(json.length>8192)throw new IllegalArgumentException("Waypoint packet too large");var packet=new ByteArrayOutputStream();int length=json.length;do{int next=length&127;length>>>=7;if(length!=0)next|=128;packet.write(next);}while(length!=0);packet.writeBytes(json);player.sendPluginMessage(plugin,CHANNEL,packet.toByteArray());
    }
}
