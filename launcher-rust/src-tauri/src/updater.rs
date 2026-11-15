//! Rust channel inside the shared update.json. Legacy Electron fields are never a fallback.
use crate::library as lib;
use serde::Serialize;
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    io::Read,
    sync::{
        atomic::{AtomicBool, Ordering},
        Mutex,
    },
    time::Duration,
};
use tauri_plugin_updater::UpdaterExt;

const ENDPOINT: &str = "https://batclient.xyz/update.json";
const LIMIT: u64 = 1024 * 1024 * 1024;
static BUSY: AtomicBool = AtomicBool::new(false);
static INSTALLING: AtomicBool = AtomicBool::new(false);
static STORE: Mutex<Option<Store>> = Mutex::new(None);
struct Store {
    status: Status,
    pending: Option<(tauri_plugin_updater::Update, String, u64)>,
}
#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Status {
    stage: String,
    message: String,
    version: Option<String>,
    progress: Option<f64>,
    target: String,
}
fn target_for(os: &str, arch: &str) -> Option<String> {
    let os = match os {
        "windows" => "windows",
        "macos" => "darwin",
        "linux" => "linux",
        _ => return None,
    };
    if !matches!(arch, "x86_64" | "aarch64") {
        return None;
    }
    Some(format!("{os}-{arch}"))
}
fn target() -> String {
    target_for(std::env::consts::OS, std::env::consts::ARCH).unwrap_or_else(|| "unsupported".into())
}
fn state(stage: &str, message: &str, version: Option<String>, progress: Option<f64>) -> Status {
    Status {
        stage: stage.into(),
        message: message.into(),
        version,
        progress,
        target: target(),
    }
}
fn set(s: Status) {
    if let Ok(mut store) = STORE.lock() {
        if let Some(store) = store.as_mut() {
            store.status = s;
        } else {
            *store = Some(Store {
                status: s,
                pending: None,
            });
        }
    }
}
pub fn active() -> bool {
    INSTALLING.load(Ordering::SeqCst)
}
struct Busy;
impl Drop for Busy {
    fn drop(&mut self) {
        INSTALLING.store(false, Ordering::SeqCst);
        BUSY.store(false, Ordering::SeqCst);
    }
}
fn acquire() -> Result<Busy, String> {
    BUSY.compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
        .map_err(|_| "An update operation is already in progress")?;
    Ok(Busy)
}
fn asset_url(value: &str) -> Result<reqwest::Url, String> {
    let u = reqwest::Url::parse(value).map_err(|_| "Invalid Rust update URL")?;
    if u.scheme() != "https"
        || u.port_or_known_default() != Some(443)
        || !u.username().is_empty()
        || u.password().is_some()
        || u.query().is_some()
        || u.fragment().is_some()
        || u.host_str() != Some("github.com")
        || !u
            .path()
            .starts_with("/evilgamer123113-lang/bat-client-releases/releases/download/")
    {
        return Err("Untrusted Rust update URL".into());
    }
    Ok(u)
}
fn channel(document: &Value, target: &str) -> Result<Option<(Value, String, u64)>, String> {
    let c = &document["rust"];
    if c.is_null() || c["enabled"] == false {
        return Ok(None);
    }
    if c["enabled"] != true || c["schema"] != 1 || c["product"] != "bat-client-rust" {
        return Err("Unsupported Rust update channel".into());
    }
    let entry = &c["platforms"][target];
    if !entry.is_object() {
        return Err(format!("No Rust update package is published for {target}."));
    }
    let url = entry["url"].as_str().ok_or("Rust update URL missing")?;
    asset_url(url)?;
    let signature = entry["signature"]
        .as_str()
        .filter(|s| !s.is_empty() && s.len() < 16384)
        .ok_or("Rust update signature missing")?;
    let hash = entry["sha256"]
        .as_str()
        .filter(|s| s.len() == 64 && s.chars().all(|c| c.is_ascii_hexdigit()))
        .ok_or("Rust update checksum missing")?;
    let size = entry["size"]
        .as_u64()
        .filter(|n| *n > 0 && *n <= LIMIT)
        .ok_or("Rust update size is invalid")?;
    let raw = json!({"version":c["version"],"notes":c["notes"],"platforms":{target:{"url":url,"signature":signature}}});
    Ok(Some((raw, hash.to_lowercase(), size)))
}
fn fetch() -> Result<Value, String> {
    let http = reqwest::blocking::Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .timeout(Duration::from_secs(25))
        .build()
        .map_err(|_| "Cannot initialize update check")?;
    let response = http
        .get(ENDPOINT)
        .header("Cache-Control", "no-cache")
        .send()
        .map_err(|_| "Cannot reach Bat Client update.json")?
        .error_for_status()
        .map_err(|_| "Update server returned an error")?;
    let mut bytes = vec![];
    response
        .take(1048577)
        .read_to_end(&mut bytes)
        .map_err(|_| "Update check interrupted")?;
    if bytes.len() > 1048576 {
        return Err("Update manifest exceeds its limit".into());
    }
    serde_json::from_slice(&bytes).map_err(|_| "Bat Client update.json is invalid".into())
}

