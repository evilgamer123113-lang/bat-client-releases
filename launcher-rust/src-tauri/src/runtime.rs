use crate::{game_launch, library as lib};
use serde_json::Value;
use std::sync::Mutex;
use tauri::{menu::MenuBuilder, tray::TrayIconBuilder, Manager};
use tauri_plugin_autostart::ManagerExt;
use tauri_plugin_notification::NotificationExt;
static PREFS: Mutex<Option<Value>> = Mutex::new(None);
pub fn preference(key: &str) -> bool {
    PREFS
        .lock()
        .ok()
        .and_then(|p| p.as_ref().and_then(|v| v[key].as_bool()))
        .unwrap_or(matches!(key, "notifications" | "discordRPC"))
}
pub fn apply(app: &tauri::AppHandle) -> Result<(), String> {
    let config = lib::config(&lib::root()?)?;
    let settings = config["appSettings"].clone();
    if settings["startWithWindows"].as_bool().unwrap_or(false) {
        if !app.autolaunch().is_enabled().unwrap_or(false) {
            app.autolaunch()
                .enable()
                .map_err(|_| "Settings saved; automatic startup could not be enabled")?;
        }
    } else if app.autolaunch().is_enabled().unwrap_or(false) {
        app.autolaunch()
            .disable()
            .map_err(|_| "Settings saved; automatic startup could not be disabled")?;
    }
    if let Some(tray) = app.tray_by_id("bat-launcher") {
        tray.set_visible(settings["closeToTray"].as_bool().unwrap_or(false))
            .map_err(|_| "Settings saved; tray visibility could not change")?;
    }
    *PREFS
        .lock()
        .map_err(|_| "Runtime preferences unavailable")? = Some(settings);
    Ok(())
}
pub fn setup(app: &tauri::AppHandle) -> Result<(), String> {
    let menu = MenuBuilder::new(app)
        .text("open", "Open Bat Client")
        .separator()
        .text("quit", "Quit Bat Client")
        .build()
        .map_err(|_| "Cannot create launcher tray menu")?;
    let icon = app
        .default_window_icon()
        .cloned()
        .ok_or("Launcher icon missing")?;
    TrayIconBuilder::with_id("bat-launcher")
        .icon(icon)
        .tooltip("Bat Client")
        .menu(&menu)
        .on_menu_event(|app, event| match event.id().as_ref() {
            "open" => {
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.show();
                    let _ = window.unminimize();
                    let _ = window.set_focus();
                }
            }
            "quit" => {
                if !game_launch::active() {
                    app.exit(0);
                } else if let Some(window) = app.get_webview_window("main") {
                    let _ = window.show();
                }
            }
            _ => {}
        })
        .build(app)
        .map_err(|_| "Cannot create launcher tray icon")?;
    apply(app)?;
    crate::discord_presence::start();
    Ok(())
}
pub fn notification(app: &tauri::AppHandle, title: &str, body: &str) {
    if preference("notifications") {
        let _ = app.notification().builder().title(title).body(body).show();
    }
}
