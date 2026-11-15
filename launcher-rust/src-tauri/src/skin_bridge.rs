//! Per-game loopback-only Yggdrasil bridge. No credentials are served or logged.
use base64::{engine::general_purpose::STANDARD, Engine};
use md5::{Digest, Md5};
use rsa::{
    pkcs1v15::SigningKey,
    pkcs8::EncodePublicKey,
    signature::{RandomizedSigner, SignatureEncoding},
    RsaPrivateKey,
};
use serde_json::{json, Value};
use sha1::Sha1;
use sha2::Sha256;
use std::{
    collections::HashMap,
    io::Read,
    path::Path,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
    thread,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use tiny_http::{Header, Response, Server};

pub fn offline_uuid(name: &str) -> String {
    let mut bytes: [u8; 16] = Md5::digest(format!("OfflinePlayer:{name}")).into();
    bytes[6] = (bytes[6] & 15) | 48;
    bytes[8] = (bytes[8] & 63) | 128;
    uuid::Uuid::from_bytes(bytes).simple().to_string()
}
pub struct Bridge {
    pub root: String,
    pub profile_properties: String,
    stop: Arc<AtomicBool>,
}
impl Drop for Bridge {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
    }
}
struct Context {
    premium: bool,
    root: String,
    name: String,
    id: String,
    key: SigningKey<Sha1>,
    metadata: Value,
    profile: Value,
    services: Value,
    assets: Mutex<HashMap<String, Vec<u8>>>,
    profiles: Mutex<HashMap<String, Value>>,
}
fn profile(key: &SigningKey<Sha1>, id: &str, name: &str, textures: Value) -> Value {
    let value=STANDARD.encode(json!({"timestamp":SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_millis(),"profileId":id,"profileName":name,"textures":textures}).to_string());
    let signature = STANDARD.encode(
        key.sign_with_rng(&mut rand::thread_rng(), value.as_bytes())
            .to_bytes(),
    );
    json!({"id":id,"name":name,"properties":[{"name":"textures","value":value,"signature":signature}]})
}
fn asset(context: &Context, bytes: Vec<u8>) -> Option<String> {
    if bytes.len() > 4 * 1024 * 1024 || !bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        return None;
    }
    let hash = format!("{:x}", Sha256::digest(&bytes));
    let mut assets = context.assets.lock().ok()?;
    if assets.len() > 128
        || assets.values().map(Vec::len).sum::<usize>() + bytes.len() > 24 * 1024 * 1024
    {
        return None;
    }
    assets.insert(hash.clone(), bytes);
    Some(format!("{}/textures/{hash}", context.root))
}
fn file(root: &Path, name: Option<&str>) -> Option<Vec<u8>> {
    let path = crate::library::regular_file(&root.join("skins"), name?).ok()?;
    if std::fs::metadata(&path).ok()?.len() > 4 * 1024 * 1024 {
        return None;
    }
    std::fs::read(path).ok()
}
fn remote_json(url: &str) -> Option<Value> {
    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(4))
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .ok()?;
    let mut response = client.get(url).send().ok()?;
    if !response.status().is_success() {
        return None;
    }
    let mut bytes = vec![];
    response
        .by_ref()
        .take(6 * 1024 * 1024 + 1)
        .read_to_end(&mut bytes)
        .ok()?;
    if bytes.len() > 6 * 1024 * 1024 {
        return None;
    }
    serde_json::from_slice(&bytes).ok()
}
fn remote_profile(c: &Context, id: &str) -> Option<Value> {
    if let Some(value) = c.profiles.lock().ok()?.get(id) {
        return Some(value.clone());
    }
    if c.profiles.lock().ok()?.len() >= 128 {
        return None;
    }
    // Without authlib-injector, premium clients require Mojang's ORIGINAL
    // signature. Never replace their official texture properties with ours.
    if c.premium {
        let original = remote_json(&format!(
            "https://sessionserver.mojang.com/session/minecraft/profile/{id}?unsigned=false"
        ))?;
        if original["id"].as_str() != Some(id) {
            return None;
        }
        c.profiles.lock().ok()?.insert(id.into(), original.clone());
        return Some(original);
    }
    let mut result = None;
    if let Some(remote) = remote_json(&format!("https://batclient.xyz/skinapi/api/player/{id}")) {
        let mut textures = json!({});
        for (kind, field) in [("SKIN", "skin"), ("CAPE", "cape")] {
            if let Some(data) = remote[field]["data"].as_str() {
                if data.len() < 6 * 1024 * 1024 {
                    if let Some(bytes) = data
                        .split_once(',')
                        .and_then(|(_, s)| STANDARD.decode(s).ok())
                    {
                        if let Some(url) = asset(c, bytes) {
                            textures[kind] = json!({"url":url});
                            if kind == "SKIN" && remote[field]["model"].as_str() == Some("slim") {
                                textures[kind]["metadata"] = json!({"model":"slim"});
                            }
                        }
                    }
                }
            }
        }
        if textures.as_object().is_some_and(|v| !v.is_empty()) {
            let name = remote["username"].as_str().unwrap_or(id);
            result = Some(profile(&c.key, id, name, textures));
        }
    }
    if result.is_none() {
        if let Some(original) = remote_json(&format!(
            "https://sessionserver.mojang.com/session/minecraft/profile/{id}"
        )) {
            if let Some(value) = original["properties"]
                .as_array()
                .and_then(|p| p.iter().find(|p| p["name"] == "textures"))
                .and_then(|p| p["value"].as_str())
                .and_then(|s| STANDARD.decode(s).ok())
                .and_then(|b| serde_json::from_slice::<Value>(&b).ok())
            {
                result = Some(profile(
                    &c.key,
                    id,
                    original["name"].as_str().unwrap_or(id),
                    value["textures"].clone(),
                ));
            }
        }
    }
    if let Some(value) = &result {
        c.profiles.lock().ok()?.insert(id.into(), value.clone());
    }
    result
}
fn route(c: &Context, path: &str) -> (u16, &'static str, Vec<u8>) {
    let attrs = json!({"privileges":{"onlineChat":{"enabled":true},"multiplayerServer":{"enabled":true},"multiplayerRealms":{"enabled":false},"telemetry":{"enabled":false}},"profanityFilterPreferences":{"profanityFilterOn":false},"banStatus":{"bannedScopes":{}}});
    let json = if path == "/" {
        c.metadata.clone()
    } else if let Some(hash) = path.strip_prefix("/textures/") {
        return match c.assets.lock().ok().and_then(|a| a.get(hash).cloned()) {
            Some(bytes) => (200, "image/png", bytes),
            None => (404, "text/plain", vec![]),
        };
    } else if let Some(id) = path.strip_prefix("/sessionserver/session/minecraft/profile/") {
        let id = id.replace('-', "").to_lowercase();
        if id.len() != 32 || !id.chars().all(|c| c.is_ascii_hexdigit()) {
            return (400, "text/plain", vec![]);
        }
        if id == c.id {
            c.profile.clone()
        } else if let Some(value) = remote_profile(c, &id) {
            value
        } else {
            return (204, "application/json", vec![]);
        }
    } else if path == "/sessionserver/session/minecraft/hasJoined" {
        c.profile.clone()
    } else if path == "/sessionserver/session/minecraft/join"
        || path == "/authserver/validate"
        || path == "/authserver/invalidate"
        || path == "/authserver/signout"
    {
        return (204, "application/json", vec![]);
    } else if path == "/authserver/authenticate" || path == "/authserver/refresh" {
        json!({"accessToken":"bat_session_pass","clientToken":"bat_client_pass","selectedProfile":c.profile,"availableProfiles":[c.profile],"user":{"id":c.id,"username":c.name}})
    } else if path == "/api/profiles/minecraft" {
        json!([c.profile])
    } else if path == "/minecraftservices/minecraft/profile" {
        c.services.clone()
    } else if path == "/minecraftservices/player/attributes" {
        attrs
    } else if path == "/minecraftservices/privileges" {
        attrs["privileges"].clone()
    } else if path.starts_with("/minecraftservices/") {
        json!({})
    } else {
        return (404, "application/json", b"{}".to_vec());
    };
    (200, "application/json", json.to_string().into_bytes())
}
pub fn start(root: &Path, account: &Value, id: &str) -> Result<Bridge, String> {
    let name = account["username"]
        .as_str()
        .ok_or("Account has no username")?;
    let server =
        Arc::new(Server::http("127.0.0.1:0").map_err(|_| "Cannot open local skin service")?);
    let address = server
        .server_addr()
        .to_ip()
        .ok_or("Invalid local service address")?;
    let url = format!("http://127.0.0.1:{}", address.port());
    let key = RsaPrivateKey::new(&mut rand::thread_rng(), 2048)
        .map_err(|_| "Cannot initialize local texture signing")?;
    let public = key
        .to_public_key()
        .to_public_key_pem(Default::default())
        .map_err(|_| "Cannot encode texture public key")?;
    let mut context = Context {
        premium: account["type"] == "premium",
        root: url.clone(),
        name: name.into(),
        id: id.into(),
        key: SigningKey::<Sha1>::new(key),
        metadata: json!({"meta":{"serverName":"Bat Client","implementationName":"bat-native-skins","implementationVersion":"1"},"skinDomains":["127.0.0.1","localhost","textures.minecraft.net","batclient.xyz"],"signaturePublickey":public}),
        profile: Value::Null,
        services: Value::Null,
        assets: Mutex::new(HashMap::new()),
        profiles: Mutex::new(HashMap::new()),
    };
    let skin = file(root,account["skinFile"].as_str())
    .or_else(||file(root,account["customSkin"].as_str()))
    .unwrap_or_else(|| include_bytes!("../../ui/assets/steve.png").to_vec());
    let skin_url = asset(&context, skin).ok_or("Invalid local skin image")?;
    let mut textures = json!({"SKIN":{"url":skin_url}});
    if account["skinModel"] == "slim" {
        textures["SKIN"]["metadata"] = json!({"model":"slim"});
    }
    let cape = file(root, account["capeFrames"][0].as_str())
        .or_else(|| file(root, account["customCape"].as_str()))
        .and_then(|b| asset(&context, b));
    if let Some(cape) = &cape {
        textures["CAPE"] = json!({"url":cape});
    }
    context.profile = profile(&context.key, id, name, textures);
    if context.premium {
        context.profile = remote_json(&format!(
            "https://sessionserver.mojang.com/session/minecraft/profile/{id}?unsigned=false"
        ))
        .filter(|v| v["id"].as_str() == Some(id))
        .unwrap_or_else(|| json!({"id":id,"name":name,"properties":[]}));
    }
    context.services = json!({"id":id,"name":name,"skins":[{"id":id,"state":"ACTIVE","url":skin_url,"variant":if account["skinModel"]=="slim"{"SLIM"}else{"CLASSIC"}}],"capes":cape.map(|url|vec![json!({"id":"custom-cape","state":"ACTIVE","url":url,"alias":"Custom Cape"})]).unwrap_or_default()});
    let properties = context.profile["properties"].to_string();
    let context = Arc::new(context);
    let stop = Arc::new(AtomicBool::new(false));
    for _ in 0..4 {
        let (server, context, stop) = (server.clone(), context.clone(), stop.clone());
        thread::spawn(move || {
            while !stop.load(Ordering::Relaxed) {
                if let Ok(Some(req)) = server.recv_timeout(Duration::from_millis(200)) {
                    if req.body_length().unwrap_or(0) > 65536 {
                        let _ = req.respond(Response::empty(413));
                        continue;
                    }
                    let path = req.url().split('?').next().unwrap_or("");
                    let (code, mime, bytes) = route(&context, path);
                    let mut response = Response::from_data(bytes).with_status_code(code);
                    for (name, value) in [("Content-Type", mime), ("Cache-Control", "no-store")] {
                        if let Ok(header) = Header::from_bytes(name, value) {
                            response.add_header(header);
                        }
                    }
                    let _ = req.respond(response);
                }
            }
        });
    }
    Ok(Bridge {
        root: url,
        profile_properties: properties,
        stop,
    })
}
#[cfg(test)]
mod tests {
    use super::*;
    use rsa::{
        pkcs1v15::{Signature, VerifyingKey},
        pkcs8::DecodePublicKey,
        signature::Verifier,
        RsaPublicKey,
    };
    #[test]
    fn native_bridge_serves_signed_local_profile_and_stops() {
        let root = tempfile::tempdir().unwrap();
        let bridge = start(
            root.path(),
            &json!({"username":"Player"}),
            &offline_uuid("Player"),
        )
        .unwrap();
        let client = reqwest::blocking::Client::new();
        let metadata: Value = client.get(&bridge.root).send().unwrap().json().unwrap();
        let profile: Value = client
            .get(format!(
                "{}/sessionserver/session/minecraft/profile/{}",
                bridge.root,
                offline_uuid("Player")
            ))
            .send()
            .unwrap()
            .json()
            .unwrap();
        let public =
            RsaPublicKey::from_public_key_pem(metadata["signaturePublickey"].as_str().unwrap())
                .unwrap();
        let property = &profile["properties"][0];
        let signature = Signature::try_from(
            STANDARD
                .decode(property["signature"].as_str().unwrap())
                .unwrap()
                .as_slice(),
        )
        .unwrap();
        VerifyingKey::<Sha1>::new(public)
            .verify(property["value"].as_str().unwrap().as_bytes(), &signature)
            .unwrap();
        let textures: Value = serde_json::from_slice(
            &STANDARD
                .decode(property["value"].as_str().unwrap())
                .unwrap(),
        )
        .unwrap();
        let texture = client
            .get(textures["textures"]["SKIN"]["url"].as_str().unwrap())
            .send()
            .unwrap()
            .bytes()
            .unwrap();
        assert!(texture.starts_with(b"\x89PNG"));
        assert_eq!(
            client
                .get(format!("{}/textures/../secret", bridge.root))
                .send()
                .unwrap()
                .status(),
            404
        );
    }
    #[test]
    fn offline_identity_matches_minecraft() {
        assert_eq!(offline_uuid("Notch"), "b50ad385829d3141a2167e7d7539ba7f");
    }
}
