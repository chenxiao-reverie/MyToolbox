package com.toolbox.core.model

/**
 * 静态工具目录。P1 仍是硬编码；接 ToolRegistry 后改为注册表，UI 不用动。
 */
object ToolCatalog {

    val tools: List<ToolSpec> = listOf(
        ToolSpec(
            id = ToolId("photo_diary"),
            title = "图片日记",
            summary = "照片 · 一句话",
            category = Category.IMAGE,
            inputs = InputSpec(emptyList()),
            outputExtension = "png",
            glyph = Glyph.PHOTO,
            kind = ToolKind.CREATE
        ),
        ToolSpec(
            id = ToolId("m4a_to_mp4"),
            title = "转成 MP4",
            summary = "容器 · 无损",
            category = Category.CONTAINER,
            inputs = InputSpec(listOf("audio/mp4", "audio/*")),
            outputExtension = "mp4",
            glyph = Glyph.CONVERT,
            params = listOf(
                ParamSpec.Text(
                    key = "name",
                    title = "输出文件名",
                    default = "录音_2026-11-18",
                    suffix = ".mp4"
                )
            )
        ),
        ToolSpec(
            id = ToolId("video_to_m4a"),
            title = "转成 M4A",
            summary = "只留音轨",
            category = Category.CONTAINER,
            inputs = InputSpec(listOf("video/*")),
            outputExtension = "m4a",
            glyph = Glyph.MUSIC
        ),
        ToolSpec(
            id = ToolId("extract_audio"),
            title = "提取音轨",
            summary = "视频 → 音频",
            category = Category.AUDIO,
            inputs = InputSpec(listOf("video/*")),
            outputExtension = "m4a",
            glyph = Glyph.FILM
        ),
        ToolSpec(
            id = ToolId("lossless_trim"),
            title = "无损裁剪",
            summary = "不重编码",
            category = Category.CONTAINER,
            inputs = InputSpec(listOf("audio/*", "video/*")),
            outputExtension = "mp4",
            glyph = Glyph.SCISSORS,
            params = listOf(
                ParamSpec.Text(key = "start", title = "开始", default = "00:00", suffix = ""),
                ParamSpec.Text(key = "end", title = "结束", default = "03:42", suffix = "")
            )
        ),
        ToolSpec(
            id = ToolId("audio_transcode"),
            title = "音频转码",
            summary = "MP3 · FLAC",
            category = Category.AUDIO,
            inputs = InputSpec(listOf("audio/*")),
            outputExtension = "mp3",
            glyph = Glyph.CORNERS,
            comingSoon = true
        ),
        ToolSpec(
            id = ToolId("video_compress"),
            title = "视频压缩",
            summary = "720p · 1080p",
            category = Category.VIDEO,
            inputs = InputSpec(listOf("video/*")),
            outputExtension = "mp4",
            glyph = Glyph.FRAME,
            comingSoon = true
        )
    )
}
