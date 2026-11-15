use serde::Serialize;
use std::{
    fs,
    io::{Read, Seek, SeekFrom},
    path::Path,
};
#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Failure {
    pub id: String,
    pub code: String,
    pub title: String,
    pub detail: String,
    pub remedies: Vec<String>,
    pub log_name: Option<String>,
    pub excerpt: String,
    pub exit_code: Option<i32>,
}
pub fn redact(text: &str) -> String {
    let quoted=regex::Regex::new(r#"(?i)("(?:access[_-]?token|refresh[_-]?token|client[_-]?(?:secret|token)|password|authorization|x-bat-token)"\s*:\s*)"[^"]*""#).unwrap();
    let text = quoted.replace_all(text, "$1\"[REDACTED]\"");
    let tokens=regex::Regex::new(r#"(?i)((?:--)?(?:access[_-]?token|refresh[_-]?token|client[_-]?(?:secret|token)|password|authorization|x-bat-token))([\s"':=]+)(?:Bearer\s+)?[^\s,"}]+"#).unwrap();
    let text = tokens.replace_all(&text, "$1$2[REDACTED]");
    let bearer = regex::Regex::new(r"(?i)Bearer\s+[A-Za-z0-9._~+/=-]+").unwrap();
    bearer
        .replace_all(&text, "Bearer [REDACTED]")
        .chars()
        .take(65536)
        .collect()
}
pub fn tail(path: &Path) -> Result<String, String> {
    let mut file = fs::File::open(path).map_err(|_| "Cannot read this launch log")?;
    let size = file
        .metadata()
        .map_err(|_| "Cannot inspect launch log")?
        .len();
    let start = size.saturating_sub(65536);
    file.seek(SeekFrom::Start(start))
        .map_err(|_| "Cannot read launch log")?;
    let mut bytes = vec![];
    file.take(65536)
        .read_to_end(&mut bytes)
        .map_err(|_| "Cannot read launch log")?;
    if start > 0 {
        if let Some(end) = bytes.iter().position(|b| *b == b'\n') {
            bytes.drain(..=end);
        } else {
            bytes.clear();
        }
    }
    Ok(redact(&String::from_utf8_lossy(&bytes)))
}
pub fn classify(
    message: &str,
    excerpt: &str,
    exit_code: Option<i32>,
    log_name: Option<String>,
) -> Failure {
    let lower = format!("{message}\n{excerpt}").to_lowercase();
    let (code, title, remedies): (&str, &str, Vec<&str>) = if lower.contains("checksum")
        || lower.contains("signature")
    {
        (
            "INTEGRITY",
            "A downloaded file could not be verified",
            vec![
                "Retry to download and verify the file again.",
                "Check available disk space and your connection. Verification stays enabled.",
            ],
        )
    } else if lower.contains("incompatible mods")
        || lower.contains("disable it in mods before launching")
        || lower.contains("requires version")
        || lower.contains("mixin apply failed")
        || lower.contains("mod resolution")
    {
        ("MOD_CONFLICT","Minecraft could not load its mods",vec!["Open Mods and check that each mod matches this loadout’s Minecraft version and loader.","Disable the conflicting mod or add the exact missing dependency reported in the log."])
    } else if lower.contains("unsupportedclassversionerror")
        || lower.contains("java runtime")
        || lower.contains("required java")
        || lower.contains("no compatible java")
    {
        (
            "JAVA_VERSION",
            "The Java version does not match this loadout",
            vec![
                "Open Settings → Performance and check the selected Java runtime.",
                "Install the required Java version or clear an incompatible custom Java path.",
            ],
        )
    } else if lower.contains("outofmemoryerror")
        || lower.contains("could not reserve enough space")
        || lower.contains("native memory allocation")
    {
        ("MEMORY","Minecraft could not get enough memory",vec!["Close memory-heavy apps and check the loadout’s RAM allocation.","Use a heap that fits your available memory; very high allocations can also prevent startup."])
    } else if lower.contains("unrecognized vm option")
        || lower.contains("improperly specified vm option")
        || lower.contains("invalid maximum heap")
        || lower.contains("could not create the java virtual machine")
    {
        (
            "JVM_ARGUMENTS",
            "Java rejected the launch settings",
            vec![
                "Check custom JVM arguments in Settings and this loadout’s editor.",
                "Remove the unsupported option shown in the log, then retry.",
            ],
        )
    } else if lower.contains("opengl")
        || lower.contains("glfw error")
        || lower.contains("pixel format")
    {
        ("GRAPHICS","Minecraft could not initialize graphics",vec!["Check that your GPU driver supports this Minecraft version.","If a renderer mod is unsupported on this PC, disable it in Mods. Your choice will be preserved."])
    } else if lower.contains("unsatisfiedlinkerror")
        || lower.contains("native library")
        || lower.contains("could not find or load main class")
        || lower.contains("classnotfoundexception")
        || lower.contains("noclassdeffounderror")
    {
        (
            "LIBRARIES",
            "Minecraft could not load a required component",
            vec![
                "Retry so the launcher can verify the required game libraries.",
                "Check the log for a missing native library or mod dependency.",
            ],
        )
    } else if lower.contains("sign in")
        || lower.contains("microsoft")
        || lower.contains("session expired")
    {
        (
            "ACCOUNT",
            "The selected account needs attention",
            vec![
                "Open Accounts and sign in to the Microsoft account again.",
                "Then select the account and retry this loadout.",
            ],
        )
    } else if lower.contains("download")
        || lower.contains("connection")
        || lower.contains("dns")
        || lower.contains("timed out")
        || lower.contains("http")
    {
        (
            "NETWORK",
            "Minecraft files could not be downloaded",
            vec![
                "Check your connection and retry. Verified files will be reused.",
                "If the service is temporarily unavailable, try again later.",
            ],
        )
    } else if lower.contains("still being migrated") || lower.contains("unsupported loader") {
        (
            "LOADER",
            "This loader is not available in the Rust launcher yet",
            vec![
                "Choose a supported Vanilla, Fabric or Quilt loadout.",
                "Keep the Electron launcher available for loaders still being migrated.",
            ],
        )
    } else {
        (
            "LAUNCH_FAILED",
            "Minecraft did not finish launching",
            vec![
                "Inspect the launch log below for the first reported error.",
                "Check the loadout’s Java, JVM arguments and installed mods, then retry.",
            ],
        )
    };
    Failure {
        id: uuid::Uuid::new_v4().to_string(),
        code: code.into(),
        title: title.into(),
        detail: redact(message),
        remedies: remedies.into_iter().map(str::to_owned).collect(),
        log_name,
        excerpt: redact(excerpt),
        exit_code,
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn classifies_real_startup_errors() {
        for (text, code) in [
            ("Unrecognized VM option 'bad'", "JVM_ARGUMENTS"),
            ("Incompatible mods found!", "MOD_CONFLICT"),
            ("java.lang.UnsupportedClassVersionError", "JAVA_VERSION"),
            ("GLFW error 65542 OpenGL", "GRAPHICS"),
            ("java.lang.OutOfMemoryError", "MEMORY"),
            (
                "Could not reserve enough space; Could not create the Java Virtual Machine",
                "MEMORY",
            ),
            ("File checksum mismatch", "INTEGRITY"),
        ] {
            assert_eq!(classify(text, "", Some(1), None).code, code);
        }
    }
    #[test]
    fn credentials_are_redacted_before_display_or_copy() {
        let text = r#"--accessToken SECRET1 Authorization: Bearer SECRET2 {"refresh_token":"SECRET3 with spaces","password":"SECRET4"} X-Bat-Token=SECRET5"#;
        let out = redact(text);
        for secret in ["SECRET1", "SECRET2", "SECRET3", "SECRET4", "SECRET5"] {
            assert!(!out.contains(secret), "{out}");
        }
    }
    #[test]
    fn tail_is_bounded_and_drops_partial_first_line() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("game.log");
        fs::write(
            &path,
            format!("{}\nUnrecognized VM option\n", "s".repeat(70000)),
        )
        .unwrap();
        let text = tail(&path).unwrap();
        assert!(!text.contains("ssss"));
        assert!(text.contains("Unrecognized VM option"));
        assert!(text.len() < 65536);
    }
    #[test]
    #[ignore = "Runs a real Java child with a deliberately rejected JVM argument"]
    fn native_rejected_jvm_option() {
        let java =
            Path::new("C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot/bin/java.exe");
        let mut command = std::process::Command::new(java);
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            command.creation_flags(0x08000000);
        }
        let output = command
            .args(["-XX:BatClientInvalidTestFlag=1", "-version"])
            .output()
            .unwrap();
        assert!(!output.status.success());
        let failure = classify(
            "Java stopped before opening Minecraft",
            &String::from_utf8_lossy(&output.stderr),
            output.status.code(),
            None,
        );
        assert_eq!(failure.code, "JVM_ARGUMENTS");
        println!("NATIVE_JVM_FAILURE_DIAGNOSIS_PASS");
    }
}
