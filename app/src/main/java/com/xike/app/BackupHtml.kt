package com.xike.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A static, offline reading copy of the archive. The manifest remains the source for restoration. */
internal object BackupHtml {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")

    fun render(entries: List<JournalEntry>): String = buildString {
        append("""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">""")
        append("""<meta name="viewport" content="width=device-width,initial-scale=1">""")
        append("""<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src file: data:; media-src file:; style-src 'unsafe-inline'">""")
        append("<title>息刻 · 日记备份</title><style>")
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
        append("</style></head><body><main><header><h1>息刻 · 日记备份</h1><p>")
        append(entries.size)
        append(" 条记录 · 解压备份包后可离线阅读</p></header>")
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
                    append(escape(tag))
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
                append("<p class=\"weather\">此刻窗外 · ")
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
                    append("<img loading=\"lazy\" alt=\"日记图片 ")
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
                append("\">浏览器无法播放这段录音。</audio>")
            }
            append("</article>")
        }
        append("<footer>此页面保存在本地。未加密备份中的文字、照片和录音可被拿到文件的人直接查看。</footer></main></body></html>")
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
