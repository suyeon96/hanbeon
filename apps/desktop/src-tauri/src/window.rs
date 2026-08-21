//! floating 컨트롤러 창의 배치와 플랫폼별 창 속성.

use tauri::{AppHandle, Manager, PhysicalPosition, WebviewWindow};

/// 화면 가장자리에서 띄울 여백(논리 px).
const EDGE_MARGIN: f64 = 24.0;

pub fn prepare_floating(window: &WebviewWindow) -> tauri::Result<()> {
    place_bottom_right(window)?;
    make_non_activating(window);
    Ok(())
}

/// 기본 위치는 주 모니터 우하단. 사용자가 옮긴 위치는 프로필에 저장해 복원한다(M3).
fn place_bottom_right(window: &WebviewWindow) -> tauri::Result<()> {
    let Some(monitor) = window.current_monitor()? else {
        return Ok(());
    };

    let scale = monitor.scale_factor();
    let screen = monitor.size();
    let origin = monitor.position();
    let size = window.outer_size()?;
    let margin = (EDGE_MARGIN * scale) as i32;

    let x = origin.x + screen.width as i32 - size.width as i32 - margin;
    let y = origin.y + screen.height as i32 - size.height as i32 - margin;

    window.set_position(PhysicalPosition::new(x, y))
}

/// floating 창이 대상 앱의 포커스를 뺏으면 안 된다. 포커스를 가져가는 순간
/// 우리가 주입한 Tab/Enter가 대상 앱이 아니라 우리 창으로 들어간다.
///
/// macOS는 `ActivationPolicy::Accessory`(lib.rs)로 앱 자체의 활성화를 먼저 막는다.
/// 그것만으로 창 클릭 시 활성화가 남는다면 NSPanel + NonactivatingPanel로 승격한다.
#[allow(unused_variables)]
fn make_non_activating(window: &WebviewWindow) {
    #[cfg(target_os = "windows")]
    {
        // TODO(M1-win): WS_EX_NOACTIVATE 추가. Windows 실기에서 검증한다.
    }
}

/// 설정 창은 floating 컨트롤러와 요구가 정반대다. 조작을 받아야 하므로
/// 반드시 활성화되어야 한다. Accessory 정책인 채로 열면 창은 보이지만
/// 포커스가 가지 않아 아무것도 할 수 없다.
pub fn show_settings(app: &AppHandle) -> Result<(), String> {
    let window = app
        .get_webview_window("settings")
        .ok_or_else(|| "설정 창을 찾을 수 없습니다.".to_string())?;

    #[cfg(target_os = "macos")]
    let _ = app.set_activation_policy(tauri::ActivationPolicy::Regular);

    window.show().map_err(|e| e.to_string())?;
    window.set_focus().map_err(|e| e.to_string())?;
    Ok(())
}

/// 설정 창을 닫으면 다시 보조 도구로 내려간다.
/// 그래야 floating 컨트롤러가 대상 앱의 포커스를 뺏지 않는다.
pub fn hide_settings(app: &AppHandle) -> Result<(), String> {
    let window = app
        .get_webview_window("settings")
        .ok_or_else(|| "설정 창을 찾을 수 없습니다.".to_string())?;
    window.hide().map_err(|e| e.to_string())?;

    #[cfg(target_os = "macos")]
    let _ = app.set_activation_policy(tauri::ActivationPolicy::Accessory);

    Ok(())
}
