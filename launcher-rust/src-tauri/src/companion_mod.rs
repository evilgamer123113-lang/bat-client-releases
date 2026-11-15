use crate::library as lib;
use serde::Serialize;
use std::{
    fs,
    io::{Read, Seek, SeekFrom, Write},
    path::{Path, PathBuf},
};

pub const SUPPORTED_VERSIONS: [&str; 15] = [
    "1.8", "1.8.9", "1.16.5", "1.17.1", "1.18.2", "1.19.2", "1.19.4", "1.20.1", "1.20.4",
    "1.20.6", "1.21.1", "1.21.4", "1.21.5", "1.21.8", "1.21.11",
];

pub const SUPPORTED_ENGINES: [&str; 2] = ["fabric", "forge"];

const PACK_MAGIC: &[u8; 8] = b"BATMODS1";
const PACK_MASK: [u8; 4] = [0x42, 0x41, 0x54, 0x21];

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CompanionModStatus {
    pub supported: bool,
    pub available: bool,
    pub source: Option<String>,
    pub message: String,
}

pub struct StagedCompanionMod {
    path: PathBuf,
}

impl Drop for StagedCompanionMod {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.path);
        let disabled = PathBuf::from(format!("{}.disabled", self.path.to_string_lossy()));
        let _ = fs::remove_file(disabled);
    }
}

pub fn is_supported(version: &str, engine: &str) -> bool {
    let v = version.trim();
    let e = engine.trim().to_ascii_lowercase();
    SUPPORTED_VERSIONS.contains(&v) && SUPPORTED_ENGINES.contains(&e.as_str())
}

fn valid_mod_jar(path: &Path, engine: &str) -> bool {
    let Ok(file) = fs::File::open(path) else {
        return false;
    };
    let Ok(jar) = zip::ZipArchive::new(file) else {
        return false;
    };
    match engine {
        "fabric" => jar.index_for_name("fabric.mod.json").is_some(),
        "forge" => {
            jar.index_for_name("META-INF/mods.toml").is_some()
                || jar.index_for_name("mcmod.info").is_some()
        }
        _ => false,
    }
}

fn find_pack_file() -> Option<PathBuf> {
    let mut candidates: Vec<PathBuf> = Vec::new();
    if let Ok(custom) = std::env::var("BAT_CLIENT_MOD_PACK") {
        candidates.push(PathBuf::from(custom));
    }
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            candidates.push(dir.join("resources").join("bat-mods.dat"));
            candidates.push(dir.join("bat-mods.dat"));
            if cfg!(target_os = "macos") {
                candidates.push(dir.join("..").join("Resources").join("resources").join("bat-mods.dat"));
                candidates.push(dir.join("..").join("Resources").join("bat-mods.dat"));
            } else if cfg!(target_os = "linux") {
                candidates.push(
                    dir.join("..")
                        .join("lib")
                        .join("bat-client-next")
                        .join("resources")
                        .join("bat-mods.dat"),
                );
                candidates.push(
                    dir.join("..")
                        .join("share")
                        .join("bat-client-next")
                        .join("bat-mods.dat"),
                );
            }
        }
    }
    if let Ok(root) = lib::root() {
        candidates.push(root.join("bat-mods.dat"));
    }
    candidates.push(
        Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("resources")
            .join("bat-mods.dat"),
    );
    candidates.push(PathBuf::from("D:/BatClientBuildCache/bat-mods.dat"));
    candidates.into_iter().find(|p| p.is_file())
}

fn read_u16_le(file: &mut fs::File) -> Result<u16, String> {
    let mut buf = [0u8; 2];
    file.read_exact(&mut buf).map_err(|_| "Corrupt mod pack")?;
    Ok(u16::from_le_bytes(buf))
}

fn read_u32_le(file: &mut fs::File) -> Result<u32, String> {
    let mut buf = [0u8; 4];
    file.read_exact(&mut buf).map_err(|_| "Corrupt mod pack")?;
    Ok(u32::from_le_bytes(buf))
}

fn read_u64_le(file: &mut fs::File) -> Result<u64, String> {
    let mut buf = [0u8; 8];
    file.read_exact(&mut buf).map_err(|_| "Corrupt mod pack")?;
    Ok(u64::from_le_bytes(buf))
}

