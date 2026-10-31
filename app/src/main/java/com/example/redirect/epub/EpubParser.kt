package com.example.redirect.epub

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.net.URLDecoder
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Parsed structure of an EPUB 2 or 3 publication. */
data class EpubBook(
    val title: String,
    val author: String?,
    val description: String?,
    val spine: List<SpineItem>,
    val toc: List<TocEntry>,
    /** Zip path of the cover image, if one is declared. */
    val coverPath: String?,
    val coverMediaType: String?,
) {
    private val totalWeight = spine.sumOf { it.weight }.coerceAtLeast(1)

    /** Overall progress for a position, weighting each chapter by its size. */
    fun progressOf(spineIndex: Int, fraction: Float): Float {
        if (spine.isEmpty()) return 0f
        val i = spineIndex.coerceIn(0, spine.lastIndex)
        val before = spine.take(i).sumOf { it.weight }
        val p = (before + spine[i].weight * fraction.coerceIn(0f, 1f)) / totalWeight.toFloat()
        return p.coerceIn(0f, 1f)
    }

    /** The table-of-contents entry a spine index falls under: its own first entry, else the nearest before it. */
    fun chapterTitleFor(spineIndex: Int): String? =
        (toc.firstOrNull { it.spineIndex == spineIndex } ?: toc.lastOrNull { it.spineIndex < spineIndex })?.title

    fun spineIndexOf(path: String): Int = spine.indexOfFirst { it.path == path }
}

data class SpineItem(val path: String, val mediaType: String, val weight: Long)

data class TocEntry(val title: String, val spineIndex: Int, val fragment: String?, val depth: Int)

class EpubException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { NOT_ZIP, NOT_EPUB, DRM_PROTECTED, NO_READABLE_CONTENT, MALFORMED }
}

/** Open archive for reading resources while a book is displayed. */
class EpubArchive(file: File) : Closeable {
    private val zip: ZipFile = try {
        ZipFile(file)
    } catch (e: ZipException) {
        throw EpubException(EpubException.Reason.NOT_ZIP, "This file isn't a valid EPUB archive.")
    }

    // The reader's WebView reads entries on its own thread and can still be loading when the
    // screen closes the archive, so reads and close are serialized and reads after close miss.
    private var closed = false

    @Synchronized fun exists(path: String) = !closed && zip.getEntry(path) != null
    /** Callers must finish with the stream before [close]; prefer [readBytes]. */
    @Synchronized fun open(path: String): InputStream? = if (closed) null else zip.getEntry(path)?.let { zip.getInputStream(it) }
    @Synchronized fun readBytes(path: String): ByteArray? = open(path)?.use { it.readBytes() }
    @Synchronized fun size(path: String): Long =
        if (closed) 0 else zip.getEntry(path)?.let { if (it.size > 0) it.size else it.compressedSize } ?: 0

    @Synchronized override fun close() {
        closed = true
        zip.close()
    }
}

object EpubParser {

    private val FONT_OBFUSCATION = setOf(
        "http://www.idpf.org/2008/embedding",
        "http://ns.adobe.com/pdf/enc#RC",
    )

    fun parse(file: File): EpubBook = EpubArchive(file).use { parse(it) }

    fun parse(archive: EpubArchive): EpubBook {
        val container = archive.readBytes("META-INF/container.xml")
            ?: throw EpubException(EpubException.Reason.NOT_EPUB, "This file doesn't look like an EPUB (no container.xml).")
        checkDrm(archive)

        val opfPath = parseXml(container)
            .elements("rootfile")
            .firstOrNull { it.getAttribute("media-type").ifEmpty { "application/oebps-package+xml" } == "application/oebps-package+xml" }
            ?.getAttribute("full-path")
            ?.takeIf { it.isNotBlank() }
            ?: throw EpubException(EpubException.Reason.MALFORMED, "The EPUB's container doesn't point to a package file.")

        val opfBytes = archive.readBytes(opfPath)
            ?: throw EpubException(EpubException.Reason.MALFORMED, "The EPUB's package file is missing.")
        val opf = try {
            parseXml(opfBytes)
        } catch (e: Exception) {
            throw EpubException(EpubException.Reason.MALFORMED, "The EPUB's package file couldn't be read.")
        }
        val baseDir = opfPath.substringBeforeLast('/', "")

        // Metadata
        val title = opf.elements("title").firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Untitled book"
        val author = opf.elements("creator").map { it.textContent.trim() }.filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }?.joinToString(", ")
        val description = opf.elements("description").firstOrNull()?.textContent?.let(::stripTags)?.trim()
            ?.takeIf { it.isNotEmpty() }