// Explicit native verification harness: reads a signed local package, never installs it.
pub fn native_verify_smoke(app: &tauri::AppHandle) -> Result<(), String> {
    let path =
        std::env::var("BAT_UPDATER_SMOKE_PACKAGE").map_err(|_| "Smoke package path missing")?;
    let meta = std::fs::metadata(&path).map_err(|_| "Smoke package missing")?;
    if !meta.is_file() || meta.len() > LIMIT {
        return Err("Invalid smoke package".into());
    }
    let mut bytes = std::fs::read(&path).map_err(|_| "Cannot read smoke package")?;
    let signature =
        std::fs::read_to_string(format!("{path}.sig")).map_err(|_| "Smoke signature missing")?;
    let version = app.package_info().version.to_string();
    let raw = json!({"version":version,"platforms":{target():{"url":"https://github.com/evilgamer123113-lang/bat-client-releases/releases/download/test/package.exe","signature":signature.trim()}}});
    let updater = app
        .updater_builder()
        .pubkey(include_str!("../updater.pub").trim())
        .target(target())
        .version_comparator(|_, _| true)
        .build()
        .map_err(|_| "Smoke updater init failed")?;
    let update = updater
        .restore_update(raw.clone())
        .map_err(|_| "Smoke metadata rejected")?
        .ok_or("Smoke update missing")?;
    update
        .verify(&bytes)
        .map_err(|_| "Authentic package signature was rejected")?;
    if bytes.is_empty() {
        return Err("Empty smoke artifact".into());
    }
    bytes[0] ^= 1;
    if update.verify(&bytes).is_ok() {
        return Err("Tampered package was accepted".into());
    }
    bytes[0] ^= 1;
    let mut wrong = raw;
    wrong["version"] = json!("999.0.0");
    let wrong = updater
        .restore_update(wrong)
        .map_err(|_| "Version smoke metadata invalid")?
        .ok_or("Version smoke update missing")?;
    if wrong.verify(&bytes).is_ok() {
        return Err("Version substitution was accepted".into());
    }
    Ok(())
}
#[tauri::command]
pub fn launcher_update_status() -> Result<Status, String> {
    Ok(STORE
        .lock()
        .map_err(|_| "Updater busy")?
        .as_ref()
        .map(|s| s.status.clone())
        .unwrap_or_else(|| state("idle", "Updates use batclient.xyz/update.json.", None, None)))
}
#[tauri::command]
pub async fn check_launcher_update(app: tauri::AppHandle) -> Result<Status, String> {
    let _busy = acquire()?;
    set(state(
        "checking",
        "Checking Bat Client updates…",
        None,
        None,
    ));
    let result: Result<(Status, Option<(tauri_plugin_updater::Update, String, u64)>), String> =
        async {
            let document = tauri::async_runtime::spawn_blocking(fetch)
                .await
                .map_err(|_| "Update check interrupted")??;
            let Some((raw, hash, size)) = channel(&document, &target())? else {
                return Ok((
                    state(
                        "unpublished",
                        "No Rust launcher update has been published yet.",
                        None,
                        None,
                    ),
                    None,
                ));
            };
            let updater = app
                .updater_builder()
                .pubkey(include_str!("../updater.pub").trim())
                .target(target())
                .timeout(Duration::from_secs(180))
                .build()
                .map_err(|_| "Cannot initialize signed updater")?;
            match updater
                .restore_update(raw)
                .map_err(|_| "Rust update metadata is invalid")?
            {
                Some(update) => Ok((
                    state(
                        "available",
                        &format!("Rust launcher {} is available.", update.version),
                        Some(update.version.clone()),
                        None,
                    ),
                    Some((update, hash, size)),
                )),
                None => Ok((
                    state("current", "Your Rust launcher is up to date.", None, None),
                    None,
                )),
            }
        }
        .await;
    match result {
        Ok((status, pending)) => {
            *STORE.lock().map_err(|_| "Updater busy")? = Some(Store {
                status: status.clone(),
                pending,
            });
            Ok(status)
        }
        Err(error) => {
            let status = state("failed", &error, None, None);
            *STORE.lock().map_err(|_| "Updater busy")? = Some(Store {
                status,
                pending: None,
            });
            Err(error)
        }
    }
}
fn download(
    update: &tauri_plugin_updater::Update,
    expected: &str,
    size: u64,
) -> Result<Vec<u8>, String> {
    asset_url(update.download_url.as_str())?;
    let http = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(300))
        .redirect(reqwest::redirect::Policy::custom(|attempt| {
            let u = attempt.url();
            if attempt.previous().len() > 5
                || u.scheme() != "https"
                || !matches!(
                    u.host_str(),
                    Some(
                        "github.com"
                            | "release-assets.githubusercontent.com"
                            | "objects.githubusercontent.com"
                    )
                )
            {
                attempt.stop()
            } else {
                attempt.follow()
            }
        }))
        .build()
        .map_err(|_| "Cannot initialize update download")?;
    let mut response = http
        .get(update.download_url.clone())
        .send()
        .map_err(|_| "Update download failed")?
        .error_for_status()
        .map_err(|_| "Update package is not available")?;
    if response.content_length().is_some_and(|n| n != size) {
        return Err("Update package size does not match the manifest".into());
    }
    let mut data = Vec::with_capacity(size.min(16 * 1024 * 1024) as usize);
    let mut buf = [0; 65536];
    let mut digest = Sha256::new();
    loop {
        let n = response
            .read(&mut buf)
            .map_err(|_| "Update download interrupted; installed launcher unchanged")?;
        if n == 0 {
            break;
        }
        if data.len() as u64 + n as u64 > size {
            return Err("Update package exceeded its expected size".into());
        }
        data.extend_from_slice(&buf[..n]);
        digest.update(&buf[..n]);
        set(state(
            "downloading",
            "Downloading the signed launcher update…",
            Some(update.version.clone()),
            Some(data.len() as f64 / size as f64),
        ));
    }
    set(state(
        "verifying",
        "Verifying downloaded update…",
        Some(update.version.clone()),
        None,
    ));
    if data.len() as u64 != size || format!("{:x}", digest.finalize()) != expected {
        return Err("Update checksum mismatch; installed launcher unchanged".into());
    }
    update.verify(&data).map_err(|_| {
        "Update signature or signed version is invalid; installed launcher unchanged"
    })?;
    Ok(data)
}
fn update_result(receipt: &Value, running_version: &str) -> Option<Value> {
    let previous = receipt["from"].as_str()?;
    let target = receipt["to"].as_str()?;
    let valid_version = |version: &str| {
        !version.is_empty()
            && version.len() <= 80
            && version
                .bytes()
                .all(|c| c.is_ascii_alphanumeric() || b".-+".contains(&c))
    };
    if !valid_version(previous) || !valid_version(target) || previous == target {
        return None;
    }
    let (stage, message) = if running_version == target {
        ("applied", format!("Bat Client updated to {target}."))
    } else if running_version != previous {
        return None;
    } else if receipt["stage"] == "failed" {
        (
            "failed",
            format!("Update to {target} failed. You can check for updates to retry."),
        )
    } else if receipt["stage"] == "installing" {
        (
            "incomplete",
            format!("Update to {target} did not finish. You are running {previous}."),
        )
    } else {
        return None;
    };
    Some(json!({"stage":stage,"message":message,"version":target}))
}

