use crate::library as lib;
use serde_json::Value;
use std::{
    collections::{HashMap, HashSet},
    fs,
    io::Read,
    path::Path,
};

const KNOWN_CONFLICT: &str = "Kind's Crystal Optimizer 1.6.3 is incompatible with Fabric networking on Minecraft 1.21.11. Disable it in Mods before launching.";

struct Scan {
    ids: HashSet<String>,
    findings: Vec<String>,
    known_conflict: bool,
    blockers: Vec<String>,
}

fn scan(root: &Path, version: &str, engine: &str) -> Result<Scan, String> {
    let mut result = Scan {
        ids: HashSet::new(),
        findings: Vec::new(),
        known_conflict: false,
        blockers: Vec::new(),
    };
    if engine == "vanilla" {
        return Ok(result);
    }
    let directory = lib::mods_dir(root)?;
    let mut fabric_files = HashMap::<String, String>::new();
    let entries = match fs::read_dir(&directory) {
        Ok(entries) => entries,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(result),
        Err(_) => return Err("Cannot read installed mods".into()),
    };
    for entry in entries {
        let entry = entry.map_err(|_| "Cannot inspect installed mod")?;
        let name = entry.file_name().to_string_lossy().into_owned();
        if !name.to_ascii_lowercase().ends_with(".jar") {
            continue;
        }
        let path = lib::regular_file(&directory, &name)?;
        let file = fs::File::open(path).map_err(|_| "Cannot open installed mod")?;
        let mut jar = match zip::ZipArchive::new(file) {
            Ok(jar) => jar,
            Err(_) => {
                result
                    .findings
                    .push(format!("{name} is not a readable mod JAR."));
                continue;
            }
        };
        let fabric = jar.index_for_name("fabric.mod.json").is_some();
        let quilt = jar.index_for_name("quilt.mod.json").is_some();
        let forge = jar.index_for_name("META-INF/mods.toml").is_some()
            || jar.index_for_name("mcmod.info").is_some();
        let neo = jar.index_for_name("META-INF/neoforge.mods.toml").is_some();
        let mismatched = match engine {
            "fabric" => !fabric && (forge || neo || quilt),
            "quilt" => !fabric && !quilt && (forge || neo),
            "forge" => !forge && (fabric || quilt || neo),
            "neoforge" => !forge && !neo && (fabric || quilt),
            _ => false,
        };
        if mismatched {
            result
                .findings
                .push(format!("{name} declares a different loader than {engine}."));
        }
        if fabric {
            let metadata = jar
                .by_name("fabric.mod.json")
                .map_err(|_| "Cannot open Fabric mod metadata")?;
            let data: Result<Value, _> = serde_json::from_reader(metadata.take(1024 * 1024));
            match data {
                Ok(data) => {
                    if let Some(id) = data["id"].as_str() {
                        result.ids.insert(id.to_ascii_lowercase());
                        if matches!(engine, "fabric" | "quilt") {
                            if let Some(first) = fabric_files.get(id) {
                                result.findings.push(format!(
                                    "{first} and {name} both declare the mod ID {id}."
                                ));
                            } else {
                                fabric_files.insert(id.into(), name.clone());
                            }
                        }
                    }
                    if matches!(engine, "fabric" | "quilt") {
                        let required = &data["depends"]["minecraft"];
                        if matches_range(version, required) == Some(false) {
                            let message = format!("{name} requires Minecraft {required}. This loadout uses {version}.");
                            result.findings.push(message.clone());
                            result.blockers.push(message);
                        }
                    }
                    if matches!(engine, "fabric" | "quilt")
                        && version == "1.21.11"
                        && data["id"] == "kindscrystaloptimizer"
                        && data["version"] == "1.6.3"
                    {
                        result.known_conflict = true;
                    }
                }
                Err(_) => result
                    .findings
                    .push(format!("{name} has invalid or oversized Fabric metadata.")),
            }
        }
        let stem = name.to_ascii_lowercase();
        for id in [
            "optifine",
            "optifabric",
            "embeddium",
            "rubidium",
            "oculus",
            "betterfps",
            "servercore",
            "starlight",
            "phosphor",
            "lambdynamiclights",
            "iris",
            "canvas",
        ] {
            if stem.starts_with(id) {
                result.ids.insert(id.into());
            }
        }
    }
    Ok(result)
}