fn find_pack_entry(pack_path: &Path, key: &str) -> Result<Option<(u64, u64)>, String> {
    let mut file = fs::File::open(pack_path).map_err(|_| "Cannot open bundled mod pack")?;
    let mut magic = [0u8; 8];
    if file.read_exact(&mut magic).is_err() || &magic != PACK_MAGIC {
        return Err("Invalid bundled mod pack header".into());
    }
    let count = read_u32_le(&mut file)?;
    if count > 256 {
        return Err("Invalid bundled mod pack index".into());
    }
    for _ in 0..count {
        let klen = read_u16_le(&mut file)? as usize;
        if klen > 128 {
            return Err("Invalid bundled mod key".into());
        }
        let mut kbuf = vec![0u8; klen];
        file.read_exact(&mut kbuf)
            .map_err(|_| "Corrupt bundled mod key")?;
        for (i, b) in kbuf.iter_mut().enumerate() {
            *b ^= PACK_MASK[i % PACK_MASK.len()];
        }
        let offset = read_u64_le(&mut file)?;
        let length = read_u64_le(&mut file)?;
        if kbuf == key.as_bytes() {
            return Ok(Some((offset, length)));
        }
    }
    Ok(None)
}

fn extract_from_pack(pack_path: &Path, offset: u64, length: u64, dest: &Path) -> Result<(), String> {
    let mut src = fs::File::open(pack_path).map_err(|_| "Cannot open bundled mod pack")?;
    src.seek(SeekFrom::Start(offset))
        .map_err(|_| "Cannot seek bundled mod pack")?;
    let tmp = dest.with_extension("tmp");
    let mut out = fs::File::create(&tmp).map_err(|_| "Cannot write staged companion mod")?;
    let mut remaining = length;
    let mut buf = vec![0u8; 64 * 1024];
    let mut pos: u64 = 0;
    while remaining > 0 {
        let want = std::cmp::min(remaining, buf.len() as u64) as usize;
        let n = src
            .read(&mut buf[..want])
            .map_err(|_| "Cannot read bundled companion mod")?;
        if n == 0 {
            let _ = fs::remove_file(&tmp);
            return Err("Truncated bundled companion mod".into());
        }
        for (i, b) in buf[..n].iter_mut().enumerate() {
            *b ^= PACK_MASK[((pos + i as u64) % PACK_MASK.len() as u64) as usize];
        }
        out.write_all(&buf[..n])
            .map_err(|_| "Cannot write staged companion mod")?;
        pos += n as u64;
        remaining -= n as u64;
    }
    out.flush()
        .map_err(|_| "Cannot flush staged companion mod")?;
    drop(out);
    if dest.exists() {
        let _ = fs::remove_file(dest);
    }
    fs::rename(&tmp, dest).map_err(|_| "Cannot finalize staged companion mod")?;
    Ok(())
}

fn find_local_port_jar(version: &str, engine: &str) -> Option<PathBuf> {
    let mut bases: Vec<PathBuf> = Vec::new();
    if let Ok(custom) = std::env::var("BAT_CLIENT_MOD_PORTS_DIR") {
        let p = PathBuf::from(custom);
        if p.is_dir() {
            bases.push(p);
        }
    }
    if let Ok(exe) = std::env::current_exe() {
        for ancestor in exe.ancestors().take(7) {
            let candidate = ancestor.join("bat-client-mod");
            if candidate.is_dir() {
                bases.push(candidate);
            }
        }
    }
    if let Ok(cwd) = std::env::current_dir() {
        for ancestor in cwd.ancestors().take(4) {
            let candidate = ancestor.join("bat-client-mod");
            if candidate.is_dir() {
                bases.push(candidate);
            }
        }
    }
    let manifest_mod = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("..")
        .join("..")
        .join("bat-client-mod");
    if manifest_mod.is_dir() {
        bases.push(manifest_mod);
    }

    for base in bases {
        let mut candidates: Vec<PathBuf> = Vec::new();
        if engine == "fabric" && version == "1.21.1" {
            candidates.push(base.join("build/libs/bat-client-mod-0.1.0.jar"));
        }
        if engine == "forge" && version == "1.21.1" {
            candidates.push(base.join(
                "ports/forge-1.21.1-full/build/libs/bat-client-forge-1.21.1-INTERNAL-UNVERIFIED-0.1.0-forge-internal.jar",
            ));
        }
        candidates.push(base.join(format!(
            "ports/{engine}-{version}/build/libs/bat-client-{engine}-{version}-INTERNAL-UNVERIFIED-0.1.0-{engine}-internal.jar"
        )));
        for candidate in candidates {
            if candidate.is_file() && valid_mod_jar(&candidate, engine) {
                return Some(candidate);
            }
        }
    }
    None
}

