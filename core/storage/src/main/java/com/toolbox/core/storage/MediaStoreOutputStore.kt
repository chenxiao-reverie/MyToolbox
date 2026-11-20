package com.toolbox.core.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * API 29+ 写入 MediaStore/Downloads（用户可见，无需存储权限）；
 * API 26–28 退回应用专属目录（无需运行时权限）。
 */
class MediaStoreOutputStore(private val context: Context) : OutputStore {

    override fun resolveAvailableName(
        displayName: String,
        mime: String,
        relativePath: String
    ): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolveMediaStoreName(displayName, relativePath, collectionFor(mime))
        } else {
            resolveLegacyName(displayName, relativePath)
        }
    }

    private fun resolveMediaStoreName(
        displayName: String,
        relativePath: String,
        collection: Uri
    ): String {
        val base = displayName.substringBeforeLast('.', displayName)
        val extension = displayName.substringAfterLast('.', "")
        for (index in 0..99) {
            val candidate = if (index == 0) {
                displayName
            } else if (extension.isEmpty()) {
                "$base ($index)"
            } else {
                "$base ($index).$extension"
            }
            if (!existsIn(collection, candidate, relativePath)) return candidate
        }
        return displayName
    }

    private fun existsIn(collection: Uri, displayName: String, relativePath: String): Boolean {
        val resolver = context.contentResolver
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(displayName, "$relativePath%")
        return runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                args,
                null
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    private fun resolveLegacyName(displayName: String, relativePath: String): String {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            relativePath.substringAfterLast('/')
        )
        val base = displayName.substringBeforeLast('.', displayName)
        val extension = displayName.substringAfterLast('.', "")
        for (index in 0..99) {
            val candidate = if (index == 0) {
                displayName
            } else if (extension.isEmpty()) {
                "$base ($index)"
            } else {
                "$base ($index).$extension"
            }
            if (!File(dir, candidate).exists()) return candidate
        }
        return displayName
    }

    override fun create(
        displayName: String,
        mime: String,
        relativePath: String
    ): OutputHandle {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaStoreHandle(displayName, mime, relativePath, collectionFor(mime))
        } else {
            legacyHandle(displayName, relativePath)
        }
    }

    private fun collectionFor(mime: String): Uri = when {
        mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
    }

    private fun mediaStoreHandle(
        displayName: String,
        mime: String,
        relativePath: String,
        collection: Uri
    ): OutputHandle {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: error("无法在下载目录创建文件")

        return object : OutputHandle {
            override val displayName: String = displayName
            override val uri: Uri = uri
            override val path: String? = null

            override fun openStream(): OutputStream =
                resolver.openOutputStream(uri) ?: error("无法写入输出文件")

            override fun openFileDescriptor(): ParcelFileDescriptor? =
                resolver.openFileDescriptor(uri, "rw")

            override fun commit() {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }

            override fun abort() {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }

    private fun legacyHandle(displayName: String, relativePath: String): OutputHandle {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            relativePath.substringAfterLast('/')
        ).apply { mkdirs() }
        val file = File(dir, displayName)

        return object : OutputHandle {
            override val displayName: String = displayName
            override val uri: Uri? = null
            override val path: String = file.absolutePath

            override fun openStream(): OutputStream = FileOutputStream(file)

            override fun openFileDescriptor(): ParcelFileDescriptor? =
                ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_WRITE or
                        ParcelFileDescriptor.MODE_CREATE or
                        ParcelFileDescriptor.MODE_TRUNCATE
                )

            override fun commit() = Unit

            override fun abort() {
                runCatching { file.delete() }
            }
        }
    }
}