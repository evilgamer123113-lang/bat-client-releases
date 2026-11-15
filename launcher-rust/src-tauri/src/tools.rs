use crate::{library as lib, profiles};
use serde_json::{json, Value};
use std::{
    fs,
    io::{Read, Write},
    net::{TcpStream, ToSocketAddrs},
    path::Path,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tauri_plugin_dialog::DialogExt;
use tauri_plugin_opener::OpenerExt;
pub fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}
pub fn record(root: &Path, profile_id: &str, pid: u32, server: Option<&str>) -> Result<(), String> {
    let config = lib::config(root)?;
    let p = config["profiles"]
        .as_array()
        .and_then(|a| a.iter().find(|p| crate::id(p.get("id")) == profile_id))
        .ok_or("Loadout missing")?;
    let path = root.join("launch-log.json");
    let mut entries = lib::read_json(&path, json!([]))?
        .as_array()
        .cloned()
        .ok_or("Invalid launch history")?;
    entries.insert(
        0,
        json!({
            "atMs": now(), "username": config["activeUsername"], "version": p["version"],
            "engine": p["engine"], "pid": pid, "server": server,
        }),
    );
    entries.truncate(60);
    lib::atomic_json(&path, &json!(entries))?;
    lib::edit_config(root, |c| {
        if let Some(p) = c["profiles"]
            .as_array_mut()
            .and_then(|a| a.iter_mut().find(|p| crate::id(p.get("id")) == profile_id))
        {
            p["lastPlayed"] = json!(now());
        }
        Ok(())
    })
}
fn public_profile(p: &Value) -> Value {
    json!({"name":p["name"],"version":p["version"],"engine":p["engine"],"ram":p["ram"].as_str().and_then(|s|s.parse::<u32>().ok()).or(p["ram"].as_u64().map(|v|v as u32)).unwrap_or(4),"ramOverride":p["ramOverride"].as_bool().unwrap_or(false),"favorite":p["favorite"].as_bool().unwrap_or(false),"extraJvmArgs":p["extraJvmArgs"].as_str().unwrap_or("")})
}
pub fn import_profiles(root: &Path, data: &Value) -> Result<usize, String> {
    let list = data["profiles"]
        .as_array()
        .ok_or("This file contains no loadouts")?;
    if list.is_empty() || list.len() > 300 {
        return Err("Loadout import must contain 1–300 entries".into());
    }
    let mut edits = vec![];
    for p in list {
        let mut value = public_profile(p);
        value["id"] = Value::Null;
        let edit: profiles::Edit =
            serde_json::from_value(value).map_err(|_| "Invalid imported loadout")?;
        profiles::valid(&edit)?;
        edits.push(edit);
    }
    lib::edit_config(root, |config| {
        let existing = config["profiles"].as_array().cloned().unwrap_or_default();
        if existing.len() + edits.len() > 300 {
            return Err("Too many loadouts after import".into());
        }
        let mut all = existing;
        for edit in edits {
            let mut p = serde_json::to_value(edit).map_err(|_| "Cannot encode loadout")?;
            p["id"] = json!(format!("p_rust_{}", uuid::Uuid::new_v4()));
            p["ram"] = json!(p["ram"].as_u64().unwrap_or(4).to_string());
            all.push(p);
        }
        config["profiles"] = json!(all);
        Ok(list.len())
    })
}
fn reorder(root: &Path, kind: &str, ids: &Value) -> Result<(), String> {
    let ids = ids
        .as_array()
        .filter(|a| a.len() <= 300)
        .ok_or("Invalid ordering")?;
    lib::edit_config(root, |config| {
        let key = if kind == "profiles" {
            "profiles"
        } else if kind == "accounts" {
            "savedAccounts"
        } else {
            return Err("Invalid order kind".into());
        };
        let list = config[key].as_array().ok_or("Invalid library")?;
        let mut out = vec![];
        let mut seen = std::collections::HashSet::new();
        for id in ids {
            let id = id.as_str().ok_or("Invalid ordering identity")?;
            if !seen.insert(id) {
                return Err("Duplicate ordering identity".into());
            }
            let p = list
                .iter()
                .find(|p| {
                    if kind == "profiles" {
                        crate::id(p.get("id")) == id
                    } else {
                        p["username"].as_str() == Some(id)
                    }
                })
                .ok_or("Ordering contains an unknown entry")?;
            out.push(p.clone());
        }
        for p in list {
            let id = if kind == "profiles" {
                crate::id(p.get("id"))
            } else {
                p["username"].as_str().unwrap_or("").to_owned()
            };
            if !seen.contains(id.as_str()) {
                out.push(p.clone());
            }
        }
        config[key] = json!(out);
        Ok(())
    })
}
fn tail(path: &Path) -> Result<String, String> {
    crate::launch_errors::tail(path)
}
fn newest(dir: &Path, extension: &str) -> Option<std::path::PathBuf> {
    fs::read_dir(dir)
        .ok()?
        .flatten()
        .filter(|e| {
            e.file_type().is_ok_and(|t| t.is_file())
                && e.path().extension().is_some_and(|v| v == extension)
        })
        .max_by_key(|e| {
            e.metadata()
                .and_then(|m| m.modified())
                .unwrap_or(UNIX_EPOCH)
        })
        .map(|e| e.path())
}
fn screenshot(root: &Path, name: &str, action: &str) -> Result<Value, String> {
    let dir = lib::game_dir(root)?.join("screenshots");
    let path = lib::regular_file(&dir, name)?;
    let extension = path
        .extension()
        .and_then(|s| s.to_str())
        .unwrap_or("")
        .to_lowercase();
    if !["png", "jpg", "jpeg"].contains(&extension.as_str()) {
        return Err("Unsupported screenshot type".into());
    }
    if action == "remove" {
        let backup = dir.join(".bat-removed-screenshots");
        fs::create_dir_all(&backup).map_err(|_| "Cannot preserve removed screenshot")?;
        fs::rename(
            path,
            backup.join(format!("{}-{name}", uuid::Uuid::new_v4())),
        )
        .map_err(|_| "Cannot remove screenshot")?;
        return Ok(json!({"removed":true}));
    }
    if fs::metadata(&path)
        .map_err(|_| "Cannot inspect screenshot")?
        .len()
        > 16 * 1024 * 1024
    {
        return Err("Screenshot is too large to preview".into());
    }
    let bytes = fs::read(path).map_err(|_| "Cannot read screenshot")?;
    let mime = if bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        "image/png"
    } else if bytes.starts_with(b"\xff\xd8\xff") {
        "image/jpeg"
    } else {
        return Err("Invalid screenshot image".into());
    };
    use base64::Engine;
    Ok(
        json!({"dataUrl":format!("data:{mime};base64,{}",base64::engine::general_purpose::STANDARD.encode(bytes))}),
    )
}
fn varint(mut n: u32) -> Vec<u8> {
    let mut v = vec![];
    loop {
        let mut byte = (n & 127) as u8;
        n >>= 7;
        if n != 0 {
            byte |= 128;
        }
        v.push(byte);
        if n == 0 {
            break;
        }
    }
    v
}
fn read_varint(stream: &mut impl Read) -> Result<u32, String> {
    let mut n = 0;
    for i in 0..5 {
        let mut b = [0];
        stream
            .read_exact(&mut b)
            .map_err(|_| "Server status read failed")?;
        n |= ((b[0] & 127) as u32) << (7 * i);
        if b[0] & 128 == 0 {
            return Ok(n);
        }
    }
    Err("Invalid server response length".into())
}
pub fn server_address(value: &str) -> Result<(String, u16), String> {
    let value = value.trim();
    if value.is_empty()
        || value.len() > 260
        || value
            .chars()
            .any(|c| c.is_whitespace() || c.is_control() || "/\\@?#".contains(c))
    {
        return Err("Enter a server hostname or hostname:port".into());
    }
    if value.starts_with('[') {
        let end = value.find(']').ok_or("Invalid IPv6 server")?;
        let host = &value[1..end];
        let port = if value.len() > end + 1 {
            value[end + 1..]
                .strip_prefix(':')
                .ok_or("Invalid server port")?
                .parse::<u16>()
                .map_err(|_| "Invalid server port")?
        } else {
            25565
        };
        if port == 0 {
            return Err("Invalid server port".into());
        }
        return Ok((host.into(), port));
    }
    let (host, port) = if let Some((host, port)) = value.rsplit_once(':') {
        (
            host,
            port.parse::<u16>().map_err(|_| "Invalid server port")?,
        )
    } else {
        (value, 25565)
    };
    if host.is_empty() || port == 0 {
        return Err("Invalid server address".into());
    }
    Ok((host.into(), port))
}
fn ping(value: &str) -> Result<Value, String> {
    let (host, port) = server_address(value)?;
    let start = std::time::Instant::now();
    let address = (host.as_str(), port)
        .to_socket_addrs()
        .map_err(|_| "Server DNS lookup failed")?
        .next()
        .ok_or("Server address not found")?;
    let mut socket = TcpStream::connect_timeout(&address, Duration::from_secs(4))
        .map_err(|_| "Server did not accept a connection")?;
    socket.set_read_timeout(Some(Duration::from_secs(4))).ok();
    socket.set_write_timeout(Some(Duration::from_secs(4))).ok();
    let mut handshake = vec![0];
    handshake.extend(varint(767));
    handshake.extend(varint(host.len() as u32));
    handshake.extend(host.as_bytes());
    handshake.extend(port.to_be_bytes());
    handshake.push(1);
    let mut frame = varint(handshake.len() as u32);
    frame.extend(handshake);
    frame.extend([1, 0]);
    socket
        .write_all(&frame)
        .map_err(|_| "Server status request failed")?;
    let length = read_varint(&mut socket)?;
    if length > 1024 * 1024 {
        return Err("Server response exceeds limit".into());
    }
    let mut packet = vec![0; length as usize];
    socket
        .read_exact(&mut packet)
        .map_err(|_| "Server status response interrupted")?;
    let mut cursor = std::io::Cursor::new(packet);
    if read_varint(&mut cursor)? != 0 {
        return Err("Unexpected server status packet".into());
    }
    let size = read_varint(&mut cursor)?;
    if size > length {
        return Err("Invalid server status text size".into());
    }
    let mut data = vec![0; size as usize];
    cursor
        .read_exact(&mut data)
        .map_err(|_| "Incomplete server status text")?;
    let status: Value = serde_json::from_slice(&data).map_err(|_| "Invalid server status JSON")?;
    Ok(
        json!({"latencyMs":start.elapsed().as_millis(),"version":status["version"]["name"],"online":status["players"]["online"],"max":status["players"]["max"],"description":status["description"]}),
    )
}
fn presets(root: &Path, kind: &str, action: &str, payload: &Value) -> Result<Value, String> {
    let filename = match kind {
        "launch" => "launch-presets.json",
        "mods" => "mod-presets.json",
        _ => return Err("Invalid preset type".into()),
    };
    let path = root.join(filename);
    let mut entries = lib::read_json(&path, json!([]))?
        .as_array()
        .cloned()
        .ok_or("Invalid preset library")?;
    if action == "list" {
        return Ok(json!(entries.iter().map(|p|json!({"id":p["id"],"name":p["name"],"profileId":p["profileId"],"modFilenames":p["modFilenames"]})).collect::<Vec<_>>()));
    }
    if action == "save" {
        let name = payload["name"]
            .as_str()
            .filter(|s| !s.trim().is_empty() && s.len() <= 80)
            .ok_or("Use a preset name of 1–80 characters")?;
        let editing = payload["id"]
            .as_str()
            .map(|id| {
                entries
                    .iter()
                    .position(|entry| entry["id"].as_str() == Some(id))
                    .ok_or("Preset not found")
            })
            .transpose()?;
        let mut entry = editing
            .map(|index| entries[index].clone())
            .unwrap_or_else(|| json!({"id":uuid::Uuid::new_v4().to_string(),"createdAt":now()}));
        entry["name"] = json!(name.trim());
        if kind == "launch" {
            let profile = payload["profileId"].as_str().ok_or("Select a loadout")?;
            let config = lib::config(root)?;
            if !config["profiles"]
                .as_array()
                .is_some_and(|a| a.iter().any(|p| crate::id(p.get("id")) == profile))
            {
                return Err("Loadout no longer exists".into());
            }
            entry["profileId"] = json!(profile);
        } else {
            entry["modFilenames"] = json!(crate::mods::list_at(root)?
                .into_iter()
                .filter(|m| m.enabled)
                .map(|m| m.name)
                .collect::<Vec<_>>());
        }
        if let Some(index) = editing {
            entries[index] = entry;
        } else if entries.len() >= 100 {
            return Err("Preset limit reached".into());
        } else {
            entries.push(entry);
        }
    } else {
        let id = payload["id"].as_str().ok_or("Select a preset")?;
        let index = entries
            .iter()
            .position(|p| p["id"].as_str() == Some(id))
            .ok_or("Preset not found")?;
        if action == "delete" {
            entries.remove(index);
        } else if action == "apply" {
            if kind == "launch" {
                crate::profiles::action_at(
                    root,
                    entries[index]["profileId"]
                        .as_str()
                        .ok_or("Preset loadout missing")?,
                    "select",
                )?;
            } else {
                lib::ensure_game_closed()?;
                let wanted = entries[index]["modFilenames"]
                    .as_array()
                    .ok_or("Invalid mod collection")?
                    .iter()
                    .map(|v| v.as_str().map(str::to_owned).ok_or("Invalid mod filename"))
                    .collect::<Result<Vec<_>, _>>()?;
                crate::mods::apply_preset(root, &wanted)?;
            }
            return Ok(json!({"applied":true}));
        } else {
            return Err("Invalid preset action".into());
        }
    }
    lib::atomic_json(&path, &json!(entries))?;
    Ok(json!({"saved":true}))
}
fn health(root: &Path) -> Result<Value, String> {
    let game = lib::game_dir(root)?;
    let config = lib::config(root)?;
    let profiles = config["profiles"].as_array().map(|a| a.len()).unwrap_or(0);
    let agents = ["bat-cosmetics.jar", "authlib-injector.jar"]
        .map(|name| json!({"name":name,"present":root.join(name).is_file()}));
    let disks = sysinfo::Disks::new_with_refreshed_list();
    let disk = disks
        .iter()
        .filter(|d| game.starts_with(d.mount_point()))
        .max_by_key(|d| d.mount_point().as_os_str().len());
    let free = disk.map(|d| d.available_space());
    Ok(
        json!({"gameDirectory":game,"directoryPresent":game.is_dir(),"profiles":profiles,"agents":agents,"freeBytes":free,"lowSpace":free.is_some_and(|n|n<5*1024*1024*1024),"gameActive":crate::game_launch::active()}),
    )
}
fn benchmark() -> Result<Value, String> {
    let status = crate::game_launch::game_status()?;
    let value = serde_json::to_value(status).map_err(|_| "Game status unavailable")?;
    let pid = value["pid"]
        .as_u64()
        .ok_or("Launch Minecraft from this window before sampling")? as u32;
    if !crate::game_launch::active() {
        return Err("No owned Minecraft process is running".into());
    }
    let pid = sysinfo::Pid::from_u32(pid);
    let mut system = sysinfo::System::new_all();
    std::thread::sleep(sysinfo::MINIMUM_CPU_UPDATE_INTERVAL.max(Duration::from_millis(500)));
    system.refresh_processes(sysinfo::ProcessesToUpdate::Some(&[pid]), true);
    let process = system
        .process(pid)
        .ok_or("Minecraft exited before sampling completed")?;
    Ok(
        json!({"pid":pid.as_u32(),"cpuPercent":process.cpu_usage(),"memoryBytes":process.memory(),"sampleMs":500,"fps":Value::Null,"note":"CPU percentage may exceed 100% across multiple cores. Frame timing is unavailable without a game-side probe."}),
    )
}

