use crate::{downloads as net, game_files, library as lib};
use serde_json::{json, Value};
use sha1::{Digest, Sha1};
use std::{
    collections::{HashMap, HashSet},
    fs,
    path::{Path, PathBuf},
    sync::{
        atomic::{AtomicUsize, Ordering},
        Mutex,
    },
};
pub type Progress<'a> = &'a (dyn Fn(&str, Option<f64>) + Sync);
struct Job {
    url: String,
    path: PathBuf,
    hash: String,
    size: Option<u64>,
}
fn name(v: &str) -> Result<(), String> {
    if matches!(v, "." | "..")
        || v.is_empty()
        || v.len() > 120
        || !v
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || "._-+".contains(c))
    {
        return Err("Invalid installation identity".into());
    }
    Ok(())
}
fn manifest_path(game: &Path, id: &str) -> Result<PathBuf, String> {
    name(id)?;
    Ok(game.join("versions").join(id).join(format!("{id}.json")))
}
pub fn selected(game: &Path, version: &str, engine: &str) -> Result<String, String> {
    if engine == "vanilla" {
        return Ok(version.into());
    }
    let map = lib::read_json(&game.join("bat-loader-installations.json"), json!({}))?;
    if let Some(id) = map[format!("{engine}:{version}")].as_str() {
        name(id)?;
        return Ok(id.into());
    }
    let legacy = format!("{engine}-loader-{version}");
    if manifest_path(game, &legacy)?.is_file() {
        return Ok(legacy);
    }
    Err(format!("{engine} for Minecraft {version} is not installed"))
}
fn official(http: &reqwest::blocking::Client, game: &Path, version: &str) -> Result<Value, String> {
    let path = manifest_path(game, version)?;
    if path.exists() {
        let value = lib::read_json(&path, Value::Null)?;
        if value["id"] == version && value["downloads"]["client"]["sha1"].is_string() {
            return Ok(value);
        }
    }
    let catalog = net::json(
        http,
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
    )?;
    let entry = catalog["versions"]
        .as_array()
        .and_then(|a| a.iter().find(|v| v["id"] == version))
        .ok_or("Version not present in official Minecraft catalog")?;
    let data = net::bytes(
        http,
        entry["url"]
            .as_str()
            .ok_or("Missing official manifest URL")?,
        16 * 1024 * 1024,
    )?;
    if entry["sha1"].as_str() != Some(format!("{:x}", Sha1::digest(&data)).as_str()) {
        return Err("Official manifest checksum mismatch".into());
    }
    let value: Value = serde_json::from_slice(&data).map_err(|_| "Invalid official manifest")?;
    if value["id"] != version {
        return Err("Official version identity mismatch".into());
    }
    net::parent(&path)?;
    lib::atomic_json(&path, &value)?;
    Ok(value)
}
fn loader(
    http: &reqwest::blocking::Client,
    game: &Path,
    version: &str,
    engine: &str,
) -> Result<Value, String> {
    if let Ok(id) = selected(game, version, engine) {
        let value = lib::read_json(&manifest_path(game, &id)?, Value::Null)?;
        if value.is_object() {
            return Ok(value);
        }
    }
    let bases: Vec<&str> = match engine {
        "fabric" => vec![
            "https://meta.fabricmc.net/v2",
            "https://meta.legacyfabric.net/v2",
        ],
        "quilt" => vec!["https://meta.quiltmc.org/v3"],
        _ => return Err("Forge-family installer processing is still being migrated".into()),
    };
    let mut last = "No compatible loader release is available".to_owned();
    for base in bases {
        let result = (|| {
            let list = net::json(http, &format!("{base}/versions/loader/{version}"))?;
            let items = list.as_array().ok_or("Invalid loader catalog")?;
            let item = items
                .iter()
                .find(|v| v["loader"]["stable"] == true)
                .or_else(|| items.first())
                .ok_or("No loader for selected Minecraft version")?;
            let release = item["loader"]["version"]
                .as_str()
                .ok_or("Loader version missing")?;
            name(release)?;
            let value = net::json(
                http,
                &format!("{base}/versions/loader/{version}/{release}/profile/json"),
            )?;
            if value["inheritsFrom"] != version {
                return Err("Loader profile targets a different Minecraft version".into());
            }
            let id = value["id"].as_str().ok_or("Loader identity missing")?;
            name(id)?;
            lib::atomic_json(&manifest_path(game, id)?, &value)?;
            let mut map = lib::read_json(&game.join("bat-loader-installations.json"), json!({}))?;
            map[format!("{engine}:{version}")] = json!(id);
            lib::atomic_json(&game.join("bat-loader-installations.json"), &map)?;
            Ok(value)
        })();
        match result {
            Ok(v) => return Ok(v),
            Err(error) => last = error,
        }
    }
    Err(last)
}
fn existing_file_sha1(path: &Path) -> Option<String> {
    let meta = fs::symlink_metadata(path).ok()?;
    if !meta.is_file() || meta.file_type().is_symlink() || meta.len() == 0 {
        return None;
    }
    let mut file = fs::File::open(path).ok()?;
    let mut hasher = Sha1::new();
    let mut buf = [0u8; 65536];
    loop {
        let n = std::io::Read::read(&mut file, &mut buf).ok()?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Some(format!("{:x}", hasher.finalize()))
}
fn checksum(http: &reqwest::blocking::Client, url: &str) -> Result<String, String> {
    for attempt in 0..2 {
        if attempt > 0 {
            std::thread::sleep(std::time::Duration::from_millis(350));
        }
        for (suffix, length) in [("sha1", 40), ("sha256", 64)] {
            let Ok(bytes) = net::bytes(http, &format!("{url}.{suffix}"), 1024) else {
                continue;
            };
            let content = String::from_utf8_lossy(&bytes);
            let Some(hash) = content
                .trim_start_matches('\u{feff}')
                .split_whitespace()
                .next()
            else {
                continue;
            };
            if hash.len() == length && hash.bytes().all(|c| c.is_ascii_hexdigit()) {
                return Ok(hash.to_ascii_lowercase());
            }
        }
    }
    Err("Repository artifact checksum unavailable".into())
}
fn artifact(http: &reqwest::blocking::Client, game: &Path, item: &Value) -> Result<Job, String> {
    let path = if let Some(path) = item["path"].as_str() {
        path.to_owned()
    } else {
        return Err("Artifact path missing".into());
    };
    let url = item["url"]
        .as_str()
        .filter(|s| !s.is_empty())
        .map(str::to_owned)
        .unwrap_or_else(|| format!("https://libraries.minecraft.net/{path}"));
    let dest = game_files::relative(&game.join("libraries"), &path)?;
    let hash = match item["sha1"].as_str() {
        Some(h) => h.into(),
        None => match checksum(http, &url) {
            Ok(h) => h,
            Err(e) => match existing_file_sha1(&dest) {
                Some(local_sha1) => local_sha1,
                None => return Err(format!("{e}: {path}")),
            },
        },
    };
    Ok(Job {
        url,
        path: dest,
        hash,
        size: item["size"].as_u64(),
    })
}
pub fn ensure(root: &Path, version: &str, engine: &str, progress: Progress) -> Result<(), String> {
    name(version)?;
    if matches!(engine, "forge" | "neoforge") {
        return crate::forge_install::ensure(root, version, engine, progress);
    }
    ensure_files(root, version, engine, progress, None)
}
pub(crate) fn prefetch(
    root: &Path,
    version: &str,
    cancelled: &(dyn Fn() -> bool + Sync),
) -> Result<(), String> {
    name(version)?;
    ensure_files(root, version, "vanilla", &|_, _| {}, Some(cancelled))
}
fn ensure_files(
    root: &Path,
    version: &str,
    engine: &str,
    progress: Progress,
    background: Option<&(dyn Fn() -> bool + Sync)>,
) -> Result<(), String> {
    let cancelled = background.unwrap_or(&|| false);
    net::check_cancelled(cancelled)?;
    let game = lib::game_dir(root)?;
    fs::create_dir_all(&game).map_err(|_| "Cannot create game directory")?;
    let http = if background.is_some() {
        net::client_with_timeout(std::time::Duration::from_secs(12))?
    } else {
        net::client()?
    };
    progress("Preparing Minecraft installation…", None);
    let base = official(&http, &game, version)?;
    net::check_cancelled(cancelled)?;
    let child = if engine == "vanilla" {
        None
    } else {
        Some(loader(&http, &game, version, engine)?)
    };
    let mut jobs = vec![];
    let client = &base["downloads"]["client"];
    jobs.push(Job {
        url: client["url"].as_str().ok_or("Client URL missing")?.into(),
        path: game
            .join("versions")
            .join(version)
            .join(format!("{version}.jar")),
        hash: client["sha1"]
            .as_str()
            .ok_or("Client checksum missing")?
            .into(),
        size: client["size"].as_u64(),
    });
    let features = HashMap::new();
    for manifest in std::iter::once(&base).chain(child.iter()) {
        for item in manifest["libraries"]
            .as_array()
            .ok_or("Manifest libraries missing")?
        {
            if !game_files::allowed(item, &features) || !game_files::platform_library(item) {
                continue;
            }
            if game_files::has_classpath_artifact(item) {
                let art = if item["downloads"]["artifact"].is_object() {
                    item["downloads"]["artifact"].clone()
                } else {
                    let path =
                        game_files::maven(item["name"].as_str().ok_or("Library name missing")?)?;
                    let repository = item["url"]
                        .as_str()
                        .unwrap_or("https://libraries.minecraft.net/");
                    json!({"path":path,"url":format!("{}/{}",repository.trim_end_matches('/'),path),"sha1":item["sha1"],"size":item["size"]})
                };
                jobs.push(artifact(&http, &game, &art)?);
            }
            let os = if cfg!(windows) {
                "windows"
            } else if cfg!(target_os = "macos") {
                "osx"
            } else {
                "linux"
            };
            if let Some(class) = item["natives"][os].as_str() {
                let key = class.replace(
                    "${arch}",
                    if cfg!(target_pointer_width = "64") {
                        "64"
                    } else {
                        "32"
                    },
                );
                jobs.push(artifact(
                    &http,
                    &game,
                    &game_files::native_artifact(item, &key)?,
                )?);
            }
        }
    }
    let index = &base["assetIndex"];
    let index_id = index["id"]
        .as_str()
        .or(base["assets"].as_str())
        .ok_or("Asset index missing")?;
    name(index_id)?;
    let index_path = game.join("assets/indexes").join(format!("{index_id}.json"));
    net::file_cancellable(
        &http,
        index["url"].as_str().ok_or("Asset index URL missing")?,
        &index_path,
        index["sha1"]
            .as_str()
            .ok_or("Asset index checksum missing")?,
        index["size"].as_u64(),
        32 * 1024 * 1024,
        |_, _| {},
        cancelled,
    )?;
    let objects = lib::read_json(&index_path, Value::Null)?;
    let entries = objects["objects"]
        .as_object()
        .ok_or("Asset object index missing")?;
    if entries.len() > 100000 {
        return Err("Asset index exceeds file limit".into());
    }
    for (name, obj) in entries {
        let hash = obj["hash"]
            .as_str()
            .filter(|h| h.len() == 40 && h.chars().all(|c| c.is_ascii_hexdigit()))
            .ok_or("Invalid asset checksum")?;
        let object = game.join("assets/objects").join(&hash[..2]).join(hash);
        jobs.push(Job {
            url: format!(
                "https://resources.download.minecraft.net/{}/{hash}",
                &hash[..2]
            ),
            path: object.clone(),
            hash: hash.into(),
            size: obj["size"].as_u64(),
        });
        // Legacy virtual indexes need their named resources tree as well as content-addressed objects.
        if objects["virtual"] == true || objects["map_to_resources"] == true {
            let base_dir = if objects["map_to_resources"] == true {
                game.join("resources")
            } else {
                game.join("assets/virtual").join(index_id)
            };
            jobs.push(Job {
                url: format!(
                    "https://resources.download.minecraft.net/{}/{hash}",
                    &hash[..2]
                ),
                path: game_files::relative(&base_dir, name)?,
                hash: hash.into(),
                size: obj["size"].as_u64(),
            });
        }
    }
    if let Some(log) = base["logging"]["client"]["file"].as_object() {
        let id = log["id"].as_str().ok_or("Log config name missing")?;
        jobs.push(Job {
            url: log["url"].as_str().ok_or("Log config URL missing")?.into(),
            path: game_files::relative(&game.join("assets/log_configs"), id)?,
            hash: log["sha1"]
                .as_str()
                .ok_or("Log config checksum missing")?
                .into(),
            size: log["size"].as_u64(),
        });
    }
    let mut paths = HashSet::new();
    jobs.retain(|j| paths.insert(j.path.clone()));
    let config = lib::config(root)?;
    let mut workers = config["appSettings"]["downloadThreads"]
        .as_u64()
        .unwrap_or(8)
        .clamp(1, 32) as usize;
    if background.is_some() {
        workers = workers.min(2);
    }
    let cursor = AtomicUsize::new(0);
    let done = AtomicUsize::new(0);
    let error = Mutex::new(None);
    let total = jobs.len();
    std::thread::scope(|scope| {
        for _ in 0..workers.min(total) {
            let jobs = &jobs;
            let http = &http;
            let cursor = &cursor;
            let done = &done;
            let error = &error;
            scope.spawn(move || loop {
                if cancelled() || error.lock().unwrap().is_some() {
                    break;
                }
                let i = cursor.fetch_add(1, Ordering::SeqCst);
                let Some(job) = jobs.get(i) else {
                    break;
                };
                if let Err(e) = net::file_cancellable(
                    http,
                    &job.url,
                    &job.path,
                    &job.hash,
                    job.size,
                    512 * 1024 * 1024,
                    |_, _| {},
                    cancelled,
                ) {
                    *error.lock().unwrap() = Some(e);
                    break;
                }
                let n = done.fetch_add(1, Ordering::SeqCst) + 1;
                progress(
                    &format!("Minecraft files: {n} / {total}"),
                    Some(n as f64 / total as f64),
                );
            });
        }
    });
    net::check_cancelled(cancelled)?;
    if let Some(error) = error.into_inner().unwrap() {
        return Err(error);
    }
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn loader_selection_never_retags() {
        let root = tempfile::tempdir().unwrap();
        assert!(selected(root.path(), "1.21.1", "forge").is_err());
        lib::atomic_json(
            &root.path().join("bat-loader-installations.json"),
            &json!({"fabric:1.21.1":"fabric-loader-0.16.9-1.21.1"}),
        )
        .unwrap();
        assert_eq!(
            selected(root.path(), "1.21.1", "fabric").unwrap(),
            "fabric-loader-0.16.9-1.21.1"
        );
    }
    #[test]
    fn rejects_path_versions() {
        for s in ["../1.21", "a/b", "", ".", ".."] {
            assert!(name(s).is_err());
        }
    }
    #[test]
    #[ignore = "Installs actual official game/loader files in an isolated D: fixture"]
    fn native_clean_install() {
        let dir = if let Ok(value) = std::env::var("BAT_INSTALL_TEST_ROOT") {
            let path = fs::canonicalize(value).unwrap();
            let base = fs::canonicalize("D:/BatClientBuildCache").unwrap();
            assert_eq!(path.parent(), Some(base.as_path()));
            assert!(path
                .file_name()
                .unwrap()
                .to_string_lossy()
                .starts_with("rust-clean-install-"));
            path
        } else {
            tempfile::Builder::new()
                .prefix("rust-clean-install-")
                .tempdir_in("D:/BatClientBuildCache")
                .unwrap()
                .keep()
        };
        let game = dir.join("game");
        lib::atomic_json(
            &dir.join("profiles.config.json"),
            &json!({"appSettings":{"gameDir":game,"downloadThreads":8}}),
        )
        .unwrap();
        println!("INSTALL_FIXTURE={}", dir.display());
        ensure(&dir, "1.21.1", "fabric", &|_, _| {}).unwrap();
        let files = game_files::resolve(&game, "1.21.1", "fabric").unwrap();
        assert!(files.client.is_file());
        println!("CLEAN_FABRIC_INSTALL_VERIFIED");
    }
}
