package com.xike.app

internal fun weatherConditionLabel(code: Int): String =
    when (code) {
        0 -> localizedText("晴朗")
        1,
        2 -> localizedText("晴间多云")
        3 -> localizedText("阴")
        45,
        48 -> localizedText("有雾")
        51,
        53,
        55 -> localizedText("毛毛雨")
        56,
        57,
        66,
        67 -> localizedText("冻雨")
        61,
        63,
        65 -> localizedText("有雨")
        71,
        73,
        75,
        77 -> localizedText("有雪")
        80,
        81,
        82 -> localizedText("阵雨")
        85,
        86 -> localizedText("阵雪")
        95 -> localizedText("雷雨")
        96,
        99 -> localizedText("雷雨伴冰雹")
        else -> localizedText("天气未知")
    }
