use crate::{game_files, library as lib, skin_bridge};
use serde::Serialize;
use serde_json::Value;
use std::{
    collections::HashMap,
    fs,
    io::{Read, Seek, SeekFrom},
    path::Path,
    process::{Command, Stdio},
    sync::{
        atomic::{AtomicBool, Ordering},
        Mutex,
    },
    thread,
    time::{SystemTime, UNIX_EPOCH},
};
use tauri::{Emitter, Manager};

static LOG_PATH: Mutex<Option<std::path::PathBuf>> = Mutex::new(None);
static ACTIVE: AtomicBool = AtomicBool::new(false);
static STATUS: Mutex<Status> = Mutex::new(Status {
    stage: String::new(),
    message: String::new(),
    pid: None,
    progress: None,
    failure: None,
});
#[derive(Clone, Serialize)]
pub struct Status {
    pub(crate) stage: String,
    message: String,
    pid: Option<u32>,
    progress: Option<f64>,
    failure: Option<crate::launch_errors::Failure>,
}
pub fn active() -> bool {
    ACTIVE.load(Ordering::SeqCst)
}
fn status(stage: &str, message: &str, pid: Option<u32>) {
    if let Ok(mut s) = STATUS.lock() {
        *s = Status {
            stage: stage.into(),
            message: message.into(),
            pid,
            progress: None,
            failure: None,
        };
    }
}
#[tauri::command]
pub fn game_status() -> Result<Status, String> {
    STATUS
        .lock()
        .map(|s| s.clone())
        .map_err(|_| "Game status unavailable".into())
}
fn failed(message: &str, path: Option<&Path>, exit_code: Option<i32>) {
    let excerpt = path
        .and_then(|p| crate::launch_errors::tail(p).ok())
        .unwrap_or_default();
    let name = path
        .and_then(|p| p.file_name())
        .map(|s| s.to_string_lossy().into_owned());
    let failure = crate::launch_errors::classify(message, &excerpt, exit_code, name);
    if let Ok(mut status) = STATUS.lock() {
        status.stage = "failed".into();
        status.message = failure.title.clone();
        status.pid = None;
        status.progress = None;
        status.failure = Some(failure.clone());
    }
    if let Ok(root) = lib::root() {
        if lib::config(&root).is_ok_and(|c| c["appSettings"]["crashReports"] == true) {
            if let Ok(value) = serde_json::to_value(&failure) {
                let _ = lib::atomic_json(
                    &root
                        .join("rust-launch-reports")
                        .join(format!("{}.json", failure.id)),
                    &value,
                );
            }
        }
    }
}
#[tauri::command]
pub async fn launch_diagnostics() -> Result<serde_json::Value, String> {
    tauri::async_runtime::spawn_blocking(||{let path=LOG_PATH.lock().map_err(|_|"Launch log unavailable")?.clone();let snapshot=game_status()?;let excerpt=path.as_deref().map(crate::launch_errors::tail).transpose()?.unwrap_or_default();Ok(serde_json::json!({"failure":snapshot.failure,"logName":path.and_then(|p|p.file_name().map(|s|s.to_string_lossy().into_owned())),"excerpt":excerpt}))}).await.map_err(|_|"Cannot read launch diagnostics".to_owned())?
}
struct ResetOnError;
impl Drop for ResetOnError {
    fn drop(&mut self) {
        ACTIVE.store(false, Ordering::SeqCst);
        if let Ok(mut s) = STATUS.lock() {
            if s.stage == "preparing" {
                s.stage = "failed".into();
                s.message =
                    "Minecraft preparation was interrupted. You can try launching again.".into();
            }
        }
    }
}

