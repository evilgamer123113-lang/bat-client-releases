#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod accounts;
mod agents;
mod background_prepare;
mod cape_animation;
mod changelog;
mod companion_mod;
mod discord_presence;
mod downloads;
mod forge_install;
mod game_files;
mod game_install;
mod game_launch;
mod game_repair;
mod java_install;
mod java_runtime;
mod jvm;
mod launch_errors;
mod library;
mod microsoft;
mod mod_compatibility;
mod mod_icon;
mod mods;
mod optimization;
mod optional_renderer;
mod profiles;
mod runtime;
mod settings;
mod skin_bridge;
mod tools;
mod updater;
mod versions;
mod wardrobe;
mod web_cache;
mod window_title;

use serde::Serialize;
use serde_json::Value;
use std::{
    fs,
    path::{Path, PathBuf},
};
use tauri::Manager;
use tauri_plugin_opener::OpenerExt;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Profile {
    id: String,
    name: String,
    version: String,
    engine: String,
    favorite: bool,
}
#[derive(Serialize)]
struct InstalledMod {
    name: String,
    enabled: bool,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Bootstrap {
    accounts: Vec<PublicAccount>,
    username: String,
    profiles: Vec<Profile>,
    active_profile_id: String,
    installed_versions: Vec<String>,
    mods: Vec<InstalledMod>,
    native: bool,
}
fn label(value: Option<&Value>, fallback: &str, limit: usize) -> String {
    value
        .and_then(Value::as_str)
        .unwrap_or(fallback)
        .chars()
        .take(limit)
        .collect()
}
fn id(value: Option<&Value>) -> String {
    match value {
        Some(Value::String(value)) => value.chars().take(80).collect(),
        Some(Value::Number(value)) => value.to_string(),
        _ => String::new(),
    }
}
fn public_profiles(config: &Value) -> Vec<Profile> {
    config
        .get("profiles")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .take(300)
        .map(|p| Profile {
            id: id(p.get("id")),
            name: label(p.get("name"), "My loadout", 80),
            version: label(p.get("version"), "Unknown", 64),
            engine: label(p.get("engine"), "vanilla", 24),
            favorite: p.get("favorite").and_then(Value::as_bool).unwrap_or(false),
        })
        .collect()
}
#[derive(Serialize)]
struct PublicAccount {
    username: String,
    kind: String,
}
fn public_accounts(config: &Value) -> Vec<PublicAccount> {
    config
        .get("savedAccounts")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .take(100)
        .filter_map(|account| {
            let username = label(account.get("username"), "", 32);
            if username.is_empty() {
                return None;
            }
            let kind = if account.get("type").and_then(Value::as_str) == Some("premium") {
                "microsoft"
            } else {
                "offline"
            };
            Some(PublicAccount {
                username,
                kind: kind.into(),
            })
        })
        .collect()
}
fn load_local(root: &Path) -> Result<Bootstrap, String> {
    // Read-only migration inspection. Never return the original object or account tokens.
    let file = root.join("profiles.config.json");
    let config: Value = if file.exists() {
        if fs::metadata(&file)
            .map_err(|_| "Cannot inspect existing profiles")?
            .len()
            > 16 * 1024 * 1024
        {
            return Err("Existing profile file is too large".into());
        }
        serde_json::from_slice(&fs::read(file).map_err(|_| "Cannot read existing profiles")?)
            .map_err(|_| "Existing profile file is invalid; it was left untouched")?
    } else {
        Value::Null
    };
    let game = config
        .pointer("/appSettings/gameDir")
        .and_then(Value::as_str)
        .filter(|s| !s.trim().is_empty())
        .map(PathBuf::from)
        .unwrap_or_else(|| root.join("minecraft_runtime"));
    let mut versions = Vec::new();
    if let Ok(entries) = fs::read_dir(game.join("versions")) {
        for entry in entries.flatten().take(1000) {
            let p = entry.path();
            if p.is_dir() {
                if let Some(name) = p.file_name().and_then(|n| n.to_str()) {
                    if p.join(format!("{name}.json")).is_file() {
                        versions.push(name.to_owned());
                    }
                }
            }
        }
    }
    versions.sort();
    let mut mods = Vec::new();
    if let Ok(entries) = fs::read_dir(game.join("mods")) {
        for entry in entries.flatten().take(2000) {
            if !entry.path().is_file() {
                continue;
            }
            let name = entry.file_name().to_string_lossy().into_owned();
            if name.ends_with(".jar") || name.ends_with(".disabled") {
                mods.push(InstalledMod {
                    enabled: name.ends_with(".jar"),
                    name,
                });
            }
        }
    }
    mods.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    Ok(Bootstrap {
        accounts: public_accounts(&config),
        username: label(config.get("activeUsername"), "Player", 32),
        profiles: public_profiles(&config),
        active_profile_id: id(config.get("activeProfileId")),
        installed_versions: versions,
        mods,
        native: true,
    })
}
#[tauri::command]
async fn bootstrap() -> Result<Bootstrap, String> {
    let root = dirs::config_dir()
        .ok_or("Cannot locate your application data directory")?
        .join("bat-client");
    tauri::async_runtime::spawn_blocking(move || load_local(&root))
        .await
        .map_err(|_| "Library inspection failed".to_owned())?
}
#[tauri::command]
fn window_action(window: tauri::WebviewWindow, action: &str) -> Result<(), String> {
    let result = match action {
        "minimize" => window.minimize(),
        "maximize" => {
            if window.is_maximized().map_err(|_| "Cannot inspect window")? {
                window.unmaximize()
            } else {
                window.maximize()
            }
        }
        "close" => window.close(),
        _ => return Err("Unknown window action".into()),
    };
    result.map_err(|_| "Window action failed".into())
}
#[tauri::command]
fn open_link(app: tauri::AppHandle, destination: &str) -> Result<(), String> {
    let url = match destination {
        "news" => "https://batclient.xyz/changelog.html",
        "support" => "https://batclient.xyz/contact.html",
        "discord" => "https://batclient.xyz/discord",
        _ => return Err("Unknown link".into()),
    };
    app.opener()
        .open_url(url, None::<&str>)
        .map_err(|_| "Cannot open this link".into())
}
fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_autostart::Builder::new().build())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .on_window_event(|window, event| {
            if window.label() == "main"
                && (game_launch::active() || runtime::preference("closeToTray"))
            {
                if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                    api.prevent_close();
                    let _ = window.hide();
                }
            }
        })
        .setup(|app| {
            if let Ok(root) = library::root() {
                if library::ensure_game_closed().is_ok() {
                    companion_mod::cleanup_stale_at(&root);
                }
            }
            if !std::env::args().any(|a| a.ends_with("-smoke")) {
                let _ = runtime::setup(app.handle());
            }
            if std::env::args().any(|arg| arg == "--updater-verify-smoke") {
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.hide();
                }
                let handle = app.handle().clone();
                std::thread::spawn(move || {
                    let result = updater::native_verify_smoke(&handle);
                    println!(
                        "NATIVE_UPDATER_VERIFY_SMOKE {}",
                        if result.is_ok() { "PASS" } else { "FAIL" }
                    );
                    if let Err(error) = &result {
                        eprintln!("{error}");
                    }
                    handle.exit(if result.is_ok() { 0 } else { 1 });
                });
            }
            if std::env::args().any(|arg| arg == "--auth-window-smoke") {
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.hide();
                }
                let handle = app.handle().clone();
                std::thread::spawn(move || {
                    let result = microsoft::native_window_smoke(&handle);
                    println!(
                        "NATIVE_AUTH_WINDOW_SMOKE {}",
                        if result.is_ok() { "PASS" } else { "FAIL" }
                    );
                    handle.exit(if result.is_ok() { 0 } else { 1 });
                });
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            bootstrap,
            tools::tools_action,
            updater::check_launcher_update,
            updater::launcher_update_status,
            updater::launcher_update_result,
            web_cache::clear_launcher_cache,
            updater::install_launcher_update,
            versions::minecraft_versions,
            settings::launcher_settings,
            settings::save_launcher_settings,
            microsoft::start_microsoft_login,
            microsoft::refresh_microsoft_account,
            accounts::account_action,
            accounts::account_skin,
            wardrobe::wardrobe_outfit,
            wardrobe::save_cosmetic,
            wardrobe::wardrobe_slot,
            game_launch::launch_installed_game,
            game_launch::game_status,
            background_prepare::prepare_selected_game,
            game_launch::launch_diagnostics,
            profiles::save_profile,
            profiles::profile_details,
            profiles::profile_action,
            companion_mod::prepare_companion_mod,
            java_runtime::detect_java,
            java_install::download_java,
            mods::installed_mods,
            mod_compatibility::check_mod_conflicts,
            mods::change_mod,
            mods::check_mod_updates,
            mods::update_mod,
            mods::modpack_action,
            mods::search_modrinth,
            mods::install_mod,
            mods::mod_details,
            mods::install_mod_version,
            changelog::fetch_changelog,
            window_action,
            open_link
        ])
        .run(tauri::generate_context!())
        .expect("Unable to start Bat Client preview");
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn profiles_do_not_expose_credentials_or_launch_arguments() {
        let source = serde_json::json!({"profiles":[{"id":123,"name":"My profile","version":"1.21.1","engine":"fabric","accessToken":"SECRET","javaArgs":"SECRET","account":{"token":"SECRET"}}]});
        let public = serde_json::to_string(&public_profiles(&source)).unwrap();
        assert!(!public.contains("SECRET"));
        assert!(public.contains("fabric"));
        assert!(public.contains("\"123\""));
    }
    #[test]
    fn empty_library_remains_empty() {
        assert!(public_profiles(&Value::Null).is_empty());
    }
    #[test]
    fn invalid_ids_do_not_serialize_nested_data() {
        assert!(id(Some(&serde_json::json!({"accessToken":"SECRET"}))).is_empty());
    }
}
