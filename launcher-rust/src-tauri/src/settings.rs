use crate::library as lib;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::path::{Path, PathBuf};

#[derive(Clone, Copy, Default, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
enum BatSpeed {
    Slow,
    #[default]
    Normal,
    Fast,
}

#[derive(Serialize, Deserialize)]
#[serde(default, deny_unknown_fields)]
struct FlyingBat {
    enabled: bool,
    react: bool,
    hang: bool,
    particles: bool,
    speed: BatSpeed,
}

impl Default for FlyingBat {
    fn default() -> Self {
        Self { enabled: true, react: true, hang: true, particles: false, speed: BatSpeed::Normal }
    }
}

pub(crate) fn flying_bat(root: &Path, payload: Value) -> Result<Value, String> {
    if payload.is_null() {
        let config = lib::config(root)?;
        let saved: FlyingBat = serde_json::from_value(config["appSettings"]["flyingBat"].clone())
            .unwrap_or_default();
        return Ok(json!(saved));
    }
    let preferences: FlyingBat = serde_json::from_value(payload).map_err(|_| "Invalid Flying Bat settings")?;
    let _guard = lib::WRITE_LOCK.lock().map_err(|_| "Library busy")?;
    lib::ensure_legacy_launcher_closed()?;
    lib::edit_config(root, |config| {
        if config["appSettings"].is_null() {
            config["appSettings"] = json!({});
        }
        let settings = config["appSettings"].as_object_mut().ok_or("Invalid launcher settings")?;
        settings.insert("flyingBat".into(), json!(preferences));
        Ok(json!(preferences))
    })
}
#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Settings {
    #[serde(default, skip_deserializing)]
    pub skin_token_configured: bool,
    #[serde(default, skip_serializing)]
    pub skin_token_update: Option<String>,
    #[serde(default, skip_serializing)]
    pub clear_skin_token: bool,
    pub default_ram: u32,
    pub java_path: String,
    pub extra_jvm_args: String,
    pub game_dir: String,
    pub download_threads: u32,
    pub window_width: u32,
    pub window_height: u32,
    pub fullscreen: bool,
    pub hide_on_launch: bool,
    pub keep_launcher_open: bool,
    pub auto_close_on_game_exit: bool,
    pub auto_check_updates: bool,
    pub show_console: bool,
    pub close_to_tray: bool,
    pub start_with_windows: bool,
    #[serde(rename = "discordRPC")]
    pub discord_rpc: bool,
    pub launch_sound: bool,
    pub notifications: bool,
    pub auto_optimize: bool,
    pub cull_leaves: bool,
    pub fast_math: bool,
    pub crash_reports: bool,
    pub publish_skins_to_bat_client: bool,
    pub jvm_preset: String,
    pub language: String,
    pub game_window_title: String,
}
fn number(value: &Value, fallback: u32) -> u32 {
    value
        .as_u64()
        .and_then(|v| v.try_into().ok())
        .or_else(|| value.as_str().and_then(|v| v.parse().ok()))
        .unwrap_or(fallback)
}
pub fn get_at(root: &Path) -> Result<Settings, String> {
    let c = lib::config(root)?;
    let settings = &c["appSettings"];
    Ok(Settings {
        skin_token_configured: settings["batClientSkinToken"]
            .as_str()
            .is_some_and(|v| !v.is_empty()),
        skin_token_update: None,
        clear_skin_token: false,
        default_ram: number(&settings["defaultRam"], 4),
        java_path: crate::label(settings.get("javaPath"), "", 4096),
        extra_jvm_args: crate::label(settings.get("extraJvmArgs"), "", 8192),
        game_dir: crate::label(settings.get("gameDir"), "", 4096),
        download_threads: number(&settings["downloadThreads"], 16),
        window_width: number(&settings["windowWidth"], 1150),
        window_height: number(&settings["windowHeight"], 680),
        fullscreen: settings["fullscreen"].as_bool().unwrap_or(false),
        hide_on_launch: settings["hideOnLaunch"].as_bool().unwrap_or(false),
        keep_launcher_open: settings["keepLauncherOpen"].as_bool().unwrap_or(false),
        auto_close_on_game_exit: settings["autoCloseOnGameExit"].as_bool().unwrap_or(false),
        auto_check_updates: settings["autoCheckUpdates"].as_bool().unwrap_or(true),
        show_console: settings["showConsole"].as_bool().unwrap_or(false),
        close_to_tray: settings["closeToTray"].as_bool().unwrap_or(false),
        start_with_windows: settings["startWithWindows"].as_bool().unwrap_or(false),
        discord_rpc: settings["discordRPC"].as_bool().unwrap_or(true),
        launch_sound: settings["launchSound"].as_bool().unwrap_or(false),
        notifications: settings["notifications"].as_bool().unwrap_or(true),
        auto_optimize: settings["autoOptimize"].as_bool().unwrap_or(true),
        cull_leaves: settings["cullLeaves"].as_bool().unwrap_or(true),
        fast_math: settings["fastMath"].as_bool().unwrap_or(true),
        crash_reports: settings["crashReports"].as_bool().unwrap_or(false),
        publish_skins_to_bat_client: settings["publishSkinsToBatClient"]
            .as_bool()
            .unwrap_or(true),
        jvm_preset: crate::label(settings.get("jvmPreset"), "balanced", 256),
        language: crate::label(settings.get("language"), "en", 256),
        game_window_title: crate::label(settings.get("gameWindowTitle"), "", 256),
    })
}
pub fn save_at(root: &Path, value: Settings) -> Result<(), String> {
    let token = value.skin_token_update.as_deref().unwrap_or("");
    if token.len() > 4096
        || token.chars().any(char::is_whitespace)
        || (value.clear_skin_token && !token.is_empty())
    {
        return Err("Enter a valid replacement token or choose clear, not both".into());
    }
    if !(1..=64).contains(&value.default_ram)
        || !(1..=32).contains(&value.download_threads)
        || !(640..=7680).contains(&value.window_width)
        || !(480..=4320).contains(&value.window_height)
    {
        return Err("Invalid memory, download thread count or window dimensions".into());
    }
    if value.java_path.len() > 4096
        || value.game_dir.len() > 4096
        || (!value.game_dir.is_empty() && !PathBuf::from(&value.game_dir).is_absolute())
    {
        return Err("Use an absolute game directory path".into());
    }
    if !["balanced", "low", "high", "custom"].contains(&value.jvm_preset.as_str())
        || !["en", "es", "fr", "de", "pt", "ru", "tr", "ar"].contains(&value.language.as_str())
        || value.game_window_title.len() > 256
        || value.game_window_title.chars().any(char::is_control)
    {
        return Err("Invalid preset, language or game window title".into());
    }
    crate::jvm::validate(&value.extra_jvm_args)?;
    lib::edit_config(root, |config| {
        if config.get("appSettings").is_none() {
            config["appSettings"] = json!({});
        }
        let settings = &mut config["appSettings"];
        if !settings.is_object() {
            return Err("Invalid saved settings".into());
        }
        if value.clear_skin_token {
            settings["batClientSkinToken"] = json!("");
        } else if !token.is_empty() {
            settings["batClientSkinToken"] = json!(token);
        }
        settings["defaultRam"] = json!(value.default_ram);
        settings["javaPath"] = json!(value.java_path);
        settings["extraJvmArgs"] = json!(value.extra_jvm_args);
        settings["gameDir"] = json!(value.game_dir);
        settings["downloadThreads"] = json!(value.download_threads);
        settings["windowWidth"] = json!(value.window_width);
        settings["windowHeight"] = json!(value.window_height);
        settings["fullscreen"] = json!(value.fullscreen);
        settings["hideOnLaunch"] = json!(value.hide_on_launch);
        settings["keepLauncherOpen"] = json!(value.keep_launcher_open);
        settings["autoCloseOnGameExit"] = json!(value.auto_close_on_game_exit);
        settings["autoCheckUpdates"] = json!(value.auto_check_updates);
        settings["showConsole"] = json!(value.show_console);
        settings["closeToTray"] = json!(value.close_to_tray);
        settings["startWithWindows"] = json!(value.start_with_windows);
        settings["discordRPC"] = json!(value.discord_rpc);
        settings["launchSound"] = json!(value.launch_sound);
        settings["notifications"] = json!(value.notifications);
        settings["autoOptimize"] = json!(value.auto_optimize);
        settings["cullLeaves"] = json!(value.cull_leaves);
        settings["fastMath"] = json!(value.fast_math);
        settings["crashReports"] = json!(value.crash_reports);
        settings["publishSkinsToBatClient"] = json!(value.publish_skins_to_bat_client);
        settings["jvmPreset"] = json!(value.jvm_preset);
        settings["language"] = json!(value.language);
        settings["gameWindowTitle"] = json!(value.game_window_title);

        Ok(())
    })
}
#[tauri::command]
pub async fn launcher_settings() -> Result<Settings, String> {
    tauri::async_runtime::spawn_blocking(|| get_at(&lib::root()?))
        .await
        .map_err(|_| "Cannot inspect settings")?
}
#[tauri::command]
pub async fn save_launcher_settings(
    app: tauri::AppHandle,
    settings: Settings,
) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library busy")?;
        lib::ensure_legacy_launcher_closed()?;
        lib::ensure_game_closed()?;
        save_at(&lib::root()?, settings)?;
        crate::runtime::apply(&app)
    })
    .await
    .map_err(|_| "Cannot save settings")?
}
#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    #[test]
    fn skin_token_is_write_only_and_blank_preserves_explicit_clear_removes() {
        let root = tempfile::tempdir().unwrap();
        fs::write(
            root.path().join("profiles.config.json"),
            json!({"appSettings":{"batClientSkinToken":"original-private"}}).to_string(),
        )
        .unwrap();
        let mut settings = get_at(root.path()).unwrap();
        assert!(settings.skin_token_configured);
        settings.skin_token_update = Some(String::new());
        save_at(root.path(), settings).unwrap();
        assert_eq!(
            lib::config(root.path()).unwrap()["appSettings"]["batClientSkinToken"],
            "original-private"
        );
        let mut settings = get_at(root.path()).unwrap();
        settings.skin_token_update = Some("replacement-private".into());
        let public = serde_json::to_string(&settings).unwrap();
        assert!(!public.contains("private") && !public.contains("skinTokenUpdate"));
        save_at(root.path(), settings).unwrap();
        assert_eq!(
            lib::config(root.path()).unwrap()["appSettings"]["batClientSkinToken"],
            "replacement-private"
        );
        let mut settings = get_at(root.path()).unwrap();
        settings.clear_skin_token = true;
        save_at(root.path(), settings).unwrap();
        assert!(!get_at(root.path()).unwrap().skin_token_configured);
    }
    #[test]
    fn legacy_preferences_roundtrip_without_acronym_or_secret_loss() {
        let root = tempfile::tempdir().unwrap();
        let original = json!({"appSettings":{"discordRPC":false,"language":"ar","jvmPreset":"custom","gameWindowTitle":"Survival","firstLaunch":false,"futurePreference":{"retain":true},"batClientSkinToken":"PRIVATE"}});
        fs::write(
            root.path().join("profiles.config.json"),
            original.to_string(),
        )
        .unwrap();
        let mut settings = get_at(root.path()).unwrap();
        assert!(!settings.discord_rpc);
        assert_eq!(settings.language, "ar");
        assert_eq!(settings.jvm_preset, "custom");
        assert_eq!(settings.game_window_title, "Survival");
        assert_eq!(settings.download_threads, 16);
        let public = serde_json::to_value(&settings).unwrap();
        assert_eq!(public["discordRPC"], false);
        assert!(public.get("discordRpc").is_none());
        assert!(public.get("batClientSkinToken").is_none());
        settings.discord_rpc = true;
        settings.start_with_windows = true;
        settings.publish_skins_to_bat_client = false;
        settings.hide_on_launch = true;
        settings.language = "de".into();
        save_at(root.path(), settings).unwrap();
        let read = get_at(root.path()).unwrap();
        assert!(read.discord_rpc && read.start_with_windows && read.hide_on_launch);
        assert!(!read.publish_skins_to_bat_client);
        assert_eq!(read.language, "de");
        let c = lib::config(root.path()).unwrap();
        assert_eq!(c["appSettings"]["batClientSkinToken"], "PRIVATE");
        assert_eq!(c["appSettings"]["futurePreference"], json!({"retain":true}));
        assert_eq!(c["appSettings"]["firstLaunch"], false);
    }
    #[test]
    fn invalid_preferences_leave_config_bytes_unchanged() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("profiles.config.json");
        fs::write(&path, "{\"appSettings\":{}}").unwrap();
        let before = fs::read(&path).unwrap();
        for field in ["language", "jvmPreset", "gameWindowTitle"] {
            let mut value = serde_json::to_value(get_at(root.path()).unwrap()).unwrap();
            value.as_object_mut().unwrap().remove("skinTokenConfigured");
            value[field] = json!(if field == "gameWindowTitle" {
                "bad\nwindow"
            } else {
                "invalid"
            });
            assert!(save_at(root.path(), serde_json::from_value(value).unwrap()).is_err());
            assert_eq!(fs::read(&path).unwrap(), before);
        }
        let mut value = serde_json::to_value(get_at(root.path()).unwrap()).unwrap();
        value.as_object_mut().unwrap().remove("skinTokenConfigured");
        value["batClientSkinToken"] = json!("injected");
        assert!(serde_json::from_value::<Settings>(value).is_err());
    }
    #[test]
    fn settings_exclude_secrets_and_preserve_unported_fields() {
        let root = tempfile::tempdir().unwrap();
        fs::write(root.path().join("profiles.config.json"),json!({"appSettings":{"batClientSkinToken":"PRIVATE","extraJvmArgs":"-Dkeep=1","discordRPC":false},"savedAccounts":[{"token":"PRIVATE"}]}).to_string()).unwrap();
        let mut settings = get_at(root.path()).unwrap();
        assert!(!serde_json::to_string(&settings)
            .unwrap()
            .contains("PRIVATE"));
        settings.default_ram = 6;
        save_at(root.path(), settings).unwrap();
        let c = lib::config(root.path()).unwrap();
        assert_eq!(c["appSettings"]["batClientSkinToken"], "PRIVATE");
        assert_eq!(c["appSettings"]["extraJvmArgs"], "-Dkeep=1");
        assert_eq!(c["appSettings"]["discordRPC"], false);
        assert_eq!(c["savedAccounts"][0]["token"], "PRIVATE");
    }
}
