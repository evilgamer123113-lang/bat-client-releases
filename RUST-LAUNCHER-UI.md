# Bat Client Rust Launcher — UI Design & Architecture Notes

**Frontend root:** `C:\Users\Noor-ul-Islam\Desktop\Raven-Client\launcher-rust\ui\` (`index.html`, `styles.css`, `wardrobe.css`, and 18 vanilla ES/IIFE JS modules)
**Rust Cargo target cache:** `D:\BatClientBuildCache\rust-launcher-target\` (built debug binary: `D:\BatClientBuildCache\rust-launcher-target\debug\bat-client-next.exe`)
**Tauri CSP constraint:** `default-src 'self'; ... style-src 'self';` — all CSS, JS, fonts, and images must remain local under `launcher-rust/ui/`.

## Theme & Typography (`styles.css`, `wardrobe.css`)

- **True Jet-Black Surfaces Everywhere:**
  - `--bg: #000`, `--panel: #000`, with explicit `background-color: #000 !important` across `html`, `body`, `#main`, `.app-shell`, `.titlebar`, `.sidebar`, `.hero`, `.quick-card`, `.loadout-card`, `.modrinth-card`, `.settings-panel`, `.wardrobe-stage`, popups (`#loadout-popup`, `#account-popup`, `#version-popup`), and dialogs.
- **Clean Normal System Typography:**
  - `--font: "Segoe UI Variable", "Segoe UI", Arial, sans-serif` (`14px` body, clean bold headings without `Press Start 2P` overrides — reverted per user preference for readability).
- **Emerald Green Launch CTA ONLY (`#page-home #play-button`):**
  - Classic Minecraft emerald beveled button strictly scoped to the primary Launch button:
    - Normal: `#3c8527`, text `#ffffff`, border `1px solid #13280d`, inset bevel `inset 0 2px 0 #6bc349, inset -2px -2px 0 #265918`.
    - Hover: `#489c30`.
    - Active press: `transform: translateY(1px)` with inverted bevel.
  - All other UI elements (sidebar nav, tabs, loadouts, quick cards, toggles, white `#startup-screen` loading bar, white `#launch-progress` bar) use crisp monochrome `#000000` / `#ffffff`.
- **Hero Widget Art:** `assets/home-widget-generated-v3.png` with `filter: invert(1); opacity: .24` and vertical fade mask.

## Launcher Pages & Modules (`index.html`)

1. **Startup Splash (`#startup-screen`, `startup.js`):** Bat Client logo + white animated progress bar (`background: #fff`), dispatches `bat-startup-complete` when ready.
2. **Home (`#page-home`):** Greeting heading, account switcher chip (`#account-trigger` / `#account-popup`), compact hero card with `#loadout-trigger` custom popover, emerald `#play-button` (`game-launch.js`), 3 Quick-access cards, and live Changelog news bar (`#news-panel`).
3. **Loadouts (`#page-loadouts`, `profiles.js`):** Per-profile MC version browser (1.8–1.21.11), loader (`vanilla`, `fabric`, `forge`, `neoforge`, `quilt`), RAM, JVM args, favorite toggle, and duplicate/edit/delete actions.
4. **Mods (`#page-mods`, `mods.js`):** Modrinth search + pagination + version compatibility check, plus Installed mods list with enable/disable switches.
5. **Wardrobe (`#page-wardrobe`, `wardrobe.js`, `wardrobe.css`):**
   - `skinview3d` 3D character canvas (`#wardrobe-canvas`, `280×340`) with `Front` / `Back` camera buttons, zoom/rotate drag, `Classic` / `Slim` model selector.
   - Skins & Capes tabs supporting PNG skins/capes and **animated GIF capes** (`frames` + `delays` cycled live on the 3D cape mesh; static frame used for online sharing), up to 20 saved slots.
6. **Accounts (`#page-accounts`, `accounts.js`):** Built-in Microsoft login, browser device-code login, and offline username creation.
7. **Settings (`#page-settings`, `settings.js`):** Home artwork & reduce-motion switches, default RAM/download threads/game dir/Java path/JVM args/resolution, Java check, and Flying Bat controls.

## Flying Bat Pet (`bat-flight.js`, `bat-sprite.js`, `flying-bat.js`)

- Renders in `.flying-bat-layer` (`88×60` pixelated canvas) across launcher pages when window is focused and startup is finished (`focused && ready`).
- Pure white heart indicator (`.flying-bat-heart`, `background: #fff` clip-path heart).
- Interactive: left-click drag to carry, right-click to pet; configurable in Settings (`enabled`, `hang`, `particles`, `speed`: `slow` / `normal` / `fast`) persisted via Tauri `tools_action`.
