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
    "https://github.com/evilgamer123113-lang/bat-client-releases/releases/download/bat-mods-v9";
const MOD_BUNDLES: &[(&str, &str, u64)] = &[
    ("fabric-1.8", "e01ca7750fa4f8cefb4370d1d82ec2b57fce722fda10053d0b6aadc9a0dec38a", 95875551),
    ("forge-1.8", "9a36502adbd35be3cd3ebe809ca819f8690bbb27152c877fdb7b0da0e12a7135", 97499558),
    ("fabric-1.8.9", "3e80e965912ddfade060195f711d25fd6cd893a250f1276eaed86cdd60b033ca", 95876380),
    ("forge-1.8.9", "8139bfc9946cb7409b3ff0949e379f83dc36f48d8e8ca5014ba1404842270d22", 97500108),
    ("fabric-1.16.5", "71b689b59e97b7d341d94bdf6a1011812da1b87139b98b6138b77e27cb2ed97a", 95929299),
    ("forge-1.16.5", "1dadeec37acdd37e1606de2bc4e7a286b113725a869d42def745459400908094", 191610167),
    ("fabric-1.17.1", "96018cb6d02ad98a1184871e3cf92a6261f1da7176d29a45d73bb598aca4a4ff", 95927115),
    ("forge-1.17.1", "4bd52398e67acf6e4a29862d72dc00e3193737c4fea64250f82516a39a23ec2a", 191600177),
    ("fabric-1.18.2", "41b4e1f24fb711f54e5ccfb957e46c17e9a02af0b7757505bcbcf72b41220a1c", 95911965),
    ("forge-1.18.2", "ef4b46e4034e682e797641c139aa930f5c4e4da3ed3193c60252536d77a72477", 95926828),
    ("fabric-1.19.2", "acd7e9d9c3017cdf74d0badf54f8418a5a84c2f32c61d5ee1d6f84549c2c75ca", 95913838),
    ("forge-1.19.2", "0f46e1a00d053a1cffd460614fb3836f8454403998f8f78542f60484b91c9e44", 95929132),
    ("fabric-1.19.4", "be1308f36b5e3e6b1e5049ac7671312ea13680130d2306e265855da9256aaf41", 95913357),
    ("forge-1.19.4", "0d4b6f91a7f98d08c7718e1bfa6f4bc1290942198c0362cef616864f0dd2a9ab", 95928641),
    ("fabric-1.20.1", "b2c24b2272169519fd73561a2df7fe9b9b4a8b636efa151884a7754189bf1ea0", 95911074),
    ("forge-1.20.1", "6686bcd6977dc7b7e604e47248643546e1d2f71aea7b5f79d851bd4fe8e39541", 95925149),
    ("fabric-1.20.4", "f3ba978c032ace989ddf894e48c9afcae94b2daf747b8c62989c1209b785b481", 95907043),
    ("forge-1.20.4", "6db923edfa7b2993d0be3038f4b307391c340d8a7b63b376fce6538211f269e7", 95920678),
    ("fabric-1.20.6", "36d5d7203f3e5be56a153dc90d61d787b373eafd25afc0c963bc9a94659bb88c", 95903158),
    ("forge-1.20.6", "766a2ae9202afb4d4e5ab831eeafc1cd5085f6c202395769104c13a6fef861f6", 95917278),
    ("fabric-1.21.1", "29162c62d79a5dcda55ba8b46be601baf03ad6e79697b9c4f482b0e80039cf28", 95890097),
    ("forge-1.21.1", "9e160891e751d7f598ddcbfa0eabfc4caeac81796e103664ce267650f65adbb8", 95916880),
    ("fabric-1.21.4", "b437dcea64153b8b43fc669f1f4472883e30aa4c6a453edd51a74d805ece1983", 95908379),
    ("forge-1.21.4", "e3786f548ac7711c4233a81de932aa95bd01a64760c1518cdee8eef70474976c", 95924025),
    ("fabric-1.21.5", "3f04577e7f6840f241b81edd94b92a5239c55739c556f8a63674ef9213dedbed", 95918333),
    ("forge-1.21.5", "17234e28437c90bd622dd5e9077c0762bf66267d3a99ec0d42f86178648dc573", 95933536),
    ("fabric-1.21.8", "ae498d2a19ac326b1f9303f49a6276188d04aaae1c3d6514796cd481eddba689", 95915319),
    ("forge-1.21.8", "ef86f5c886a8d06afe304589c72562391af4f9aeab00d1c646266f2e0e73c71a", 95930661),
    ("fabric-1.21.11", "3305f28c48e9d3f40c411f47dd70fa7b12851da9c69f60b47f9e0d7643b9859e", 95918483),
    ("forge-1.21.11", "683b6b5f0f7ec333eddbc0db5dd552042f79be66936f1b81c59ae6547c1ee803", 95933800),
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
