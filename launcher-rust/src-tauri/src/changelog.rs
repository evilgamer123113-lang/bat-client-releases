use std::time::Duration;

const URL: &str = "https://batclient.xyz/changelog.html";
const MAX_BYTES: usize = 1024 * 1024;

#[tauri::command]
pub async fn fetch_changelog() -> Result<String, String> {
    // No frontend URL parameter, cookies, authorization or arbitrary proxy target.
    let client = reqwest::Client::builder()
        .connect_timeout(Duration::from_secs(3))
        .timeout(Duration::from_secs(8))
        .redirect(reqwest::redirect::Policy::none())
        .user_agent("BatClient-Launcher/0.1")
        .build()
        .map_err(|_| "Cannot initialize update request".to_owned())?;
    let mut response = client
        .get(URL)
        .header("Accept", "text/html")
        .send()
        .await
        .map_err(|_| "Cannot reach the Bat Client changelog".to_owned())?;
    if !response.status().is_success() {
        return Err("The changelog returned an unsuccessful response".into());
    }
    if response
        .content_length()
        .is_some_and(|size| size > MAX_BYTES as u64)
    {
        return Err("Changelog response exceeds the size limit".into());
    }
    let mut bytes = Vec::new();
    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(|_| "Changelog download was interrupted".to_owned())?
    {
        if bytes.len().saturating_add(chunk.len()) > MAX_BYTES {
            return Err("Changelog response exceeds the size limit".into());
        }
        bytes.extend_from_slice(&chunk);
    }
    String::from_utf8(bytes).map_err(|_| "Changelog is not valid UTF-8".into())
}
