# Bat Client `fabric-1.16.5` Port Status

- **Target:** Minecraft `1.16.5` (Fabric Loader `0.20.1`, Yarn `1.16.5+build.10:v2`, Java `8` bytecode via `options.release = 8`, **no Fabric API** — pure Fabric Loader + `batclient.nofapi.mixins.json`)
- **Status:** `VISUAL_PARITY_VERIFIED` + `PRODUCTION_BUILD_OK`
- **Production JAR:** `build/libs/bat-client-fabric-1.16.5-INTERNAL-UNVERIFIED-0.1.0-fabric-internal.jar`
  - **Size:** `22406521` bytes
  - **SHA-256:** `0321D5C5F1F5C1A9657B10A9FBF1790474B3E174A7EED79A5099A86C0EB35A9A`
- **Verification Gates:**
  - `audit-port-mixins.py`: `47 targets; 0 issues`
  - `audit-constructor-callbacks.py`: `0 callbacks; 0 issues`
  - `run-fabric-production-smoke.py --minecraft 1.16.5 --visual-parity`: `Process exit: 0`, `Result: BAT_VISUAL_PARITY_CAPTURE_OK`
  - Visual inspection (`D:\BatClientBuildCache\mod-native\fabric-1.16.5-visual\visual-parity`): `main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png` all crisp and pixel-accurate.
  - `gradlew.bat build --rerun-tasks` (without `-PvisualParitySmoke=true`): `BUILD SUCCESSFUL`, `PROTECTED_FABRIC_PORT_OK`
