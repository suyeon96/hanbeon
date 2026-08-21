//! '한번' 데스크톱 코어.
//!
//! 스캔 상태기계·입력 판정·키 주입은 프론트가 아니라 이 코어에 둔다.
//! floating 창이 가려지거나 렌더링이 지연돼도 주사 간격이 흔들리면 안 되고,
//! 스위치 입력 판정(짧게/길게)은 대상 앱으로 키를 보내는 지점과 같은 쪽에
//! 있어야 지연을 예측할 수 있기 때문이다.

mod action;
mod adapt;
mod audio;
mod emit;
mod input;
mod profile;
mod scan;
mod tray;
mod window;

use std::sync::{Arc, Mutex};
use std::time::Duration;

use serde::Serialize;
use tauri::{AppHandle, Manager, State};

use audio::Audio;
use input::{GestureDetector, SharedDetector};
use profile::Profile;
use scan::{Scanner, Snapshot};

/// 코어가 들고 있는 현재 프로필. 설정 화면과 적응 로직이 함께 쓴다.
struct SharedProfile(Arc<Mutex<Profile>>);

/// 설정 저장 결과.
///
/// 일부만 실패할 수 있어서 경고를 함께 돌려준다. 스위치 키 등록이 실패했다고
/// 나머지 설정까지 버리면 사용자는 방금 맞춘 속도를 다시 잡아야 한다.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SaveResult {
    profile: Profile,
    warning: Option<String>,
}

/// 프론트가 창을 띄운 직후 현재 커서를 맞추기 위해 호출한다.
/// 이벤트만으로는 첫 틱이 올 때까지 화면이 비어 있게 된다.
#[tauri::command]
fn scan_snapshot(scanner: State<'_, Scanner>) -> Result<Snapshot, String> {
    scanner
        .snapshot()
        .ok_or_else(|| "스캔 상태를 읽지 못했습니다.".to_string())
}

#[tauri::command]
fn get_profile(shared: State<'_, SharedProfile>) -> Result<Profile, String> {
    shared
        .0
        .lock()
        .map(|profile| profile.clone())
        .map_err(|_| "설정을 읽지 못했습니다.".to_string())
}

#[tauri::command]
fn save_profile(
    app: AppHandle,
    mut next: Profile,
    shared: State<'_, SharedProfile>,
    scanner: State<'_, Scanner>,
    detector: State<'_, SharedDetector>,
) -> Result<SaveResult, String> {
    next.sanitize();

    let previous_key = shared
        .0
        .lock()
        .map(|profile| profile.switch_key.clone())
        .map_err(|_| "설정을 읽지 못했습니다.".to_string())?;

    // 스위치 키가 바뀌면 먼저 붙여본다. 실패하면 키만 되돌리고 나머지는 살린다.
    let mut warning = None;
    if next.switch_key != previous_key {
        let old = input::configured_code(&previous_key);
        let new = input::configured_code(&next.switch_key);
        if let Err(message) = input::rebind(&app, old, new) {
            next.switch_key = previous_key;
            warning = Some(message);
        }
    }

    next.save(&app)?;

    if let Ok(mut profile) = shared.0.lock() {
        *profile = next.clone();
    }
    scanner.apply_profile();
    if let Ok(mut detector) = detector.lock() {
        detector.set_long_press(Duration::from_millis(next.long_press_ms));
    }

    Ok(SaveResult {
        profile: next,
        warning,
    })
}

#[tauri::command]
fn open_settings(app: AppHandle) -> Result<(), String> {
    window::show_settings(&app)
}

#[tauri::command]
fn close_settings(app: AppHandle) -> Result<(), String> {
    window::hide_settings(&app)
}

pub fn run() {
    let app = tauri::Builder::default()
        .setup(|app| {
            // 앱을 보조 도구로 낮춘다. Dock/전환기에 뜨지 않고,
            // 활성 앱을 가로채지 않아 키 주입 대상이 유지된다.
            #[cfg(target_os = "macos")]
            app.set_activation_policy(tauri::ActivationPolicy::Accessory);

            if let Some(floating) = app.get_webview_window("floating") {
                window::prepare_floating(&floating)?;
            }

            tray::setup(app)?;

            let mut profile = Profile::load(app.handle());
            // 검증용 통로. 저장된 설정을 건드리지 않고 시작 간격만 바꿔 끼운다.
            if let Some(interval_ms) = scan::interval_override() {
                profile.interval_ms = interval_ms;
                profile.max_interval_ms = profile.max_interval_ms.max(interval_ms);
                profile.sanitize();
            }

            if std::env::var("HANBEON_LOG").is_ok() {
                eprintln!(
                    "[profile] 간격 {}ms (범위 {}~{}ms), 적응 {}, 수동고정 {}, 길게누름 {}ms, 소리 {}, 초기설정 {}",
                    profile.interval_ms,
                    profile.min_interval_ms,
                    profile.max_interval_ms,
                    profile.adaptive,
                    profile.manual_lock,
                    profile.long_press_ms,
                    profile.sound,
                    profile.onboarded,
                );
            }

            let audio = Audio::spawn();
            audio.set_enabled(profile.sound);

            let switch_code = input::configured_code(&profile.switch_key);
            let detector: SharedDetector = Arc::new(Mutex::new(GestureDetector::new(
                Duration::from_millis(profile.long_press_ms),
            )));

            let profile = Arc::new(Mutex::new(profile));
            let scanner = Scanner::new(Arc::clone(&profile), audio);

            app.manage(SharedProfile(Arc::clone(&profile)));
            app.manage(Arc::clone(&detector));
            app.manage(scanner.clone());

            scanner.start(app.handle().clone());
            input::register(app.handle(), detector, switch_code, move |app, gesture| {
                scanner.handle(app, gesture);
            })?;

            Ok(())
        })
        .on_window_event(|window, event| {
            // 설정 창을 닫아도 앱은 살아 있어야 한다. 창을 파괴하는 대신 숨기고
            // 보조 도구 정책으로 되돌린다. 그러지 않으면 이후 floating 컨트롤러가
            // 대상 앱의 포커스를 계속 뺏는다.
            if let tauri::WindowEvent::CloseRequested { api, .. } = event
                && window.label() == "settings"
            {
                api.prevent_close();
                let _ = window::hide_settings(window.app_handle());
            }
        })
        .invoke_handler(tauri::generate_handler![
            scan_snapshot,
            get_profile,
            save_profile,
            open_settings,
            close_settings
        ])
        .build(tauri::generate_context!())
        .expect("한번 앱을 시작하지 못했습니다");

    app.run(|app, event| {
        // 적응으로 조정된 간격은 메모리에만 있다. 종료할 때 한 번 적어 두어야
        // 다음에 켰을 때 사용자가 익숙해진 속도로 시작한다.
        if let tauri::RunEvent::Exit = event
            && let Some(shared) = app.try_state::<SharedProfile>()
            && let Ok(profile) = shared.0.lock()
            && let Err(message) = profile.save(app)
        {
            eprintln!("종료하며 설정을 저장하지 못했습니다. {message}");
        }
    });
}
