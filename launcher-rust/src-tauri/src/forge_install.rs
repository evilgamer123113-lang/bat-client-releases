//! Official Forge-family installer, isolated from player profiles and mods.
use crate::{downloads as net, game_files, game_install, library as lib};
use serde_json::{json, Value};
use sha1::{Digest, Sha1};
use std::{
    fs,
    io::Read,
    path::{Path, PathBuf},
    process::{Command, Stdio},
    thread,
    time::{Duration, Instant},
};
fn identity(s: &str) -> Result<(), String> {
    if s.is_empty()
        || s.len() > 120
        || !s
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"._-+".contains(&b))
    {
        return Err("Invalid Forge installation identity".into());
    }
    Ok(())
}
fn release(xml: &str, version: &str, engine: &str) -> Result<String, String> {
    let prefix = if engine == "forge" || (engine == "neoforge" && version == "1.20.1") {
        format!("{version}-")
    } else if engine == "neoforge" {
        let parts = version.split('.').collect::<Vec<_>>();
        if parts.first() != Some(&"1") || parts.len() < 2 {
            return Err("No NeoForge release for this Minecraft version".into());
        }
        let minor = parts[1]
            .parse::<u32>()
            .map_err(|_| "Invalid NeoForge Minecraft version")?;
        if minor < 20 {
            return Err("NeoForge has no release for this Minecraft version".into());
        }
        format!("{}.{}.", minor, parts.get(2).unwrap_or(&"0"))
    } else {
        return Err("Unsupported Forge-family loader".into());
    };
    let regex = regex::Regex::new(r"<version>([^<]+)</version>").unwrap();
    let mut versions = regex
        .captures_iter(xml)
        .map(|c| c[1].to_owned())
        .filter(|v| v.starts_with(&prefix) && identity(v).is_ok())
        .collect::<Vec<_>>();
    // Stable first, then numeric version order; metadata is not assumed sorted.
    versions.sort_by_key(|v| {
        (
            !v.contains("beta") && !v.contains("alpha"),
            v.split(|c: char| !c.is_ascii_digit())
                .filter_map(|v| v.parse::<u32>().ok())
                .collect::<Vec<_>>(),
        )
    });
    versions
        .pop()
        .ok_or_else(|| format!("No official {engine} release is available for Minecraft {version}"))
}
fn digest(path: &Path) -> Result<String, String> {
    let mut f = fs::File::open(path).map_err(|_| "Cannot read installed artifact")?;
    let mut hash = Sha1::new();
    let mut buffer = [0u8; 65536];
    loop {
        let n = f.read(&mut buffer).map_err(|_| "Cannot verify artifact")?;
        if n == 0 {
            break;
        }
        hash.update(&buffer[..n]);
    }
    Ok(format!("{:x}", hash.finalize()))
}
fn zip_json(zip: &mut zip::ZipArchive<fs::File>, name: &str) -> Result<Value, String> {
    let file = zip
        .by_name(name)
        .map_err(|_| "Official installer metadata missing")?;
    if file.size() > 16 * 1024 * 1024 {
        return Err("Installer metadata too large".into());
    }
    serde_json::from_reader(file.take(16 * 1024 * 1024 + 1))
        .map_err(|_| "Invalid installer metadata".into())
}
fn inspect(installer: &Path, version: &str) -> Result<(Value, Option<Value>), String> {
    let mut zip =
        zip::ZipArchive::new(fs::File::open(installer).map_err(|_| "Cannot read installer")?)
            .map_err(|_| "Invalid installer JAR")?;
    let profile = zip_json(&mut zip, "install_profile.json")?;
    let (manifest, legacy) = if let Some(name) = profile["json"].as_str() {
        if profile["minecraft"] != version {
            return Err("Forge installer targets a different Minecraft version".into());
        }
        let name = name.trim_start_matches('/');
        game_files::relative(Path::new("metadata"), name)?;
        (zip_json(&mut zip, name)?, None)
    } else {
        let manifest = profile["versionInfo"].clone();
        let install = &profile["install"];
        if install["minecraft"] != version
            || install["target"] != manifest["id"]
            || manifest["mainClass"] != "net.minecraft.launchwrapper.Launch"
        {
            return Err("Unrecognized legacy Forge client installer metadata".into());
        }
        (manifest, Some(install.clone()))
    };
    identity(
        manifest["id"]
            .as_str()
            .ok_or("Installer version identity missing")?,
    )?;
    if manifest["inheritsFrom"] != version {
        return Err("Installed loader inherits a different Minecraft version".into());
    }
    Ok((manifest, legacy))
}