pub fn check_availability(version: &str, engine: &str) -> CompanionModStatus {
    if !is_supported(version, engine) {
        return CompanionModStatus {
            supported: false,
            available: false,
            source: None,
            message: format!(
                "Not supported ({engine} {version}). Bat Client mod supports 30 Fabric/Forge versions."
            ),
        };
    }

    let engine = engine.trim().to_ascii_lowercase();
    let version = version.trim();
    let key = format!("{engine}-{version}");

    if let Some(pack) = find_pack_file() {
        if matches!(find_pack_entry(&pack, &key), Ok(Some(_))) {
            return CompanionModStatus {
                supported: true,
                available: true,
                source: Some("bundled".into()),
                message: format!("Supported · Bat Client mod for {engine} {version} loads automatically on launch."),
            };
        }
    }

    if find_local_port_jar(version, &engine).is_some() {
        return CompanionModStatus {
            supported: true,
            available: true,
            source: Some("bundled".into()),
            message: format!("Supported · Bat Client mod for {engine} {version} loads automatically on launch."),
        };
    }

    CompanionModStatus {
        supported: true,
        available: false,
        source: None,
        message: format!("Supported ({engine} {version}) · Bundled mod package not found."),
    }
}

pub fn cleanup_stale_at(root: &Path) {
    let Ok(mods) = lib::mods_dir(root) else {
        return;
    };
    let Ok(entries) = fs::read_dir(&mods) else {
        return;
    };
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
        let is_managed = name.starts_with("bat-client-fabric-")
            || name.starts_with("bat-client-forge-");
        if is_managed && (name.ends_with(".jar") || name.ends_with(".jar.disabled")) {
            let _ = fs::remove_file(entry.path());
        }
    }
}

pub fn stage_for_launch(
    root: &Path,
    version: &str,
    engine: &str,
    progress: crate::game_install::Progress,
) -> Result<Option<StagedCompanionMod>, String> {
    cleanup_stale_at(root);
    if !is_supported(version, engine) {
        return Ok(None);
    }
    let engine = engine.trim().to_ascii_lowercase();
    let version = version.trim();
    let key = format!("{engine}-{version}");
    let mods = lib::mods_dir(root)?;
    fs::create_dir_all(&mods).map_err(|_| "Cannot access mods folder")?;
    let staged_path = mods.join(format!("bat-client-{engine}-{version}.jar"));

    if let Some(pack) = find_pack_file() {
        if let Some((offset, length)) = find_pack_entry(&pack, &key)? {
            progress("Preparing Bat Client companion mod…", None);
            extract_from_pack(&pack, offset, length, &staged_path)?;
            if !valid_mod_jar(&staged_path, &engine) {
                let _ = fs::remove_file(&staged_path);
                return Err("Bundled Bat Client mod failed verification".into());
            }
            return Ok(Some(StagedCompanionMod { path: staged_path }));
        }
    }

    if let Some(local) = find_local_port_jar(version, &engine) {
        progress("Preparing Bat Client companion mod…", None);
        fs::copy(&local, &staged_path)
            .map_err(|_| "Cannot stage Bat Client mod into mods folder")?;
        return Ok(Some(StagedCompanionMod { path: staged_path }));
    }

    Ok(None)
}

#[tauri::command]
pub async fn prepare_companion_mod(
    version: String,
    engine: String,
) -> Result<CompanionModStatus, String> {
    tauri::async_runtime::spawn_blocking(move || Ok(check_availability(&version, &engine)))
        .await
        .map_err(|_| "Cannot check companion mod".to_owned())?
}

#[cfg(test)]
mod tests {
    use super::*;

