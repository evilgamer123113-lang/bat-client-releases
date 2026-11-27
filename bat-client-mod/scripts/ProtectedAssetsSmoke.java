import com.batclient.web.WebAssetManager;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ProtectedAssetsSmoke {
    public static void main(String[] args)throws Exception {
        WebAssetsSmoke.main(args);
        String root="/assets/batclient/web/";
        String js=new String(WebAssetManager.read("app.js"),StandardCharsets.UTF_8);
        if(!js.contains("batPlayerHead")||js.contains("sourceMappingURL"))throw new AssertionError("Incorrect decrypted UI");
        if(WebAssetManager.class.getResourceAsStream(root+"app.js")!=null)throw new AssertionError("Plaintext JS bundled");
        byte[] encrypted;
        try(var stream=WebAssetManager.class.getResourceAsStream(root+"app.js.bin")){encrypted=stream.readAllBytes();}
        if(!new String(encrypted,0,4,StandardCharsets.US_ASCII).equals("BAT2"))throw new AssertionError("Missing envelope");
        Method decrypt=WebAssetManager.class.getDeclaredMethod("decrypt",String.class,byte[].class);decrypt.setAccessible(true);
        byte[] original=(byte[])decrypt.invoke(null,"app.js",encrypted);
        if(!Arrays.equals(original,WebAssetManager.read("app.js")))throw new AssertionError("Decrypt mismatch");
        byte[] changed=encrypted.clone();changed[changed.length-1]^=1;
        reject(decrypt,"app.js",changed);reject(decrypt,"overlay.html",encrypted);
        System.out.println("PROTECTED_ASSETS_OK: decrypt, no plaintext, tamper rejection, asset-swap rejection");
    }
    static void reject(Method decrypt,String name,byte[] data)throws Exception {
        try{decrypt.invoke(null,name,data);throw new AssertionError("Unauthenticated asset accepted");}
        catch(InvocationTargetException expected){if(!(expected.getCause() instanceof IllegalStateException))throw expected;}
    }
}
