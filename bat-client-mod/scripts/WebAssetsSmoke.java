import com.batclient.web.WebAssetManager;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Exercise actual in-memory asset allowlist, not unrestricted file:// loading. */
public class WebAssetsSmoke {
    public static void main(String[] args) {
        int checked = 0;
        var pattern = Pattern.compile("<(?:script|link|img)\\b[^>]*?\\b(?:src|href)=[\"']([^\"']+)",Pattern.CASE_INSENSITIVE);
        for (String page : new String[]{"index.html","overlay.html","pause.html","respacks.html","brand.html","list-icons.html"}) {
            String html = new String(WebAssetManager.read(page),StandardCharsets.UTF_8);
            var matches = pattern.matcher(html);
            while (matches.find()) {
                String name = matches.group(1);
                if (name.contains(":") || name.startsWith("#") || name.startsWith("//")) continue;
                if (!WebAssetManager.contains(name)) throw new AssertionError(page+" cannot serve "+name);
                if (WebAssetManager.read(name).length == 0) throw new AssertionError("Empty asset "+name);
                checked++;
            }
        }
        if (!WebAssetManager.contains("client-tools.js")) throw new AssertionError("Managers script blocked");
        System.out.println("WEB_ASSET_ALLOWLIST_OK: "+checked+" referenced assets readable through production asset manager");
    }
}
