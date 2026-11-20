package com.toolbox.core.media

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal fun be16(b: ByteArray, o: Int): Int =
    ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

internal fun be32(b: ByteArray, o: Int): Int =
    ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
        ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

internal fun be64(b: ByteArray, o: Int): Long {
    var v = 0L
    for (i in 0 until 8) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
    return v
}

internal fun fourcc(b: ByteArray, o: Int): String =
    String(b, o, 4, Charsets.ISO_8859_1)

/** 顶层 box 头，offset/size 指文件中的绝对位置。 */
internal data class BoxHeader(
    val type: String,
    val offset: Long,
    val headerSize: Int,
    val size: Long
) {
    val bodyStart: Long get() = offset + headerSize
    val end: Long get() = offset + size
}

/** 按位置读取的 channel 包装：不改变 channel 的 position，探测与拷贝可以共用一个 fd。 */
internal class ChannelSource(val channel: FileChannel) {

    val size: Long get() = channel.size()

    fun readAt(pos: Long, len: Int): ByteArray {
        val available = (size - pos).coerceAtMost(len.toLong()).toInt()
        if (available <= 0) return ByteArray(0)
        val buf = ByteBuffer.allocate(available)
        var p = pos
        while (buf.hasRemaining()) {
            val read = channel.read(buf, p)
            if (read <= 0) break
            p += read
        }
        return buf.array().copyOf(buf.position())
    }

    fun headerAt(pos: Long, limit: Long = size): BoxHeader? {
        if (pos + 8 > limit) return null
        val head = readAt(pos, 16)
        if (head.size < 8) return null

        val size32 = be32(head, 0)
        val type = fourcc(head, 4)
        var headerSize = 8
        var boxSize = size32.toLong() and 0xFFFFFFFFL

        when {
            size32 == 1 -> {
                if (head.size < 16) return null
                boxSize = be64(head, 8)
                headerSize = 16
            }

            size32 == 0 -> boxSize = limit - pos
            boxSize < 0 -> return null
        }

        if (boxSize < headerSize) return null
        if (pos + boxSize > limit) return null
        return BoxHeader(type, pos, headerSize, boxSize)
    }

    fun children(start: Long, end: Long): List<BoxHeader> {
        val out = ArrayList<BoxHeader>()
        var p = start
        while (p + 8 <= end) {
            val header = headerAt(p, end) ?: break
            out.add(header)
            p = header.end
        }
        return out
    }
}

/** 已读进内存的 box（只用于 moov 子树）。 */
internal class ByteBox(
    val data: ByteArray,
    val offset: Int,
    val headerSize: Int,
    val size: Int
) {
    val type: String get() = fourcc(data, offset + 4)
    val bodyStart: Int get() = offset + headerSize
    val end: Int get() = offset + size

    fun children(): List<ByteBox> {
        val out = ArrayList<ByteBox>()
        var p = bodyStart
        while (p + 8 <= end) {
            val box = parseBox(data, p, end) ?: break
            out.add(box)
            p = box.end
        }
        return out
    }

    fun child(type: String): ByteBox? = children().firstOrNull { it.type == type }

    fun find(type: String, depth: Int = 4): ByteBox? {
        if (this.type == type) return this
        if (depth == 0) return null
        for (c in children()) c.find(type, depth - 1)?.let { return it }
        return null
    }
}

internal fun parseBox(data: ByteArray, offset: Int, limit: Int): ByteBox? {
    if (offset + 8 > limit) return null
    val size32 = be32(data, offset)
    var headerSize = 8
    var size = size32.toLong() and 0xFFFFFFFFL
    when {
        size32 == 1 -> {
            if (offset + 16 > limit) return null
            size = be64(data, offset + 8)
            headerSize = 16
        }

        size32 == 0 -> size = (limit - offset).toLong()
    }
    if (size < headerSize || offset + size > limit) return null
    return ByteBox(data, offset, headerSize, size.toInt())
}