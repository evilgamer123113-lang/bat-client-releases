use crate::library as lib;
use base64::{engine::general_purpose::STANDARD, Engine};
use serde::Serialize;
use serde_json::{json, Value};
use std::{
    fs,
    path::Path,
    time::{SystemTime, UNIX_EPOCH},
};
fn username_valid(name: &str) -> bool {
    (3..=16).contains(&name.len()) && name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
}
pub fn action_at(root: &Path, username: &str, action: &str) -> Result<(), String> {
    if !username_valid(username) {
        return Err("Use a Minecraft username with 3–16 letters, numbers or underscores".into());
    }
    lib::edit_config(root, |config| {
        if config.get("savedAccounts").is_none() {
            config["savedAccounts"] = json!([]);
        }
        let list = config["savedAccounts"]
            .as_array_mut()
            .ok_or("Invalid saved accounts")?;
        let found = list.iter().position(|a| {
            a.get("username")
                .and_then(Value::as_str)
                .is_some_and(|name| name.eq_ignore_ascii_case(username))
        });
        match action {
            "add_offline" => {
                if found.is_some() {
                    return Err("This account is already saved".into());
                }
                if list.len() >= 100 {
                    return Err("Too many accounts".into());
                }
                let nonce = SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_nanos();
                list.push(json!({"username":username,"uuid":format!("bat_user_{nonce}")}));
                config["activeUsername"] = json!(username);
            }
            "select" => {
                let index = found.ok_or("Account no longer exists")?;
                config["activeUsername"] = list[index]["username"].clone();
            }
            "remove" => {
                let index = found.ok_or("Account no longer exists")?;
                let removed = list[index]["username"].clone();
                list.remove(index);
                let first = list
                    .first()
                    .map(|a| a["username"].clone())
                    .unwrap_or(json!(""));
                if config["activeUsername"] == removed {
                    config["activeUsername"] = first;
                }
            }
            _ => return Err("Unsupported account action".into()),
        }
        Ok(())
    })
}
#[tauri::command]
pub async fn account_action(username: String, action: String) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || {
        let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library busy")?;
        lib::ensure_legacy_launcher_closed()?;
        let root = lib::root()?;
        let before = lib::config(&root)?;
        let reference = if action == "remove" {
            before["savedAccounts"]
                .as_array()
                .and_then(|a| {
                    a.iter().find(|a| {
                        a["username"]
                            .as_str()
                            .is_some_and(|n| n.eq_ignore_ascii_case(&username))
                    })
                })
                .and_then(|a| a["rustAuthId"].as_str())
                .map(str::to_owned)
        } else {
            None
        };
        action_at(&root, &username, &action)?;
        if let Some(id) = reference {
            let after = lib::config(&root)?;
            if !after["savedAccounts"]
                .as_array()
                .is_some_and(|a| a.iter().any(|a| a["rustAuthId"] == id))
            {
                crate::microsoft::remove_credentials(&id)?;
            }
        }
        Ok(())
    })
    .await
    .map_err(|_| "Cannot update account")?
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Skin {
    pub skin_data_url: Option<String>,
    pub head_data_url: Option<String>,
    pub model: String,
}
fn png_data(root: &Path, name: &str) -> Option<String> {
    let path = lib::regular_file(&root.join("skins"), name).ok()?;
    if fs::metadata(&path).ok()?.len() > 4 * 1024 * 1024 {
        return None;
    }
    let bytes = fs::read(path).ok()?;
    if !bytes.starts_with(b"\x89PNG\r\n\x1a\n") || bytes.len() < 24 {
        return None;
    }
    let w = u32::from_be_bytes(bytes.get(16..20)?.try_into().ok()?);
    let h = u32::from_be_bytes(bytes.get(20..24)?.try_into().ok()?);
    if w > 4096 || h > 4096 {
        return None;
    }
    Some(format!("data:image/png;base64,{}", STANDARD.encode(bytes)))
}
pub fn skin_at(root: &Path, username: &str) -> Result<Skin, String> {
    let config = lib::config(root)?;
    let account = config["savedAccounts"]
        .as_array()
        .and_then(|list| {
            list.iter()
                .find(|a| a["username"].as_str() == Some(username))
        })
        .ok_or("Account not found")?;
    let skin = account["skinFile"]
        .as_str()
        .and_then(|name| png_data(root, name))
        .or_else(|| account["customSkin"].as_str().and_then(|name|png_data(root,name)));
    let head = account["headIcon"]
        .as_str()
        .filter(|s| s.starts_with("data:image/png;base64,") && s.len() < 64 * 1024)
        .map(str::to_owned);
    Ok(Skin {
        skin_data_url: skin,
        head_data_url: head,
        model: if account["skinModel"].as_str() == Some("slim") {
            "slim".into()
        } else {
            "classic".into()
        },
    })
}
#[tauri::command]
pub async fn account_skin(username: String) -> Result<Skin, String> {
    let (mut skin, remote_id) = tauri::async_runtime::spawn_blocking(move || {
        let root = lib::root()?;
        let skin = skin_at(&root, &username)?;
        let remote_id = if skin.skin_data_url.is_none() && skin.head_data_url.is_none() {
            premium_texture_id(&root, &username)
        } else {
            None
        };
        Ok::<_, String>((skin, remote_id))
    })
    .await
    .map_err(|_| "Cannot inspect saved skin")??;
    if let Some(id) = remote_id {
        let remote = premium_textures(id, true, false).await;
        if remote.skin.is_some() {
            skin.skin_data_url = remote.skin;
            skin.model = remote.model.into();
        }
    }
    Ok(skin)
}

