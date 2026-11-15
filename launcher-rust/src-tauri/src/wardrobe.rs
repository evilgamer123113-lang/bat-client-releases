use crate::library as lib;
use base64::{engine::general_purpose::STANDARD, Engine};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use tauri::Emitter;
use std::{
    fs,
    io::{Cursor, Read, Write},
    path::Path,
};

const MAX_IMAGE: usize = 4 * 1024 * 1024;
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Outfit {
    skin: Option<String>,
    cape: Option<String>,
    frames: Vec<String>,
    delays: Vec<u32>,
    model: String,
    premium: bool,
    skin_from_mojang: bool,
    cape_from_mojang: bool,
}
fn account<'a>(config: &'a Value, username: &str) -> Result<&'a Value, String> {
    config["savedAccounts"]
        .as_array()
        .and_then(|a| a.iter().find(|a| a["username"].as_str() == Some(username)))
        .ok_or("Select a saved account first".into())
}
fn read_image(root: &Path, name: &str) -> Option<String> {
    let path = lib::regular_file(&root.join("skins"), name).ok()?;
    if fs::metadata(&path).ok()?.len() > MAX_IMAGE as u64 {
        return None;
    }
    let bytes = fs::read(path).ok()?;
    let mime = if bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        "png"
    } else if bytes.starts_with(b"GIF89a") || bytes.starts_with(b"GIF87a") {
        "gif"
    } else {
        return None;
    };
    Some(format!(
        "data:image/{mime};base64,{}",
        STANDARD.encode(bytes)
    ))
}
pub fn get_at(root: &Path, username: &str) -> Result<Outfit, String> {
    let config = lib::config(root)?;
    let a = account(&config, username)?;
    let skin = a["skinFile"]
        .as_str().and_then(|n|read_image(root,n))
        .or_else(||a["customSkin"].as_str().and_then(|n|read_image(root,n)));
    let cape = a["customCape"].as_str().and_then(|n| read_image(root, n));
    let mut frames = Vec::new();
    let mut delays = Vec::new();
    let mut total = 0;
    if let Some(names) = a["capeFrames"].as_array() {
        for (i, name) in names.iter().take(120).enumerate() {
            if let Some(data) = name.as_str().and_then(|n| read_image(root, n)) {
                total += data.len();
                if total > 16 * 1024 * 1024 * 4 / 3 + 120 * 64 {
                    break;
                }
                frames.push(data);
                delays.push(a["capeDelays"][i].as_u64().unwrap_or(100).clamp(20, 10000) as u32);
            }
        }
    }
    Ok(Outfit {
        skin,
        cape,
        frames,
        delays,
        model: if a["skinModel"].as_str() == Some("slim") {
            "slim"
        } else {
            "classic"
        }
        .into(),
        premium: a["type"].as_str() == Some("premium"),
        skin_from_mojang: false,
        cape_from_mojang: false,
    })
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Edit {
    username: String,
    kind: String,
    data: Option<String>,
    model: String,
    #[serde(default)]
    save_look: bool,
}
fn decode_png(data: &str, skin: bool) -> Result<Vec<u8>, String> {
    if data.len() > MAX_IMAGE * 4 / 3 + 128 {
        return Err("Image is too large (4 MB maximum)".into());
    }
    let bytes = data
        .strip_prefix("data:image/png;base64,")
        .and_then(|s| STANDARD.decode(s).ok())
        .ok_or("Choose a PNG image")?;
    if bytes.len() > MAX_IMAGE {
        return Err("Image is too large".into());
    }
    let mut decoder = png::Decoder::new(Cursor::new(&bytes));
    decoder.set_limits(png::Limits {
        bytes: 16 * 1024 * 1024,
    });
    let mut reader = decoder.read_info().map_err(|_| "Invalid PNG image")?;
    let info = reader.info();
    let (w, h) = (info.width, info.height);
    let valid = h <= 2048
        && if skin {
            w >= 64 && w <= 1024 && w % 64 == 0 && (h == w || h * 2 == w)
        } else {
            w >= 64 && w <= 2048 && w % 64 == 0 && h * 2 == w
        };
    if !valid || info.animation_control.is_some() {
        return Err(if skin {
            "Use a 64×64 or 64×32 skin (or an integer multiple)"
        } else {
            "Use a 64×32 cape (or an integer multiple)"
        }
        .into());
    }
    let size = reader
        .output_buffer_size()
        .filter(|s| *s <= 16 * 1024 * 1024)
        .ok_or("Image dimensions exceed the limit")?;
    reader
        .next_frame(&mut vec![0; size])
        .map_err(|_| "Damaged PNG image")?;
    Ok(bytes)
}
pub fn save_at(root: &Path, mut edit: Edit) -> Result<(), String> {
    let config = lib::config(root)?;
    let original=account(&config, &edit.username)?;
    if edit.kind=="model"&&edit.save_look&&get_at(root,&edit.username)?.skin.is_none(){
        edit.kind="skin".into();edit.data=Some(format!("data:image/png;base64,{}",STANDARD.encode(include_bytes!("../../ui/assets/steve.png"))));
    }
    let _=original;
    if !["classic", "slim"].contains(&edit.model.as_str())
        || !["skin", "cape", "model", "remove_skin", "remove_cape"].contains(&edit.kind.as_str())
    {
        return Err("Invalid cosmetic action".into());
    }
    if edit.kind == "cape"
        && edit
            .data
            .as_deref()
            .is_some_and(|d| d.starts_with("data:image/gif;base64,"))
    {
        let mut staged=Vec::new();
        let result=crate::cape_animation::with_frames(root, edit.data.as_deref().unwrap(), |frames, delays| {
            staged=frames.to_vec();
            lib::edit_config(root, |config| {
                let a = config["savedAccounts"].as_array_mut().and_then(|list| list.iter_mut().find(|a| a["username"] == edit.username)).ok_or("Account no longer exists")?;
                a["customCape"] = json!(frames[0]); a["capeFrames"] = json!(frames); a["capeDelays"] = json!(delays);
                if edit.save_look { save_equipped_slot(root,a,"cape",None)?; }
                Ok(())
            })
        });
        if result.is_ok(){discard_unreferenced_new_files(root,&staged);}
        return result;
    }
    let mut created = None;
    if ["skin", "cape"].contains(&edit.kind.as_str()) {
        let bytes = decode_png(
            edit.data.as_deref().ok_or("Choose an image first")?,
            edit.kind == "skin",
        )?;
        let dir = root.join("skins");
        fs::create_dir_all(&dir).map_err(|_| "Cannot create skin folder")?;
        if fs::symlink_metadata(&dir)
            .map_err(|_| "Cannot inspect skin folder")?
            .file_type()
            .is_symlink()
        {
            return Err("Linked cosmetic folders are not supported".into());
        }
        let name = format!("rust-{}-{}.png", edit.kind, uuid::Uuid::new_v4());
        let mut staged = tempfile::NamedTempFile::new_in(&dir).map_err(|_| "Cannot stage image")?;
        staged.write_all(&bytes).map_err(|_| "Cannot save image")?;
        staged
            .as_file()
            .sync_all()
            .map_err(|_| "Cannot flush image")?;
        staged
            .persist_noclobber(dir.join(&name))
            .map_err(|_| "Cannot commit image")?;
        created = Some(name);
    }
    let result = lib::edit_config(root, |config| {
        let a = config["savedAccounts"]
            .as_array_mut()
            .and_then(|list| {
                list.iter_mut()
                    .find(|a| a["username"].as_str() == Some(edit.username.as_str()))
            })
            .ok_or("Account no longer exists")?;
        match edit.kind.as_str() {
            "skin" => {
                a["skinFile"] = json!(created);
                a["skinModel"] = json!(edit.model);
                a.as_object_mut().unwrap().remove("headIcon");
            }
            "model" => a["skinModel"] = json!(edit.model),
            "cape" => {
                a["customCape"] = json!(created);
                a["capeFrames"] = json!([]);
                a["capeDelays"] = json!([]);
            }
            "remove_skin" => {
                for key in ["skinFile", "customSkin", "headIcon"] {
                    a.as_object_mut().unwrap().remove(key);
                }
            }
            "remove_cape" => {
                a["customCape"] = json!("");
                a["capeFrames"] = json!([]);
                a["capeDelays"] = json!([]);
            }
            _ => {}
        };
        if edit.save_look && matches!(edit.kind.as_str(), "skin" | "cape" | "model") {
            save_equipped_slot(root,a,if edit.kind=="model" {"skin"} else {&edit.kind},None)?;
        }
        Ok(())
    });
    if result.is_err() {
        if let Some(name) = created.as_ref() {
            let _ = fs::remove_file(root.join("skins").join(name));
        }
    }
    if result.is_ok(){if let Some(name)=created.as_ref(){discard_unreferenced_new_files(root,std::slice::from_ref(name));}}
    result
}
fn slot_key(kind: &str) -> Result<&'static str, String> {
    match kind {
        "skin" => Ok("customSkins"),
        "cape" => Ok("customCapes"),
        _ => Err("Select skin or cape slots".into()),
    }
}
fn next_slot_name(account: &Value, key: &str, kind: &str) -> String {
    let slots = account[key].as_array().cloned().unwrap_or_default();
    let label = if kind == "skin" { "Skin" } else { "Cape" };
    let mut number = slots.iter().filter(|slot| slot.is_object()).count() + 1;
    loop {
        let name = format!("{label} {number}");
        if !slots.iter().any(|slot| slot["name"].as_str() == Some(name.as_str())) { return name; }
        number += 1;
    }
}
fn validate_slot(root: &Path, slot: &Value, kind: &str) -> Result<(), String> {
    let mut names = vec![slot["file"].as_str().ok_or("Saved cosmetic has no file")?];
    if kind == "cape" {
        if let Some(frames) = slot["frames"].as_array() {
            if frames.len() > 120 {
                return Err("Saved animation has too many frames".into());
            }
            for frame in frames {
                names.push(frame.as_str().ok_or("Invalid saved frame")?);
            }
        }
    }
    let mut total = 0u64;
    let mut seen = std::collections::HashSet::new();
    for name in names {
        if !seen.insert(name) { continue; }
        let path = lib::regular_file(&root.join("skins"), name)?;
        let size = fs::metadata(path)
            .map_err(|_| "Saved cosmetic is missing")?
            .len();
        if size > MAX_IMAGE as u64 {
            return Err("Saved cosmetic is too large".into());
        }
        total += size;
        if total > 16 * 1024 * 1024 {
            return Err("Saved animation is too large".into());
        }
    }
    Ok(())
}
fn slot_identity(root: &Path, slot: &Value, kind: &str) -> Result<String, String> {
    use sha2::{Digest,Sha256};
    let mut hash=Sha256::new(); hash.update(kind.as_bytes());
    if kind=="skin" { hash.update(slot["model"].as_str().unwrap_or("classic").as_bytes()); }
    let frames=slot["frames"].as_array().filter(|f|!f.is_empty());
    let files=frames.map(|f|f.iter().filter_map(Value::as_str).collect::<Vec<_>>())
        .unwrap_or_else(||slot["file"].as_str().into_iter().collect());
    if files.is_empty(){return Err("No equipped image to save".into());}
    for (i,name) in files.iter().enumerate(){
        let path=lib::regular_file(&root.join("skins"),name)?;
        let bytes=fs::read(path).map_err(|_|"Cannot read saved look")?;
        hash.update(Sha256::digest(&bytes));
        if frames.is_some(){hash.update(slot["delays"][i].as_u64().unwrap_or(120).clamp(20,10000).to_le_bytes());}
    }
    Ok(format!("{:x}",hash.finalize()))
}
fn store_slot(root: &Path, account: &mut Value, key: &str, kind: &str, slot: Value) -> Result<usize,String> {
    store_slot_at(root,account,key,kind,slot,None)
}
fn store_slot_at(root: &Path, account: &mut Value, key: &str, kind: &str, mut slot: Value, index_target:Option<u32>) -> Result<usize,String> {
    if index_target.is_some_and(|i| i>=20){return Err("Choose a slot between 1 and 20".into());}
    validate_slot(root,&slot,kind)?;
    let fingerprint=slot_identity(root,&slot,kind)?;
    if account[key].is_null(){account[key]=json!([]);}
    let slots=account[key].as_array_mut().ok_or("Saved cosmetic collection is invalid")?;
    // Content, animation timing and model identify a look, not its generated filename.
    if let Some(index)=slots.iter().take(20).position(|old|old.is_object()&&slot_identity(root,old,kind).ok().as_deref()==Some(fingerprint.as_str())){
        if let Some(chosen)=index_target {
            let chosen=chosen as usize;
            if chosen!=index {
                if slots.get(chosen).is_some_and(|s|!s.is_null()){return Err("That card is already filled. Choose an empty card.".into());}
                // A chosen card is intentional even for an existing look. Reuse its
                // immutable texture files while retaining this card's automatic name.
                let mut reused=slots[index].clone();reused["name"]=slot["name"].clone();
                slots.resize(slots.len().max(chosen+1),Value::Null);slots[chosen]=reused;return Ok(chosen);
            }
        }
        return Ok(index);
    }
    slot["fingerprint"]=json!(fingerprint);
    if let Some(index)=index_target {let index=index as usize;if slots.get(index).is_some_and(|s|!s.is_null()){return Err("That card is already filled. Choose an empty card.".into());}slots.resize(slots.len().max(index+1),Value::Null);slots[index]=slot;return Ok(index);}
    if let Some(index)=slots.iter().take(20).position(Value::is_null){slots[index]=slot;return Ok(index);}
    if slots.len()>=20{return Err("Your library has 20 looks. Remove a saved look to make space; the equipped look is kept.".into());}
    slots.push(slot);Ok(slots.len()-1)
}
fn save_equipped_slot(root:&Path,account:&mut Value,kind:&str,name:Option<&str>)->Result<usize,String>{
    let key=slot_key(kind)?;let fallback=next_slot_name(account,key,kind);
    let title=name.filter(|n|!n.trim().is_empty()).unwrap_or(&fallback).trim();
    if title.chars().count()>80{return Err("Use a name up to 80 characters".into());}
    let slot=if kind=="skin"{json!({"name":title,"file":account["skinFile"].as_str().filter(|s|!s.is_empty()).or_else(||account["customSkin"].as_str()),"model":account["skinModel"].as_str().unwrap_or("classic")})}
        else{json!({"name":title,"file":account["customCape"],"frames":account["capeFrames"].as_array().cloned().unwrap_or_default(),"delays":account["capeDelays"].as_array().cloned().unwrap_or_default()})};
    let index=store_slot(root,account,key,kind,slot)?;
    let saved=account[key][index].clone();
    if kind=="skin" { account["skinFile"]=saved["file"].clone(); }
    else { account["customCape"]=saved["file"].clone();account["capeFrames"]=json!(saved["frames"].as_array().cloned().unwrap_or_default());account["capeDelays"]=json!(saved["delays"].as_array().cloned().unwrap_or_default()); }
    Ok(index)
}
fn references_file(value:&Value,name:&str)->bool {
    match value {Value::String(s)=>s==name,Value::Array(a)=>a.iter().any(|v|references_file(v,name)),Value::Object(a)=>a.values().any(|v|references_file(v,name)),_=>false}
}
fn discard_unreferenced_new_files(root:&Path,names:&[String]) {
    if let Ok(config)=lib::config(root){for name in names{if !references_file(&config,name){let _=fs::remove_file(root.join("skins").join(name));}}}
}
fn slots_at(
    root: &Path,
    username: &str,
    kind: &str,
    action: &str,
    index: Option<u32>,
    name: Option<&str>,
) -> Result<Value, String> {
    let key = slot_key(kind)?;
    let config = lib::config(root)?;
    let original = account(&config, username)?;
    if action == "list" {
        let mut total = 0usize;
        let entries = original[key]
            .as_array()
            .into_iter()
            .flatten()
            .take(20)
            .enumerate();
        let slots = entries.filter(|(_, slot)| slot.is_object()).map(|(index, slot)| {
            let preview = [slot["thumbFile"].as_str(), slot["frames"][0].as_str(), slot["file"].as_str()]
                .into_iter()
                .flatten()
                .find_map(|file| read_image(root, file))
                .filter(|image| {
                    if image.len() > 8 * 1024 * 1024 - total {
                        return false;
                    }
                    total += image.len();
                    true
                });
            let animated = slot["frames"].as_array().is_some_and(|f| f.len() > 1)
                || slot["file"].as_str().is_some_and(|file| file.to_ascii_lowercase().ends_with(".gif"));
            json!({
                "index": index,
                "name": crate::label(slot.get("name"), if kind == "skin" {"Saved skin"} else {"Saved cape"}, 80),
                "model": slot["model"].as_str().unwrap_or("classic"),
                "animated": animated,
                "equipped": slot["file"].as_str().is_some_and(|file| {
                    if kind == "skin" {
                        original["skinFile"].as_str().or_else(|| original["customSkin"].as_str()) == Some(file)
                            && slot["model"].as_str().unwrap_or("classic") == original["skinModel"].as_str().unwrap_or("classic")
                    } else {
                        original["customCape"].as_str() == Some(file)
                    }
                }),
                "preview": preview,
            })
        }).collect::<Vec<_>>();
        return Ok(json!(slots));
    }
    let captured = if action == "save" {
        let default_title = next_slot_name(original, key, kind);
        let title = name.filter(|name| !name.trim().is_empty()).unwrap_or(&default_title).trim();
        if title.is_empty() || title.chars().count() > 80 {
            return Err("Give the saved cosmetic a name (1–80 characters)".into());
        }
        let slot = if kind == "skin" {
            json!({"name":title,"file":original["skinFile"].as_str().or_else(||original["customSkin"].as_str()),"model":original["skinModel"].as_str().unwrap_or("classic")})
        } else {
            json!({"name":title,"file":original["customCape"],"frames":original["capeFrames"].as_array().cloned().unwrap_or_default(),"delays":original["capeDelays"].as_array().cloned().unwrap_or_default()})
        };
        validate_slot(root, &slot, kind)?;
        Some(slot)
    } else if ["equip", "remove"].contains(&action) {
        None
    } else {
        return Err("Invalid slot action".into());
    };
    let commit = |decoded: Option<(&[String], &[u32])>| {
        lib::edit_config(root, |config| {
            let a = config["savedAccounts"]
                .as_array_mut()
                .and_then(|list| {
                    list.iter_mut()
                        .find(|a| a["username"].as_str() == Some(username))
                })
                .ok_or("Account no longer exists")?;
            if action == "save" {
                store_slot(root,a,key,kind,captured.clone().unwrap())?;
            } else {
                let index = index.filter(|i| *i < 20).ok_or("Invalid saved cosmetic")? as usize;
                let slot = a[key]
                    .get(index)
                    .filter(|s| s.is_object())
                    .cloned()
                    .ok_or("Saved cosmetic no longer exists")?;
                if action == "remove" {
                    a[key][index] = Value::Null;
                } else {
                    validate_slot(root, &slot, kind)?;
                    if kind == "skin" {
                        a["skinFile"] = slot["file"].clone();
                        a["skinModel"] = json!(if slot["model"] == "slim" {
                            "slim"
                        } else {
                            "classic"
                        });
                        a.as_object_mut().unwrap().remove("headIcon");
                    } else {
                        a["customCape"] = slot["file"].clone();
                        a["capeFrames"] =
                            json!(slot["frames"].as_array().cloned().unwrap_or_default());
                        a["capeDelays"] =
                            json!(slot["delays"].as_array().cloned().unwrap_or_default());
                        if let Some((frames, delays)) = decoded {
                            let mut animated_slot = slot;
                            animated_slot["frames"] = json!(frames);
                            animated_slot["delays"] = json!(delays);
                            validate_slot(root, &animated_slot, kind)?;
                            a[key][index] = animated_slot;
                            a["capeFrames"] = json!(frames);
                            a["capeDelays"] = json!(delays);
                        }
                    }
                }
            }
            Ok(())
        })
    };
    let legacy_gif = (action == "equip" && kind == "cape")
        .then(|| index.filter(|i| *i < 20))
        .flatten()
        .and_then(|i| original[key].get(i as usize))
        .filter(|slot| slot["frames"].as_array().is_none_or(Vec::is_empty))
        .and_then(|slot| slot["file"].as_str())
        .filter(|file| file.to_ascii_lowercase().ends_with(".gif"));
    if let Some(file) = legacy_gif {
        let image = read_image(root, file).ok_or("Cannot read the saved GIF cape")?;
        crate::cape_animation::with_frames(root, &image, |frames, delays| {
            commit(Some((frames, delays)))
        })?;
    } else {
        commit(None)?;
    }
    Ok(
        json!({"message":match action{"save"=>"Saved to your collection.","remove"=>"Removed from your collection. Equipped cosmetics and files were kept.",_=>"Cosmetic equipped locally."}}),
    )
}
#[tauri::command]
pub async fn wardrobe_slot(
    username: String,
    kind: String,
    action: String,
    index: Option<u32>,
    name: Option<String>,
    imports: Option<Vec<SlotImport>>,
    app: tauri::AppHandle,
 ) -> Result<Value, String> {
    // Fetch the displayed premium look before taking the filesystem lock. Saving
    // a collection snapshot does not equip an override or publish to a service.
    let snapshot = if action == "save" {
        Some(wardrobe_outfit(username.clone()).await?)
    } else { None };
    tauri::async_runtime::spawn_blocking(move || {
        let root = lib::root()?;
        if action == "list" {
            return slots_at(&root, &username, &kind, &action, index, name.as_deref());
        }
        let result = {
            let _lock = lib::WRITE_LOCK.lock().map_err(|_| "Library busy")?;
            lib::ensure_legacy_launcher_closed()?;
            if action == "import" {
                import_slots_into(&root, &username, &kind, imports.unwrap_or_default(),index)?
            } else if action == "save" && snapshot.as_ref().is_some_and(|outfit|
                if kind == "skin" { outfit.skin_from_mojang || outfit.skin.is_none() }
                else { outfit.cape_from_mojang }) {
                save_displayed_slot(&root, &username, &kind, snapshot.as_ref().unwrap(), name.as_deref())?
            } else {
                slots_at(&root, &username, &kind, &action, index, name.as_deref())?
            }
        };
        if action == "equip" {
            queue_sync(app,root,username,kind);
            return Ok(json!({"message":"Equipped and saved on this device. Ready for the next Minecraft launch."}));
        }
        Ok(result)
    })
    .await
    .map_err(|_| "Saved cosmetic operation interrupted".to_owned())?
}

