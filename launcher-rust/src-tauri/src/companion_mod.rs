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
    "https://github.com/evilgamer123113-lang/bat-client-releases/releases/download/bat-mods-v2";
const MOD_BUNDLES: &[(&str, &str, u64)] = &[
    ("fabric-1.8", "90087e1369f66f31ba74736da53e672df2bf4404bcdadf186521fc2534986d6f", 22355793),
    ("forge-1.8", "4bc45de3ffc6247d23fabbf13f47d31e1e94cb3939bef42bed7299e93b098348", 23565512),
    ("fabric-1.8.9", "a8589decf6dd46c9946daf7cf8b7bb937ab7da4f2cc8be01e101ac47f63c0b62", 22356614),
    ("forge-1.8.9", "a2543a70cf2adc6de75f2f0fa6a69fc99c1ec56654780492e3790d92bd88897d", 23566063),
    ("fabric-1.16.5", "ce9b788fa14a0b996041f7ad0a02943c91cee5f9f5dec1a7fbc386afc2bebf8a", 22409498),
    ("forge-1.16.5", "631ad089a7ae40d219c0a8219a115fab57326ca4e4dbceebed1002d7f946b23f", 44178318),
    ("fabric-1.17.1", "6d62233eb5b2c16fc90b5f8eccd106927c97a490b58ae36493f79f466ea22f8f", 22407237),
    ("forge-1.17.1", "05d7dfdd0f3f9649b0b5dc94fa28ff42dab27896e9258c8f24132947ac700ace", 44168269),
    ("fabric-1.18.2", "13a48d3f4040bc0d5b61c45f61d103eaa3ccdf6e6042367014464ea543063ba6", 22392097),
    ("forge-1.18.2", "39ee6f49a375e42d22cc6d7da7210f15e03d0bfcc5581403f8a6c25eca7ff438", 22408456),
    ("fabric-1.19.2", "4c073b1a5211603bc82456c73bab7d0d2aecd0bf0003ac6b1c9355faed4dc00a", 22393967),
    ("forge-1.19.2", "a6b747e2ac11e59cb9cadd07e0ee68bb4d36d5a8af058c2c1c1974ba4774ba88", 22410754),
    ("fabric-1.19.4", "6a2ad639f04cb83f510d80da6e472d2f4664e0d660d9645954e8696c217a8377", 22393483),
    ("forge-1.19.4", "55f827c6a9e857a5afe8d04938a464c052c98ebf362477ba00d1d9af4e0cb2bd", 22410265),
    ("fabric-1.20.1", "dee73e6ce16ba47b7495089622f4e997fefde5cf437b67ad1ee83f8e69d144e4", 22391204),
    ("forge-1.20.1", "26e7a88540538b2ed10a3913c62371a5740f3e2487bec73784ef281085d7c0a3", 22406783),
    ("fabric-1.20.4", "cbc82153338b1661bf6e760f501b3c7c4bce309b20d95bb9c93e87ec755b26bc", 22387178),
    ("forge-1.20.4", "f8452cf037c7d022c38b35cce2ed991d9f13658204282f8477014bf254749394", 22402311),
    ("fabric-1.20.6", "77be454bc457676fab04ff64444666f40ff3789315ed19c7045177fb09b65f09", 22383294),
    ("forge-1.20.6", "d46799d100ab0928979d9ae4f1fc19d8cd2b3c3f20234b9724deaf69f76ac904", 22398905),
    ("fabric-1.21.1", "ba7f396c469f689e8c73b8f48665c977f16efce0826a3711a38b36353e9118da", 22370313),
    ("forge-1.21.1", "ddb0e1a49ac955a6e8ee4fa75445be511293d7b4f77e5b945e753fbc2b48cf48", 22398514),
    ("fabric-1.21.4", "b2b6cc73bb3bee1ca0e9efbd54adbe61f737e0b72b1c32ce64c5cf42c3f3c0ea", 22388509),
    ("forge-1.21.4", "760137396babb35dab8e2761e507e1040be66949846a3de020f38da7fecdee3e", 22405666),
    ("fabric-1.21.5", "ef3d060866c379eced12980c569de05628484edad4a0f1d4762714b370ed0184", 22398467),
    ("forge-1.21.5", "205692a638ef2622795f38e7eff0ca6bf8ffaea58af3b7520fabcb83809be6f3", 22415243),
    ("fabric-1.21.8", "0e5bea68cee663c1c86a499947a5f0bf6302d26e27a441123cc2a371d7f3c17a", 22395443),
    ("forge-1.21.8", "9dc36d5e7773a7ad32500df6fd81bbcc24de236a4c69b6e2e34aba90b8a21fb9", 22412380),
    ("fabric-1.21.11", "1b0d134891c5add90428170c5e9523f4e00174c501b2070111f46c2d0fbe6d21", 22398606),
    ("forge-1.21.11", "20223dbc86da1555e026024e1c623c52f6aea31c73598777fc3e8be3a1d588ab", 22415441),
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
    let http = crate::downloads::client_with_timeout(std::time::Duration::from_secs(300))?;
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