pub(crate) fn validate_launch(root: &Path, version: &str, engine: &str) -> Result<(), String> {
    let scan = scan(root, version, engine)?;
    let mut blockers = scan.blockers;
    if scan.known_conflict { blockers.push(KNOWN_CONFLICT.into()); }
    if !blockers.is_empty() {
        return Err(format!("Incompatible mods for Minecraft {version}:\n{}\nOpen Mods → Find compatible versions to replace verified Modrinth files for this loadout, or disable incompatible files. Your Minecraft version has not been changed.", blockers.join("\n")));
    }
    Ok(())
}

fn jar_compatible_with(path: &Path, name: &str, version: &str, engine: &str) -> bool {
    let Ok(file) = fs::File::open(path) else {
        return false;
    };
    let Ok(mut jar) = zip::ZipArchive::new(file) else {
        return false;
    };
    let fabric = jar.index_for_name("fabric.mod.json").is_some();
    let quilt = jar.index_for_name("quilt.mod.json").is_some();
    let forge = jar.index_for_name("META-INF/mods.toml").is_some()
        || jar.index_for_name("mcmod.info").is_some();
    let neo = jar.index_for_name("META-INF/neoforge.mods.toml").is_some();
    let mismatched = match engine {
        "fabric" => !fabric && (forge || neo || quilt),
        "quilt" => !fabric && !quilt && (forge || neo),
        "forge" => !forge && (fabric || quilt || neo),
        "neoforge" => !forge && !neo && (fabric || quilt),
        _ => false,
    };
    if mismatched {
        return false;
    }
    if fabric && matches!(engine, "fabric" | "quilt") {
        let Ok(metadata) = jar.by_name("fabric.mod.json") else {
            return false;
        };
        let Ok(data) = serde_json::from_reader::<_, Value>(metadata.take(1024 * 1024)) else {
            return false;
        };
        if matches_range(version, &data["depends"]["minecraft"]) == Some(false) {
            return false;
        }
        if version == "1.21.11"
            && data["id"] == "kindscrystaloptimizer"
            && data["version"] == "1.6.3"
        {
            return false;
        }
    }
    let _ = name;
    true
}

fn move_mod_file(src: &Path, dest: &Path) -> Result<(), String> {
    if fs::rename(src, dest).is_ok() {
        return Ok(());
    }
    fs::copy(src, dest).map_err(|_| "Cannot move incompatible mod")?;
    fs::remove_file(src).map_err(|_| "Cannot clean up moved mod")?;
    Ok(())
}

/// Automatically stashes mods incompatible with `(version, engine)` out of `mods/`
/// and restores any previously stashed mods that are compatible with `(version, engine)`.
pub(crate) fn auto_fix_for_launch(root: &Path, version: &str, engine: &str) -> Result<usize, String> {
    if engine == "vanilla" {
        return Ok(0);
    }
    let mods = lib::mods_dir(root)?;
    let stash = root.join("rust-mod-stash");
    let mut moved = 0usize;

    if let Ok(entries) = fs::read_dir(&mods) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if !name.to_ascii_lowercase().ends_with(".jar") {
                continue;
            }
            let Ok(path) = lib::regular_file(&mods, &name) else {
                continue;
            };
            if !jar_compatible_with(&path, &name, version, engine) {
                fs::create_dir_all(&stash).map_err(|_| "Cannot create mod stash folder")?;
                let dest = stash.join(&name);
                if dest.exists() {
                    let _ = fs::remove_file(&dest);
                }
                move_mod_file(&path, &dest)?;
                moved += 1;
            }
        }
    }

    if let Ok(entries) = fs::read_dir(&stash) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if !name.to_ascii_lowercase().ends_with(".jar") {
                continue;
            }
            let Ok(path) = lib::regular_file(&stash, &name) else {
                continue;
            };
            if jar_compatible_with(&path, &name, version, engine) {
                fs::create_dir_all(&mods).map_err(|_| "Cannot access mods folder")?;
                let dest = mods.join(&name);
                if !dest.exists() {
                    move_mod_file(&path, &dest)?;
                    moved += 1;
                }
            }
        }
    }

    Ok(moved)
}

