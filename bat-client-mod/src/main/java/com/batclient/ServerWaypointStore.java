package com.batclient;

import com.google.gson.*;
import java.util.*;

/** Session-only server markers. Never mixes remote IDs or mutations into personal saves. */
public final class ServerWaypointStore {
    private final Map<String,WaypointStore.Entry> points=new LinkedHashMap<>();
    private final Set<String> hidden=new HashSet<>();
    private String world="",tracked="";
    public void clear(){points.clear();hidden.clear();tracked="";world="";}
    public void accept(JsonObject message,String scope,String dimension){
        if(!message.has("version")||message.get("version").getAsInt()!=1)throw new IllegalArgumentException("Unsupported waypoint protocol");
        String op=message.get("op").getAsString();
        if(op.equals("clear")){clear();return;}
        String worldId=UUID.fromString(message.get("worldId").getAsString()).toString();
        if(op.equals("world")){if(!world.equals(worldId))clear();world=worldId;return;}
        if(!world.equals(worldId))throw new IllegalArgumentException("Server must select its world before sending markers");
        String id="server:"+UUID.fromString(message.get("id").getAsString());
        if(op.equals("remove")){points.remove(id);hidden.remove(id);if(tracked.equals(id))tracked="";return;}
        if(!op.equals("upsert"))throw new IllegalArgumentException("Unknown waypoint operation");
        if(!points.containsKey(id)&&points.size()>=64)throw new IllegalArgumentException("Server waypoint limit reached");
        var data=message.getAsJsonObject("waypoint").deepCopy();data.addProperty("tracked",false);data.addProperty("visible",true);
        long duration;try{duration=message.has("durationSeconds")?message.get("durationSeconds").getAsBigDecimal().longValueExact():0;}catch(RuntimeException e){throw new IllegalArgumentException("Duration must be a whole number");}
        if(duration<0||duration>604800)throw new IllegalArgumentException("Duration must be 0–604800 seconds");
        data.addProperty("expiresAt",duration==0?0:System.currentTimeMillis()+duration*1000);
        var entry=WaypointStore.parse(data,scope,id,System.currentTimeMillis(),dimension);
        if(!entry.dimension().equals(dimension))throw new IllegalArgumentException("Waypoint does not belong to current dimension");
        points.put(id,entry);
    }
    public List<WaypointStore.Entry> list(){
        points.values().removeIf(WaypointStore::expired);
        return points.values().stream().map(e->e.flag("visible",!hidden.contains(e.id())).flag("tracked",tracked.equals(e.id()))).toList();
    }
    public boolean contains(String id){return points.containsKey(id);}
    public void visible(String id,boolean value){if(value)hidden.remove(id);else hidden.add(id);}
    public void track(String id,boolean value){tracked=value?id:"";}
    public void stopTracking(){tracked="";}
}