fn install_legacy(
    http: &reqwest::blocking::Client,
    installer: &Path,
    stage: &Path,
    release: &str,
    mut manifest: Value,
    install: &Value,
) -> Result<(), String> {
    let coordinate = format!("net.minecraftforge:forge:{release}");
    if install["path"] != coordinate {
        return Err("Legacy Forge library identity does not match its installer".into());
    }
    let mut archive =
        zip::ZipArchive::new(fs::File::open(installer).map_err(|_| "Cannot read installer")?)
            .map_err(|_| "Invalid installer JAR")?;
    let embedded_name = install["filePath"]
        .as_str()
        .filter(|name| lib::safe_filename(name))
        .ok_or("Invalid embedded Forge JAR name")?;
    let mut embedded = archive
        .by_name(embedded_name)
        .map_err(|_| "Embedded Forge JAR missing")?;
    if embedded.size() == 0 || embedded.size() > 64 * 1024 * 1024 {
        return Err("Invalid embedded Forge JAR size".into());
    }
    let libraries = manifest["libraries"]
        .as_array_mut()
        .ok_or("Legacy Forge libraries missing")?;
    libraries.retain(|library| library["clientreq"] != false);
    if !libraries
        .iter()
        .any(|library| library["name"] == coordinate)
    {
        return Err("Legacy Forge client library is missing from the launch metadata".into());
    }
    for library in libraries {
        let name = library["name"]
            .as_str()
            .ok_or("Invalid legacy Forge library name")?;
        let relative = game_files::maven(name)?;
        let path = game_files::relative(&stage.join("libraries"), &relative)?;
        let sha1 = if name == coordinate {
            net::parent(&path)?;
            let mut file =
                fs::File::create(&path).map_err(|_| "Cannot stage embedded Forge JAR")?;
            std::io::copy(&mut embedded, &mut file)
                .map_err(|_| "Cannot extract embedded Forge JAR")?;
            digest(&path)?
        } else {
            let repository = library["url"]
                .as_str()
                .unwrap_or("https://libraries.minecraft.net");
            let repository = repository
                .trim_end_matches('/')
                .replace("http://", "https://");
            let mut candidates = vec![repository];
            for fallback in [
                "https://maven.minecraftforge.net",
                "https://repo.maven.apache.org/maven2",
            ] {
                if !candidates.iter().any(|candidate| candidate == fallback) {
                    candidates.push(fallback.into());
                }
            }
            let embedded_hashes: Vec<String> = library["checksums"]
                .as_array()
                .into_iter()
                .flatten()
                .filter_map(Value::as_str)
                .filter(|hash| hash.len() == 40 && hash.bytes().all(|c| c.is_ascii_hexdigit()))
                .map(str::to_owned)
                .collect();
            let mut verified = None;
            'repositories: for repository in candidates {
                let url = format!("{repository}/{relative}");
                let mut hashes = embedded_hashes.clone();
                if hashes.is_empty() {
                    let Ok(checksum) = net::bytes(http, &format!("{url}.sha1"), 1024) else {
                        continue;
                    };
                    if let Some(hash) = String::from_utf8_lossy(&checksum).split_whitespace().next()
                    {
                        hashes.push(hash.to_owned());
                    }
                }
                for checksum in hashes {
                    if net::file(
                        http,
                        &url,
                        &path,
                        &checksum,
                        None,
                        64 * 1024 * 1024,
                        |_, _| {},
                    )
                    .is_ok()
                    {
                        verified = Some(checksum);
                        break 'repositories;
                    }
                }
            }
            verified.ok_or_else(|| format!("Cannot verify legacy Forge library {name}"))?
        };
        library["downloads"]["artifact"] = json!({"path":relative,"sha1":sha1});
    }
    let id = manifest["id"]
        .as_str()
        .ok_or("Legacy Forge version identity missing")?;
    lib::atomic_json(
        &stage.join("versions").join(id).join(format!("{id}.json")),
        &manifest,
    )
}

