//! Bounded verified downloads shared by game, loader and runtime installation.
use sha1::{Digest, Sha1};
use sha2::Sha256;
use std::{
    fs,
    io::{Read, Write},
    path::Path,
    time::Duration,
};
pub fn trusted(value: &str) -> Result<reqwest::Url, String> {
    let url = reqwest::Url::parse(value).map_err(|_| "Invalid installation URL")?;
    if url.scheme() != "https"
        || url.port_or_known_default() != Some(443)
        || !url.username().is_empty()
        || url.password().is_some()
        || !matches!(
            url.host_str(),
            Some(
                "piston-meta.mojang.com"
                    | "piston-data.mojang.com"
                    | "launchermeta.mojang.com"
                    | "launcher.mojang.com"
                    | "libraries.minecraft.net"
                    | "resources.download.minecraft.net"
                    | "meta.fabricmc.net"
                    | "meta.legacyfabric.net"
                    | "maven.fabricmc.net"
                    | "maven.legacyfabric.net"
                    | "repo.legacyfabric.net"
                    | "meta.quiltmc.org"
                    | "maven.quiltmc.org"
                    | "maven.minecraftforge.net"
                    | "files.minecraftforge.net"
                    | "maven.neoforged.net"
                    | "api.adoptium.net"
                    | "github.com"
                    | "release-assets.githubusercontent.com"
                    | "objects.githubusercontent.com"
                    | "authlib-injector.yushi.moe"
                    | "repo1.maven.org"
                    | "repo.maven.apache.org"
            )
        )
    {
        return Err("Untrusted installation host".into());
    }
    Ok(url)
}
pub fn client() -> Result<reqwest::blocking::Client, String> {
    client_with_timeout(Duration::from_secs(240))
}
pub(crate) fn client_with_timeout(timeout: Duration) -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .connect_timeout(Duration::from_secs(12))
        .timeout(timeout)
        .user_agent("BatClientRust/0.1 (https://batclient.xyz/contact.html)")
        .redirect(reqwest::redirect::Policy::custom(|attempt| {
            if attempt.previous().len() > 6 || trusted(attempt.url().as_str()).is_err() {
                attempt.stop()
            } else {
                attempt.follow()
            }
        }))
        .build()
        .map_err(|_| "Cannot initialize installation download".into())
}
pub fn bytes(http: &reqwest::blocking::Client, url: &str, max: u64) -> Result<Vec<u8>, String> {
    let response = http
        .get(trusted(url)?)
        .send()
        .map_err(|_| "Installation metadata request failed")?
        .error_for_status()
        .map_err(|_| "Official installation metadata is unavailable")?;
    if response.content_length().is_some_and(|n| n > max) {
        return Err("Installation metadata exceeds limit".into());
    }
    let mut data = vec![];
    response
        .take(max + 1)
        .read_to_end(&mut data)
        .map_err(|_| "Installation metadata interrupted")?;
    if data.len() as u64 > max {
        return Err("Installation metadata exceeds limit".into());
    }
    Ok(data)
}
pub fn json(http: &reqwest::blocking::Client, url: &str) -> Result<serde_json::Value, String> {
    serde_json::from_slice(&bytes(http, url, 16 * 1024 * 1024)?)
        .map_err(|_| "Invalid installation metadata".into())
}
pub fn hash(path: &Path, expected: &str) -> Result<bool, String> {
    if !matches!(expected.len(), 40 | 64) || !expected.chars().all(|c| c.is_ascii_hexdigit()) {
        return Err("Installation checksum is missing or invalid".into());
    }
    let meta = match fs::symlink_metadata(path) {
        Ok(m) => m,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(false),
        Err(_) => return Err("Cannot inspect cached installation file".into()),
    };
    if !meta.is_file() || meta.file_type().is_symlink() {
        return Err("Installation file is not a regular file".into());
    }
    let mut file = fs::File::open(path).map_err(|_| "Cannot read cached installation file")?;
    let mut one = Sha1::new();
    let mut two = Sha256::new();
    let mut buffer = [0; 65536];
    loop {
        let n = file
            .read(&mut buffer)
            .map_err(|_| "Cannot verify cached installation file")?;
        if n == 0 {
            break;
        }
        if expected.len() == 40 {
            one.update(&buffer[..n]);
        } else {
            two.update(&buffer[..n]);
        }
    }
    Ok(if expected.len() == 40 {
        format!("{:x}", one.finalize())
    } else {
        format!("{:x}", two.finalize())
    } == expected.to_lowercase())
}
pub fn parent(path: &Path) -> Result<(), String> {
    let parent = path.parent().ok_or("Invalid installation path")?;
    for component in parent.ancestors() {
        if fs::symlink_metadata(component).is_ok_and(|m| m.file_type().is_symlink()) {
            return Err("Linked installation directories are not supported".into());
        }
    }
    fs::create_dir_all(parent).map_err(|_| "Cannot create installation directory")?;
    Ok(())
}
pub fn file(
    http: &reqwest::blocking::Client,
    url: &str,
    path: &Path,
    expected: &str,
    size: Option<u64>,
    max: u64,
    progress: impl Fn(u64, u64),
) -> Result<(), String> {
    file_cancellable(http, url, path, expected, size, max, progress, &|| false)
}
pub(crate) fn file_cancellable(
    http: &reqwest::blocking::Client,
    url: &str,
    path: &Path,
    expected: &str,
    size: Option<u64>,
    max: u64,
    progress: impl Fn(u64, u64),
    cancelled: &dyn Fn() -> bool,
) -> Result<(), String> {
    check_cancelled(cancelled)?;
    trusted(url)?;
    parent(path)?;
    if fs::metadata(path).is_ok_and(|m| m.len() > max) {
        return Err("Cached artifact exceeds limit; original retained".into());
    }
    if hash(path, expected)? && size.is_none_or(|s| fs::metadata(path).is_ok_and(|m| m.len() == s))
    {
        return Ok(());
    }
    if size.is_some_and(|s| s > max) {
        return Err("Installation artifact exceeds limit".into());
    }
    check_cancelled(cancelled)?;
    let mut response = http
        .get(url)
        .send()
        .map_err(|_| "Installation download failed; retry Launch to resume")?
        .error_for_status()
        .map_err(|_| "Required installation artifact unavailable")?;
    if response
        .content_length()
        .is_some_and(|s| s > max || size.is_some_and(|n| n != s))
    {
        return Err("Installation artifact size mismatch".into());
    }
    let mut stage = tempfile::NamedTempFile::new_in(path.parent().unwrap())
        .map_err(|_| "Cannot stage installation file")?;
    let mut buffer = [0; 65536];
    let mut done = 0;
    loop {
        check_cancelled(cancelled)?;
        let n = response
            .read(&mut buffer)
            .map_err(|_| "Installation download interrupted; original retained")?;
        if n == 0 {
            break;
        }
        done += n as u64;
        if done > max || size.is_some_and(|s| done > s) {
            return Err("Installation download exceeded expected size".into());
        }
        stage
            .write_all(&buffer[..n])
            .map_err(|_| "Cannot write installation file; check free space")?;
        progress(done, size.unwrap_or(max));
    }
    if size.is_some_and(|s| done != s) || !hash(stage.path(), expected)? {
        return Err("Installation checksum mismatch; original retained".into());
    }
    stage
        .as_file()
        .sync_all()
        .map_err(|_| "Cannot flush installation file")?;
    check_cancelled(cancelled)?;
    if path.exists() {
        let old = fs::read(path).map_err(|_| "Cannot preserve damaged installation file")?;
        let backup = path.parent().unwrap().join(".bat-replaced");
        fs::create_dir_all(&backup).map_err(|_| "Cannot create installation backup")?;
        let dest = backup.join(format!(
            "{}-{}",
            format!("{:x}", Sha256::digest(&old)),
            path.file_name().unwrap().to_string_lossy()
        ));
        if !dest.exists() {
            let mut temp = tempfile::NamedTempFile::new_in(&backup)
                .map_err(|_| "Cannot stage installation backup")?;
            temp.write_all(&old)
                .map_err(|_| "Cannot preserve original installation file")?;
            temp.persist_noclobber(dest)
                .map_err(|_| "Cannot commit installation backup")?;
        }
    }
    stage
        .persist(path)
        .map_err(|_| "Cannot replace installation file; close Minecraft")?;
    Ok(())
}
pub(crate) fn check_cancelled(cancelled: &dyn Fn() -> bool) -> Result<(), String> {
    if cancelled() {
        Err("Background preparation cancelled".into())
    } else {
        Ok(())
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_unsafe_sources() {
        for url in [
            "http://libraries.minecraft.net/a.jar",
            "https://evil.example/a",
            "https://user@github.com/x",
            "https://github.com:8443/x",
        ] {
            assert!(trusted(url).is_err());
        }
    }
    #[test]
    fn checks_cached_bytes_without_network() {
        let root = tempfile::tempdir().unwrap();
        let p = root.path().join("file");
        fs::write(&p, b"fixture").unwrap();
        assert!(hash(&p, &format!("{:x}", Sha256::digest(b"fixture"))).unwrap());
        assert!(!hash(&p, &"0".repeat(40)).unwrap());
        assert!(hash(&p, "").is_err());
    }
}
