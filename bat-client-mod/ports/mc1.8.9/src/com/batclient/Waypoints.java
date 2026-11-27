package com.batclient;

import com.batclient.platform.ClientHookServices;
import com.batclient.platform.Platforms;
import com.batclient.platform.mc189.GLFW;
import com.batclient.web.BatWebClipboard;
import com.batclient.web.WebScreen;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Client-thread controller (1.8.9). No marker data is sent to a server. */
public final class Waypoints {
    private static final WaypointStore STORE = new WaypointStore(Platforms.get().getConfigDir().resolve("batclient-waypoints.json"));
    private static String scope = "", dimension = "", label = "", arrived = "";
    private static Object world;
    private static List<WaypointStore.Entry> current = Collections.emptyList();
    private static boolean dirty = true, pendingOpen, pendingNew;
    private static int age, openTicks;
    private static boolean wasDead;
    private static JsonObject draft;
    private static final ServerWaypointStore SERVER = new ServerWaypointStore();

    private Waypoints() {}

    public static void init() {
        BatKeybinds.register("waypoint_add", ClientHookServices.get().registerKeyBinding(new KeyBinding("key.batclient.waypoint_add", GLFW.GLFW_KEY_N, "category.batclient.title")));
        BatKeybinds.register("waypoint_manager", ClientHookServices.get().registerKeyBinding(new KeyBinding("key.batclient.waypoint_manager", GLFW.GLFW_KEY_M, "category.batclient.title")));
        WaypointRenderer.init();
        WaypointNetwork.init();
    }

    private static void refresh(MinecraftClient c) {
        if (c.world != world) {
            world = c.world;
            dirty = true;
            arrived = "";
            SERVER.clear();
            wasDead = false;
            if (c.world == null || c.player == null) {
                scope = dimension = label = "";
                current = Collections.emptyList();
                world = null;
                return;
            }
            dimension = c.world.dimension != null ? c.world.dimension.getName() : "overworld";
            if (c.getServer() != null) {
                scope = "save:" + c.getServer().getRunDirectory().toPath().toAbsolutePath().normalize() + ":" + c.getServer().getLevelName();
                label = c.getServer().getLevelName();
            } else if (c.getCurrentServerEntry() != null) {
                label = ServerProfiles.normalize(c.getCurrentServerEntry().address);
                scope = "server:" + label;
            } else {
                net.minecraft.client.network.ClientPlayNetworkHandler network = c.getNetworkHandler();
                label = network == null ? "" : String.valueOf(network.getClientConnection().getAddress());
                scope = label.isEmpty() ? "" : "server:" + label;
            }
        }
        if (dirty) {
            current = STORE.list(scope);
            dirty = false;
        }
    }

    public static List<WaypointStore.Entry> current() {
        List<WaypointStore.Entry> list = new ArrayList<>();
        for (WaypointStore.Entry e : current) {
            if (!WaypointStore.expired(e)) list.add(e);
        }
        if (HudConfig.bool(VisualModules.settings("waypoints"), "serverMarkers", true)) {
            list.addAll(SERVER.list());
        }
        return list;
    }

    public static void receive(JsonObject message, MinecraftClient c) {
        refresh(c);
        if (c.world == null || scope.isEmpty()) return;
        SERVER.accept(message, scope, dimension);
    }

