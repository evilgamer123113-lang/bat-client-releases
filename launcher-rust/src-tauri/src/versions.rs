use serde::Serialize;
use serde_json::Value;
use std::{
    io::Read,
    sync::{Mutex, OnceLock},
    time::{Duration, Instant},
};
const URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Version {
    pub id: String,
    pub kind: String,
    pub release_time: String,
}
static CACHE: OnceLock<Mutex<Option<(Instant, Vec<Version>)>>> = OnceLock::new();
fn parse(value: &Value) -> Result<Vec<Version>, String> {
    let mut versions = vec![];
    for item in value["versions"]
        .as_array()
        .ok_or("Invalid Mojang version catalog")?
        .iter()
        .take(10000)
    {
        let id = item["id"].as_str().unwrap_or("");
        let kind = item["type"].as_str().unwrap_or("");
        if id.is_empty()
            || id.len() > 64
            || !id
                .chars()
                .all(|c| c.is_ascii_alphanumeric() || ".-_".contains(c))
            || !["release", "snapshot", "old_beta", "old_alpha"].contains(&kind)
        {
            continue;
        }
        versions.push(Version {
            id: id.into(),
            kind: kind.into(),
            release_time: item["releaseTime"]
                .as_str()
                .unwrap_or("")
                .chars()
                .take(40)
                .collect(),
        });
    }
    if versions.is_empty() {
        return Err("Mojang returned no usable Minecraft versions".into());
    }
    Ok(versions)
}
pub fn fetch() -> Result<Vec<Version>, String> {
    let cache = CACHE.get_or_init(|| Mutex::new(None));
    if let Ok(cache) = cache.lock() {
        if let Some((at, items)) = &*cache {
            if at.elapsed() < Duration::from_secs(3600) {
                return Ok(items.clone());
            }
        }
    }
    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(15))
        .redirect(reqwest::redirect::Policy::none())
        .user_agent("BatClient/0.1")
        .build()
        .map_err(|_| "Cannot initialize Minecraft catalog request")?;
    let mut response = client
        .get(URL)
        .send()
        .map_err(|_| "Cannot reach the Minecraft version catalog")?;
    if !response.status().is_success() {
        return Err("Minecraft version catalog request failed".into());
    }
    let mut bytes = Vec::new();
    response
        .by_ref()
        .take(4 * 1024 * 1024 + 1)
        .read_to_end(&mut bytes)
        .map_err(|_| "Minecraft catalog download interrupted")?;
    if bytes.len() > 4 * 1024 * 1024 {
        return Err("Minecraft catalog is too large".into());
    }
    let json = serde_json::from_slice(&bytes).map_err(|_| "Invalid Minecraft catalog response")?;
    let items = parse(&json)?;
    if let Ok(mut cache) = cache.lock() {
        *cache = Some((Instant::now(), items.clone()));
    }
    Ok(items)
}
#[tauri::command]
pub async fn minecraft_versions() -> Result<Vec<Version>, String> {
    tauri::async_runtime::spawn_blocking(fetch)
        .await
        .map_err(|_| "Minecraft catalog request failed")?
}
#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn parses_releases_snapshots_without_private_metadata() {
        let result=parse(&json!({"versions":[{"id":"1.21.1","type":"release","url":"not-exposed"},{"id":"24w14a","type":"snapshot"},{"id":"b1.7.3","type":"old_beta"},{"id":"a1.2.6","type":"old_alpha"},{"id":"../bad","type":"release"}]})).unwrap();
        assert_eq!(result.len(), 4);
        assert!(!serde_json::to_string(&result)
            .unwrap()
            .contains("not-exposed"));
    }
    #[test]
    #[ignore = "Contacts the public Mojang metadata service"]
    fn actual_mojang_catalog() {
        let versions = fetch().unwrap();
        for kind in ["release", "snapshot", "old_beta", "old_alpha"] {
            assert!(
                versions.iter().any(|v| v.kind == kind),
                "Missing catalog type {kind}"
            );
        }
        for id in ["1.21.1", "1.8", "1.8.9", "b1.7.3", "a1.2.6"] {
            assert!(versions.iter().any(|v| v.id == id), "Missing version {id}");
        }
    }
}
