#!/bin/bash
set -euo pipefail

ios_dir=$(cd "$(dirname "$0")/.." && pwd)
vendor="$ios_dir/build/amap"
mkdir -p "$vendor"

fetch_framework() {
    local archive="$1"
    local checksum="$2"
    local framework="$3"
    local destination="$vendor/$archive"
    if [[ ! -f "$destination" ]]; then
        curl --fail --silent --show-error --location --retry 2 \
            "https://amappc.oss-cn-zhangjiakou.aliyuncs.com/lbs/static/zip/$archive" \
            --output "$destination.partial"
        mv "$destination.partial" "$destination"
    fi
    if [[ $(shasum -a 256 "$destination" | cut -d ' ' -f 1) != "$checksum" ]]; then
        echo "AMap archive checksum mismatch: $destination" >&2
        return 1
    fi
    if [[ ! -f "$vendor/$framework.framework/$framework" ]]; then
        unzip -q -o "$destination" "$framework.framework/*" -d "$vendor"
    fi
}

fetch_framework AMap_iOS_Loc_Lib_V2.12.2.zip \
    1a62b65b55a2a5a5fcc645cf7afa8509bc74e5490b25548e08be8aac714bce26 AMapLocationKit
fetch_framework AMap_iOS_Foundation_NO_IDFA_Lib_V1.9.1.zip \
    12f5d13232871132f9fd55dc3a1bb84128fd8aa813d795033fc94dd29d130ef5 AMapFoundationKit
