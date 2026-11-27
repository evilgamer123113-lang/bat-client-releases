import com.batclient.web.BatWebFileSystem;
import com.batclient.web.WebAssetManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class WebAssetMemorySmoke {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path game = Path.of(System.getProperty("bat.test.game")).toAbsolutePath();
        Files.createDirectories(game);
        Path root = WebAssetManager.webRoot();
        var fs = new BatWebFileSystem();
        check(!Files.exists(root), "virtual root must not exist");
        for (String asset : new String[]{"index.html", "overlay.html", "pause.html", "app.js", "style.css", "brand.html", "list-icons.html", "bg.png"}) {
            String uri = root.resolve(asset).toUri().toString();
            String stripped = uri.replaceFirst("^file:///+", "");
            String linuxStripped = "home/crafter/.config/bat-client/minecraft_runtime/.batclient-ui/" + asset;
            String encodedStripped = "home/crafter/My%20Games/bat+client/.batclient-ui/" + asset;
            check(fs.exists(uri), "asset URI missing: " + asset);
            check(fs.exists(stripped), "stripped URI missing: " + asset);
            check(fs.exists(linuxStripped), "Linux stripped URI missing: " + asset);
            check(fs.exists(encodedStripped), "encoded stripped URI missing: " + asset);
            check(fs.exists(asset), "relative asset missing: " + asset);
            check(fs.read(uri).length > 0, "empty asset: " + asset);
            check(Arrays.equals(fs.read(uri), fs.read(asset)), "relative/absolute mismatch");
            check(Arrays.equals(fs.read(uri), fs.read(linuxStripped)), "Linux stripped content mismatch: " + asset);
        }
        check("text/html".equals(fs.getMimeType("index.html")), "html mime mismatch");
        check("text/css".equals(fs.getMimeType("style.css")), "css mime mismatch");
        check("application/javascript".equals(fs.getMimeType("app.js")), "js mime mismatch");
        check("image/png".equals(fs.getMimeType("bg.png")), "png mime mismatch");
        check(!fs.exists("home/crafter/.config/bat-client/minecraft_runtime/.batclient-ui/../options.txt"), "Linux stripped traversal accepted");
        // Disk files placed at the virtual root cannot override bundled code.
        Files.createDirectories(root);
        Files.writeString(root.resolve("app.js"), "UNTRUSTED DISK OVERRIDE");
        check(!new String(fs.read("app.js")).contains("UNTRUSTED DISK OVERRIDE"), "disk code override accepted");
        check(!fs.exists("../options.txt"), "relative traversal accepted");
        check(!fs.exists(root.resolve("../options.txt").toUri().toString()), "URI traversal accepted");
        check(!fs.exists("unknown.js"), "unknown asset accepted");
        Path shots = game.resolve("screenshots");
        Files.createDirectories(shots);
        Files.write(shots.resolve("test shot.png"), new byte[]{1,2,3});
        String shot = root.resolve("screenshots/test shot.png").toUri().toString();
        check(Arrays.equals(fs.read(shot), new byte[]{1,2,3}), "screenshot alias/escaped filename failed");
        check(!fs.exists("screenshots/../../options.txt"), "screenshot traversal accepted");
        check(!Files.exists(game.resolve("batclient-web")), "readable cache extracted");
        System.out.println("WEB_ASSETS_IN_MEMORY_OK");
    }
}
