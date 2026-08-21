//! 스캔 대상이 되는 동작. 순서가 곧 스캔 순서다.
//!
//! 프론트의 `src/lib/actions.ts`와 순서·id가 일치해야 한다.

use serde::Serialize;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum Action {
    /// Tab — 다음 요소로
    Next,
    /// Shift+Tab — 이전 요소로
    Prev,
    /// Enter — 선택
    Enter,
    /// 설정 화면 진입
    Settings,
}

pub const SCAN_ORDER: [Action; 4] = [Action::Next, Action::Prev, Action::Enter, Action::Settings];

impl Action {
    pub fn at(index: usize) -> Self {
        SCAN_ORDER[index % SCAN_ORDER.len()]
    }
}
