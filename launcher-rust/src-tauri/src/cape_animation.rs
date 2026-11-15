use crate::{downloads, library as lib};
use base64::{engine::general_purpose::STANDARD, Engine};
use serde_json::json;
use std::{
    fs,
    io::{Cursor, Write},
    path::Path,
};
const MAX: usize = 16 * 1024 * 1024;
const MAX_DECODED: usize = 64 * 1024 * 1024;
fn frame_width(source: usize, count: usize) -> usize {
    let mut width = source;
    while width * (width / 2) * 4 * count > MAX_DECODED { width -= 64; }
    width
}
pub fn decode(data: &str) -> Result<(Vec<Vec<u8>>, Vec<u32>), String> {
    if data.len() > 4 * 1024 * 1024 * 4 / 3 + 128 {
        return Err("GIF is too large (4 MB maximum)".into());
    }
    let bytes = data
        .strip_prefix("data:image/gif;base64,")
        .and_then(|v| STANDARD.decode(v).ok())
        .ok_or("Choose a GIF cape")?;
    let mut options = gif::DecodeOptions::new();
    options.set_color_output(gif::ColorOutput::RGBA);
    let mut decoder = options
        .read_info(Cursor::new(bytes.as_slice()))
        .map_err(|_| "Invalid GIF cape")?;
    let (width, height) = (decoder.width() as usize, decoder.height() as usize);
    if width < 64 || width > 2048 || width % 64 != 0 || height * 2 != width {
        return Err("Use a 64x32 GIF cape or an integer multiple up to 2048x1024".into());
    }
    // Inspect compressed frame records without decoding pixels twice. Preserve the
    // original detail whenever it fits the bounded in-game decoded-frame budget.
    let mut scan_options = gif::DecodeOptions::new();
    scan_options.skip_frame_decoding(true);
    let mut scan = scan_options.read_info(Cursor::new(bytes.as_slice())).map_err(|_| "Invalid GIF cape")?;
    let mut frame_count = 0usize;
    while scan.read_next_frame().map_err(|_| "Damaged GIF cape")?.is_some() {
        frame_count += 1;
        if frame_count > 120 { return Err("GIF cape exceeds 120 frames".into()); }
    }
    if frame_count == 0 { return Err("GIF contains no frames".into()); }
    let output_width = frame_width(width, frame_count);
    let output_height = output_width / 2;
    let mut canvas = vec![0u8; width * height * 4];
    let mut frames = vec![];
    let mut delays = vec![];
    let mut expanded = 0usize;
    let mut encoded = 0usize;
    while let Some(frame) = decoder.read_next_frame().map_err(|_| "Damaged GIF cape")? {
        if frames.len() >= 120 {
            return Err("GIF cape exceeds 120 frames".into());
        }
        expanded += output_width * output_height * 4;
        if expanded > MAX_DECODED {
            return Err("Decoded GIF cape exceeds 64 MB".into());
        }
        let (left, top, w, h) = (
            frame.left as usize,
            frame.top as usize,
            frame.width as usize,
            frame.height as usize,
        );
        if left + w > width || top + h > height || frame.buffer.len() != w * h * 4 {
            return Err("GIF frame is outside its canvas".into());
        }
        let previous = if frame.dispose == gif::DisposalMethod::Previous {
            Some(canvas.clone())
        } else {
            None
        };
        for y in 0..h {
            for x in 0..w {
                let source = (y * w + x) * 4;
                let target = ((top + y) * width + left + x) * 4;
                if frame.buffer[source + 3] != 0 {
                    canvas[target..target + 4].copy_from_slice(&frame.buffer[source..source + 4]);
                }
            }
        }
        let mut sampled = vec![0u8; output_width * output_height * 4];
        for y in 0..output_height {
            for x in 0..output_width {
                let source = ((y * height / output_height) * width + x * width / output_width) * 4;
                let target = (y * output_width + x) * 4;
                sampled[target..target+4].copy_from_slice(&canvas[source..source+4]);
            }
        }
        let mut png = vec![];
        {
            let mut encoder = png::Encoder::new(&mut png, output_width as u32, output_height as u32);
            encoder.set_color(png::ColorType::Rgba);
            encoder.set_depth(png::BitDepth::Eight);
            let mut writer = encoder
                .write_header()
                .map_err(|_| "Cannot encode cape frame")?;
            writer
                .write_image_data(&sampled)
                .map_err(|_| "Cannot encode cape frame")?;
        }
        encoded += png.len();
        if encoded > MAX {
            return Err("Encoded cape frames exceed 16 MB".into());
        }
        frames.push(png);
        delays.push((frame.delay as u32 * 10).clamp(20, 10000));
        match frame.dispose {
            gif::DisposalMethod::Background => {
                for y in top..top + h {
                    canvas[(y * width + left) * 4..(y * width + left + w) * 4].fill(0);
                }
            }
            gif::DisposalMethod::Previous => canvas = previous.unwrap(),
            _ => {}
        }
    }
    if frames.is_empty() {
        return Err("GIF contains no frames".into());
    }
    Ok((frames, delays))
}
#[cfg(test)]
pub fn save(root: &Path, username: &str, data: &str) -> Result<(), String> {
    with_frames(root, data, |frames, delays| {
        lib::edit_config(root, |config| {
            let account = config["savedAccounts"]
                .as_array_mut()
                .and_then(|accounts| {
                    accounts
                        .iter_mut()
                        .find(|a| a["username"].as_str() == Some(username))
                })
                .ok_or("Account no longer exists")?;
            account["customCape"] = json!(frames[0]);
            account["capeFrames"] = json!(frames);
            account["capeDelays"] = json!(delays);
            Ok(())
        })
    })
}

