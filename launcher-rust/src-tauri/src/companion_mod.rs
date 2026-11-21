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
const MOD_BUNDLE_BASE_URL: &str =
    "https://github.com/evilgamer123113-lang/bat-client-releases/releases/download/bat-mods-v5";
const MOD_BUNDLES: &[(&str, &str, u64)] = &[
    ("fabric-1.8", "ac500b5521854011913ba9f61c00bfacfaff3d36e7d4900ef444f368f537be29", 95873690),
    ("forge-1.8", "e62309579ec3c9f6946c50a4be7414d4377a418ddae1383e5247ac8c5374202e", 97497766),
    ("fabric-1.8.9", "ad3b0e27a33e377d96589ade2beb5d28926c18e16381085df61ea999228dba2e", 95874512),
    ("forge-1.8.9", "e67815a7af0b93597c81e15f48cccab4b32083d12d7e16d8fa62e3ed97126e0a", 97498323),
    ("fabric-1.16.5", "747f5e52329006d76fad7d01c5ae83bb5e608d5e905488b21c5c8899e046ec1f", 95927397),
    ("forge-1.16.5", "001cf0eb908bd787b8ab5f86a62ed32f94d79e49e3551c4bc50cb1ab6c554e66", 191608341),
    ("fabric-1.17.1", "7db1b12a65cd4a463d1eb3dd085559cb4ea98b445af4ed7661b6a0097c54d457", 95925139),
    ("forge-1.17.1", "229d5b957f6ad3f8616ce5bf753359ae89b0e2f49ed10c14f3f36e83d7f0e623", 191598295),
    ("fabric-1.18.2", "f5dea90f981a97984ddab533f9edfd392651250b2ebebc15c6ad6af6965aa0b8", 95909993),
    ("forge-1.18.2", "e04d09e39ad83cdcd7fdd9c92320e11e7d0f405a9acababfbaebf78640bdea31", 95924948),
    ("fabric-1.19.2", "e8732f4deac0ab060003593039c478fc3af4679a56461fce5d58a67443725898", 95911867),
    ("forge-1.19.2", "7f0e9a486f97794a0035dd69854f55006dcf88d0307df41f1058bb0fc9f4628e", 95927239),
    ("fabric-1.19.4", "9614d7d32cef3e32a7072ae04c98d90fb42c9c1fe79bcd87fd1f86279d01aa4a", 95911388),
    ("forge-1.19.4", "17ee625ee5922a14a84c6f468e9b023083b5a1b98fdc4d13aac1a9148d9682ea", 95926749),
    ("fabric-1.20.1", "43c4cd95859be43295227d93c187b35047fd0c04d2bbcfa154e251d59925e2d2", 95909103),
    ("forge-1.20.1", "f7e0acba40a5313d8e3e2c80856bb2a366b101448ecbeaec551598e51f39a2d5", 95923263),
    ("fabric-1.20.4", "edd77d44b6e3999871fcc25df5fb16511d18b8b1d82f86e2c2ce2634bf01debb", 95905070),
    ("forge-1.20.4", "f008d84da1a716698b577164cabe8913caa71ba59ed36d788125190847eb27f9", 95918790),
    ("fabric-1.20.6", "429847fc772815a1fd16ddf5c534881883022df13ea990caa817d2f082cea556", 95901193),
    ("forge-1.20.6", "13a07580c5c940b59e5c4f8179bf19734b6403b39af5e04065997266f64630e4", 95915380),
    ("fabric-1.21.1", "603b103696739d6fc769e82c4d16da2483bcf17f6b6cf436735abbe7506d646f", 95888207),
    ("forge-1.21.1", "8e8106b519f39a43d3a4aeccd1f1f65f208f768bb2b5db3fac98d88344b36d86", 95915000),
    ("fabric-1.21.4", "17f42a4489d7cc26b9c5b695172dc0e16e106b9119def940b22b27691fc3b938", 95906406),
    ("forge-1.21.4", "5ff4b0689c8d21f7e68963937adf88040e85a5e5a8d2e8e56da4f5e8d0476772", 95922146),
    ("fabric-1.21.5", "aea750b94413e58a60d0179d6d3ca147d98c9a972b2a5a47c9b5d22408d06f68", 95916363),
    ("forge-1.21.5", "09656db8a6631abf67ba6c96b77e8581300f9c09a0851a3880970ca5aa3215d1", 95931731),
    ("fabric-1.21.8", "f70ba103d57e27a8d5409ea63d27d05dd82eed3a47b0d620bd24832ca90827cc", 95913345),
    ("forge-1.21.8", "d0e4523ccd9b09e210d13c41f5a3ede9a1916af1c0e7b87211f9830839a468f9", 95928859),
    ("fabric-1.21.11", "e125a192ca2be2ffedb59c39e5a84803e8b07d472577b58e77b0041f25537afb", 95916506),
    ("forge-1.21.11", "93db43e9232a9279f1173b3a34b373b338e2b01d104619b68b08a439d1c4d5bb", 95931928),
];
static PACK_DOWNLOAD_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

