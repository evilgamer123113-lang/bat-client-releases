# Bat Client `fabric-1.17.1` Port Status

- **Target:** Minecraft `1.17.1` (Fabric Loader `0.20.1`, Yarn `1.17.1+build.65:v2`, Java `16`, **no Fabric API** — pure Fabric Loader + `batclient.nofapi.mixins.json`)
- **Status:** `VISUAL_PARITY_VERIFIED` + `PRODUCTION_BUILD_OK`
- **Production JAR:** `build/libs/bat-client-fabric-1.17.1-INTERNAL-UNVERIFIED-0.1.0-fabric-internal.jar`
  - **Size:** `22404088` bytes
  - **SHA-256:** `954AEE36BC08323E031207BF0B35A33105A0A9D3CBBF041A51153D206A1D4E67`
- **Verification Gates:**
  - `audit-port-mixins.py`: `47 targets; 0 issues`
  - `audit-constructor-callbacks.py`: `0 callbacks; 0 issues`
  - `run-fabric-production-smoke.py --minecraft 1.17.1 --visual-parity`: `Process exit: 0`, `Result: BAT_VISUAL_PARITY_CAPTURE_OK`
  - Visual inspection (`D:\BatClientBuildCache\mod-native\fabric-1.17.1-visual\visual-parity`): `main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png` all crisp and pixel-accurate.
  - `gradlew.bat build --rerun-tasks` (without `-PvisualParitySmoke=true`): `BUILD SUCCESSFUL`, `PROTECTED_FABRIC_PORT_OK`