pub(crate) fn with_frames<T>(
    root: &Path,
    data: &str,
    commit: impl FnOnce(&[String], &[u32]) -> Result<T, String>,
) -> Result<T, String> {
    // Decode and bound the complete animation before writing any files or account metadata.
    let (frames, delays) = decode(data)?;
    let dir = root.join("skins");
    downloads::parent(&dir.join("frame.png"))?;
    let mut created = vec![];
    let result = (|| {
        for frame in frames {
            let name = format!("rust-cape-{}.png", uuid::Uuid::new_v4());
            let mut temp =
                tempfile::NamedTempFile::new_in(&dir).map_err(|_| "Cannot stage cape frame")?;
            temp.write_all(&frame)
                .map_err(|_| "Cannot save cape frame")?;
            temp.as_file()
                .sync_all()
                .map_err(|_| "Cannot flush cape frame")?;
            temp.persist_noclobber(dir.join(&name))
                .map_err(|_| "Cannot commit cape frame")?;
            created.push(name);
        }
        commit(&created, &delays)
    })();
    if result.is_err() {
        for name in created {
            let _ = fs::remove_file(dir.join(name));
        }
    }
    result
}
// Export the equipped snapshot in the Java agent's numbered-frame format.
pub(crate) fn stage_for_game(
    root: &Path, dir: &Path, username: &str, account: &serde_json::Value,
) -> Result<(), String> {
    let Some(frames) = account["capeFrames"].as_array().filter(|frames| frames.len() > 1) else {
        // A launch override also prevents stale legacy frames from replacing a static cape.
        return Ok(());
    };
    if frames.len() > 120 || username.is_empty() || username.len() > 16
        || !username.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_') {
        return Err("Invalid equipped cape animation. Re-equip the cape in Wardrobe.".into());
    }
    let name = username.to_ascii_lowercase();
    let mut total = 0;
    let mut delays = Vec::with_capacity(frames.len());
    fs::create_dir(dir).map_err(|_| "Cannot create the in-game cape folder")?;
    for (index, frame) in frames.iter().enumerate() {
        let filename = frame.as_str().filter(|name| name.to_ascii_lowercase().ends_with(".png"))
            .ok_or("Equipped cape contains an invalid frame")?;
        let path = lib::regular_file(&root.join("skins"), filename)
            .map_err(|_| "Equipped cape frame is missing. Re-equip the cape in Wardrobe.")?;
        let bytes = fs::metadata(&path).map_err(|_| "Cannot inspect equipped cape frame")?.len();
        total += bytes;
        if bytes == 0 || bytes > 4 * 1024 * 1024 || total > MAX as u64 {
            return Err("Equipped cape frames exceed the size limit".into());
        }
        fs::copy(&path, dir.join(format!("cape-{name}-frame-{index}.png")))
            .map_err(|_| "Cannot stage an equipped cape frame")?;
        delays.push(account["capeDelays"][index].as_u64().unwrap_or(120).clamp(20, 10000));
    }
    fs::write(dir.join(format!("cape-{name}.json")), json!(delays).to_string())
        .map_err(|_| "Cannot stage equipped cape timing".into())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn detail_budget_keeps_small_animations_native_and_bounds_long_ones() {
        assert_eq!(frame_width(512,60),512);
        assert_eq!(frame_width(1024,2),1024);
        assert_eq!(frame_width(2048,8),2048);
        let width=frame_width(2048,120);
        assert_eq!(width,512);
        assert!(width*(width/2)*4*120<=MAX_DECODED);
    }
    #[test]
    fn detailed_gif_preserves_source_resolution_frames_and_delays() {
        let mut bytes=vec![];
        { let mut encoder=gif::Encoder::new(&mut bytes,512,256,&[255,0,0,0,0,255]).unwrap();
          for i in 0..60 { encoder.write_frame(&gif::Frame {width:512,height:256,delay:if i%2==0 {8}else{14},buffer:vec![(i%2) as u8;512*256].into(),..Default::default()}).unwrap(); }
        }
        let (frames,delays)=decode(&format!("data:image/gif;base64,{}",STANDARD.encode(bytes))).unwrap();
        assert_eq!(frames.len(),60);assert_eq!(&delays[..2],&[80,140]);
        let reader=png::Decoder::new(Cursor::new(&frames[0])).read_info().unwrap();
        assert_eq!((reader.info().width,reader.info().height),(512,256));assert_ne!(frames[0],frames[1]);
    }
    #[test]
    fn stages_equipped_frames_with_agent_names_and_order() {
        let root = tempfile::tempdir().unwrap();
        let skins = root.path().join("skins");
        fs::create_dir(&skins).unwrap();
        fs::write(skins.join("rust-one.png"), b"first").unwrap();
        fs::write(skins.join("rust-two.png"), b"second").unwrap();
        let target = root.path().join("launch");
        let account = json!({"capeFrames":["rust-two.png","rust-one.png"],"capeDelays":[80,140]});
        stage_for_game(root.path(), &target, "BatUser", &account).unwrap();
        assert_eq!(fs::read(target.join("cape-batuser-frame-0.png")).unwrap(), b"second");
        assert_eq!(fs::read(target.join("cape-batuser-frame-1.png")).unwrap(), b"first");
        assert_eq!(fs::read_to_string(target.join("cape-batuser.json")).unwrap(), "[80,140]");
        assert!(stage_for_game(root.path(), &target, "../bad", &account).is_err());
    }

    fn fixture() -> String {
        let mut bytes = vec![];
        {
            let mut encoder =
                gif::Encoder::new(&mut bytes, 64, 32, &[255, 0, 0, 0, 0, 255]).unwrap();
            let frame = gif::Frame {
                width: 64,
                height: 32,
                delay: 8,
                dispose: gif::DisposalMethod::Background,
                buffer: vec![0u8; 64 * 32].into(),
                ..Default::default()
            };
            encoder.write_frame(&frame).unwrap();
            let frame = gif::Frame {
                width: 64,
                height: 32,
                delay: 12,
                buffer: vec![1u8; 64 * 32].into(),
                ..Default::default()
            };
            encoder.write_frame(&frame).unwrap();
        }
        format!("data:image/gif;base64,{}", STANDARD.encode(bytes))
    }
    #[test]
    fn saves_real_decoded_frames_and_preserves_private_fields() {
        let root = tempfile::tempdir().unwrap();
        lib::atomic_json(&root.path().join("profiles.config.json"),&json!({"savedAccounts":[{"username":"Player","identifier":"PRIVATE","customCape":"old.png"}]})).unwrap();
        save(root.path(), "Player", &fixture()).unwrap();
        let config = lib::config(root.path()).unwrap();
        let a = &config["savedAccounts"][0];
        assert_eq!(a["identifier"], "PRIVATE");
        assert_eq!(a["capeFrames"].as_array().unwrap().len(), 2);
        assert_eq!(a["capeDelays"], json!([80, 120]));
        for name in a["capeFrames"].as_array().unwrap() {
            assert!(
                fs::read(root.path().join("skins").join(name.as_str().unwrap()))
                    .unwrap()
                    .starts_with(b"\x89PNG")
            );
        }
    }
    #[test]
    fn invalid_animation_leaves_account_and_files_untouched() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("profiles.config.json");
        lib::atomic_json(
            &path,
            &json!({"savedAccounts":[{"username":"Player","customCape":"keep.png"}]}),
        )
        .unwrap();
        let before = fs::read(&path).unwrap();
        assert!(save(root.path(), "Player", "data:image/gif;base64,AAAA").is_err());
        assert_eq!(fs::read(path).unwrap(), before);
        assert!(!root.path().join("skins").exists());
    }
}