pub struct Plan {
    pub command: Command,
    _natives: tempfile::TempDir,
    _bridge: skin_bridge::Bridge,
    _companion_mod: Option<crate::companion_mod::StagedCompanionMod>,
}
pub(crate) fn java_path(path: &Path) -> String {
    let value = path.to_string_lossy();
    if let Some(unc) = value.strip_prefix(r"\\?\UNC\") {
        format!(r"\\{unc}")
    } else {
        value.strip_prefix(r"\\?\").unwrap_or(&value).to_owned()
    }
}
fn text(v: &Value, key: &str, fallback: &str) -> String {
    v[key].as_str().unwrap_or(fallback).to_owned()
}
fn offline_identity(account: &Value, username: &str) -> String {
    // Preserve the existing launcher's per-session identity policy.
    let equipped = account["skinFile"].as_str().is_some_and(|v| !v.is_empty())
        || account["customCape"]
            .as_str()
            .is_some_and(|v| !v.is_empty())
        || account["customSkins"].as_array().is_some_and(|slots| {
            slots
                .iter()
                .any(|s| s["file"].as_str().is_some_and(|v| !v.is_empty()))
        });
    if equipped {
        skin_bridge::offline_uuid(username)
    } else {
        "00000000000000000000000000000000".into()
    }
}
pub(crate) fn lock_game(root: &Path) -> Result<fs::File, String> {
    let path = root.join(".rust-game.lock");
    if fs::symlink_metadata(&path).is_ok_and(|m| !m.is_file() || m.file_type().is_symlink()) {
        return Err("Invalid native game lock file".into());
    }
    let file = fs::OpenOptions::new()
        .create(true)
        .truncate(false)
        .read(true)
        .write(true)
        .open(path)
        .map_err(|_| "Cannot acquire native game lock")?;
    fs2::FileExt::try_lock_exclusive(&file)
        .map_err(|_| "Minecraft is already starting or running in another Rust launcher window")?;
    Ok(file)
}
pub fn prepare(root: &Path, profile_id: &str) -> Result<Plan, String> {
    prepare_for(root, profile_id, None)
}
fn prepare_for(
    root: &Path,
    profile_id: &str,
    run_directory: Option<&Path>,
) -> Result<Plan, String> {
    let config = lib::config(root)?;
    let profile = config["profiles"]
        .as_array()
        .and_then(|p| p.iter().find(|p| crate::id(p.get("id")) == profile_id))
        .ok_or("Select a saved loadout")?;
    let username = config["activeUsername"]
        .as_str()
        .ok_or("Select an account")?;
    if !(3..=16).contains(&username.len())
        || !username
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '_')
    {
        return Err("Invalid Minecraft username".into());
    }
    let account = config["savedAccounts"]
        .as_array()
        .and_then(|a| a.iter().find(|a| a["username"].as_str() == Some(username)))
        .ok_or("Selected account no longer exists")?;
    let game = lib::game_dir(root)?;
    let run_directory = run_directory.unwrap_or(&game);
    let version = profile["version"]
        .as_str()
        .ok_or("Loadout has no version")?;
    let engine = profile["engine"].as_str().unwrap_or("vanilla");
    status("preparing", "Verifying installed Minecraft files…", None);
    let files = game_files::resolve(&game, version, engine)?;
    let java = crate::java_runtime::detect_at(root, profile_id)?;
    let executable=java.executable.ok_or_else(||format!("Java {} is required. Select a compatible installed Java in Settings; automatic installation is still being migrated.",java.required_major))?;
    let premium = account["type"] == "premium";
    let (id, access) = if premium {
        let key = account["rustAuthId"].as_str().ok_or(
            "Sign in with Microsoft once in this Rust launcher; your old session is preserved",
        )?;
        let tokens = crate::microsoft::read_tokens(key)?;
        if tokens.expires_at
            < SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs()
                + 30
        {
            return Err(
                "Microsoft session expired. Sign in again in Accounts before launching.".into(),
            );
        }
        (tokens.uuid, tokens.access_token)
    } else {
        (
            offline_identity(account, username),
            "bat_session_pass".into(),
        )
    };
    status(
        "preparing",
        "Preparing signed local textures and Bat agent…",
        None,
    );
    let bridge = skin_bridge::start(root, account, &id)?;
    let natives_root = root.join("rust-launch-cache/natives");
    fs::create_dir_all(&natives_root).map_err(|_| "Cannot create native library cache")?;
    let natives = tempfile::Builder::new()
        .prefix("game-")
        .tempdir_in(&natives_root)
        .map_err(|_| "Cannot stage native libraries")?;
    game_files::extract_natives(&files.native_jars, natives.path())?;
    let agent = crate::agents::stage_bat(natives.path())?;
    let cape_dir = natives.path().join("bat-cape");
    crate::cape_animation::stage_for_game(root, &cape_dir, username, account)?;
    let client_jar_name = files
        .client
        .file_name()
        .ok_or("Missing Minecraft client filename")?
        .to_string_lossy()
        .into_owned();
    let mut classpath = files.libraries;
    classpath.push(files.client);
    let classpath = std::env::join_paths(classpath.iter().map(|p| java_path(p)))
        .map_err(|_| "Invalid library classpath")?
        .into_string()
        .map_err(|_| "Unsupported library path encoding")?;
    let app_settings = &config["appSettings"];
    crate::optimization::apply(run_directory, version, app_settings)?;
    let num = |value: &Value, fallback: u64| {
        value
            .as_u64()
            .or_else(|| value.as_str().and_then(|v| v.parse().ok()))
            .unwrap_or(fallback)
    };
    let ram = if profile["ramOverride"].as_bool().unwrap_or(false) {
        num(&profile["ram"], 4)
    } else {
        num(&app_settings["defaultRam"], 4)
    };
    if !(1..=64).contains(&ram) {
        return Err("Invalid loadout memory setting".into());
    }
    let mut vars = HashMap::from([
        ("natives_directory".into(), java_path(natives.path())),
        ("launcher_name".into(), "Bat Client".into()),
        ("launcher_version".into(), env!("CARGO_PKG_VERSION").into()),
        ("classpath".into(), classpath),
        ("auth_player_name".into(), username.into()),
        ("auth_uuid".into(), id),
        ("auth_access_token".into(), access),
        ("auth_session".into(), "0".into()),
        ("user_type".into(), "msa".into()),
        (
            "user_properties".into(),
            if premium {
                "{}".into()
            } else {
                bridge.profile_properties.clone()
            },
        ),
        ("version_name".into(), files.version),
        (
            "version_type".into(),
            text(&files.manifest, "type", "release"),
        ),
        ("game_directory".into(), java_path(run_directory)),
        ("assets_root".into(), java_path(&game.join("assets"))),
        (
            "game_assets".into(),
            java_path(&game.join("assets/virtual/legacy")),
        ),
        ("assets_index_name".into(), files.asset_index),
        ("clientid".into(), "0".into()),
        ("auth_xuid".into(), "0".into()),
        (
            "resolution_width".into(),
            num(&app_settings["windowWidth"], 1150).to_string(),
        ),
        (
            "resolution_height".into(),
            num(&app_settings["windowHeight"], 680).to_string(),
        ),
    ]);
    vars.insert(
        "classpath_separator".into(),
        if cfg!(windows) { ";" } else { ":" }.into(),
    );
    vars.insert(
        "library_directory".into(),
        java_path(&game.join("libraries")),
    );
    let features = HashMap::from([
        ("has_custom_resolution".into(), true),
        ("is_demo_user".into(), false),
    ]);
    let mut args = if files.manifest["arguments"]["jvm"].is_array() {
        game_files::arguments(&files.manifest["arguments"]["jvm"], &features, &vars)?
    } else {
        vec![
            format!("-Djava.library.path={}", java_path(natives.path())),
            "-cp".into(),
            vars["classpath"].clone(),
        ]
    };
    if matches!(engine, "forge" | "neoforge") {
        for argument in &mut args {
            if let Some(list) = argument.strip_prefix("-DignoreList=") {
                if !list.split(',').any(|name| name == client_jar_name) {
                    argument.push(',');
                    argument.push_str(&client_jar_name);
                }
            }
        }
    }
    let mut defaults = vec![
        format!("-Xmx{ram}G"),
        "-Xms512M".into(),
        "-Dfile.encoding=UTF-8".into(),
    ];
    defaults.extend(crate::jvm::preset(&text(
        app_settings,
        "jvmPreset",
        "balanced",
    )));
    if java.required_major >= 9 {
        for package in [
            "java.lang",
            "java.util",
            "java.lang.invoke",
            "java.net",
            "java.io",
            "jdk.internal.loader",
        ] {
            defaults.extend([
                "--add-opens".into(),
                format!("java.base/{package}=ALL-UNNAMED"),
            ]);
        }
    }
    let mapping = if matches!(engine, "fabric" | "quilt") {
        "intermediary"
    } else {
        "official"
    };
    defaults.push(format!("-Dbatclient.cape.directory={}", java_path(&cape_dir)));
    defaults.push(format!("-Dbatclient.cape.player={username}"));
    defaults.push(format!(
        "-javaagent:{}={version},{mapping},{}",
        java_path(&agent),
        bridge.root
    ));
    if matches!(engine, "fabric" | "quilt") {
        defaults.push("-Dfabric.debug.disableClassPathIsolation=true".into());
    }
    if !premium {
        let authlib = lib::regular_file(root, "authlib-injector.jar").map_err(|_| {
            "The offline skin agent is missing. Install it using the existing launcher first."
        })?;
        defaults.push(format!(
            "-javaagent:{}={}",
            java_path(&authlib),
            bridge.root
        ));
    }
    args.extend(crate::jvm::compose(
        &defaults,
        &text(app_settings, "extraJvmArgs", ""),
        &text(profile, "extraJvmArgs", ""),
        java.required_major,
    )?);
    if let Some(log) = files.manifest["logging"]["client"].as_object() {
        if let Some(id) = log.get("file").and_then(|f| f["id"].as_str()) {
            let file = game_files::relative(&game.join("assets/log_configs"), id)?;
            if file.is_file() {
                if let Some(argument) = log.get("argument").and_then(Value::as_str) {
                    args.push(argument.replace("${path}", &java_path(&file)));
                }
            }
        }
    }
    crate::jvm::unlock_experimental_options(&mut args);
    args.push(
        files.manifest["mainClass"]
            .as_str()
            .filter(|s| !s.is_empty() && !s.chars().any(char::is_whitespace))
            .ok_or("Invalid Minecraft main class")?
            .into(),
    );
    let game_args = if files.manifest["arguments"]["game"].is_array() {
        game_files::arguments(&files.manifest["arguments"]["game"], &features, &vars)?
    } else {
        let values = crate::jvm::split(
            files.manifest["minecraftArguments"]
                .as_str()
                .ok_or("Missing Minecraft game arguments")?,
        )?;
        game_files::arguments(&serde_json::json!(values), &features, &vars)?
    };
    args.extend(game_args);
    if app_settings["fullscreen"].as_bool().unwrap_or(false) {
        args.push("--fullscreen".into());
    }
    let modern = version
        .split('.')
        .filter_map(|v| v.parse::<u32>().ok())
        .collect::<Vec<_>>();
    if !premium
        && modern.first() == Some(&1)
        && (modern.get(1).copied().unwrap_or(0) > 20
            || (modern.get(1) == Some(&20) && modern.get(2).copied().unwrap_or(0) >= 5))
    {
        args.extend([
            "--profileProperties".into(),
            bridge.profile_properties.clone(),
        ]);
    }
    let mut command = Command::new(executable);
    command
        .args(args)
        .current_dir(run_directory)
        .env("BAT_CLIENT_LAUNCHER_PID", std::process::id().to_string())
        .stdin(Stdio::null());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    Ok(Plan {
        command,
        _natives: natives,
        _bridge: bridge,
        _companion_mod: None,
    })
}
fn renderer_initialized(path: &Path) -> bool {
    let Ok(mut file) = fs::File::open(path) else {
        return false;
    };
    let Ok(size) = file.metadata().map(|m| m.len()) else {
        return false;
    };
    if file
        .seek(SeekFrom::Start(size.saturating_sub(65536)))
        .is_err()
    {
        return false;
    }
    let mut bytes = vec![];
    if file.take(65536).read_to_end(&mut bytes).is_err() {
        return false;
    }
    let text = String::from_utf8_lossy(&bytes);
    text.contains("Created:") || text.contains("OpenAL initialized")
}
#[tauri::command]
pub async fn launch_installed_game(
    app: tauri::AppHandle,
    profile_id: String,
    server: Option<String>,
) -> Result<Status, String> {
    if crate::updater::active() {
        return Err("Wait for the launcher update to finish".into());
    }
    let server_address = server.as_deref().map(crate::tools::server_address).transpose()?;
    if ACTIVE
        .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
        .is_err()
    {
        return Err("Minecraft is already starting or running".into());
    }
    status(
        "preparing",
        "Checking the selected session and installed Minecraft…",
        None,
    );
    if let Ok(mut path) = LOG_PATH.lock() {
        *path = None;
    }
    let restore_window = app.clone();
    let result=tauri::async_runtime::spawn_blocking(move|| -> Result<Status,String> {
        let guard=ResetOnError;
        crate::background_prepare::yield_to_foreground()?;
        let initial_root=lib::root()?;
        lib::ensure_legacy_launcher_closed()?;
        let initial=lib::config(&initial_root)?;
        if let Some(username)=initial["activeUsername"].as_str(){crate::microsoft::fresh_for_launch(&initial_root,username)?;}

        let _lock=lib::WRITE_LOCK.lock().map_err(|_|"Library busy")?;
        lib::ensure_legacy_launcher_closed()?;lib::ensure_game_closed()?;
        let root=lib::root()?;let game_lock=lock_game(&root)?;
        let config=lib::config(&root)?;
        let profile=config["profiles"].as_array().and_then(|p|p.iter().find(|p|crate::id(p.get("id"))==profile_id)).ok_or("Select a saved loadout")?;
        let launch_version=profile["version"].as_str().ok_or("Loadout has no version")?;
        let launch_engine=profile["engine"].as_str().unwrap_or("vanilla");
        crate::companion_mod::cleanup_stale_at(&root);
        crate::mod_compatibility::auto_fix_for_launch(&root,launch_version,launch_engine)?;
        if launch_engine.eq_ignore_ascii_case("fabric") {
            status("preparing","Checking Fabric API…",None);
            crate::mods::ensure_fabric_api(&root,&profile_id,launch_version)?;
        }
        crate::mod_compatibility::validate_launch(&root,launch_version,launch_engine)?;
        crate::game_install::ensure(&root,launch_version,launch_engine,&|message,progress|{
            status("preparing",message,None);if let Ok(mut s)=STATUS.lock(){s.progress=progress;}
        })?;
        let progress=|message:&str,progress:Option<f64>|{status("preparing",message,None);if let Ok(mut s)=STATUS.lock(){s.progress=progress;}};
        let java=crate::java_runtime::detect_at(&root,&profile_id)?;
        if !java.found {crate::java_install::install(&root,java.required_major,&progress)?;}
        let version_str=profile["version"].as_str().ok_or("Loadout has no version")?;
        let engine_str=profile["engine"].as_str().unwrap_or("vanilla");
        crate::agents::ensure(&root,&progress)?;
        let staged_companion=crate::companion_mod::stage_for_launch(&root,version_str,engine_str,&progress)?;
        if matches!(engine_str,"fabric"|"quilt"){crate::optional_renderer::apply(&root)?;}
        let mut plan=prepare(&root,&profile_id)?;
        plan._companion_mod=staged_companion;
        if let Some((host,port))=server_address {
            let version=profile["version"].as_str().unwrap_or("");
            let minor=version.split('.').nth(1).and_then(|v|v.parse::<u32>().ok()).unwrap_or(0);
            if minor>=20 {
                let destination=if host.contains(':') { format!("[{host}]:{port}") } else { format!("{host}:{port}") };
                plan.command.args(["--quickPlayMultiplayer",&destination]);
            } else {
                plan.command.args(["--server",&host,"--port",&port.to_string()]);
            }
        }

        let logs=root.join("rust-game-logs");fs::create_dir_all(&logs).map_err(|_|"Cannot create private game log directory")?;
        let log_path=logs.join(format!("game-{}.log",uuid::Uuid::new_v4()));
        if let Ok(mut current)=LOG_PATH.lock(){*current=Some(log_path.clone());}
        let log=fs::OpenOptions::new().create_new(true).write(true).open(&log_path).map_err(|_|"Cannot create game log")?;
        plan.command.stdout(log.try_clone().map_err(|_|"Cannot attach game output")?).stderr(log);
        let settings=lib::config(&root)?;
        let mut child=plan.command.spawn().map_err(|_|"Java could not start Minecraft. Check the selected Java installation and JVM arguments.")?;
        let pid=child.id();let _=crate::tools::record(&root,&profile_id,pid,server.as_deref());let _=app.emit("bat-game-event",serde_json::json!({"kind":"started","pid":pid}));status("starting","Java started. Opening Minecraft…",Some(pid));
        if let Some(window)=app.get_webview_window("main"){
            if settings["appSettings"]["showConsole"]==true{let _=window.show();}
            else if settings["appSettings"]["hideOnLaunch"]==true{let _=window.hide();}
            else if !settings["appSettings"]["keepLauncherOpen"].as_bool().unwrap_or(false){let _=window.minimize();}
        }
        std::mem::forget(guard);
        thread::spawn(move||{
            let mut opened=false;let started=std::time::Instant::now();let mut slow=false;let mut title_check=started;
            let exit=loop{
                match child.try_wait(){Ok(Some(exit))=>break Ok(exit),Err(error)=>break Err(error),Ok(None)=>{}}
                if !opened&&renderer_initialized(&log_path){opened=true;status("running","Minecraft is open with Bat’s Java agent.",Some(pid));}
                if opened&&title_check.elapsed()>=std::time::Duration::from_secs(2){if let Some(title)=settings["appSettings"]["gameWindowTitle"].as_str(){crate::window_title::apply(pid,title);}title_check=std::time::Instant::now();}
                if !opened&&!slow&&started.elapsed()>std::time::Duration::from_secs(60){slow=true;status("starting","Minecraft is taking longer to open. View the launch log for progress.",Some(pid));}
                thread::sleep(std::time::Duration::from_millis(500));
            };
            let exit_code=exit.as_ref().ok().and_then(|s|s.code());let ok=exit.as_ref().is_ok_and(|s|s.success())&&opened;
            if ok{status("ready","Minecraft closed.",None);}else{failed(if opened{"Minecraft exited with an error."}else{"Minecraft closed before startup completed."},Some(&log_path),exit_code);}
            drop(plan);drop(game_lock);ACTIVE.store(false,Ordering::SeqCst);
            crate::runtime::notification(&app,if ok{"Minecraft closed"}else{"Minecraft could not launch"},if ok{"Your game session finished."}else{"The launcher has details and recovery options."});
            let _=app.emit("bat-game-event",serde_json::json!({"kind":"closed","success":ok}));
            if ok&&settings["appSettings"]["autoCloseOnGameExit"]==true{app.exit(0);}else if let Some(window)=app.get_webview_window("main"){let _=window.show();let _=window.unminimize();if !ok{let _=window.set_focus();}}

        });
        Ok(Status{stage:"starting".into(),message:"Java started. Opening Minecraft…".into(),pid:Some(pid),progress:None,failure:None})
    }).await.map_err(|_|"Native launch preparation failed".to_owned())?;
    if let Err(error) = &result {
        ACTIVE.store(false, Ordering::SeqCst);
        failed(error, None, None);
        if let Some(window) = restore_window.get_webview_window("main") {
            let _ = window.show();
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn opening_status_waits_for_renderer_log() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("game.log");
        fs::write(&path, b"Java started\n").unwrap();
        assert!(!renderer_initialized(&path));
        fs::write(
            &path,
            b"[Render thread/INFO]: Created: 1024x512 minecraft:textures/atlas/blocks.png-atlas\n",
        )
        .unwrap();
        assert!(renderer_initialized(&path));
    }
    #[test]
    fn native_game_lock_is_exclusive_and_releases() {
        let root = tempfile::tempdir().unwrap();
        let first = lock_game(root.path()).unwrap();
        assert!(lock_game(root.path()).is_err());
        drop(first);
        assert!(lock_game(root.path()).is_ok());
    }
    #[test]
    fn preserves_electron_offline_session_identity_policy() {
        assert_eq!(
            offline_identity(&serde_json::json!({"username":"Player"}), "Player"),
            "00000000000000000000000000000000"
        );
        assert_eq!(
            offline_identity(&serde_json::json!({"skinFile":"skin.png"}), "Player"),
            skin_bridge::offline_uuid("Player")
        );
        assert_eq!(
            offline_identity(
                &serde_json::json!({"customSkins":[{"file":"slot.png"}]}),
                "Player"
            ),
            skin_bridge::offline_uuid("Player")
        );
    }
    #[test]
    fn java_agents_do_not_receive_windows_verbatim_prefixes() {
        assert_eq!(
            java_path(Path::new(r"\\?\D:\Bat Client\agent.jar")),
            r"D:\Bat Client\agent.jar"
        );
        assert_eq!(
            java_path(Path::new(r"\\?\UNC\server\share\agent.jar")),
            r"\\server\share\agent.jar"
        );
    }

    #[test]
    #[ignore = "User-requested interactive private combined session; waits for the player to close Minecraft"]
    fn native_combined_interactive() {
        lib::ensure_game_closed().unwrap();
        let base = fs::canonicalize("D:/BatClientBuildCache/mod-terrain-20261107/interactive-rust-combined").unwrap();
        let root = base.join("appdata/bat-client");
        let game = base.join("game");
        assert_eq!(fs::canonicalize(std::env::var("BAT_COMBINED_SESSION_ROOT").unwrap()).unwrap(), fs::canonicalize(&root).unwrap());
        let config = lib::config(&root).unwrap();
        assert_eq!(config["profiles"][0]["version"], "1.21.1");
        assert_eq!(config["activeUsername"], "BatComparison");
        assert!(game.join("mods/bat-client-mod-0.1.0.jar").is_file());
        assert!(game.join("saves/Comparison/level.dat").is_file());
        let mut plan = prepare_for(&root, "test", Some(&game)).unwrap();
        plan.command.env("APPDATA", root.parent().unwrap());
        let path = base.join("game.log");
        let log = fs::File::create(&path).unwrap();
        plan.command.stdout(log.try_clone().unwrap()).stderr(log);
        let mut child = plan.command.spawn().unwrap();
        println!("COMBINED_INTERACTIVE_PID={}; LOG={}", child.id(), path.display());
        // Keep native staging and the local skin bridge alive for the actual game.
        let exit = child.wait().unwrap();
        println!("COMBINED_INTERACTIVE_EXIT={exit}");
        assert!(exit.success(), "Inspect the private game.log");
    }

    #[test]
    #[ignore = "Opens only our official loader disposable fixture, with Bat agents and no user mods/worlds"]
    fn native_forge_launch() {
        lib::ensure_game_closed().unwrap();
        let root = fs::canonicalize(std::env::var("BAT_LOADER_TEST_ROOT").unwrap()).unwrap();
        let base = fs::canonicalize("D:/BatClientBuildCache").unwrap();
        assert_eq!(root.parent(), Some(base.as_path()));
        assert!(root
            .file_name()
            .unwrap()
            .to_string_lossy()
            .starts_with("rust-forge-install-"));
        let version = std::env::var("BAT_LOADER_TEST_VERSION").unwrap_or("1.21.1".into());
        let engine = std::env::var("BAT_LOADER_TEST_ENGINE").unwrap_or("forge".into());
        assert!(["forge", "neoforge", "fabric", "quilt"].contains(&engine.as_str()));
        let existing = lib::root().unwrap();
        for name in ["bat-cosmetics.jar", "authlib-injector.jar"] {
            fs::copy(lib::regular_file(&existing, name).unwrap(), root.join(name)).unwrap();
        }
        lib::edit_config(&root, |config| {
            config["activeUsername"] = serde_json::json!("BatLoaderTest");
            config["savedAccounts"] = serde_json::json!([{"username":"BatLoaderTest"}]);
            config["profiles"] =
                serde_json::json!([{"id":"test","version":version,"engine":engine}]);
            Ok(())
        })
        .unwrap();
        let java = crate::java_runtime::detect_at(&root, "test").unwrap();
        if !java.found {
            crate::java_install::install(&root, java.required_major, &|_, _| {}).unwrap();
        }
        let run = root.join(format!("run-{}", uuid::Uuid::new_v4()));
        fs::create_dir_all(&run).unwrap();
        let path = run.join("game.log");
        let log = fs::File::create(&path).unwrap();
        let mut plan = prepare_for(&root, "test", Some(&run)).unwrap();
        plan.command.stdout(log.try_clone().unwrap()).stderr(log);
        let mut child = plan.command.spawn().unwrap();
        let started = std::time::Instant::now();
        let mut ready = false;
        while started.elapsed() < std::time::Duration::from_secs(120) {
            if child.try_wait().unwrap().is_some() {
                break;
            }
            if renderer_initialized(&path) {
                ready = true;
                break;
            }
            thread::sleep(std::time::Duration::from_millis(500));
        }
        #[cfg(windows)]
        let title_applied = if ready {
            let limit = std::time::Instant::now();
            loop {
                if crate::window_title::apply(child.id(), "Bat native title test") {
                    break true;
                }
                if limit.elapsed() > std::time::Duration::from_secs(10) {
                    break false;
                }
                thread::sleep(std::time::Duration::from_millis(250));
            }
        } else {
            false
        };
        let _ = child.kill();
        let _ = child.wait();
        println!("LOADER_GAME_LOG={}", path.display());
        #[cfg(windows)]
        assert!(title_applied, "Owned game title was not applied");
        assert!(
            ready,
            "Official loader did not reach renderer initialization"
        );
        println!(
            "NATIVE_LOADER_RENDERER_AGENT_PASS {} {}ms",
            engine,
            started.elapsed().as_millis()
        );
    }
    #[test]
    #[ignore = "Opens an actual Minecraft test window using read-only installed caches and a disposable D: game directory"]
    fn native_cached_launch_smoke() {
        lib::ensure_game_closed().unwrap();
        let version = std::env::var("BAT_NATIVE_TEST_VERSION").unwrap_or_else(|_| "1.21.1".into());
        assert!(matches!(version.as_str(), "1.21" | "1.21.1"));
        let existing = lib::root().unwrap();
        let cache = lib::game_dir(&existing).unwrap();
        let base = tempfile::Builder::new()
            .prefix("native-rust-game-")
            .tempdir_in("D:/BatClientBuildCache")
            .unwrap()
            .keep();
        let root = base.join("appdata/bat-client");
        let game = base.join("game");
        fs::create_dir_all(root.join("skins")).unwrap();
        fs::create_dir_all(game.join("mods")).unwrap();
        for name in ["bat-cosmetics.jar", "authlib-injector.jar"] {
            fs::copy(lib::regular_file(&existing, name).unwrap(), root.join(name)).unwrap();
        }
        // Copy only existing JAR bytes; user worlds/config/options/accounts never enter the fixture.
        for entry in fs::read_dir(cache.join("mods")).unwrap().flatten() {
            let path = entry.path();
            if path.extension().and_then(|v| v.to_str()) == Some("jar")
                && entry.file_type().unwrap().is_file()
            {
                fs::copy(&path, game.join("mods").join(entry.file_name())).unwrap();
            }
        }
        fs::write(
            root.join("skins/test.png"),
            include_bytes!("../../ui/assets/steve.png"),
        )
        .unwrap();
        fs::write(root.join("profiles.config.json"),serde_json::json!({"activeUsername":"BatNativeTest","savedAccounts":[{"username":"BatNativeTest","skinFile":"test.png"}],"profiles":[{"id":"test","version":version,"engine":"fabric"}],"appSettings":{"gameDir":cache,"defaultRam":4,"javaPath":"C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot/bin/java.exe"}}).to_string()).unwrap();
        let mut plan = prepare_for(&root, "test", Some(&game)).unwrap();
        plan.command.env("APPDATA", root.parent().unwrap());
        let log_path = base.join("game.log");
        let log = fs::File::create(&log_path).unwrap();
        plan.command.stdout(log.try_clone().unwrap()).stderr(log);
        let mut child = plan.command.spawn().unwrap();
        let pid = child.id();
        println!("NATIVE_GAME_TEST_PID={pid}; EVIDENCE={}", base.display());
        let start = std::time::Instant::now();
        let mut ready = false;
        while start.elapsed() < std::time::Duration::from_secs(90) {
            if child.try_wait().unwrap().is_some() {
                break;
            }
            let text = fs::read_to_string(&log_path).unwrap_or_default();
            if text.contains("Created:") && text.contains("[BatCape] premain") {
                ready = true;
                break;
            }
            thread::sleep(std::time::Duration::from_millis(500));
        }
        // This is our disposable menu-only test process, never an existing user JVM.
        let _ = child.kill();
        let _ = child.wait();
        assert!(ready,"Native Minecraft test did not reach renderer texture initialization; inspect its private fixture log");
        println!(
            "NATIVE_GAME_RENDERER_AND_AGENT_PASS {}ms",
            start.elapsed().as_millis()
        );
    }
}
