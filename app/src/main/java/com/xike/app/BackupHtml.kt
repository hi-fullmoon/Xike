package com.xike.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URI

/** A static, offline reading copy of the archive. The manifest remains the source for restoration. */
internal object BackupHtml {
    fun render(entries: List<JournalEntry>): String = buildString {
        val scriptNonce = java.util.UUID.randomUUID().toString()
        val dateFormat = DateTimeFormatter.ofPattern(tr("yyyy年M月d日 HH:mm", "MMM d, yyyy HH:mm"), AppLocale.locale)
        append("""<!doctype html><html lang="${AppLocale.language.tag}"><head><meta charset="utf-8">""")
        append("""<meta name="viewport" content="width=device-width,initial-scale=1">""")
        append("""<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src file: data:; media-src file:; style-src 'unsafe-inline'; script-src 'nonce-$scriptNonce'; base-uri 'none'; form-action 'none'">""")
        append("<title>${tr("息刻 · 日记备份", "Xike · Journal backup")}</title><style>")
        append("""
            :root{color-scheme:light dark}*{box-sizing:border-box}body{margin:0;background:#edf4f2;color:#193936;font:16px/1.7 system-ui,-apple-system,"Noto Sans SC",sans-serif}
            main{max-width:760px;margin:0 auto;padding:42px 18px 72px}header{padding:28px 30px;margin-bottom:26px;border-radius:24px;background:#245c56;color:#fff}
            h1{font-size:30px;line-height:1.25;margin:0 0 8px}header p{margin:0;color:#d9ebe8}article{background:#fff;border:1px solid #dce8e5;border-radius:20px;padding:25px 28px;margin:18px 0;box-shadow:0 4px 20px #17433b0a}
            .top{display:flex;justify-content:space-between;gap:12px;align-items:baseline}.date{color:#687d79;font-size:14px}.mood{font-weight:700;font-size:18px}.tags{display:flex;gap:7px;flex-wrap:wrap;margin:14px 0}
            .tag{background:#edf4f2;color:#245c56;border-radius:99px;padding:2px 10px;font-size:13px}.note{white-space:pre-wrap;overflow-wrap:anywhere;margin:16px 0}
            .weather{color:#687d79;font-size:14px;margin:12px 0}.photos{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:18px;max-width:560px}
            .photos[data-count="1"]{grid-template-columns:minmax(0,1fr);max-width:440px}.photos[data-count="2"],.photos[data-count="4"]{grid-template-columns:repeat(2,minmax(0,1fr));max-width:440px}
            .photo{display:block;overflow:hidden;border-radius:12px;background:#edf4f2;aspect-ratio:1;cursor:zoom-in}.photo img{display:block;width:100%;height:100%;object-fit:cover}
            .photos[data-count="1"] .photo{aspect-ratio:auto}.photos[data-count="1"] img{height:auto;max-height:440px;object-fit:contain}
            .photo:focus-visible,button:focus-visible{outline:3px solid #62bfb0;outline-offset:3px}audio{width:100%;margin-top:18px}footer{color:#687d79;font-size:13px;margin-top:28px}
            .viewer{position:fixed;inset:0;width:100vw;height:100dvh;max-width:none;max-height:none;margin:0;padding:20px;border:0;background:#101916f5;color:#fff;overflow:auto}
            .viewer::backdrop{background:#101916}.viewer[open]{display:flex;flex-direction:column;align-items:center;gap:16px}.viewer-bar{display:flex;align-items:center;justify-content:space-between;width:100%;gap:16px}
            .viewer button{font:inherit;color:#fff;border:1px solid #ffffff40;border-radius:99px;background:#ffffff15;min-width:44px;min-height:44px;padding:6px 16px;cursor:pointer}
            .viewer-image{display:block;max-width:100%;height:calc(100dvh - 160px);object-fit:contain;min-height:0}.viewer-nav{display:flex;align-items:center;gap:20px}.viewer button:disabled{opacity:.35;cursor:default}
            .viewer-error{margin:auto;text-align:center}.viewer-error a{color:#b9e9df}.viewer-error[hidden],.viewer-image[hidden]{display:none}
            body:has(.viewer[open]){overflow:hidden}
            @media(max-width:540px){main{padding:18px 12px 44px}header,article{padding:20px}h1{font-size:25px}.top{flex-wrap:wrap;gap:4px 12px}.date{font-size:12px}.photos{gap:6px}.photo{border-radius:8px}.viewer{padding:12px}}
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
                append("<div class=\"photos\" data-count=\"${entry.imageFileNames.size}\">")
                entry.imageFileNames.forEachIndexed { index, fileName ->
                    val url = mediaUrl("images", fileName)
                    append("<a class=\"photo\" href=\"$url\" aria-label=\"${tr("放大查看图片", "Enlarge photo")} ${index + 1}\"><img loading=\"lazy\" alt=\"${tr("日记图片", "Journal photo")} ")
                    append(index + 1)
                    append("\" src=\"")
                    append(url)
                    append("\"></a>")
                }
                append("</div>")
            }
            entry.audio?.let { audio ->
                append("<audio controls preload=\"none\" src=\"")
                append(mediaUrl("audios", audio.fileName))
                append("\">${tr("浏览器无法播放这段录音。", "Your browser cannot play this recording.")}</audio>")
            }
            append("</article>")
        }
        append("<footer>${tr("此页面保存在本地。未加密备份中的文字、照片和录音可被拿到文件的人直接查看。", "This page is stored locally. Anyone with an unencrypted backup can view its text, photos and recordings.")}</footer></main>")
        append("""<dialog class="viewer" aria-label="${tr("图片预览", "Photo preview")}"><div class="viewer-bar"><span class="viewer-count" aria-live="polite"></span><button class="viewer-close" autofocus>${tr("关闭", "Close")} ×</button></div><img class="viewer-image" alt=""><p class="viewer-error" role="status" hidden>${tr("图片无法显示，文件可能缺失或浏览器不支持此格式。", "Unable to display this photo. The file may be missing or its format unsupported.")} <a target="_blank" rel="noopener">${tr("打开原文件", "Open original file")}</a></p><div class="viewer-nav"><button class="viewer-prev" aria-label="${tr("上一张", "Previous photo")}">←</button><button class="viewer-next" aria-label="${tr("下一张", "Next photo")}">→</button></div></dialog>""")
        append("""<script nonce="$scriptNonce">
            (() => {
                const viewer = document.querySelector('.viewer');
                const image = viewer.querySelector('.viewer-image');
                const previous = viewer.querySelector('.viewer-prev');
                const next = viewer.querySelector('.viewer-next');
                const error = viewer.querySelector('.viewer-error');
                let photos = [], current = 0, opener;
                function show(index) {
                    current = index;
                    image.hidden = false;
                    error.hidden = true;
                    error.querySelector('a').href = photos[current].href;
                    image.src = photos[current].href;
                    image.alt = photos[current].querySelector('img').alt;
                    viewer.querySelector('.viewer-count').textContent = (current + 1) + ' / ' + photos.length;
                    previous.disabled = current === 0;
                    next.disabled = current === photos.length - 1;
                }
                image.addEventListener('error', () => {
                    if (!viewer.open || !image.hasAttribute('src')) return;
                    image.hidden = true;
                    error.hidden = false;
                });
                document.querySelectorAll('.photo').forEach(photo => photo.addEventListener('click', event => {
                    if (typeof viewer.showModal !== 'function' || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
                    event.preventDefault();
                    opener = photo;
                    photos = Array.from(photo.closest('.photos').querySelectorAll('.photo'));
                    show(photos.indexOf(photo));
                    viewer.showModal();
                }));
                viewer.querySelector('.viewer-close').addEventListener('click', () => viewer.close());
                previous.addEventListener('click', () => { if (current > 0) show(current - 1); });
                next.addEventListener('click', () => { if (current < photos.length - 1) show(current + 1); });
                viewer.addEventListener('click', event => { if (event.target === viewer) viewer.close(); });
                viewer.addEventListener('keydown', event => {
                    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
                        event.preventDefault();
                        if (event.key === 'ArrowLeft' && current > 0) show(current - 1);
                        if (event.key === 'ArrowRight' && current < photos.length - 1) show(current + 1);
                    }
                });
                viewer.addEventListener('close', () => {
                    if (viewer.open) return;
                    image.removeAttribute('src');
                    if (opener) opener.focus({preventScroll:true});
                });
            })();
        </script></body></html>""".trimIndent())
    }

    private fun mediaUrl(directory: String, fileName: String): String =
        escape(URI(null, null, "$directory/$fileName", null).toASCIIString())

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
