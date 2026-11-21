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
    "https://github.com/evilgamer123113-lang/bat-client-releases/releases/download/bat-mods-v6";
const MOD_BUNDLES: &[(&str, &str, u64)] = &[
    ("fabric-1.8", "80fa1a494b9585fdd2ebfc2d0e648c489c7dc616f1c4b336972672ff9d8b9ea3", 95874495),
    ("forge-1.8", "9be9c7074344339004b5100c5c02083fc62b36edb86cc92231954521f8378823", 97498571),
    ("fabric-1.8.9", "faa903671fe5ea9ba72cc8a7f4af7b9a625d14c791fd68e2f2ead88b1ff8512a", 95875317),
    ("forge-1.8.9", "706e7e462534f4786bcdd7bdea33810da86b490cc7712176a1ebe181dfcb0e02", 97499128),
    ("fabric-1.16.5", "db7a1a658270e12a3b089078bb447c8ebcc47e09e291c92ab9b9aba81d4545d4", 95928202),
    ("forge-1.16.5", "af7544d8a4a1dfe14950620ba1b1b046771b4a96f89db78cbb8bac64109f1651", 191609146),
    ("fabric-1.17.1", "4e1ed79be5f9e02c628dfd4da55d4e4bc1ea0ec474c3ecbd54c7c04e16f782db", 95925938),
    ("forge-1.17.1", "77674fbfd64a093fa328874b4080efaa5ce6f5b7f1e86352350e05db8b6ac3de", 191599095),
    ("fabric-1.18.2", "811c622d16ba61014636f7419a136860c688af18917f52b80847c65ce29b8e32", 95910791),
    ("forge-1.18.2", "7de22e6082014541d53d97531a668c64a125b97f28db0cdd6397895ff16fc799", 95925751),
    ("fabric-1.19.2", "96e63bafe4ed8b86e219a80a600e8cd64b3a7c4136c3c2d39c7e740256703fe0", 95912665),
    ("forge-1.19.2", "e9b503aef58f5a896275d4f377a9327c2f3a44f37723bab2e971113481722825", 95928042),
    ("fabric-1.19.4", "bc4ed3183a34848d56327aa926c1f944a86caaf09754b9f38fbcc75f344a78a9", 95912186),
    ("forge-1.19.4", "083f48a60db418bc7e8048938e8a5879cd98bd3323c8991d29df73320227b168", 95927552),
    ("fabric-1.20.1", "2e5369460ae123dd299c0a4cb06342f3c4338924f6a3bd095f5883ac97c9f27a", 95909901),
    ("forge-1.20.1", "dd251cb3ba697ae5590c820a020d2494a691ed6dabe3d290310fea05aa45bffe", 95924066),
    ("fabric-1.20.4", "69af3515223bf00e0ac8b1341818277074173b2e749c303312365771f5a98a75", 95905868),
    ("forge-1.20.4", "1e79e42f2d614c5e6ff52937faca46e75d9d77b43a61985b5c6b1d593af809cf", 95919593),
    ("fabric-1.20.6", "2dd21dfe4cf7549e15f6aa77bb4439f927854149ce3f9d247058e1fb9a6bdd4f", 95901992),
    ("forge-1.20.6", "fe053abaff49db8998c9f524a6ef939ce666f1fbaa08192397ef1ba2b64fa33d", 95916183),
    ("fabric-1.21.1", "16ef85b8965289e66cda4f5a120d6e6eeb785f8c7db8183c619d0899df26a39b", 95889005),
    ("forge-1.21.1", "9cb1f113a4fae4742d06efcf6c02c2f464ba8687021f86126abf2dd819a30a7b", 95915799),
    ("fabric-1.21.4", "dbcd18ceddeb9fde9cb452f1299665bf1bc5457a561c9858680d0efbd655ed5c", 95907204),
    ("forge-1.21.4", "229191b8bdc1a17593ce18b12af562c1d4ba4c43c7b2f3dbbb7ee1fe12c7a95b", 95922945),
    ("fabric-1.21.5", "602e1d734351dd096d5afa7856b61a3032fa4a6d7a8a88039ce70c571e545b91", 95917161),
    ("forge-1.21.5", "02b30bc218703212f891070ef0e59152a89a6dffed21bf3fb7b102961aae1ae9", 95932530),
    ("fabric-1.21.8", "b039c5fedc2582374a185a3fd44d30bc2f45a02f69045198da5c2c8a05fcee3d", 95914143),
    ("forge-1.21.8", "5dc75f57c2bb092dc30e92685cc48ad10eaa327e849cbc79f99fa2fe40211bc3", 95929658),
    ("fabric-1.21.11", "0648a4564d05431d9e9d4d688e5020589f7b0279087f458168e12898f3600e37", 95917304),
    ("forge-1.21.11", "d960d1cde7fee9a5f30929c7ad25a02678ab335fab5f9a853bae6dad490576d6", 95932727),
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
