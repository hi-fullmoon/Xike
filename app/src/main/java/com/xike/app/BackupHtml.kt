package com.xike.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URI

/** A static, offline reading copy of the archive. The manifest remains the source for restoration. */
internal object BackupHtml {
    fun render(entries: List<JournalEntry>): String = buildString {
        val scriptNonce = java.util.UUID.randomUUID().toString()
        val dateFormat = DateTimeFormatter.ofPattern(tr("yyyy年M月d日", "MMM d, yyyy"), AppLocale.locale)
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm", AppLocale.locale)
        append("""<!doctype html><html lang="${AppLocale.language.tag}"><head><meta charset="utf-8">""")
        append("""<meta name="viewport" content="width=device-width,initial-scale=1">""")
        append("""<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src file: data:; media-src file:; style-src 'unsafe-inline'; script-src 'nonce-$scriptNonce'; base-uri 'none'; form-action 'none'">""")
        append("<title>${tr("息刻 · 日记备份", "Xike · Journal backup")}</title><style>")
        append("""
            :root{color-scheme:light dark;--canvas:#f5f3ef;--paper:#fffefc;--ink:#303742;--muted:#6f7480;--line:#e4e1db;--soft:#edf1f5;--accent:#435d78;--ornament:#b38d66}
            *{box-sizing:border-box}body{margin:0;background:var(--canvas);color:var(--ink);font:16px/1.8 system-ui,-apple-system,"Noto Sans SC",sans-serif;-webkit-font-smoothing:antialiased}
            main{max-width:800px;margin:0 auto;padding:32px 24px 40px}header{position:relative;overflow:hidden;padding:24px 28px;margin-bottom:22px;border:1px solid var(--line);border-radius:12px;background:var(--paper);color:var(--ink);box-shadow:0 3px 16px #333b4905}
            header::before{content:"";display:block;width:28px;height:2px;border-radius:2px;background:var(--ornament);margin-bottom:12px}
            h1{font-family:ui-serif,Georgia,"Songti SC","Noto Serif SC",serif;font-size:28px;font-weight:600;letter-spacing:-.02em;line-height:1.3;margin:0 0 6px}header p{margin:0;color:var(--muted);font-size:13px;letter-spacing:.02em}
            article{display:flex;flex-direction:column;gap:12px;background:var(--paper);border:1px solid var(--line);border-radius:10px;padding:16px 20px;margin:16px 0;box-shadow:0 2px 8px #333b4904}
            .top{display:flex;flex-wrap:wrap;gap:8px 14px;align-items:center}.mood{display:flex;align-items:center;gap:8px;font-weight:600;font-size:15px;flex:none}
            .mood-emoji{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:8px;background:transparent;font-size:18px;flex:none}
            .tags{display:flex;gap:6px;flex-wrap:wrap;min-width:0;max-width:100%;flex:0 1 auto}.tag{background:var(--soft);color:var(--accent);border:1px solid transparent;border-radius:6px;padding:2px 8px;font-size:12px;max-width:100%;overflow-wrap:anywhere}
            .note{white-space:pre-wrap;overflow-wrap:anywhere;margin:0;font-size:16px;line-height:1.7}.weather{color:var(--muted);font-size:12px;margin:0;overflow-wrap:anywhere}
            .photos{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;max-width:280px;width:100%}
            .photos[data-count="1"]{grid-template-columns:minmax(0,1fr);max-width:180px}.photos[data-count="2"],.photos[data-count="4"]{grid-template-columns:repeat(2,minmax(0,1fr));max-width:184px}
            .photo{display:block;overflow:hidden;border-radius:6px;background:var(--soft);aspect-ratio:1;cursor:zoom-in}.photo img{display:block;width:100%;height:100%;object-fit:cover}
            .photos[data-count="1"] .photo{aspect-ratio:auto}.photos[data-count="1"] img{height:auto;max-height:200px;object-fit:contain}
            .photo:focus-visible,button:focus-visible{outline:3px solid #809abc;outline-offset:3px}audio{display:block;width:100%;margin:0;border-radius:12px}footer{color:var(--muted);font-size:12px;line-height:1.7;margin-top:24px;padding:16px 12px 0;border-top:1px solid var(--line);text-align:center}
            .viewer{position:fixed;inset:0;width:100vw;height:100dvh;max-width:none;max-height:none;margin:0;padding:20px;border:0;background:#12171ef5;color:#fff;overflow:auto}
            .viewer::backdrop{background:#12171e}.viewer[open]{display:flex;flex-direction:column;align-items:center;gap:16px}.viewer-bar{display:flex;align-items:center;justify-content:space-between;width:100%;gap:16px}
            .viewer button{font:inherit;color:#fff;border:1px solid #ffffff40;border-radius:99px;background:#ffffff15;min-width:44px;min-height:44px;padding:6px 16px;cursor:pointer}
            .viewer-image{display:block;max-width:100%;height:calc(100dvh - 160px);object-fit:contain;min-height:0}.viewer-nav{display:flex;align-items:center;gap:20px}.viewer button:disabled{opacity:.35;cursor:default}
            .viewer-error{margin:auto;text-align:center}.viewer-error a{color:#c0d3ec}.viewer-error[hidden],.viewer-image[hidden]{display:none}
            body:has(.viewer[open]){overflow:hidden}
            .timeline-entry{position:relative;margin-left:24px;padding-bottom:18px}
            .timeline-entry::before{content:"";position:absolute;left:-18px;top:0;bottom:0;width:1px;background:var(--line);pointer-events:none}
            .timeline-entry::after{content:"";position:absolute;left:-22px;top:10px;width:9px;height:9px;border:2px solid var(--accent);border-radius:50%;background:var(--paper);box-shadow:0 0 0 4px var(--canvas);pointer-events:none}
            .timeline-entry:first-of-type::before{top:14px}.timeline-entry:last-of-type::before{bottom:auto;height:14px}.timeline-entry:last-of-type{padding-bottom:0}
            .timeline-entry:only-of-type::before{display:none}
            .timeline-date{display:flex;align-items:baseline;flex-wrap:wrap;gap:4px 12px;min-height:28px;line-height:28px;font-variant-numeric:tabular-nums;color:var(--accent)}
            .timeline-date strong{font-size:17px;font-weight:650}.timeline-clock{font-size:13px;color:var(--muted)}.timeline-entry>article{margin:8px 0 0}
            @media(max-width:540px){.timeline-entry{margin-left:18px;padding-bottom:16px}.timeline-entry::before{left:-13px}.timeline-entry::after{left:-17px}.timeline-date strong{font-size:15px}.timeline-clock{font-size:12px}}
            @media(max-width:540px){main{padding:16px 14px 28px}header{padding:20px;margin-bottom:18px;border-radius:10px}article{padding:14px;margin:12px 0;border-radius:8px}h1{font-size:24px}header p{font-size:12px}.top{gap:8px 12px}.photos{gap:6px}.photo{border-radius:6px}.viewer{padding:12px}footer{margin-top:20px;padding:14px 4px 0}}
            @media(prefers-color-scheme:dark){:root{--canvas:#191d23;--paper:#22272f;--ink:#e3e6eb;--muted:#a7afbc;--line:#353b45;--soft:#2e3846;--accent:#bdcfe4;--ornament:#c8a582}header,article{box-shadow:none}}
        """.trimIndent())
        append("</style></head><body><main><header><h1>${tr("息刻 · 日记备份", "Xike · Journal backup")}</h1><p>")
        append(entries.size)
        append(tr(" 条记录 · 解压备份包后可离线阅读", " entries · Extract the backup to read offline"))
        append("</p></header>")
        entries.sortedByDescending(JournalEntry::createdAt).forEach { entry ->
            val instant = Instant.ofEpochMilli(entry.createdAt)
            val date = instant.atZone(ZoneId.systemDefault())
            append("<section class=\"timeline-entry\"><time class=\"timeline-date\" datetime=\"$instant\"><strong>")
            append(escape(dateFormat.format(date)))
            append("</strong><span class=\"timeline-clock\">")
            append(escape(timeFormat.format(date)))
            append("</span></time><article><div class=\"top\"><span class=\"mood\"><span class=\"mood-emoji\">")
            append(entry.mood.emoji)
            append("</span>")
            append(escape(entry.mood.label))
            append("</span>")
            if (entry.tags.isNotEmpty()) {
                append("<div class=\"tags\">")
                entry.tags.forEach { tag ->
                    append("<span class=\"tag\">")
                    append(escape(localizedText(tag)))
                    append("</span>")
                }
                append("</div>")
            }
            append("</div>")
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
            entry.video?.let { video ->
                val url = mediaUrl("videos", video.fileName)
                append("<video controls preload=\"none\" style=\"width:100%;max-height:480px\" poster=\"")
                append(mediaUrl("videos", video.coverFileName))
                append("\" src=\"$url\"></video><a href=\"$url\">${tr("打开视频原文件", "Open original video")}</a>")
            }
            append("</article></section>")
        }
        append("<footer>${tr("此页面保存在本地。未加密备份中的文字、照片、录音和视频可被拿到文件的人直接查看。", "This page is stored locally. Anyone with an unencrypted backup can view its text, photos, recordings and videos.")}</footer></main>")
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
