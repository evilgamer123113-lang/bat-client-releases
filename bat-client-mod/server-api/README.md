# Bat Client Waypoints server API (v1)

This optional plugin targets Bukkit-compatible Minecraft 1.21.1 servers running Java 21. Install `build/libs/batclient-waypoints-api-1.0.0.jar` in your **server's** `plugins` directory. Players install the protected Bat Client mod on Fabric 1.21.1 and enable Waypoints. The server plugin does not belong in a player's `mods` directory.

This is Bat Client's protocol and API. It does not impersonate Feather or Lunar and does not automatically receive their proprietary plugin messages. A plugin that currently uses Feather/Apollo needs to call this API as well for Bat Client users. No website host, external account or player-to-player network connection is required.

## Use from your plugin

Add `depend: [BatClientWaypoints]` to your plugin.yml and add the API JAR as a compile-only dependency. Run calls on the Bukkit main thread. Obtain the registered service:

```java
WaypointService service = Bukkit.getServicesManager().load(WaypointService.class);
if (service == null) return;
Player player = /* online recipient */;
UUID id = service.createWaypoint(player,
    service.createWaypointBuilder(100, 65, -200)
        .withName("Event arena")
        .withGroup("Events")
        .withColor(117, 207, 255, 200)
        .withBeam(true)
        .withChroma(false)
        .withDuration(300)
        .withWorldId(player.getWorld().getUID()));
```

Import `com.batclient.api.waypoints.WaypointService`. `withColor` accepts RGB or RGBA integers 0–255. `withOpacity(0–100)` is also available. Duration is an integer number of seconds, 0 for the player's connection lifetime, at most 604800 (7 days). A reusable builder is copied when each player marker is created; send the same builder individually to each intended recipient for shared objectives. Up to 64 markers are retained per player connection.

`destroyWaypoint(player, id)`, `destroyWaypoints(player, ids)` and `destroyAllWaypoints(player)` remove server-owned markers. Update a marker by destroying its ID and creating a replacement. Your plugin owns recreation after player reconnect or server restart; no remote markers are written into players' personal waypoint files. World changes and respawns automatically resynchronize markers for the current world. Other-world markers remain on the server until that player enters their world or they expire.

Players can hide, track or copy a server marker. They cannot edit/delete the server's definition. Hiding/tracking is local to the connection. Personal waypoint settings, groups and saves are separate; clearing server markers cannot clear personal locations. The client Waypoints module and Show Server Waypoints setting control display.

## Test commands

As an operator in-game, use `/batwaypoint create Spawn` at the destination and `/batwaypoint clear` to clear your server markers. Other players do not receive your test command marker; use your plugin's recipient loop for a group objective. These commands require `batclient.waypoints.admin` (operator by default).

Build from the mod root on Windows: `gradlew.bat -p server-api build`.

## Wire protocol for other server implementations

Minecraft play custom payload channel: `batclient:waypoints`, server to client only. The payload is **VarInt byte length + UTF-8 JSON**, compatible with a Minecraft bounded string, not Java `writeUTF`. Maximum 8192 JSON characters / 8192 UTF-8 bytes from the helper; one marker per message, at most 64 active remote markers, bounded client message processing. Never send account credentials or other secrets here.

First select the current Bukkit world UUID:

```json
{"version":1,"op":"world","worldId":"11111111-1111-4111-8111-111111111111"}
```

Then create/update a marker:

```json
{
  "version":1,
  "op":"upsert",
  "worldId":"11111111-1111-4111-8111-111111111111",
  "id":"22222222-2222-4222-8222-222222222222",
  "durationSeconds":300,
  "waypoint":{
    "name":"Event arena","group":"Events",
    "x":100,"y":65,"z":-200,
    "dimension":"minecraft:overworld",
    "color":"#75CFFF","opacity":80,"beam":true,"chroma":false
  }
}
```

The dimension must match the recipient's actual client dimension registry ID. The helper uses `World.getKey()`. A proxy/custom-dimension integration must map its world correctly and send a fresh world selection plus current markers after a dimension/world transition. Client dimension transitions and disconnect clear session markers as a safeguard against stale destinations. The UUID disambiguates multiple server worlds that use the same dimension type.

`{"version":1,"op":"remove","worldId":"...","id":"..."}` removes one marker; `{"version":1,"op":"clear"}` removes all server markers for the receiving player. IDs must be valid UUIDs. `world` only clears previous markers if its UUID changes. Server IDs are namespaced separately from personal IDs internally. Names/groups are plain text, limited to 48/24 characters; formatting/control codes are rejected. X/Z must be within ±29999984 and Y within -2048…4096. Invalid messages are ignored, not executed as commands or shown as HTML.

## Player features

N opens a new-waypoint draft at the current position; M opens the manager. Both can be rebound in Minecraft Controls or Bat Client Keybinds. Create/edit personal points with groups, favorites, visibility, tracking, color, chroma, opacity, beam and optional duration. Personal locations use atomic UTF-8 storage and a last-good backup, scoped to the save path/server address and dimension.

Settings include beam height, label scale/border/padding/background/shadow, marker count/range, terrain visibility, navigation-guide position, minimap and compass markers, arrival notices, automatic death points (24-hour expiry), and chat coordinate drafts. Left-click a chat message containing `X: 100 Y: 65 Z: -200` to review those coordinates in the editor; saving still requires Create waypoint. No automatic chat sending, coordinate sharing, teleportation, chunk loading or route finding is performed.
