use crate::library as lib;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{
    path::Path,
    time::{SystemTime, UNIX_EPOCH},
};
#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Edit {
    pub id: Option<String>,
    pub name: String,
    pub version: String,
    pub engine: String,
    pub ram: u32,
    pub ram_override: bool,
    pub favorite: bool,
    pub extra_jvm_args: String,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Details {
    pub id: String,
    pub name: String,
    pub version: String,
    pub engine: String,
    pub ram: u32,
    pub ram_override: bool,
    pub favorite: bool,
    pub extra_jvm_args: String,
}
pub(crate) fn valid(edit: &Edit) -> Result<(), String> {
    if edit.name.trim().is_empty() || edit.name.chars().count() > 80 {
        return Err("Loadout name must contain 1–80 characters".into());
    }
    if edit.version.is_empty()
        || edit.version.len() > 64
        || !edit
            .version
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || ".-_".contains(c))
    {
        return Err("Invalid Minecraft version".into());
    }
    if !["vanilla", "fabric", "forge", "neoforge", "quilt"].contains(&edit.engine.as_str()) {
        return Err("Unsupported loader".into());
    }
    if !(1..=64).contains(&edit.ram) {
        return Err("RAM must be between 1 and 64 GB".into());
    }
    if edit.extra_jvm_args.len() > 8192 {
        return Err("Custom JVM arguments are too long".into());
    }
    crate::jvm::validate(&edit.extra_jvm_args)?;
    Ok(())
}
fn profiles(config: &mut Value) -> Result<&mut Vec<Value>, String> {
    if config.get("profiles").is_none() {
        config["profiles"] = json!([]);
    }
    config
        .get_mut("profiles")
        .and_then(Value::as_array_mut)
        .ok_or("Invalid saved loadouts".into())
}
pub fn save_at(root: &Path, edit: Edit) -> Result<String, String> {
    valid(&edit)?;
    lib::edit_config(root, |config| {
        let list = profiles(config)?;
        if list.len() >= 300 && edit.id.is_none() {
            return Err("Too many loadouts".into());
        }
        let id = edit.id.clone().unwrap_or_else(|| {
            format!(
                "p_rust_{}",
                SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_nanos()
            )
        });
        let index = if edit.id.is_some() {
            list.iter()
                .position(|p| crate::id(p.get("id")) == id)
                .ok_or("Loadout no longer exists")?
        } else {
            list.push(json!({"id":id}));
            list.len() - 1
        };
        let p = &mut list[index];
        p["name"] = json!(edit.name.trim());
        p["version"] = json!(edit.version);
        p["engine"] = json!(edit.engine);
        p["ram"] = json!(edit.ram.to_string());
        p["ramOverride"] = json!(edit.ram_override);
        p["favorite"] = json!(edit.favorite);
        p["extraJvmArgs"] = json!(edit.extra_jvm_args);
        if config.get("activeProfileId").is_none() || edit.id.is_none() {
            config["activeProfileId"] = json!(&id);
        }
        Ok(id)
    })
}
pub fn action_at(root: &Path, id: &str, action: &str) -> Result<(), String> {
    lib::edit_config(root, |config| {
        let list = profiles(config)?;
        let index = list
            .iter()
            .position(|p| crate::id(p.get("id")) == id)
            .ok_or("Loadout no longer exists")?;
        match action {
            "select" => {
                let original_id = list[index]["id"].clone();
                config["activeProfileId"] = original_id;
            }
            "favorite" => {
                let favorite = list[index]["favorite"].as_bool().unwrap_or(false);
                list[index]["favorite"] = json!(!favorite);
            }
            "delete" => {
                if list.len() <= 1 {
                    return Err("Keep at least one loadout".into());
                }
                list.remove(index);
                let first = list[0]["id"].clone();
                if crate::id(config.get("activeProfileId")) == id {
                    config["activeProfileId"] = first;
                }
            }
            "duplicate" => {
                if list.len() >= 300 {
                    return Err("Too many loadouts".into());
                }
                let mut copy = list[index].clone();
                let name = copy
                    .get("name")
                    .and_then(Value::as_str)
                    .unwrap_or("Loadout")
                    .to_owned();
                let mut number = 1;
                while list
                    .iter()
                    .any(|p| p["name"].as_str() == Some(&format!("{name} (Copy {number})")))
                {
                    number += 1;
                }
                copy["name"] = json!(format!("{name} (Copy {number})"));
                let new_id = format!(
                    "p_rust_{}",
                    SystemTime::now()
                        .duration_since(UNIX_EPOCH)
                        .unwrap_or_default()
                        .as_nanos()
                );
                copy["id"] = json!(&new_id);
                if let Some(object) = copy.as_object_mut() {
                    object.remove("gameDir");
                }
                copy["mods"] = json!([]);
                list.push(copy);
                config["activeProfileId"] = json!(new_id);
            }
            _ => return Err("Unsupported loadout action".into()),
        }
        Ok(())
    })
}
#[tauri::command]
pub async fn profile_details(profile_id: String) -> Result<Details, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let config = lib::config(&lib::root()?)?;
        let p = config["profiles"]
            .as_array()
            .and_then(|list| list.iter().find(|p| crate::id(p.get("id")) == profile_id))
            .ok_or("Loadout not found")?;
        Ok(Details {
            id: profile_id,
            name: crate::label(p.get("name"), "Loadout", 80),
            version: crate::label(p.get("version"), "1.21.1", 64),
            engine: crate::label(p.get("engine"), "vanilla", 24),
            ram: p
                .get("ram")
                .and_then(Value::as_str)
                .and_then(|v| v.parse().ok())
                .unwrap_or(4),
            ram_override: p["ramOverride"].as_bool().unwrap_or(false),
            favorite: p["favorite"].as_bool().unwrap_or(false),
            extra_jvm_args: crate::label(p.get("extraJvmArgs"), "", 8192),
        })
    })
    .await
    .map_err(|_| "Cannot inspect loadout")?
}
#[tauri::command]
pub async fn save_profile(edit: Edit) -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library busy")?;
        lib::ensure_legacy_launcher_closed()?;
        save_at(&lib::root()?, edit)
    })
    .await
    .map_err(|_| "Cannot save loadout")?
}
#[tauri::command]
pub async fn profile_action(profile_id: String, action: String) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library busy")?;
        lib::ensure_legacy_launcher_closed()?;
        action_at(&lib::root()?, &profile_id, &action)
    })
    .await
    .map_err(|_| "Cannot update loadout")?
}
#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    fn fixture() -> tempfile::TempDir {
        let root = tempfile::tempdir().unwrap();
        fs::write(root.path().join("profiles.config.json"),json!({"savedAccounts":[{"username":"User","token":"PRIVATE"}],"appSettings":{"secret":"UNCHANGED"},"profiles":[{"id":12,"name":"Old","version":"1.21.1","engine":"fabric","unportedField":{"keep":true}}],"activeProfileId":12}).to_string()).unwrap();
        root
    }
    #[test]
    fn editing_preserves_opaque_accounts_and_fields_with_backup() {
        let root = fixture();
        save_at(
            root.path(),
            Edit {
                id: Some("12".into()),
                name: "New".into(),
                version: "1.20.1".into(),
                engine: "forge".into(),
                ram: 6,
                ram_override: true,
                favorite: true,
                extra_jvm_args: "-Dexample=1".into(),
            },
        )
        .unwrap();
        let value = lib::config(root.path()).unwrap();
        assert_eq!(value["savedAccounts"][0]["token"], "PRIVATE");
        assert_eq!(value["profiles"][0]["unportedField"]["keep"], true);
        assert_eq!(value["appSettings"]["secret"], "UNCHANGED");
        assert!(fs::read_dir(root.path().join("rust-migration-backups"))
            .unwrap()
            .next()
            .is_some());
    }
    #[test]
    fn duplicates_preserve_args_and_deletes_only_metadata() {
        let root = fixture();
        action_at(root.path(), "12", "duplicate").unwrap();
        let config = lib::config(root.path()).unwrap();
        assert_eq!(config["profiles"].as_array().unwrap().len(), 2);
        let id = crate::id(config.get("activeProfileId"));
        action_at(root.path(), &id, "delete").unwrap();
        assert_eq!(lib::config(root.path()).unwrap()["activeProfileId"], 12);
        assert!(action_at(root.path(), "12", "delete").is_err());
    }
}
