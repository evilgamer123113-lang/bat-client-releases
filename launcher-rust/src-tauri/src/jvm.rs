use std::collections::HashMap;
pub fn split(text: &str) -> Result<Vec<String>, String> {
    let chars = text.chars().collect::<Vec<_>>();
    let mut tokens = vec![];
    let mut token = String::new();
    let mut quote = None;
    let mut started = false;
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        if c == '\\' && quote.is_some() && chars.get(i + 1) == quote.as_ref() {
            i += 1;
            token.push(chars[i]);
            started = true;
            i += 1;
            continue;
        }
        if c == '"' || c == '\'' {
            if quote == Some(c) {
                quote = None;
            } else if quote.is_none() {
                quote = Some(c);
            } else {
                token.push(c);
            }
            started = true;
        } else if c.is_whitespace() && quote.is_none() {
            if started {
                tokens.push(std::mem::take(&mut token));
                started = false;
            }
        } else {
            token.push(c);
            started = true;
        }
        i += 1;
    }
    if quote.is_some() {
        return Err("Custom JVM arguments contain an unclosed quote".into());
    }
    if started {
        tokens.push(token);
    }
    Ok(tokens)
}
pub fn validate(text: &str) -> Result<Vec<String>, String> {
    if text.len() > 8192 {
        return Err("Custom JVM arguments are too long".into());
    }
    let tokens = split(text)?;
    let paired = [
        "--add-opens",
        "--add-exports",
        "--add-reads",
        "--add-modules",
        "--limit-modules",
        "--patch-module",
        "--module-path",
        "-p",
        "--upgrade-module-path",
        "--enable-native-access",
    ];
    let forbidden = [
        "-cp",
        "-classpath",
        "--class-path",
        "-jar",
        "-m",
        "--module",
        "--source",
        "-version",
        "--version",
        "-help",
        "--help",
        "-h",
        "--dry-run",
        "-X",
    ];
    let mut i = 0;
    while i < tokens.len() {
        let token = &tokens[i];
        let name = token.split('=').next().unwrap_or("");
        if forbidden.contains(&name) {
            return Err("Classpath, main-class and exit options are managed by Bat Client".into());
        }
        if paired.contains(&token.as_str()) {
            if tokens
                .get(i + 1)
                .is_none_or(|v| v.is_empty() || v.starts_with('-'))
            {
                return Err("A custom JVM option is missing its value".into());
            }
            i += 2;
            continue;
        }
        let supported = token.strip_prefix("-D").is_some_and(|property| {
            let name = property.split('=').next().unwrap_or("");
            !name.is_empty() && !name.chars().any(char::is_whitespace)
        }) || ["-XX:", "-X", "-javaagent:", "-agentlib:", "-agentpath:"]
            .iter()
            .any(|prefix| token.starts_with(prefix) && token.len() > prefix.len())
            || [
                "-ea",
                "-da",
                "-esa",
                "-dsa",
                "-verbose",
                "-server",
                "-client",
                "--enable-preview",
            ]
            .contains(&token.as_str())
            || ["-ea:", "-da:", "-esa:", "-dsa:", "-verbose:"]
                .iter()
                .any(|prefix| token.starts_with(prefix))
            || paired
                .iter()
                .any(|prefix| token.starts_with(&format!("{prefix}=")));
        if !supported {
            return Err(
                "Unsupported custom JVM option; remove plain text, main classes and argument files"
                    .into(),
            );
        }
        i += 1;
    }
    Ok(tokens)
}
fn collector(arg: &str) -> Option<(&str, bool)> {
    let value = arg.strip_prefix("-XX:")?;
    let enabled = value.starts_with('+');
    if !enabled && !value.starts_with('-') {
        return None;
    }
    let name = &value[1..];
    [
        "UseSerialGC",
        "UseParallelGC",
        "UseParallelOldGC",
        "UseG1GC",
        "UseZGC",
        "UseShenandoahGC",
        "UseEpsilonGC",
        "UseConcMarkSweepGC",
    ]
    .contains(&name)
    .then_some((name, enabled))
}
// HotSpot checks experimental options as it reads the command line.
// Settings imported from other launchers can omit or misorder the unlock flag.
pub fn unlock_experimental_options(args: &mut Vec<String>) {
    let mut unlocked = false;
    let mut index = 0;
    while index < args.len() {
        let arg = &args[index];
        if arg == "-XX:+UnlockExperimentalVMOptions" {
            unlocked = true;
        } else if arg == "-XX:-UnlockExperimentalVMOptions" {
            unlocked = false;
        } else if !unlocked
            && (arg.starts_with("-XX:G1NewSizePercent=")
                || arg.starts_with("-XX:G1MaxNewSizePercent=")
                || arg == "-XX:+UseEpsilonGC")
        {
            args.insert(index, "-XX:+UnlockExperimentalVMOptions".into());
            unlocked = true;
            index += 1;
        }
        index += 1;
    }
}
// Presets supply defaults only; explicit global/profile collector choices still win.
pub fn preset(name: &str) -> Vec<String> {
    match name {
        "custom" => vec![],
        "low" | "low-end" => vec!["-XX:+UseSerialGC".into()],
        "high" | "high-end" => vec![
            "-XX:+UseG1GC".into(),
            "-XX:+ParallelRefProcEnabled".into(),
            "-XX:MaxGCPauseMillis=100".into(),
        ],
        _ => vec!["-XX:+UseG1GC".into(), "-XX:+ParallelRefProcEnabled".into()],
    }
}
// Port of the existing composition policy, ready for the native launch pipeline.
#[allow(dead_code)]
pub fn compose(
    defaults: &[String],
    global: &str,
    profile: &str,
    major: u32,
) -> Result<Vec<String>, String> {
    let groups = [validate(global)?, validate(profile)?];
    let mut selected = None;
    let mut custom = false;
    for group in &groups {
        let mut policy = HashMap::new();
        for arg in group {
            if let Some((name, on)) = collector(arg) {
                policy.insert(name.to_owned(), on);
            }
        }
        let enabled = policy
            .iter()
            .filter(|(_, on)| **on)
            .map(|(name, _)| name.clone())
            .collect::<Vec<_>>();
        if enabled.len() > 1 {
            return Err("Choose only one garbage collector in each custom JVM field".into());
        }
        if let Some(name) = enabled.first() {
            selected = Some(name.clone());
            custom = true;
        } else if !policy.is_empty() {
            custom = true;
            if selected
                .as_ref()
                .is_some_and(|name| policy.get(name) == Some(&false))
            {
                selected = None;
            }
        }
    }
    let mut result = defaults
        .iter()
        .filter(|arg| {
            !custom
                || collector(arg).is_none()
                    && (selected.as_deref() == Some("UseG1GC")
                        || ![
                            "-XX:G1",
                            "-XX:InitiatingHeapOccupancyPercent",
                            "-XX:UseStringDeduplication",
                            "-XX:+UseStringDeduplication",
                            "-Xmn",
                        ]
                        .iter()
                        .any(|p| arg.starts_with(p)))
        })
        .cloned()
        .collect::<Vec<_>>();
    if let Some(name) = &selected {
        result.push(format!("-XX:+{name}"));
    }
    for arg in groups.into_iter().flatten() {
        if let Some((name, on)) = collector(&arg) {
            if on || selected.as_deref() == Some(name) {
                continue;
            }
        }
        if major >= 11 && ["-XX:+AggressiveOpts", "-XX:-AggressiveOpts"].contains(&arg.as_str()) {
            continue;
        }
        result.push(arg);
    }
    Ok(result)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    #[ignore = "requires BAT_JVM_TEST_JAVA pointing to an installed Java executable"]
    fn experimental_flags_start_a_real_java_vm() {
        let java = std::env::var_os("BAT_JVM_TEST_JAVA").expect("Set BAT_JVM_TEST_JAVA");
        let defaults = vec!["-Xmx128m".into(), "-Xms32m".into(), "-XX:+UseG1GC".into()];
        let mut args = compose(
            &defaults,
            "-XX:G1NewSizePercent=30",
            "-XX:G1MaxNewSizePercent=60 -XX:+UnlockExperimentalVMOptions",
            21,
        ).unwrap();
        let broken = std::process::Command::new(&java).args(&args).arg("-version").output().unwrap();
        assert!(!broken.status.success(), "The unfixed command must reproduce the failure");
        assert!(String::from_utf8_lossy(&broken.stderr).contains("UnlockExperimentalVMOptions"));
        unlock_experimental_options(&mut args);
        let fixed = std::process::Command::new(&java).args(&args).arg("-version").output().unwrap();
        assert!(fixed.status.success(), "{}", String::from_utf8_lossy(&fixed.stderr));
    }
    #[test]
    fn experimental_unlock_precedes_flags_from_both_settings_fields() {
        let mut args = compose(
            &preset("balanced"),
            "-XX:G1NewSizePercent=30",
            "-XX:G1MaxNewSizePercent=60 -XX:+UnlockExperimentalVMOptions",
            21,
        ).unwrap();
        unlock_experimental_options(&mut args);
        let first_unlock = args.iter().position(|a| a == "-XX:+UnlockExperimentalVMOptions").unwrap();
        let first_g1_option = args.iter().position(|a| a.starts_with("-XX:G1NewSizePercent=")).unwrap();
        assert!(first_unlock < first_g1_option);
        assert!(args.contains(&"-XX:G1MaxNewSizePercent=60".into()));
        let once = args.clone();
        unlock_experimental_options(&mut args);
        assert_eq!(args, once);
    }
    #[test]
    fn experimental_unlock_preserves_order_and_reopens_after_explicit_lock() {
        let mut args = vec![
            "-XX:+UnlockExperimentalVMOptions".into(),
            "-XX:G1NewSizePercent=30".into(),
            "-XX:-UnlockExperimentalVMOptions".into(),
            "-javaagent:my-agent.jar".into(),
            "-XX:G1MaxNewSizePercent=60".into(),
        ];
        unlock_experimental_options(&mut args);
        assert_eq!(args[2], "-XX:-UnlockExperimentalVMOptions");
        assert_eq!(args[3], "-javaagent:my-agent.jar");
        assert_eq!(args[4], "-XX:+UnlockExperimentalVMOptions");
        assert_eq!(args[5], "-XX:G1MaxNewSizePercent=60");
        let mut regular = preset("high");
        let original = regular.clone();
        unlock_experimental_options(&mut regular);
        assert_eq!(regular, original);
    }
    #[test]
    fn presets_respect_custom_collector_and_agents() {
        let args = compose(&preset("high"), "-XX:+UseZGC", "-javaagent:custom.jar", 21).unwrap();
        assert!(!args.iter().any(|s| s.contains("UseG1GC")));
        assert!(args.contains(&"-XX:+UseZGC".into()));
        assert!(args.contains(&"-javaagent:custom.jar".into()));
        assert!(preset("custom").is_empty());
        assert_eq!(preset("low"), vec!["-XX:+UseSerialGC"]);
    }
    #[test]
    fn preserves_agents_and_quoted_windows_paths() {
        assert_eq!(
            validate(r#"-javaagent:"D:\Bat Client\agent.jar" -Dlabel="two words""#).unwrap(),
            vec![r"-javaagent:D:\Bat Client\agent.jar", "-Dlabel=two words"]
        );
        assert!(validate("-Dtest='unclosed").is_err());
        assert!(validate("-cp secret main.Class").is_err());
        assert!(validate("@args.txt").is_err());
    }
    #[test]
    fn profile_collector_overrides_global_without_g1_tuning() {
        let defaults = vec![
            "-XX:+UseG1GC".into(),
            "-XX:G1HeapRegionSize=8M".into(),
            "-Dkeep=true".into(),
        ];
        let args = compose(
            &defaults,
            "-XX:+UseG1GC -Dglobal=1",
            "-XX:+UseZGC -javaagent:my.jar",
            21,
        )
        .unwrap();
        assert!(!args.iter().any(|a| a.contains("G1")));
        assert!(args.contains(&"-XX:+UseZGC".into()));
        assert!(args.contains(&"-javaagent:my.jar".into()));
        assert!(compose(&[], "-XX:+UseG1GC -XX:+UseZGC", "", 21).is_err());
    }
}
