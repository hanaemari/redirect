package com.example.redirect.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.redirect.data.Book
import com.example.redirect.data.Http
import com.example.redirect.ui.theme.CoverPalettes
import com.example.redirect.ui.theme.Sans
import com.example.redirect.ui.theme.Serif
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val coverCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private fun decodeCover(path: String, targetWidth: Int = 480): Bitmap? {
    coverCache.get(path)?.let { return it }
    val file = File(path)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    coverCache.put(path, bitmap)
    return bitmap
}

@Composable
fun rememberCoverImage(path: String?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = path?.let { coverCache.get(it)?.asImageBitmap() }, path) {
        if (path != null && value == null) {
            value = withContext(Dispatchers.IO) { runCatching { decodeCover(path) }.getOrNull()?.asImageBitmap() }
        }
    }
    return image
}

/** Downloads a remote cover into the cache once, then decodes it like a local cover. */
@Composable
fun rememberRemoteCoverPath(url: String?): String? {
    val context = LocalContext.current
    val path by produceState<String?>(null, url) {
        if (url == null) return@produceState
        value = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "remote-covers").apply { mkdirs() }
            val file = File(dir, "${url.hashCode().toUInt()}.jpg")
            if (file.exists() && file.length() > 0) return@withContext file.absolutePath
            runCatching { Http().download(url, file, maxBytes = 5L * 1024 * 1024) }
                .map { file.absolutePath }
                .getOrNull()
        }
    }
    return path
}

/** A 2:3 book cover: the real image when one exists, otherwise a typographic cover. */
@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier, elevated: Boolean = true) {
    BookCover(book.title, book.author, book.coverTint, book.coverPath, modifier, elevated)
}

@Composable
fun BookCover(
    title: String,
    author: String,
    tint: Int,
    coverPath: String? = null,
    modifier: Modifier = Modifier,
    elevated: Boolean = true,
) {
    val shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp)
    val image = rememberCoverImage(coverPath)
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .then(if (elevated) Modifier.shadow(6.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x44000000)) else Modifier)
            .clip(shape),
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            GeneratedCover(title, author, tint)
        }
        // Spine shading for a subtle, physical feel.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to Color(0x26000000), 0.035f to Color(0x10FFFFFF), 0.07f to Color.Transparent,
                    )
                )
        )
    }
}

@Composable
private fun GeneratedCover(title: String, author: String, tint: Int) {
    val palette = CoverPalettes[((tint % CoverPalettes.size) + CoverPalettes.size) % CoverPalettes.size]
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background)) {
        val w = maxWidth.value
        val titleSize = (w / 7.2f).coerceIn(9f, 30f)
        val inset = (w * 0.07f).dp
        Box(
            Modifier
                .fillMaxSize()
                .padding(inset)
                .border(0.75.dp, palette.accent.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                .padding(horizontal = inset * 0.8f, vertical = inset * 1.2f),
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(inset * 0.6f))
                    Text(
                        title,
                        color = palette.ink,
                        fontFamily = Serif,
                        fontSize = titleSize.sp,
                        lineHeight = (titleSize * 1.15f).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(inset * 0.7f))
                    Box(Modifier.width((w * 0.16f).dp).height(0.75.dp).background(palette.accent))
                }
                Text(
                    author.uppercase(),
                    color = palette.ink.copy(alpha = 0.85f),
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = (titleSize * 0.42f).coerceAtLeast(6f).sp,
                    letterSpacing = (titleSize * 0.05f).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
