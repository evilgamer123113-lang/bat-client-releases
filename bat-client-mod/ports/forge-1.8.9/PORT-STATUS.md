# Port Status: forge-1.8.9 (Minecraft 1.8.9 + Forge 11.15.1.2318-1.8.9)

- **Status:** Complete and verified (28/30)
- **Toolchain:** Essential Loom 1.15.52, MCP `mcp_stable:22-1.8.9`, Java 8 bytecode (`options.release = 8`), Mixin `0.7.11-SNAPSHOT` runtime (`0.8.5:processor` compile-time AP), JRE `jdk8u512-b09-jre`, `bat-client-next.exe` launcher-parent fixture
- **Visual-Parity Runtime:** `BAT_VISUAL_PARITY_CAPTURE_OK` (`D:\BatClientBuildCache\mod-native\forge-1.8.9-vp\visual-parity\`; all 6 PNGs inspected: `main-menu.png`, `game-overlay.png`, `mod-manager.png`, `pause-menu.png`, `hud-editor.png`, `world-hud.png`)
- **Audits:** `ACTUAL_GAME_MIXIN_MEMBER_AUDIT: 37 targets; 0 issues`, `COMPILED_CONSTRUCTOR_CALLBACK_AUDIT: 0 callbacks; 0 issues`
- **Production Build:** `..\..\gradlew.bat :remapJar --no-daemon` (without `-PvisualParitySmoke=true`)
