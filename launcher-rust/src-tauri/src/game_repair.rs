//! Verified base-client repair; never changes mods, options or worlds.
#![allow(dead_code)]
use crate::library as lib;
use serde_json::Value;
use sha1::{Digest, Sha1};
use std::{
    fs,
    io::{Read, Write},
    path::Path,
    time::Duration,
};

fn client() -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(180))
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .map_err(|_| "Cannot initialize game repair".to_owned())
}
fn trusted(url: &str) -> Result<reqwest::Url, String> {
    let u = reqwest::Url::parse(url).map_err(|_| "Invalid official download URL")?;
    if u.scheme() != "https"
        || u.port_or_known_default() != Some(443)
        || !u.username().is_empty()
        || u.password().is_some()
        || !matches!(
            u.host_str(),
            Some(
                "piston-meta.mojang.com"
                    | "piston-data.mojang.com"
                    | "launchermeta.mojang.com"
                    | "launcher.mojang.com"
            )
        )
    {
        return Err("Untrusted Minecraft repair URL".into());
    }
    Ok(u)
}
fn json_get(
    client: &reqwest::blocking::Client,
    url: &str,
    hash: Option<&str>,
) -> Result<Value, String> {
    let mut r = client
        .get(trusted(url)?)
        .send()
        .map_err(|_| "Cannot reach official Minecraft metadata")?
        .error_for_status()
        .map_err(|_| "Official Minecraft metadata request failed")?;
    let mut b = vec![];
    r.by_ref()
        .take(8 * 1024 * 1024 + 1)
        .read_to_end(&mut b)
        .map_err(|_| "Minecraft metadata interrupted")?;
    if b.len() > 8 * 1024 * 1024 {
        return Err("Minecraft metadata exceeds limit".into());
    }
    if hash.is_some_and(|h| format!("{:x}", Sha1::digest(&b)) != h.to_lowercase()) {
        return Err("Official metadata checksum mismatch".into());
    }
    serde_json::from_slice(&b).map_err(|_| "Invalid official Minecraft metadata".into())
}
fn hash(path: &Path) -> Result<String, String> {
    let meta = fs::symlink_metadata(path).map_err(|_| "Cannot inspect cached client")?;
    if !meta.is_file() || meta.file_type().is_symlink() || meta.len() > 512 * 1024 * 1024 {
        return Err("Unsupported cached client file".into());
    }
    let mut f = fs::File::open(path).map_err(|_| "Cannot read cached client")?;
    let mut s = Sha1::new();
    let mut buf = [0; 65536];
    loop {
        let n = f
            .read(&mut buf)
            .map_err(|_| "Cannot verify cached client")?;
        if n == 0 {
            break;
        }
        s.update(&buf[..n]);
    }
    Ok(format!("{:x}", s.finalize()))
}
fn commit(
    path: &Path,
    stage: tempfile::NamedTempFile,
    backups: &Path,
    original: Option<&str>,
) -> Result<(), String> {
    if let Some(old) = original {
        if hash(path)? != old {
            return Err("The cached client changed during repair; no replacement made".into());
        }
        fs::create_dir_all(backups).map_err(|_| "Cannot create repair backup")?;
        let backup = backups.join(format!("client-{old}.jar"));
        if backup.exists() {
            if hash(&backup)? != old {
                return Err("Existing client backup failed verification".into());
            }
        } else {
            let mut temp = tempfile::NamedTempFile::new_in(backups)
                .map_err(|_| "Cannot stage client backup")?;
            let mut source = fs::File::open(path).map_err(|_| "Cannot back up original client")?;
            std::io::copy(&mut source, temp.as_file_mut()).map_err(|_| "Client backup failed")?;
            temp.as_file()
                .sync_all()
                .map_err(|_| "Cannot flush client backup")?;
            if hash(temp.path())? != old {
                return Err("Original client changed while backing up".into());
            }
            temp.persist_noclobber(&backup)
                .map_err(|_| "Cannot commit client backup")?;
        }
    }
    stage
        .persist(path)
        .map_err(|_| "Cannot replace client JAR; close any game using it")?;
    Ok(())
}
pub fn repair(
    root: &Path,
    version: &str,
    mut progress: impl FnMut(u64, u64),
) -> Result<bool, String> {
    if !lib::safe_filename(version) || version.len() > 64 {
        return Err("Invalid Minecraft version".into());
    }
    let game = lib::game_dir(root)?;
    let dir = game.join("versions").join(version);
    let metadata_path = dir.join(format!("{version}.json"));
    let mut cached = lib::read_json(&metadata_path, Value::Null)?;
    if !cached.is_object() {
        return Ok(false);
    } // Full installation is a separate operation.
    let path = dir.join(format!("{version}.jar"));
    let original = if path.exists() {
        Some(hash(&path)?)
    } else {
        None
    };
    if original.as_deref() == cached["downloads"]["client"]["sha1"].as_str() {
        return Ok(false);
    }
    let http = client()?;
    let catalog = json_get(
        &http,
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
        None,
    )?;
    let entry = catalog["versions"]
        .as_array()
        .and_then(|a| a.iter().find(|v| v["id"].as_str() == Some(version)))
        .ok_or("Minecraft version not found in official catalog")?;
    let official = json_get(
        &http,
        entry["url"]
            .as_str()
            .ok_or("Official manifest URL missing")?,
        entry["sha1"].as_str(),
    )?;
    if official["id"].as_str() != Some(version) {
        return Err("Official version identity mismatch".into());
    }
    let download = &official["downloads"]["client"];
    let expected = download["sha1"]
        .as_str()
        .filter(|s| s.len() == 40 && s.chars().all(|c| c.is_ascii_hexdigit()))
        .ok_or("Client checksum missing")?;
    let size = download["size"]
        .as_u64()
        .filter(|s| *s > 0 && *s <= 512 * 1024 * 1024)
        .ok_or("Client size is invalid")?;
    let mut response = http
        .get(trusted(
            download["url"].as_str().ok_or("Client URL missing")?,
        )?)
        .send()
        .map_err(|_| "Minecraft client download failed")?
        .error_for_status()
        .map_err(|_| "Official client download failed")?;
    let mut temp =
        tempfile::NamedTempFile::new_in(&dir).map_err(|_| "Cannot stage client repair")?;
    let mut state = Sha1::new();
    let mut bytes = [0u8; 65536];
    let mut done = 0;
    progress(0, size);
    loop {
        let n = response
            .read(&mut bytes)
            .map_err(|_| "Client download interrupted; original file retained")?;
        if n == 0 {
            break;
        }
        done += n as u64;
        if done > size {
            return Err("Client exceeded its expected size".into());
        }
        state.update(&bytes[..n]);
        temp.write_all(&bytes[..n])
            .map_err(|_| "Cannot write staged client")?;
        progress(done, size);
    }
    if done != size || format!("{:x}", state.finalize()) != expected.to_lowercase() {
        return Err("Downloaded client checksum mismatch; original file retained".into());
    }
    temp.as_file()
        .sync_all()
        .map_err(|_| "Cannot flush verified client")?;
    zip::ZipArchive::new(fs::File::open(temp.path()).map_err(|_| "Cannot verify client archive")?)
        .map_err(|_| "Downloaded client is not a valid JAR")?;
    commit(
        &path,
        temp,
        &root.join("rust-migration-backups/game-files"),
        original.as_deref(),
    )?;
    if cached["downloads"]["client"] != *download {
        cached["downloads"]["client"] = download.clone();
        lib::atomic_json(&metadata_path, &cached)?;
    }
    Ok(true)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    #[ignore = "Explicitly repairs the authorized installed Minecraft 1.21 client cache"]
    fn repair_authorized_installed_121() {
        let root = lib::root().unwrap();
        let _guard = lib::WRITE_LOCK.lock().unwrap();
        lib::ensure_legacy_launcher_closed().unwrap();
        lib::ensure_game_closed().unwrap();
        let repaired = repair(&root, "1.21", |_, _| {}).unwrap();
        let path = lib::game_dir(&root).unwrap().join("versions/1.21/1.21.jar");
        assert_eq!(
            hash(&path).unwrap(),
            "0e9a07b9bb3390602f977073aa12884a4ce12431"
        );
        println!("INSTALLED_121_CLIENT_VERIFIED repaired={repaired}");
    }
    #[test]
    fn rejects_untrusted_urls() {
        for u in [
            "http://piston-data.mojang.com/a",
            "https://evil.test/a",
            "https://piston-data.mojang.com:8443/a",
        ] {
            assert!(trusted(u).is_err());
        }
    }
    #[test]
    fn repairs_atomically_and_preserves_original() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("client.jar");
        fs::write(&path, b"old incomplete").unwrap();
        let old = hash(&path).unwrap();
        let mut stage = tempfile::NamedTempFile::new_in(root.path()).unwrap();
        stage.write_all(b"verified new").unwrap();
        commit(&path, stage, &root.path().join("backup"), Some(&old)).unwrap();
        assert_eq!(fs::read(&path).unwrap(), b"verified new");
        assert_eq!(
            fs::read(root.path().join("backup").join(format!("client-{old}.jar"))).unwrap(),
            b"old incomplete"
        );
    }
}
