package com.toolbox.core.media

import com.toolbox.core.model.Container
import com.toolbox.core.model.MediaFlag
import com.toolbox.core.model.MediaInfo
import com.toolbox.core.model.ProbeResult
import com.toolbox.core.model.SourceFile
import com.toolbox.core.model.TrackInfo
import com.toolbox.core.model.TrackType
import java.nio.channels.FileChannel
import java.util.Locale

/**
 * 只读 box 头 + moov 的必要子树，不解析 stsz/stco 全表。
 * 一个 2 小时的 m4a，moov 通常几百 KB，探测在几十毫秒内完成。
 */
class MediaProber {

    fun probe(channel: FileChannel, displayName: String, sizeBytes: Long): ProbeResult {
        val src = ChannelSource(channel)
        val top = src.children(0, src.size)

        val ftyp = top.firstOrNull { it.type == "ftyp" }
        val moov = top.firstOrNull { it.type == "moov" }
        val mdat = top.firstOrNull { it.type == "mdat" }
        val hasMoof = top.any { it.type == "moof" }

        var majorBrand = ""
        var brands = emptyList<String>()
        if (ftyp != null && ftyp.size in 8..MAX_HEAD) {
            val body = src.readAt(ftyp.bodyStart, (ftyp.size - ftyp.headerSize).toInt())
            if (body.size >= 8) {
                majorBrand = fourcc(body, 0)
                val count = (body.size - 8) / 4
                brands = (0 until count).map { fourcc(body, 8 + it * 4) }
            }
        }

        val moovBytes = if (moov != null && moov.size in 8..MAX_MOOV) {
            src.readAt(moov.offset, moov.size.toInt())
        } else {
            null
        }
        val moovBox = moovBytes?.let { parseBox(it, 0, it.size) }

        val flags = mutableSetOf<MediaFlag>()
        val tracks = ArrayList<TrackInfo>()
        var soundVersion = 0
        var hasWave = false
        var objectTypeIndication: Int? = null
        var chunkOffset64 = false
        var durationMs = 0L

        if (moovBox != null) {
            durationMs = readDurationMs(moovBox)
            if (moovBox.child("mvex") != null) flags.add(MediaFlag.FRAGMENTED)
            if (moovBox.find("pssh") != null) flags.add(MediaFlag.ENCRYPTED)

            moovBox.child("udta")?.let { udta ->
                if (udta.child("chpl") != null || udta.child("chpt") != null) {
                    flags.add(MediaFlag.HAS_CHAPTERS)
                }
                if (udta.child("meta")?.find("covr") != null) flags.add(MediaFlag.HAS_COVER)
            }

            moovBox.children().filter { it.type == "trak" }.forEachIndexed { index, trak ->
                val mdia = trak.child("mdia") ?: return@forEachIndexed
                val handler = mdia.child("hdlr")?.let {
                    String(it.data, it.bodyStart + 8, 4, Charsets.ISO_8859_1)
                }
                val stbl = mdia.child("minf")?.child("stbl") ?: return@forEachIndexed
                if (stbl.child("co64") != null) chunkOffset64 = true

                val entry = stbl.child("stsd")?.let { stsdBox ->
                    parseBox(stsdBox.data, stsdBox.bodyStart + 8, stsdBox.end)
                } ?: return@forEachIndexed

                val type = entry.type
                if (type == "enca" || type == "encv" || entry.find("sinf") != null) {
                    flags.add(MediaFlag.ENCRYPTED)
                }

                when (handler) {
                    "soun" -> {
                        val version = runCatching { be16(entry.data, entry.bodyStart + 8) }.getOrDefault(0)
                        val channels = runCatching { be16(entry.data, entry.bodyStart + 16) }.getOrDefault(0)
                        val rate = runCatching {
                            (be32(entry.data, entry.bodyStart + 24) ushr 16) and 0xFFFF
                        }.getOrDefault(0)
                        soundVersion = version
                        if (entry.child("wave") != null) hasWave = true

                        val esds = entry.child("esds")
                            ?: entry.child("wave")?.child("esds")
                        if (esds != null) {
                            objectTypeIndication = readObjectTypeIndication(esds)
                        }

                        tracks.add(
                            TrackInfo(
                                index = index,
                                type = TrackType.AUDIO,
                                codec = type,
                                sampleRate = rate,
                                channels = channels
                            )
                        )
                    }

                    "vide" -> {
                        val width = runCatching {
                            be16(entry.data, entry.bodyStart + 24)
                        }.getOrDefault(0)
                        val height = runCatching {
                            be16(entry.data, entry.bodyStart + 26)
                        }.getOrDefault(0)
                        tracks.add(
                            TrackInfo(
                                index = index,
                                type = TrackType.VIDEO,
                                codec = type,
                                width = width,
                                height = height
                            )
                        )
                    }
                }
            }
        }

        if (soundVersion > 0 || hasWave) flags.add(MediaFlag.QUICKTIME_SOUND)
        if (hasMoof) flags.add(MediaFlag.FRAGMENTED)
        if (mdat != null && moov != null && mdat.offset < moov.offset) flags.add(MediaFlag.MOOV_AT_END)
        if (chunkOffset64) flags.add(MediaFlag.CO64)

        val audio = tracks.firstOrNull { it.type == TrackType.AUDIO }
        val sourceFile = SourceFile(
            displayName = displayName,
            sizeBytes = if (sizeBytes > 0) sizeBytes else src.size,
            durationMs = durationMs,
            codecLabel = codecLabel(audio?.codec ?: tracks.firstOrNull()?.codec ?: "", audio?.sampleRate ?: 0),
            media = MediaInfo(
                sizeBytes = src.size,
                container = Container.ISOBMFF,
                brands = brands,
                tracks = tracks,
                durationMs = durationMs,
                flags = flags
            )
        )

        val brandCount = if (ftyp != null) ((ftyp.size - 16) / 4).toInt() else 0
        val hasIsoBrand = brands.any { it == "isom" || it == "iso2" }
        val hasReplaceableBrand = brands.any { it == "M4A " || it == "M4B " || it == "mp41" }
        val encrypted = flags.contains(MediaFlag.ENCRYPTED)
        val fragmented = flags.contains(MediaFlag.FRAGMENTED)

        val blockReason = when {
            ftyp == null -> "缺少 ftyp 头，文件可能已损坏"
            encrypted -> "文件受 DRM 保护，无法转换"
            fragmented -> "碎片化 MP4，需要先拼接成普通 MP4"
            ftyp.size < 20 || brandCount < 1 -> "ftyp 结构异常，品牌列表为空"
            !hasIsoBrand && !hasReplaceableBrand -> "品牌列表没有可替换项，无法原地改写"
            ftyp.offset + ftyp.size > MAX_HEAD -> "ftyp 位置异常"
            else -> null
        }

        return ProbeResult(
            file = sourceFile,
            media = MediaInfo(
                sizeBytes = src.size,
                container = Container.ISOBMFF,
                brands = brands,
                tracks = tracks,
                durationMs = durationMs,
                flags = flags
            ),
            ftypOffset = ftyp?.offset ?: -1L,
            ftypSize = ftyp?.size ?: 0L,
            majorBrand = majorBrand,
            compatibleBrands = brands,
            soundVersion = soundVersion,
            hasWaveAtom = hasWave,
            objectTypeIndication = objectTypeIndication,
            moovBeforeMdat = moov != null && (mdat == null || moov.offset < mdat.offset),
            chunkOffset64 = chunkOffset64,
            canPatchFtyp = blockReason == null,
            blockReason = blockReason
        )
    }

