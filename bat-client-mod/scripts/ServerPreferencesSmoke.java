import com.batclient.ServerPreferences;
import com.google.gson.JsonObject;

public class ServerPreferencesSmoke {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        String address = "Example.COM:25565";
        ServerPreferences.toggle(address, "favorite");
        ServerPreferences.toggle(address, "collapsed");
        JsonObject geo = new JsonObject(); geo.addProperty("country", "Test country");
        JsonObject patch = new JsonObject(); patch.add("geo", geo); patch.addProperty("geoTime", 12345);
        ServerPreferences.put(address, patch);
        var data = ServerPreferences.class.getDeclaredField("data"); data.setAccessible(true); data.set(null, null);
        check(ServerPreferences.flag("example.com:25565", "favorite"), "favorite persists across reload");
        check(ServerPreferences.flag("example.com:25565", "collapsed"), "MOTD preference persists across reload");
        check(ServerPreferences.get(address).getAsJsonObject("geo").get("country").getAsString().equals("Test country"), "location cache preserves preferences");
        ServerPreferences.toggle(address, "favorite");
        data.set(null, null);
        check(!ServerPreferences.flag(address, "favorite") && ServerPreferences.flag(address, "collapsed"), "toggle does not change other settings");
        System.out.println("SERVER_PREFERENCES_DISK_OK");
    }
}
