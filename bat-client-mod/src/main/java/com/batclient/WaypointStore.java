package com.batclient;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local-only versioned storage. Transactions reach disk before replacing the in-memory snapshot. */
public final class WaypointStore {
    public static final int PER_WORLD=256, TOTAL=4096;
    public record Entry(String id,String scope,String dimension,String name,String group,int x,int y,int z,String color,boolean visible,boolean favorite,boolean tracked,long created,boolean beam,boolean chroma,int opacity,long expiresAt){
        public JsonObject json(){var j=new JsonObject();j.addProperty("id",id);j.addProperty("scope",scope);j.addProperty("dimension",dimension);j.addProperty("name",name);j.addProperty("group",group);j.addProperty("x",x);j.addProperty("y",y);j.addProperty("z",z);j.addProperty("color",color);j.addProperty("visible",visible);j.addProperty("favorite",favorite);j.addProperty("tracked",tracked);j.addProperty("created",created);j.addProperty("beam",beam);j.addProperty("chroma",chroma);j.addProperty("opacity",opacity);j.addProperty("expiresAt",expiresAt);return j;}
        public Entry flag(String key,boolean value){return new Entry(id,scope,dimension,name,group,x,y,z,color,key.equals("visible")?value:visible,key.equals("favorite")?value:favorite,key.equals("tracked")?value:tracked,created,beam,chroma,opacity,expiresAt);}
    }
    private final Path file;
    private List<Entry> entries=List.of();
    private boolean readOnly;
    private String notice="";
    public WaypointStore(Path file){this.file=file;load();}
    public synchronized List<Entry> list(String scope){return entries.stream().filter(e->e.scope().equals(scope)&&!expired(e)).toList();}
    public synchronized String notice(){return notice;}
    public synchronized boolean readOnly(){return readOnly;}
    private static String text(JsonObject j,String key,String fallback){return j.has(key)&&j.get(key).isJsonPrimitive()?j.get(key).getAsString():fallback;}
    public static String label(String value,int max,String what){String clean=value==null?"":value.strip();if(clean.isEmpty()||clean.length()>max||clean.chars().anyMatch(c->Character.isISOControl(c)||c==0xA7))throw new IllegalArgumentException(what+" must contain 1–"+max+" plain characters.");return clean;}
    public static String dimension(String value){if(value==null||value.length()>128||!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Use a dimension ID such as minecraft:overworld.");return value;}
    private static int coord(JsonObject j,String key,int min,int max){try{var value=j.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new Exception();var n=value.getAsBigDecimal();int result=n.intValueExact();if(result<min||result>max)throw new Exception();return result;}catch(Exception e){throw new IllegalArgumentException(key.toUpperCase(Locale.ROOT)+" must be a whole number between "+min+" and "+max+".");}}
    private static boolean bool(JsonObject j,String key,boolean fallback){return j.has(key)&&j.get(key).isJsonPrimitive()&&j.getAsJsonPrimitive(key).isBoolean()?j.get(key).getAsBoolean():fallback;}
    public static boolean expired(Entry e){return e.expiresAt()>0&&e.expiresAt()<=System.currentTimeMillis();}
    public static Entry parse(JsonObject j,String scope,String id,long created,String fallbackDim){
        String color=text(j,"color","#75CFFF").toUpperCase(Locale.ROOT);if(!color.matches("#[0-9A-F]{6}"))throw new IllegalArgumentException("Choose a six-digit RGB color.");
        return new Entry(id,scope,dimension(text(j,"dimension",fallbackDim)),label(text(j,"name","Waypoint"),48,"Name"),label(text(j,"group","General"),24,"Group"),coord(j,"x",-29999984,29999984),coord(j,"y",-2048,4096),coord(j,"z",-29999984,29999984),color,bool(j,"visible",true),bool(j,"favorite",false),bool(j,"tracked",false),created,bool(j,"beam",true),bool(j,"chroma",false),j.has("opacity")?coord(j,"opacity",0,100):100,expiry(j));
    }
    private static long expiry(JsonObject j){if(!j.has("expiresAt"))return 0;try{long v=j.get("expiresAt").getAsBigDecimal().longValueExact();if(v<0)throw new Exception();return v;}catch(Exception e){throw new IllegalArgumentException("Invalid waypoint expiry.");}}
    public synchronized Entry save(String scope,JsonObject data,String currentDimension)throws java.io.IOException{
        if(scope==null||scope.isEmpty())throw new IllegalArgumentException("Join a world or server first.");
        String id=text(data,"id","");Entry old=null;
        if(!id.isEmpty()){String selected=id;old=list(scope).stream().filter(e->e.id().equals(selected)).findFirst().orElseThrow(()->new IllegalArgumentException("That waypoint is no longer available."));}
        else{if(list(scope).size()>=PER_WORLD||entries.stream().filter(e->!expired(e)).count()>=TOTAL)throw new IllegalArgumentException("Waypoint limit reached (256 per world, 4096 total).");id=UUID.randomUUID().toString();}
        Entry value=parse(data,scope,id,old==null?System.currentTimeMillis():old.created(),currentDimension);
        var next=new ArrayList<>(entries.stream().filter(e->!expired(e)).toList());next.removeIf(e->e.scope().equals(scope)&&e.id().equals(value.id()));
        if(value.tracked())next.replaceAll(e->e.scope().equals(scope)?e.flag("tracked",false):e);
        next.add(value);commit(next);return value;
    }
    public synchronized void remove(String scope,String id)throws java.io.IOException{var next=new ArrayList<>(entries);if(!next.removeIf(e->e.scope().equals(scope)&&e.id().equals(id)))throw new IllegalArgumentException("Waypoint not found.");commit(next);}
    public synchronized void flag(String scope,String id,String key,boolean value)throws java.io.IOException{
        if(!Set.of("visible","favorite","tracked").contains(key))throw new IllegalArgumentException("Unknown waypoint preference.");
        if(list(scope).stream().noneMatch(e->e.id().equals(id)))throw new IllegalArgumentException("Waypoint not found.");
        var next=entries.stream().map(e->!e.scope().equals(scope)?e:e.id().equals(id)?e.flag(key,value):key.equals("tracked")&&value?e.flag("tracked",false):e).toList();commit(next);
    }
    public synchronized void group(String scope,String group,boolean visible)throws java.io.IOException{commit(entries.stream().map(e->e.scope().equals(scope)&&e.group().equals(group)?e.flag("visible",visible):e).toList());}
    private List<Entry> read(Path path)throws java.io.IOException{
        if(Files.size(path)>4*1024*1024)throw new IllegalArgumentException("Waypoint file exceeds the safe size limit.");
        var root=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();if(root.get("version").getAsInt()!=1)throw new IllegalArgumentException("Unsupported waypoint file version.");
        var result=new ArrayList<Entry>();var ids=new HashSet<String>();var counts=new HashMap<String,Integer>();
        for(var item:root.getAsJsonArray("waypoints")){var j=item.getAsJsonObject();String scope=text(j,"scope","");if(scope.isEmpty()||scope.length()>4096||scope.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid world identity.");String id=UUID.fromString(text(j,"id","")).toString();if(!ids.add(id)||result.size()>=TOTAL||counts.merge(scope,1,Integer::sum)>PER_WORLD)throw new IllegalArgumentException("Duplicate IDs or waypoint limit exceeded.");result.add(parse(j,scope,id,j.has("created")?j.get("created").getAsLong():0,"minecraft:overworld"));}return List.copyOf(result);
    }
    private void load(){if(!Files.exists(file))return;try{entries=read(file);}catch(Exception bad){
        try{entries=read(file.resolveSibling(file.getFileName()+".bak"));notice="Recovered waypoints from the local backup. Original file was unreadable.";}
        catch(Exception ignored){readOnly=true;notice="Waypoint file could not be read. It has been preserved; saving is disabled to protect your locations.";}
    }}
    private void commit(List<Entry> next)throws java.io.IOException{
        if(readOnly)throw new IllegalArgumentException(notice);
        var root=new JsonObject();root.addProperty("version",1);var list=new JsonArray();next.forEach(e->list.add(e.json()));root.add("waypoints",list);
        Files.createDirectories(file.toAbsolutePath().getParent());Path temp=file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(root),StandardCharsets.UTF_8);
        if(Files.isRegularFile(file)&&notice.isEmpty())Files.copy(file,file.resolveSibling(file.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
        try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        entries=List.copyOf(next);notice="";
    }
}