// Deliberately tri-state: unknown snapshot/custom predicates remain the loader's
// responsibility. Never block a version by guessing an unsupported range.
fn matches_range(version: &str, requirement: &Value) -> Option<bool> {
    fn release(value: &str) -> Option<(Vec<u32>, bool)> {
        let value = value.split('+').next()?;
        let pre = value.ends_with('-');
        let value = value.strip_suffix('-').unwrap_or(value);
        let numbers = value.split('.').map(str::parse::<u32>).collect::<Result<Vec<_>,_>>().ok()?;
        if numbers.is_empty() { return None; }
        Some((numbers, pre))
    }
    fn compare(a: &(Vec<u32>,bool), b: &(Vec<u32>,bool)) -> std::cmp::Ordering {
        for i in 0..a.0.len().max(b.0.len()) {
            let order = a.0.get(i).unwrap_or(&0).cmp(b.0.get(i).unwrap_or(&0));
            if !order.is_eq() { return order; }
        }
        b.1.cmp(&a.1)
    }
    fn predicate(version: &str, text: &str) -> Option<bool> {
        if text == "*" { return Some(true); }
        let v = release(version)?;
        let terms = text.split_whitespace().collect::<Vec<_>>();
        if terms.is_empty() { return None; }
        let mut unknown = false;
        for term in terms {
            let (op, operand) = [">=","<=",">","<","=","~","^"]
                .iter().find_map(|op| term.strip_prefix(op).map(|rest| (*op,rest))).unwrap_or(("=",term));
            let result = if operand.split('.').any(|part| matches!(part,"*"|"x"|"X")) {
                if op != "=" { None } else {
                    let prefix = operand.split('.').take_while(|part| !matches!(*part,"*"|"x"|"X"))
                        .map(str::parse::<u32>).collect::<Result<Vec<_>,_>>().ok()?;
                    Some(prefix.iter().enumerate().all(|(i,n)| v.0.get(i).unwrap_or(&0)==n))
                }
            } else if let Some(bound) = release(operand) {
                let order = compare(&v,&bound);
                Some(match op {
                    ">=" => !order.is_lt(), "<=" => !order.is_gt(), ">" => order.is_gt(), "<" => order.is_lt(),
                    "^"|"~" => {
                        let mut upper = bound.0.clone();
                        let index = if op=="^" {0} else {1};
                        upper.resize(upper.len().max(index+1),0);
                        upper[index] = upper[index].checked_add(1)?;
                        upper.truncate(index+1);
                        !order.is_lt() && compare(&v,&(upper,true)).is_lt()
                    },
                    _ => order.is_eq(),
                })
            } else { None };
            match result {Some(false)=>return Some(false),None=>unknown=true,_=>{}}
        }
        if unknown {None} else {Some(true)}
    }
    if let Some(array) = requirement.as_array() {
        let mut unknown = false;
        for item in array {
            match matches_range(version,item) {Some(true)=>return Some(true),None=>unknown=true,_=>{}}
        }
        return if unknown||array.is_empty() {None} else {Some(false)};
    }
    predicate(version, requirement.as_str()?)
}

