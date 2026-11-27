//! Preserve a renderer opt-out inside old Bat packages without changing protected payload bytes.
use crate::library as lib;
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    collections::HashSet,
    fs,
    io::{Cursor, Read, Write},
    path::Path,
};
fn hash(b: &[u8]) -> String {
    format!("{:x}", Sha256::digest(b))
}
fn metadata<R: Read + std::io::Seek>(
    zip: &mut zip::ZipArchive<R>,
) -> Result<Option<Value>, String> {
    let file = match zip.by_name("fabric.mod.json") {
        Ok(f) => f,
        Err(zip::result::ZipError::FileNotFound) => return Ok(None),
        Err(_) => return Err("Cannot inspect mod metadata".into()),
    };
    if file.size() > 1024 * 1024 {
        return Err("Mod metadata exceeds limit".into());
    }
    serde_json::from_reader(file.take(1024 * 1024 + 1))
        .map(Some)
        .map_err(|_| "Invalid mod metadata".into())
}
fn transformed(original: &[u8]) -> Result<Option<Vec<u8>>, String> {
    let mut zip = zip::ZipArchive::new(Cursor::new(original)).map_err(|_| "Invalid mod archive")?;
    if zip.len() > 20000 {
        return Err("Mod archive too large".into());
    }
    let Some(mut info) = metadata(&mut zip)? else {
        return Ok(None);
    };
    if info["id"] != "batclient" {
        return Ok(None);
    }
    let mut removed = HashSet::new();
    for item in info["jars"].as_array().into_iter().flatten() {
        let Some(name) = item["file"].as_str() else {
            continue;
        };
        let Some(file) = name.strip_prefix("META-INF/jars/") else {
            continue;
        };
        if file.contains('/') || !file.ends_with(".jar") {
            continue;
        }
        let mut entry = zip
            .by_name(name)
            .map_err(|_| "Declared nested mod is missing")?;
        if entry.size() > 64 * 1024 * 1024 {
            return Err("Nested renderer archive exceeds limit".into());
        }
        let mut data = vec![];
        entry
            .by_ref()
            .take(64 * 1024 * 1024 + 1)
            .read_to_end(&mut data)
            .map_err(|_| "Cannot inspect nested mod")?;
        let mut nested =
            zip::ZipArchive::new(Cursor::new(data)).map_err(|_| "Invalid nested mod")?;
        if metadata(&mut nested)?.is_some_and(|v| v["id"] == "sodium") {
            removed.insert(name.to_owned());
        }
    }
    if removed.is_empty() {
        return Ok(None);
    }
    if info["depends"].get("sodium").is_some() {
        return Err("This Bat package requires its nested Sodium. Install the current renderer-optional Bat mod.".into());
    }
    for i in 0..zip.len() {
        let f = zip
            .by_index(i)
            .map_err(|_| "Cannot inspect archive signature")?;
        let name = f.name().to_ascii_uppercase();
        if name.starts_with("META-INF/")
            && [".SF", ".RSA", ".DSA", ".EC"]
                .iter()
                .any(|s| name.ends_with(s))
        {
            return Err(
                "Signed Bat archive preserved; install the current renderer-optional Bat mod."
                    .into(),
            );
        }
    }
    let jars = info["jars"]
        .as_array()
        .cloned()
        .unwrap_or_default()
        .into_iter()
        .filter(|v| !v["file"].as_str().is_some_and(|n| removed.contains(n)))
        .collect::<Vec<_>>();
    info["jars"] = json!(jars);
    let mut output = zip::ZipWriter::new(Cursor::new(Vec::new()));
    for i in 0..zip.len() {
        let entry = zip
            .by_index(i)
            .map_err(|_| "Cannot read protected mod entry")?;
        if entry.name() == "fabric.mod.json" || removed.contains(entry.name()) {
            continue;
        }
        output
            .raw_copy_file(entry)
            .map_err(|_| "Cannot preserve protected mod entry")?;
    }
    output
        .start_file(
            "fabric.mod.json",
            zip::write::SimpleFileOptions::default()
                .compression_method(zip::CompressionMethod::Deflated),
        )
        .map_err(|_| "Cannot write mod metadata")?;
    output
        .write_all(&serde_json::to_vec_pretty(&info).map_err(|_| "Cannot encode metadata")?)
        .map_err(|_| "Cannot stage metadata")?;
    let bytes = output
        .finish()
        .map_err(|_| "Cannot finish renderer migration")?
        .into_inner();
    let mut verified = zip::ZipArchive::new(Cursor::new(&bytes))
        .map_err(|_| "Cannot verify renderer migration")?;
    let mut total = 0u64;
    for i in 0..zip.len() {
        let mut old = zip
            .by_index(i)
            .map_err(|_| "Cannot verify protected entry")?;
        if old.is_dir() || old.name() == "fabric.mod.json" || removed.contains(old.name()) {
            continue;
        }
        total = total.saturating_add(old.size());
        if total > 512 * 1024 * 1024 {
            return Err("Protected mod expanded size exceeds limit".into());
        }
        let mut replacement = verified
            .by_name(old.name())
            .map_err(|_| "Protected mod entry was lost")?;
        if old.size() != replacement.size() {
            return Err("Protected mod contents changed".into());
        }
        let a = stream_hash(&mut old)?;
        let b = stream_hash(&mut replacement)?;
        if a != b {
            return Err("Protected mod contents changed; original preserved".into());
        }
    }
    Ok(Some(bytes))
}
fn stream_hash(r: &mut impl Read) -> Result<Vec<u8>, String> {
    let mut hash = Sha256::new();
    let mut buf = [0; 65536];
    loop {
        let n = r.read(&mut buf).map_err(|_| "Cannot verify mod entry")?;
        if n == 0 {
            break;
        }
        hash.update(&buf[..n]);
    }
    Ok(hash.finalize().to_vec())
}
pub fn apply(root: &Path) -> Result<usize, String> {
    let choices = lib::read_json(&root.join("dismissed-mods.json"), json!([]))?;
    if !choices
        .as_array()
        .is_some_and(|a| a.iter().any(|v| v == "AANobbMI"))
    {
        return Ok(0);
    }
    let dir = lib::mods_dir(root)?;
    if !dir.exists() {
        return Ok(0);
    }
    // If the user has an enabled Sodium JAR in mods/ (e.g. re-installed or re-enabled),
    // Sodium is NOT opted out — never disable Sodium Extra / Reese's Sodium Options.
    for item in fs::read_dir(&dir).map_err(|_| "Cannot inspect renderer opt-out")? {
        let Ok(item) = item else { continue };
        let name = item.file_name().to_string_lossy().into_owned();
        if !name.ends_with(".jar") {
            continue;
        }
        if let Ok(path) = lib::regular_file(&dir, &name) {
            let has_sodium = fs::File::open(&path)
                .ok()
                .and_then(|f| zip::ZipArchive::new(f).ok())
                .and_then(|mut z| metadata(&mut z).ok().flatten())
                .is_some_and(|m| m["id"] == "sodium");
            if has_sodium {
                return Ok(0);
            }
        }
    }
    let mut changed = 0;
    for item in fs::read_dir(&dir).map_err(|_| "Cannot inspect renderer opt-out")? {
        let item = item.map_err(|_| "Cannot inspect mod file")?;
        let name = item.file_name().to_string_lossy().into_owned();
        if !name.ends_with(".jar") {
            continue;
        }
        let path = lib::regular_file(&dir, &name)?;
        let info = fs::File::open(&path)
            .ok()
            .and_then(|f| zip::ZipArchive::new(f).ok())
            .and_then(|mut z| metadata(&mut z).ok().flatten());
        if info.as_ref().is_some_and(|m| {
            matches!(
                m["id"].as_str(),
                Some("sodium-extra" | "reeses-sodium-options")
            )
        }) {
            let disabled = dir.join(format!("{name}.disabled"));
            if fs::symlink_metadata(&disabled).is_ok() {
                return Err(
                    "A disabled Sodium add-on already exists; both files were preserved".into(),
                );
            }
            fs::rename(&path, &disabled)
                .map_err(|_| "Cannot disable Sodium-dependent add-on; its file was preserved")?;
            changed += 1;
            continue;
        }
        if !info.is_some_and(|m| m["id"] == "batclient") {
            continue;
        }
        let size = fs::metadata(&path)
            .map_err(|_| "Cannot inspect Bat mod")?
            .len();
        if size > 100 * 1024 * 1024 {
            return Err("Mod is too large for safe renderer migration".into());
        }
        let original = fs::read(&path).map_err(|_| "Cannot read mod")?;
        let Some(output) = transformed(&original)? else {
            continue;
        };
        let backups = root.join("mod-backups");
        fs::create_dir_all(&backups).map_err(|_| "Cannot create renderer backup")?;
        let backup = backups.join(format!("{}.jar.backup", hash(&original)));
        if backup.exists() {
            if hash(&fs::read(&backup).map_err(|_| "Cannot verify renderer backup")?)
                != hash(&original)
            {
                return Err("Renderer backup mismatch; original preserved".into());
            }
        } else {
            let mut file = fs::OpenOptions::new()
                .write(true)
                .create_new(true)
                .open(&backup)
                .map_err(|_| "Cannot preserve original Bat package")?;
            file.write_all(&original)
                .and_then(|_| file.sync_all())
                .map_err(|_| "Cannot preserve original Bat package")?;
        }
        let mut temp =
            tempfile::NamedTempFile::new_in(&dir).map_err(|_| "Cannot stage renderer choice")?;
        temp.write_all(&output)
            .and_then(|_| temp.as_file().sync_all())
            .map_err(|_| "Cannot save renderer choice")?;
        if hash(&fs::read(&path).map_err(|_| "Cannot recheck original")?) != hash(&original) {
            return Err("Mod changed during migration; original preserved".into());
        }
        temp.persist(&path)
            .map_err(|_| "Cannot commit renderer choice")?;
        changed += 1;
    }
    Ok(changed)
}
#[cfg(test)]
mod tests {
    use super::*;
    fn jar(meta: Value, entries: Vec<(&str, Vec<u8>)>) -> Vec<u8> {
        let mut z = zip::ZipWriter::new(Cursor::new(vec![]));
        for (name, data) in
            std::iter::once(("fabric.mod.json", serde_json::to_vec(&meta).unwrap())).chain(entries)
        {
            z.start_file(name, zip::write::SimpleFileOptions::default())
                .unwrap();
            z.write_all(&data).unwrap();
        }
        z.finish().unwrap().into_inner()
    }
    #[test]
    fn protected_payload_and_backup_preserved() {
        let nested = jar(json!({"id":"sodium"}), vec![]);
        let original = jar(
            json!({"id":"batclient","jars":[{"file":"META-INF/jars/renderer.jar"}]}),
            vec![
                ("META-INF/jars/renderer.jar", nested),
                ("protected.bin", vec![7; 128]),
            ],
        );
        let root = tempfile::tempdir().unwrap();
        let game = root.path().join("game");
        fs::create_dir_all(game.join("mods")).unwrap();
        lib::atomic_json(
            &root.path().join("profiles.config.json"),
            &json!({"appSettings":{"gameDir":game}}),
        )
        .unwrap();
        lib::atomic_json(
            &root.path().join("dismissed-mods.json"),
            &json!(["AANobbMI"]),
        )
        .unwrap();
        fs::write(game.join("mods/bat.jar"), &original).unwrap();
        assert_eq!(apply(root.path()).unwrap(), 1);
        let mut zip =
            zip::ZipArchive::new(fs::File::open(game.join("mods/bat.jar")).unwrap()).unwrap();
        assert!(zip.by_name("META-INF/jars/renderer.jar").is_err());
        let mut payload = vec![];
        zip.by_name("protected.bin")
            .unwrap()
            .read_to_end(&mut payload)
            .unwrap();
        assert_eq!(payload, vec![7; 128]);
        assert_eq!(
            fs::read(
                root.path()
                    .join("mod-backups")
                    .join(format!("{}.jar.backup", hash(&original)))
            )
            .unwrap(),
            original
        );
        assert_eq!(apply(root.path()).unwrap(), 0);
    }
    #[test]
    fn opt_out_disables_only_required_addons() {
        let root = tempfile::tempdir().unwrap();
        let game = root.path().join("game");
        fs::create_dir_all(game.join("mods")).unwrap();
        lib::atomic_json(
            &root.path().join("profiles.config.json"),
            &json!({"appSettings":{"gameDir":game}}),
        )
        .unwrap();
        let addon = jar(json!({"id":"sodium-extra"}), vec![]);
        fs::write(game.join("mods/addon.jar"), &addon).unwrap();
        assert_eq!(apply(root.path()).unwrap(), 0);
        lib::atomic_json(
            &root.path().join("dismissed-mods.json"),
            &json!(["AANobbMI"]),
        )
        .unwrap();
        assert_eq!(apply(root.path()).unwrap(), 1);
        assert_eq!(
            fs::read(game.join("mods/addon.jar.disabled")).unwrap(),
            addon
        );
    }
    #[test]
    fn enabled_sodium_prevents_addon_disable() {
        let root = tempfile::tempdir().unwrap();
        let game = root.path().join("game");
        fs::create_dir_all(game.join("mods")).unwrap();
        lib::atomic_json(
            &root.path().join("profiles.config.json"),
            &json!({"appSettings":{"gameDir":game}}),
        )
        .unwrap();
        lib::atomic_json(
            &root.path().join("dismissed-mods.json"),
            &json!(["AANobbMI"]),
        )
        .unwrap();
        fs::write(game.join("mods/sodium.jar"), jar(json!({"id":"sodium"}), vec![])).unwrap();
        let addon = jar(json!({"id":"sodium-extra"}), vec![]);
        fs::write(game.join("mods/sodium-extra.jar"), &addon).unwrap();
        assert_eq!(apply(root.path()).unwrap(), 0);
        assert!(game.join("mods/sodium-extra.jar").is_file());
        assert!(!game.join("mods/sodium-extra.jar.disabled").exists());
    }
    #[test]
    fn signed_or_hard_dependency_archives_are_unchanged() {
        let nested = jar(json!({"id":"sodium"}), vec![]);
        for signed in [false, true] {
            let mut meta = json!({"id":"batclient","jars":[{"file":"META-INF/jars/r.jar"}]});
            let mut entries = vec![("META-INF/jars/r.jar", nested.clone())];
            if signed {
                entries.push(("META-INF/BAT.SF", b"signature".to_vec()));
            } else {
                meta["depends"] = json!({"sodium":"*"});
            }
            let original = jar(meta, entries);
            assert!(transformed(&original).is_err());
        }
    }
}
