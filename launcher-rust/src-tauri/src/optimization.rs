use crate::downloads;
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::{fs, io::Write, path::Path};
// Preserves existing options except the explicit Electron automatic-optimization policy.
fn transform(original: &str, version: &str, settings: &Value) -> String {
    let mut lines: Vec<String> = original.lines().map(str::to_owned).collect();
    let mut set = |key: &str, value: &str, force: bool| {
        if let Some(index) = lines
            .iter()
            .position(|l| l.split_once(':').is_some_and(|(k, _)| k == key))
        {
            if force {
                lines[index] = format!("{key}:{value}");
            }
        } else {
            lines.push(format!("{key}:{value}"));
        }
    };
    let modern = version
        .split('.')
        .nth(1)
        .and_then(|v| v.parse::<u32>().ok())
        .is_some_and(|v| v >= 13);
    for (key, value) in [
        ("fancyGraphics", "false"),
        ("smoothLighting", "false"),
        ("entityShadows", "false"),
        ("enableVsync", "false"),
        ("maxFps", "999"),
        ("particles", "0"),
        ("mipmapLevels", "0"),
        ("anaglyph", "false"),
        ("bobView", "false"),
        ("aosLevel", "0"),
        ("clouds", "false"),
        ("useBlockFaceCulling", "true"),
        ("entityDistanceScaling", "0.5"),
        ("gamma", "1.0"),
        ("guiScale", "2"),
        ("chatScale", "1.0"),
        ("chatWidth", "1.0"),
        ("chatHeight", "1.0"),
        ("chatLineSpacing", "0.0"),
        ("showFPS", "false"),
        ("levelType", "default"),
        ("fogStart", "0.8"),
        ("fogEnd", "1.0"),
        ("ofFogType", "1"),
        ("ofClouds", "0"),
        ("ofWeather", "0"),
        ("ofTrees", "0"),
        ("ofGrass", "0"),
        ("ofDroppedItems", "0"),
        ("ofRain", "0"),
        ("ofSky", "0"),
        ("ofStars", "0"),
        ("ofSunMoon", "0"),
        ("ofVignette", "0"),
        ("ofShadowQuality", "0"),
        ("ofShadowDistance", "0"),
        ("ofSmoothFps", "0"),
        ("ofSmoothWorld", "0"),
        ("ofLazyChunkLoading", "0"),
        ("ofRenderRegions", "0"),
        ("ofFastRender", "1"),
        ("ofFastMath", "1"),
        ("ofChunkUpdates", "1"),
        ("ofChunkUpdatesDynamic", "0"),
        ("ofNegitive", "0"),
        ("ofAntialiasing", "0"),
        ("ofClearWater", "0"),
        ("ofBetterGrass", "0"),
        ("ofCustomColors", "0"),
        ("ofCustomEntityModels", "0"),
        ("ofCustomGuis", "0"),
        ("ofCustomItems", "0"),
        ("ofCustomModels", "0"),
        ("ofCustomSky", "0"),
        ("ofCustomWeather", "0"),
        ("ofDynamicLights", "0"),
        ("ofEntities", "0"),
        ("ofFarPlane", "0.0"),
        ("ofFrameTime", "0"),
        ("ofTranslucentBlocks", "1"),
        ("ofAnimatedWater", "0"),
        ("ofAnimatedRedstone", "0"),
        ("ofAnimatedLava", "0"),
        ("ofAnimatedFire", "0"),
        ("ofAnimatedPortal", "0"),
        ("ofAnimatedText", "0"),
        ("ofAnimatedTerrain", "0"),
        ("ofEntityModels", "0"),
        ("ofEntityShadow", "0"),
        ("ofWater", "fast"),
        ("ofBanner", "fast"),
        ("ofSigns", "fast"),
        ("ofChests", "fast"),
        ("ofConnectedTextures", "0"),
        ("ofEntityDistance", "0"),
        ("ofDamageTilt", "0"),
        ("useVbo", "true"),
    ] {
        set(key, value, false);
    }
    set("maxFps", if modern { "260" } else { "999" }, true);
    set("enableVsync", "false", true);
    if modern {
        set("ao", "false", true);
        set("graphicsMode", "0", true);
    }
    // These two settings belong to OptiFine; do not pretend they tune vanilla/Sodium.
    let leaves = settings["cullLeaves"].as_bool().unwrap_or(true);
    let math = settings["fastMath"].as_bool().unwrap_or(true);
    lines.retain(|line| {
        !(line.starts_with("ofTrees:") && !leaves || line.starts_with("ofFastMath:") && !math)
    });
    let number = |key: &str| {
        lines
            .iter()
            .find_map(|l| l.strip_prefix(&format!("{key}:")))
            .and_then(|v| v.parse::<u32>().ok())
    };
    if let (Some(render), Some(simulation)) =
        (number("renderDistance"), number("simulationDistance"))
    {
        if simulation > render {
            if let Some(line) = lines
                .iter_mut()
                .find(|l| l.starts_with("simulationDistance:"))
            {
                *line = format!("simulationDistance:{render}");
            }
        }
    }
    lines.join("\n") + "\n"
}
pub fn apply(game: &Path, version: &str, settings: &Value) -> Result<(), String> {
    if settings["autoOptimize"] == false {
        return Ok(());
    }
    let path = game.join("options.txt");
    downloads::parent(&path)?;
    if fs::symlink_metadata(&path).is_ok_and(|m| !m.is_file() || m.file_type().is_symlink()) {
        return Err("Game options must be a regular file".into());
    }
    let original = if path.is_file() {
        if fs::metadata(&path)
            .map_err(|_| "Cannot inspect game options")?
            .len()
            > 1024 * 1024
        {
            return Err("Game options file is too large".into());
        }
        fs::read_to_string(&path).map_err(|_| "Cannot read game options")?
    } else {
        String::new()
    };
    let changed = transform(&original, version, settings);
    if changed == original {
        return Ok(());
    }
    if !original.is_empty() {
        let backup = game
            .join(".bat-options-backups")
            .join(format!("{:x}.txt", Sha256::digest(original.as_bytes())));
        downloads::parent(&backup)?;
        if !backup.exists() {
            fs::write(backup, &original).map_err(|_| "Cannot preserve original game options")?;
        }
    }
    let mut temp =
        tempfile::NamedTempFile::new_in(game).map_err(|_| "Cannot stage game options")?;
    temp.write_all(changed.as_bytes())
        .map_err(|_| "Cannot write game options")?;
    temp.as_file()
        .sync_all()
        .map_err(|_| "Cannot flush game options")?;
    temp.persist(path).map_err(|_| "Cannot save game options")?;
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn preserves_unknown_options_and_has_version_specific_unlimited() {
        let v = transform(
            "resourcePacks:[\"user.zip\"]\nrenderDistance:8\nsimulationDistance:16\n",
            "1.21.1",
            &json!({"fastMath":false}),
        );
        assert!(v.contains("resourcePacks:[\"user.zip\"]"));
        assert!(v.contains("simulationDistance:8"));
        assert!(v.contains("maxFps:260"));
        assert!(!v.contains("ofFastMath:"));
        assert_eq!(v, transform(&v, "1.21.1", &json!({"fastMath":false})));
        assert!(transform("", "1.8.9", &json!({})).contains("maxFps:999"));
    }
    #[test]
    fn disabled_does_not_create_or_change_options() {
        let dir = tempfile::tempdir().unwrap();
        apply(dir.path(), "1.21.1", &json!({"autoOptimize":false})).unwrap();
        assert!(!dir.path().join("options.txt").exists());
        fs::write(dir.path().join("options.txt"), b"original").unwrap();
        apply(dir.path(), "1.21.1", &json!({"autoOptimize":false})).unwrap();
        assert_eq!(
            fs::read(dir.path().join("options.txt")).unwrap(),
            b"original"
        );
    }
}
