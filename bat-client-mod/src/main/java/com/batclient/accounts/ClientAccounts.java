package com.batclient.accounts;

import com.batclient.ClientAccountAccess;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.batclient.platform.Platforms;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.util.Util;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Offline names persist; authenticated sessions and OAuth secrets stay in memory. */
public final class ClientAccounts {
    private record Entry(String id,String name,String kind,AccountSession account,long expires) { }
    private static final Map<String,Entry> ACCOUNTS=new LinkedHashMap<>();
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"bat-account-login");t.setDaemon(true);return t;});
    private static final Path FILE=Platforms.get().getConfigDir().resolve("batclient-accounts.json");
    private static boolean initialized,busy;
    private static String active="launch",message="",phase="idle";
    private static MicrosoftLogin.Pending pending;
    private static int generation;
    private ClientAccounts() { }
    private static boolean connected(MinecraftClient client){return client.world!=null || client.getNetworkHandler()!=null || client.getServer()!=null;}
    private static void init(MinecraftClient client) {
        if(initialized)return;initialized=true;
        AccountSession original=((ClientAccountAccess)client).bat$captureAccount();
        ACCOUNTS.put("launch",new Entry("launch",original.session().getUsername(),"Launcher",original,Long.MAX_VALUE));
        try {
            if(Files.exists(FILE) && Files.size(FILE)<32768) {
                JsonArray names=JsonParser.parseString(Files.readString(FILE)).getAsJsonArray();
                for(JsonElement name:names){String text=name.getAsString();if(valid(text) && ACCOUNTS.size()<65)addOffline(text);}
            }
        }catch(Exception ignored){message="Could not load saved offline accounts.";}
    }
    private static boolean valid(String name){return name!=null && name.matches("[A-Za-z0-9_]{3,16}");}
    private static String addOffline(String name) {
        UUID uuid=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
        String id="offline:"+uuid;
        Session session=new Session(name,uuid,"0",Optional.empty(),Optional.empty(),Session.AccountType.LEGACY);
        AccountSession account=new AccountSession(session,UserApiService.OFFLINE,CompletableFuture.completedFuture(new ProfileResult(new GameProfile(uuid,name))),CompletableFuture.completedFuture(UserApiService.OFFLINE_PROPERTIES));
        ACCOUNTS.put(id,new Entry(id,name,"Offline",account,Long.MAX_VALUE));return id;
    }
    private static void persist() throws Exception {
        JsonArray names=new JsonArray();ACCOUNTS.values().stream().filter(e->e.kind.equals("Offline")).forEach(e->names.add(e.name));
        Files.createDirectories(FILE.getParent());Path temporary=Files.createTempFile(FILE.getParent(),"bat-accounts-",".tmp");
        try {Files.writeString(temporary,names.toString(),StandardCharsets.UTF_8);try{Files.move(temporary,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,FILE,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temporary);}
    }
    public static synchronized String action(MinecraftClient client,String action,String value) {
        init(client);value=Objects.requireNonNullElse(value,"");
        if(value.length()>8192)return state(client);
        try {
            if(action.equals("list"))return state(client);
            if(action.equals("cancel")){generation++;pending=null;busy=false;phase="idle";message="Sign-in cancelled.";return state(client);}
            if(busy){message="Please wait for the current account operation.";return state(client);}
            switch(action) {
                case "offline" -> {
                    if(!valid(value))throw new IllegalArgumentException("Use 3–16 letters, numbers or underscores.");
                    if(ACCOUNTS.size()>=65)throw new IllegalArgumentException("Remove an account before adding another.");
                    addOffline(value);persist();message="Offline account added. Select Switch to use it.";
                }
                case "remove" -> {
                    if(value.equals("launch"))throw new IllegalArgumentException("Your launcher account stays available.");
                    if(value.equals(active)) {
                        if(connected(client))throw new IllegalArgumentException("Return to the main menu before removing your active account.");
                        ((ClientAccountAccess)client).bat$applyAccount(ACCOUNTS.get("launch").account);active="launch";
                    }
                    ACCOUNTS.remove(value);persist();message="Account removed.";
                }
                case "switch" -> {
                    if(connected(client))throw new IllegalArgumentException("Return to the main menu before switching accounts.");
                    Entry entry=ACCOUNTS.get(value);
                    if(entry==null)throw new IllegalArgumentException("Account not found.");
                    if(entry.expires<System.currentTimeMillis()+60000)throw new IllegalArgumentException("This Microsoft session expired. Sign in again.");
                    ((ClientAccountAccess)client).bat$applyAccount(entry.account);active=entry.id;
                    message="Now playing as "+entry.name+".";
                }
                case "microsoft" -> {
                    if(ACCOUNTS.size()>=65)throw new IllegalArgumentException("Remove an account before adding another.");
                    generation++;pending=MicrosoftLogin.begin();phase="browser";
                    message="Sign in in your browser, then paste its final Microsoft address below.";
                    Util.getOperatingSystem().open(java.net.URI.create(pending.url()));
                }
                case "finish" -> {
                    MicrosoftLogin.code(pending,value); // Reject wrong/expired URLs before dispatch.
                    MicrosoftLogin.Pending attempt=pending;pending=null;busy=true;phase="working";
                    message="Verifying your Minecraft account…";
                    int ticket=generation;String redirected=value;
                    WORKER.execute(()->complete(client,attempt,redirected,ticket));
                }
                default -> throw new IllegalArgumentException("Unknown account action.");
            }
        }catch(IllegalArgumentException e){message=e.getMessage();}
        catch(Exception e){message="The account operation failed. Please try again.";}
        return state(client);
    }
    private static void complete(MinecraftClient client,MicrosoftLogin.Pending attempt,String redirected,int ticket) {
        try {
            MicrosoftLogin.Identity identity=MicrosoftLogin.finish(attempt,redirected);
            // User API must use production Microsoft services, not launcher-provided offline credentials.
            UserApiService api=new YggdrasilAuthenticationService(Proxy.NO_PROXY).createUserApiService(identity.token());
            UserApiService.UserProperties properties=api.fetchProperties();
            ProfileResult result=client.getSessionService().fetchProfile(identity.uuid(),true);
            if(result==null)throw new IllegalStateException("Minecraft could not verify your profile. Try again.");
            Session session=new Session(identity.name(),identity.uuid(),identity.token(),identity.xuid().isEmpty()?Optional.empty():Optional.of(identity.xuid()),Optional.empty(),Session.AccountType.MSA);
            AccountSession account=new AccountSession(session,api,CompletableFuture.completedFuture(result),CompletableFuture.completedFuture(properties));
            synchronized(ClientAccounts.class) {
                if(ticket!=generation)return;
                String id="microsoft:"+identity.uuid();ACCOUNTS.put(id,new Entry(id,identity.name(),"Microsoft",account,identity.expires()));
                busy=false;phase="idle";message="Signed in as "+identity.name()+". Select Switch to use it.";
            }
        }catch(Exception e) {
            synchronized(ClientAccounts.class) {
                if(ticket!=generation)return;
                busy=false;phase="idle";
                // Known messages are deliberately sanitized; never return raw network responses/URLs.
                message=e instanceof MicrosoftLogin.Failure?e.getMessage():"Sign-in failed. Check your connection and start Microsoft sign-in again.";
            }
        }
    }
    private static String state(MinecraftClient client) {
        JsonObject state=new JsonObject();state.addProperty("active",active);state.addProperty("current",client.getSession().getUsername());
        state.addProperty("busy",busy);state.addProperty("connected",connected(client));state.addProperty("message",message);state.addProperty("phase",phase);
        state.addProperty("currentHead",AccountHeads.current(client));
        JsonArray accounts=new JsonArray();
        for(Entry entry:ACCOUNTS.values()) {
            JsonObject row=new JsonObject();row.addProperty("id",entry.id);row.addProperty("name",entry.name);row.addProperty("kind",entry.kind);
            row.addProperty("expired",entry.expires<System.currentTimeMillis()+60000);accounts.add(row);
            row.addProperty("head",AccountHeads.head(client,entry.name,entry.account.session().getUuidOrNull(),entry.kind.equals("Microsoft")||(entry.kind.equals("Launcher")&&AccountHeads.premium(entry.account.session()))));
        }
        state.add("accounts",accounts);return state.toString();
    }
}