pub(crate) fn premium_texture_id(root: &Path, username: &str) -> Option<uuid::Uuid> {
    let config = lib::config(root).ok()?;
    let account = config["savedAccounts"].as_array()?.iter().find(|account| {
        account["username"].as_str() == Some(username) && account["type"] == "premium"
    })?;
    uuid::Uuid::parse_str(account["uuid"].as_str()?).ok()
}

pub(crate) struct PremiumTextures {
    pub skin: Option<String>,
    pub cape: Option<String>,
    pub model: &'static str,
}

async fn bounded_texture_response(
    client: &reqwest::Client,
    url: reqwest::Url,
    limit: usize,
) -> Option<Vec<u8>> {
    let mut response = client.get(url).send().await.ok()?;
    if !response.status().is_success()
        || response
            .content_length()
            .is_some_and(|size| size > limit as u64)
    {
        return None;
    }
    let mut bytes = Vec::new();
    while let Some(chunk) = response.chunk().await.ok()? {
        if chunk.len() > limit - bytes.len() {
            return None;
        }
        bytes.extend_from_slice(&chunk);
    }
    Some(bytes)
}

async fn download_texture(client: &reqwest::Client, texture: &Value) -> Option<String> {
    let url = reqwest::Url::parse(texture["url"].as_str()?).ok()?;
    if !matches!(url.scheme(), "http" | "https")
        || url.host_str() != Some("textures.minecraft.net")
        || url.port().is_some()
        || !url.username().is_empty()
        || url.password().is_some()
        || url.query().is_some()
        || url.fragment().is_some()
    {
        return None;
    }
    let hash = url.path().strip_prefix("/texture/")?;
    if hash.is_empty() || hash.len() > 64 || !hash.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return None;
    }
    let url = format!("https://textures.minecraft.net/texture/{hash}")
        .parse()
        .ok()?;
    let bytes = bounded_texture_response(client, url, 1024 * 1024).await?;
    if !bytes.starts_with(b"\x89PNG\r\n\x1a\n") || bytes.get(12..16)? != b"IHDR" {
        return None;
    }
    let width = u32::from_be_bytes(bytes.get(16..20)?.try_into().ok()?);
    let height = u32::from_be_bytes(bytes.get(20..24)?.try_into().ok()?);
    if !(1..=1024).contains(&width) || !(1..=1024).contains(&height) {
        return None;
    }
    Some(format!("data:image/png;base64,{}", STANDARD.encode(bytes)))
}

