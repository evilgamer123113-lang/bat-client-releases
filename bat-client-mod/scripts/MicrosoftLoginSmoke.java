import com.batclient.accounts.MicrosoftLogin;
import com.google.gson.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MicrosoftLoginSmoke {
    static int checks;
    static void check(boolean ok){if(!ok)throw new AssertionError("check "+checks);checks++;}
    static String redirect(MicrosoftLogin.Pending p){return "https://login.live.com/oauth20_desktop.srf?code=test-code&state="+p.state();}
    static void reject(MicrosoftLogin.Pending p,String url){try{MicrosoftLogin.code(p,url);throw new AssertionError("URL accepted");}catch(IllegalArgumentException expected){checks++;}}
    static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    public static void main(String[] args)throws Exception {
        var p=MicrosoftLogin.begin();var other=MicrosoftLogin.begin();
        check(!p.state().equals(other.state())&&!p.verifier().equals(other.verifier()));
        check(p.url().startsWith("https://login.live.com/")&&p.url().contains("code_challenge_method=S256")&&!p.url().contains(p.verifier()));
        check(MicrosoftLogin.code(p,redirect(p)).equals("test-code"));
        reject(p,redirect(other));reject(p,redirect(p).replace("login.live.com","login.live.com.evil.test"));
        reject(p,redirect(p).replace("https:","http:"));reject(p,redirect(p)+"&state="+p.state());
        reject(p,"https://login.live.com@evil.test/oauth20_desktop.srf?code=x&state="+p.state());
        reject(p,redirect(p)+"&error=access_denied");
        reject(new MicrosoftLogin.Pending(p.verifier(),p.state(),0,p.url()),redirect(p));
        List<String> urls=new ArrayList<>();
        var result=MicrosoftLogin.finish(p,redirect(p),(url,content,body,bearer)->{
            urls.add(url);
            if(url.endsWith("oauth20_token.srf")){check(body.contains("code_verifier="+p.verifier())&&body.contains("test-code"));return json("{\"access_token\":\"msa-test\"}");}
            if(url.endsWith("/user/authenticate")){check(json(body).getAsJsonObject("Properties").get("RpsTicket").getAsString().equals("d=msa-test"));return json("{\"Token\":\"xbl-test\"}");}
            if(url.endsWith("/xsts/authorize"))return json("{\"Token\":\"xsts-test\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"hash\",\"xid\":\"123\"}]}}");
            if(url.endsWith("/login_with_xbox")){check(json(body).get("identityToken").getAsString().equals("XBL3.0 x=hash;xsts-test"));return json("{\"access_token\":\"mc-test\",\"expires_in\":3600}");}
            check("mc-test".equals(bearer));return json("{\"id\":\"0123456789abcdef0123456789abcdef\",\"name\":\"AccountSmoke\"}");
        });
        check(urls.size()==5&&result.name().equals("AccountSmoke")&&result.token().equals("mc-test")&&result.expires()>System.currentTimeMillis());
        check(result.uuid().equals(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")));
        System.out.println("MICROSOFT_LOGIN_PROTOCOL_OK: "+checks+" checks (mock transport; no live login)");
    }
}
