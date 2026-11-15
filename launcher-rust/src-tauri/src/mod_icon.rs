use base64::{engine::general_purpose::STANDARD, Engine};
use serde_json::Value;
use std::{
    collections::{HashMap, VecDeque},
    fs::{self, File},
    io::{Read, Seek},
    path::{Path, PathBuf},
    sync::{Mutex, OnceLock},
    time::SystemTime,
};
const MAX_ICON: usize = 1024 * 1024;
#[derive(Clone, PartialEq)]
struct Signature {
    len: u64,
    modified: Option<SystemTime>,
    created: Option<SystemTime>,
}
#[derive(Default)]
struct Cache {
    entries: HashMap<PathBuf, (Signature, Option<String>)>,
    order: VecDeque<PathBuf>,
    bytes: usize,
}
static CACHE: OnceLock<Mutex<Cache>> = OnceLock::new();
fn entry<R: Read + Seek>(
    zip: &mut zip::ZipArchive<R>,
    name: &str,
    limit: usize,
) -> Option<Vec<u8>> {
    let name = name.replace('\\', "/");
    let name = name.strip_prefix("./").unwrap_or(&name);
    if name.starts_with('/') || name.split('/').any(|c| c == ".." || c.contains(':')) {
        return None;
    }
    let mut file = zip.by_name(name).ok()?;
    if file.is_dir() || file.size() > limit as u64 || file.compressed_size() > limit as u64 {
        return None;
    }
    let mut bytes = Vec::new();
    file.by_ref()
        .take(limit as u64 + 1)
        .read_to_end(&mut bytes)
        .ok()?;
    if bytes.len() > limit {
        return None;
    }
    Some(bytes)
}
fn icon_path(value: &Value) -> Option<String> {
    if let Some(s) = value.as_str() {
        return Some(s.into());
    }
    let mut sizes: Vec<_> = value
        .as_object()?
        .iter()
        .filter_map(|(key, value)| {
            Some((
                key.parse::<i32>().ok()?.abs_diff(128),
                value.as_str()?.to_owned(),
            ))
        })
        .collect();
    sizes.sort_by_key(|v| v.0);
    sizes.first().map(|v| v.1.clone())
}
fn mime(bytes: &[u8]) -> Option<&'static str> {
    if bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        Some("image/png")
    } else if bytes.starts_with(b"\xff\xd8\xff") {
        Some("image/jpeg")
    } else if bytes.starts_with(b"GIF87a") || bytes.starts_with(b"GIF89a") {
        Some("image/gif")
    } else if bytes.starts_with(b"RIFF") && bytes.get(8..12) == Some(b"WEBP") {
        Some("image/webp")
    } else {
        None
    }
}
fn load(path: &Path) -> Option<String> {
    let mut zip = zip::ZipArchive::new(File::open(path).ok()?).ok()?;
    if zip.len() > 40000 {
        return None;
    }
    let mut candidates = vec![];
    for name in ["fabric.mod.json", "quilt.mod.json"] {
        if let Some(data) = entry(&mut zip, name, 256 * 1024) {
            if let Ok(json) = serde_json::from_slice::<Value>(&data) {
                let metadata = json.pointer("/quilt_loader/metadata").unwrap_or(&json);
                if let Some(icon) = icon_path(&metadata["icon"]) {
                    candidates.push(icon);
                }
                let id = json
                    .get("id")
                    .or_else(|| json.pointer("/quilt_loader/id"))
                    .and_then(Value::as_str);
                if let Some(id) = id.filter(|s| {
                    !s.is_empty()
                        && s.chars()
                            .all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '-')
                }) {
                    candidates.push(format!("assets/{id}/icon.png"));
                    candidates.push(format!("assets/{id}/logo.png"));
                    if id == "batclient" {
                        candidates.push("assets/batclient/web/bat-logo.png".into());
                    }
                }
            }
        }
    }
    for name in ["META-INF/mods.toml", "META-INF/neoforge.mods.toml"] {
        if let Some(data) = entry(&mut zip, name, 256 * 1024) {
            if let Ok(text) = std::str::from_utf8(&data) {
                if let Ok(value) = text.parse::<toml::Value>() {
                    if let Some(s) = value.get("logoFile").and_then(toml::Value::as_str) {
                        candidates.push(s.into());
                    }
                    if let Some(mods) = value.get("mods").and_then(toml::Value::as_array) {
                        for metadata in mods {
                            if let Some(s) = metadata.get("logoFile").and_then(toml::Value::as_str)
                            {
                                candidates.push(s.into());
                            }
                        }
                    }
                }
            }
        }
    }
    if let Some(data) = entry(&mut zip, "mcmod.info", 256 * 1024) {
        if let Ok(value) = serde_json::from_slice::<Value>(&data) {
            let metadata = if value.is_array() {
                value.get(0)
            } else {
                value.pointer("/modList/0").or(Some(&value))
            };
            if let Some(s) = metadata
                .and_then(|v| v.get("logoFile"))
                .and_then(Value::as_str)
            {
                candidates.push(s.into());
            }
        }
    }
    candidates.extend(["icon.png".into(), "logo.png".into()]);
    for candidate in candidates {
        if let Some(bytes) = entry(&mut zip, &candidate, MAX_ICON) {
            if let Some(mime) = mime(&bytes) {
                return Some(format!("data:{mime};base64,{}", STANDARD.encode(bytes)));
            }
        }
    }
    None
}
pub fn read(path: &Path) -> Option<String> {
    let meta = fs::symlink_metadata(path).ok()?;
    if !meta.is_file() || meta.file_type().is_symlink() || meta.len() > 128 * 1024 * 1024 {
        return None;
    }
    let signature = Signature {
        len: meta.len(),
        modified: meta.modified().ok(),
        created: meta.created().ok(),
    };
    let cache = CACHE.get_or_init(|| Mutex::new(Cache::default()));
    if let Ok(cache) = cache.lock() {
        if let Some((old, result)) = cache.entries.get(path) {
            if old == &signature {
                return result.clone();
            }
        }
    }
    let result = load(path);
    if let Ok(mut cache) = cache.lock() {
        if let Some((_, old)) = cache.entries.remove(path) {
            cache.bytes = cache
                .bytes
                .saturating_sub(old.as_ref().map_or(0, String::len));
        }
        cache.order.retain(|p| p != path);
        cache.bytes += result.as_ref().map_or(0, String::len);
        cache.order.push_back(path.to_owned());
        cache
            .entries
            .insert(path.to_owned(), (signature, result.clone()));
        while cache.entries.len() > 128 || cache.bytes > 8 * 1024 * 1024 {
            if let Some(old) = cache.order.pop_front() {
                if let Some((_, result)) = cache.entries.remove(&old) {
                    cache.bytes = cache
                        .bytes
                        .saturating_sub(result.as_ref().map_or(0, String::len));
                }
            } else {
                break;
            }
        }
    }
    result
}
#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use zip::write::SimpleFileOptions;
    fn archive(path: &Path, metadata: &str, body: &[u8]) {
        let mut writer = zip::ZipWriter::new(File::create(path).unwrap());
        writer
            .start_file("fabric.mod.json", SimpleFileOptions::default())
            .unwrap();
        writer.write_all(metadata.as_bytes()).unwrap();
        writer
            .start_file("art/icon.png", SimpleFileOptions::default())
            .unwrap();
        writer.write_all(body).unwrap();
        writer.finish().unwrap();
    }
    #[test]
    fn reads_disabled_metadata_size_map_and_invalidates_cache() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("test.disabled");
        let png = include_bytes!("../../ui/assets/bat-logo.png");
        archive(
            &path,
            r#"{"id":"test","icon":{"32":"missing.png","128":"art/icon.png"}}"#,
            png,
        );
        assert!(read(&path).unwrap().starts_with("data:image/png;base64,"));
        archive(
            &path,
            r#"{"id":"test","icon":"art/icon.png"}"#,
            b"not an image",
        );
        assert!(read(&path).is_none());
    }
    #[test]
    fn corrupt_and_oversized_images_fall_back() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("test.jar");
        fs::write(&path, b"corrupt").unwrap();
        assert!(read(&path).is_none());
        archive(&path, r#"{"icon":"art/icon.png"}"#, &vec![0; MAX_ICON + 1]);
        assert!(read(&path).is_none());
    }
    #[test]
    fn rejects_archive_escape_and_active_content() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("test.jar");
        archive(
            &path,
            r#"{"icon":"../art/icon.png"}"#,
            b"<svg onload='alert(1)'/>",
        );
        assert!(read(&path).is_none());
        assert_eq!(mime(b"<svg/>"), None);
    }
}
