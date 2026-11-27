package com.batclient.accounts;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/** Browser OAuth with PKCE. No passwords, token files, or raw response logging. */
public final class MicrosoftLogin {
    private static final String CLIENT="00000000402b5328";
    private static final String REDIRECT="https://login.live.com/oauth20_desktop.srf";
    private static final String SCOPE="XboxLive.signin offline_access";
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public record Pending(String verifier,String state,long started,String url) { }
    public record Identity(String name,UUID uuid,String token,String xuid,long expires) { }
    public static final class Failure extends IllegalStateException {
        private Failure(String message){super(message);}
    }
    @FunctionalInterface public interface Transport {
        JsonObject request(String url,String contentType,String body,String bearer) throws Exception;
    }
    private MicrosoftLogin() { }
    private static String random(){byte[] b=new byte[48];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    public static Pending begin() {
        try {
            String verifier=random(),state=random();
            String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            String url="https://login.live.com/oauth20_authorize.srf?"+form(Map.of("client_id",CLIENT,"response_type","code","redirect_uri",REDIRECT,"scope",SCOPE,"code_challenge",challenge,"code_challenge_method","S256","state",state,"prompt","select_account"));
            return new Pending(verifier,state,System.currentTimeMillis(),url);
        }catch(GeneralSecurityException e){throw new Failure("Could not start sign-in.");}
    }
    public static String code(Pending pending,String redirected) {
        if(pending==null || System.currentTimeMillis()-pending.started()>600_000)throw new IllegalArgumentException("Sign-in expired. Start Microsoft sign-in again.");
        try {
            URI uri=URI.create(redirected.trim());
            if(!"https".equals(uri.getScheme()) || !"login.live.com".equals(uri.getHost()) || !"/oauth20_desktop.srf".equals(uri.getPath()) || uri.getUserInfo()!=null || uri.getPort()!=-1)
                throw new IllegalArgumentException();
            Map<String,String> query=new HashMap<>();
            for(String pair:Objects.requireNonNullElse(uri.getRawQuery(),"").split("&")) {
                String[] parts=pair.split("=",2);
                if(parts.length==2 && query.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8))!=null)throw new IllegalArgumentException();
            }
            if(!pending.state().equals(query.get("state")) || !query.containsKey("code") || query.get("code").isBlank() || query.containsKey("error"))throw new IllegalArgumentException();
            return query.get("code");
        }catch(RuntimeException e){throw new IllegalArgumentException("Paste the complete final Microsoft address from this sign-in attempt.");}
    }
    public static Identity finish(Pending pending,String redirected) throws Exception {return finish(pending,redirected,MicrosoftLogin::request);}
    public static Identity finish(Pending pending,String redirected,Transport transport) throws Exception {
        String code=code(pending,redirected);
        JsonObject msa=transport.request("https://login.live.com/oauth20_token.srf","application/x-www-form-urlencoded",
                form(Map.of("client_id",CLIENT,"grant_type","authorization_code","code",code,"redirect_uri",REDIRECT,"code_verifier",pending.verifier(),"scope",SCOPE)),null);
        JsonObject xbox=json("{\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\",\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\"}}");
        xbox.getAsJsonObject("Properties").addProperty("RpsTicket","d="+required(msa,"access_token"));
        JsonObject xbl=transport.request("https://user.auth.xboxlive.com/user/authenticate","application/json",xbox.toString(),null);
        JsonObject xsts=json("{\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\",\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[]}}");
        xsts.getAsJsonObject("Properties").getAsJsonArray("UserTokens").add(required(xbl,"Token"));
        JsonObject authorized=transport.request("https://xsts.auth.xboxlive.com/xsts/authorize","application/json",xsts.toString(),null);
        if(authorized.has("XErr"))throw new Failure("Xbox sign-in requires account setup, permission or age verification at xbox.com.");
        JsonObject xui=authorized.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject();
        JsonObject login=new JsonObject();login.addProperty("identityToken","XBL3.0 x="+required(xui,"uhs")+";"+required(authorized,"Token"));
        JsonObject minecraft=transport.request("https://api.minecraftservices.com/authentication/login_with_xbox","application/json",login.toString(),null);
        String token=required(minecraft,"access_token");
        JsonObject profile=transport.request("https://api.minecraftservices.com/minecraft/profile",null,null,token);
        String id=required(profile,"id"),name=required(profile,"name");
        if(!id.matches("[0-9a-fA-F]{32}") || !name.matches("[A-Za-z0-9_]{1,16}"))throw new Failure("Microsoft did not return a valid Minecraft Java profile.");
        UUID uuid=UUID.fromString(id.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})","$1-$2-$3-$4-$5"));
        long seconds=minecraft.has("expires_in")?minecraft.get("expires_in").getAsLong():86400;
        return new Identity(name,uuid,token,xui.has("xid")?xui.get("xid").getAsString():"",System.currentTimeMillis()+Math.max(0,Math.min(seconds,86400))*1000);
    }
    private static String required(JsonObject object,String key){if(!object.has(key)||object.get(key).getAsString().isBlank())throw new Failure("Sign-in service returned an incomplete response.");return object.get(key).getAsString();}
    private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    private static String form(Map<String,String> values){return values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(Collectors.joining("&"));}
    private static JsonObject request(String url,String contentType,String body,String bearer) throws Exception {
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25)).header("Accept","application/json");
        if(bearer!=null)builder.header("Authorization","Bearer "+bearer);
        if(body!=null)builder.header("Content-Type",contentType).POST(HttpRequest.BodyPublishers.ofString(body));else builder.GET();
        HttpResponse<java.io.InputStream> response=HTTP.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var stream=response.body()) {
            if(response.statusCode()<200 || response.statusCode()>=300)throw new Failure(response.statusCode()==404?"This Microsoft account has no Minecraft Java profile.":"Sign-in was rejected by the account service (HTTP "+response.statusCode()+").");
            byte[] bytes=stream.readNBytes(1_048_577);if(bytes.length>1_048_576)throw new Failure("Sign-in response was too large.");
            return json(new String(bytes,StandardCharsets.UTF_8));
        }
    }
}
