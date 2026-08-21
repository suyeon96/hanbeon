/**
 * floating 컨트롤러가 순환시키는 동작. 순서가 곧 스캔 순서다.
 * Rust의 `src-tauri/src/action.rs`와 순서·id가 일치해야 한다.
 */
export const SCAN_ACTIONS = [
  { id: 'next', label: '>', name: '다음으로', hint: 'Tab' },
  { id: 'prev', label: '<', name: '이전으로', hint: 'Shift+Tab' },
  { id: 'enter', label: 'Enter', name: '선택', hint: 'Enter' },
  { id: 'settings', label: '설정', name: '설정 열기', hint: '설정 화면' },
] as const

export type ScanActionId = (typeof SCAN_ACTIONS)[number]['id']

/**
 * 스캔 상태. Rust `scan::Mode`와 일치한다.
 *
 * - `scanning` 커서가 순환 중
 * - `dwelling` 실행한 칸에 머무는 중. 다시 누르면 같은 동작 반복
 * - `confirm`  되돌리기 창. 누르면 직전 선택을 되돌림
 * - `paused`   정지
 */
export type ScanMode = 'scanning' | 'dwelling' | 'confirm' | 'paused'

/** 코어가 `scan://state`로 보내는 커서 상태. */
export interface ScanSnapshot {
  cursor: number
  action: ScanActionId
  mode: ScanMode
  intervalMs: number
}

/** 코어가 `scan://error`로 보내는, 사용자가 알아야 하는 문제. */
export interface ScanError {
  message: string
  needsPermission: boolean
}

/** 기본 주사 간격. 사용자 프로필이 있으면 그 값이 우선한다. */
export const DEFAULT_SCAN_INTERVAL_MS = 1800

/** 선택 직후 이 시간 안에 다시 누르면 되돌리기로 판정한다. */
export const UNDO_WINDOW_MS = 3000
