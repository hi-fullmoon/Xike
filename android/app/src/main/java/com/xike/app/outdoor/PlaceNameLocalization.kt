package com.xike.app.outdoor

import com.github.promeg.pinyinhelper.Pinyin
import com.github.promeg.pinyinhelper.PinyinMapDict
import com.github.promeg.tinypinyin.lexicons.java.cncity.CnCityDict
import java.util.Locale

private val hanText = Regex("\\p{IsHan}+")

// Initialize once on the repository's background dispatcher, before any conversion can run.
private object AddressPinyin {
    private val supplementalReadings =
        object : PinyinMapDict() {
            // The city lexicon omits these traditional forms and the non-city word 银行.
            private val readings =
                mapOf(
                    "重慶" to arrayOf("CHONG", "QING"),
                    "廈門" to arrayOf("XIA", "MEN"),
                    "银行" to arrayOf("YIN", "HANG"),
                    "銀行" to arrayOf("YIN", "HANG"),
                )

            override fun mapping(): Map<String, Array<String>> = readings
        }

    init {
        Pinyin.init(Pinyin.newConfig().with(supplementalReadings).with(CnCityDict.getInstance()))
    }

    fun convert(value: String): String =
        Pinyin.toPinyin(value, "").lowercase(Locale.ROOT).replace('v', 'ü')
}

/** Only newly fetched place labels use this fallback; journal text is never transliterated. */
internal fun localizePlaceName(value: String, locale: Locale): String {
    if (locale.language != "en" || !hanText.containsMatchIn(value)) return value
    return hanText.replace(value) { match ->
        val pinyin = AddressPinyin.convert(match.value)
        pinyin.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
}
