package com.toolbox.core.model

/**
 * P0 骨架的领域模型。
 * 这里只放"界面和引擎都需要知道"的事实，不放任何 Android / Compose 类型。
 */

enum class Category(val label: String) {
    CONTAINER("容器"),
    AUDIO("音频"),
    VIDEO("视频"),
    IMAGE("图片")
}

@JvmInline
value class ToolId(val value: String)

/** UI 只认这个语义图标键，具体画法在 core:ui 里。 */
enum class Glyph { CONVERT, MUSIC, FILM, SCISSORS, CORNERS, FRAME, PHOTO }

data class InputSpec(
    val mimeTypes: List<String>,
    val maxCount: Int = 1
)

enum class ToolKind { CONVERT, CREATE }

data class ToolSpec(
    val id: ToolId,
    val title: String,
    val summary: String,
    val category: Category,
    val inputs: InputSpec,
    val outputExtension: String,
    val glyph: Glyph,
    val params: List<ParamSpec> = emptyList(),
    val comingSoon: Boolean = false,
    val kind: ToolKind = ToolKind.CONVERT
)

sealed interface ParamSpec {
    val key: String
    val title: String

    data class Toggle(
        override val key: String,
        override val title: String,
        val default: Boolean = false
    ) : ParamSpec

    data class Choice(
        override val key: String,
        override val title: String,
        val options: List<Option>,
        val defaultIndex: Int = 0
    ) : ParamSpec {
        data class Option(val value: String, val label: String)
    }

    data class Text(
        override val key: String,
        override val title: String,
        val default: String = "",
        val suffix: String? = null
    ) : ParamSpec
}

enum class Container { ISOBMFF, MATROSKA, WAV, OGG, MPEG_TS, UNKNOWN }

enum class TrackType { AUDIO, VIDEO, SUBTITLE }

data class TrackInfo(
    val index: Int,
    val type: TrackType,
    val codec: String,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Int = 0
)

enum class MediaFlag {
    FRAGMENTED,
    ENCRYPTED,
    QUICKTIME_SOUND,
    HAS_COVER,
    HAS_CHAPTERS,
    MOOV_AT_END,
    CO64
}

data class MediaInfo(
    val sizeBytes: Long,
    val container: Container,
    val brands: List<String> = emptyList(),
    val tracks: List<TrackInfo> = emptyList(),
    val durationMs: Long = 0L,
    val flags: Set<MediaFlag> = emptySet()
)

/** 工具页顶部展示的源文件信息。P0 用假数据，接上 Prober 后由探测结果填充。 */
data class SourceFile(
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val codecLabel: String,
    val media: MediaInfo? = null
)

enum class JobStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

enum class Stage { PROBING, PLANNING, COPYING, TRANSCODING, MUXING, VERIFYING }

data class Progress(
    val stage: Stage,
    val fraction: Float,
    val message: String? = null
)

enum class ErrorCode {
    SRC_UNREADABLE,
    SRC_CORRUPTED,
    UNSUPPORTED_CODEC,
    ENCRYPTED,
    NO_SPACE,
    OUTPUT_CONFLICT,
    PLATFORM_CODEC_FAIL,
    ENGINE_UNAVAILABLE,
    VERIFY_FAILED,
    CANCELLED,
    TIMEOUT,
    UNKNOWN
}

data class JobError(
    val code: ErrorCode,
    val title: String,
    val detail: String
)

data class Job(
    val id: String,
    val source: SourceFile,
    val toolTitle: String,
    val status: JobStatus,
    val progress: Progress,
    val outputName: String? = null,
    val bytesTotal: Long = 0L,
    val bytesDone: Long = 0L,
    val elapsedMs: Long = 0L,
    val lossless: Boolean = true,
    val remuxed: Boolean = false,
    val error: JobError? = null,
    val toolId: String = "",
    val sourceUri: String = "",
    val params: Map<String, String> = emptyMap(),
    val outputUri: String = ""
)

/** 一次探测的完整结果：既能驱动 UI 展示，也能驱动引擎决策。 */
data class ProbeResult(
    val file: SourceFile,
    val media: MediaInfo,
    val ftypOffset: Long = -1L,
    val ftypSize: Long = 0L,
    val majorBrand: String = "",
    val compatibleBrands: List<String> = emptyList(),
    val soundVersion: Int = 0,
    val hasWaveAtom: Boolean = false,
    val objectTypeIndication: Int? = null,
    val moovBeforeMdat: Boolean = false,
    val chunkOffset64: Boolean = false,
    val canPatchFtyp: Boolean = false,
    val blockReason: String? = null
) {
    val losslessRemux: Boolean get() = canPatchFtyp && !media.flags.contains(MediaFlag.ENCRYPTED)
}
