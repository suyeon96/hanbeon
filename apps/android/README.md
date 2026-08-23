# 안드로이드 앱

'한번'의 안드로이드 판(PRD 5.5, M5). 아직 오버레이만 세운 상태다.

## 왜 Tauri가 아닌가

데스크톱은 Tauri지만 여기는 **순수 Gradle 프로젝트**다. Tauri의 안드로이드 지원은
Activity를 주는데, 이 제품에 필요한 것은 **다른 앱 위에 뜨는 오버레이 창**이다.
오버레이는 `SYSTEM_ALERT_WINDOW` 권한을 받은 포그라운드 Service가
`TYPE_APPLICATION_OVERLAY` 창을 직접 올려야 한다. Activity로는 안 된다.

코어(스캔 상태기계, 간격 적응, 프리셋, 프로필, 기록)는 데스크톱과 같은 Rust 코드를
쓴다. `cdylib`으로 빌드해 JNI로 붙인다. 같은 코드를 쓰는 이유는 손이 덜 가서가 아니라,
주사 간격과 눌림 판정이 플랫폼마다 미묘하게 달라지면 사용자가 기기를 옮길 때마다
타이밍을 다시 익혀야 하기 때문이다.

## 데스크톱과 무엇이 다른가

| 하는 일 | 데스크톱 | 안드로이드 |
| --- | --- | --- |
| 다음/이전 요소로 | `Tab` / `Shift+Tab` 주입 | `focusSearch(FOCUS_FORWARD/BACKWARD)` |
| 선택 | `Enter` 주입 | `AccessibilityNodeInfo.performAction(ACTION_CLICK)` |
| 되돌리기 | 단축키 주입 | `performGlobalAction(GLOBAL_ACTION_BACK)` |
| 스위치 입력 | 시리얼 포트 | USB Host API (같은 CDC 프로토콜) |
| 앱 전환 감지 | 300ms 폴링 | `onAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED)` |
| 창이 포커스를 안 뺏게 | NSPanel / `WS_EX_NOACTIVATE` | 오버레이는 애초에 포커스를 안 받는다 |

**스위치 입력은 접근성 서비스로 오지 않는다.** 아두이노 우노가 HID 키보드가 아니라
시리얼로 `P`/`R`을 보내기 때문에(PRD 7절) `onKeyEvent()`에 잡히지 않는다. USB Host
API로 직접 읽어야 한다. 접근성 서비스는 **출력 전용**이다.

## 기기 준비

`scripts/android/README.md`를 따른다. 무선 adb 연결과 점검 도구가 거기 있다.

**Android 13부터 사이드로드한 앱은 접근성 서비스를 그냥 못 켠다.** 설치 후
설정 → 앱 → 한번 → 우측 상단 메뉴 → **제한된 설정 허용**을 먼저 눌러야 접근성
목록에서 토글이 활성화된다. 모르면 "목록에는 뜨는데 안 켜진다"에서 한참 헤맨다.

## 빌드

```sh
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 지금 있는 것

- `OverlayService` — 포그라운드 서비스가 4칸을 다른 앱 위에 올린다
- `MainActivity` — 보호자가 권한을 켜 주는 화면

## 아직 없는 것

- 접근성 서비스(포커스 이동·선택)
- USB 시리얼로 스위치 읽기
- Rust 코어 JNI 연결 — 지금 칸은 껍데기이고 스캔이 돌지 않는다
