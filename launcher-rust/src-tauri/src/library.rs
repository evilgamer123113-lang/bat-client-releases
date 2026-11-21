use serde_json::Value;
use std::{
    fs,
    io::Write,
    path::{Path, PathBuf},
    sync::Mutex,
};
pub static WRITE_LOCK: Mutex<()> = Mutex::new(());
pub fn root() -> Result<PathBuf, String> {
    Ok(dirs::config_dir()
        .ok_or("Cannot locate application data")?
        .join("bat-client"))
}
pub fn read_json(path: &Path, fallback: Value) -> Result<Value, String> {
    match fs::symlink_metadata(path) {
        Ok(info) => {
            if !info.is_file() || info.file_type().is_symlink() || info.len() > 16 * 1024 * 1024 {
                return Err("Unsupported or oversized library metadata".into());
            }
            serde_json::from_slice(&fs::read(path).map_err(|_| "Cannot read library metadata")?)
                .map_err(|_| "Library metadata is invalid; no files changed".into())
        }
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(fallback),
        Err(_) => Err("Cannot inspect library metadata".into()),
    }
}
pub fn config(root: &Path) -> Result<Value, String> {
    read_json(&root.join("profiles.config.json"), Value::Null)
}
pub fn game_dir(root: &Path) -> Result<PathBuf, String> {
    let config = config(root)?;
    let path = config
        .pointer("/appSettings/gameDir")
        .and_then(Value::as_str)
        .filter(|p| !p.trim().is_empty())
        .map(PathBuf::from)
        .unwrap_or_else(|| root.join("minecraft_runtime"));
    if !path.is_absolute() {
        return Err("The configured game directory must be absolute".into());
    }
    Ok(path)
}
pub fn mods_dir(root: &Path) -> Result<PathBuf, String> {
    Ok(game_dir(root)?.join("mods"))
}
pub fn profile(root: &Path, id: &str) -> Result<(String, String), String> {
    let config = config(root)?;
    let profiles = config
        .get("profiles")
        .and_then(Value::as_array)
        .ok_or("No saved loadouts found")?;
    let p = profiles
        .iter()
        .find(|p| super::id(p.get("id")) == id)
        .ok_or("Select a saved loadout first")?;
    let version = p
        .get("version")
        .and_then(Value::as_str)
        .ok_or("Missing Minecraft version")?;
    let loader = p
        .get("engine")
        .and_then(Value::as_str)
        .unwrap_or("vanilla")
        .to_lowercase();
    if version.len() > 64
        || !version
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || ".-_".contains(c))
    {
        return Err("Invalid Minecraft version".into());
    }
    if !["fabric", "forge", "neoforge", "quilt"].contains(&loader.as_str()) {
        return Err(
            "This loadout does not support mods. Select Fabric, Forge, NeoForge or Quilt.".into(),
        );
    }
    Ok((version.into(), loader))
}
pub fn safe_filename(name: &str) -> bool {
    if name.is_empty()
        || name.len() > 230
        || name.starts_with('.')
        || name.ends_with('.')
        || name.ends_with(' ')
        || name
            .chars()
            .any(|c| c.is_control() || "/\\:<>\"|?*".contains(c))
    {
        return false;
    }
    let stem = name.split('.').next().unwrap_or("").to_ascii_uppercase();
    ![
        "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8",
        "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    ]
    .contains(&stem.as_str())
}
pub(crate) fn legacy_instance_dir(
    root: &Path,
    profile_id: &str,
) -> Result<Option<PathBuf>, String> {
    if !safe_filename(profile_id) {
        return Err("Invalid loadout folder name".into());
    }
    profile(root, profile_id)?;
    let base = root.join("instances");
    let path = base.join(profile_id);
    for folder in [&base, &path] {
        match fs::symlink_metadata(folder) {
            Ok(info) if info.is_dir() && !info.file_type().is_symlink() => {}
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
            _ => return Err("Cannot open a linked or invalid legacy instance folder".into()),
        }
    }
    let canonical = path
        .canonicalize()
        .map_err(|_| "Cannot inspect the legacy instance")?;
    let expected = root
        .canonicalize()
        .map_err(|_| "Cannot inspect the launcher folder")?
        .join("instances")
        .join(profile_id);
    if canonical != expected {
        return Err("The legacy instance redirects outside its expected folder".into());
    }
    Ok(Some(path))
}
pub fn regular_file(dir: &Path, name: &str) -> Result<PathBuf, String> {
    if !safe_filename(name) {
        return Err("Invalid mod filename".into());
    }
    let base = dir
        .canonicalize()
        .map_err(|_| "Cannot locate mods folder")?;
    let file = base.join(name);
    let info = fs::symlink_metadata(&file).map_err(|_| "Mod file not found")?;
    if !info.is_file()
        || info.file_type().is_symlink()
        || file
            .canonicalize()
            .map_err(|_| "Cannot inspect mod")?
            .parent()
            != Some(base.as_path())
    {
        return Err("Mod path is not a regular file inside the mods folder".into());
    }
    Ok(file)
}
pub fn atomic_json(path: &Path, value: &Value) -> Result<(), String> {
    let parent = path.parent().ok_or("Invalid metadata path")?;
    fs::create_dir_all(parent).map_err(|_| "Cannot create metadata directory")?;
    if path.exists()
        && fs::symlink_metadata(path)
            .map_err(|_| "Cannot inspect metadata")?
            .file_type()
            .is_symlink()
    {
        return Err("Metadata link rejected".into());
    }
    let mut temp = tempfile::NamedTempFile::new_in(parent).map_err(|_| "Cannot stage metadata")?;
    temp.write_all(&serde_json::to_vec(value).map_err(|_| "Cannot encode metadata")?)
        .map_err(|_| "Cannot write metadata")?;
    temp.as_file()
        .sync_all()
        .map_err(|_| "Cannot flush metadata")?;
    temp.persist(path)
        .map_err(|_| "Cannot save metadata atomically")?;
    Ok(())
}
pub fn ensure_game_closed() -> Result<(), String> {
    let system = sysinfo::System::new_all();
    let bat_dir = root()
        .ok()
        .and_then(|r| game_dir(&r).ok())
        .map(|p| p.to_string_lossy().to_lowercase().replace('\\', "/"));
    if system.processes().values().any(|p| {
        if matches!(
            p.status(),
            sysinfo::ProcessStatus::Zombie | sysinfo::ProcessStatus::Dead
        ) {
            return false;
        }
        let name = p.name().to_string_lossy().to_lowercase();
        if !(name == "java" || name == "java.exe" || name == "javaw.exe") {
            return false;
        }
        let args: Vec<String> = p
            .cmd()
            .iter()
            .map(|s| s.to_string_lossy().to_lowercase().replace('\\', "/"))
            .collect();
        let is_mc_client = args.iter().any(|s| {
            s.contains("net.minecraft.client")
                || s.contains("knotclient")
                || s.contains("modlauncher")
                || s.contains("launchwrapper")
                || s.contains("minecraftclient")
        });
        if !is_mc_client {
            return false;
        }
        if let Some(ref dir) = bat_dir {
            args.iter().any(|s| {
                s.contains(dir.as_str())
                    || s.contains("bat-client")
                    || s.contains("bat-cosmetics")
            })
        } else {
            true
        }
    }) {
        return Err("Save and fully close Bat Client's Minecraft window first".into());
    }
    Ok(())
}
// Full-object edits preserve opaque Electron account/token fields without exposing them to UI.
pub fn edit_config<F, T>(root: &Path, edit: F) -> Result<T, String>
where
    F: FnOnce(&mut Value) -> Result<T, String>,
{
    use fs2::FileExt;
    use sha2::{Digest, Sha256};
    fs::create_dir_all(root).map_err(|_| "Cannot create library directory")?;
    let lock_path = root.join(".rust-config.lock");
    if fs::symlink_metadata(&lock_path).is_ok_and(|m| m.file_type().is_symlink()) {
        return Err("Configuration lock link rejected".into());
    }
    let disk_lock = fs::OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .open(lock_path)
        .map_err(|_| "Cannot lock configuration")?;
    disk_lock
        .try_lock_exclusive()
        .map_err(|_| "Another Rust launcher is saving configuration; retry shortly")?;
    let path = root.join("profiles.config.json");
    let mut value = config(root)?;
    if value.is_null() {
        value = serde_json::json!({"profiles":[],"savedAccounts":[],"appSettings":{}});
    }
    if !value.is_object() {
        return Err("Unsupported launcher configuration; original preserved".into());
    }
    let original = if path.exists() {
        Some(fs::read(&path).map_err(|_| "Cannot read original configuration")?)
    } else {
        None
    };
    if let Some(bytes) = &original {
        value = serde_json::from_slice(bytes)
            .map_err(|_| "Invalid configuration; original preserved")?;
    }
    if !value.is_object() {
        return Err("Unsupported configuration; original preserved".into());
    }
    let result = edit(&mut value)?;
    if let Some(bytes) = &original {
        let backup = root.join("rust-migration-backups");
        fs::create_dir_all(&backup).map_err(|_| "Cannot create configuration backup")?;
        if fs::symlink_metadata(&backup)
            .map_err(|_| "Cannot inspect backup directory")?
            .file_type()
            .is_symlink()
        {
            return Err("Backup directory link rejected".into());
        }
        let file = backup.join(format!("profiles-{:x}.json", Sha256::digest(bytes)));
        if file.exists() {
            if fs::read(&file).map_err(|_| "Cannot verify prior backup")? != *bytes {
                return Err("Backup mismatch; original preserved".into());
            }
        } else {
            let mut out = fs::OpenOptions::new()
                .write(true)
                .create_new(true)
                .open(file)
                .map_err(|_| "Cannot back up configuration")?;
            out.write_all(bytes)
                .map_err(|_| "Cannot write configuration backup")?;
            out.sync_all()
                .map_err(|_| "Cannot flush configuration backup")?;
        }
        if fs::read(&path).map_err(|_| "Cannot recheck configuration")? != *bytes {
            return Err("Configuration changed in another launcher; reload and try again".into());
        }
    } else if path.exists() {
        return Err("Configuration was created by another launcher; reload first".into());
    }
    atomic_json(&path, &value)?;
    Ok(result)
}
pub fn ensure_legacy_launcher_closed() -> Result<(), String> {
    let system = sysinfo::System::new_all();
    if system.processes().values().any(|p| {
        if matches!(
            p.status(),
            sysinfo::ProcessStatus::Zombie | sysinfo::ProcessStatus::Dead
        ) {
            return false;
        }
        let name = p.name().to_string_lossy().to_lowercase();
        [
            "bat client.exe",
            "batclient.exe",
            "bat-client.exe",
            "bat client",
            "batclient",
            "bat-client",
        ]
        .contains(&name.as_str())
            || (name == "electron.exe"
                && p.cmd().iter().any(|arg| {
                    arg.to_string_lossy()
                        .to_lowercase()
                        .contains("raven-client")
                }))
    }) {
        return Err("Close the old Electron Bat Client before saving shared profiles, accounts or settings. No files changed.".into());
    }
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn legacy_instance_uses_only_existing_folders_for_saved_loadouts() {
        let root = tempfile::tempdir().unwrap();
        atomic_json(
            &root.path().join("profiles.config.json"),
            &serde_json::json!({"profiles":[{"id":"old","version":"1.21.1","engine":"fabric"}]}),
        )
        .unwrap();
        assert!(legacy_instance_dir(root.path(), "old").unwrap().is_none());
        let instance = root.path().join("instances/old");
        fs::create_dir_all(&instance).unwrap();
        assert_eq!(
            legacy_instance_dir(root.path(), "old").unwrap(),
            Some(instance)
        );
        assert!(legacy_instance_dir(root.path(), "../old").is_err());
        assert!(legacy_instance_dir(root.path(), "unknown").is_err());
    }
    #[test]
    fn filenames_cannot_escape() {
        for name in [
            "../x.jar",
            "..\\x.jar",
            "C:x.jar",
            "CON.jar",
            "a.jar:stream",
            "a.jar ",
            ".hidden.jar",
        ] {
            assert!(!safe_filename(name), "{name}");
        }
        assert!(safe_filename("fabric-api-0.1+1.21.jar"));
    }
    #[test]
    fn invalid_metadata_fails_closed() {
        let dir = tempfile::tempdir().unwrap();
        let p = dir.path().join("bad.json");
        fs::write(&p, "bad").unwrap();
        assert!(read_json(&p, Value::Null).is_err());
        assert_eq!(fs::read_to_string(p).unwrap(), "bad");
    }
    #[test]
    fn atomic_replacement() {
        let dir = tempfile::tempdir().unwrap();
        let p = dir.path().join("state.json");
        atomic_json(&p, &serde_json::json!(["old"])).unwrap();
        atomic_json(&p, &serde_json::json!(["new"])).unwrap();
        assert_eq!(
            read_json(&p, Value::Null).unwrap(),
            serde_json::json!(["new"])
        );
    }
}
