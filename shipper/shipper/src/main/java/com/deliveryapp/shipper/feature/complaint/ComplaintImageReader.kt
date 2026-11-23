package com.deliveryapp.shipper.feature.complaint

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.deliveryapp.shipper.data.model.ComplaintImage
import com.deliveryapp.shipper.data.model.ComplaintLimits
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface PickedImage {
    data class Ready(val image: ComplaintImage) : PickedImage
    data object Unsupported : PickedImage
    data object TooLarge : PickedImage
    data object Unreadable : PickedImage
}

/** Reads at most one byte past the limit, so a huge file cannot exhaust memory. */
suspend fun readComplaintImage(context: Context, uri: Uri): PickedImage = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val type = resolver.getType(uri)?.lowercase()
    if (type != "image/jpeg" && type != "image/png") return@withContext PickedImage.Unsupported
    try {
        val output = ByteArrayOutputStream()
        val stream = resolver.openInputStream(uri) ?: return@withContext PickedImage.Unreadable
        stream.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                if (output.size() > ComplaintLimits.MAX_IMAGE_BYTES) return@withContext PickedImage.TooLarge
            }
        }
        if (output.size() == 0) return@withContext PickedImage.Unreadable
        val extension = if (type == "image/png") "png" else "jpg"
        PickedImage.Ready(ComplaintImage(displayName(context, uri) ?: "anh.$extension", type, output.toByteArray()))
    } catch (_: IOException) {
        PickedImage.Unreadable
    } catch (_: SecurityException) {
        PickedImage.Unreadable
    }
}

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()?.takeIf { it.isNotBlank() }

/** Decodes a downsampled preview off the main thread; returns null for undecodable data. */
suspend fun decodeThumbnail(bytes: ByteArray, maxSidePx: Int = 720): ImageBitmap? = withContext(Dispatchers.Default) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSidePx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}
