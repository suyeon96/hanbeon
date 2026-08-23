# 안드로이드 검증 도구

M5(PRD 5.5) 작업에 쓰는 도구입니다. 앱을 만들기 전에 기기가 조건을 만족하는지,
스위치가 가정대로 동작하는지 먼저 확인합니다.

전체 Android SDK는 필요 없습니다. `adb` 하나면 됩니다.

```sh
brew install --cask android-platform-tools
```

## 기기 연결

스위치를 USB HID로 붙이면 USB 포트가 스위치 차지라 **adb를 무선으로** 써야 합니다.
Android 11 이상에서 됩니다.

1. 설정 → 휴대전화 정보 → 빌드 번호 7번 탭 (개발자 옵션)
2. 개발자 옵션 → 무선 디버깅 켜기
3. "Wi-Fi로 페어링" → 코드와 주소가 뜹니다

```sh
adb pair <페어링 주소>  <6자리 코드>   # 팝업에 뜬 주소
adb connect <연결 주소>                # 무선 디버깅 메인 화면에 뜬 주소
```

**포트가 두 개입니다.** 페어링 팝업의 포트와 메인 화면의 포트가 다릅니다. 헷갈리면
페어링이 됐는데 연결이 안 됩니다.

`protocol fault (couldn't read status message)`가 나면 `adb kill-server` 후 다시
합니다. 데몬을 갓 띄웠을 때 나는 일이 있습니다.

무선 연결은 기기를 재부팅하면 끊기고 **포트도 바뀝니다.** 다시 `adb connect` 하세요.
페어링은 유지되므로 다시 페어링할 필요는 없습니다.

## check.sh

기기가 '한번'을 올릴 수 있는 상태인지 봅니다. 앱 설치 전에 확인할 수 있는 것만
확인합니다.

```sh
sh scripts/android/check.sh [serial]
```

Android 버전, 오버레이 가능 여부(API 26 이상), USB 호스트(OTG) 지원, 켜져 있는
접근성 서비스, 입력 장치 목록을 냅니다.

## switch-probe.py

스위치가 어떤 키를 어떻게 보내는지 봅니다.

```sh
python3 scripts/android/switch-probe.py <serial> [초]
python3 scripts/android/switch-probe.py --selftest
```

PRD 7절은 스위치가 **특정 키 하나를 눌림 상태로 유지한다**고 가정합니다. 이 가정이
틀리면 짧게/길게 누름을 판정할 수 없어 제품의 절반이 무너집니다. 이 도구는 눌림
시간과 키 리피트 여부를 재서 그 가정을 확인합니다.

**`adb shell input keyevent`로는 검증되지 않습니다.** 그건 InputManager에 바로
넣는 것이라 `/dev/input`을 거치지 않아 `getevent`에 잡히지 않습니다. 실제 하드웨어를
눌러야 합니다.

`--selftest`는 기기 없이 파싱만 검증합니다.
