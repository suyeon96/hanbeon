//! 스캔 대상이 되는 칸. 배열 순서가 곧 스캔 순서다.
//!
//! 앞 4칸(`>`, `<`, `Enter`, `설정`)은 **언제나 이 순서로 맨 앞에 있다.**
//! 앱별 칸은 그 뒤에만 붙는다. 사용자는 자리로 동작을 기억하므로, 앞 4칸의
//! 자리가 앱에 따라 달라지면 익힌 것이 모두 무효가 된다.

use crate::shortcut::Shortcut;

/// 한 칸을 눌렀을 때 일어나는 일.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Action {
    /// Tab — 다음 요소로
    Next,
    /// Shift+Tab — 이전 요소로
    Prev,
    /// Enter — 선택
    Enter,
    /// 설정 화면 진입
    Settings,
    /// 앱별 칸이 보내는 키 조합.
    Shortcut(Shortcut),
}

/// 화면에 그려지는 칸 하나.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Cell {
    /// 칸에 크게 적히는 글자.
    pub label: String,
    /// 커서가 왔을 때 아래에 작게 적히는 이름.
    pub name: String,
    pub action: Action,
}

impl Cell {
    fn new(label: &str, name: &str, action: Action) -> Self {
        Self {
            label: label.to_string(),
            name: name.to_string(),
            action,
        }
    }
}

/// 어떤 앱에서도 바뀌지 않는 앞 4칸.
pub fn base_cells() -> Vec<Cell> {
    vec![
        Cell::new(">", "다음으로", Action::Next),
        Cell::new("<", "이전으로", Action::Prev),
        Cell::new("Enter", "선택", Action::Enter),
        Cell::new("설정", "설정 열기", Action::Settings),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 앞_네_칸의_순서는_고정이다() {
        let cells = base_cells();
        assert_eq!(cells.len(), 4);
        assert_eq!(cells[0].action, Action::Next);
        assert_eq!(cells[1].action, Action::Prev);
        assert_eq!(cells[2].action, Action::Enter);
        assert_eq!(cells[3].action, Action::Settings);
    }
}