pub(crate) async fn premium_textures(id: uuid::Uuid, skin: bool, cape: bool) -> PremiumTextures {
    let mut result = PremiumTextures {
        skin: None,
        cape: None,
        model: "classic",
    };
    let Ok(client) = reqwest::Client::builder()
        .connect_timeout(std::time::Duration::from_secs(3))
        .timeout(std::time::Duration::from_secs(5))
        .redirect(reqwest::redirect::Policy::none())
        .build()
    else {
        return result;
    };
    let url = format!(
        "https://sessionserver.mojang.com/session/minecraft/profile/{}",
        id.simple()
    );
    let Ok(url) = url.parse() else { return result };
    let Some(profile) = bounded_texture_response(&client, url, 64 * 1024).await else {
        return result;
    };
    let Ok(profile) = serde_json::from_slice::<Value>(&profile) else {
        return result;
    };
    let Some(property) = profile["properties"].as_array().and_then(|properties| {
        properties
            .iter()
            .find(|property| property["name"] == "textures")
    }) else {
        return result;
    };
    let Some(value) = property["value"].as_str() else {
        return result;
    };
    let Ok(bytes) = STANDARD.decode(value) else {
        return result;
    };
    let Ok(data) = serde_json::from_slice::<Value>(&bytes) else {
        return result;
    };
    let textures = &data["textures"];
    if skin {
        result.skin = download_texture(&client, &textures["SKIN"]).await;
    }
    if cape {
        result.cape = download_texture(&client, &textures["CAPE"]).await;
    }
    if textures["SKIN"]["metadata"]["model"] == "slim" {
        result.model = "slim";
    }
    result
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn offline_lifecycle_preserves_premium_credentials() {
        let root = tempfile::tempdir().unwrap();
        fs::write(root.path().join("profiles.config.json"),json!({"savedAccounts":[{"username":"Premium","type":"premium","identifier":"ENCRYPTED_STORE_REFERENCE","private":"DO_NOT_EXPOSE"}],"profiles":[],"appSettings":{"preserve":true}}).to_string()).unwrap();
        action_at(root.path(), "Offline_1", "add_offline").unwrap();
        action_at(root.path(), "Premium", "select").unwrap();
        action_at(root.path(), "Offline_1", "remove").unwrap();
        let config = lib::config(root.path()).unwrap();
        assert_eq!(config["savedAccounts"][0]["private"], "DO_NOT_EXPOSE");
        assert_eq!(config["appSettings"]["preserve"], true);
        action_at(root.path(), "Premium", "remove").unwrap();
        assert!(lib::config(root.path()).unwrap()["savedAccounts"]
            .as_array()
            .unwrap()
            .is_empty());
    }
    #[test]
    fn invalid_names_and_duplicates_do_not_modify() {
        let root = tempfile::tempdir().unwrap();
        assert!(action_at(root.path(), "../escape", "add_offline").is_err());
        action_at(root.path(), "PlayerOne", "add_offline").unwrap();
        assert!(action_at(root.path(), "playerone", "add_offline").is_err());
    }
    #[test]
    fn skin_payload_excludes_tokens_and_rejects_escape() {
        let root = tempfile::tempdir().unwrap();
        fs::write(root.path().join("profiles.config.json"),json!({"savedAccounts":[{"username":"Player","accessToken":"PRIVATE","skinFile":"../escape.png","headIcon":"https://untrusted/skin"}]}).to_string()).unwrap();
        let skin = skin_at(root.path(), "Player").unwrap();
        assert!(skin.skin_data_url.is_none());
        assert!(skin.head_data_url.is_none());
        assert!(!serde_json::to_string(&skin).unwrap().contains("PRIVATE"));
    }
}
