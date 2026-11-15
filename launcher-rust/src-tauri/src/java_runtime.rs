use crate::library as lib;
use serde::Serialize;
use serde_json::Value;
use std::{
    collections::HashSet,
    env, fs,
    path::{Path, PathBuf},
    process::{Command, Stdio},
    thread,
    time::{Duration, Instant},
};
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaStatus {
    pub required_major: u32,
    pub found: bool,
    pub major: Option<u32>,
    pub version: Option<String>,
    pub executable: Option<String>,
}
fn java_major(output: &str) -> Option<u32> {
    for line in output.lines() {
        let lower = line.to_lowercase();
        if !lower.contains("java") && !lower.contains("openjdk") {
            continue;
        }
        for word in line.split_whitespace().skip(1) {
            let cleaned = word.trim_matches(['\'', '"']);
            if !cleaned.starts_with(|c: char| c.is_ascii_digit()) {
                continue;
            }
            let mut digits = cleaned.split(|c: char| !c.is_ascii_digit());
            let first = digits.next()?.parse::<u32>().ok()?;
            return if first == 1 {
                digits.next()?.parse().ok()
            } else {
                Some(first)
            };
        }
    }
    None
}
fn normalize(path: PathBuf) -> PathBuf {
    let name = if cfg!(windows) { "java.exe" } else { "java" };
    if path.is_dir() {
        return path.join("bin").join(name);
    }
    if cfg!(windows)
        && path
            .file_name()
            .is_some_and(|p| p.to_string_lossy().eq_ignore_ascii_case("javaw.exe"))
    {
        return path.with_file_name("java.exe");
    }
    path
}
pub(crate) fn probe(path: &Path) -> Option<(u32, String)> {
    let mut command = Command::new(path);
    command
        .arg("-version")
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    let mut child = command.spawn().ok()?;
    let start = Instant::now();
    loop {
        match child.try_wait() {
            Ok(Some(status)) => {
                if !status.success() {
                    return None;
                }
                break;
            }
            Ok(None) if start.elapsed() < Duration::from_secs(5) => {
                thread::sleep(Duration::from_millis(25))
            }
            _ => {
                let _ = child.kill();
                let _ = child.wait();
                return None;
            }
        }
    }
    let output = child.wait_with_output().ok()?;
    if output.stdout.len() + output.stderr.len() > 128 * 1024 {
        return None;
    }
    let text = format!(
        "{}\n{}",
        String::from_utf8_lossy(&output.stderr),
        String::from_utf8_lossy(&output.stdout)
    );
    Some((
        java_major(&text)?,
        text.lines()
            .find(|s| !s.trim().is_empty())?
            .chars()
            .take(180)
            .collect(),
    ))
}
pub(crate) fn required_major(root: &Path, version: &str) -> Result<u32, String> {
    if !lib::safe_filename(version) {
        return Err("Invalid Minecraft version".into());
    }
    let manifest = lib::game_dir(root)?
        .join("versions")
        .join(version)
        .join(format!("{version}.json"));
    if manifest.exists() {
        let json = lib::read_json(&manifest, Value::Null)?;
        if let Some(n) = json
            .pointer("/javaVersion/majorVersion")
            .and_then(Value::as_u64)
        {
            if (8..=100).contains(&n) {
                return Ok(n as u32);
            }
        }
    }
    let parts: Vec<u32> = version.split('.').filter_map(|p| p.parse().ok()).collect();
    if parts.first() != Some(&1) || parts.len() < 2 {
        return Err("Cannot determine required Java version from metadata".into());
    }
    Ok(match (parts[1], parts.get(2).copied().unwrap_or(0)) {
        (21.., _) | (20, 5..) => 21,
        (18.., _) => 17,
        (17, _) => 16,
        _ => 8,
    })
}
fn candidates(root: &Path, major: u32, custom: Option<&str>) -> Vec<PathBuf> {
    let mut paths = vec![normalize(
        root.join("runtimes").join(format!("java-{major}")),
    )];
    if let Some(custom) = custom.filter(|s| !s.trim().is_empty()) {
        paths.push(normalize(PathBuf::from(custom.trim().trim_matches('"'))));
    }
    paths.push(normalize(root.join(if major == 21 {
        "jre".into()
    } else {
        format!("jre-{major}")
    })));
    for key in ["JAVA_HOME", "JDK_HOME"] {
        if let Some(home) = env::var_os(key) {
            paths.push(normalize(PathBuf::from(home)));
        }
    }
    paths.push(PathBuf::from(if cfg!(windows) {
        "java.exe"
    } else {
        "java"
    }));
    let mut roots = vec![];
    if cfg!(windows) {
        for key in ["ProgramFiles", "ProgramFiles(x86)"] {
            if let Some(base) = env::var_os(key) {
                for vendor in ["Java", "Eclipse Adoptium", "Microsoft", "Zulu", "BellSoft"] {
                    roots.push(PathBuf::from(&base).join(vendor));
                }
            }
        }
    } else if cfg!(target_os = "macos") {
        roots.push(PathBuf::from("/Library/Java/JavaVirtualMachines"));
        if let Some(home) = dirs::home_dir() {
            roots.push(home.join("Library/Java/JavaVirtualMachines"));
        }
    } else {
        roots.extend(["/usr/lib/jvm", "/usr/java", "/opt/java"].map(PathBuf::from));
    }
    for base in roots {
        if let Ok(entries) = fs::read_dir(base) {
            for entry in entries.flatten().take(50) {
                let path = if cfg!(target_os = "macos") {
                    entry.path().join("Contents/Home")
                } else {
                    entry.path()
                };
                if path.is_dir() {
                    paths.push(normalize(path));
                }
            }
        }
    }
    let mut seen = HashSet::new();
    paths.retain(|p| seen.insert(p.to_string_lossy().to_lowercase()));
    paths
}
pub fn detect_at(root: &Path, profile_id: &str) -> Result<JavaStatus, String> {
    let config = lib::config(root)?;
    let profiles = config
        .get("profiles")
        .and_then(Value::as_array)
        .ok_or("No loadouts found")?;
    let profile = profiles
        .iter()
        .find(|p| crate::id(p.get("id")) == profile_id)
        .ok_or("Select a loadout first")?;
    let version = profile
        .get("version")
        .and_then(Value::as_str)
        .ok_or("Loadout has no Minecraft version")?;
    let required = required_major(root, version)?;
    Ok(find_runtime(
        root,
        required,
        config
            .pointer("/appSettings/javaPath")
            .and_then(Value::as_str),
        &mut Default::default(),
    ))
}

