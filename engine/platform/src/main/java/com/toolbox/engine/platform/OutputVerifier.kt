package com.toolbox.engine.platform

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlin.math.abs

/** 输出必须能被平台重新解析，否则宁可报失败，也不要把半成品交给用户。 */
class OutputVerifier(private val context: Context) {

    fun verify(uri: Uri?, path: String?, expectedDurationMs: Long): Boolean {
        val sourceUri = uri
        val sourcePath = path
        if (sourceUri == null && sourcePath == null) return false
        val extractor = MediaExtractor()
        return try {
            if (sourceUri != null) {
                extractor.setDataSource(context, sourceUri, emptyMap<String, String>())
            } else if (sourcePath != null) {
                extractor.setDataSource(sourcePath)
            }
            if (extractor.trackCount < 1) return false

            var hasAudio = false
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) hasAudio = true
            }
            if (!hasAudio) return false

            if (expectedDurationMs > 500) {
                val format = extractor.getTrackFormat(0)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    val actualMs = format.getLong(MediaFormat.KEY_DURATION) / 1000
                    abs(actualMs - expectedDurationMs) <= 150
                } else {
                    true
                }
            } else {
                true
            }
        } catch (e: Exception) {
            false
        } finally {
            runCatching { extractor.release() }
        }
    }
}