    fn write_fake_mod_jar(path: &Path, engine: &str) {
        let file = fs::File::create(path).unwrap();
        let mut zip = zip::ZipWriter::new(file);
        let opts = zip::write::SimpleFileOptions::default();
        if engine == "fabric" {
            zip.start_file("fabric.mod.json", opts).unwrap();
            zip.write_all(br#"{"schemaVersion":1,"id":"batclient","version":"0.1.0"}"#)
                .unwrap();
        } else {
            zip.start_file("META-INF/mods.toml", opts).unwrap();
            zip.write_all(b"modLoader=\"javafml\"\n[[mods]]\nmodId=\"batclient\"\n")
                .unwrap();
        }
        zip.finish().unwrap();
    }

    fn write_fake_pack(pack_path: &Path, entries: &[(&str, &[u8])]) {
        let mut out = fs::File::create(pack_path).unwrap();
        out.write_all(PACK_MAGIC).unwrap();
        out.write_all(&(entries.len() as u32).to_le_bytes()).unwrap();

        let mut header_size = 8 + 4;
        for (k, _) in entries {
            header_size += 2 + k.len() + 8 + 8;
        }
        let mut cursor = header_size as u64;
        for (k, data) in entries {
            out.write_all(&(k.len() as u16).to_le_bytes()).unwrap();
            let mut kbuf = k.as_bytes().to_vec();
            for (i, b) in kbuf.iter_mut().enumerate() {
                *b ^= PACK_MASK[i % PACK_MASK.len()];
            }
            out.write_all(&kbuf).unwrap();
            out.write_all(&cursor.to_le_bytes()).unwrap();
            out.write_all(&(data.len() as u64).to_le_bytes()).unwrap();
            cursor += data.len() as u64;
        }
        for (_, data) in entries {
            let mut masked = data.to_vec();
            for (i, b) in masked.iter_mut().enumerate() {
                *b ^= PACK_MASK[i % PACK_MASK.len()];
            }
            out.write_all(&masked).unwrap();
        }
    }

    #[test]
    fn supports_exact_30_combinations() {
        let mut count = 0;
        for v in SUPPORTED_VERSIONS {
            for e in SUPPORTED_ENGINES {
                assert!(is_supported(v, e), "{e} {v} should be supported");
                count += 1;
            }
        }
        assert_eq!(count, 30);
        assert!(!is_supported("1.21.1", "vanilla"));
        assert!(!is_supported("1.21.1", "neoforge"));
        assert!(!is_supported("1.21.1", "quilt"));
        assert!(!is_supported("1.21.3", "fabric"));
        assert!(!is_supported("1.7.10", "forge"));
    }

    #[test]
    fn extracts_from_opaque_pack_and_removes_on_game_exit() {
        let root = tempfile::tempdir().unwrap();
        let mods = root.path().join("minecraft_runtime").join("mods");
        fs::create_dir_all(&mods).unwrap();
        let user_mod = mods.join("sodium-unrelated.jar");
        fs::write(&user_mod, b"keep-me").unwrap();

        let jar_src = root.path().join("temp-mod.jar");
        write_fake_mod_jar(&jar_src, "fabric");
        let jar_bytes = fs::read(&jar_src).unwrap();
        let pack_path = root.path().join("bat-mods.dat");
        write_fake_pack(&pack_path, &[("fabric-1.21.1", &jar_bytes)]);

        // Extract directly via pack helpers into mods/
        let (offset, length) = find_pack_entry(&pack_path, "fabric-1.21.1")
            .unwrap()
            .expect("entry should exist");
        let staged_path = mods.join("bat-client-fabric-1.21.1.jar");
        extract_from_pack(&pack_path, offset, length, &staged_path).unwrap();
        assert!(valid_mod_jar(&staged_path, "fabric"));

        let guard = StagedCompanionMod {
            path: staged_path.clone(),
        };
        assert!(staged_path.is_file());
        drop(guard);
        assert!(!staged_path.exists(), "mod must be removed when game closes");
        assert!(user_mod.is_file(), "unrelated user mod must remain");
    }

    #[test]
    fn cleanup_stale_removes_only_managed_bat_jars() {
        let root = tempfile::tempdir().unwrap();
        let mods = root.path().join("minecraft_runtime").join("mods");
        fs::create_dir_all(&mods).unwrap();
        let stale = mods.join("bat-client-forge-1.8.9.jar");
        let user = mods.join("optifine.jar");
        fs::write(&stale, b"stale").unwrap();
        fs::write(&user, b"user").unwrap();
        cleanup_stale_at(root.path());
        assert!(!stale.exists());
        assert!(user.is_file());
    }

    #[test]
    fn packed_bundle_contains_all_30_targets_when_present() {
        let pack = Path::new("D:/BatClientBuildCache/bat-mods.dat");
        if !pack.is_file() {
            return;
        }
        for v in SUPPORTED_VERSIONS {
            for e in SUPPORTED_ENGINES {
                let key = format!("{e}-{v}");
                let entry = find_pack_entry(pack, &key).unwrap();
                assert!(entry.is_some(), "Missing {key} in bat-mods.dat");
            }
        }
    }
}
