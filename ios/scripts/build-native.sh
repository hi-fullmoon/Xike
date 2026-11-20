#!/bin/bash
set -euo pipefail

ios_dir=$(cd "$(dirname "$0")/.." && pwd)
platform=${1:-simulator}
case "$platform" in
    simulator) sdk=iphonesimulator ;;
    device) sdk=iphoneos ;;
    *) echo "Usage: $0 [simulator|device]" >&2; exit 2 ;;
esac

build_dir="$ios_dir/build/native-$platform"
if [[ $platform == device ]]; then
    bash "$ios_dir/scripts/prepare-amap.sh"
fi
cmake -S "$ios_dir/native" -B "$build_dir" \
    -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_SYSROOT="$(xcrun --sdk "$sdk" --show-sdk-path)" \
    -DCMAKE_OSX_DEPLOYMENT_TARGET=17.0 -DCMAKE_OSX_ARCHITECTURES=arm64 \
    -DCMAKE_BUILD_TYPE=Release -DFETCHCONTENT_BASE_DIR="$ios_dir/build/dependencies-$platform"
cmake --build "$build_dir" --target XikeNative --parallel "$(sysctl -n hw.logicalcpu)"
