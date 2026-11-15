//! Installed-game resolution. Never installs, restores or alters mods/options.
use crate::library as lib;
use serde_json::{json, Value};
use sha1::{Digest, Sha1};
use std::{
    collections::{HashMap, HashSet},
    fs,
    io::{Read, Write},
    path::{Path, PathBuf},
};

pub struct Files {
    pub manifest: Value,
    pub version: String,
    pub libraries: Vec<PathBuf>,
    pub native_jars: Vec<PathBuf>,
    pub client: PathBuf,
    pub asset_index: String,
}
pub fn relative(base: &Path, name: &str) -> Result<PathBuf, String> {
    if name.is_empty()
        || name.contains('\\')
        || name.contains(':')
        || name
            .split('/')
            .any(|p| p.is_empty() || p == "." || p == "..")
    {
        return Err("Invalid game file path".into());
    }
    Ok(base.join(name))
}
fn version_name(v: &str) -> Result<(), String> {
    if v.len() > 100
        || !v
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || "._-".contains(c))
        || v == "."
        || v == ".."
    {
        Err("Invalid installed version name".into())
    } else {
        Ok(())
    }
}
fn library_key(name: &str) -> String {
    let p = name.split(':').collect::<Vec<_>>();
    format!(
        "{}:{}:{}",
        p.first().unwrap_or(&""),
        p.get(1).unwrap_or(&""),
        p.get(3).unwrap_or(&"")
    )
}
fn merge(parent: &mut Value, child: Value) {
    if let Some(libs) = child["libraries"].as_array() {
        let mut all = parent["libraries"].as_array().cloned().unwrap_or_default();
        for item in libs {
            let name = item["name"].as_str().unwrap_or("");
            let key = library_key(name);
            all.retain(|old| library_key(old["name"].as_str().unwrap_or("")) != key);
            all.push(item.clone());
        }
        parent["libraries"] = json!(all);
    }
    if let Some(args) = child["arguments"].as_object() {
        for (kind, value) in args {
            let mut all = parent["arguments"][kind]
                .as_array()
                .cloned()
                .unwrap_or_default();
            if let Some(a) = value.as_array() {
                all.extend(a.clone());
                parent["arguments"][kind] = json!(all);
            }
        }
    }
    if let Some(object) = child.as_object() {
        for (k, v) in object {
            if !["libraries", "arguments", "inheritsFrom"].contains(&k.as_str()) {
                parent[k] = v.clone();
            }
        }
    }
}
fn chain(game: &Path, id: &str, seen: &mut HashSet<String>) -> Result<(Value, String), String> {
    version_name(id)?;
    if seen.len() > 12 || !seen.insert(id.into()) {
        return Err("Minecraft manifest inheritance cycle".into());
    }
    let path = game.join("versions").join(id).join(format!("{id}.json"));
    let child = lib::read_json(&path, Value::Null)?;
    if !child.is_object() {
        return Err(format!(
            "Minecraft {id} is not installed yet. Install it with the existing launcher first."
        ));
    }
    if let Some(parent) = child["inheritsFrom"].as_str() {
        let (mut base, jar) = chain(game, parent, seen)?;
        merge(&mut base, child);
        Ok((base, jar))
    } else {
        Ok((child, id.into()))
    }
}
fn matches_os(os: &Value) -> bool {
    let platform = if cfg!(windows) {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    };
    if os["name"].as_str().is_some_and(|v| v != platform) {
        return false;
    }
    if let Some(arch) = os["arch"].as_str() {
        let actual = std::env::consts::ARCH;
        let aliases = match actual {
            "x86_64" => vec!["amd64", "x86_64"],
            "aarch64" => vec!["aarch64", "arm64"],
            "x86" => vec!["x86", "i386"],
            other => vec![other],
        };
        if !aliases.contains(&arch) {
            return false;
        }
    }
    // Version-dependent rules need the real OS version, not a guessed string.
    if let Some(pattern) = os["version"].as_str() {
        let version = if cfg!(windows) { "10.0" } else { "" };
        if !regex::Regex::new(pattern).is_ok_and(|r| r.is_match(version)) {
            return false;
        }
    }
    true
}
pub fn allowed(value: &Value, features: &HashMap<String, bool>) -> bool {
    let Some(rules) = value.get("rules").and_then(Value::as_array) else {
        return true;
    };
    let mut allow = false;
    for rule in rules {
        let os = rule.get("os").is_none_or(|v| matches_os(v));
        let feature = rule["features"].as_object().is_none_or(|f| {
            f.iter()
                .all(|(k, v)| v.as_bool() == Some(*features.get(k).unwrap_or(&false)))
        });
        if os && feature {
            allow = rule["action"].as_str() == Some("allow");
        }
    }
    allow
}
pub(crate) fn has_classpath_artifact(library: &Value) -> bool {
    library["downloads"]["artifact"].is_object()
        || (!library["downloads"]["classifiers"].is_object() && !library["natives"].is_object())
}
pub(crate) fn native_artifact(library: &Value, classifier: &str) -> Result<Value, String> {
    let classifiers = &library["downloads"]["classifiers"];
    if let Some(entries) = classifiers.as_object() {
        return entries
            .get(classifier)
            .filter(|item| item.is_object())
            .cloned()
            .ok_or_else(|| format!("Missing native classifier {classifier}"));
    }
    let name = library["name"]
        .as_str()
        .ok_or("Native library name missing")?;
    let path = maven(&format!("{name}:{classifier}"))?;
    let repository = library["url"]
        .as_str()
        .unwrap_or("https://libraries.minecraft.net");
    Ok(json!({"path":path,"url":format!("{}/{}",repository.trim_end_matches('/'),path)}))
}
pub(crate) fn maven(name: &str) -> Result<String, String> {
    let p = name.split(':').collect::<Vec<_>>();
    if !(3..=4).contains(&p.len())
        || p.iter()
            .any(|v| v.is_empty() || v.contains('/') || v.contains('\\') || v.contains('@'))
    {
        return Err("Invalid installed Maven library".into());
    }
    Ok(format!(
        "{}/{}/{}/{}-{}{}.jar",
        p[0].replace('.', "/"),
        p[1],
        p[2],
        p[1],
        p[2],
        p.get(3).map(|v| format!("-{v}")).unwrap_or_default()
    ))
}
fn checked(path: PathBuf, hash: Option<&str>) -> Result<PathBuf, String> {
    let meta = fs::symlink_metadata(&path).map_err(|_| {
        format!(
            "Missing installed game file: {}",
            path.file_name().unwrap_or_default().to_string_lossy()
        )
    })?;
    if !meta.is_file() || meta.file_type().is_symlink() || meta.len() > 512 * 1024 * 1024 {
        return Err("Unsupported installed game file".into());
    }
    if let Some(hash) = hash.filter(|h| h.len() == 40) {
        let mut file = fs::File::open(&path).map_err(|_| "Cannot verify game file")?;
        let mut state = Sha1::new();
        let mut buf = [0u8; 65536];
        loop {
            let n = file
                .read(&mut buf)
                .map_err(|_| "Game file verification failed")?;
            if n == 0 {
                break;
            }
            state.update(&buf[..n]);
        }
        if format!("{:x}", state.finalize()) != hash.to_lowercase() {
            return Err(format!("Installed file checksum mismatch: {}. Repair this installation in the existing launcher.",path.file_name().unwrap_or_default().to_string_lossy()));
        }
    }
    Ok(path)
}
pub(crate) fn platform_library(item: &Value) -> bool {
    let Some(name) = item["name"].as_str() else {
        return false;
    };
    if !name.contains(":natives-") {
        return true;
    }
    let os = if cfg!(windows) {
        "windows"
    } else if cfg!(target_os = "macos") {
        "macos"
    } else {
        "linux"
    };
    let classifier = if cfg!(target_arch = "aarch64") {
        format!("natives-{os}-arm64")
    } else if cfg!(target_arch = "x86") {
        format!("natives-{os}-x86")
    } else {
        format!("natives-{os}")
    };
    name.rsplit(':').next() == Some(classifier.as_str())
}
pub fn resolve(game: &Path, version: &str, engine: &str) -> Result<Files, String> {
    if !["vanilla", "fabric", "quilt", "forge", "neoforge"].contains(&engine) {
        return Err("Forge-family installation processing is still being migrated".into());
    }
    let selected = crate::game_install::selected(game, version, engine)?;
    resolve_selected(game, &selected, engine)
}
pub(crate) fn resolve_selected(game: &Path, selected: &str, engine: &str) -> Result<Files, String> {
    let (manifest, base) = chain(game, selected, &mut HashSet::new())?;
    if engine == "fabric"
        && !manifest["mainClass"]
            .as_str()
            .is_some_and(|v| v.contains("fabricmc"))
    {
        return Err("The selected installation is not a Fabric client".into());
    }
    if matches!(engine, "forge" | "neoforge") {
        let coordinate = if engine == "forge" {
            "net.minecraftforge:forge:"
        } else {
            "net.neoforged.fancymodloader:loader:"
        };
        if !manifest["libraries"].as_array().is_some_and(|a| {
            a.iter().any(|v| {
                v["name"]
                    .as_str()
                    .is_some_and(|n| n.starts_with(coordinate))
            })
        }) {
            return Err("Selected installed loader does not match this loadout".into());
        }
    }
    let features = HashMap::new();
    let mut libraries = vec![];
    let mut natives = vec![];
    for item in manifest["libraries"]
        .as_array()
        .ok_or("Minecraft libraries are missing")?
    {
        if !allowed(item, &features) {
            continue;
        }
        let name = item["name"].as_str().ok_or("Invalid library metadata")?;
        // New manifests put platform native JARs directly on the classpath.
        if name.contains(":natives-") {
            let target = if cfg!(windows) {
                "windows"
            } else if cfg!(target_os = "macos") {
                "macos"
            } else {
                "linux"
            };
            let classifier = name.rsplit(':').next().unwrap_or("");
            let expected = if cfg!(target_arch = "aarch64") {
                format!("natives-{target}-arm64")
            } else if cfg!(target_arch = "x86") {
                format!("natives-{target}-x86")
            } else {
                format!("natives-{target}")
            };
            if classifier != expected {
                continue;
            }
        }
        if has_classpath_artifact(item) {
            let artifact = &item["downloads"]["artifact"];
            let path = artifact["path"]
                .as_str()
                .map(str::to_owned)
                .map(Ok)
                .unwrap_or_else(|| maven(name))?;
            let library = checked(
                relative(&game.join("libraries"), &path)?,
                artifact["sha1"].as_str().or_else(|| item["sha1"].as_str()),
            )?;
            if name.contains(":natives-") {
                natives.push(library.clone());
            }
            libraries.push(library);
        }
        let platform = if cfg!(windows) {
            "windows"
        } else if cfg!(target_os = "macos") {
            "osx"
        } else {
            "linux"
        };
        if let Some(classifier) = item["natives"][platform].as_str() {
            let key = classifier.replace(
                "${arch}",
                if cfg!(target_pointer_width = "64") {
                    "64"
                } else {
                    "32"
                },
            );
            let art = native_artifact(item, &key)?;
            let p = art["path"].as_str().ok_or("Native library path missing")?;
            natives.push(checked(
                relative(&game.join("libraries"), p)?,
                art["sha1"].as_str(),
            )?);
        }
    }
    let client = checked(
        game.join("versions")
            .join(&base)
            .join(format!("{base}.jar")),
        manifest["downloads"]["client"]["sha1"].as_str(),
    )?;
    let mut index = manifest["assetIndex"]["id"]
        .as_str()
        .or_else(|| manifest["assets"].as_str())
        .ok_or("Missing asset index")?
        .to_owned();
    version_name(&index)?;
    // MCLC names indexes after the game version. Accept the alias only with
    // the SAME official SHA1, and pass its actual filename to Minecraft.
    // Numeric indexes can be stale after another version uses the same ID.
    // A version-named alias is usable only if its bytes match THIS manifest.
    let indexes = game.join("assets/indexes");
    let expected = manifest["assetIndex"]["sha1"].as_str();
    if let Err(primary_error) = checked(indexes.join(format!("{index}.json")), expected) {
        if checked(indexes.join(format!("{base}.json")), expected).is_ok() {
            index = base;
        } else {
            return Err(primary_error);
        }
    }
    Ok(Files {
        manifest,
        version: selected.to_owned(),
        libraries,
        native_jars: natives,
        client,
        asset_index: index,
    })
}
pub fn extract_natives(jars: &[PathBuf], dir: &Path) -> Result<(), String> {
    let mut total = 0u64;
    let mut count = 0;
    for path in jars {
        let mut zip =
            zip::ZipArchive::new(fs::File::open(path).map_err(|_| "Cannot read native JAR")?)
                .map_err(|_| "Invalid native JAR")?;
        if zip.len() > 20000 {
            return Err("Native archive too large".into());
        }
        for i in 0..zip.len() {
            let mut file = zip.by_index(i).map_err(|_| "Cannot read native entry")?;
            let name = file.name().to_owned();
            if name.starts_with("META-INF/") || file.is_dir() {
                continue;
            }
            let Some(p) = file.enclosed_name() else {
                return Err("Unsafe native archive path".into());
            };
            let extension = p.extension().and_then(|v| v.to_str()).unwrap_or("");
            if !["dll", "so", "dylib", "jnilib"].contains(&extension) {
                continue;
            }
            if file.size() > 128 * 1024 * 1024 {
                return Err("Native entry too large".into());
            }
            let basename = p
                .file_name()
                .and_then(|v| v.to_str())
                .ok_or("Invalid native filename")?;
            if !lib::safe_filename(basename) {
                return Err("Unsafe native filename".into());
            }
            total += file.size();
            count += 1;
            if total > 512 * 1024 * 1024 || count > 512 {
                return Err("Native extraction exceeds its safety budget".into());
            }
            let dest = dir.join(basename);
            if dest.exists() {
                continue;
            }
            let mut output = fs::OpenOptions::new()
                .create_new(true)
                .write(true)
                .open(&dest)
                .map_err(|_| "Cannot extract native")?;
            std::io::copy(&mut file.by_ref().take(128 * 1024 * 1024 + 1), &mut output)
                .map_err(|_| "Native extraction failed")?;
            output.flush().map_err(|_| "Cannot flush native")?;
        }
    }
    Ok(())
}
pub fn arguments(
    value: &Value,
    features: &HashMap<String, bool>,
    vars: &HashMap<String, String>,
) -> Result<Vec<String>, String> {
    fn substitute(s: &str, vars: &HashMap<String, String>) -> Result<String, String> {
        let mut out = s.to_owned();
        for (k, v) in vars {
            out = out.replace(&format!("${{{k}}}"), v);
        }
        if out.contains("${") {
            return Err("Unsupported Minecraft launch variable".into());
        }
        Ok(out)
    }
    let mut out = vec![];
    for arg in value.as_array().ok_or("Missing Minecraft arguments")? {
        if let Some(s) = arg.as_str() {
            out.push(substitute(s, vars)?);
        } else if allowed(arg, features) {
            match &arg["value"] {
                Value::String(s) => out.push(substitute(s, vars)?),
                Value::Array(a) => {
                    for v in a {
                        out.push(substitute(
                            v.as_str().ok_or("Invalid launch argument")?,
                            vars,
                        )?);
                    }
                }
                _ => return Err("Invalid argument rule".into()),
            }
        }
    }
    Ok(out)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn inherited_platform_classifiers_are_not_removed() {
        let mut parent =
            json!({"libraries":[{"name":"g:lib:1"},{"name":"g:lib:1:natives-windows"}]});
        merge(&mut parent, json!({"libraries":[{"name":"g:lib:2"}]}));
        assert_eq!(parent["libraries"].as_array().unwrap().len(), 2);
        assert!(parent["libraries"]
            .as_array()
            .unwrap()
            .iter()
            .any(|v| v["name"] == "g:lib:1:natives-windows"));
    }
    #[test]
    fn mclc_asset_index_alias_requires_identical_official_hash() {
        let game = tempfile::tempdir().unwrap();
        let version = game.path().join("versions/1.21.1");
        fs::create_dir_all(&version).unwrap();
        fs::create_dir_all(game.path().join("assets/indexes")).unwrap();
        let hash = |b: &[u8]| format!("{:x}", Sha1::digest(b));
        fs::write(version.join("1.21.1.jar"), b"fixture").unwrap();
        fs::write(game.path().join("assets/indexes/1.21.1.json"), b"{}").unwrap();
        fs::write(version.join("1.21.1.json"),json!({"mainClass":"net.minecraft.client.main.Main","libraries":[],"downloads":{"client":{"sha1":hash(b"fixture")}},"assetIndex":{"id":"17","sha1":hash(b"{}")}}).to_string()).unwrap();
        assert_eq!(
            resolve(game.path(), "1.21.1", "vanilla")
                .unwrap()
                .asset_index,
            "1.21.1"
        );
        // A stale numeric file must not hide an already verified MCLC alias.
        fs::write(game.path().join("assets/indexes/17.json"), b"stale").unwrap();
        assert_eq!(resolve(game.path(), "1.21.1", "vanilla").unwrap().asset_index, "1.21.1");
        assert_eq!(fs::read(game.path().join("assets/indexes/17.json")).unwrap(), b"stale");
        fs::write(game.path().join("assets/indexes/17.json"), b"{}").unwrap();
        assert_eq!(resolve(game.path(), "1.21.1", "vanilla").unwrap().asset_index, "17");
        fs::write(game.path().join("assets/indexes/17.json"), b"stale").unwrap();
        fs::write(game.path().join("assets/indexes/1.21.1.json"), b"different").unwrap();
        assert!(resolve(game.path(), "1.21.1", "vanilla").is_err());
    }
    #[test]
    fn rules_and_placeholders() {
        let f = HashMap::new();
        assert!(!allowed(
            &json!({"rules":[{"action":"allow","features":{"is_demo_user":true}}]}),
            &f
        ));
        let vars = HashMap::from([("classpath".into(), "C:\\space folder\\a.jar".into())]);
        assert_eq!(
            arguments(&json!(["-cp", "${classpath}"]), &f, &vars).unwrap()[1],
            "C:\\space folder\\a.jar"
        );
        assert!(arguments(&json!(["${secret}"]), &f, &vars).is_err());
    }
    #[test]
    fn paths_and_maven_are_confined() {
        assert!(relative(Path::new("x"), "../secret").is_err());
        assert!(relative(Path::new("x"), "C:/escape").is_err());
        assert_eq!(
            maven("net.fabricmc:fabric-loader:0.16.0").unwrap(),
            "net/fabricmc/fabric-loader/0.16.0/fabric-loader-0.16.0.jar"
        );
    }
    #[test]
    fn inheritance_keeps_game_args_and_replaces_library_versions() {
        let mut parent = json!({"libraries":[{"name":"a:b:1"}],"arguments":{"game":["--username","${auth_player_name}"]}});
        merge(
            &mut parent,
            json!({"mainClass":"child.Main","libraries":[{"name":"a:b:2"}],"arguments":{"jvm":["-Dtest=1"]}}),
        );
        assert_eq!(parent["libraries"].as_array().unwrap().len(), 1);
        assert_eq!(parent["libraries"][0]["name"], "a:b:2");
        assert_eq!(parent["arguments"]["game"].as_array().unwrap().len(), 2);
    }
}
