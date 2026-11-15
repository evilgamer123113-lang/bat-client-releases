//! Optional title for the exact child Minecraft window. No process-name guessing.
fn valid(pid: u32, title: &str) -> bool {
    pid != 0
        && !title.trim().is_empty()
        && title.chars().count() <= 256
        && !title.chars().any(char::is_control)
}
#[cfg(windows)]
mod platform {
    use std::ffi::c_void;
    type Window = *mut c_void;
    #[link(name = "user32")]
    extern "system" {
        fn EnumWindows(
            callback: Option<unsafe extern "system" fn(Window, isize) -> i32>,
            data: isize,
        ) -> i32;
        fn GetWindowThreadProcessId(window: Window, pid: *mut u32) -> u32;
        fn IsWindowVisible(window: Window) -> i32;
        fn GetClassNameW(window: Window, buffer: *mut u16, max: i32) -> i32;
        fn GetWindowTextW(window: Window, buffer: *mut u16, max: i32) -> i32;
        fn SendMessageTimeoutW(
            window: Window,
            message: u32,
            wparam: usize,
            lparam: isize,
            flags: u32,
            timeout: u32,
            result: *mut usize,
        ) -> isize;
    }
    struct Request {
        pid: u32,
        title: Vec<u16>,
        applied: bool,
    }
    unsafe extern "system" fn visit(window: Window, data: isize) -> i32 {
        let request = &mut *(data as *mut Request);
        let mut pid = 0;
        GetWindowThreadProcessId(window, &mut pid);
        if pid != request.pid || IsWindowVisible(window) == 0 {
            return 1;
        }
        let mut class = [0u16; 128];
        let count = GetClassNameW(window, class.as_mut_ptr(), 128);
        let class = String::from_utf16_lossy(&class[..count.max(0) as usize]);
        if !class.starts_with("GLFW") && !class.contains("LWJGL") {
            return 1;
        }
        let mut current = [0u16; 520];
        let count = GetWindowTextW(window, current.as_mut_ptr(), 520);
        if current[..count.max(0) as usize] == request.title[..request.title.len() - 1] {
            request.applied = true;
            return 0;
        }
        let mut result = 0;
        request.applied = SendMessageTimeoutW(
            window,
            0x000c,
            0,
            request.title.as_ptr() as isize,
            0x2,
            100,
            &mut result,
        ) != 0
            && result != 0;
        if request.applied {
            let n = GetWindowTextW(window, current.as_mut_ptr(), 520);
            request.applied =
                current[..n.max(0) as usize] == request.title[..request.title.len() - 1];
        }
        0
    }
    pub fn apply(pid: u32, title: &str) -> bool {
        let mut request = Request {
            pid,
            title: title
                .trim()
                .encode_utf16()
                .chain(std::iter::once(0))
                .collect(),
            applied: false,
        };
        unsafe {
            EnumWindows(Some(visit), &mut request as *mut Request as isize);
        }
        request.applied
    }
}
pub fn apply(pid: u32, title: &str) -> bool {
    if !valid(pid, title) {
        return false;
    }
    #[cfg(windows)]
    {
        return platform::apply(pid, title);
    }
    #[cfg(not(windows))]
    {
        false
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn no_empty_invalid_or_unowned_title() {
        assert!(!valid(0, "Test"));
        assert!(!valid(1, " "));
        assert!(!valid(1, "bad\nname"));
        assert!(!valid(1, &"x".repeat(257)));
        assert!(valid(1, "Bat survival"));
    }
}