fn safe_parent(base: &Path, target: &Path) -> Result<(), String> {
    let relative = target
        .strip_prefix(base)
        .map_err(|_| "Installation path escapes game directory")?;
    let mut current = base.to_path_buf();
    for part in relative.components() {
        current.push(part);
        if let Ok(meta) = fs::symlink_metadata(&current) {
            #[cfg(windows)]
            {
                use std::os::windows::fs::MetadataExt;
                if meta.file_attributes() & 0x400 != 0 {
                    return Err("Reparse paths are not allowed for installer commits".into());
                }
            }
            if meta.file_type().is_symlink() {
                return Err("Symlink paths are not allowed for installer commits".into());
            }
        }
    }
    Ok(())
}
fn commit_tree(stage: &Path, game: &Path) -> Result<(), String> {
    let mut stack = vec![stage.join("libraries"), stage.join("versions")];
    let mut jobs = vec![];
    let mut bytes = 0u64;
    while let Some(dir) = stack.pop() {
        for item in fs::read_dir(&dir).map_err(|_| "Installer output is incomplete")? {
            let item = item.map_err(|_| "Cannot inspect installer output")?;
            let ty = item
                .file_type()
                .map_err(|_| "Cannot inspect installer file")?;
            if ty.is_symlink() {
                return Err("Installer output contains a symlink".into());
            }
            if ty.is_dir() {
                stack.push(item.path());
                continue;
            }
            if !ty.is_file() {
                return Err("Unexpected installer output".into());
            }
            let source = item.path();
            let meta = item
                .metadata()
                .map_err(|_| "Cannot inspect installer size")?;
            bytes = bytes.saturating_add(meta.len());
            if jobs.len() >= 20000 || bytes > 2 * 1024 * 1024 * 1024 {
                return Err("Installer output exceeds limits".into());
            }
            let target = game.join(
                source
                    .strip_prefix(stage)
                    .map_err(|_| "Invalid staged artifact")?,
            );
            safe_parent(game, &target)?;
            if target.exists() {
                if !target.is_file() || digest(&source)? != digest(&target)? {
                    return Err(format!(
                        "An existing game artifact differs: {}. It was preserved.",
                        target.file_name().unwrap().to_string_lossy()
                    ));
                }
                continue;
            }
            jobs.push((source, target));
        }
    }
    // Validate the complete plan before committing; publish the loader map only after resolution.
    for (source, target) in jobs {
        safe_parent(game, &target)?;
        fs::create_dir_all(target.parent().ok_or("Invalid destination")?)
            .map_err(|_| "Cannot create loader library directory")?;
        let mut output = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&target)
            .map_err(|_| "Cannot commit loader library; existing file preserved")?;
        let mut input = fs::File::open(source).map_err(|_| "Cannot read staged loader artifact")?;
        if std::io::copy(&mut input, &mut output).is_err() {
            drop(output);
            let _ = fs::remove_file(&target);
            return Err("Loader artifact copy failed".into());
        }
        output
            .sync_all()
            .map_err(|_| "Cannot save loader artifact")?;
    }
    Ok(())
}
pub fn ensure(
    root: &Path,
    version: &str,
    engine: &str,
    progress: game_install::Progress,
) -> Result<(), String> {
    identity(version)?;
    let game = lib::game_dir(root)?;
    if game_install::selected(&game, version, engine).is_ok()
        && game_files::resolve(&game, version, engine).is_ok()
    {
        return Ok(());
    }
    game_install::ensure(root, version, "vanilla", progress)?;
    let http = net::client()?;
    let (repository, coordinate, artifact) = if engine == "forge" {
        (
            "https://maven.minecraftforge.net",
            "net/minecraftforge/forge",
            "forge",
        )
    } else if version == "1.20.1" {
        (
            "https://maven.neoforged.net/releases",
            "net/neoforged/forge",
            "forge",
        )
    } else {
        (
            "https://maven.neoforged.net/releases",
            "net/neoforged/neoforge",
            "neoforge",
        )
    };
    progress("Looking up the official loader installer...", None);
    let xml = net::bytes(
        &http,
        &format!("{repository}/{coordinate}/maven-metadata.xml"),
        4 * 1024 * 1024,
    )?;
    let selected = release(&String::from_utf8_lossy(&xml), version, engine)?;
    let url = format!("{repository}/{coordinate}/{selected}/{artifact}-{selected}-installer.jar");
    let checksum = String::from_utf8_lossy(&net::bytes(&http, &format!("{url}.sha1"), 1024)?)
        .trim()
        .split_whitespace()
        .next()
        .unwrap_or("")
        .to_owned();
    let staging = tempfile::Builder::new()
        .prefix(".bat-forge-install-")
        .tempdir_in(&game)
        .map_err(|_| "Cannot create private installer staging directory")?;
    let installer = staging.path().join("loader-installer.jar");
    net::file(
        &http,
        &url,
        &installer,
        &checksum,
        None,
        64 * 1024 * 1024,
        |_, _| {},
    )?;
    let (expected, legacy) = inspect(&installer, version)?;
    let id = expected["id"].as_str().unwrap();
    let staged_game = staging.path().join("client");
    let vanilla = staged_game.join("versions").join(version);
    fs::create_dir_all(&vanilla).map_err(|_| "Cannot prepare installer staging")?;
    for extension in ["json", "jar"] {
        let name = format!("{version}.{extension}");
        fs::copy(
            game.join("versions").join(version).join(&name),
            vanilla.join(name),
        )
        .map_err(|_| "Cannot stage verified vanilla files")?;
    }
    let vanilla_files = game_files::resolve(&game, version, "vanilla")?;
    fs::create_dir_all(staged_game.join("libraries"))
        .map_err(|_| "Cannot stage vanilla libraries")?;
    for source in vanilla_files
        .libraries
        .iter()
        .chain(vanilla_files.native_jars.iter())
    {
        let relative = source
            .strip_prefix(&game)
            .map_err(|_| "Invalid cached vanilla library")?;
        let target = staged_game.join(relative);
        fs::create_dir_all(target.parent().ok_or("Invalid stage path")?)
            .map_err(|_| "Cannot create staged library directory")?;
        fs::copy(source, target).map_err(|_| "Cannot stage verified vanilla library")?;
    }
    let index_name = format!("{}.json", vanilla_files.asset_index);
    fs::create_dir_all(staged_game.join("assets/indexes"))
        .map_err(|_| "Cannot stage assets index")?;
    fs::copy(
        game.join("assets/indexes").join(&index_name),
        staged_game.join("assets/indexes").join(&index_name),
    )
    .map_err(|_| "Cannot stage verified assets index")?;
    if let Some(legacy) = legacy {
        progress("Installing legacy Forge client libraries...", None);
        install_legacy(
            &http,
            &installer,
            &staged_game,
            &selected,
            expected.clone(),
            &legacy,
        )?;
    } else {
        let config = lib::config(root)?;
        let profile_id = config["profiles"]
            .as_array()
            .and_then(|p| {
                p.iter()
                    .find(|p| p["version"] == version && p["engine"] == engine)
            })
            .and_then(|p| p["id"].as_str())
            .ok_or("Select a saved loadout for this loader")?;
        let java = crate::java_runtime::detect_at(root, profile_id)?;
        let executable = match java.executable {
            Some(p) => PathBuf::from(p),
            None => crate::java_install::install(root, java.required_major, progress)?,
        };
        lib::atomic_json(
            &staged_game.join("launcher_profiles.json"),
            &json!({"profiles":{}}),
        )?;
        let log_path = staging.path().join("installer.log");
        let log = fs::File::create(&log_path).map_err(|_| "Cannot create installer log")?;
        let mut command = Command::new(executable);
        command
            .args(["-Djava.awt.headless=true", "-jar"])
            .arg(crate::game_launch::java_path(&installer))
            .arg("--installClient")
            .arg(crate::game_launch::java_path(&staged_game))
            .current_dir(staging.path())
            .stdin(Stdio::null())
            .stdout(log.try_clone().map_err(|_| "Cannot attach installer log")?)
            .stderr(log);
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            command.creation_flags(0x08000000);
        }
        let mut child = command
            .spawn()
            .map_err(|_| "Cannot start official loader installer")?;
        let started = Instant::now();
        progress("Processing the official loader installer...", None);
        let result = loop {
            match child.try_wait() {
                Ok(Some(status)) => break Ok(status),
                Ok(None) if started.elapsed() < Duration::from_secs(600) => {
                    thread::sleep(Duration::from_millis(250))
                }
                _ => {
                    let _ = child.kill();
                    let _ = child.wait();
                    break Err("Official loader installer timed out".to_owned());
                }
            }
        };
        let evidence = game.join("bat-launch-logs");
        fs::create_dir_all(&evidence).map_err(|_| "Cannot preserve installer diagnostics")?;
        let report = evidence.join(format!("{engine}-installer-{}.log", uuid::Uuid::new_v4()));
        let text = crate::launch_errors::tail(&log_path).unwrap_or_default();
        fs::write(&report, &text).map_err(|_| "Cannot preserve installer log")?;
        if !result?.success() {
            return Err(format!(
                "Official {engine} installer failed. {}\n{text}",
                report.display()
            ));
        }
    }
    let actual = lib::read_json(
        &staged_game
            .join("versions")
            .join(id)
            .join(format!("{id}.json")),
        Value::Null,
    )?;
    if actual["id"] != expected["id"]
        || actual["inheritsFrom"] != version
        || actual["mainClass"] != expected["mainClass"]
    {
        return Err("Official installer output does not match verified metadata".into());
    }
    progress("Verifying and committing loader files...", None);
    game_files::resolve_selected(&staged_game, id, engine)?;
    commit_tree(&staged_game, &game)?;
    let mut map = lib::read_json(&game.join("bat-loader-installations.json"), json!({}))?;
    map[format!("{engine}:{version}")] = json!(id);
    lib::atomic_json(&game.join("bat-loader-installations.json"), &map)?;
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn catalog_is_exact_stable_and_numerically_sorted() {
        let xml="<version>1.21.1-52.1.9</version><version>1.21.11-61.0.0</version><version>1.21.1-52.1.21</version>";
        assert_eq!(release(xml, "1.21.1", "forge").unwrap(), "1.21.1-52.1.21");
        assert!(release(xml, "1.20.1", "forge").is_err());
        assert_eq!(
            release(
                "<version>21.1.5-beta</version><version>21.1.4</version>",
                "1.21.1",
                "neoforge"
            )
            .unwrap(),
            "21.1.4"
        );
        assert_eq!(
            release(
                "<version>1.20.1-47.1.106</version><version>20.2.99</version>",
                "1.20.1",
                "neoforge"
            )
            .unwrap(),
            "1.20.1-47.1.106"
        );
    }
    #[test]
    fn commit_preserves_conflicts_and_player_profiles() {
        let base = tempfile::tempdir().unwrap();
        let stage = base.path().join("stage");
        let game = base.path().join("game");
        fs::create_dir_all(stage.join("libraries")).unwrap();
        fs::create_dir_all(stage.join("versions")).unwrap();
        fs::create_dir_all(game.join("libraries")).unwrap();
        fs::write(stage.join("libraries/a.jar"), b"new").unwrap();
        fs::write(game.join("libraries/a.jar"), b"original").unwrap();
        fs::write(game.join("launcher_profiles.json"), b"player").unwrap();
        assert!(commit_tree(&stage, &game).is_err());
        assert_eq!(fs::read(game.join("libraries/a.jar")).unwrap(), b"original");
        assert_eq!(
            fs::read(game.join("launcher_profiles.json")).unwrap(),
            b"player"
        );
    }
    #[test]
    #[ignore = "Downloads and runs official loader installer in a disposable D: fixture"]
    fn native_loader_install() {
        let engine = std::env::var("BAT_LOADER_TEST_ENGINE").unwrap_or("forge".into());
        assert!(["forge", "neoforge", "fabric", "quilt"].contains(&engine.as_str()));
        let dir = if let Ok(value) = std::env::var("BAT_LOADER_TEST_ROOT") {
            let dir = fs::canonicalize(value).unwrap();
            let base = fs::canonicalize("D:/BatClientBuildCache").unwrap();
            assert_eq!(dir.parent(), Some(base.as_path()));
            assert!(dir
                .file_name()
                .unwrap()
                .to_string_lossy()
                .starts_with("rust-forge-install-"));
            dir
        } else {
            tempfile::Builder::new()
                .prefix("rust-forge-install-")
                .tempdir_in("D:/BatClientBuildCache")
                .unwrap()
                .keep()
        };
        let game = dir.join("game");
        let version = std::env::var("BAT_LOADER_TEST_VERSION").unwrap_or("1.21.1".into());
        lib::atomic_json(&dir.join("profiles.config.json"),&json!({"profiles":[{"id":"test","version":version,"engine":engine}],"appSettings":{"gameDir":game,"javaPath":"C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot/bin/java.exe","downloadThreads":8}})).unwrap();
        println!("LOADER_FIXTURE={}", dir.display());
        game_install::ensure(&dir, &version, &engine, &|message, progress| {
            if progress.is_none() {
                println!("{message}")
            }
        })
        .unwrap();
        game_files::resolve(&game, &version, &engine).unwrap();
        println!("OFFICIAL_LOADER_INSTALL_AND_RESOLVE_PASS {engine} {version}");
    }
}
