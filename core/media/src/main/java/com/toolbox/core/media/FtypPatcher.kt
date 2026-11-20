package com.toolbox.core.media

import com.toolbox.core.model.ProbeResult
import java.io.OutputStream
import java.nio.channels.FileChannel

/**
 * 只改 ftyp 的品牌字段，其余字节原样搬运。
 * 因为总长度不变，所有 stco/co64 偏移继续有效——这是"无损"的根本原因。
 */
class FtypPatcher {

    class NotPatchableException(message: String) : Exception(message)

    fun patch(
        channel: FileChannel,
        output: OutputStream,
        probe: ProbeResult,
        onProgress: (bytesWritten: Long) -> Unit = {}
    ): Long {
        val source = ChannelSource(channel)
        val total = source.size
        val headLength = minOf(total, HEAD_LIMIT).toInt()
        val head = source.readAt(0, headLength)

        val ftypOffset = probe.ftypOffset.toInt()
        val ftypSize = probe.ftypSize.toInt()

        if (probe.ftypOffset < 0 || ftypSize < 20) {
            throw NotPatchableException("ftyp 缺失或过小")
        }
        if (ftypOffset + ftypSize > head.size) {
            throw NotPatchableException("ftyp 不在可改写范围内")
        }

        val brandCount = (ftypSize - 16) / 4
        val brands = (0 until brandCount).map { fourcc(head, ftypOffset + 16 + it * 4) }

        var replaceIndex = -1
        var hasIso = false
        brands.forEachIndexed { index, brand ->
            if (brand == "isom" || brand == "iso2") hasIso = true
            if (replaceIndex < 0 && (brand == "M4A " || brand == "M4B " || brand == "mp41")) {
                replaceIndex = index
            }
        }
        if (!hasIso && replaceIndex < 0) {
            throw NotPatchableException("品牌列表没有可替换项")
        }

        writeFourcc(head, ftypOffset + 8, "mp42")
        writeInt(head, ftypOffset + 12, 0)
        if (!hasIso) {
            writeFourcc(head, ftypOffset + 16 + replaceIndex * 4, "isom")
        }

        output.write(head)
        var written = head.size.toLong()
        onProgress(written)

        val bufferSize = 1 shl 20
        var position = head.size.toLong()
        while (position < total) {
            val chunk = source.readAt(position, bufferSize)
            if (chunk.isEmpty()) break
            output.write(chunk)
            position += chunk.size
            written += chunk.size
            onProgress(written)
        }
        output.flush()
        return written
    }

    private fun writeFourcc(target: ByteArray, offset: Int, value: String) {
        val bytes = value.toByteArray(Charsets.ISO_8859_1)
        for (i in 0 until 4) target[offset + i] = bytes[i]
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private companion object {
        const val HEAD_LIMIT = 64L * 1024
    }
}