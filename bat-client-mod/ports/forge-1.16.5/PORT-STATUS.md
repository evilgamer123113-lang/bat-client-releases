# Bat Client `forge-1.16.5` Port Status

- **Target:** Minecraft `1.16.5` (Forge `36.2.34`, Yarn `1.16.5+build.10:v2`, Java `8` bytecode via `options.release = 8`)
- **Status:** `VISUAL_PARITY_VERIFIED` + `PRODUCTION_BUILD_OK`
- **Production JAR:** `build/libs/bat-client-forge-1.16.5-INTERNAL-UNVERIFIED-0.1.0-forge-internal.jar`
  - **Size:** `44177501` bytes
  - **SHA-256:** `C1E084B40993D528F7FA8CD043A501F1A1DD639BFC76C5A2AA611B389DE9F2A0`
- **Verification Gates:**
  - `audit-port-mixins.py`: `47 targets; 0 issues`
  - `audit-constructor-callbacks.py`: `0 callbacks; 0 issues`
  - `run-forge-production-smoke.py --version 1.16.5-forge-36.2.34 --visual-parity` (Java `16.0.2+7` via `bat-client-next.exe`): `Process exit: 0`, `Result: BAT_VISUAL_PARITY_CAPTURE_OK`
  - Visual inspection (`D:\BatClientBuildCache\mod-native\forge-1.16.5-visual\visual-parity`): `main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png` all crisp and pixel-accurate.
  - `gradlew.bat build --rerun-tasks` (without `-PvisualParitySmoke=true`): `BUILD SUCCESSFUL`, `FORGE_36_MIXIN_BYTECODE_OK`, `PROTECTED_FORGE_ARTIFACT_OK`