#[tauri::command]
pub async fn check_mod_conflicts(profile_id: String) -> Result<Vec<String>, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let root = lib::root()?;
        let (version, engine) = lib::profile(&root, &profile_id)?;
        let mut scan = scan(&root, &version, &engine)?;
        let has = |ids: &[&str]| ids.iter().any(|id| scan.ids.contains(*id));
        for (left, right, message) in [
            (
                &["sodium", "embeddium"][..],
                &["optifine", "optifabric"][..],
                "Sodium or Embeddium may conflict with OptiFine.",
            ),
            (
                &["sodium"][..],
                &["rubidium"][..],
                "Sodium and Rubidium target different loader setups.",
            ),
            (
                &["sodium"][..],
                &["oculus"][..],
                "Check whether Oculus and standalone Sodium are compatible with this loadout.",
            ),
            (
                &["betterfps"][..],
                &["sodium", "embeddium", "rubidium"][..],
                "BetterFps may overlap with the installed renderer.",
            ),
            (
                &["lithium"][..],
                &["servercore"][..],
                "Lithium and ServerCore have overlapping server optimizations.",
            ),
            (
                &["starlight"][..],
                &["phosphor", "lambdynamiclights"][..],
                "Check Starlight compatibility with the other lighting mods.",
            ),
            (
                &["iris"][..],
                &["optifine", "optifabric"][..],
                "Iris and OptiFine both handle shader loading.",
            ),
            (
                &["canvas", "canvas-renderer"][..],
                &["sodium", "embeddium"][..],
                "Canvas and Sodium or Embeddium provide alternative renderers.",
            ),
        ] {
            if has(left) && has(right) {
                scan.findings.push(message.into());
            }
        }
        if scan.known_conflict {
            scan.findings.push(KNOWN_CONFLICT.into());
        }
        scan.findings.sort();
        Ok(scan.findings)
    })
    .await
    .map_err(|_| "Mod compatibility check interrupted".to_owned())?
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn fabric_release_ranges_cover_reported_1215_conflicts() {
        use serde_json::json;
        for range in [json!(">=1.21- <1.21.1-"),json!(">=1.21 <=1.21.1"),json!(["1.21","1.21.1"])] {
            assert_eq!(matches_range("1.21.5", &range), Some(false));
            assert_eq!(matches_range("1.21", &range), Some(true));
        }
        assert_eq!(matches_range("1.21.5", &json!(">=1.21.5 <1.22-")),Some(true));
        assert_eq!(matches_range("1.21.5", &json!("1.21.x")),Some(true));
        assert_eq!(matches_range("1.21.5", &json!("~1.21.1")),Some(true));
        assert_eq!(matches_range("1.21.5", &json!("^1.20")),Some(true));
        assert_eq!(matches_range("1.21.5", &json!(["unknown-snapshot","1.21"])),None);
        assert_eq!(matches_range("25w01a", &json!(">=1.21")),None);
    }
    #[test]
    fn mismatched_game_is_blocked_without_changing_files() {
        let root = tempfile::tempdir().unwrap();
        let game = root.path().join("game"); let mods = game.join("mods");
        fs::create_dir_all(&mods).unwrap();
        lib::atomic_json(&root.path().join("profiles.config.json"), &serde_json::json!({"appSettings":{"gameDir":game}})).unwrap();
        let path = mods.join("fabric-api.jar");
        let mut jar = zip::ZipWriter::new(fs::File::create(&path).unwrap());
        jar.start_file("fabric.mod.json",zip::write::SimpleFileOptions::default()).unwrap();
        jar.write_all(br#"{"id":"fabric-api","depends":{"minecraft":">=1.21- <1.21.1-"}}"#).unwrap(); jar.finish().unwrap();
        let before=fs::read(&path).unwrap();
        assert!(validate_launch(root.path(),"1.21.5","fabric").unwrap_err().contains("fabric-api.jar"));
        assert!(validate_launch(root.path(),"1.21","fabric").is_ok());
        assert_eq!(fs::read(&path).unwrap(),before);
        fs::rename(path,mods.join("fabric-api.jar.disabled")).unwrap();
        assert!(validate_launch(root.path(),"1.21.5","fabric").is_ok());
    }
    #[test]
    fn known_conflict_only_blocks_the_matching_enabled_mod() {
        let root = tempfile::tempdir().unwrap();
        let game = root.path().join("game");
        let mods = game.join("mods");
        fs::create_dir_all(&mods).unwrap();
        lib::atomic_json(
            &root.path().join("profiles.config.json"),
            &serde_json::json!({"appSettings":{"gameDir":game}}),
        )
        .unwrap();
        let path = mods.join("renamed.jar");
        let mut jar = zip::ZipWriter::new(fs::File::create(&path).unwrap());
        jar.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default())
            .unwrap();
        jar.write_all(br#"{"id":"kindscrystaloptimizer","version":"1.6.3"}"#)
            .unwrap();
        jar.finish().unwrap();
        assert!(validate_launch(root.path(), "1.21.11", "fabric").is_err());
        assert!(validate_launch(root.path(), "1.21.1", "fabric").is_ok());
        fs::rename(&path, mods.join("renamed.jar.disabled")).unwrap();
        assert!(validate_launch(root.path(), "1.21.11", "fabric").is_ok());
    }
    #[test]
    fn auto_fix_stashes_incompatible_mods_and_restores_matching_ones() {
        let root = tempfile::tempdir().unwrap();
        let game = root.path().join("game");
        let mods = game.join("mods");
        fs::create_dir_all(&mods).unwrap();
        lib::atomic_json(
            &root.path().join("profiles.config.json"),
            &serde_json::json!({"appSettings":{"gameDir":game}}),
        )
        .unwrap();
        let path = mods.join("sodium-1.21.1.jar");
        let mut jar = zip::ZipWriter::new(fs::File::create(&path).unwrap());
        jar.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default())
            .unwrap();
        jar.write_all(br#"{"id":"sodium","depends":{"minecraft":["1.21","1.21.1"]}}"#)
            .unwrap();
        jar.finish().unwrap();

        // Switching to 1.8.9 Fabric stashes the 1.21.1 mod so launch succeeds.
        assert_eq!(auto_fix_for_launch(root.path(), "1.8.9", "fabric").unwrap(), 1);
        assert!(!path.exists());
        assert!(root.path().join("rust-mod-stash/sodium-1.21.1.jar").is_file());
        assert!(validate_launch(root.path(), "1.8.9", "fabric").is_ok());

        // Switching back to 1.21.1 Fabric restores it automatically.
        assert_eq!(auto_fix_for_launch(root.path(), "1.21.1", "fabric").unwrap(), 1);
        assert!(path.is_file());
        assert!(validate_launch(root.path(), "1.21.1", "fabric").is_ok());
    }
}