        // Manifest
        data class Item(val id: String, val path: String, val mediaType: String, val properties: String)
        val manifest = opf.elements("item").map {
            Item(
                id = it.getAttribute("id"),
                path = resolve(baseDir, it.getAttribute("href")),
                mediaType = it.getAttribute("media-type"),
                properties = it.getAttribute("properties"),
            )
        }.associateBy { it.id }

        // Spine (skip non-linear items such as pop-up footnotes)
        val spineEl = opf.elements("spine").firstOrNull()
        val spine = opf.elements("itemref")
            .filter { it.getAttribute("linear") != "no" }
            .mapNotNull { manifest[it.getAttribute("idref")] }
            .filter { it.mediaType.contains("html") || it.path.endsWith(".html") || it.path.endsWith(".xhtml") || it.path.endsWith(".htm") }
            .filter { archive.exists(it.path) }
            .map { SpineItem(it.path, it.mediaType.ifEmpty { "application/xhtml+xml" }, archive.size(it.path).coerceAtLeast(1)) }
        if (spine.isEmpty()) {
            throw EpubException(EpubException.Reason.NO_READABLE_CONTENT, "This EPUB doesn't contain any readable chapters.")
        }

        // Cover: EPUB 3 property, then EPUB 2 <meta name="cover">, then a best guess.
        val cover = manifest.values.firstOrNull { "cover-image" in it.properties.split(' ') }
            ?: opf.elements("meta").firstOrNull { it.getAttribute("name") == "cover" }
                ?.getAttribute("content")?.let { manifest[it] }
                ?.takeIf { it.mediaType.startsWith("image/") }
            ?: manifest.values.firstOrNull { it.mediaType.startsWith("image/") && it.id.contains("cover", ignoreCase = true) }
        val coverPath = cover?.path?.takeIf { archive.exists(it) }

