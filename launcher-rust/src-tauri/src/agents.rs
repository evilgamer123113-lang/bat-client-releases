use crate::downloads as net;
use std::{fs, path::Path};
fn valid(path: &Path) -> bool {
    fs::File::open(path)
        .ok()
        .and_then(|f| zip::ZipArchive::new(f).ok())
        .is_some()
}
pub fn ensure(root: &Path, progress: crate::game_install::Progress) -> Result<(), String> {
    let path = root.join("authlib-injector.jar");
    if path.exists() {
        if valid(&path) {
            return Ok(());
        }
        return Err("Existing offline skin agent is damaged; preserved for inspection".into());
    }
    progress("Preparing the verified offline skin agent…", None);
    let http = net::client()?;
    let meta = net::json(
        &http,
        "https://authlib-injector.yushi.moe/artifact/latest.json",
    )?;
    net::file(
        &http,
        meta["download_url"]
            .as_str()
            .ok_or("Skin agent URL missing")?,
        &path,
        meta["checksums"]["sha256"]
            .as_str()
            .ok_or("Skin agent checksum missing")?,
        None,
        16 * 1024 * 1024,
        |_, _| {},
    )?;
    if !valid(&path) {
        return Err("Downloaded offline skin agent is not a valid JAR".into());
    }
    Ok(())
}
// Keep the launcher-owned agent with the child process files. The shared Electron
// copy may be older, and the Rust launcher should not replace it.
pub fn stage_bat(dir: &Path) -> Result<std::path::PathBuf, String> {
    let path = dir.join("bat-cosmetics.jar");
    fs::write(&path, include_bytes!("../resources/bat-cosmetics.jar"))
        .map_err(|_| "Cannot stage the bundled Bat agent")?;
    Ok(path)
}

#[cfg(test)]
mod tests {
    #[test]
    fn bundled_agent_has_premain() {
        let mut jar = zip::ZipArchive::new(std::io::Cursor::new(include_bytes!(
            "../resources/bat-cosmetics.jar"
        )))
        .unwrap();
        let mut entry = jar.by_name("META-INF/MANIFEST.MF").unwrap();
        let mut text = String::new();
        std::io::Read::read_to_string(&mut entry, &mut text).unwrap();
        assert!(text.contains("Premain-Class: com.batclient.cosmetics.AgentMain"));
    }
}