fn save_displayed_slot(root: &Path, username: &str, kind: &str, outfit: &Outfit, name: Option<&str>) -> Result<Value, String> {
    let key = slot_key(kind)?;
    let data = if kind == "skin" {
        outfit.skin.clone().unwrap_or_else(|| format!("data:image/png;base64,{}",
            STANDARD.encode(include_bytes!("../../ui/assets/steve.png"))))
    } else { outfit.cape.clone().ok_or("No cape is equipped")? };
    let config = lib::config(root)?;
    let a = account(&config, username)?;
    let title = name.filter(|name| !name.trim().is_empty()).map(str::to_owned)
        .unwrap_or_else(|| next_slot_name(a, key, kind));
    import_slots(root, username, kind, vec![SlotImport {name:title,data,model:outfit.model.clone()}])
}

#[derive(Deserialize)]
pub struct SlotImport {
    name: String,
    data: String,
    model: String,
}

fn import_slots(
    root: &Path,
    username: &str,
    kind: &str,
    imports: Vec<SlotImport>,
) -> Result<Value, String> {
    import_slots_into(root,username,kind,imports,None)
}
fn import_slots_into(root:&Path,username:&str,kind:&str,imports:Vec<SlotImport>,index:Option<u32>)->Result<Value,String>{
    if index.is_some_and(|i|i>=20)||index.is_some()&&imports.len()!=1{return Err("Choose one image for an empty card".into());}
    let key = slot_key(kind)?;
    let config = lib::config(root)?;account(&config,username)?;
    if imports.is_empty()||imports.len()>20{return Err("Choose between 1 and 20 looks".into());}
    if imports.iter().map(|item| item.data.len()).sum::<usize>() > 16 * 1024 * 1024 {
        return Err("Selected images exceed the collection import size limit".into());
    }
    let dir = root.join("skins");
    crate::downloads::parent(&dir.join("collection.png"))?;
    let mut created = Vec::new();
    let count = imports.len();
    let result = (|| {
        let mut prepared=Vec::new();
        let mut expanded = 0usize;
        for item in imports {
            let name = item.name.trim();
            if name.chars().count() > 80
                || !["classic", "slim"].contains(&item.model.as_str())
            {
                return Err("Invalid saved look name or model".into());
            }
            let (images, delays) =
                if kind == "cape" && item.data.starts_with("data:image/gif;base64,") {
                    crate::cape_animation::decode(&item.data)?
                } else {
                    (vec![decode_png(&item.data, kind == "skin")?], Vec::new())
                };
            expanded += images.iter().map(Vec::len).sum::<usize>();
            if expanded > 32 * 1024 * 1024 {
                return Err(
                    "Decoded collection exceeds 32 MB. Import fewer images at a time.".into(),
                );
            }
            let mut files = Vec::with_capacity(images.len());
            for bytes in images {
                let filename = format!("rust-{kind}-{}.png", uuid::Uuid::new_v4());
                let mut staged =
                    tempfile::NamedTempFile::new_in(&dir).map_err(|_| "Cannot stage cosmetic")?;
                staged
                    .write_all(&bytes)
                    .map_err(|_| "Cannot save cosmetic")?;
                staged
                    .as_file()
                    .sync_all()
                    .map_err(|_| "Cannot flush cosmetic")?;
                staged
                    .persist_noclobber(dir.join(&filename))
                    .map_err(|_| "Cannot commit cosmetic")?;
                files.push(filename.clone());
                created.push(filename);
            }
            let mut slot = json!({"name":name,"file":files[0],"model":item.model});
            if files.len() > 1 {
                slot["frames"] = json!(files);
                slot["delays"] = json!(delays);
            }
            validate_slot(root, &slot, kind)?;
            prepared.push(slot);
        }
        let retained=lib::edit_config(root, |config| {
            let target=config["savedAccounts"].as_array_mut().and_then(|list|list.iter_mut().find(|a|a["username"]==username)).ok_or("Account no longer exists")?;
            let mut indices=Vec::new();
            for mut slot in prepared {if slot["name"].as_str().unwrap_or("").is_empty(){slot["name"]=json!(next_slot_name(target,key,kind));}indices.push(store_slot_at(root,target,key,kind,slot,index)?);}
            let mut names=std::collections::HashSet::new();
            for slot in target[key].as_array().into_iter().flatten(){
                if let Some(name)=slot["file"].as_str(){names.insert(name.to_owned());}
                for frame in slot["frames"].as_array().into_iter().flatten().filter_map(Value::as_str){names.insert(frame.to_owned());}
            }
            Ok((names,indices))
        })?;
        for name in &created { if !retained.0.contains(name) { let _=fs::remove_file(dir.join(name)); } }
        Ok(json!({"message":format!("Added {count} saved looks. Select a card to wear it."),"indices":retained.1}))
    })();
    if result.is_err() {
        for name in created {
            let _ = fs::remove_file(dir.join(name));
        }
    }
    result
}
#[tauri::command]
pub async fn wardrobe_outfit(username: String) -> Result<Outfit, String> {
    let (mut outfit, remote_id) = tauri::async_runtime::spawn_blocking(move || {
        let root = lib::root()?;
        let outfit = get_at(&root, &username)?;
        let remote_id = if outfit.premium && (outfit.skin.is_none() || outfit.cape.is_none()) {
            crate::accounts::premium_texture_id(&root, &username)
        } else {
            None
        };
        Ok::<_, String>((outfit, remote_id))
    })
    .await
    .map_err(|_| "Cannot read wardrobe")??;
    if let Some(id) = remote_id {
        let needs_cape = outfit.cape.is_none() && outfit.frames.is_empty();
        if outfit.skin.is_none() || needs_cape {
            let remote =
                crate::accounts::premium_textures(id, outfit.skin.is_none(), needs_cape).await;
            if remote.skin.is_some() {
                outfit.skin = remote.skin;
                outfit.model = remote.model.into();
                outfit.skin_from_mojang = true;
            }
            if needs_cape {
                outfit.cape = remote.cape;
                outfit.cape_from_mojang = outfit.cape.is_some();
            }
        }
    }
    Ok(outfit)
}
static SYNC_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());
fn queue_sync(app: tauri::AppHandle, root: std::path::PathBuf, username: String, kind: String) {
    tauri::async_runtime::spawn_blocking(move || {
        let Ok(_guard)=SYNC_LOCK.lock() else{return;};
        let text=sync_saved(&root,&username,&kind).unwrap_or_else(|e|format!("Saved on this device. {e}"));
        let _=app.emit("bat-cosmetic-sync",json!({"username":username,"kind":kind,"message":text}));
    });
}
#[tauri::command]
pub async fn save_cosmetic(edit: Edit, app: tauri::AppHandle) -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let root=lib::root()?;let username=edit.username.clone();let kind=edit.kind.clone();
        {let _lock=lib::WRITE_LOCK.lock().map_err(|_|"Library busy")?;
         lib::ensure_legacy_launcher_closed()?;save_at(&root,edit)?;}
        queue_sync(app,root,username,kind);
        Ok("Saved on this device. Ready for your next Minecraft launch; online sync runs in the background.".into())
    }).await.map_err(|_|"Cannot save cosmetic".to_owned())?
}
fn registry_payload(username: &str, outfit: &Outfit) -> Value {
    json!({"username":username,"skin":outfit.skin.as_ref().filter(|s|s.starts_with("data:image/png;")).map(|s|json!({"model":outfit.model,"dataUrl":s})),"cape":outfit.frames.first().or(outfit.cape.as_ref()).filter(|s|s.starts_with("data:image/png;")).map(|s|json!({"dataUrl":s}))})
}
fn sync_saved(root: &Path, username: &str, kind: &str) -> Result<String, String> {
    if kind.starts_with("remove_") {
        return Ok(
            "Removed locally. The current skin service does not support deleting hosted cosmetics."
                .into(),
        );
    }
    let outfit = get_at(root, username)?;
    let config = lib::config(root)?;
    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .map_err(|_| "Online sync could not initialize")?;
    let mut messages = vec![];
    if outfit.premium && matches!(kind, "skin" | "model") {
        if let Some(data) = outfit.skin.as_ref() {
            crate::microsoft::fresh_for_launch(root, username)
                .map_err(|_| "Microsoft session needs a new sign-in; local skin preserved")?;
            let fresh = lib::config(root)?;
            let a = account(&fresh, username)?;
            let tokens = crate::microsoft::read_tokens(
                a["rustAuthId"]
                    .as_str()
                    .ok_or("Sign in with Microsoft to publish your skin")?,
            )?;
            let bytes = decode_png(data, true)?;
            let boundary = format!("BatSkin{}", uuid::Uuid::new_v4().simple());
            let variant = if outfit.model == "slim" {
                "slim"
            } else {
                "classic"
            };
            let mut body=format!("--{boundary}\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n{variant}\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n").into_bytes();
            body.extend(bytes);
            body.extend(format!("\r\n--{boundary}--\r\n").as_bytes());
            let response = client
                .post("https://api.minecraftservices.com/minecraft/profile/skins")
                .bearer_auth(tokens.access_token)
                .header(
                    "Content-Type",
                    format!("multipart/form-data; boundary={boundary}"),
                )
                .body(body)
                .send()
                .map_err(|_| "Microsoft skin upload interrupted; try Apply again")?;
            if !response.status().is_success() {
                return Err(format!(
                    "Microsoft rejected the skin upload (HTTP {}).",
                    response.status().as_u16()
                ));
            }
            messages.push("Minecraft skin uploaded.");
        }
    }
    if config["appSettings"]["publishSkinsToBatClient"]
        .as_bool()
        .unwrap_or(true)
    {
        let token = config["appSettings"]["batClientSkinToken"]
            .as_str()
            .filter(|t| !t.trim().is_empty())
            .ok_or(
                "Bat skin sync requires a configured service credential; local cosmetics preserved",
            )?;
        let body = serde_json::to_vec(&registry_payload(username, &outfit))
            .map_err(|_| "Cannot prepare online cosmetic")?;
        if body.len() > 4 * 1024 * 1024 {
            return Err("Cosmetics exceed the online service's 4 MB request limit; local cosmetics preserved".into());
        }
        let response = client
            .post("https://batclient.xyz/skinapi/api/skins/upload")
            .header("X-Bat-Token", token)
            .header("Content-Type", "application/json")
            .body(body)
            .send()
            .map_err(|_| "Bat skin service could not be reached; try Apply again")?;
        if !response.status().is_success() {
            return Err(format!(
                "Bat skin service rejected the upload (HTTP {}).",
                response.status().as_u16()
            ));
        }
        let mut bytes = vec![];
        std::io::Read::take(response, 65537)
            .read_to_end(&mut bytes)
            .map_err(|_| "Skin service response interrupted")?;
        if bytes.len() > 65536
            || serde_json::from_slice::<Value>(&bytes)
                .ok()
                .is_none_or(|v| v["ok"] != true)
        {
            return Err("Skin service did not confirm the upload".into());
        }
        messages.push(if outfit.frames.len() > 1 {
            "Bat skin service updated with the first cape frame; full animation remains local."
        } else {
            "Bat skin service updated."
        });
    }
    if messages.is_empty() {
        Ok("Saved locally. Bat skin publishing is disabled in your saved settings.".into())
    } else {
        Ok(format!("Saved locally. {}", messages.join(" ")))
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn atomic_gif_duplicate_reuses_saved_frames_without_orphan_files(){
        let root=fixture();let mut bytes=vec![];
        {let mut encoder=gif::Encoder::new(&mut bytes,64,32,&[255,0,0,0,0,255]).unwrap();for i in 0..2{encoder.write_frame(&gif::Frame{width:64,height:32,delay:if i==0{8}else{14},buffer:vec![i;64*32].into(),..Default::default()}).unwrap();}}
        let data=format!("data:image/gif;base64,{}",STANDARD.encode(bytes));
        for _ in 0..2{let mut request=edit("cape",Some(data.clone()));request.save_look=true;save_at(root.path(),request).unwrap();}
        assert_eq!(fs::read_dir(root.path().join("skins")).unwrap().count(),2);
        let c=lib::config(root.path()).unwrap();let a=&c["savedAccounts"][0];assert_eq!(a["capeDelays"],json!([80,140]));assert_eq!(a["customCapes"].as_array().unwrap().len(),1);
        assert_eq!(a["capeFrames"],a["customCapes"][0]["frames"]);
    }
    #[test]
    fn apply_and_save_is_idempotent_and_survives_a_fresh_process() {
        let root=fixture();
        lib::edit_config(root.path(),|c|{c["savedAccounts"][0]["customSkins"]=json!(vec![Value::Null;20]);Ok(())}).unwrap();
        let mut request=edit("skin",Some(png(64,64)));request.save_look=true;save_at(root.path(),request).unwrap();
        let first=lib::config(root.path()).unwrap()["savedAccounts"][0]["skinFile"].clone();
        let mut request=edit("skin",Some(png(64,64)));request.save_look=true;save_at(root.path(),request).unwrap();
        let c=lib::config(root.path()).unwrap();assert_eq!(c["savedAccounts"][0]["skinFile"],first);
        assert_eq!(slots_at(root.path(),"Player","skin","list",None,None).unwrap().as_array().unwrap().len(),1);
        assert_eq!(fs::read_dir(root.path().join("skins")).unwrap().count(),1);
        let result=std::process::Command::new(std::env::current_exe().unwrap()).args(["--ignored","--exact","wardrobe::tests::fresh_process_wardrobe_probe"])
            .env("BAT_WARDROBE_RESTART_FIXTURE",root.path()).output().unwrap();
        assert!(result.status.success(),"{} {}",String::from_utf8_lossy(&result.stdout),String::from_utf8_lossy(&result.stderr));
    }
    #[test]
    #[ignore="Run by the isolated restart regression, with a synthetic test library only"]
    fn fresh_process_wardrobe_probe(){
        let path=std::env::var_os("BAT_WARDROBE_RESTART_FIXTURE").expect("Synthetic fixture required");let root=Path::new(&path);
        let outfit=get_at(root,"Player").unwrap();assert!(outfit.skin.unwrap().starts_with("data:image/png;base64,"));assert_eq!(outfit.model,"slim");
        let list=slots_at(root,"Player","skin","list",None,None).unwrap();assert_eq!(list.as_array().unwrap().len(),1);assert_eq!(list[0]["equipped"],true);
        let config=lib::config(root).unwrap();let account=&config["savedAccounts"][0];
        assert_eq!(account["identifier"],"PRIVATE_REF");
        let mut bridge_account=account.clone();bridge_account["type"]=json!("offline");
        let bridge=crate::skin_bridge::start(root,&bridge_account,&crate::skin_bridge::offline_uuid("Player")).unwrap();
        let http=reqwest::blocking::Client::new();
        let profile:Value=http.get(format!("{}/sessionserver/session/minecraft/profile/{}",bridge.root,crate::skin_bridge::offline_uuid("Player"))).send().unwrap().json().unwrap();
        let textures:Value=serde_json::from_slice(&STANDARD.decode(profile["properties"][0]["value"].as_str().unwrap()).unwrap()).unwrap();
        let texture=http.get(textures["textures"]["SKIN"]["url"].as_str().unwrap()).send().unwrap().bytes().unwrap();
        assert_eq!(texture.as_ref(),fs::read(root.join("skins").join(account["skinFile"].as_str().unwrap())).unwrap());
        assert_eq!(textures["textures"]["SKIN"]["metadata"]["model"],"slim");
    }
    #[test]
    fn full_library_apply_failure_preserves_equipped_image_and_new_files_are_removed(){
        let root=fixture();save_at(root.path(),edit("skin",Some(png(64,64)))).unwrap();
        slots_at(root.path(),"Player","skin","save",None,None).unwrap();
        lib::edit_config(root.path(),|c|{let slot=c["savedAccounts"][0]["customSkins"][0].clone();c["savedAccounts"][0]["customSkins"]=json!(vec![slot;20]);Ok(())}).unwrap();
        let before=fs::read(root.path().join("profiles.config.json")).unwrap();let files=fs::read_dir(root.path().join("skins")).unwrap().count();
        let mut request=edit("skin",Some(png(64,32)));request.save_look=true;assert!(save_at(root.path(),request).is_err());
        assert_eq!(fs::read(root.path().join("profiles.config.json")).unwrap(),before);assert_eq!(fs::read_dir(root.path().join("skins")).unwrap().count(),files);
    }
    #[test]
    fn gif_apply_save_equip_and_game_staging_keep_real_frames() {
        let root=fixture();let mut bytes=vec![];
        {let mut gif=gif::Encoder::new(&mut bytes,512,256,&[255,0,0,0,0,255]).unwrap();
         for i in 0..60 {gif.write_frame(&gif::Frame{width:512,height:256,delay:if i%2==0 {8}else{14},buffer:vec![(i%2) as u8;512*256].into(),..Default::default()}).unwrap();}}
        save_at(root.path(),edit("cape",Some(format!("data:image/gif;base64,{}",STANDARD.encode(bytes))))).unwrap();
        slots_at(root.path(),"Player","cape","save",None,None).unwrap();
        save_at(root.path(),edit("remove_cape",None)).unwrap();
        slots_at(root.path(),"Player","cape","equip",Some(0),None).unwrap();
        let config=lib::config(root.path()).unwrap();let account=&config["savedAccounts"][0];
        let target=root.path().join("game-cape");
        crate::cape_animation::stage_for_game(root.path(),&target,"Player",account).unwrap();
        let delays:Value=serde_json::from_slice(&fs::read(target.join("cape-player.json")).unwrap()).unwrap();
        assert_eq!(delays.as_array().unwrap().len(),60);assert_eq!(delays[0],80);assert_eq!(delays[1],140);
        assert_ne!(fs::read(target.join("cape-player-frame-0.png")).unwrap(),fs::read(target.join("cape-player-frame-1.png")).unwrap());
        assert_eq!(get_at(root.path(),"Player").unwrap().frames.len(),60);
    }
    #[test]
    fn displayed_premium_and_default_skin_save_without_equipping_override() {
        let root=fixture();
        let mut outfit=Outfit {skin:Some(png(64,64)),cape:Some(png(64,32)),frames:vec![],delays:vec![],model:"slim".into(),premium:true,skin_from_mojang:true,cape_from_mojang:true};
        save_displayed_slot(root.path(),"Player","skin",&outfit,None).unwrap();
        save_displayed_slot(root.path(),"Player","cape",&outfit,None).unwrap();
        outfit.skin=None;
        save_displayed_slot(root.path(),"Player","skin",&outfit,None).unwrap();
        let config=lib::config(root.path()).unwrap();let a=&config["savedAccounts"][0];
        assert!(a["skinFile"].is_null()); assert!(a["customCape"].is_null());
        assert_eq!(a["customSkins"][0]["name"],"Skin 1");assert_eq!(a["customSkins"][1]["name"],"Skin 2");
        assert_eq!(a["customCapes"][0]["name"],"Cape 1");
        assert_eq!(a["identifier"],"PRIVATE_REF");
    }
    #[test]
    fn unnamed_equipped_looks_save_without_import_and_keep_animation() {
        let root=fixture();
        save_at(root.path(),edit("skin",Some(png(64,64)))).unwrap();
        slots_at(root.path(),"Player","skin","save",None,None).unwrap();
        slots_at(root.path(),"Player","skin","save",None,Some(" ")).unwrap();
        assert_eq!(slots_at(root.path(),"Player","skin","list",None,None).unwrap().as_array().unwrap().len(),1);
        save_at(root.path(),edit("skin",Some(png(64,32)))).unwrap();
        slots_at(root.path(),"Player","skin","save",None,None).unwrap();
        let config=lib::config(root.path()).unwrap();
        assert_eq!(config["savedAccounts"][0]["customSkins"][0]["name"],"Skin 1");
        assert_eq!(config["savedAccounts"][0]["customSkins"][1]["name"],"Skin 2");
        save_at(root.path(),edit("cape",Some(png(64,32)))).unwrap();
        let file=lib::config(root.path()).unwrap()["savedAccounts"][0]["customCape"].clone();
        lib::edit_config(root.path(),|config| {config["savedAccounts"][0]["capeFrames"]=json!([file,file]);config["savedAccounts"][0]["capeDelays"]=json!([80,140]);Ok(())}).unwrap();
        slots_at(root.path(),"Player","cape","save",None,None).unwrap();
        let config=lib::config(root.path()).unwrap();
        let slot=&config["savedAccounts"][0]["customCapes"][0];
        assert_eq!(slot["name"],"Cape 1");assert_eq!(slot["delays"],json!([80,140]));assert_eq!(slot["frames"].as_array().unwrap().len(),2);
    }
    #[test]
    fn slots_save_equip_remove_preserve_files_credentials_and_indices() {
        let root = fixture();
        save_at(root.path(), edit("skin", Some(png(64, 64)))).unwrap();
        slots_at(root.path(), "Player", "skin", "save", None, Some("First")).unwrap();
        let first = lib::config(root.path()).unwrap()["savedAccounts"][0]["skinFile"]
            .as_str()
            .unwrap()
            .to_owned();
        save_at(root.path(), edit("skin", Some(png(64, 32)))).unwrap();
        slots_at(root.path(), "Player", "skin", "save", None, Some("Second")).unwrap();
        slots_at(root.path(), "Player", "skin", "equip", Some(0), None).unwrap();
        let cfg = lib::config(root.path()).unwrap();
        assert_eq!(cfg["savedAccounts"][0]["skinFile"], first);
        assert_eq!(cfg["savedAccounts"][0]["identifier"], "PRIVATE_REF");
        slots_at(root.path(), "Player", "skin", "remove", Some(0), None).unwrap();
        let list = slots_at(root.path(), "Player", "skin", "list", None, None).unwrap();
        assert_eq!(list[0]["index"], 1);
        assert!(root.path().join("skins").join(first).is_file());
    }
    #[test]
    fn cape_slots_restore_animation_timing_and_full_slots_are_atomic() {
        let root = fixture();
        save_at(root.path(), edit("cape", Some(png(64, 32)))).unwrap();
        let first = lib::config(root.path()).unwrap()["savedAccounts"][0]["customCape"]
            .as_str()
            .unwrap()
            .to_owned();
        save_at(root.path(), edit("cape", Some(png(64, 32)))).unwrap();
        let second = lib::config(root.path()).unwrap()["savedAccounts"][0]["customCape"]
            .as_str()
            .unwrap()
            .to_owned();
        lib::edit_config(root.path(), |c| {
            c["savedAccounts"][0]["capeFrames"] = json!([first, second]);
            c["savedAccounts"][0]["capeDelays"] = json!([80, 140]);
            Ok(())
        })
        .unwrap();
        slots_at(
            root.path(),
            "Player",
            "cape",
            "save",
            None,
            Some("Animated"),
        )
        .unwrap();
        save_at(root.path(), edit("remove_cape", None)).unwrap();
        slots_at(root.path(), "Player", "cape", "equip", Some(0), None).unwrap();
        let c = lib::config(root.path()).unwrap();
        assert_eq!(c["savedAccounts"][0]["capeDelays"], json!([80, 140]));
        assert_eq!(get_at(root.path(), "Player").unwrap().frames.len(), 2);
        let slot = c["savedAccounts"][0]["customCapes"][0].clone();
        lib::edit_config(root.path(), |c| {
            c["savedAccounts"][0]["customCapes"] = json!(vec![slot; 20]);
            Ok(())
        })
        .unwrap();
        // Saving a look already in a full library is idempotent, not overflow.
        slots_at(root.path(),"Player","cape","save",None,Some("Existing")).unwrap();
        lib::edit_config(root.path(),|c|{c["savedAccounts"][0]["capeDelays"]=json!([90,140]);Ok(())}).unwrap();
        let path = root.path().join("profiles.config.json");
        let before = fs::read(&path).unwrap();
        assert!(slots_at(
            root.path(),
            "Player",
            "cape",
            "save",
            None,
            Some("Overflow")
        )
        .is_err());
        assert_eq!(fs::read(path).unwrap(), before);
    }
    #[test]
    fn invalid_slot_cannot_escape_or_modify_config() {
        let root = fixture();
        lib::edit_config(root.path(), |c| {
            c["savedAccounts"][0]["customCapes"] = json!([{"file":"../escape.png","name":"Bad"}]);
            Ok(())
        })
        .unwrap();
        let before = fs::read(root.path().join("profiles.config.json")).unwrap();
        assert!(slots_at(root.path(), "Player", "cape", "equip", Some(0), None).is_err());
        assert_eq!(
            fs::read(root.path().join("profiles.config.json")).unwrap(),
            before
        );
        assert!(slots_at(root.path(), "Player", "skin", "save", None, Some("")).is_err());
    }
    #[test]
    fn registry_payload_uses_first_png_frame_and_excludes_credentials() {
        let outfit = Outfit {
            skin: Some("data:image/png;base64,skin".into()),
            cape: Some("data:image/gif;base64,gif".into()),
            frames: vec!["data:image/png;base64,frame".into()],
            delays: vec![100],
            model: "slim".into(),
            premium: false,
            skin_from_mojang: false,
            cape_from_mojang: false,
        };
        let payload = registry_payload("Fixture", &outfit);
        assert_eq!(payload["cape"]["dataUrl"], "data:image/png;base64,frame");
        assert_eq!(payload["skin"]["model"], "slim");
        assert_eq!(payload.as_object().unwrap().len(), 3);
        assert!(payload.get("token").is_none());
    }
    fn png(w: u32, h: u32) -> String {
        let mut bytes = vec![];
        {
            let mut encoder = png::Encoder::new(&mut bytes, w, h);
            encoder.set_color(png::ColorType::Rgba);
            encoder.set_depth(png::BitDepth::Eight);
            let mut writer = encoder.write_header().unwrap();
            writer
                .write_image_data(&vec![255; (w * h * 4) as usize])
                .unwrap();
        }
        format!("data:image/png;base64,{}", STANDARD.encode(bytes))
    }
    fn fixture() -> tempfile::TempDir {
        let root = tempfile::tempdir().unwrap();
        fs::write(root.path().join("profiles.config.json"),json!({"savedAccounts":[{"username":"Player","type":"premium","identifier":"PRIVATE_REF","capeFrames":["old.png"],"capeDelays":[80]}],"appSettings":{"preserve":true}}).to_string()).unwrap();
        root
    }
    #[test]
    fn selected_model_reaches_real_game_skin_endpoints(){
        let root=fixture();save_at(root.path(),edit("skin",Some(png(64,64)))).unwrap();
        let client=reqwest::blocking::Client::new();
        for model in ["classic","slim","classic"]{
            let mut change=edit("model",None);change.model=model.into();change.save_look=true;save_at(root.path(),change).unwrap();
            // Re-read disk just as a new Minecraft launch does.
            let c=lib::config(root.path()).unwrap();let mut a=c["savedAccounts"][0].clone();assert_eq!(a["skinModel"],model);a["type"]=json!("offline");
            let bridge=crate::skin_bridge::start(root.path(),&a,&crate::skin_bridge::offline_uuid("Player")).unwrap();
            let profile:Value=client.get(format!("{}/sessionserver/session/minecraft/profile/{}",bridge.root,crate::skin_bridge::offline_uuid("Player"))).send().unwrap().json().unwrap();
            let textures:Value=serde_json::from_slice(&STANDARD.decode(profile["properties"][0]["value"].as_str().unwrap()).unwrap()).unwrap();
            assert_eq!(textures["textures"]["SKIN"]["metadata"]["model"].as_str()==Some("slim"),model=="slim");
            let services:Value=client.get(format!("{}/minecraftservices/minecraft/profile",bridge.root)).send().unwrap().json().unwrap();assert_eq!(services["skins"][0]["variant"],if model=="slim"{"SLIM"}else{"CLASSIC"});
            let outfit=get_at(root.path(),"Player").unwrap();assert_eq!(registry_payload("Player",&outfit)["skin"]["model"],model);
        }
    }
    #[test]
    fn direct_upload_uses_chosen_empty_card_without_equipping(){
        let root=fixture();let initial=lib::config(root.path()).unwrap();
        let imported=import_slots_into(root.path(),"Player","skin",vec![SlotImport{name:String::new(),data:png(64,64),model:"slim".into()}],Some(4)).unwrap();
        assert_eq!(imported["indices"],json!([4]));
        let after=lib::config(root.path()).unwrap();let a=&after["savedAccounts"][0];assert_eq!(a["customSkins"][4]["name"],"Skin 1");assert!(a["customSkins"][0].is_null());assert_eq!(a["skinFile"],initial["savedAccounts"][0]["skinFile"]);
        // Another image must never silently overwrite the chosen occupied card.
        assert!(import_slots_into(root.path(),"Player","skin",vec![SlotImport{name:String::new(),data:png(64,32),model:"slim".into()}],Some(4)).is_err());
        assert_eq!(lib::config(root.path()).unwrap(),after);assert_eq!(fs::read_dir(root.path().join("skins")).unwrap().count(),1);
        let duplicate=import_slots_into(root.path(),"Player","skin",vec![SlotImport{name:String::new(),data:png(64,64),model:"slim".into()}],Some(2)).unwrap();assert_eq!(duplicate["indices"],json!([2]));assert_eq!(fs::read_dir(root.path().join("skins")).unwrap().count(),1);let c=lib::config(root.path()).unwrap();assert_eq!(c["savedAccounts"][0]["customSkins"][2]["file"],c["savedAccounts"][0]["customSkins"][4]["file"]);assert_eq!(c["savedAccounts"][0]["customSkins"][2]["name"],"Skin 2");
        assert!(import_slots_into(root.path(),"Player","skin",vec![SlotImport{name:String::new(),data:png(64,32),model:"slim".into()}],Some(20)).is_err());
    }
    #[test]
    fn direct_gif_upload_preserves_animation_until_selected(){
        let root=fixture();let mut bytes=vec![];
        {let mut encoder=gif::Encoder::new(&mut bytes,64,32,&[255,0,0,0,0,255]).unwrap();for i in 0..2{encoder.write_frame(&gif::Frame{width:64,height:32,delay:if i==0{8}else{14},buffer:vec![i;64*32].into(),..Default::default()}).unwrap();}}
        let result=import_slots_into(root.path(),"Player","cape",vec![SlotImport{name:String::new(),data:format!("data:image/gif;base64,{}",STANDARD.encode(bytes)),model:"classic".into()}],Some(3)).unwrap();assert_eq!(result["indices"],json!([3]));
        let c=lib::config(root.path()).unwrap();let a=&c["savedAccounts"][0];assert_eq!(a["customCapes"][3]["name"],"Cape 1");assert_eq!(a["customCapes"][3]["delays"],json!([80,140]));assert_eq!(a["capeFrames"],json!(["old.png"]));
        slots_at(root.path(),"Player","cape","equip",Some(3),None).unwrap();let c=lib::config(root.path()).unwrap();assert_eq!(c["savedAccounts"][0]["capeDelays"],json!([80,140]));
    }
    #[test]
    fn collection_import_rolls_back_failed_batches_and_fills_sparse_slots() {
        let root = fixture();
        let make = |name: &str, data: String| SlotImport {
            name: name.into(),
            data,
            model: "classic".into(),
        };
        let before = lib::config(root.path()).unwrap();
        assert!(import_slots(
            root.path(),
            "Player",
            "skin",
            vec![
                make("Valid", png(64, 64)),
                make("Broken", "data:image/png;base64,AAAA".into()),
            ]
        )
        .is_err());
        assert_eq!(lib::config(root.path()).unwrap(), before);
        assert_eq!(fs::read_dir(root.path().join("skins")).unwrap().count(), 0);
        lib::edit_config(root.path(), |config| {
            config["savedAccounts"][0]["customSkins"] =
                json!([null, {"name":"Keep","file":"legacy.png"}]);
            Ok(())
        })
        .unwrap();
        import_slots(
            root.path(),
            "Player",
            "skin",
            vec![make("Imported", png(64, 64))],
        )
        .unwrap();
        let config = lib::config(root.path()).unwrap();
        assert_eq!(
            config["savedAccounts"][0]["customSkins"][0]["name"],
            "Imported"
        );
        assert_eq!(config["savedAccounts"][0]["customSkins"][1]["name"], "Keep");
    }
    #[test]
    fn legacy_gif_slot_generates_frames_without_replacing_source() {
        let root = fixture();
        let mut bytes = Vec::new();
        {
            let mut encoder =
                gif::Encoder::new(&mut bytes, 64, 32, &[0, 0, 0, 255, 255, 255]).unwrap();
            for (pixel, delay) in [(0, 7), (1, 13)] {
                encoder
                    .write_frame(&gif::Frame {
                        width: 64,
                        height: 32,
                        delay,
                        buffer: vec![pixel; 64 * 32].into(),
                        ..Default::default()
                    })
                    .unwrap();
            }
        }
        let source = root.path().join("skins/legacy.gif");
        fs::create_dir_all(source.parent().unwrap()).unwrap();
        fs::write(&source, &bytes).unwrap();
        lib::edit_config(root.path(), |config| {
            config["savedAccounts"][0]["customCapes"] =
                json!([null, {"name":"Old cape","file":"legacy.gif"}]);
            Ok(())
        })
        .unwrap();
        slots_at(root.path(), "Player", "cape", "equip", Some(1), None).unwrap();
        let config = lib::config(root.path()).unwrap();
        let account = &config["savedAccounts"][0];
        assert_eq!(account["capeDelays"], json!([70, 130]));
        assert_eq!(account["capeFrames"], account["customCapes"][1]["frames"]);
        assert_eq!(fs::read(&source).unwrap(), bytes);
        assert_eq!(account["identifier"], "PRIVATE_REF");
    }
    fn edit(kind: &str, data: Option<String>) -> Edit {
        Edit {
            username: "Player".into(),
            kind: kind.into(),
            data,
            model: "slim".into(),
            save_look: false,
        }
    }
    #[test]
    fn wardrobe_skin_lifecycle_preserves_private_account_and_files() {
        let root = fixture();
        save_at(root.path(), edit("skin", Some(png(64, 64)))).unwrap();
        let c = lib::config(root.path()).unwrap();
        let name = c["savedAccounts"][0]["skinFile"]
            .as_str()
            .unwrap()
            .to_owned();
        assert_eq!(c["savedAccounts"][0]["identifier"], "PRIVATE_REF");
        assert_eq!(c["savedAccounts"][0]["capeFrames"], json!(["old.png"]));
        let public = serde_json::to_string(&get_at(root.path(), "Player").unwrap()).unwrap();
        assert!(!public.contains("PRIVATE_REF"));
        assert!(public.contains("data:image/png;base64,"));
        save_at(root.path(), edit("remove_skin", None)).unwrap();
        assert!(get_at(root.path(), "Player").unwrap().skin.is_none());
        assert!(root.path().join("skins").join(name).exists());
    }
    #[test]
    fn wardrobe_rejects_invalid_images_before_any_write() {
        let root = fixture();
        let path = root.path().join("profiles.config.json");
        let before = fs::read(&path).unwrap();
        for value in [
            "data:image/png;base64,AAAA".to_owned(),
            png(32, 32),
            png(65, 64),
        ] {
            assert!(save_at(root.path(), edit("skin", Some(value))).is_err());
            assert_eq!(fs::read(&path).unwrap(), before);
        }
        assert!(!root.path().join("skins").exists());
        assert!(save_at(root.path(), edit("cape", Some(png(64, 64)))).is_err());
    }
    #[test]
    fn wardrobe_cape_replacement_and_model_keep_legacy_contract() {
        let root = fixture();
        save_at(root.path(), edit("model", None)).unwrap();
        assert_eq!(
            lib::config(root.path()).unwrap()["savedAccounts"][0]["capeDelays"],
            json!([80])
        );
        save_at(root.path(), edit("cape", Some(png(64, 32)))).unwrap();
        let c = lib::config(root.path()).unwrap();
        assert!(c["savedAccounts"][0]["capeFrames"]
            .as_array()
            .unwrap()
            .is_empty());
        assert!(get_at(root.path(), "Player").unwrap().cape.is_some());
        save_at(root.path(), edit("remove_cape", None)).unwrap();
        assert!(get_at(root.path(), "Player").unwrap().cape.is_none());
        assert_eq!(
            lib::config(root.path()).unwrap()["appSettings"]["preserve"],
            true
        );
    }
}
