//! Prepare shared vanilla files without launching Java or changing a loadout.
use crate::{game_install, game_launch, library};
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Mutex,
};

static GENERATION: AtomicU64 = AtomicU64::new(0);
static WORKER: Mutex<()> = Mutex::new(());

pub(crate) fn yield_to_foreground() -> Result<(), String> {
    GENERATION.fetch_add(1, Ordering::SeqCst);
    let _worker = WORKER
        .lock()
        .map_err(|_| "Minecraft preparation unavailable")?;
    Ok(())
}

#[tauri::command]
pub async fn prepare_selected_game(profile_id: String) -> Result<bool, String> {
    let generation = GENERATION.fetch_add(1, Ordering::SeqCst) + 1;
    tauri::async_runtime::spawn_blocking(move || {
        let cancelled = || {
            GENERATION.load(Ordering::SeqCst) != generation
                || game_launch::active()
                || crate::updater::active()
        };
        let _worker = WORKER
            .lock()
            .map_err(|_| "Minecraft preparation unavailable")?;
        if cancelled() {
            return Ok(false);
        }
        let root = library::root()?;
        library::ensure_legacy_launcher_closed()?;
        library::ensure_game_closed()?;
        let _game = game_launch::lock_game(&root)?;
        let (version, _) = library::profile(&root, &profile_id)?;
        let result = game_install::prefetch(&root, &version, &cancelled);
        if cancelled() {
            return Ok(false);
        }
        result.map(|_| true)
    })
    .await
    .map_err(|_| "Background preparation interrupted".to_owned())?
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn foreground_cancels_before_waiting_for_the_worker() {
        let worker = WORKER.lock().unwrap();
        let initial = GENERATION.load(Ordering::SeqCst);
        let (send, receive) = std::sync::mpsc::channel();
        let foreground = std::thread::spawn(move || {
            yield_to_foreground().unwrap();
            send.send(()).unwrap();
        });
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(2);
        while GENERATION.load(Ordering::SeqCst) == initial && std::time::Instant::now() < deadline {
            std::thread::yield_now();
        }
        let cancelled = GENERATION.load(Ordering::SeqCst) != initial;
        let waiting = receive.try_recv().is_err();
        drop(worker);
        foreground.join().unwrap();
        assert!(cancelled && waiting);
    }
}
