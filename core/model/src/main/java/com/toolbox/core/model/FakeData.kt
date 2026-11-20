package com.toolbox.core.model

/**
 * 仅用于 Compose Preview 的示例数据；真实任务来自 JobRepository。
 */
object FakeData {

    val sampleAudio = SourceFile(
        displayName = "录音_2026-11-18.m4a",
        sizeBytes = 8_600_000L,
        durationMs = 222_000L,
        codecLabel = "AAC 44.1 kHz"
    )

    private val meeting = SourceFile(
        displayName = "会议记录_11月.m4a",
        sizeBytes = 42_000_000L,
        durationMs = 1_380_000L,
        codecLabel = "AAC 48 kHz"
    )

    private val interview = SourceFile(
        displayName = "访谈_ep12.m4a",
        sizeBytes = 61_000_000L,
        durationMs = 2_040_000L,
        codecLabel = "AAC 44.1 kHz"
    )

    private val course = SourceFile(
        displayName = "课程录音_03.m4a",
        sizeBytes = 19_400_000L,
        durationMs = 640_000L,
        codecLabel = "AAC 44.1 kHz"
    )

    val initialJobs: List<Job> = listOf(
        Job(
            id = "j1",
            source = sampleAudio,
            toolTitle = "转成 MP4",
            status = JobStatus.RUNNING,
            progress = Progress(Stage.COPYING, 0.68f),
            outputName = "录音_2026-11-18.mp4",
            bytesTotal = 8_600_000L,
            bytesDone = 5_850_000L,
            lossless = true
        ),
        Job(
            id = "j2",
            source = meeting,
            toolTitle = "音频转码",
            status = JobStatus.RUNNING,
            progress = Progress(Stage.TRANSCODING, 0.24f),
            outputName = "会议记录_11月.mp3",
            bytesTotal = 42_000_000L,
            bytesDone = 10_100_000L,
            lossless = false
        ),
        Job(
            id = "j3",
            source = interview,
            toolTitle = "转成 MP4",
            status = JobStatus.DONE,
            progress = Progress(Stage.VERIFYING, 1f),
            outputName = "访谈_ep12.mp4",
            bytesTotal = 61_000_000L,
            bytesDone = 61_000_000L,
            elapsedMs = 2_100L,
            lossless = true
        ),
        Job(
            id = "j4",
            source = course,
            toolTitle = "转成 MP4",
            status = JobStatus.FAILED,
            progress = Progress(Stage.COPYING, 0f),
            outputName = "课程录音_03.mp4",
            bytesTotal = 19_400_000L,
            bytesDone = 0L,
            error = JobError(
                code = ErrorCode.OUTPUT_CONFLICT,
                title = "目标已存在同名文件",
                detail = "换个名字，或先删除旧文件。"
            )
        )
    )
}