    private fun readDurationMs(moov: ByteBox): Long {
        val mvhd = moov.child("mvhd") ?: return 0L
        return runCatching {
            val version = mvhd.data[mvhd.bodyStart].toInt() and 0xFF
            if (version == 1) {
                val timescale = be32(mvhd.data, mvhd.bodyStart + 20).toLong() and 0xFFFFFFFFL
                val duration = be64(mvhd.data, mvhd.bodyStart + 24)
                if (timescale > 0) duration * 1000 / timescale else 0L
            } else {
                val timescale = be32(mvhd.data, mvhd.bodyStart + 12).toLong() and 0xFFFFFFFFL
                val duration = be32(mvhd.data, mvhd.bodyStart + 16).toLong() and 0xFFFFFFFFL
                if (timescale > 0) duration * 1000 / timescale else 0L
            }
        }.getOrDefault(0L)
    }

    /** esds → ES_Descriptor(0x03) → DecoderConfigDescriptor(0x04) 的第一个字节。 */
    private fun readObjectTypeIndication(esds: ByteBox): Int? {
        val data = esds.data
        var p = esds.bodyStart + 4
        val end = esds.end
        var guard = 0
        while (p < end && guard++ < 16) {
            val tag = data[p].toInt() and 0xFF
            p++
            var length = 0
            var i = 0
            while (i < 4 && p < end) {
                val b = data[p].toInt() and 0xFF
                p++
                length = (length shl 7) or (b and 0x7F)
                i++
                if (b and 0x80 == 0) break
            }
            val bodyEnd = (p + length).coerceAtMost(end)
            when (tag) {
                0x03 -> {
                    var q = p + 2
                    if (q >= end) return null
                    val esFlags = data[q].toInt() and 0xFF
                    q++
                    if (esFlags and 0x80 != 0) q += 2
                    if (esFlags and 0x40 != 0) {
                        val urlLen = data.getOrElse(q) { 0 }.toInt() and 0xFF
                        q += 1 + urlLen
                    }
                    if (esFlags and 0x20 != 0) q += 2
                    p = q.coerceAtMost(end)
                    continue
                }

                0x04 -> return data.getOrElse(p) { 0 }.toInt() and 0xFF
                else -> p = bodyEnd
            }
        }
        return null
    }

    private fun codecLabel(codec: String, rate: Int): String {
        val name = when (codec) {
            "mp4a" -> "AAC"
            "alac" -> "ALAC"
            "ac-3" -> "AC-3"
            "ec-3" -> "E-AC-3"
            "fLaC" -> "FLAC"
            "Opus" -> "Opus"
            "samr" -> "AMR-NB"
            "sawb" -> "AMR-WB"
            "avc1" -> "H.264"
            "hvc1", "hev1" -> "HEVC"
            else -> codec.trim().ifEmpty { "未知" }
        }
        return if (rate > 0) {
            String.format(Locale.US, "%s %.1f kHz", name, rate / 1000f)
        } else {
            name
        }
    }

    private companion object {
        const val MAX_MOOV = 64L * 1024 * 1024
        const val MAX_HEAD = 64L * 1024
    }
}