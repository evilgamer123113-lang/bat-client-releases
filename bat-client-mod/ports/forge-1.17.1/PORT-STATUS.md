# Bat Client `forge-1.17.1` Port Status

- **Target:** Minecraft `1.17.1` (Forge `37.1.1`, Yarn `1.17.1+build.65:v2`, Java `16`)
- **Status:** `VISUAL_PARITY_VERIFIED` + `PRODUCTION_BUILD_OK`
- **Production JAR:** `build/libs/bat-client-forge-1.17.1-INTERNAL-UNVERIFIED-0.1.0-forge-internal.jar`
  - **Size:** `44167419` bytes
  - **SHA-256:** `E1EB9C9BA0360BC619DB6F76D81E720ADF6C64B0079914C6FEC42BBA8CD5B18D`
- **Verification Gates:**
  - `audit-port-mixins.py`: `47 targets; 0 issues`
  - `audit-constructor-callbacks.py`: `0 callbacks; 0 issues`
  - `run-forge-production-smoke.py --version 1.17.1-forge-37.1.1 --visual-parity` (Java `16.0.2+7` via `bat-client-next.exe`): `Process exit: 0`, `Result: BAT_VISUAL_PARITY_CAPTURE_OK`
  - Visual inspection (`D:\BatClientBuildCache\mod-native\forge-1.17.1-visual\visual-parity`): `main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png` all crisp and pixel-accurate.
  - `gradlew.bat build --rerun-tasks` (without `-PvisualParitySmoke=true`): `BUILD SUCCESSFUL`, `FORGE_37_MIXIN_BYTECODE_OK`, `PROTECTED_FORGE_ARTIFACT_OK`
