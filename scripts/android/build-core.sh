#!/bin/sh
# 코어(Rust)를 안드로이드용으로 빌드해 APK가 집어 갈 자리에 둔다.
#
# NDK 경로는 기기마다 다르므로 `.cargo/config.toml`에 적어 둔다(커밋하지 않는다).
# 그 파일이 없으면 링커를 못 찾아 알 수 없는 오류로 끝난다.
set -e

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TARGET=aarch64-linux-android
OUT="$ROOT/apps/android/app/src/main/jniLibs/arm64-v8a"

if [ ! -f "$ROOT/.cargo/config.toml" ]; then
  echo "'.cargo/config.toml'이 없습니다. NDK 링커 경로를 먼저 적어 주세요." >&2
  echo "apps/android/README.md 참고." >&2
  exit 1
fi

cd "$ROOT"
cargo build -p hanbeon-jni --target "$TARGET" --release

mkdir -p "$OUT"
cp "target/$TARGET/release/libhanbeon_jni.so" "$OUT/"
echo "넣었습니다: $OUT/libhanbeon_jni.so"
