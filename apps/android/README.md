# 안드로이드 앱

'한번'의 안드로이드 판(PRD 5.5, M5).

데스크톱과 **같은 Rust 코어**가 돌고 있다. 갤럭시 A15에서 스위치 → 아두이노 →
USB 시리얼 → 코어 → 접근성 서비스 → 크롬까지 한 줄로 이어지는 것을 확인했다
(2026-08-24). 4칸 순환, 간격 적응, 실증 기록이 모두 동작한다.

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

코어(Rust)를 먼저 만들고 그다음 APK를 만든다.

```sh
sh ../../scripts/android/build-core.sh          # .so 를 jniLibs 에 넣는다

export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**NDK 링커 경로를 `<repo>/.cargo/config.toml`에 적어 둬야 한다.** 기기마다 다르므로
커밋하지 않는다.

```toml
[target.aarch64-linux-android]
linker = "<NDK>/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android26-clang"
ar = "<NDK>/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar"
```

숫자 26은 `minSdk`와 맞춘다. 오버레이가 API 26부터라 그 아래는 받지 않는다.

`sdkmanager`로 NDK를 받다 `Failed to download package!`가 나면
[구글에서 직접](https://dl.google.com/android/repository/android-ndk-r28c-darwin.zip)
받는다. 빈 폴더가 남으면 sdkmanager가 '이미 설치됨'으로 건너뛰므로 지우고 다시 한다.

## 검증할 때

**앱을 새로 깔거나 강제 종료하면 안드로이드가 접근성 서비스를 끈다.** 그러면 스위치는
눌리는데 화면이 아무 반응도 하지 않는다. 순서가 중요하다 — 앱을 먼저 띄우고 그다음에
켠다.

```sh
adb shell am start -n kr.devfive.hanbeon/.MainActivity --ez start_overlay true
adb shell settings put secure enabled_accessibility_services \
  kr.devfive.hanbeon/kr.devfive.hanbeon.HanbeonAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

## 지금 있는 것

- `OverlayService` — 포그라운드 서비스가 4칸을 다른 앱 위에 올린다
- `HanbeonAccessibilityService` — 포커스 이동·선택·되돌리기 (출력 전용)
- `HighlightView` — 고르고 있는 요소에 테두리. 안드로이드가 접근성 포커스를 그려 주지
  않아서 우리가 그린다
- `UsbSwitch` — 아두이노 시리얼로 `P`/`R` 수신
- `Core` — Rust 코어로 가는 통로. 스캔 순서·판정·간격 조정은 전부 코어의 것이다

## 실증 기록

코어가 데스크톱과 같은 형식으로 남기므로 **지표 도구를 그대로 쓴다.** 계산이 두
벌이 되면 안드로이드 숫자와 데스크톱 숫자가 갈린다.

```sh
adb shell "run-as kr.devfive.hanbeon cat files/logs/events-2026-08-24.jsonl" > events.jsonl
bun run summary events.jsonl
```

`run-as`는 디버그 빌드에서만 된다. 기록은 앱 전용 폴더에 있어 기기 밖으로 나가지
않는다.

## 아직 없는 것

- 소리. 화면 강조만으로는 원칙 4(두 감각)를 못 지킨다
- 앱별 칸의 단축키 실행. 코어는 칸을 만들지만 안드로이드에 보낼 경로가 없다
- 설정 화면. 지금은 보호자용 권한 화면뿐이다
