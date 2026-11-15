# First artwork and UI direction

## Current Home artwork and composition (v2)

User chose a hidden cliffside base at dusk with subtle warm lights because the bright green valley did not fit the black UI. Current original generated asset: `ui/assets/cliffside-home-v2.png` (2172x724). Generated with the built-in image-generation tool, using the inspected reference only for Minecraft geometry/materials. Reference: https://i.imgur.com/SnD6akd.png from https://unlimitedworld.de/threads/das-dorf-der-orni.28962/ . Local reference is outside the packaged UI under `references/cliffside-reference.png`. The first PlanetMinecraft reference download returned403 and was not used. This new scene is generated artwork, not an authentic captured gameplay image.

Current composition: plain Minecraft / Java Edition title and loadout + Launch controls floating DIRECTLY on the artwork. No full-width dark launch strip and no separate launch card. User explicitly rejected “A little escape. A whole new world.” as AI-sounding copy; removed that slogan and simplified shortcut/news text. Keep the rest of the UI neutral black/white.

### Exact v2 generation prompt

Create an ORIGINAL wide 3:1 landscape artwork for the HOME CARD of a BLACK AND WHITE Minecraft desktop launcher, Bat Client. Input image is a reference ONLY for authentic Minecraft block geometry, default pixel textures, actual build scale and timber/stone materials. Do NOT reproduce its viewpoint, building design, purple sky or glaring sunset. New scene: a modest hidden cliffside survival base at late dusk, recessed into a charcoal-gray stone cliff. On the RIGHT HALF, a compact, geometrically plausible spruce-and-deepslate entrance, one rectangular window with subdued warm interior light, two small amber lanterns and a short wooden walkway built into the rock. Forested ridge and a tiny sliver of neutral dusk sky beyond. The LEFT THIRD is dark, gently textured shadowed rock / distant silhouettes with quiet negative space for white launcher text. Scene is original, restrained, comfortably readable: deep neutral charcoal blacks with visible voxel detail, sparse warm amber lights as the ONLY strong color accent, no blue cast, no purple/magenta cast, no bright green foliage, no fiery orange wash. It must integrate with a pure black UI rather than feel like a bright photograph pasted on top. Natural subtle Minecraft shader illumination, physically consistent cubic blocks, straight wooden beams, recognizable pixel textures, ordinary in-game player-eye camera and game-like lighting. Not gloomy horror. Not generic AI fantasy art. No smooth photoreal rock, no melty blocks, no painterly detail, no giant castles, no floating islands, no cinematic bloom/lens flare/god rays, no moon filling sky, no complex magical objects. NO TEXT, no UI, no logo, no characters, no weapon. Output a panoramic 3:1 composition. Keep the little entrance and both lanterns within the middle vertical third on the right so they survive a shallow banner crop; bottom quarter will have an overlaid launcher control strip, so do not place essential objects there. Make it look like an intentional real Minecraft build screenshot with restrained exposure, not an AI poster.

The prompt anticipated a bottom control strip, but the user's subsequent correction removed that strip in the UI. The generated source was retained and the actual controls now float directly over it.

## Initial v1 direction (historical)

User-approved direction: deep black and white, with colorful Minecraft artwork. Simple, friendly and lively. Inspiration: Feather, Lunar, Fast Client, while retaining Bat identity rather than cloning another client's assets or layout.

The user approved the first Home layout and asked to remove its bluish/gray cast. Surfaces are now pure/near black, typography and borders neutral white/grayscale. Keep this layout and black palette while implementing the remaining native launcher features.

Use black surfaces, restrained borders, compact navigation, clear hierarchy, a visible player head/name, one central launch area and small shortcut/news rows. Short finite page/hover transitions, reduced-motion option, static artwork, no continuous WebGL viewer or idle animation loop.

## Asset provenance

- Reference inspected: https://cdn.modrinth.com/data/WtcuxDNN/images/cf6f618af024a8d3f2da5987b2d0edd5d888d455.png
- Reference page: https://modrinth.com/mod/ecospherical-expansion/version/MZpkXduS
- Reference stored only under `references/`, outside packaged UI.
- Generated using the built-in image-generation tool with the inspected image as a visual reference. Final original composition: `ui/assets/valley-home-v1.png`.
- Existing Bat logo and default Steve skin reused from this repository; no new brand logo generated.
- The generated image is artwork, not a real captured game screenshot; do not market it as measured gameplay footage.

## Generation prompt

Create ONE original wide 16:9 Minecraft Java landscape banner for the Bat Client desktop LAUNCHER Home screen. The supplied image is a REFERENCE for authentic in-engine block geometry, default-style pixel textures, terrain scale and restrained shader lighting ONLY. Make a substantially different scene/composition: a broad grassy river valley in clear late-afternoon sunlight, a small ordinary oak-and-cobblestone footbridge crossing water in the middle-right, mixed oak and birch trees, layered rugged voxel mountains further away, small believable blue sky with a few block clouds. Camera at ordinary player eye-level from a gentle grassy rise. Natural green foliage and blue water, warm but understated sunlight; crisp square block edges and consistent Minecraft geometry, pixel-textured grass/dirt/stone and water, visually plausible player-built bridge. The left third should be dark foreground foliage/grass suitable for a subtle overlaid white heading; the right two thirds show the valley and bridge. NO text, no logos, no UI, no characters, no bats, no floating islands, no enormous fantasy castles, no cinematic lens flares, no extreme god rays, no painterly brushwork, no melted/smooth terrain, no photorealistic rocks, no bokeh. The result should read as a carefully composed real Minecraft shader screenshot with modest contrast, not a generic AI fantasy illustration. This is a compact in-launcher artwork card, not a website landing-page poster.

2026-10-30: user explicitly supplied monochrome pixel-art image codex-clipboard-5a440946-93c3-4f95-8983-7fc6d6054f15.png for the Home hero. Copied byte-for-byte to ui/assets/home-widget-user.png and selected via CSS; image-rendering pixelated retains supplied pixel edges. Replaces generated cliffside art as active image; original asset retained. No image generation or reinterpretation. Existing dark readability overlay and floating controls preserved.
