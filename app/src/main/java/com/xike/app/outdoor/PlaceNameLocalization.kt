package com.xike.app.outdoor

import java.util.Locale
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

private val hanText = Regex("\\p{IsHan}+")

/** Only newly fetched place labels use this fallback; journal text is never transliterated. */
internal fun localizePlaceName(value: String, locale: Locale): String {
    if (locale.language != "en" || !hanText.containsMatchIn(value)) return value
    val format =
        HanyuPinyinOutputFormat().apply {
            toneType = HanyuPinyinToneType.WITHOUT_TONE
            vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
        }
    return hanText.replace(value) { match ->
        val pinyin =
            match.value
                .codePoints()
                .toArray()
                .map { codePoint ->
                    // Unknown characters are retained rather than dropped from the address.
                    val syllable =
                        if (codePoint <= Char.MAX_VALUE.code) {
                            PinyinHelper.toHanyuPinyinStringArray(codePoint.toChar(), format)
                                ?.firstOrNull()
                        } else null
                    syllable?.lowercase(Locale.ROOT) ?: String(Character.toChars(codePoint))
                }
                .joinToString("")
        pinyin.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
}
