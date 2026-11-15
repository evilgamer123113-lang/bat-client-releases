use crate::library as lib;
use serde::Serialize;
use serde_json::{json, Value};
use sha2::{Digest, Sha512};
use std::{
    collections::{HashSet, VecDeque},
    fs,
    io::{Read, Write},
    path::Path,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tauri_plugin_dialog::DialogExt;
const API: &str = "https://api.modrinth.com/v2";
const MAX_FILE: u64 = 100 * 1024 * 1024;
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ModFile {
    pub name: String,
    pub enabled: bool,
    pub managed: bool,
    pub project_id: Option<String>,
    pub icon_data_url: Option<String>,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchHit {
    pub project_id: String,
    pub title: String,
    pub description: String,
    pub author: String,
    pub downloads: u64,
    pub icon_url: Option<String>,
}
#[derive(Serialize)]
pub struct SearchResult {
    pub hits: Vec<SearchHit>,
    pub total: u64,
}
fn client() -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .user_agent("BatClient/0.1 (https://batclient.xyz/contact.html)")
        .redirect(reqwest::redirect::Policy::none())
        .connect_timeout(Duration::from_secs(8))
        .timeout(Duration::from_secs(90))
        .build()
        .map_err(|_| "Cannot initialize mod request".into())
}
fn bounded(mut response: reqwest::blocking::Response, max: u64) -> Result<Vec<u8>, String> {
    if !response.status().is_success() {
        return Err(format!(
            "Modrinth request failed ({})",
            response.status().as_u16()
        ));
    }
    if response.content_length().is_some_and(|n| n > max) {
        return Err("Download is too large".into());
    }
    let mut bytes = Vec::new();
    response
        .by_ref()
        .take(max + 1)
        .read_to_end(&mut bytes)
        .map_err(|_| "Download interrupted")?;
    if bytes.len() as u64 > max {
        return Err("Download exceeded size limit".into());
    }
    Ok(bytes)
}
fn get_json(
    client: &reqwest::blocking::Client,
    url: &str,
    query: &[(&str, String)],
) -> Result<Value, String> {
    let response = client
        .get(url)
        .timeout(Duration::from_secs(20))
        .query(query)
        .send()
        .map_err(|_| "Cannot reach Modrinth")?;
    serde_json::from_slice(&bounded(response, 8 * 1024 * 1024)?)
        .map_err(|_| "Invalid Modrinth response".into())
}
fn clean(value: Option<&Value>, max: usize) -> String {
    value
        .and_then(Value::as_str)
        .unwrap_or("")
        .chars()
        .take(max)
        .collect()
}
fn project_for(name: &str, map: &Value) -> Option<String> {
    let enabled = enabled_name(name);
    if let Some(id) = map
        .get(name)
        .or_else(|| map.get(&enabled))
        .and_then(Value::as_str)
    {
        return Some(id.into());
    }
    let lower = name.to_lowercase();
    let matches = |prefix: &str| {
        lower.strip_prefix(prefix).is_some_and(|suffix| {
            suffix.starts_with(['-', '_'])
                || suffix.chars().next().is_some_and(|c| c.is_ascii_digit())
        })
    };
    if matches("sodium-extra") {
        Some("PtjYWJkn".into())
    } else if matches("reeses-sodium-options") {
        Some("Bh37bMuy".into())
    } else if matches("sodium") {
        Some("AANobbMI".into())
    } else {
        None
    }
}
fn enabled_name(name: &str) -> String {
    if let Some(base) = name.strip_suffix(".jar.disabled") {
        format!("{base}.jar")
    } else if let Some(base) = name.strip_suffix(".disabled") {
        format!("{base}.jar")
    } else {
        name.into()
    }
}
fn preferences(root: &Path) -> Result<Vec<String>, String> {
    let v = lib::read_json(&root.join("dismissed-mods.json"), json!([]))?;
    v.as_array()
        .filter(|a| a.iter().all(Value::is_string))
        .map(|a| {
            a.iter()
                .filter_map(Value::as_str)
                .map(str::to_owned)
                .collect()
        })
        .ok_or("Saved mod choices are invalid; no mods changed".into())
}
pub fn list_at(root: &Path) -> Result<Vec<ModFile>, String> {
    let dir = lib::mods_dir(root)?;
    let map = lib::read_json(&root.join("mod-project-map.json"), json!({}))?;
    let legacy_managed =
        lib::read_json(&root.join("managed-mods.json"), json!([])).unwrap_or_else(|_| json!([]));
    let managed = legacy_managed
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(Value::as_str)
        .map(enabled_name)
        .collect::<HashSet<_>>();
    if !dir.exists() {
        return Ok(vec![]);
    }
    let mut files = Vec::new();
    for entry in fs::read_dir(&dir)
        .map_err(|_| "Cannot read mods folder")?
        .take(3000)
    {
        let entry = entry.map_err(|_| "Cannot inspect mod")?;
        let name = entry.file_name().to_string_lossy().into_owned();
        if !(name.ends_with(".jar") || name.ends_with(".disabled"))
            || lib::regular_file(&dir, &name).is_err()
        {
            continue;
        }
        files.push(ModFile {
            icon_data_url: crate::mod_icon::read(&dir.join(&name)),
            project_id: project_for(&name, &map),
            enabled: name.ends_with(".jar"),
            managed: managed.contains(&enabled_name(&name)),
            name,
        });
    }
    files.sort_by_key(|m| m.name.to_lowercase());
    Ok(files)
}
pub fn change_at(root: &Path, name: &str, action: &str) -> Result<Vec<ModFile>, String> {
    let dir = lib::mods_dir(root)?;
    let source = lib::regular_file(&dir, name)?;
    if !(name.ends_with(".jar") || name.ends_with(".disabled")) {
        return Err("Unsupported mod file".into());
    }
    let map = lib::read_json(&root.join("mod-project-map.json"), json!({}))?;
    if !map.is_object() {
        return Err("Invalid mod identity metadata; no files changed".into());
    }
    let project = project_for(name, &map);
    let old_preferences = preferences(root)?;
    if action == "enable"
        && project
            .as_deref()
            .is_some_and(|p| ["PtjYWJkn", "Bh37bMuy"].contains(&p))
        && !list_at(root)?
            .iter()
            .any(|m| m.enabled && m.project_id.as_deref() == Some("AANobbMI"))
    {
        return Err("Enable Sodium before enabling this dependent add-on".into());
    }
    let destination = match action {
        "disable" if name.ends_with(".jar") => {
            dir.join(format!("{}.disabled", name.trim_end_matches(".jar")))
        }
        "enable" if name.ends_with(".disabled") => dir.join(enabled_name(name)),
        "remove" => {
            let nanos = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map_err(|_| "Clock error")?
                .as_nanos();
            let backup = dir
                .parent()
                .ok_or("Invalid mods folder")?
                .join(".bat-removed-mods")
                .join(nanos.to_string());
            fs::create_dir_all(&backup).map_err(|_| "Cannot prepare removed-mod backup")?;
            backup.join(name)
        }
        _ => return Err("Invalid mod action".into()),
    };
    let mut moves = vec![(source, destination, project.clone())];
    if project.as_deref() == Some("AANobbMI") && action != "enable" {
        for addon in list_at(root)?.into_iter().filter(|m| {
            m.enabled
                && m.project_id
                    .as_deref()
                    .is_some_and(|p| ["PtjYWJkn", "Bh37bMuy"].contains(&p))
        }) {
            moves.push((
                lib::regular_file(&dir, &addon.name)?,
                dir.join(format!("{}.disabled", addon.name.trim_end_matches(".jar"))),
                addon.project_id,
            ));
        }
    }
    for (_, destination, _) in &moves {
        if destination.exists() {
            return Err("Destination already exists; all mod files preserved".into());
        }
    }
    let mut updated = old_preferences;
    for (_, _, id) in &moves {
        if let Some(id) = id {
            updated.retain(|p| p != id);
            if action != "enable" {
                updated.push(id.clone());
            }
        }
    }
    // Save opt-outs before removals. Enable choices change only after successful file move.
    if action != "enable" {
        lib::atomic_json(&root.join("dismissed-mods.json"), &json!(updated))?;
    }
    let mut moved: Vec<(std::path::PathBuf, std::path::PathBuf)> = vec![];
    for (source, destination, _) in &moves {
        let result = fs::hard_link(source, destination).and_then(|_| {
            if let Err(error) = fs::remove_file(source) {
                let _ = fs::remove_file(destination);
                return Err(error);
            }
            Ok(())
        });
        if result.is_err() {
            for (source, destination) in moved.iter().rev() {
                if fs::hard_link(destination, source).is_ok() {
                    let _ = fs::remove_file(destination);
                }
            }
            return Err(
                "Cannot change mods; prior moves restored where possible; opt-outs preserved"
                    .into(),
            );
        }
        moved.push((source.clone(), destination.clone()));
    }
    if action == "enable" {
        if let Err(error) = lib::atomic_json(&root.join("dismissed-mods.json"), &json!(updated)) {
            for (source, destination) in moved.iter().rev() {
                if fs::hard_link(destination, source).is_ok() {
                    let _ = fs::remove_file(destination);
                }
            }
            return Err(error);
        }
    }
    list_at(root)
}
pub(crate) fn apply_preset(root: &Path, filenames: &[String]) -> Result<(), String> {
    let files = list_at(root)?;
    let key = |name: &str| enabled_name(name).to_ascii_lowercase();
    let mut wanted = std::collections::HashMap::new();
    for name in filenames {
        let enabled = name.ends_with(".jar");
        if !(enabled || name.ends_with(".disabled")) || wanted.insert(key(name), enabled).is_some()
        {
            return Err("Invalid or duplicate file in the saved mod preset".into());
        }
    }
    let available = files
        .iter()
        .map(|file| key(&file.name))
        .collect::<std::collections::HashSet<_>>();
    if available.len() != files.len() {
        return Err(
            "Resolve duplicate enabled and disabled mod files before applying a preset".into(),
        );
    }
    if wanted.keys().any(|name| !available.contains(name)) {
        return Err(
            "Some preset mods are missing. Install them before applying the preset.".into(),
        );
    }
    let should_enable = |file: &ModFile| wanted.get(&key(&file.name)).copied().unwrap_or(false);
    let enabled_projects = files
        .iter()
        .filter(|file| should_enable(file))
        .filter_map(|file| file.project_id.as_deref())
        .collect::<std::collections::HashSet<_>>();
    if !enabled_projects.contains("AANobbMI")
        && ["PtjYWJkn", "Bh37bMuy"]
            .iter()
            .any(|id| enabled_projects.contains(id))
    {
        return Err("The preset enables a Sodium add-on without Sodium".into());
    }
    let dir = lib::mods_dir(root)?;
    let mut updated = preferences(root)?;
    let mut moves = Vec::new();
    for file in &files {
        let enabled = should_enable(file);
        if enabled == file.enabled {
            continue;
        }
        let source = lib::regular_file(&dir, &file.name)?;
        let name = if enabled {
            enabled_name(&file.name)
        } else {
            format!("{}.disabled", file.name.trim_end_matches(".jar"))
        };
        let destination = dir.join(name);
        match fs::symlink_metadata(&destination) {
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            _ => return Err("A mod destination already exists or cannot be inspected".into()),
        }
        if let Some(id) = &file.project_id {
            updated.retain(|entry| entry != id);
            if !enabled_projects.contains(id.as_str()) {
                updated.push(id.clone());
            }
        }
        moves.push((source, destination));
    }
    if moves.is_empty() {
        return Ok(());
    }
    let rename = |source: &Path, destination: &Path| {
        fs::hard_link(source, destination)?;
        if let Err(error) = fs::remove_file(source) {
            let _ = fs::remove_file(destination);
            return Err(error);
        }
        Ok::<_, std::io::Error>(())
    };
    let mut completed = 0;
    let result = (|| {
        for (source, destination) in &moves {
            rename(source, destination).map_err(|_| "Cannot change a mod file for the preset")?;
            completed += 1;
        }
        lib::atomic_json(&root.join("dismissed-mods.json"), &json!(updated))
    })();
    if result.is_err() {
        let mut restored = true;
        for (source, destination) in moves[..completed].iter().rev() {
            restored &= rename(destination, source).is_ok();
        }
        if !restored {
            return Err("Could not restore every filename after a preset error. Refresh the mod list before launching.".into());
        }
    }
    result
}

#[tauri::command]
pub async fn installed_mods() -> Result<Vec<ModFile>, String> {
    tauri::async_runtime::spawn_blocking(|| list_at(&lib::root()?))
        .await
        .map_err(|_| "Cannot inspect mods")?
}
#[tauri::command]
pub async fn change_mod(filename: String, action: String) -> Result<Vec<ModFile>, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library is busy")?;
        lib::ensure_game_closed()?;
        change_at(&lib::root()?, &filename, &action)
    })
    .await
    .map_err(|_| "Mod operation failed")?
}
fn valid_id(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 80
        && id
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
}
#[tauri::command]
pub async fn search_modrinth(
    query: String,
    profile_id: String,
    offset: u32,
    sort: Option<String>,
) -> Result<SearchResult, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let root = lib::root()?;
        let (version, loader) = lib::profile(&root, &profile_id)?;
        let facets = json!([
            ["project_type:mod"],
            [format!("versions:{version}")],
            [format!("categories:{loader}")]
        ])
        .to_string();
        let client = client()?;
        let data = get_json(
            &client,
            &format!("{API}/search"),
            &[
                ("query", query.chars().take(150).collect()),
                ("facets", facets),
                ("limit", "20".into()),
                ("offset", offset.min(10000).to_string()),
                ("index", search_index(sort.as_deref())?.into()),
            ],
        )?;
        let hits = data
            .get("hits")
            .and_then(Value::as_array)
            .ok_or("Invalid search results")?
            .iter()
            .take(20)
            .filter_map(|h| {
                let project_id = clean(h.get("project_id"), 80);
                if !valid_id(&project_id) {
                    return None;
                }
                let icon = clean(h.get("icon_url"), 1000);
                let icon_url = reqwest::Url::parse(&icon)
                    .ok()
                    .filter(|u| u.scheme() == "https" && u.host_str() == Some("cdn.modrinth.com"))
                    .map(|_| icon);
                Some(SearchHit {
                    project_id,
                    title: clean(h.get("title"), 100),
                    description: clean(h.get("description"), 300),
                    author: clean(h.get("author"), 80),
                    downloads: h.get("downloads").and_then(Value::as_u64).unwrap_or(0),
                    icon_url,
                })
            })
            .collect();
        Ok(SearchResult {
            hits,
            total: data.get("total_hits").and_then(Value::as_u64).unwrap_or(0),
        })
    })
    .await
    .map_err(|_| "Mod search failed")?
}
fn compatible(v: &Value, game: &str, loader: &str) -> bool {
    [("game_versions", game), ("loaders", loader)]
        .iter()
        .all(|(key, target)| {
            v.get(*key)
                .and_then(Value::as_array)
                .is_some_and(|a| a.iter().any(|v| v.as_str() == Some(target)))
        })
}
fn version_for(
    client: &reqwest::blocking::Client,
    project: &str,
    exact: Option<&str>,
    game: &str,
    loader: &str,
) -> Result<Value, String> {
    if !valid_id(project) || exact.is_some_and(|id| !valid_id(id)) {
        return Err("Invalid Modrinth identifier".into());
    }
    let version = if let Some(id) = exact {
        get_json(client, &format!("{API}/version/{id}"), &[])?
    } else {
        let list = get_json(
            client,
            &format!("{API}/project/{project}/version"),
            &[
                ("game_versions", json!([game]).to_string()),
                ("loaders", json!([loader]).to_string()),
            ],
        )?;
        list.as_array()
            .and_then(|a| a.iter().find(|v| compatible(v, game, loader)))
            .cloned()
            .ok_or("No compatible mod version found")?
    };
    if version.get("project_id").and_then(Value::as_str) != Some(project)
        || !compatible(&version, game, loader)
    {
        return Err(
            "Mod or required dependency does not support this Minecraft version and loader".into(),
        );
    }
    Ok(version)
}
fn search_index(value: Option<&str>) -> Result<&str, String> {
    let value = value.unwrap_or("relevance");
    if ["relevance", "downloads", "follows", "newest", "updated"].contains(&value) {
        Ok(value)
    } else {
        Err("Unsupported mod sort order".into())
    }
}
fn choose_file<'a>(version: &'a Value, filename: Option<&str>) -> Result<&'a Value, String> {
    let files = version["files"].as_array().ok_or("Mod has no downloads")?;
    let file = if let Some(filename) = filename {
        files
            .iter()
            .find(|f| f["filename"].as_str() == Some(filename))
    } else {
        files
            .iter()
            .find(|f| f["primary"] == true)
            .or_else(|| files.first())
    };
    file.ok_or_else(|| "Selected file is not part of this mod version".into())
}
fn public_details(project: &Value, versions: &Value, game: &str, loader: &str) -> Value {
    let gallery = project["gallery"]
        .as_array()
        .into_iter()
        .flatten()
        .take(8)
        .filter_map(|image| {
            let url = image["url"].as_str()?;
            let parsed = reqwest::Url::parse(url).ok()?;
            if parsed.scheme() != "https"
                || parsed.host_str() != Some("cdn.modrinth.com")
                || !parsed.username().is_empty()
                || parsed.password().is_some()
                || parsed.port_or_known_default() != Some(443)
            {
                return None;
            }
            Some(json!({"url":url,"title":clean(image.get("title"),120)}))
        })
        .collect::<Vec<_>>();
    let versions=versions.as_array().into_iter().flatten().filter(|v|compatible(v,game,loader)&&v["project_id"]==project["id"]).take(300).map(|v|json!({"id":clean(v.get("id"),80),"name":clean(v.get("name"),160),"number":clean(v.get("version_number"),100),"type":clean(v.get("version_type"),30),"date":clean(v.get("date_published"),50),"files":v["files"].as_array().into_iter().flatten().filter(|f|f["filename"].as_str().is_some_and(|n|lib::safe_filename(n)&&n.ends_with(".jar"))).take(30).map(|f|json!({"name":clean(f.get("filename"),240),"size":f["size"].as_u64().unwrap_or(0),"primary":f["primary"]==true})).collect::<Vec<_>>()})).collect::<Vec<_>>();
    json!({"id":clean(project.get("id"),80),"title":clean(project.get("title"),160),"description":clean(project.get("description"),1000),"body":clean(project.get("body"),30000),"downloads":project["downloads"].as_u64().unwrap_or(0),"license":clean(project.pointer("/license/id"),80),"gallery":gallery,"versions":versions})
}
#[tauri::command]
pub async fn mod_details(project_id: String, profile_id: String) -> Result<Value, String> {
    tauri::async_runtime::spawn_blocking(move || {
        if !valid_id(&project_id) {
            return Err("Invalid project".into());
        }
        let root = lib::root()?;
        let (game, loader) = lib::profile(&root, &profile_id)?;
        let client = client()?;
        let project = get_json(&client, &format!("{API}/project/{project_id}"), &[])?;
        if project["id"].as_str() != Some(&project_id) {
            return Err("Modrinth project identity mismatch".into());
        }
        let versions = get_json(
            &client,
            &format!("{API}/project/{project_id}/version"),
            &[
                ("game_versions", json!([game]).to_string()),
                ("loaders", json!([loader]).to_string()),
            ],
        )?;
        Ok(public_details(&project, &versions, &game, &loader))
    })
    .await
    .map_err(|_| "Cannot load mod details".to_owned())?
}
#[tauri::command]
pub async fn install_mod_version(
    project_id: String,
    profile_id: String,
    version_id: String,
    filename: String,
) -> Result<Vec<ModFile>, String> {
    tauri::async_runtime::spawn_blocking(move || {
        if !valid_id(&version_id) || !lib::safe_filename(&filename) {
            return Err("Invalid mod selection".into());
        }
        let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library busy")?;
        lib::ensure_legacy_launcher_closed()?;
        lib::ensure_game_closed()?;
        install_selected_at(
            &lib::root()?,
            &project_id,
            &profile_id,
            Some(&version_id),
            Some(&filename),
            None,
        )
    })
    .await
    .map_err(|_| "Mod installation failed".to_owned())?
}
pub fn install_at(root: &Path, project: &str, profile_id: &str) -> Result<Vec<ModFile>, String> {
    install_selected_at(root, project, profile_id, None, None, None)
}
pub(crate) fn ensure_fabric_api(root: &Path, profile_id: &str, version: &str) -> Result<(), String> {
    let minor = version
        .split('.')
        .nth(1)
        .and_then(|v| v.parse::<u32>().ok())
        .unwrap_or(0);
    if minor < 18 {
        return Ok(());
    }
    let dir = lib::mods_dir(root)?;
    if dir.is_dir() {
        if let Ok(entries) = fs::read_dir(&dir) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().into_owned();
                if !name.ends_with(".jar") {
                    continue;
                }
                if let Ok(file) = fs::File::open(entry.path()) {
                    if let Ok(mut zip) = zip::ZipArchive::new(file) {
                        if let Ok(mut meta) = zip.by_name("fabric.mod.json") {
                            let mut text = String::new();
                            if std::io::Read::read_to_string(&mut meta, &mut text).is_ok() {
                                if let Ok(json) = serde_json::from_str::<Value>(&text) {
                                    if matches!(json["id"].as_str(), Some("fabric-api" | "fabric")) {
                                        return Ok(());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    let existing = list_at(root)?;
    let old_name = existing
        .iter()
        .find(|m| m.project_id.as_deref() == Some("P7dR8mSH"))
        .map(|m| m.name.clone());
    install_selected_at(
        root,
        "P7dR8mSH",
        profile_id,
        None,
        None,
        old_name.as_deref(),
    )?;
    Ok(())
}
fn install_selected_at(
    root: &Path,
    project: &str,
    profile_id: &str,
    version_id: Option<&str>,
    filename: Option<&str>,
    replacement: Option<&str>,
) -> Result<Vec<ModFile>, String> {
    let (game, loader) = lib::profile(root, profile_id)?;
    let dir = lib::mods_dir(root)?;
    fs::create_dir_all(&dir).map_err(|_| "Cannot create mods folder")?;
    let client = client()?;
    let existing = list_at(root)?;
    let opt_outs = preferences(root)?;
    let requested_project = project.to_owned();
    let mut queue = VecDeque::from([(project.to_owned(), version_id.map(str::to_owned))]);
    let mut seen = HashSet::new();
    let mut plan = Vec::new();
    while let Some((project, exact)) = queue.pop_front() {
        if !seen.insert((project.clone(), exact.clone())) {
            continue;
        }
        if seen.len() > 40 {
            return Err("Dependency graph exceeds safety limit".into());
        }
        if project != requested_project
            && (opt_outs.contains(&project)
                || (["PtjYWJkn", "Bh37bMuy"].contains(&project.as_str())
                    && opt_outs.iter().any(|id| id == "AANobbMI")))
        {
            return Err("A required dependency was previously removed or disabled. Install or enable that dependency explicitly in Installed/Modrinth first.".into());
        }
        let version = version_for(&client, &project, exact.as_deref(), &game, &loader)?;
        if let Some(deps) = version.get("dependencies").and_then(Value::as_array) {
            for dep in deps {
                if dep.get("dependency_type").and_then(Value::as_str) != Some("required") {
                    continue;
                }
                let id = dep.get("project_id").and_then(Value::as_str);
                let exact = dep.get("version_id").and_then(Value::as_str);
                let dependency_project = if let Some(id) = id {
                    id.to_owned()
                } else if let Some(exact) = exact {
                    if !valid_id(exact) {
                        return Err("Invalid dependency".into());
                    }
                    clean(
                        get_json(&client, &format!("{API}/version/{exact}"), &[])?
                            .get("project_id"),
                        80,
                    )
                } else {
                    return Err("Required dependency cannot be resolved".into());
                };
                queue.push_back((dependency_project, exact.map(str::to_owned)));
            }
        }
        let file = choose_file(
            &version,
            if project == requested_project {
                filename
            } else {
                None
            },
        )?
        .clone();
        let name = clean(file.get("filename"), 240);
        if !lib::safe_filename(&name) || !name.ends_with(".jar") {
            return Err("Unsafe mod download filename".into());
        }
        let hash = clean(file.pointer("/hashes/sha512"), 128);
        if hash.len() != 128 || !hash.chars().all(|c| c.is_ascii_hexdigit()) {
            return Err("Modrinth did not supply a valid SHA512 checksum".into());
        }
        if let Some(current) = existing
            .iter()
            .find(|m| m.project_id.as_deref() == Some(&project))
        {
            let same = mod_hash(&lib::regular_file(&dir, &current.name)?)? == hash;
            if same && current.enabled {
                continue;
            }
            if (!current.enabled && replacement != Some(current.name.as_str()))
                || project != requested_project
                || replacement != Some(current.name.as_str())
            {
                return Err(format!("An installed or disabled version of {} differs. Manage it in Installed before installing this version.",current.name));
            }
        }
        for candidate in [
            &name,
            &name.replace(".jar", ".disabled"),
            &format!("{name}.disabled"),
        ] {
            if dir.join(candidate).exists()
                && !(project == requested_project && replacement == Some(candidate.as_str()))
            {
                return Err(
                    "A mod with this filename already exists; no replacement performed".into(),
                );
            }
        }
        let url = clean(file.get("url"), 2000);
        let parsed = reqwest::Url::parse(&url).map_err(|_| "Invalid download URL")?;
        if parsed.scheme() != "https"
            || parsed.host_str() != Some("cdn.modrinth.com")
            || parsed.port_or_known_default() != Some(443)
            || !parsed.username().is_empty()
            || parsed.password().is_some()
        {
            return Err("Untrusted mod download host".into());
        }
        if plan.iter().any(|(_, n, _, _)| n == &name) {
            return Err("Dependency filename collision".into());
        }
        plan.push((project, name, url, hash));
    }
    if plan.is_empty() {
        return Ok(existing);
    }
    // Download and verify everything before committing any files.
    let mut staged = Vec::new();
    let mut total = 0usize;
    for (project, name, url, hash) in plan {
        let response = client.get(&url).send().map_err(|_| "Cannot download mod")?;
        let bytes = bounded(response, MAX_FILE)?;
        total += bytes.len();
        if total > 300 * 1024 * 1024 {
            return Err("Install exceeds total size limit".into());
        }
        if !bytes.starts_with(b"PK\x03\x04") || format!("{:x}", Sha512::digest(&bytes)) != hash {
            return Err("Mod checksum or archive signature is invalid; no files installed".into());
        }
        let mut temp = tempfile::NamedTempFile::new_in(&dir).map_err(|_| "Cannot stage mod")?;
        temp.write_all(&bytes)
            .map_err(|_| "Cannot write staged mod")?;
        temp.as_file()
            .sync_all()
            .map_err(|_| "Cannot flush staged mod")?;
        staged.push((project, name, temp));
    }
    lib::ensure_game_closed()?;
    commit_mods(root, &dir, &staged, replacement)?;
    list_at(root)
}
fn mod_hash(path: &Path) -> Result<String, String> {
    let mut file = fs::File::open(path).map_err(|_| "Cannot read installed mod")?;
    if file.metadata().map_err(|_| "Cannot inspect mod")?.len() > MAX_FILE {
        return Err("Installed mod exceeds verification size limit".into());
    }
    let mut hash = Sha512::new();
    let mut buffer = [0u8; 65536];
    loop {
        let n = file
            .read(&mut buffer)
            .map_err(|_| "Cannot hash installed mod")?;
        if n == 0 {
            break;
        }
        hash.update(&buffer[..n]);
    }
    Ok(format!("{:x}", hash.finalize()))
}
fn commit_mods(
    root: &Path,
    dir: &Path,
    staged: &[(String, String, tempfile::NamedTempFile)],
    replacement: Option<&str>,
) -> Result<(), String> {
    let map_path = root.join("mod-project-map.json");
    let choices_path = root.join("dismissed-mods.json");
    let original_map = lib::read_json(&map_path, json!({}))?;
    if !original_map.is_object() {
        return Err("Invalid mod project map".into());
    }
    let original_choices = preferences(root)?;
    for (_, name, _) in staged {
        if !lib::safe_filename(name) || !(name.ends_with(".jar") || name.ends_with(".disabled")) {
            return Err("Invalid staged mod filename".into());
        }
        if dir.join(name).exists() && replacement != Some(name.as_str()) {
            return Err("Install destination already exists".into());
        }
    }
    let mut backup = None;
    if let Some(name) = replacement {
        let source = lib::regular_file(dir, name)?;
        if !name.ends_with(".jar") {
            return Err("Disabled mods are not updated".into());
        }
        let destination = dir
            .parent()
            .ok_or("Invalid mods folder")?
            .join(".bat-mod-update-backups")
            .join(uuid::Uuid::new_v4().to_string())
            .join(name);
        crate::downloads::parent(&destination)?;
        lib::atomic_json(
            &destination.parent().unwrap().join("metadata.json"),
            &json!({"modMap":original_map,"dismissedMods":original_choices}),
        )?;
        fs::rename(&source, &destination)
            .map_err(|_| "Cannot preserve installed mod; close Minecraft")?;
        backup = Some((source, destination));
    }
    let mut committed: Vec<std::path::PathBuf> = vec![];
    let result = (|| {
        for (_, name, temp) in staged {
            let destination = dir.join(name);
            fs::hard_link(temp.path(), &destination)
                .map_err(|_| "Install conflict; changes will be rolled back")?;
            committed.push(destination);
        }
        let mut map = original_map.clone();
        if let Some(name) = replacement {
            map.as_object_mut().unwrap().remove(name);
        }
        for (project, name, _) in staged {
            if valid_id(project) {
                map[name] = json!(project);
            }
        }
        let mut choices = original_choices.clone();
        choices.retain(|id| {
            !staged
                .iter()
                .any(|(project, name, _)| project == id && name.ends_with(".jar"))
        });
        lib::atomic_json(&map_path, &map)?;
        lib::atomic_json(&choices_path, &json!(choices))?;
        Ok::<(), String>(())
    })();
    if let Err(error) = result {
        for path in committed {
            if let Some((_, _, temp)) = staged.iter().find(|(_, name, _)| dir.join(name) == path) {
                if let (Ok(actual), Ok(expected)) = (mod_hash(&path), mod_hash(temp.path())) {
                    if actual == expected {
                        let _ = fs::remove_file(path);
                    }
                }
            }
        }
        let _ = lib::atomic_json(&map_path, &original_map);
        let _ = lib::atomic_json(&choices_path, &json!(original_choices));
        if let Some((source, destination)) = backup {
            if source.exists() || fs::rename(&destination, &source).is_err() {
                return Err(format!(
                    "{error}. Original mod remains preserved in the update backup folder."
                ));
            }
        }
        return Err(error);
    }
    Ok(())
}
fn verified_project<'a>(identity: &'a Value, expected: Option<&str>) -> Result<&'a str, String> {
    let project = identity["project_id"].as_str().filter(|id| valid_id(id))
        .ok_or("This file has no verified Modrinth identity; disable it or replace it manually")?;
    if expected.is_some_and(|expected| expected != project) {
        return Err("Installed file does not match its Modrinth identity".into());
    }
    Ok(project)
}
#[tauri::command]
pub async fn check_mod_updates(profile_id: String) -> Result<Value, String> {
    tauri::async_runtime::spawn_blocking(move||{
    let root=lib::root()?;let(game,loader)=lib::profile(&root,&profile_id)?;let dir=lib::mods_dir(&root)?;let client=client()?;let files=list_at(&root)?;let mut updates=vec![];let mut errors=vec![];let mut checked=0;let started=std::time::Instant::now();
    for file in files.iter().filter(|m|m.enabled).take(100){if started.elapsed()>Duration::from_secs(60){errors.push("Remaining checks deferred because the metadata service is taking too long".into());break;}let result=(||{let hash=mod_hash(&lib::regular_file(&dir,&file.name)?)?;let identity=get_json(&client,&format!("{API}/version_file/{hash}"),&[("algorithm","sha512".into())])?;let project=verified_project(&identity,file.project_id.as_deref())?;let version=version_for(&client,project,None,&game,&loader)?;let target=choose_file(&version,None)?;if target["hashes"]["sha512"].as_str()!=Some(&hash){updates.push(json!({"filename":file.name,"projectId":project,"versionId":version["id"],"versionName":version["version_number"],"expectedHash":hash}));}Ok::<(),String>(())})();checked+=1;if let Err(error)=result{errors.push(format!("{}: {error}",file.name));}}
    Ok(json!({"updates":updates,"checked":checked,"errors":errors,"skipped":files.len().saturating_sub(checked)}))
}).await.map_err(|_|"Mod update check interrupted".to_owned())?
}
#[tauri::command]
pub async fn update_mod(
    filename: String,
    profile_id: String,
    version_id: String,
    expected_hash: String,
) -> Result<Vec<ModFile>, String> {
    tauri::async_runtime::spawn_blocking(move || {
        if !valid_id(&version_id)
            || expected_hash.len() != 128
            || !expected_hash.chars().all(|c| c.is_ascii_hexdigit())
        {
            return Err("Invalid update selection".into());
        }
        let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library busy")?;
        lib::ensure_legacy_launcher_closed()?;
        lib::ensure_game_closed()?;
        let root = lib::root()?;
        let files = list_at(&root)?;
        let file = files
            .iter()
            .find(|m| m.name == filename && m.enabled)
            .ok_or("The mod was removed or disabled; update skipped")?;
        let identity = get_json(&client()?, &format!("{API}/version_file/{expected_hash}"),
            &[("algorithm", "sha512".into())])?;
        let project = verified_project(&identity, file.project_id.as_deref())?;
        if files
            .iter()
            .filter(|m| m.project_id.as_deref() == Some(project))
            .count()
            > 1
        {
            return Err(
                "Multiple files belong to this project. Resolve duplicates before updating".into(),
            );
        }
        if mod_hash(&lib::regular_file(&lib::mods_dir(&root)?, &filename)?)? != expected_hash {
            return Err("Installed mod changed since the check. Check updates again".into());
        }
        let identity = get_json(
            &client()?,
            &format!("{API}/version_file/{expected_hash}"),
            &[("algorithm", "sha512".into())],
        )?;
        if identity["project_id"].as_str() != Some(project) {
            return Err("Installed mod identity could not be verified".into());
        }
        install_selected_at(
            &root,
            project,
            &profile_id,
            Some(&version_id),
            None,
            Some(&filename),
        )
    })
    .await
    .map_err(|_| "Mod update interrupted".to_owned())?
}

#[tauri::command]
pub async fn install_mod(project_id: String, profile_id: String) -> Result<Vec<ModFile>, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library is busy")?;
        lib::ensure_legacy_launcher_closed()?;
        lib::ensure_game_closed()?;
        install_at(&lib::root()?, &project_id, &profile_id)
    })
    .await
    .map_err(|_| "Mod installation failed")?
}
fn export_pack(root: &Path, profile_id: &str, path: &Path) -> Result<usize, String> {
    let (game, loader) = lib::profile(root, profile_id)?;
    let files = list_at(root)?;
    if files.len() > 200 {
        return Err("Modpack exceeds 200 files".into());
    }
    let dir = lib::mods_dir(root)?;
    let parent = path.parent().ok_or("Invalid export path")?;
    let staged =
        tempfile::NamedTempFile::new_in(parent).map_err(|_| "Cannot stage modpack export")?;
    let mut archive =
        zip::ZipWriter::new(staged.reopen().map_err(|_| "Cannot open modpack export")?);
    let mut entries = vec![];
    let mut total = 0u64;
    for item in &files {
        let file_path = lib::regular_file(&dir, &item.name)?;
        let size = fs::metadata(&file_path)
            .map_err(|_| "Cannot inspect mod")?
            .len();
        total += size;
        if size > MAX_FILE || total > 512 * 1024 * 1024 {
            return Err("Modpack exceeds 512 MB export limit".into());
        }
        let hash = mod_hash(&file_path)?;
        archive
            .start_file(
                format!("mods/{}", item.name),
                zip::write::SimpleFileOptions::default()
                    .compression_method(zip::CompressionMethod::Deflated),
            )
            .map_err(|_| "Cannot write modpack")?;
        let mut input = fs::File::open(&file_path).map_err(|_| "Cannot read mod")?;
        std::io::copy(&mut input, &mut archive).map_err(|_| "Cannot export mod")?;
        entries.push(json!({"name":item.name,"enabled":item.enabled,"sha512":hash,"projectId":item.project_id}));
    }
    archive
        .start_file("bat-modpack.json", zip::write::SimpleFileOptions::default())
        .map_err(|_| "Cannot write manifest")?;
    archive
        .write_all(
            &serde_json::to_vec(
                &json!({"schema":1,"minecraft":game,"loader":loader,"files":entries}),
            )
            .map_err(|_| "Cannot encode modpack")?,
        )
        .map_err(|_| "Cannot export manifest")?;
    archive
        .start_file("manifest.json", zip::write::SimpleFileOptions::default())
        .map_err(|_| "Cannot write compatibility manifest")?;
    archive.write_all(&serde_json::to_vec(&json!({"version":1,"name":"Bat Client modpack","mods":files.iter().map(|m|json!({"filename":m.name,"enabled":m.enabled})).collect::<Vec<_>>(),"managedMods":[]})).map_err(|_|"Cannot encode manifest")?).map_err(|_|"Cannot write manifest")?;
    let file = archive.finish().map_err(|_| "Cannot finalize modpack")?;
    file.sync_all().map_err(|_| "Cannot flush modpack")?;
    drop(file);
    staged.persist(path).map_err(|_| "Cannot save modpack")?;
    Ok(files.len())
}
fn import_pack(root: &Path, profile_id: &str, path: &Path) -> Result<usize, String> {
    let (game, loader) = lib::profile(root, profile_id)?;
    if fs::metadata(path)
        .map_err(|_| "Cannot inspect modpack")?
        .len()
        > 600 * 1024 * 1024
    {
        return Err("Modpack archive is too large".into());
    }
    let mut archive =
        zip::ZipArchive::new(fs::File::open(path).map_err(|_| "Cannot open modpack")?)
            .map_err(|_| "Invalid modpack ZIP")?;
    if archive.len() > 205 {
        return Err("Modpack contains too many entries".into());
    }
    let legacy = archive.by_name("bat-modpack.json").is_err();
    let manifest_name = if legacy {
        "manifest.json"
    } else {
        "bat-modpack.json"
    };
    let mut manifest = {
        let mut entry = archive
            .by_name(manifest_name)
            .map_err(|_| "Choose a Bat Client modpack")?;
        if entry.size() > 1024 * 1024 {
            return Err("Modpack manifest too large".into());
        }
        let mut bytes = vec![];
        entry
            .by_ref()
            .take(1024 * 1024 + 1)
            .read_to_end(&mut bytes)
            .map_err(|_| "Cannot read manifest")?;
        if bytes.len() > 1024 * 1024 {
            return Err("Modpack manifest too large".into());
        }
        serde_json::from_slice::<Value>(&bytes).map_err(|_| "Invalid modpack manifest")?
    };
    if legacy {
        if manifest["version"] != 1 {
            return Err("Unsupported legacy modpack".into());
        }
        let files = manifest["mods"]
            .as_array()
            .filter(|a| a.len() <= 200)
            .ok_or("Invalid legacy modpack")?
            .iter()
            .map(|m| {
                let name = m["filename"].as_str().unwrap_or("");
                json!({"name":name,"enabled":m["enabled"],"projectId":project_for(name,&json!({}))})
            })
            .collect::<Vec<_>>();
        manifest = json!({"schema":1,"minecraft":game,"loader":loader,"files":files});
    }
    if manifest["schema"] != 1
        || manifest["minecraft"].as_str() != Some(&game)
        || manifest["loader"].as_str() != Some(&loader)
    {
        return Err(
            "Select a loadout with the Minecraft version and loader used by this modpack".into(),
        );
    }
    let entries = manifest["files"]
        .as_array()
        .filter(|a| a.len() <= 200)
        .ok_or("Invalid modpack file list")?;
    let mut allowed = HashSet::from([manifest_name.to_owned(), "mods/".into()]);
    if legacy {
        allowed.insert("config/".into());
        allowed.insert("config/profiles.config.json".into());
    } else {
        allowed.insert("manifest.json".into());
    }
    let mut planned_names = HashSet::new();
    let dir = lib::mods_dir(root)?;
    fs::create_dir_all(&dir).map_err(|_| "Cannot prepare mods folder")?;
    let existing = list_at(root)?;
    let choices = preferences(root)?;
    let mut staged = vec![];
    let mut total = 0usize;
    for item in entries {
        let original = item["name"].as_str().ok_or("Missing modpack filename")?;
        if !lib::safe_filename(original)
            || !(original.ends_with(".jar") || original.ends_with(".disabled"))
        {
            return Err("Unsafe modpack filename".into());
        }
        let expected = if legacy {
            None
        } else {
            Some(
                item["sha512"]
                    .as_str()
                    .filter(|h| h.len() == 128 && h.chars().all(|c| c.is_ascii_hexdigit()))
                    .ok_or("Modpack checksum missing")?,
            )
        };
        let project = item["projectId"]
            .as_str()
            .filter(|id| valid_id(id))
            .unwrap_or("");
        let key = enabled_name(original);
        if !planned_names.insert(key.clone()) {
            return Err("Duplicate modpack mod".into());
        }
        let entry_name = format!("mods/{original}");
        if !allowed.insert(entry_name.clone()) {
            return Err("Duplicate modpack file".into());
        }
        let mut entry = archive
            .by_name(&entry_name)
            .map_err(|_| "Modpack file is missing")?;
        if entry.size() > MAX_FILE || entry.unix_mode().is_some_and(|m| m & 0o170000 == 0o120000) {
            return Err("Unsafe or oversized modpack file".into());
        }
        let mut bytes = vec![];
        entry
            .by_ref()
            .take(MAX_FILE + 1)
            .read_to_end(&mut bytes)
            .map_err(|_| "Cannot extract modpack file")?;
        total += bytes.len();
        if bytes.len() as u64 > MAX_FILE || total > 512 * 1024 * 1024 {
            return Err("Modpack exceeds extraction limit".into());
        }
        let hash = format!("{:x}", Sha512::digest(&bytes));
        if expected.is_some_and(|expected| expected != hash)
            || zip::ZipArchive::new(std::io::Cursor::new(&bytes)).is_err()
        {
            return Err("Modpack checksum or JAR validation failed; no mods installed".into());
        }
        if let Some(current) = existing.iter().find(|m| {
            enabled_name(&m.name) == key
                || (!project.is_empty() && m.project_id.as_deref() == Some(project))
        }) {
            if mod_hash(&lib::regular_file(&dir, &current.name)?)? != hash {
                return Err(format!(
                    "{} already has a different installed version. No mods imported",
                    current.name
                ));
            }
            continue;
        }
        let enabled = item["enabled"]
            .as_bool()
            .unwrap_or(original.ends_with(".jar"))
            && !choices.iter().any(|id| id == project);
        let name = if enabled {
            key
        } else {
            format!("{}.disabled", key.trim_end_matches(".jar"))
        };
        let mut temp = tempfile::NamedTempFile::new_in(&dir).map_err(|_| "Cannot stage modpack")?;
        temp.write_all(&bytes).map_err(|_| "Cannot stage mod")?;
        temp.as_file()
            .sync_all()
            .map_err(|_| "Cannot flush staged mod")?;
        staged.push((project.to_owned(), name, temp));
    }
    let mut seen = HashSet::new();
    for index in 0..archive.len() {
        let entry = archive
            .by_index(index)
            .map_err(|_| "Cannot inspect archive")?;
        if !allowed.contains(entry.name()) || !seen.insert(entry.name().to_owned()) {
            return Err("Modpack contains an unexpected path".into());
        }
    }
    let sodium = existing
        .iter()
        .any(|m| m.enabled && m.project_id.as_deref() == Some("AANobbMI"))
        || staged
            .iter()
            .any(|(p, n, _)| p == "AANobbMI" && n.ends_with(".jar"));
    if !sodium {
        for (project, name, _) in &mut staged {
            if ["PtjYWJkn", "Bh37bMuy"].contains(&project.as_str()) && name.ends_with(".jar") {
                *name = format!("{}.disabled", name.trim_end_matches(".jar"));
            }
        }
    }
    lib::ensure_game_closed()?;
    commit_mods(root, &dir, &staged, None)?;
    Ok(staged.len())
}
#[tauri::command]
pub async fn modpack_action(
    app: tauri::AppHandle,
    profile_id: String,
    action: String,
) -> Result<Value, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let path = match action.as_str() {
            "export" => app
                .dialog()
                .file()
                .set_file_name("BatClient-modpack.batpack")
                .add_filter("Bat Client modpack", &["batpack", "zip"])
                .blocking_save_file(),
            "import" => app
                .dialog()
                .file()
                .add_filter("Bat Client modpack", &["batpack", "zip"])
                .blocking_pick_file(),
            _ => return Err("Invalid modpack action".into()),
        };
        let Some(path) = path else {
            return Ok(Value::Null);
        };
        let path = path.into_path().map_err(|_| "Invalid selected path")?;
        let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library busy")?;
        lib::ensure_legacy_launcher_closed()?;
        lib::ensure_game_closed()?;
        let root = lib::root()?;
        let count = if action == "export" {
            export_pack(&root, &profile_id, &path)?
        } else {
            import_pack(&root, &profile_id, &path)?
        };
        Ok(json!({"count":count,"mods":list_at(&root)?}))
    })
    .await
    .map_err(|_| "Modpack operation interrupted".to_owned())?
}
#[cfg(test)]
mod tests {
    use super::*;
    fn fixture() -> tempfile::TempDir {
        let root = tempfile::tempdir().unwrap();
        fs::create_dir_all(root.path().join("minecraft_runtime/mods")).unwrap();
        root
    }
    #[test]
    fn copied_mod_identity_must_be_verified_before_replacement() {
        assert_eq!(verified_project(&json!({"project_id":"fabric-api"}),None).unwrap(),"fabric-api");
        assert!(verified_project(&json!({"project_id":"fabric-api"}),Some("sodium")).is_err());
        assert!(verified_project(&json!({}),None).is_err());
        assert!(verified_project(&json!({"project_id":"../bad"}),None).is_err());
    }
    #[test]
    fn preset_respects_legacy_disabled_names_and_rejects_missing_mods() {
        let root = fixture();
        let dir = root.path().join("minecraft_runtime/mods");
        fs::write(dir.join("keep.jar"), jar_bytes()).unwrap();
        fs::write(dir.join("other.jar"), jar_bytes()).unwrap();
        assert!(apply_preset(root.path(), &["missing.jar".into()]).is_err());
        assert!(dir.join("other.jar").is_file());
        apply_preset(root.path(), &["keep.jar.disabled".into()]).unwrap();
        assert!(dir.join("keep.disabled").is_file());
        assert!(dir.join("other.disabled").is_file());
        apply_preset(root.path(), &["keep.jar".into()]).unwrap();
        assert!(dir.join("keep.jar").is_file());
        assert!(dir.join("other.disabled").is_file());
    }
    fn staged(root: &Path, name: &str, bytes: &[u8]) -> (String, String, tempfile::NamedTempFile) {
        let mut temp =
            tempfile::NamedTempFile::new_in(root.join("minecraft_runtime/mods")).unwrap();
        temp.write_all(bytes).unwrap();
        ("project".into(), name.into(), temp)
    }
    fn pack_profile(root: &Path) {
        lib::atomic_json(&root.join("profiles.config.json"),&json!({"profiles":[{"id":"p","version":"1.21.1","engine":"fabric"}],"savedAccounts":[{"username":"Keep","token":"PRIVATE"}]})).unwrap();
    }
    fn jar_bytes() -> Vec<u8> {
        let mut out = std::io::Cursor::new(vec![]);
        {
            let mut zip = zip::ZipWriter::new(&mut out);
            zip.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default())
                .unwrap();
            zip.write_all(b"{}").unwrap();
            zip.finish().unwrap();
        }
        out.into_inner()
    }
    #[test]
    #[ignore = "Downloads two real mod versions into a disposable fixture and verifies update backup"]
    fn live_mod_update_roundtrip() {
        let root = fixture();
        pack_profile(root.path());
        lib::atomic_json(
            &root.path().join("dismissed-mods.json"),
            &json!(["AANobbMI"]),
        )
        .unwrap();
        let client = client().unwrap();
        let all = get_json(
            &client,
            &format!("{API}/project/uXXizFIs/version"),
            &[
                ("game_versions", json!(["1.21.1"]).to_string()),
                ("loaders", json!(["fabric"]).to_string()),
            ],
        )
        .unwrap();
        let versions = all.as_array().unwrap();
        let latest = versions
            .iter()
            .find(|v| compatible(v, "1.21.1", "fabric"))
            .unwrap();
        let latest_hash = choose_file(latest, None).unwrap()["hashes"]["sha512"]
            .as_str()
            .unwrap();
        let older = versions
            .iter()
            .rev()
            .find(|v| {
                compatible(v, "1.21.1", "fabric")
                    && choose_file(v, None)
                        .ok()
                        .is_some_and(|f| f["hashes"]["sha512"].as_str() != Some(latest_hash))
            })
            .expect("No distinct older compatible fixture version");
        let files = install_selected_at(
            root.path(),
            "uXXizFIs",
            "p",
            older["id"].as_str(),
            None,
            None,
        )
        .unwrap();
        let old = files
            .iter()
            .find(|m| m.project_id.as_deref() == Some("uXXizFIs"))
            .unwrap();
        let old_path = root.path().join("minecraft_runtime/mods").join(&old.name);
        let old_bytes = fs::read(&old_path).unwrap();
        let original_hash = mod_hash(&old_path).unwrap();
        let identity = get_json(
            &client,
            &format!("{API}/version_file/{original_hash}"),
            &[("algorithm", "sha512".into())],
        )
        .unwrap();
        assert_eq!(identity["project_id"], "uXXizFIs");
        let updated = install_selected_at(
            root.path(),
            "uXXizFIs",
            "p",
            latest["id"].as_str(),
            None,
            Some(&old.name),
        )
        .unwrap();
        let current = updated
            .iter()
            .find(|m| m.project_id.as_deref() == Some("uXXizFIs"))
            .unwrap();
        assert_eq!(
            mod_hash(
                &root
                    .path()
                    .join("minecraft_runtime/mods")
                    .join(&current.name)
            )
            .unwrap(),
            latest_hash
        );
        let backup = fs::read_dir(
            root.path()
                .join("minecraft_runtime/.bat-mod-update-backups"),
        )
        .unwrap()
        .next()
        .unwrap()
        .unwrap()
        .path();
        assert_eq!(fs::read(backup.join(&old.name)).unwrap(), old_bytes);
        assert!(preferences(root.path())
            .unwrap()
            .contains(&"AANobbMI".into()));
        println!("LIVE_MOD_UPDATE_BACKUP_PASS");
    }
    #[test]
    fn modpack_roundtrip_preserves_disable_choices_and_excludes_accounts() {
        let source = fixture();
        pack_profile(source.path());
        let bytes = jar_bytes();
        fs::write(
            source.path().join("minecraft_runtime/mods/sodium-1.jar"),
            &bytes,
        )
        .unwrap();
        fs::write(
            source.path().join("minecraft_runtime/mods/other.disabled"),
            &bytes,
        )
        .unwrap();
        let archive = source.path().join("pack.batpack");
        assert_eq!(export_pack(source.path(), "p", &archive).unwrap(), 2);
        let mut zip = zip::ZipArchive::new(fs::File::open(&archive).unwrap()).unwrap();
        assert!(zip.by_name("config/profiles.config.json").is_err());
        let target = fixture();
        pack_profile(target.path());
        lib::atomic_json(
            &target.path().join("dismissed-mods.json"),
            &json!(["AANobbMI"]),
        )
        .unwrap();
        assert_eq!(import_pack(target.path(), "p", &archive).unwrap(), 2);
        assert!(target
            .path()
            .join("minecraft_runtime/mods/sodium-1.disabled")
            .exists());
        assert!(!target
            .path()
            .join("minecraft_runtime/mods/sodium-1.jar")
            .exists());
        assert_eq!(preferences(target.path()).unwrap(), vec!["AANobbMI"]);
        assert_eq!(
            lib::config(target.path()).unwrap()["savedAccounts"][0]["token"],
            "PRIVATE"
        );
    }
    #[test]
    fn legacy_pack_imports_mods_without_copying_private_configuration() {
        let root = fixture();
        pack_profile(root.path());
        let path = root.path().join("legacy.batpack");
        let mut zip = zip::ZipWriter::new(fs::File::create(&path).unwrap());
        for (name, bytes) in [
            (
                "manifest.json",
                serde_json::to_vec(
                    &json!({"version":1,"mods":[{"filename":"legacy.jar","enabled":true}]}),
                )
                .unwrap(),
            ),
            ("mods/legacy.jar", jar_bytes()),
            (
                "config/profiles.config.json",
                b"do not import accounts".to_vec(),
            ),
        ] {
            zip.start_file(name, zip::write::SimpleFileOptions::default())
                .unwrap();
            zip.write_all(&bytes).unwrap();
        }
        zip.finish().unwrap();
        assert_eq!(import_pack(root.path(), "p", &path).unwrap(), 1);
        assert_eq!(
            lib::config(root.path()).unwrap()["savedAccounts"][0]["token"],
            "PRIVATE"
        );
    }
    #[test]
    fn modpack_unexpected_paths_do_not_install_anything() {
        let root = fixture();
        pack_profile(root.path());
        let path = root.path().join("bad.zip");
        let mut zip = zip::ZipWriter::new(fs::File::create(&path).unwrap());
        zip.start_file("bat-modpack.json", zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(br#"{"schema":1,"minecraft":"1.21.1","loader":"fabric","files":[]}"#)
            .unwrap();
        zip.start_file("../escape", zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(b"bad").unwrap();
        zip.finish().unwrap();
        assert!(import_pack(root.path(), "p", &path).is_err());
        assert_eq!(
            fs::read_dir(root.path().join("minecraft_runtime/mods"))
                .unwrap()
                .count(),
            0
        );
        assert!(!root.path().join("escape").exists());
    }
    #[test]
    fn mod_update_preserves_old_bytes_and_unrelated_opt_outs() {
        let root = fixture();
        let dir = root.path().join("minecraft_runtime/mods");
        fs::write(dir.join("old.jar"), b"original mod").unwrap();
        lib::atomic_json(
            &root.path().join("mod-project-map.json"),
            &json!({"old.jar":"project","other.jar":"other"}),
        )
        .unwrap();
        lib::atomic_json(&root.path().join("dismissed-mods.json"), &json!(["other"])).unwrap();
        let staged = vec![staged(root.path(), "new.jar", b"verified new bytes")];
        commit_mods(root.path(), &dir, &staged, Some("old.jar")).unwrap();
        assert!(!dir.join("old.jar").exists());
        assert_eq!(
            fs::read(dir.join("new.jar")).unwrap(),
            b"verified new bytes"
        );
        assert_eq!(preferences(root.path()).unwrap(), vec!["other"]);
        let backup = fs::read_dir(
            root.path()
                .join("minecraft_runtime/.bat-mod-update-backups"),
        )
        .unwrap()
        .next()
        .unwrap()
        .unwrap()
        .path();
        assert_eq!(fs::read(backup.join("old.jar")).unwrap(), b"original mod");
        let map = lib::read_json(&root.path().join("mod-project-map.json"), Value::Null).unwrap();
        assert_eq!(map["new.jar"], "project");
        assert_eq!(map["other.jar"], "other");
        assert!(map.get("old.jar").is_none());
    }
    #[test]
    fn failed_mod_commit_restores_original_and_metadata() {
        let root = fixture();
        let dir = root.path().join("minecraft_runtime/mods");
        fs::write(dir.join("old.jar"), b"old").unwrap();
        let original = json!({"old.jar":"project"});
        lib::atomic_json(&root.path().join("mod-project-map.json"), &original).unwrap();
        let entries = vec![
            staged(root.path(), "one.jar", b"new"),
            staged(root.path(), "one.jar", b"collision"),
        ];
        assert!(commit_mods(root.path(), &dir, &entries, Some("old.jar")).is_err());
        assert_eq!(fs::read(dir.join("old.jar")).unwrap(), b"old");
        assert!(!dir.join("one.jar").exists());
        assert_eq!(
            lib::read_json(&root.path().join("mod-project-map.json"), Value::Null).unwrap(),
            original
        );
    }
    #[test]
    #[ignore = "Read-only official Modrinth project/version metadata acceptance"]
    fn live_mod_details() {
        let client = client().unwrap();
        let project = get_json(&client, &format!("{API}/project/AANobbMI"), &[]).unwrap();
        let versions = get_json(
            &client,
            &format!("{API}/project/AANobbMI/version"),
            &[
                ("game_versions", json!(["1.21.1"]).to_string()),
                ("loaders", json!(["fabric"]).to_string()),
            ],
        )
        .unwrap();
        let details = public_details(&project, &versions, "1.21.1", "fabric");
        assert_eq!(details["id"], "AANobbMI");
        assert!(!details["versions"].as_array().unwrap().is_empty());
        println!(
            "LIVE_MOD_DETAILS_PASS compatible_versions={}",
            details["versions"].as_array().unwrap().len()
        );
    }
    #[test]
    fn exact_file_cannot_fall_back_or_override_compatibility() {
        let v = json!({"project_id":"right","loaders":["fabric"],"game_versions":["1.21.1"],"files":[{"filename":"primary.jar","primary":true},{"filename":"other.jar"}]});
        assert_eq!(
            choose_file(&v, Some("other.jar")).unwrap()["filename"],
            "other.jar"
        );
        assert!(choose_file(&v, Some("missing.jar")).is_err());
        assert!(!compatible(&v, "1.20.1", "fabric"));
        assert!(search_index(Some("arbitrary")).is_err());
    }
    #[test]
    fn details_exclude_untrusted_gallery_and_incompatible_versions() {
        let project = json!({"id":"a","title":"Safe","gallery":[{"url":"https://evil.example/image.png"},{"url":"https://cdn.modrinth.com/image.png"}]});
        let versions = json!([{"id":"ok","project_id":"a","loaders":["fabric"],"game_versions":["1.21.1"],"files":[{"filename":"mod.jar"}]},{"id":"wrong","project_id":"a","loaders":["forge"],"game_versions":["1.21.1"]}]);
        let out = public_details(&project, &versions, "1.21.1", "fabric");
        assert_eq!(out["gallery"].as_array().unwrap().len(), 1);
        assert_eq!(out["versions"].as_array().unwrap().len(), 1);
        assert!(!out.to_string().contains("evil.example"));
    }
    #[test]
    fn remove_sodium_preserves_choice_and_backup() {
        let root = fixture();
        let dir = root.path().join("minecraft_runtime/mods");
        fs::write(dir.join("sodium-0.5.jar"), b"protected bytes").unwrap();
        change_at(root.path(), "sodium-0.5.jar", "remove").unwrap();
        assert!(!dir.join("sodium-0.5.jar").exists());
        assert!(preferences(root.path())
            .unwrap()
            .contains(&"AANobbMI".into()));
        let backup = fs::read_dir(root.path().join("minecraft_runtime/.bat-removed-mods"))
            .unwrap()
            .next()
            .unwrap()
            .unwrap()
            .path()
            .join("sodium-0.5.jar");
        assert_eq!(fs::read(backup).unwrap(), b"protected bytes");
    }
    #[test]
    fn disable_enable_preserves_bytes() {
        let root = fixture();
        let dir = root.path().join("minecraft_runtime/mods");
        fs::write(dir.join("sodium-0.5.jar"), b"bytes").unwrap();
        change_at(root.path(), "sodium-0.5.jar", "disable").unwrap();
        assert!(!list_at(root.path()).unwrap()[0].enabled);
        change_at(root.path(), "sodium-0.5.disabled", "enable").unwrap();
        assert_eq!(fs::read(dir.join("sodium-0.5.jar")).unwrap(), b"bytes");
        assert!(preferences(root.path()).unwrap().is_empty());
    }
    #[test]
    fn collisions_and_corrupt_choices_preserve_files() {
        let root = fixture();
        let dir = root.path().join("minecraft_runtime/mods");
        fs::write(dir.join("sodium-1.jar"), b"one").unwrap();
        fs::write(dir.join("sodium-1.disabled"), b"two").unwrap();
        assert!(change_at(root.path(), "sodium-1.jar", "disable").is_err());
        fs::write(root.path().join("dismissed-mods.json"), "corrupt").unwrap();
        assert!(change_at(root.path(), "sodium-1.jar", "remove").is_err());
        assert_eq!(fs::read(dir.join("sodium-1.jar")).unwrap(), b"one");
    }
    #[test]
    #[ignore = "Downloads a real Modrinth artifact into an isolated temporary fixture"]
    fn live_modrinth_install_roundtrip() {
        let root = fixture();
        lib::atomic_json(
            &root.path().join("profiles.config.json"),
            &json!({"profiles":[{"id":"test","version":"1.21.1","engine":"fabric"}]}),
        )
        .unwrap();
        let files = install_at(root.path(), "uXXizFIs", "test").unwrap();
        assert!(!files.is_empty());
        assert!(files
            .iter()
            .any(|m| m.project_id.as_deref() == Some("uXXizFIs")));
        let current = &files[0];
        let original = fs::read(
            root.path()
                .join("minecraft_runtime/mods")
                .join(&current.name),
        )
        .unwrap();
        let disabled = change_at(root.path(), &current.name, "disable").unwrap();
        let disabled = disabled
            .iter()
            .find(|m| m.project_id == current.project_id)
            .unwrap();
        assert!(!disabled.enabled);
        let enabled = change_at(root.path(), &disabled.name, "enable").unwrap();
        let enabled = enabled
            .iter()
            .find(|m| m.project_id == current.project_id)
            .unwrap();
        assert_eq!(
            fs::read(
                root.path()
                    .join("minecraft_runtime/mods")
                    .join(&enabled.name)
            )
            .unwrap(),
            original
        );
        change_at(root.path(), &enabled.name, "remove").unwrap();
        assert!(preferences(root.path())
            .unwrap()
            .contains(&"uXXizFIs".into()));
    }
    #[test]
    fn sodium_disable_also_disables_dependent_addons() {
        let root = fixture();
        let dir = root.path().join("minecraft_runtime/mods");
        for name in [
            "sodium-0.5.jar",
            "sodium-extra-0.5.jar",
            "reeses-sodium-options-1.jar",
        ] {
            fs::write(dir.join(name), b"original").unwrap();
        }
        let files = change_at(root.path(), "sodium-0.5.jar", "disable").unwrap();
        assert_eq!(files.len(), 3);
        assert!(files.iter().all(|m| !m.enabled));
        assert!(change_at(root.path(), "sodium-extra-0.5.disabled", "enable").is_err());
        assert_eq!(preferences(root.path()).unwrap().len(), 3);
    }
    #[test]
    fn exact_version_and_loader_required() {
        let v = json!({"game_versions":["1.21.1"],"loaders":["fabric"]});
        assert!(compatible(&v, "1.21.1", "fabric"));
        assert!(!compatible(&v, "1.21.11", "fabric"));
        assert!(!compatible(&v, "1.21.1", "forge"));
    }
}
