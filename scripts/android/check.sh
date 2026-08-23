#!/bin/sh
# 안드로이드 기기가 '한번'을 올릴 수 있는 상태인지 본다.
# 앱을 깔기 전에 확인할 수 있는 것만 확인한다.

set -u

say() { printf '\n=== %s ===\n' "$1"; }

say "연결된 기기"
adb devices -l

SERIAL=${1:-$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')}
if [ -z "$SERIAL" ]; then
  echo "기기가 잡히지 않았습니다."
  exit 1
fi
echo "대상: $SERIAL"

prop() { adb -s "$SERIAL" shell getprop "$1" 2>/dev/null | tr -d '\r'; }

say "기기"
printf '제조사   %s\n' "$(prop ro.product.manufacturer)"
printf '모델     %s\n' "$(prop ro.product.model)"
printf 'Android  %s (API %s)\n' "$(prop ro.build.version.release)" "$(prop ro.build.version.sdk)"
printf 'ABI      %s\n' "$(prop ro.product.cpu.abi)"

SDK=$(prop ro.build.version.sdk)

say "이 프로젝트에 걸리는 조건"
# 오버레이(TYPE_APPLICATION_OVERLAY)는 API 26부터.
if [ "$SDK" -ge 26 ] 2>/dev/null; then
  echo "OK   오버레이 창 가능 (API $SDK >= 26)"
else
  echo "문제 오버레이 창 불가 (API $SDK < 26)"
fi

# Android 13(33)부터 사이드로드 앱은 '제한된 설정'을 풀어야 접근성 서비스를 켤 수 있다.
if [ "$SDK" -ge 33 ] 2>/dev/null; then
  echo "주의 API $SDK — 설치 후 설정 > 앱 > 한번 > 제한된 설정 허용을 먼저 눌러야"
  echo "     접근성 목록에서 토글이 활성화됨"
fi

say "접근성 서비스 현황"
ENABLED=$(adb -s "$SERIAL" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')
echo "켜져 있는 서비스: ${ENABLED:-없음}"
# 내장 스위치 제어가 켜져 있으면 스위치 한 번에 두 곳이 반응한다.
case "$ENABLED" in
  *SwitchAccess*|*switchaccess*)
    echo "주의 안드로이드 내장 '스위치 제어'가 켜져 있음. 검증 중에는 끄는 것을 권함"
    ;;
esac

say "USB 호스트(OTG) 지원"
# 스위치를 USB HID로 붙이려면 기기가 USB 호스트여야 한다.
if adb -s "$SERIAL" shell pm list features 2>/dev/null | tr -d '\r' | grep -q "android.hardware.usb.host"; then
  echo "OK   USB 호스트 지원. OTG로 스위치를 붙일 수 있음"
else
  echo "문제 USB 호스트 미지원. USB HID 스위치를 못 붙임 (블루투스 HID로 가야 함)"
fi

say "입력 장치"
# 스위치를 꽂은 상태라면 여기에 키보드로 잡혀야 한다.
adb -s "$SERIAL" shell getevent -p 2>/dev/null | tr -d '\r' \
  | awk '/^add device/{dev=$3} /name:/{sub(/^ *name: */,""); print dev, $0}'

say "끝"
