// Tokens never implement Serialize for IPC or Debug; no raw HTTP error bodies are returned.
use crate::library as lib;
use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    io::Read,
    path::Path,
    sync::{
        atomic::{AtomicBool, Ordering},
        mpsc,
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tauri::{WebviewUrl, WebviewWindowBuilder, WindowEvent};
const CLIENT_ID: &str = "00000000402b5328";
const REDIRECT: &str = "https://login.live.com/oauth20_desktop.srf";
const SERVICE: &str = "xyz.batclient.launcher.next.auth";
static SIGNING_IN: AtomicBool = AtomicBool::new(false);
struct LoginGuard;
impl Drop for LoginGuard {
    fn drop(&mut self) {
        SIGNING_IN.store(false, Ordering::SeqCst);
    }
}
#[derive(Serialize, Deserialize)]
pub struct Tokens {
    pub refresh_token: String,
    pub access_token: String,
    pub expires_at: u64,
    pub username: String,
    pub uuid: String,
}
#[derive(Serialize)]
pub struct PublicLogin {
    pub username: String,
    pub uuid: String,
}
fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}
fn entry(id: &str, suffix: &str) -> Result<keyring::Entry, String> {
    if id.len() > 80 || !id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-') {
        return Err("Invalid credential reference".into());
    }
    keyring::Entry::new(SERVICE, &format!("{id}:{suffix}"))
        .map_err(|_| "Cannot access the OS credential store".into())
}
pub(crate) fn remove_credentials(id: &str) -> Result<(), String> {
    // Own credential namespace only; preserve unrelated/legacy stores and cosmetic files.
    for suffix in (0..32)
        .map(|i| i.to_string())
        .chain(std::iter::once("parts".to_owned()))
    {
        match entry(id,&suffix)?.delete_credential(){Ok(())|Err(keyring::Error::NoEntry)=>{},Err(_)=>return Err("Account removed, but its OS credentials could not be fully cleared. Sign in again to replace the saved session.".into())}
    }
    Ok(())
}
fn delete_tokens(id: &str) {
    for i in 0..32 {
        if let Ok(e) = entry(id, &format!("{i}")) {
            let _ = e.delete_credential();
        }
    }
    if let Ok(e) = entry(id, "parts") {
        let _ = e.delete_credential();
    }
}
fn save_tokens(id: &str, tokens: &Tokens) -> Result<(), String> {
    if tokens.refresh_token.is_empty() || tokens.access_token.is_empty() {
        return Err("Cannot save empty sign-in tokens".into());
    }
    let bytes = serde_json::to_vec(tokens).map_err(|_| "Cannot encode credentials")?;
    let parts = bytes.chunks(2000).collect::<Vec<_>>();
    if parts.len() > 32 {
        return Err("Credentials exceed secure storage limit".into());
    }
    for (i, chunk) in parts.iter().enumerate() {
        if entry(id, &i.to_string())?.set_secret(chunk).is_err() {
            delete_tokens(id);
            return Err(
                "OS credential store refused sign-in tokens; no plaintext fallback was used".into(),
            );
        }
    }
    if entry(id, "parts")?
        .set_password(&parts.len().to_string())
        .is_err()
    {
        delete_tokens(id);
        return Err("Cannot finalize secure credentials".into());
    }
    Ok(())
}
pub fn read_tokens(id: &str) -> Result<Tokens, String> {
    let count = entry(id, "parts")?
        .get_password()
        .map_err(|_| "Sign in to this Microsoft account in the Rust launcher")?
        .parse::<usize>()
        .map_err(|_| "Invalid secure credential metadata")?;
    if !(1..=32).contains(&count) {
        return Err("Invalid secure credential metadata".into());
    }
    let mut bytes = Vec::new();
    for i in 0..count {
        let chunk = entry(id, &i.to_string())?
            .get_secret()
            .map_err(|_| "Secure credentials are incomplete; sign in again")?;
        if chunk.len() > 2000 {
            return Err("Invalid secure credential size".into());
        }
        bytes.extend(chunk);
    }
    serde_json::from_slice(&bytes).map_err(|_| "Cannot read secure credentials".into())
}
fn challenge(verifier: &str) -> String {
    URL_SAFE_NO_PAD.encode(Sha256::digest(verifier.as_bytes()))
}
fn callback(url: &reqwest::Url, expected_state: &str) -> Option<Result<String, String>> {
    if url.scheme() != "https"
        || url.host_str() != Some("login.live.com")
        || url.path() != "/oauth20_desktop.srf"
        || url.port_or_known_default() != Some(443)
    {
        return None;
    }
    let pairs = url.query_pairs().collect::<Vec<_>>();
    if pairs.iter().filter(|(k, _)| k == "state").count() != 1
        || pairs
            .iter()
            .find(|(k, _)| k == "state")
            .map(|(_, v)| v.as_ref())
            != Some(expected_state)
    {
        return Some(Err("Microsoft sign-in state validation failed".into()));
    }
    if pairs.iter().any(|(k, _)| k == "error") {
        return Some(Err("Microsoft sign-in was declined or cancelled".into()));
    }
    let codes = pairs
        .iter()
        .filter(|(k, _)| k == "code")
        .collect::<Vec<_>>();
    Some(if codes.len() == 1 && !codes[0].1.is_empty() {
        Ok(codes[0].1.to_string())
    } else {
        Err("Microsoft did not return an authorization code".into())
    })
}
fn http() -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(30))
        .connect_timeout(Duration::from_secs(8))
        .redirect(reqwest::redirect::Policy::none())
        .user_agent("BatClient/0.1")
        .build()
        .map_err(|_| "Cannot initialize secure authentication".to_owned())
}
fn response(mut response: reqwest::blocking::Response, stage: &str) -> Result<Value, String> {
    let status = response.status();
    let mut bytes = Vec::new();
    response
        .by_ref()
        .take(1024 * 1024 + 1)
        .read_to_end(&mut bytes)
        .map_err(|_| format!("{stage} request interrupted"))?;
    if bytes.len() > 1024 * 1024 {
        return Err(format!("{stage} response too large"));
    }
    if !status.is_success() {
        return Err(format!(
            "{stage} failed (HTTP {}). Re-sign in or check account eligibility.",
            status.as_u16()
        ));
    }
    serde_json::from_slice(&bytes).map_err(|_| format!("Invalid {stage} response"))
}
fn text<'a>(value: &'a Value, key: &str) -> Result<&'a str, String> {
    value
        .get(key)
        .and_then(Value::as_str)
        .filter(|s| !s.is_empty())
        .ok_or("Authentication response is incomplete".into())
}
fn exchange(code: &str, verifier: &str) -> Result<Value, String> {
    let client = http()?;
    response(
        client
            .post("https://login.live.com/oauth20_token.srf")
            .form(&[
                ("client_id", CLIENT_ID),
                ("grant_type", "authorization_code"),
                ("redirect_uri", REDIRECT),
                ("scope", "XboxLive.signin offline_access"),
                ("code", code),
                ("code_verifier", verifier),
            ])
            .send()
            .map_err(|_| "Cannot reach Microsoft token service")?,
        "Microsoft token exchange",
    )
}
fn minecraft(msa: &Value) -> Result<Tokens, String> {
    let client = http()?;
    let xbl=response(client.post("https://user.auth.xboxlive.com/user/authenticate").header("x-xbl-contract-version","1").json(&json!({"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT","Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":format!("d={}",text(msa,"access_token")?)}})).send().map_err(|_|"Cannot reach Xbox Live")?,"Xbox Live sign-in")?;
    let xsts=response(client.post("https://xsts.auth.xboxlive.com/xsts/authorize").header("x-xbl-contract-version","1").json(&json!({"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT","Properties":{"SandboxId":"RETAIL","UserTokens":[text(&xbl,"Token")?]}})).send().map_err(|_|"Cannot reach Xbox XSTS")?,"Xbox account authorization")?;
    let uhs = xsts
        .pointer("/DisplayClaims/xui/0/uhs")
        .and_then(Value::as_str)
        .ok_or("Xbox account hash missing")?;
    let game = response(
        client
            .post("https://api.minecraftservices.com/authentication/login_with_xbox")
            .json(&json!({"identityToken":format!("XBL3.0 x={uhs};{}",text(&xsts,"Token")?)}))
            .send()
            .map_err(|_| "Cannot reach Minecraft authentication")?,
        "Minecraft sign-in",
    )?;
    let token = text(&game, "access_token")?;
    let ownership = response(
        client
            .get("https://api.minecraftservices.com/entitlements/mcstore")
            .bearer_auth(token)
            .send()
            .map_err(|_| "Cannot check Minecraft ownership")?,
        "Minecraft ownership check",
    )?;
    if !ownership["items"].as_array().is_some_and(|items| {
        items.iter().any(|i| {
            [Some("product_minecraft"), Some("game_minecraft")].contains(&i["name"].as_str())
        })
    }) {
        return Err(
            "This Microsoft account does not have a verified Minecraft Java entitlement".into(),
        );
    }
    let profile = response(
        client
            .get("https://api.minecraftservices.com/minecraft/profile")
            .bearer_auth(token)
            .send()
            .map_err(|_| "Cannot fetch Minecraft profile")?,
        "Minecraft profile",
    )?;
    let username = text(&profile, "name")?.to_owned();
    let uuid = text(&profile, "id")?.to_owned();
    if uuid.len() != 32 || !uuid.chars().all(|c| c.is_ascii_hexdigit()) {
        return Err("Minecraft profile returned an invalid UUID".into());
    }
    Ok(Tokens {
        refresh_token: text(msa, "refresh_token")?.into(),
        access_token: token.into(),
        expires_at: now() + game["expires_in"].as_u64().unwrap_or(3600),
        username,
        uuid,
    })
}
fn persist(root: &Path, tokens: Tokens, refresh_id: Option<&str>) -> Result<PublicLogin, String> {
    let id = uuid::Uuid::new_v4().to_string();
    save_tokens(&id, &tokens)?;
    let result = (|| {
        let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library busy")?;
        lib::ensure_legacy_launcher_closed()?;
        lib::edit_config(root, |config| {
            let active = config["activeUsername"].as_str().map(str::to_owned);
            if config.get("savedAccounts").is_none() {
                config["savedAccounts"] = json!([]);
            }
            let list = config["savedAccounts"]
                .as_array_mut()
                .ok_or("Invalid account configuration")?;
            let matching_name = list.iter().position(|a| {
                a["username"]
                    .as_str()
                    .is_some_and(|name| name.eq_ignore_ascii_case(&tokens.username))
            });
            let index = if let Some(previous_id) = refresh_id {
                let index = list
                    .iter()
                    .position(|a| a["rustAuthId"] == previous_id && a["type"] == "premium")
                    .ok_or("This account session changed during refresh. Try again.")?;
                let saved_uuid = uuid::Uuid::parse_str(list[index]["uuid"].as_str().unwrap_or(""))
                    .map_err(|_| "The saved Microsoft profile has no valid UUID. Sign in again.")?;
                if Some(saved_uuid) != uuid::Uuid::parse_str(&tokens.uuid).ok()
                    || matching_name.is_some_and(|other| other != index)
                {
                    return Err("The refreshed profile does not match the saved account".into());
                }
                index
            } else if let Some(index) = matching_name {
                if list[index]["type"].as_str() != Some("premium") {
                    return Err("An offline account already uses this username; remove that saved identity first".into());
                }
                index
            } else {
                list.push(json!({}));
                list.len() - 1
            };
            // Keep legacy identifier and every cosmetic field; native credentials use a separate reference.
            let was_active = list[index]["username"].as_str() == active.as_deref();
            let old = list[index]["rustAuthId"].as_str().map(str::to_owned);
            list[index]["username"] = json!(tokens.username);
            list[index]["uuid"] = json!(tokens.uuid);
            list[index]["type"] = json!("premium");
            list[index]["rustAuthId"] = json!(id);
            if refresh_id.is_none() || was_active {
                config["activeUsername"] = json!(tokens.username);
            }
            Ok(old)
        })
    })();
    match result {
        Ok(old) => {
            if let Some(old) = old {
                delete_tokens(&old);
            }
            Ok(PublicLogin {
                username: tokens.username,
                uuid: tokens.uuid,
            })
        }
        Err(error) => {
            delete_tokens(&id);
            Err(error)
        }
    }
}
fn interactive(app: tauri::AppHandle) -> Result<PublicLogin, String> {
    let verifier = format!(
        "{}{}",
        uuid::Uuid::new_v4().simple(),
        uuid::Uuid::new_v4().simple()
    );
    let state = uuid::Uuid::new_v4().to_string();
    let mut url = reqwest::Url::parse("https://login.live.com/oauth20_authorize.srf")
        .map_err(|_| "Invalid authentication URL")?;
    url.query_pairs_mut().extend_pairs([
        ("client_id", CLIENT_ID),
        ("response_type", "code"),
        ("redirect_uri", REDIRECT),
        ("scope", "XboxLive.signin offline_access"),
        ("code_challenge", &challenge(&verifier)),
        ("code_challenge_method", "S256"),
        ("state", &state),
        ("prompt", "select_account"),
    ]);
    let (tx, rx) = mpsc::channel::<Result<String, String>>();
    let navigation = tx.clone();
    // Creation stays on this worker, as recommended for WebView2; never block the UI thread.
    let window = WebviewWindowBuilder::new(&app, "microsoft-signin", WebviewUrl::External(url))
        .title("Sign in to Microsoft · Bat Client")
        .inner_size(520.0, 720.0)
        .min_inner_size(420.0, 540.0)
        .on_navigation(move |url| {
            if let Some(result) = callback(url, &state) {
                let _ = navigation.send(result);
                false
            } else {
                url.scheme() == "https"
            }
        })
        .build()
        .map_err(|_| "Cannot open Microsoft sign-in window")?;
    window.on_window_event(move |event| {
        if matches!(event, WindowEvent::Destroyed) {
            let _ = tx.send(Err("Microsoft sign-in window closed".into()));
        }
    });
    let result = rx
        .recv_timeout(Duration::from_secs(300))
        .unwrap_or_else(|_| Err("Microsoft sign-in timed out".into()));
    let _ = window.close();
    let code = result?;
    persist(
        &lib::root()?,
        minecraft(&exchange(&code, &verifier)?)?,
        None,
    )
}
#[tauri::command]
pub async fn start_microsoft_login(app: tauri::AppHandle) -> Result<PublicLogin, String> {
    if SIGNING_IN.swap(true, Ordering::SeqCst) {
        return Err("Microsoft sign-in is already open".into());
    }
    tauri::async_runtime::spawn_blocking(move || {
        let _guard = LoginGuard;
        lib::ensure_legacy_launcher_closed()?;
        interactive(app)
    })
    .await
    .map_err(|_| "Microsoft authentication failed")?
}
pub fn refresh_at(root: &Path, username: &str) -> Result<PublicLogin, String> {
    let config = lib::config(root)?;
    let account = config["savedAccounts"]
        .as_array()
        .and_then(|a| a.iter().find(|a| a["username"].as_str() == Some(username)))
        .ok_or("Account not found")?;
    let id = account["rustAuthId"]
        .as_str()
        .ok_or("Sign in once in the Rust launcher; your old Electron session is preserved")?;
    let previous = read_tokens(id)?;
    let client = http()?;
    let mut msa = response(
        client
            .post("https://login.live.com/oauth20_token.srf")
            .form(&[
                ("client_id", CLIENT_ID),
                ("grant_type", "refresh_token"),
                ("redirect_uri", REDIRECT),
                ("scope", "XboxLive.signin offline_access"),
                ("refresh_token", previous.refresh_token.as_str()),
            ])
            .send()
            .map_err(|_| "Cannot refresh Microsoft session")?,
        "Microsoft refresh",
    )?;
    if msa.get("refresh_token").is_none() {
        msa["refresh_token"] = json!(previous.refresh_token);
    }
    persist(root, minecraft(&msa)?, Some(id))
}
pub fn fresh_for_launch(root: &Path, username: &str) -> Result<(), String> {
    let config = lib::config(root)?;
    let account = config["savedAccounts"]
        .as_array()
        .and_then(|a| a.iter().find(|a| a["username"].as_str() == Some(username)))
        .ok_or("Account not found")?;
    if account["type"] != "premium" {
        return Ok(());
    }
    let id = account["rustAuthId"]
        .as_str()
        .ok_or("Sign in with Microsoft once in this Rust launcher before launching")?;
    let tokens = read_tokens(id)?;
    if !tokens.username.eq_ignore_ascii_case(username) {
        return Err("Saved Microsoft session does not match this account. Sign in again.".into());
    }
    if tokens.expires_at <= now() + 60 {
        refresh_at(root, username)?;
    }
    Ok(())
}
#[tauri::command]
pub async fn refresh_microsoft_account(username: String) -> Result<PublicLogin, String> {
    tauri::async_runtime::spawn_blocking(move || refresh_at(&lib::root()?, &username))
        .await
        .map_err(|_| "Cannot refresh Microsoft account")?
}
// Explicit development smoke mode: hidden native webview, no OAuth request or user input.
pub fn native_window_smoke(app: &tauri::AppHandle) -> Result<(), String> {
    let url = reqwest::Url::parse("about:blank").map_err(|_| "Invalid smoke URL")?;
    let window =
        WebviewWindowBuilder::new(app, "microsoft-window-smoke", WebviewUrl::External(url))
            .title("Bat native auth window smoke")
            .visible(false)
            .inner_size(520.0, 720.0)
            .build()
            .map_err(|_| "Native auth webview creation failed")?;
    window
        .close()
        .map_err(|_| "Native auth webview close failed")?;
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn pkce_matches_rfc7636() {
        assert_eq!(
            challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        );
    }
    #[test]
    fn callback_validates_origin_state_and_duplicate_parameters() {
        let parse = |s| reqwest::Url::parse(s).unwrap();
        assert!(callback(
            &parse("https://evil.example/oauth20_desktop.srf?state=s&code=PRIVATE"),
            "s"
        )
        .is_none());
        assert!(callback(
            &parse("https://login.live.com/oauth20_desktop.srf?state=other&code=PRIVATE"),
            "s"
        )
        .unwrap()
        .is_err());
        assert!(callback(
            &parse("https://login.live.com/oauth20_desktop.srf?state=s&code=a&code=b"),
            "s"
        )
        .unwrap()
        .is_err());
        assert_eq!(
            callback(
                &parse("https://login.live.com/oauth20_desktop.srf?state=s&code=accepted"),
                "s"
            )
            .unwrap()
            .unwrap(),
            "accepted"
        );
    }
    #[test]
    #[ignore = "Uses the real OS credential store with a random disposable entry"]
    fn native_secure_storage_roundtrip() {
        let id = uuid::Uuid::new_v4().to_string();
        let tokens = Tokens {
            refresh_token: "SYNTHETIC-REFRESH".into(),
            access_token: "SYNTHETIC-ACCESS".repeat(400),
            expires_at: 123,
            username: "Test".into(),
            uuid: "0".repeat(32),
        };
        save_tokens(&id, &tokens).unwrap();
        let saved = read_tokens(&id).unwrap();
        assert_eq!(saved.access_token, tokens.access_token);
        assert_eq!(saved.refresh_token, tokens.refresh_token);
        remove_credentials(&id).unwrap();
        assert!(read_tokens(&id).is_err());
    }
}
