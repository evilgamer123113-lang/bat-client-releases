package com.batclient.accounts;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.client.util.DefaultSkinHelper;
import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Read-only skin heads. Network, local files and PNG decoding never run in a frame callback. */
public final class AccountHeads {
    private static final int LIMIT=1_048_576;
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ExecutorService WORKER=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"bat-account-heads");t.setDaemon(true);return t;});
    private static final ConcurrentHashMap<String,Cached> CACHE=new ConcurrentHashMap<>();
    private static final class Cached {volatile String png="";boolean loading;long refresh;}
    private AccountHeads() { }
    public static UUID offlineUuid(String name){return UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));}
    public static boolean premium(Session session) {
        UUID uuid=session.getUuidOrNull();String token=session.getAccessToken();
        return session.getAccountType()!=Session.AccountType.LEGACY && uuid!=null && !uuid.equals(new UUID(0,0))
                && !uuid.equals(offlineUuid(session.getUsername())) && token!=null && !token.isBlank()
                && !token.equals("0") && !token.equals("bat_session_pass");
    }
    public static String current(MinecraftClient client){Session s=client.getSession();return head(client,s.getUsername(),s.getUuidOrNull(),premium(s));}
    public static String head(MinecraftClient client,String name,UUID uuid,boolean premium) {
        if(name==null || !name.matches("[A-Za-z0-9_]{1,16}"))return "";
        UUID id=premium && uuid!=null?uuid:offlineUuid(name);
        UUID fallback=uuid!=null?uuid:id;
        String key=(premium?"msa:":"bat:")+id+":"+fallback;
        if(CACHE.size()>=128&&!CACHE.containsKey(key))CACHE.keySet().stream().findFirst().ifPresent(CACHE::remove);
        Cached cached=CACHE.computeIfAbsent(key,k->new Cached());
        synchronized(cached) {
            if(!cached.loading&&System.currentTimeMillis()>=cached.refresh) {
                cached.loading=true;WORKER.execute(()->load(client,name,id,fallback,premium,cached));
            }
        }
        return cached.png;
    }
    private static void load(MinecraftClient client,String name,UUID uuid,UUID fallback,boolean premium,Cached cached) {
        boolean found=false;
        try {
            if(cached.png.isEmpty())try {
                var texture=DefaultSkinHelper.getSkinTextures(fallback).texture();
                var resource=client.getResourceManager().getResource(texture);
                if(resource.isPresent())try(var stream=resource.get().getInputStream()){cached.png=face(stream.readNBytes(LIMIT+1));}
            }catch(Exception ignored){ }
            byte[] skin;
            if(premium)skin=mojang(uuid);
            else {
                try{skin=launcherSkin(launcherRoot(),name);if(skin!=null){cached.png=face(skin);found=true;return;}}catch(Exception ignored){skin=null;}
                if(skin==null) {
                    JsonObject result=json(get(URI.create("https://batclient.xyz/skinapi/api/player/"+uuid.toString().replace("-",""))));
                    JsonElement data=result.has("skin")&&result.get("skin").isJsonObject()?result.getAsJsonObject("skin").get("data"):null;
                    if(data==null||!data.isJsonPrimitive())throw new IOException("No Bat skin");
                    String encoded=data.getAsString();
                    if(!encoded.startsWith("data:image/png;base64,")||encoded.length()>LIMIT*2)throw new IOException("Invalid skin");
                    skin=Base64.getDecoder().decode(encoded.substring("data:image/png;base64,".length()));
                }
            }
            cached.png=face(skin);found=true;
        }catch(Exception ignored){ /* Keep native Minecraft fallback; no credentials or server bodies logged. */ }
        finally {synchronized(cached){cached.refresh=System.currentTimeMillis()+(found?60000:15000);cached.loading=false;}}
    }
    private static byte[] mojang(UUID uuid)throws Exception {
        JsonObject profile=json(get(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/"+uuid.toString().replace("-",""))));
        for(JsonElement element:profile.getAsJsonArray("properties")) {
            JsonObject property=element.getAsJsonObject();
            if(!"textures".equals(property.get("name").getAsString()))continue;
            String encoded=property.get("value").getAsString();if(encoded.length()>65536)throw new IOException("Invalid profile");
            JsonObject textures=json(Base64.getDecoder().decode(encoded)).getAsJsonObject("textures");
            URI url=URI.create(textures.getAsJsonObject("SKIN").get("url").getAsString());
            return get(officialTexture(url));
        }
        throw new IOException("No custom skin");
    }
    public static URI officialTexture(URI url)throws IOException {
        if(!"textures.minecraft.net".equals(url.getHost())||url.getUserInfo()!=null||url.getPort()!=-1
                ||!("https".equals(url.getScheme())||"http".equals(url.getScheme()))
                ||url.getQuery()!=null||url.getFragment()!=null||!url.getPath().matches("/texture/[a-fA-F0-9]{32,128}"))throw new IOException("Invalid Minecraft texture URL");
        return URI.create("https://textures.minecraft.net"+url.getRawPath());
    }
    private static JsonObject json(byte[] bytes){return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static byte[] get(URI uri)throws Exception {
        var response=HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var stream=response.body()) {
            if(response.statusCode()!=200)throw new IOException("Skin unavailable");
            byte[] bytes=stream.readNBytes(LIMIT+1);if(bytes.length>LIMIT)throw new IOException("Skin too large");return bytes;
        }
    }
    private static Path launcherRoot() {
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT),home=System.getProperty("user.home");
        if(os.contains("win")){String appData=System.getenv("APPDATA");return Path.of(appData!=null?appData:home+"/AppData/Roaming","bat-client");}
        if(os.contains("mac"))return Path.of(home,"Library","Application Support","bat-client");
        String config=System.getenv("XDG_CONFIG_HOME");return Path.of(config!=null&&!config.isBlank()?config:home+"/.config","bat-client");
    }
    /** Same selection order as the launcher: selected skinFile, then first populated skin slot. */
    public static byte[] launcherSkin(Path root,String name)throws Exception {
        Path config=root.resolve("profiles.config.json");
        if(!Files.isRegularFile(config)||Files.size(config)>2*LIMIT)return null;
        JsonObject data=json(Files.readAllBytes(config));if(!data.has("savedAccounts"))return null;
        for(JsonElement element:data.getAsJsonArray("savedAccounts")) {
            JsonObject account=element.getAsJsonObject();
            if(!account.has("username")||!name.equalsIgnoreCase(account.get("username").getAsString()))continue;
            List<String> files=new ArrayList<>();
            if(account.has("skinFile")&&!account.get("skinFile").isJsonNull())files.add(account.get("skinFile").getAsString());
            if(account.has("customSkins")&&account.get("customSkins").isJsonArray())for(JsonElement slot:account.getAsJsonArray("customSkins"))
                if(slot.isJsonObject()&&slot.getAsJsonObject().has("file")&&!slot.getAsJsonObject().get("file").isJsonNull())files.add(slot.getAsJsonObject().get("file").getAsString());
            Path skins=root.resolve("skins").toRealPath();
            for(String filename:files) {
                try {
                    Path file=skins.resolve(filename).normalize().toRealPath();
                    if(file.startsWith(skins)&&Files.isRegularFile(file)&&Files.size(file)<=LIMIT)return Files.readAllBytes(file);
                }catch(IOException|InvalidPathException ignored){ }
            }
        }
        return null;
    }
    /** Front face and transparent hat overlay, normalized to an 8x8 PNG. */
    public static String face(byte[] bytes)throws IOException {
        if(bytes.length>LIMIT)throw new IOException("Skin too large");
        BufferedImage skin;
        try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            if(input==null)throw new IOException("Invalid PNG");
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Invalid PNG");
            var reader=readers.next();
            try {
                reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(!reader.getFormatName().equalsIgnoreCase("png")||width<64||width>1024||width%64!=0||(height!=width&&height!=width/2))throw new IOException("Invalid skin dimensions");
                skin=reader.read(0);
            }finally{reader.dispose();}
        }
        int scale=skin.getWidth()/64;
        BufferedImage face=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        var graphics=face.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(skin,0,0,8,8,8*scale,8*scale,16*scale,16*scale,null);
            boolean hat=true;
            // Legacy skins commonly have opaque unused hat pixels; vanilla clears them.
            if(skin.getHeight()==skin.getWidth()/2) {
                hat=false;
                outer:for(int y=0;y<32*scale;y++)for(int x=32*scale;x<64*scale;x++)if((skin.getRGB(x,y)>>>24)<128){hat=true;break outer;}
            }
            if(hat){graphics.setComposite(AlphaComposite.SrcOver);graphics.drawImage(skin,0,0,8,8,40*scale,8*scale,48*scale,16*scale,null);}
        }finally{graphics.dispose();}
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(face,"png",out);
        return "data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
