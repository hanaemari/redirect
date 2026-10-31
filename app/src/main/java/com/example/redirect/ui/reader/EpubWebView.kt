package com.example.redirect.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.redirect.data.ReaderPreferences
import com.example.redirect.data.ReaderTheme
import com.example.redirect.epub.EpubArchive
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * WebView that renders one EPUB spine item at a time. All resources are served from the
 * open [EpubArchive] through a private host; nothing is fetched from the network and
 * JavaScript stays disabled, so book content can't run code.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class EpubWebView(context: Context) : WebView(context) {

    var onTap: () -> Unit = {}
    var onScrollFraction: (Float) -> Unit = {}
    var onPageReady: () -> Unit = {}

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onTap()
            return true
        }
    })

    init {
        settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            loadWithOverviewMode = false
            useWideViewPort = false
            textZoom = 100
            blockNetworkImage = false
        }
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestures.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    /** Scrollable distance in pixels. */
    val scrollableHeight: Int get() = (computeVerticalScrollRange() - height).coerceAtLeast(0)

    val currentFraction: Float
        get() = if (scrollableHeight <= 0) 1f else (scrollY.toFloat() / scrollableHeight).coerceIn(0f, 1f)

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrollFraction(currentFraction)
    }

    /** Scroll to [fraction] once layout has settled (content height can grow after load). */
    fun restoreFraction(fraction: Float, attempt: Int = 0) {
        postDelayed({
            val range = scrollableHeight
            if (range <= 0 && attempt < 12) {
                restoreFraction(fraction, attempt + 1)
            } else {
                scrollTo(0, (fraction * range).toInt())
                if (attempt < 3) restoreFraction(fraction, attempt + 1) else onPageReady()
            }
        }, if (attempt == 0) 30L else 60L)
    }

    companion object {
        const val HOST = "book.local"
        fun urlFor(path: String, fragment: String? = null): String =
            "https://$HOST/" + Uri.encode(path, "/") + (fragment?.let { "#" + Uri.encode(it) } ?: "")
    }
}

/** Serves archive entries and injects the reader stylesheet into chapter documents. */
class EpubClient(
    private val archive: EpubArchive,
    private val chapterPaths: Set<String>,
    private val onInternalLink: (path: String, fragment: String?) -> Unit,
    private val onExternalLink: (Uri) -> Unit,
    private val onLoaded: () -> Unit,
) : WebViewClient() {

    @Volatile var css: String = ""

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        if (url.host != EpubWebView.HOST) return notFound()
        val path = url.path?.removePrefix("/") ?: return notFound()
        val bytes = runCatching { archive.readBytes(path) }.getOrNull() ?: return notFound()
        val mime = mimeFor(path)
        val body = if (path in chapterPaths) injectStyle(bytes) else bytes
        return WebResourceResponse(mime, if (mime.startsWith("text") || mime.contains("xml")) "utf-8" else null, ByteArrayInputStream(body))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        if (url.host == EpubWebView.HOST) {
            val path = url.path?.removePrefix("/").orEmpty()
            onInternalLink(path, url.fragment)
        } else {
            onExternalLink(url)
        }
        return true
    }

    override fun onPageFinished(view: WebView, url: String) {
        onLoaded()
    }

    private fun injectStyle(bytes: ByteArray): ByteArray {
        val html = String(bytes, Charsets.UTF_8)
        val style = "<style type=\"text/css\">/*<![CDATA[*/$css/*]]>*/</style>"
        val headClose = Regex("</head\\s*>", RegexOption.IGNORE_CASE).find(html)
        val out = when {
            headClose != null -> html.substring(0, headClose.range.first) + style + html.substring(headClose.range.first)
            else -> {
                val bodyOpen = Regex("<body[^>]*>", RegexOption.IGNORE_CASE).find(html)
                if (bodyOpen != null) html.substring(0, bodyOpen.range.last + 1) + style + html.substring(bodyOpen.range.last + 1)
                else style + html
            }
        }
        return out.toByteArray(Charsets.UTF_8)
    }

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    companion object {
        fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "xhtml", "xht", "html", "htm" -> "application/xhtml+xml"
            "css" -> "text/css"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "xml", "ncx", "opf" -> "application/xml"
            else -> "application/octet-stream"
        }
    }
}

data class ReaderPalette(val background: Long, val text: Long, val accent: Long, val muted: Long, val chrome: Long, val border: Long)

fun ReaderTheme.palette() = when (this) {
    ReaderTheme.PAPER -> ReaderPalette(0xFFFBF8F2, 0xFF2B2622, 0xFF8A6A4F, 0xFF8C8075, 0xFFF4EFE6, 0xFFE4DBCD)
    ReaderTheme.WARM -> ReaderPalette(0xFFF2E6D3, 0xFF43362A, 0xFF8A5A3A, 0xFF8F7B66, 0xFFEADCC6, 0xFFDCCBB1)
    ReaderTheme.NIGHT -> ReaderPalette(0xFF1B1815, 0xFFD8CEC1, 0xFFC9A584, 0xFF8E8376, 0xFF24201C, 0xFF3A332D)
}

private fun Long.css(): String = "#%06X".format(this and 0xFFFFFF)

fun buildReaderCss(prefs: ReaderPreferences): String {
    val p = prefs.theme.palette()
    val lh = "%.2f".format(Locale.ROOT, prefs.lineHeight)
    return """
        html { background: ${p.background.css()} !important; }
        body {
          background: ${p.background.css()} !important;
          color: ${p.text.css()} !important;
          font-family: "Noto Serif", Georgia, serif !important;
          font-size: ${prefs.fontSizePx}px !important;
          line-height: $lh !important;
          max-width: 36em !important;
          margin: 0 auto !important;
          padding: 72px 26px 160px 26px !important;
          overflow-wrap: break-word;
          -webkit-hyphens: auto; hyphens: auto;
          -webkit-text-size-adjust: 100%;
        }
        body * { color: inherit !important; background-color: transparent !important; border-color: ${p.border.css()} !important; }
        p, li, blockquote, dd, dt, td, div { line-height: $lh !important; font-family: inherit; }
        p { margin-top: 0; margin-bottom: 0.9em; }
        h1, h2, h3, h4, h5, h6 { line-height: 1.3 !important; font-weight: normal !important; letter-spacing: 0.01em; }
        a[href] { color: ${p.accent.css()} !important; text-decoration: none !important; }
        img, svg, video { max-width: 100% !important; height: auto !important; }
        pre { white-space: pre-wrap !important; }
        table { max-width: 100% !important; }
        hr { border: none !important; border-top: 1px solid ${p.border.css()} !important; margin: 2em auto !important; width: 30% !important; }
        ::selection { background: ${p.accent.css()}44; }
    """.trimIndent().replace("\n", " ")
}
