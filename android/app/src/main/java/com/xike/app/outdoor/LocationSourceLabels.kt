package com.xike.app.outdoor

import com.xike.app.data.model.LocationSource
import java.util.Locale

internal fun locationSourceName(source: LocationSource, locale: Locale): String =
    when (source) {
        LocationSource.AMAP -> if (locale.language == "en") "AMap" else "高德定位"
        LocationSource.SYSTEM -> if (locale.language == "en") "System location" else "系统定位"
        LocationSource.MANUAL -> if (locale.language == "en") "Manually selected" else "手动选择"
    }
