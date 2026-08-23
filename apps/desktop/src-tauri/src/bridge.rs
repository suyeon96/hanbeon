//! 데스크톱(Tauri)에서 `Host`를 구현한 것.
//!
//! 코어가 "다음 요소로 옮겨라"라고 하면 여기서 `Tab`을 주입한다. 안드로이드에서는
//! 같은 자리에 접근성 서비스 구현이 들어간다.

use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager};

use crate::action::Action;
use crate::audio::{Audio, Cue};
use crate::host::{Host, HostError, Notice};
use crate::profile::{Profile, UndoMapping};
use crate::{emit, window};

/// 커서 상태가 바뀔 때마다 프론트로 보내는 이벤트.
pub const EVENT_STATE: &str = "scan://state";
/// 키 주입 실패처럼 사용자가 알아야 하는 문제.
pub const EVENT_ERROR: &str = "scan://error";
/// 주사 간격이 바뀐 이유. 사용자에게 그대로 보여준다.
pub const EVENT_INTERVAL: &str = "scan://interval";
/// 앱이 바뀌어 스캔 대상이 달라졌음을 알린다.
pub const EVENT_PRESET: &str = "scan://preset";

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ErrorPayload {
    message: String,
    needs_permission: bool,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct PresetPayload {
    message: String,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct IntervalPayload {
    from_ms: u64,
    to_ms: u64,
    reason: String,
}

pub struct TauriHost {
    app: AppHandle,
    audio: Audio,
    /// 프로필을 적을 폴더. 경로를 찾는 것은 플랫폼 쪽 일이다.
    config_dir: Option<PathBuf>,
    /// 저장 실패를 매번 찍지 않기 위한 최근 상태.
    last_save_failed: Mutex<bool>,
}

impl TauriHost {
    pub fn new(app: AppHandle, audio: Audio) -> Arc<Self> {
        let config_dir = crate::paths::config_dir(&app).ok();
        Arc::new(Self {
            app,
            audio,
            config_dir,
            last_save_failed: Mutex::new(false),
        })
    }
}

fn into_host_error(error: emit::EmitError) -> HostError {
    HostError {
        message: error.message,
        needs_permission: error.needs_permission,
    }
}

impl Host for TauriHost {
    fn inject(&self, action: Action) -> Result<(), HostError> {
        emit::send(action).map_err(into_host_error)
    }

    fn undo(&self, mapping: UndoMapping) -> Result<(), HostError> {
        emit::send_undo(mapping).map_err(into_host_error)
    }

    fn open_settings(&self) -> Result<(), HostError> {
        window::show_settings(&self.app).map_err(|message| HostError {
            message,
            needs_permission: false,
        })
    }

    fn fit_cells(&self, extras: usize) {
        if let Some(window) = self.app.get_webview_window("floating") {
            let _ = window::fit_cells(&window, extras);
        }
    }

    fn cue(&self, cue: Cue) {
        self.audio.play(cue);
    }

    fn set_sound(&self, enabled: bool) {
        self.audio.set_enabled(enabled);
    }

    fn publish(&self, notice: Notice) {
        let _ = match notice {
            Notice::State(snapshot) => self.app.emit(EVENT_STATE, *snapshot),
            Notice::Error {
                message,
                needs_permission,
            } => self.app.emit(
                EVENT_ERROR,
                ErrorPayload {
                    message,
                    needs_permission,
                },
            ),
            Notice::Interval {
                from_ms,
                to_ms,
                reason,
            } => self.app.emit(
                EVENT_INTERVAL,
                IntervalPayload {
                    from_ms,
                    to_ms,
                    reason,
                },
            ),
            Notice::Preset { message } => self.app.emit(EVENT_PRESET, PresetPayload { message }),
        };
    }

    fn save_profile(&self, profile: &Profile) {
        let Some(dir) = self.config_dir.as_deref() else {
            return;
        };

        let failed = profile.save(dir).err();
        // 간격 조정은 자주 일어난다. 같은 실패를 매번 찍으면 정작 다른 진단이
        // 묻히므로, 상태가 바뀔 때만 남긴다.
        if let Ok(mut last) = self.last_save_failed.lock() {
            match (&failed, *last) {
                (Some(message), false) => {
                    eprintln!("조정된 속도를 저장하지 못했습니다. {message}");
                    *last = true;
                }
                (None, true) => *last = false,
                _ => {}
            }
        }
    }
}