#[tauri::command]
pub async fn tools_action(
    app: tauri::AppHandle,
    action: String,
    payload: Option<Value>,
) -> Result<Value, String> {
    tauri::async_runtime::spawn_blocking(move||{
        let root=lib::root()?;let data=payload.unwrap_or(Value::Null);
        match action.as_str(){
            "flying_bat"=>crate::settings::flying_bat(&root,data),
            "discord_page"=>{crate::discord_presence::set_page(data["page"].as_str().ok_or("Missing launcher page")?)?;Ok(Value::Null)},
            "game_root"=>Ok(json!(lib::game_dir(&root)?.to_string_lossy())),
            "java_inventory"=>serde_json::to_value(crate::java_runtime::inventory_at(&root)?).map_err(|_|"Cannot report installed Java versions".into()),
            "legacy_instance"=>{
                let id=data["profileId"].as_str().ok_or("Select a loadout")?;
                let path=lib::legacy_instance_dir(&root,id)?;
                if data["open"]==true {
                    let path=path.as_ref().ok_or("This loadout has no legacy instance folder")?;
                    app.opener().open_path(path.to_string_lossy(),None::<&str>).map_err(|_|"Cannot open the legacy instance folder")?;
                }
                Ok(json!({"exists":path.is_some()}))
            },
            "system"=>{let mut system=sysinfo::System::new();system.refresh_memory();let total=system.total_memory()/1024/1024/1024;Ok(json!({"os":std::env::consts::OS,"arch":std::env::consts::ARCH,"memoryGB":total,"freeGB":system.available_memory()/1024/1024/1024,"threads":std::thread::available_parallelism().map(|v|v.get()).unwrap_or(1),"suggestedRam":if total>=32{12}else if total>=16{8}else if total>=8{6}else{2}}))},
            "health"=>health(&root),
            "benchmark"=>benchmark(),
            "history"=>lib::read_json(&root.join("launch-log.json"),json!([])),
            "open_folder"=>{if let Some(id)=data["profileId"].as_str(){lib::profile(&root,id)?;}let path=match data["kind"].as_str().unwrap_or("game"){"game"=>lib::game_dir(&root)?,"mods"=>lib::mods_dir(&root)?,"screenshots"=>lib::game_dir(&root)?.join("screenshots"),"logs"=>root.join("rust-game-logs"),_=>return Err("Unsupported folder".into())};fs::create_dir_all(&path).map_err(|_|"Cannot open folder")?;app.opener().open_path(path.to_string_lossy(),None::<&str>).map_err(|_|"Cannot open folder")?;Ok(Value::Null)},
            "pick_folder"=>Ok(json!(app.dialog().file().blocking_pick_folder().map(|p|p.into_path().map(|p|p.to_string_lossy().into_owned())).transpose().map_err(|_|"Invalid selected folder")?)),
            "pick_java"=>Ok(json!(app.dialog().file().blocking_pick_file().map(|p|p.into_path().map(|p|p.to_string_lossy().into_owned())).transpose().map_err(|_|"Invalid selected file")?)),
            "screenshots"=>{let dir=lib::game_dir(&root)?.join("screenshots");let mut files=vec![];if let Ok(entries)=fs::read_dir(&dir){for entry in entries.flatten().take(2000){if entry.file_type().is_ok_and(|t|t.is_file())&&entry.path().extension().is_some_and(|s|["png","jpg","jpeg"].contains(&s.to_string_lossy().to_lowercase().as_str())){files.push(json!({"name":entry.file_name().to_string_lossy(),"size":entry.metadata().map(|m|m.len()).unwrap_or(0)}));}}}files.sort_by(|a,b|b["name"].as_str().cmp(&a["name"].as_str()));Ok(json!(files))},
            "screenshot"=>screenshot(&root,data["name"].as_str().ok_or("Select a screenshot")?,data["operation"].as_str().unwrap_or("preview")),
            "log"|"crash"=>{let path=if action=="crash"{newest(&lib::game_dir(&root)?.join("crash-reports"),"txt")}else{newest(&root.join("rust-game-logs"),"log")};let text=path.map(|p|tail(&p)).transpose()?.unwrap_or_default();let lower=text.to_lowercase();let mut hints=vec![];for (pattern,hint) in [("outofmemoryerror","Java ran out of memory. Check RAM allocation and resource packs."),("unsupportedclassversionerror","A Java version is incompatible with a game/mod class."),("incompatible mods","The loader reported incompatible mods; use the Mods compatibility report."),("mixin apply failed","A mod mixin failed. Check exact Minecraft/loader versions and conflicting mods."),("could not reserve enough space","The selected Java heap exceeds available memory/address space.")]{if lower.contains(pattern){hints.push(hint);}}Ok(json!({"text":text,"hints":hints}))},
            "ping"=>ping(data["server"].as_str().ok_or("Enter a server address")?),
            "reorder"=>{let _lock=lib::WRITE_LOCK.lock().map_err(|_|"Library busy")?;lib::ensure_legacy_launcher_closed()?;reorder(&root,data["kind"].as_str().unwrap_or(""),&data["ids"])?;Ok(Value::Null)},
            "presets"=>{let _lock=lib::WRITE_LOCK.lock().map_err(|_|"Library busy")?;lib::ensure_legacy_launcher_closed()?;presets(&root,data["kind"].as_str().unwrap_or("launch"),data["operation"].as_str().unwrap_or("list"),&data)},
            "export_loadouts"=>{let Some(path)=app.dialog().file().set_file_name("BatClient-loadouts.json").add_filter("Loadouts",&["json"]).blocking_save_file() else{return Ok(Value::Null);};let path=path.into_path().map_err(|_|"Invalid export path")?;let config=lib::config(&root)?;let entries=config["profiles"].as_array().ok_or("No loadouts")?.iter().map(public_profile).collect::<Vec<_>>();lib::atomic_json(&path,&json!({"schema":1,"profiles":entries}))?;Ok(json!({"exported":entries.len()}))},
            "import_loadouts"=>{let Some(path)=app.dialog().file().add_filter("Loadouts",&["json"]).blocking_pick_file() else{return Ok(Value::Null);};let path=path.into_path().map_err(|_|"Invalid import path")?;let value=lib::read_json(&path,Value::Null)?;let _lock=lib::WRITE_LOCK.lock().map_err(|_|"Library busy")?;lib::ensure_legacy_launcher_closed()?;Ok(json!({"imported":import_profiles(&root,&value)?}))},
            "onboarding"=>{if data["complete"]!=true{return Ok(json!({"firstLaunch":lib::config(&root)?["appSettings"]["firstLaunch"].as_bool().unwrap_or(true)}));}let _lock=lib::WRITE_LOCK.lock().map_err(|_|"Library busy")?;lib::ensure_legacy_launcher_closed()?;lib::edit_config(&root,|c|{c["appSettings"]["firstLaunch"]=json!(false);Ok(())})?;Ok(json!({"complete":true}))},
            _=>Err("Unsupported launcher tool".into())
        }
    }).await.map_err(|_|"Launcher tool interrupted".to_owned())?
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn profiles_import_is_atomic_and_excludes_accounts() {
        let root = tempfile::tempdir().unwrap();
        lib::atomic_json(
            &root.path().join("profiles.config.json"),
            &json!({"savedAccounts":[{"username":"Private","token":"secret"}],"profiles":[]}),
        )
        .unwrap();
        let data = json!({"profiles":[{"name":"Valid","version":"1.21.1","engine":"fabric","ram":4},{"name":"Bad","version":"../escape","engine":"fabric","ram":4}]});
        assert!(import_profiles(root.path(), &data).is_err());
        assert!(lib::config(root.path()).unwrap()["profiles"]
            .as_array()
            .unwrap()
            .is_empty());
        assert!(!public_profile(&json!({"token":"secret"}))
            .to_string()
            .contains("secret"));
    }
    #[test]
    fn reordering_preserves_unknown_private_fields() {
        let root = tempfile::tempdir().unwrap();
        lib::atomic_json(
            &root.path().join("profiles.config.json"),
            &json!({"savedAccounts":[{"username":"Aaa","token":"kept"},{"username":"Bbb"}]}),
        )
        .unwrap();
        reorder(root.path(), "accounts", &json!(["Bbb", "Aaa"])).unwrap();
        let config = lib::config(root.path()).unwrap();
        assert_eq!(config["savedAccounts"][1]["token"], "kept");
    }
    #[test]
    fn server_input_and_lengths_are_bounded() {
        assert_eq!(server_address("example.org").unwrap().1, 25565);
        assert_eq!(server_address("[::1]:25566").unwrap().1, 25566);
        assert!(server_address("a/b").is_err());
        assert!(server_address("a:0").is_err());
        for n in [0, 127, 128, 16384, u32::MAX] {
            assert_eq!(
                read_varint(&mut std::io::Cursor::new(varint(n))).unwrap(),
                n
            );
        }
    }
}