        // Table of contents: EPUB 3 nav document, then EPUB 2 NCX.
        val spineIndex = spine.withIndex().associate { (i, s) -> s.path to i }
        val navItem = manifest.values.firstOrNull { "nav" in it.properties.split(' ') }
        val ncxItem = spineEl?.getAttribute("toc")?.let { manifest[it] }
            ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }

        val toc = navItem?.let { runCatching { parseNav(archive, it.path, spineIndex) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: ncxItem?.let { runCatching { parseNcx(archive, it.path, spineIndex) }.getOrNull() }
            ?: emptyList()

        return EpubBook(
            title = title,
            author = author,
            description = description,
            spine = spine,
            toc = toc,
            coverPath = coverPath,
            coverMediaType = cover?.mediaType,
        )
    }

    private fun checkDrm(archive: EpubArchive) {
        if (archive.exists("META-INF/rights.xml")) {
            throw EpubException(EpubException.Reason.DRM_PROTECTED, "This book is DRM-protected, so it can't be opened here.")
        }
        val encryption = archive.readBytes("META-INF/encryption.xml") ?: return
        val algorithms = runCatching {
            parseXml(encryption).elements("EncryptionMethod").map { it.getAttribute("Algorithm") }
        }.getOrDefault(listOf("unknown"))
        if (algorithms.any { it !in FONT_OBFUSCATION }) {
            throw EpubException(EpubException.Reason.DRM_PROTECTED, "This book is DRM-protected, so it can't be opened here.")
        }
    }

    private fun parseNav(archive: EpubArchive, navPath: String, spineIndex: Map<String, Int>): List<TocEntry> {
        val doc = parseXml(archive.readBytes(navPath) ?: return emptyList())
        val navDir = navPath.substringBeforeLast('/', "")
        val tocNav = doc.elements("nav").firstOrNull { it.getAttributeNS(EPUB_OPS_NS, "type").contains("toc") || it.getAttribute("epub:type").contains("toc") }
            ?: doc.elements("nav").firstOrNull()
            ?: return emptyList()
        val out = mutableListOf<TocEntry>()
        fun walk(node: Node, depth: Int) {
            for (child in node.childElements()) {
                when (child.localName ?: child.nodeName) {
                    "ol" -> walk(child, depth + 1)
                    "li" -> {
                        val link = child.childElements().firstOrNull { (it.localName ?: it.nodeName) == "a" }
                        if (link != null) addEntry(out, link.textContent, navDir, link.getAttribute("href"), depth, spineIndex)
                        walk(child, depth)
                    }
                    else -> walk(child, depth)
                }
            }
        }
        walk(tocNav, -1)
        return out
    }

    private fun parseNcx(archive: EpubArchive, ncxPath: String, spineIndex: Map<String, Int>): List<TocEntry> {
        val doc = parseXml(archive.readBytes(ncxPath) ?: return emptyList())
        val ncxDir = ncxPath.substringBeforeLast('/', "")
        val navMap = doc.elements("navMap").firstOrNull() ?: return emptyList()
        val out = mutableListOf<TocEntry>()
        fun walk(node: Node, depth: Int) {
            for (point in node.childElements().filter { (it.localName ?: it.nodeName) == "navPoint" }) {
                val label = point.childElements().firstOrNull { (it.localName ?: it.nodeName) == "navLabel" }?.textContent.orEmpty()
                val src = point.childElements().firstOrNull { (it.localName ?: it.nodeName) == "content" }?.getAttribute("src").orEmpty()
                addEntry(out, label, ncxDir, src, depth, spineIndex)
                walk(point, depth + 1)
            }
        }
        walk(navMap, 0)
        return out
    }

    private fun addEntry(
        out: MutableList<TocEntry>, rawTitle: String, dir: String, href: String, depth: Int, spineIndex: Map<String, Int>,
    ) {
        val title = rawTitle.replace(Regex("\\s+"), " ").trim()
        if (title.isEmpty() || href.isBlank()) return
        val path = resolve(dir, href.substringBefore('#'))
        val index = spineIndex[path] ?: return
        val fragment = href.substringAfter('#', "").takeIf { it.isNotEmpty() }
        out += TocEntry(title, index, fragment, depth.coerceAtLeast(0))
    }

    // -- helpers --------------------------------------------------------------

    private const val EPUB_OPS_NS = "http://www.idpf.org/2007/ops"

    /** Resolves [href] against [baseDir] and normalises "." / ".." segments. */
    fun resolve(baseDir: String, href: String): String {
        val decoded = runCatching { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
        val parts = ArrayDeque<String>()
        val combined = if (decoded.startsWith("/")) decoded else if (baseDir.isEmpty()) decoded else "$baseDir/$decoded"
        for (segment in combined.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(segment)
            }
        }
        return parts.joinToString("/")
    }

    private fun stripTags(s: String) = s.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")

    fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
            runCatching { isExpandEntityReferences = false }
        }
        val builder = factory.newDocumentBuilder()
        // Never fetch external DTDs or entities.
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        builder.setErrorHandler(null)
        return builder.parse(ByteArrayInputStream(bytes))
    }

    private fun Document.elements(localName: String): List<Element> {
        val list = getElementsByTagNameNS("*", localName)
        val out = (0 until list.length).map { list.item(it) as Element }
        if (out.isNotEmpty()) return out
        // Fallback for documents parsed without namespace information.
        val plain = getElementsByTagName(localName)
        return (0 until plain.length).map { plain.item(it) as Element }
    }

    private fun Node.childElements(): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()
}
