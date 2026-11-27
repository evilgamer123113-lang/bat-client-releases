function parseVersion(v) {
    const m = (v || '').match(/^(\d+)\.(\d+)(?:\.(\d+))?/);
    if (!m) return [0, 0, 0];
    return [parseInt(m[1]) || 0, parseInt(m[2]) || 0, parseInt(m[3]) || 0];
}

function buildForcedOptions(mcVersion, appSettings = {}) {
    if (appSettings.autoOptimize === false) return {};
    return {};
}

const LAUNCHER_MARKER = '# Raven Client auto-optimization (safe to remove)';

const VANILLA_PERF_OPTIONS = {
    'fancyGraphics': 'false',
    'smoothLighting': 'false',
    'entityShadows': 'false',
    'enableVsync': 'false',
    'maxFps': '999',
    'particles': '0',
    'mipmapLevels': '0',
    'anaglyph': 'false',
    'bobView': 'false',
    'aosLevel': '0',
    'clouds': 'false',
    'useBlockFaceCulling': 'true',
    'entityDistanceScaling': '0.5',
    'gamma': '1.0',
    'guiScale': '2',
    'chatScale': '1.0',
    'chatWidth': '1.0',
    'chatHeight': '1.0',
    'chatLineSpacing': '0.0',
    'showFPS': 'false',
    'levelType': 'default',
    'fogStart': '0.8',
    'fogEnd': '1.0'
};

const OPTIFINE_PERF_OPTIONS = {
    'ofFogType': '1',
    'ofClouds': '0',
    'ofWeather': '0',
    'ofTrees': '0',
    'ofGrass': '0',
    'ofDroppedItems': '0',
    'ofRain': '0',
    'ofSky': '0',
    'ofStars': '0',
    'ofSunMoon': '0',
    'ofVignette': '0',
    'ofShadowQuality': '0',
    'ofShadowDistance': '0',
    'ofSmoothFps': '0',
    'ofSmoothWorld': '0',
    'ofLazyChunkLoading': '0',
    'ofRenderRegions': '0',
    'ofFastRender': '1',
    'ofFastMath': '1',
    'ofChunkUpdates': '1',
    'ofChunkUpdatesDynamic': '0',
    'ofNegitive': '0',
    'ofAntialiasing': '0',
    'ofClearWater': '0',
    'ofBetterGrass': '0',
    'ofCustomColors': '0',
    'ofCustomEntityModels': '0',
    'ofCustomGuis': '0',
    'ofCustomItems': '0',
    'ofCustomModels': '0',
    'ofCustomSky': '0',
    'ofCustomWeather': '0',
    'ofDynamicLights': '0',
    'ofEntities': '0',
    'ofFarPlane': '0.0',
    'ofFrameTime': '0',
    'ofTranslucentBlocks': '1',
    'ofAnimatedWater': '0',
    'ofAnimatedRedstone': '0',
    'ofAnimatedLava': '0',
    'ofAnimatedFire': '0',
    'ofAnimatedPortal': '0',
    'ofAnimatedText': '0',
    'ofAnimatedTerrain': '0',
    'ofEntityModels': '0',
    'ofEntityShadow': '0',
    'ofWater': 'fast',
    'ofBanner': 'fast',
    'ofSigns': 'fast',
    'ofChests': 'fast',
    'ofConnectedTextures': '0',
    'ofEntityDistance': '0',
    'ofDamageTilt': '0'
};

const VBO_PERF_OPTIONS = {
    'useVbo': 'true'
};

function parseOptionsTxt(content) {
    const map = {};
    if (!content) return map;
    const lines = content.split('\n');
    for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) continue;
        const sepIdx = trimmed.indexOf(':');
        if (sepIdx === -1) {
            const spaceIdx = trimmed.indexOf(' ');
            if (spaceIdx !== -1) {
                map[trimmed.substring(0, spaceIdx)] = trimmed.substring(spaceIdx + 1);
            }
            continue;
        }
        const key = trimmed.substring(0, sepIdx).trim();
        const val = trimmed.substring(sepIdx + 1).trim();
        map[key] = val;
    }
    return map;
}

function serializeOptionsTxt(map) {
    const lines = [];
    for (const [key, val] of Object.entries(map)) {
        lines.push(`${key}:${val}`);
    }
    return lines.join('\n') + '\n';
}