fn find_runtime(
    root: &Path,
    required: u32,
    custom: Option<&str>,
    probes: &mut std::collections::HashMap<PathBuf, Option<(u32, String)>>,
) -> JavaStatus {
    let mut status = JavaStatus {
        required_major: required,
        found: false,
        major: None,
        version: None,
        executable: None,
    };
    for path in candidates(root, required, custom) {
        if let Some((major, version)) = probes.entry(path.clone()).or_insert_with(|| probe(&path)) {
            if *major == required {
                status.found = true;
                status.major = Some(*major);
                status.version = Some(version.clone());
                status.executable = Some(path.to_string_lossy().into_owned());
                break;
            }
        }
    }
    status
}

pub(crate) fn inventory_at(root: &Path) -> Result<Vec<JavaStatus>, String> {
    let config = lib::config(root)?;
    let custom = config
        .pointer("/appSettings/javaPath")
        .and_then(Value::as_str);
    let mut probes = std::collections::HashMap::new();
    Ok([8, 11, 16, 17, 21]
        .into_iter()
        .map(|major| find_runtime(root, major, custom, &mut probes))
        .collect())
}
#[tauri::command]
pub async fn detect_java(profile_id: String) -> Result<JavaStatus, String> {
    tauri::async_runtime::spawn_blocking(move || detect_at(&lib::root()?, &profile_id))
        .await
        .map_err(|_| "Java discovery failed")?
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn recognizes_legacy_and_modern_java() {
        assert_eq!(java_major("java version \"1.8.0_442\""), Some(8));
        assert_eq!(
            java_major("openjdk version \"21.0.6\" 2025-01-21"),
            Some(21)
        );
        assert_eq!(java_major("openjdk 17.0.14 2025-01-21"), Some(17));
        assert_eq!(java_major("unknown"), None);
    }
    #[test]
    fn manifest_java_requirement_overrides_fallback() {
        let root = tempfile::tempdir().unwrap();
        let dir = root.path().join("minecraft_runtime/versions/1.21.1");
        fs::create_dir_all(&dir).unwrap();
        fs::write(
            dir.join("1.21.1.json"),
            r#"{"javaVersion":{"majorVersion":25}}"#,
        )
        .unwrap();
        assert_eq!(required_major(root.path(), "1.21.1").unwrap(), 25);
        assert_eq!(required_major(root.path(), "1.20.4").unwrap(), 17);
        assert_eq!(required_major(root.path(), "1.20.6").unwrap(), 21);
        assert_eq!(required_major(root.path(), "1.8.9").unwrap(), 8);
    }
    #[test]
    #[ignore = "Requires an actual Java executable on PATH"]
    fn native_installed_java_probe() {
        let path = PathBuf::from(if cfg!(windows) { "java.exe" } else { "java" });
        let (major, _) = probe(&path).expect("Java on PATH must respond to a real -version probe");
        assert!((8..100).contains(&major));
    }
}
