#[tauri::command]
pub async fn clear_launcher_cache(app: tauri::AppHandle) -> Result<(), String> {
    #[cfg(windows)]
    {
        tauri::async_runtime::spawn_blocking(move || clear_windows_cache(app))
            .await
            .map_err(|_| "Launcher cache cleanup was interrupted".to_owned())?
    }
    #[cfg(not(windows))]
    {
        let _ = app;
        Err("Launcher cache cleanup is currently available on Windows".into())
    }
}

#[cfg(windows)]
fn clear_windows_cache(app: tauri::AppHandle) -> Result<(), String> {
    use tauri::Manager;
    use webview2_com::{
        ClearBrowsingDataCompletedHandler,
        Microsoft::Web::WebView2::Win32::{
            ICoreWebView2Profile2, ICoreWebView2_13, COREWEBVIEW2_BROWSING_DATA_KINDS_DISK_CACHE,
        },
    };
    use windows_core::Interface;

    let window = app
        .get_webview_window("main")
        .ok_or("Launcher window is unavailable")?;
    let (send, receive) = std::sync::mpsc::channel();
    window
        .with_webview(move |webview| {
            let completed = send.clone();
            let callback = ClearBrowsingDataCompletedHandler::create(Box::new(move |result| {
                let _ = completed
                    .send(result.map_err(|_| "WebView2 could not clear the launcher cache"));
                Ok(())
            }));
            // Disk cache only. WebView storage and sign-in cookies are separate data kinds.
            let result = unsafe {
                webview
                    .controller()
                    .CoreWebView2()
                    .and_then(|core| core.cast::<ICoreWebView2_13>())
                    .and_then(|core| core.Profile())
                    .and_then(|profile| profile.cast::<ICoreWebView2Profile2>())
                    .and_then(|profile| {
                        profile.ClearBrowsingData(
                            COREWEBVIEW2_BROWSING_DATA_KINDS_DISK_CACHE,
                            &callback,
                        )
                    })
            };
            if result.is_err() {
                let _ = send.send(Err("WebView2 cache controls are unavailable"));
            }
        })
        .map_err(|_| "Cannot access the launcher web cache")?;
    receive
        .recv_timeout(std::time::Duration::from_secs(15))
        .map_err(|_| "Launcher cache cleanup did not respond")?
        .map_err(str::to_owned)
}