async function injectOptimizationSettings({ mcVersion, root, appSettings, fs, path }) {
    if (appSettings.autoOptimize === false) {
        return { applied: 0, skipped: true, reason: 'auto-optimize disabled by user' };
    }

    try {
        const optionsPath = path.join(root || '.', 'options.txt');
        let existingContent = '';
        if (await fs.pathExists(optionsPath)) {
            existingContent = await fs.readFile(optionsPath, 'utf8');
        }

        const options = parseOptionsTxt(existingContent);
        let applied = 0;
        let vanillaApplied = 0;
        let optifineApplied = 0;
        let vboApplied = 0;

        // Unlimited FPS value differs by MC version engine:
        //  - 1.13+ range-checks maxFps to [10:260] and uses 260 as "Unlimited"
        //  - 1.8-1.12 treats >=250 as "Unlimited" via 999 sentinel
        const version = parseVersion(mcVersion);
        const unlimitedFps = (version[0] >= 1 && version[1] >= 13) ? '260' : '999';

        const perfTargets = { ...VANILLA_PERF_OPTIONS, ...OPTIFINE_PERF_OPTIONS, ...VBO_PERF_OPTIONS };

        for (const [key, defaultValue] of Object.entries(perfTargets)) {
            if (options[key] === undefined) {
                if (key === 'maxFps') {
                    options[key] = unlimitedFps;
                } else {
                    options[key] = defaultValue;
                }
                applied++;
                if (VANILLA_PERF_OPTIONS[key] !== undefined) vanillaApplied++;
                if (OPTIFINE_PERF_OPTIONS[key] !== undefined) optifineApplied++;
                if (VBO_PERF_OPTIONS[key] !== undefined) vboApplied++;
            }
        }

        // Select the version-specific Unlimited sentinel and disable VSync,
        // even when the user already has a value in options.txt.
        if (options['maxFps'] !== unlimitedFps) { options['maxFps'] = unlimitedFps; applied++; }
        if (options['enableVsync'] !== 'false') { options['enableVsync'] = 'false'; applied++; }

        if (appSettings.cullLeaves === false) {
            delete options['ofGrass'];
        }
        if (appSettings.fastMath === false) {
            delete options['ofFastMath'];
        }

        if (applied > 0) {
            await fs.writeFile(optionsPath, serializeOptionsTxt(options), 'utf8');
            console.log(`[Optimization] Applied ${vanillaApplied} Vanilla + ${optifineApplied} OptiFine + ${vboApplied} VBO options (${applied} total) to options.txt`);
        }

        return { optionsPath, applied, vanillaApplied, optifineApplied, vboApplied };
    } catch (err) {
        console.warn('[Optimization] Could not inject options.txt:', err.message);
        return null;
    }
}

const SODIUM_PERF_SETTINGS = {
    'quality': {
        'biome-blend-radius': 2,
        'weather': 'fast',
        'leaves': 'fast',
        'entity-distance-scaling': 0.5,
        'entity-shadow-distance': 0.0,
        'ambient-occlusion': 'flat',
        'glint-speed': 0.1,
        'glint-intensity': 0.3,
        'cloud-status': 'fast',
        'particles': 'minimal',
        'mipmaps': false,
        'graphics-level': 'fast',
        'smooth-lighting': 'none',
        'shadow-quality': 'none',
        'entity-models': false,
        'block-face-culling': true,
        'bloom': false,
        'depth-fog': false
    },
    'performance': {
        'use-blocking-staging-buffer': false,
        'chunk-notification-schedule-on-demand': true,
        'multi-threaded-chunk-building': true,
        'chunk-builder': 'fused',
        'use-entity-culling': true,
        'always-defer-entity-data': true,
        'allow-direct-memory-access': true,
        'use-fog-occlusion': true,
        'use-block-face-culling': true,
        'chunk-update-thread-count': 0,
        'async-path-finding': true
    },
    'rendering': {
        'animation-toggle': false,
        'disable-mipmap-textures': true,
        'anaglyph': false,
        '.entity-rendering': 'fast',
        'translucency': 'fast',
        'weather-quality': 'fast'
    },
    'general': {
        'show-fps': false,
        'show-glacier-info': false,
        'toggle-gamemode': true,
        'max-fps': 999
    }
};

function deepMergePreserveUser(existing, defaults) {
    const result = {};
    if (typeof existing !== 'object' || existing === null) existing = {};
    if (typeof defaults !== 'object' || defaults === null) return result;

    for (const [key, defaultValue] of Object.entries(defaults)) {
        if (typeof defaultValue === 'object' && defaultValue !== null && !Array.isArray(defaultValue)) {
            result[key] = deepMergePreserveUser(existing[key] || {}, defaultValue);
        } else {
            if (existing[key] !== undefined) {
                result[key] = existing[key];
            } else {
                result[key] = defaultValue;
            }
        }
    }

    for (const [key, val] of Object.entries(existing)) {
        if (!(key in result)) {
            result[key] = val;
        }
    }

    return result;
}

async function injectSodiumConfig({ root, appSettings, fs, path }) {
    if (appSettings.autoOptimize === false) {
        return { applied: 0, skipped: true, reason: 'auto-optimize disabled by user' };
    }

    try {
        const configDir = path.join(root || '.', 'config');
        await fs.ensureDir(configDir);

        const sodiumPath = path.join(configDir, 'sodium-options.json');
        let existing = {};
        if (await fs.pathExists(sodiumPath)) {
            try {
                existing = await fs.readJson(sodiumPath);
            } catch (e) {
                existing = {};
            }
        }

        const merged = deepMergePreserveUser(existing, SODIUM_PERF_SETTINGS);

        let applied = 0;
        let sectionsApplied = {};
        for (const [section, keys] of Object.entries(SODIUM_PERF_SETTINGS)) {
            let sectionCount = 0;
            for (const [key, val] of Object.entries(keys)) {
                if (!existing[section] || existing[section][key] === undefined) {
                    applied++;
                    sectionCount++;
                }
            }
            if (sectionCount > 0) sectionsApplied[section] = sectionCount;
        }

        if (applied > 0) {
            await fs.writeJson(sodiumPath, merged, { spaces: 2 });
            const sectionSummary = Object.entries(sectionsApplied).map(([s, c]) => `${s}(${c})`).join(', ');
            console.log(`[Optimization] Applied ${applied} Sodium options across sections: ${sectionSummary}`);
        }

        if (merged.general && merged.general['max-fps'] !== 999) {
            merged.general['max-fps'] = 999;
            await fs.writeJson(sodiumPath, merged, { spaces: 2 });
        }

        return { sodiumPath, applied, sectionsApplied };
    } catch (err) {
        console.warn('[Optimization] Could not inject Sodium config:', err.message);
        return null;
    }
}

module.exports = {
    parseVersion,
    buildForcedOptions,
    injectOptimizationSettings,
    injectSodiumConfig,
};
