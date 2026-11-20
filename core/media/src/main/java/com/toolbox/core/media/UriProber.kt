package com.toolbox.core.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.toolbox.core.model.ProbeResult
import java.io.FileInputStream

/** 从 SAF URI 打开 fd 并探测。阻塞调用，调用方负责切到 IO 线程。 */
class UriProber(private val context: Context) {

    private val prober = MediaProber()

    fun probe(uri: Uri, fallbackName: String? = null): ProbeResult? {
        val resolver = context.contentResolver
        val (name, declaredSize) = queryMeta(uri)
        val pfd = resolver.openFileDescriptor(uri, "r") ?: return null
        return pfd.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                prober.probe(
                    channel = stream.channel,
                    displayName = name ?: fallbackName ?: "未命名文件",
                    sizeBytes = declaredSize?.takeIf { it > 0 } ?: descriptor.statSize
                )
            }
        }
    }

    private fun queryMeta(uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        return name to size
    }
}