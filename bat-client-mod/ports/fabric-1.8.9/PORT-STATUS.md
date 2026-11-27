# Bat Client Mod — `fabric-1.8.9` Port Status

- **Target:** `1.8.9` (Legacy Fabric `0.20.1`, Java 8 bytecode via `lowerToJava8`, ProGuard-protected + Ultralight JNI)
- **Status:** **COMPLETE & VERIFIED**
- **Build:** `.\gradlew.bat build --no-daemon` -> `BUILD SUCCESSFUL` + `PROTECTED_FABRIC_PORT_OK`
- **Audits:**
  - `audit-port-mixins.py`: `ACTUAL_GAME_MIXIN_MEMBER_AUDIT: 37 targets; 0 issues`
  - `audit-constructor-callbacks.py`: `COMPILED_CONSTRUCTOR_CALLBACK_AUDIT: 0 callbacks; 0 issues`
- **Visual Parity (`run-fabric-production-smoke.py --visual-parity` on JRE 8 via `bat-client-next.exe`):**
  - `BAT_VISUAL_PARITY_CAPTURE_OK`
  - Inspected `main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png`
