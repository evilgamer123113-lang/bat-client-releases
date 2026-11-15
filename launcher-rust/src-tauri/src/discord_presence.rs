//! Optional Discord desktop IPC; never participates in the Minecraft render loop.
use discord_rich_presence::{activity, DiscordIpc, DiscordIpcClient};
use std::{
    sync::{
        atomic::{AtomicBool, Ordering},
        Mutex,
    },
    time::Duration,
};
static STARTED: AtomicBool = AtomicBool::new(false);
static LAUNCHER_PAGE: Mutex<&str> = Mutex::new("In Launcher");

pub(crate) fn set_page(page: &str) -> Result<(), String> {
    let label = match page {
        "home" => "In Launcher",
        "loadouts" => "In Launcher · Loadouts",
        "mods" => "In Launcher · Mods",
        "accounts" => "In Launcher · Accounts",
        "wardrobe" => "In Launcher · Wardrobe",
        "settings" => "In Launcher · Settings",
        _ => return Err("Unknown launcher page".into()),
    };
    *LAUNCHER_PAGE
        .lock()
        .map_err(|_| "Discord presence unavailable")? = label;
    Ok(())
}
fn description(stage: &str) -> (&'static str, &'static str) {
    match stage {
        "running" => ("Playing Minecraft", "In Game"),
        "preparing" | "starting" => ("Starting Minecraft", "In Launcher"),
        _ => ("Idle", "In Launcher"),
    }
}
pub fn start() {
    if STARTED.swap(true, Ordering::SeqCst) {
        return;
    }
    std::thread::spawn(|| {
        let mut client = DiscordIpcClient::new("1524782535089979482");
        let mut connected = false;
        let mut previous = String::new();
        let mut retry = std::time::Instant::now();
        loop {
            if !crate::runtime::preference("discordRPC") {
                if connected {
                    let _ = client.clear_activity();
                    let _ = client.close();
                    connected = false;
                    previous.clear();
                }
                std::thread::sleep(Duration::from_secs(2));
                continue;
            }
            if !connected {
                if retry.elapsed() < Duration::from_secs(15) && !previous.is_empty() {
                    std::thread::sleep(Duration::from_secs(2));
                    continue;
                }
                connected = client.connect().is_ok();
                retry = std::time::Instant::now();
                if !connected {
                    previous = "retry".into();
                    std::thread::sleep(Duration::from_secs(2));
                    continue;
                }
                previous.clear();
            }
            let stage = crate::game_launch::game_status()
                .map(|s| s.stage)
                .unwrap_or_default();
            let (details, mut state) = description(&stage);
            if details == "Idle" {
                state = LAUNCHER_PAGE
                    .lock()
                    .map(|page| *page)
                    .unwrap_or("In Launcher");
            }
            let key = format!("{stage}:{state}");
            if key != previous {
                let presence = activity::Activity::new()
                    .details(details)
                    .state(state)
                    .assets(
                        activity::Assets::new()
                            .large_image("bat-logo")
                            .large_text("Bat Client"),
                    )
                    .buttons(vec![
                        activity::Button::new("Join Discord", "https://batclient.xyz/discord"),
                        activity::Button::new("Get Client", "https://batclient.xyz/"),
                    ]);
                if client.set_activity(presence).is_err() {
                    let _ = client.close();
                    connected = false;
                    previous = "retry".into();
                    retry = std::time::Instant::now();
                } else {
                    previous = key;
                }
            }
            std::thread::sleep(Duration::from_secs(2));
        }
    });
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn state_policy_uses_no_identity_or_private_launch_data() {
        assert_eq!(description("running"), ("Playing Minecraft", "In Game"));
        assert_eq!(description("failed"), ("Idle", "In Launcher"));
        assert_eq!(
            description("preparing"),
            ("Starting Minecraft", "In Launcher")
        );
    }
}
