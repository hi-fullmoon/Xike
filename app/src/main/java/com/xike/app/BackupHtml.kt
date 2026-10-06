package com.xike.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A static, offline reading copy of the archive. The manifest remains the source for restoration. */
internal object BackupHtml {
    fun render(entries: List<JournalEntry>): String = buildString {
        val dateFormat = DateTimeFormatter.ofPattern(tr("yyyy年M月d日 HH:mm", "MMM d, yyyy HH:mm"), AppLocale.locale)
        append("""<!doctype html><html lang="${AppLocale.language.tag}"><head><meta charset="utf-8">""")
        append("""<meta name="viewport" content="width=device-width,initial-scale=1">""")
        append("""<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src file: data:; media-src file:; style-src 'unsafe-inline'">""")
        append("<title>${tr("息刻 · 日记备份", "Xike · Journal backup")}</title><style>")
        append("""
            :root{color-scheme:light dark}*{box-sizing:border-box}body{margin:0;background:#edf4f2;color:#193936;font:16px/1.7 system-ui,-apple-system,"Noto Sans SC",sans-serif}
            main{max-width:760px;margin:0 auto;padding:42px 18px 72px}header{padding:28px 30px;margin-bottom:26px;border-radius:24px;background:#245c56;color:#fff}
            h1{font-size:30px;line-height:1.25;margin:0 0 8px}header p{margin:0;color:#d9ebe8}article{background:#fff;border:1px solid #dce8e5;border-radius:20px;padding:25px 28px;margin:18px 0;box-shadow:0 4px 20px #17433b0a}
            .top{display:flex;justify-content:space-between;gap:12px;align-items:baseline}.date{color:#687d79;font-size:14px}.mood{font-weight:700;font-size:18px}.tags{display:flex;gap:7px;flex-wrap:wrap;margin:14px 0}
            .tag{background:#edf4f2;color:#245c56;border-radius:99px;padding:2px 10px;font-size:13px}.note{white-space:pre-wrap;overflow-wrap:anywhere;margin:16px 0}
            .weather{color:#687d79;font-size:14px}.photos{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px;margin-top:16px}
            img{max-width:100%;max-height:400px;object-fit:contain;border-radius:12px;background:#edf4f2}audio{width:100%;margin-top:14px}footer{color:#687d79;font-size:13px;margin-top:28px}
            @media(max-width:540px){main{padding:18px 12px 44px}header,article{padding:20px}}
            @media(prefers-color-scheme:dark){body{background:#152421;color:#e8f2ef}article{background:#1d302c;border-color:#35504a}.date,.weather,footer{color:#adc3bc}.tag{background:#34534d;color:#e3f4ef}}
        """.trimIndent())
        append("</style></head><body><main><header><h1>${tr("息刻 · 日记备份", "Xike · Journal backup")}</h1><p>")
        append(entries.size)
        append(tr(" 条记录 · 解压备份包后可离线阅读", " entries · Extract the backup to read offline"))
        append("</p></header>")
        entries.sortedByDescending(JournalEntry::createdAt).forEach { entry ->
            append("<article><div class=\"top\"><span class=\"mood\">")
            append(entry.mood.emoji)
            append(" ")
            append(escape(entry.mood.label))
            append("</span><time class=\"date\">")
            append(escape(dateFormat.format(Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault()))))
            append("</time></div>")
            if (entry.tags.isNotEmpty()) {
                append("<div class=\"tags\">")
                entry.tags.forEach { tag ->
                    append("<span class=\"tag\">")
                    append(escape(localizedText(tag)))
                    append("</span>")
                }
                append("</div>")
            }
            if (entry.note.isNotBlank()) {
                append("<p class=\"note\">")
                append(escape(entry.note))
                append("</p>")
            }
            entry.outdoor?.let { outdoor ->
                append("<p class=\"weather\">${localizedText("此刻窗外")} · ")
                append(escape(outdoor.placeName))
                append(" · ")
                append(outdoor.temperatureCelsius.toInt())
                append("° · ")
                append(escape(weatherConditionLabel(outdoor.weatherCode)))
                append("</p>")
            }
            if (entry.imageFileNames.isNotEmpty()) {
                append("<div class=\"photos\">")
                entry.imageFileNames.forEachIndexed { index, fileName ->
                    append("<img loading=\"lazy\" alt=\"${tr("日记图片", "Journal photo")} ")
                    append(index + 1)
                    append("\" src=\"images/")
                    append(escape(fileName))
                    append("\">")
                }
                append("</div>")
            }
            entry.audio?.let { audio ->
                append("<audio controls preload=\"none\" src=\"audios/")
                append(escape(audio.fileName))
                append("\">${tr("浏览器无法播放这段录音。", "Your browser cannot play this recording.")}</audio>")
            }
            append("</article>")
        }
        append("<footer>${tr("此页面保存在本地。未加密备份中的文字、照片和录音可被拿到文件的人直接查看。", "This page is stored locally. Anyone with an unencrypted backup can view its text, photos and recordings.")}</footer></main></body></html>")
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(when (char) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> char.toString()
            })
        }
    }
}
