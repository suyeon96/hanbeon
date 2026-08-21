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
  /**
   * 지금 모드가 통째로 지속되는 시간. 남은 시간 표시의 분모다.
   *
   * 모드마다 다르다 — 순환은 주사 간격, 머무름은 그 1.5배, 되돌리기는 3초.
   * 주사 간격만 보고 그리면 머무름·되돌리기에서 눈금이 마감과 어긋난다.
   */
  phaseMs: number
  /** 이 스냅샷을 만든 시점에 남아 있던 시간. */
  remainingMs: number
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
