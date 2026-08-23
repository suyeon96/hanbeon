//! OS별 저장 위치.
//!
//! 경로를 아는 것은 플랫폼 쪽 일이다. 프로필과 기록은 경로를 넘겨받기만 하고
//! 스스로 찾지 않는다. 그래야 같은 코드가 안드로이드처럼 폴더 규칙이 전혀 다른
//! 곳에서도 그대로 돈다.

use std::path::PathBuf;

use tauri::{AppHandle, Manager};

pub fn config_dir(app: &AppHandle) -> Result<PathBuf, String> {
    app.path()
        .app_config_dir()
        .map_err(|e| format!("설정 폴더를 찾지 못했습니다. ({e})"))
}

/// 사용자에게 보여줄 기록 폴더 위치.
///
/// 어디 있는지 모르는 기록은 지울 수도, 진행자에게 건넬 수도 없다(PRD 10.1).
pub fn log_dir(app: &AppHandle) -> Option<PathBuf> {
    app.path().app_log_dir().ok()
}