    public static void chatDraft(int x, int y, int z) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (!VisualModules.enabled("waypoints") || !HudConfig.bool(VisualModules.settings("waypoints"), "chat", true) || c.player == null) return;
        draft = new JsonObject();
        draft.addProperty("x", x);
        draft.addProperty("y", y);
        draft.addProperty("z", z);
        BatClientMod.openWaypoints(c);
        request(true);
    }

    private static void death(MinecraftClient c) {
        boolean dead = c.player.getHealth() <= 0f;
        if (dead && !wasDead && VisualModules.enabled("waypoints") && HudConfig.bool(VisualModules.settings("waypoints"), "death", true)) {
            JsonObject data = new JsonObject();
            data.addProperty("name", "Death " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            data.addProperty("group", "Deaths");
            data.addProperty("x", MathHelper.floor(c.player.x));
            data.addProperty("y", MathHelper.floor(c.player.y));
            data.addProperty("z", MathHelper.floor(c.player.z));
            data.addProperty("color", "#FF6666");
            data.addProperty("expiresAt", System.currentTimeMillis() + 86400000L);
            try {
                STORE.save(scope, data, dimension);
                dirty = true;
            } catch (Exception e) {
                BatClientMod.LOGGER.warn("[waypoints] Could not save death location: {}", e.toString());
            }
        }
        wasDead = dead;
    }

    public static String dimension() { return dimension; }

    public static void request(boolean create) {
        pendingOpen = true;
        pendingNew = create;
        openTicks = 0;
    }

    public static void tick(MinecraftClient c) {
        refresh(c);
        age++;
        boolean add = BatKeybinds.pressed(c, "waypoint_add"), manager = BatKeybinds.pressed(c, "waypoint_manager");
        if (c.player == null || c.world == null) { pendingOpen = false; return; }
        death(c);
        if (c.currentScreen == null && (add || manager)) { BatClientMod.openWaypoints(c); request(add); }
        if (pendingOpen && ++openTicks < 120 && c.currentScreen instanceof WebScreen && ((WebScreen) c.currentScreen).window() != null) {
            WebScreen screen = (WebScreen) c.currentScreen;
            try {
                String result = screen.window().view().evaluateScript("typeof openWaypointManager==='function'?(openWaypointManager(" + pendingNew + "),'ready'):'waiting'");
                if ("ready".equals(result)) {
                    pendingOpen = false;
                    if (draft != null) {
                        screen.window().view().evaluateScript("wpOpenDraft(" + draft + ")");
                        draft = null;
                    }
                }
            } catch (RuntimeException ignored) {}
        } else if (openTicks >= 120) {
            pendingOpen = false;
        }
        if (age % 5 != 0 || !VisualModules.enabled("waypoints")) return;
        JsonObject mod = VisualModules.settings("waypoints");
        for (WaypointStore.Entry e : current()) {
            if (e.tracked() && e.visible() && e.dimension().equals(dimension)) {
                double distance = new Vec3d(c.player.x, c.player.y, c.player.z).distanceTo(new Vec3d(e.x() + .5, e.y(), e.z() + .5));
                double radius = VisualModules.number(mod, "arrivalRadius", 5, 1, 32);
                if (distance <= radius && !arrived.equals(e.id())) {
                    arrived = e.id();
                    if (HudConfig.bool(mod, "arrival", true)) {
                        c.inGameHud.getChatHud().addMessage(new LiteralText("Arrived at " + e.name()));
                    }
                } else if (distance > radius * 2 && arrived.equals(e.id())) {
                    arrived = "";
                }
            }
        }
    }

    public static String action(String action, String raw) {
        MinecraftClient c = MinecraftClient.getInstance();
        refresh(c);
        String error = "", created = "";
        try {
            if (!action.equals("list")) {
                if (scope.isEmpty()) throw new IllegalArgumentException("Join a world or server first.");
                if (raw != null && raw.length() > 8192) throw new IllegalArgumentException("Waypoint request is too large.");
                JsonObject data = raw == null || raw.trim().isEmpty() ? new JsonObject() : new JsonParser().parse(raw).getAsJsonObject();
                String id = data.has("id") ? data.get("id").getAsString() : "";
                boolean remote = SERVER.contains(id);
                if (remote && !"visible".equals(action) && !"tracked".equals(action) && !"copy".equals(action)) {
                    throw new IllegalArgumentException("Server waypoints are managed by the server. You can hide or track them.");
                }
                if ("save".equals(action)) {
                    created = STORE.save(scope, data, dimension).id();
                } else if ("delete".equals(action)) {
                    STORE.remove(scope, id);
                } else if ("copy".equals(action)) {
                    WaypointStore.Entry point = current().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Waypoint not found."));
                    new BatWebClipboard().write(point.name() + ": " + point.x() + ", " + point.y() + ", " + point.z() + " (" + point.dimension() + ")");
                } else if ("visible".equals(action) || "favorite".equals(action) || "tracked".equals(action)) {
                    boolean value = data.has("value") && data.get("value").getAsBoolean();
                    if ("tracked".equals(action) && value) {
                        SERVER.stopTracking();
                        for (WaypointStore.Entry e : STORE.list(scope)) if (e.tracked()) STORE.flag(scope, e.id(), "tracked", false);
                    }
                    if (remote) {
                        if ("visible".equals(action)) SERVER.visible(id, value); else SERVER.track(id, value);
                    } else {
                        STORE.flag(scope, id, action, value);
                    }
                } else if ("group".equals(action)) {
                    String group = WaypointStore.label(data.get("group").getAsString(), 24, "Group");
                    boolean value = data.get("value").getAsBoolean();
                    STORE.group(scope, group, value);
                    for (WaypointStore.Entry e : SERVER.list()) if (e.group().equals(group)) SERVER.visible(e.id(), value);
                } else {
                    throw new IllegalArgumentException("Unknown waypoint action.");
                }
                dirty = true;
                refresh(c);
            }
        } catch (Exception failure) {
            error = failure instanceof java.io.IOException ? "Could not save waypoints. Your previous locations are intact." : failure.getMessage() == null ? "Waypoint request failed." : failure.getMessage();
        }
        JsonObject result = new JsonObject();
        result.addProperty("error", error);
        result.addProperty("notice", STORE.notice());
        result.addProperty("readOnly", STORE.readOnly());
        result.addProperty("world", label);
        result.addProperty("dimension", dimension);
        result.addProperty("createdId", created);
        result.addProperty("moduleEnabled", VisualModules.enabled("waypoints"));
        JsonArray list = new JsonArray();
        for (WaypointStore.Entry e : current()) {
            JsonObject item = e.json();
            item.addProperty("server", SERVER.contains(e.id()));
            item.remove("scope");
            if (c.player != null && e.dimension().equals(dimension)) {
                item.addProperty("distance", new Vec3d(c.player.x, c.player.y, c.player.z).distanceTo(new Vec3d(e.x() + .5, e.y(), e.z() + .5)));
            }
            list.add(item);
        }
        result.add("waypoints", list);
        if (c.player != null) {
            JsonObject position = new JsonObject();
            position.addProperty("x", MathHelper.floor(c.player.x));
            position.addProperty("y", MathHelper.floor(c.player.y));
            position.addProperty("z", MathHelper.floor(c.player.z));
            result.add("position", position);
        }
        return result.toString();
    }
}