fn bundle_meta(key: &str) -> Option<(&'static str, u64)> {
    MOD_BUNDLES
        .iter()
        .find(|(k, _, _)| *k == key)
        .map(|(_, sha, size)| (*sha, *size))
}

fn bundle_matches(path: &Path, expected_sha: &str, expected_size: u64) -> bool {
    let Ok(meta) = fs::metadata(path) else {
        return false;
    };
    if meta.len() != expected_size {
        return false;
    }
    let Ok(mut file) = fs::File::open(path) else {
        return false;
    };
    use sha2::{Digest, Sha256};
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        let Ok(n) = file.read(&mut buf) else {
            return false;
        };
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    let hex: String = hasher.finalize().iter().map(|b| format!("{b:02x}")).collect();
    hex.eq_ignore_ascii_case(expected_sha)
}

fn cached_target_bundle_path(key: &str) -> Option<PathBuf> {
    let (sha, size) = bundle_meta(key)?;
    let path = lib::root()
        .ok()?
        .join("companion-bundles")
        .join(format!("bat-mods-{key}.dat"));
    if bundle_matches(&path, sha, size) {
        Some(path)
    } else {
        None
    }
}

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

    if cached_target_bundle_path(&key).is_some() || find_local_port_jar(version, &engine).is_some() {
        return CompanionModStatus {
            supported: true,
            available: true,
            source: Some("bundled".into()),
            message: format!("Supported · Bat Client mod for {engine} {version} loads automatically on launch."),
        };
    }

    CompanionModStatus {
        supported: true,
        available: true,
        source: Some("download".into()),
        message: format!(
            "Supported · Bat Client mod for {engine} {version} downloads automatically on launch."
        ),
    }
}

fn ensure_target_bundle_downloaded(
    key: &str,
    progress: Option<&crate::game_install::Progress>,
) -> Result<PathBuf, String> {
    if let Some(existing) = cached_target_bundle_path(key) {
        return Ok(existing);
    }
    let (expected_sha, size) =
        bundle_meta(key).ok_or_else(|| format!("Unsupported companion mod target: {key}"))?;
    let _guard = PACK_DOWNLOAD_LOCK
        .lock()
        .map_err(|_| "Companion mod downloader busy")?;
    if let Some(existing) = cached_target_bundle_path(key) {
        return Ok(existing);
    }
    let dir = lib::root()?.join("companion-bundles");
    fs::create_dir_all(&dir).map_err(|_| "Cannot create companion bundle folder")?;
    let dest = dir.join(format!("bat-mods-{key}.dat"));
    let url = format!("{MOD_BUNDLE_BASE_URL}/bat-mods-{key}.dat");
    if let Some(cb) = progress {
        cb("Downloading Bat Client companion mod…", Some(0.0));
    }
    let http = crate::downloads::client_with_timeout(std::time::Duration::from_secs(600))?;
    crate::downloads::file(
        &http,
        &url,
        &dest,
        expected_sha,
        Some(size),
        size,
        |done, total| {
            if let Some(cb) = progress {
                let pct = if total > 0 {
                    Some((done as f64 / total as f64).clamp(0.0, 1.0))
                } else {
                    None
                };
                cb("Downloading Bat Client companion mod…", pct);
            }
        },
    )?;
    Ok(dest)
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

    if let Some(pack) = cached_target_bundle_path(&key) {
        if let Some((offset, length)) = find_pack_entry(&pack, &key)? {
            progress("Preparing Bat Client companion mod…", None);
            extract_from_pack(&pack, offset, length, &staged_path)?;
            if !valid_mod_jar(&staged_path, &engine) {
                let _ = fs::remove_file(&staged_path);
                return Err("Cached Bat Client mod failed verification".into());
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

    let pack = ensure_target_bundle_downloaded(&key, Some(&progress))?;
    if let Some((offset, length)) = find_pack_entry(&pack, &key)? {
        progress("Preparing Bat Client companion mod…", None);
        extract_from_pack(&pack, offset, length, &staged_path)?;
        if !valid_mod_jar(&staged_path, &engine) {
            let _ = fs::remove_file(&staged_path);
            return Err("Downloaded Bat Client mod failed verification".into());
        }
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
    fn mod_bundle_manifest_covers_all_30_targets() {
        assert_eq!(MOD_BUNDLES.len(), 30);
        for v in SUPPORTED_VERSIONS {
            for e in SUPPORTED_ENGINES {
                let key = format!("{e}-{v}");
                let (sha, size) = bundle_meta(&key).unwrap_or_else(|| panic!("Missing {key}"));
                assert_eq!(sha.len(), 64, "bad sha length for {key}");
                assert!(sha.chars().all(|c| c.is_ascii_hexdigit()), "bad sha hex for {key}");
                assert!(size > 1_000_000, "unexpected small size for {key}");
            }
        }
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
