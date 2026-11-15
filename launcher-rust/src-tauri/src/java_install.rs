use crate::{downloads as net, game_files, java_runtime, library as lib};
use std::{
    fs,
    path::{Path, PathBuf},
};
fn executable(root: &Path) -> PathBuf {
    root.join("bin")
        .join(if cfg!(windows) { "java.exe" } else { "java" })
}
fn find(root: &Path, depth: usize) -> Option<PathBuf> {
    if executable(root).is_file() {
        return Some(root.into());
    }
    if depth == 0 {
        return None;
    }
    for entry in fs::read_dir(root).ok()?.flatten() {
        if entry.file_type().ok()?.is_dir() {
            if let Some(root) = find(&entry.path(), depth - 1) {
                return Some(root);
            }
        }
    }
    None
}
fn extract(path: &Path, directory: &Path, zip: bool) -> Result<(), String> {
    let mut count = 0;
    let mut total = 0u64;
    if zip {
        let mut archive =
            zip::ZipArchive::new(fs::File::open(path).map_err(|_| "Cannot open Java archive")?)
                .map_err(|_| "Invalid Java ZIP")?;
        for i in 0..archive.len() {
            let mut entry = archive.by_index(i).map_err(|_| "Invalid Java ZIP entry")?;
            let name = entry.name().trim_end_matches('/');
            if name.is_empty() {
                continue;
            }
            let dest = game_files::relative(directory, name)?;
            count += 1;
            total += entry.size();
            if count > 100000
                || total > 2 * 1024 * 1024 * 1024
                || entry.unix_mode().is_some_and(|m| m & 0o170000 == 0o120000)
            {
                return Err("Unsafe or excessive Java archive".into());
            }
            if entry.is_dir() {
                fs::create_dir_all(dest).map_err(|_| "Cannot create Java directory")?;
            } else {
                net::parent(&dest)?;
                let mut out = fs::OpenOptions::new()
                    .write(true)
                    .create_new(true)
                    .open(dest)
                    .map_err(|_| "Cannot extract Java file")?;
                std::io::copy(&mut entry, &mut out).map_err(|_| "Java extraction interrupted")?;
            }
        }
    } else {
        let decoder = flate2::read::GzDecoder::new(
            fs::File::open(path).map_err(|_| "Cannot open Java archive")?,
        );
        let mut archive = tar::Archive::new(decoder);
        let mut links = vec![];
        for entry in archive.entries().map_err(|_| "Invalid Java tar archive")? {
            let mut entry = entry.map_err(|_| "Invalid Java tar entry")?;
            let name = entry
                .path()
                .map_err(|_| "Invalid Java archive path")?
                .to_string_lossy()
                .trim_end_matches('/')
                .to_owned();
            let dest = game_files::relative(directory, &name)?;
            let kind = entry.header().entry_type();
            count += 1;
            total += entry.size();
            if count > 100000 || total > 2 * 1024 * 1024 * 1024 {
                return Err("Excessive Java archive".into());
            }
            if kind.is_symlink() || kind.is_hard_link() {
                let target = entry
                    .link_name()
                    .map_err(|_| "Invalid Java link")?
                    .ok_or("Missing Java link target")?
                    .into_owned();
                links.push((dest, target, kind.is_hard_link()));
                continue;
            }
            if kind.is_dir() {
                fs::create_dir_all(&dest).map_err(|_| "Cannot create Java directory")?;
            } else if kind.is_file() {
                net::parent(&dest)?;
                entry
                    .unpack(&dest)
                    .map_err(|_| "Cannot extract Java file")?;
            } else {
                return Err("Unsupported Java archive entry".into());
            }
        }
        // Materialize internal links as independent bytes, never permit extraction through links.
        let base =
            fs::canonicalize(directory).map_err(|_| "Cannot resolve Java staging directory")?;
        for (dest, target, hard) in links {
            let source = if hard {
                directory.join(target)
            } else {
                dest.parent().unwrap().join(target)
            };
            let source = fs::canonicalize(source).map_err(|_| "Broken Java archive link")?;
            if !source.starts_with(&base) || !source.is_file() {
                return Err("Java link escapes extraction directory".into());
            }
            net::parent(&dest)?;
            fs::copy(source, dest).map_err(|_| "Cannot materialize internal Java link")?;
        }
    }
    Ok(())
}
pub fn install(
    root: &Path,
    major: u32,
    progress: crate::game_install::Progress,
) -> Result<PathBuf, String> {
    if !(8..=100).contains(&major) {
        return Err("Unsupported Java major".into());
    }
    let os = if cfg!(windows) {
        "windows"
    } else if cfg!(target_os = "macos") {
        "mac"
    } else {
        "linux"
    };
    let arch = if cfg!(target_arch = "aarch64") {
        "aarch64"
    } else if cfg!(target_arch = "x86_64") {
        "x64"
    } else {
        return Err("Unsupported Java architecture".into());
    };
    let base = root.join("runtimes");
    fs::create_dir_all(&base).map_err(|_| "Cannot create managed Java directory")?;
    let destination = base.join(format!("java-{major}"));
    if java_runtime::probe(&executable(&destination)).is_some_and(|v| v.0 == major) {
        return Ok(executable(&destination));
    }
    let http = net::client()?;
    progress(&format!("Finding verified Java {major}…"), None);
    let mut package = None;
    for kind in ["jre", "jdk"] {
        let url=format!("https://api.adoptium.net/v3/assets/latest/{major}/hotspot?os={os}&architecture={arch}&image_type={kind}");
        if let Ok(data) = net::json(&http, &url) {
            package = data.as_array().and_then(|a| {
                a.iter().find_map(|v| {
                    v["binary"]["package"]
                        .as_object()
                        .map(|p| serde_json::Value::Object(p.clone()))
                })
            });
            if package.is_some() {
                break;
            }
        }
    }
    let package = package.ok_or("Adoptium has no compatible runtime for this OS/Java version")?;
    let url = package["link"]
        .as_str()
        .ok_or("Java download URL missing")?;
    let expected = package["checksum"]
        .as_str()
        .ok_or("Java checksum missing")?;
    let temp = tempfile::Builder::new()
        .prefix("java-install-")
        .tempdir_in(&base)
        .map_err(|_| "Cannot stage Java installation")?;
    let archive = temp.path().join("runtime.archive");
    net::file(
        &http,
        url,
        &archive,
        expected,
        package["size"].as_u64(),
        512 * 1024 * 1024,
        |done, total| {
            progress(
                &format!("Downloading Java {major}…"),
                Some(done as f64 / total as f64),
            )
        },
    )?;
    let unpack = temp.path().join("extracted");
    fs::create_dir(&unpack).map_err(|_| "Cannot stage Java extraction")?;
    progress("Extracting and verifying Java…", None);
    extract(&archive, &unpack, url.ends_with(".zip"))?;
    let home = find(&unpack, 4).ok_or("Downloaded runtime contains no Java executable")?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(executable(&home), fs::Permissions::from_mode(0o755))
            .map_err(|_| "Cannot enable managed Java executable")?;
    }
    if !java_runtime::probe(&executable(&home)).is_some_and(|v| v.0 == major) {
        return Err("Downloaded Java does not report the required major".into());
    }
    let mut previous = None;
    if destination.exists() {
        let backups = base.join("backups");
        fs::create_dir_all(&backups).map_err(|_| "Cannot preserve old Java runtime")?;
        let backup = backups.join(format!("java-{major}-{}", uuid::Uuid::new_v4()));
        fs::rename(&destination, &backup)
            .map_err(|_| "Cannot preserve old Java runtime; close Minecraft")?;
        previous = Some(backup);
    }
    if fs::rename(home, &destination).is_err() {
        if let Some(backup) = previous {
            let _ = fs::rename(backup, &destination);
        }
        return Err("Cannot commit verified Java runtime; original runtime preserved".into());
    }
    Ok(executable(&destination))
}
#[tauri::command]
pub async fn download_java(major: u32) -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library busy")?;
        lib::ensure_game_closed()?;
        let root = lib::root()?;
        let _game = crate::game_launch::lock_game(&root)?;
        install(&root, major, &|_, _| {}).map(|p| p.to_string_lossy().into_owned())
    })
    .await
    .map_err(|_| "Java installation interrupted")?
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn zip_rejects_traversal() {
        use std::io::Write;
        let root = tempfile::tempdir().unwrap();
        let file = root.path().join("bad.zip");
        let mut zip = zip::ZipWriter::new(fs::File::create(&file).unwrap());
        zip.start_file("../escape", zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(b"bad").unwrap();
        zip.finish().unwrap();
        assert!(extract(&file, &root.path().join("out"), true).is_err());
        assert!(!root.path().join("escape").exists());
    }
    #[test]
    #[ignore = "Downloads/probes a real managed Java in disposable D: fixture"]
    fn native_runtime_install() {
        let root = tempfile::Builder::new()
            .prefix("rust-java-install-")
            .tempdir_in("D:/BatClientBuildCache")
            .unwrap()
            .keep();
        let path = install(&root, 21, &|_, _| {}).unwrap();
        assert_eq!(java_runtime::probe(&path).unwrap().0, 21);
        println!("JAVA_INSTALL_VERIFIED {}", path.display());
    }
}
