package com.toolbox.core.media

import com.toolbox.core.model.MediaInfo
import com.toolbox.core.model.ProbeResult
import com.toolbox.core.model.SourceFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel

class FtypPatcherTest {

    @Test
    fun `keeps length and payload while rewriting major brand`() {
        val brands = listOf("M4A ", "mp42", "isom")
        val input = sampleFile(brands)
        val output = patch(input, ftypSize = 16 + brands.size * 4)

        assertEquals(input.size, output.size)

        val head = output.copyOfRange(0, 32)
        assertEquals("mp42", String(head, 8, 4, Charsets.ISO_8859_1))
        assertEquals(0, be32(head, 12))
        // 已有 isom，不再额外替换品牌
        assertEquals("M4A ", String(head, 16, 4, Charsets.ISO_8859_1))

        // ftyp 之后的字节必须逐字节相同（moov/mdat 偏移仍然有效）
        assertArrayEquals(
            input.copyOfRange(28, input.size),
            output.copyOfRange(28, output.size)
        )
    }

    @Test
    fun `replaces first apple brand with isom when list has no iso brand`() {
        val brands = listOf("M4A ", "mp42")
        val input = sampleFile(brands)
        val output = patch(input, ftypSize = 16 + brands.size * 4)
        val head = output.copyOfRange(0, 24)

        assertEquals("mp42", String(head, 8, 4, Charsets.ISO_8859_1))
        assertEquals("isom", String(head, 16, 4, Charsets.ISO_8859_1))
        assertEquals("mp42", String(head, 20, 4, Charsets.ISO_8859_1))
    }

    @Test
    fun `refuses when there is no slot to normalise`() {
        val brands = listOf("3gp4", "3gp5")
        val input = sampleFile(brands)
        try {
            patch(input, ftypSize = 16 + brands.size * 4)
            throw AssertionError("应当拒绝改写")
        } catch (expected: FtypPatcher.NotPatchableException) {
            assertTrue(expected.message!!.contains("品牌"))
        }
    }

    private fun patch(input: ByteArray, ftypSize: Int): ByteArray {
        val temp = File.createTempFile("probe", ".m4a")
        FileOutputStream(temp).use { it.write(input) }
        val probe = ProbeResult(
            file = SourceFile("sample.m4a", input.size.toLong(), 1_000, "AAC"),
            media = MediaInfo(sizeBytes = input.size.toLong(), container = com.toolbox.core.model.Container.ISOBMFF),
            ftypOffset = 0,
            ftypSize = ftypSize.toLong(),
            majorBrand = "M4A ",
            compatibleBrands = emptyList(),
            canPatchFtyp = true
        )
        val out = ByteArrayOutputStream()
        FileChannel.open(temp.toPath()).use { channel ->
            FtypPatcher().patch(channel, out, probe)
        }
        temp.delete()
        return out.toByteArray()
    }

    private fun sampleFile(brands: List<String>): ByteArray {
        val ftypSize = 16 + brands.size * 4
        val out = ByteArrayOutputStream()
        out.write(u32(ftypSize))
        out.write("ftyp".toByteArray(Charsets.ISO_8859_1))
        out.write("M4A ".toByteArray(Charsets.ISO_8859_1))
        out.write(u32(0))
        brands.forEach { out.write(it.toByteArray(Charsets.ISO_8859_1)) }

        // 伪造 moov / mdat：patcher 不解析内容，只验证是否为原样拷贝
        out.write(u32(16))
        out.write("moov".toByteArray(Charsets.ISO_8859_1))
        out.write(ByteArray(8) { 0x11 })
        out.write(u32(24))
        out.write("mdat".toByteArray(Charsets.ISO_8859_1))
        out.write(ByteArray(16) { 0x22 })
        return out.toByteArray()
    }

    private fun u32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )
}