#[tauri::command]
pub async fn launcher_update_result() -> Result<Option<Value>, String> {
    tauri::async_runtime::spawn_blocking(|| {
        let receipt = lib::read_json(&lib::root()?.join("rust-update-receipt.json"), Value::Null)?;
        Ok(update_result(&receipt, env!("CARGO_PKG_VERSION")))
    })
    .await
    .map_err(|_| "Cannot read the last launcher update result".to_owned())?
}

#[tauri::command]
pub async fn install_launcher_update(_app: tauri::AppHandle) -> Result<(), String> {
    let busy = acquire()?;
    INSTALLING.store(true, Ordering::SeqCst);
    let pending = STORE
        .lock()
        .map_err(|_| "Updater busy")?
        .as_ref()
        .and_then(|s| s.pending.clone())
        .ok_or("Check for an available Rust update first")?;
    tauri::async_runtime::spawn_blocking(move || {
        let _busy = busy;
        let result: Result<(), String> = (|| {
            crate::background_prepare::yield_to_foreground()?;
            let root = lib::root()?;
            lib::ensure_legacy_launcher_closed()?;
            lib::ensure_game_closed()?;
            let _game = crate::game_launch::lock_game(&root)?;
            let (update, hash, size) = pending;
            let bytes = download(&update, &hash, size)?;
            let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library busy")?;
            lib::ensure_game_closed()?;
            set(state(
                "installing",
                "Installing update and restarting the launcher…",
                Some(update.version.clone()),
                None,
            ));
            let receipt_path = root.join("rust-update-receipt.json");
            let mut receipt = json!({"from":env!("CARGO_PKG_VERSION"),"to":update.version,"stage":"installing","atMs":crate::tools::now()});
            lib::atomic_json(&receipt_path, &receipt)?;
            if update.install(bytes).is_err() {
                receipt["stage"] = json!("failed");
                let _ = lib::atomic_json(&receipt_path, &receipt);
                return Err("Could not install the signed update".into());
            }
            #[cfg(not(windows))]
            _app.restart();
            Ok(())
        })();
        if let Err(error) = &result {
            set(state("failed", error, None, None));
        }
        result
    })
    .await
    .map_err(|_| "Update operation interrupted".to_owned())?
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn update_receipt_checks_the_version_that_actually_started() {
        let mut receipt = json!({"from":"0.1.0","to":"0.2.0","stage":"installing"});
        assert_eq!(
            update_result(&receipt, "0.2.0").unwrap()["stage"],
            "applied"
        );
        assert_eq!(
            update_result(&receipt, "0.1.0").unwrap()["stage"],
            "incomplete"
        );
        receipt["stage"] = json!("failed");
        assert_eq!(update_result(&receipt, "0.1.0").unwrap()["stage"], "failed");
        assert!(update_result(&receipt, "0.3.0").is_none());
    }
    fn fixture() -> Value {
        json!({"version":"99.0.0","download":"https://wrong.test/electron.zip","rust":{"schema":1,"enabled":true,"product":"bat-client-rust","version":"0.2.0","notes":"Fixture","platforms":{"windows-x86_64":{"url":"https://github.com/evilgamer123113-lang/bat-client-releases/releases/download/rust-v0.2.0/BatClientNext-setup.exe","signature":"fixture-signature","sha256":"a".repeat(64),"size":123}}}})
    }
    #[test]
    fn legacy_electron_never_selected() {
        assert!(channel(
            &json!({"version":"99.0.0","platforms":{"win32":{}}}),
            "windows-x86_64"
        )
        .unwrap()
        .is_none());
        let raw = channel(&fixture(), "windows-x86_64").unwrap().unwrap().0;
        assert_eq!(raw["version"], "0.2.0");
    }
    #[test]
    fn platforms_are_exact_and_missing_never_falls_back() {
        assert_eq!(target_for("macos", "aarch64").unwrap(), "darwin-aarch64");
        assert_eq!(target_for("linux", "x86_64").unwrap(), "linux-x86_64");
        assert!(channel(&fixture(), "darwin-x86_64").is_err());
    }
    #[test]
    fn unsigned_or_external_package_rejected() {
        for key in ["signature", "sha256", "size"] {
            let mut doc = fixture();
            doc["rust"]["platforms"]["windows-x86_64"]
                .as_object_mut()
                .unwrap()
                .remove(key);
            assert!(channel(&doc, "windows-x86_64").is_err());
        }
        assert!(asset_url("https://github.com/other/repo/releases/download/v1/a.exe").is_err());
    }
    #[test]
    fn disabled_channel_never_replaces_existing() {
        let mut doc = fixture();
        doc["rust"]["enabled"] = json!(false);
        assert!(channel(&doc, "windows-x86_64").unwrap().is_none());
    }
    #[test]
    #[ignore = "Read-only live Bat Client update.json check"]
    fn live_shared_manifest() {
        let doc = fetch().unwrap();
        let _ = channel(&doc, &target()).unwrap();
    }
}
