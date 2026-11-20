# Android 本地工具箱：架构方案

> 场景：现阶段只有「m4a → mp4 容器重封装」，后续扩展音频处理、视频处理、图片处理等能力。
> 目标：**新增一个工具 = 新增一个声明 + 一个引擎实现，不改 UI、不改任务框架。**
> 前提约束：纯本地、离线、不上传；不申请 `MANAGE_EXTERNAL_STORAGE`；优先零依赖方案。
> 日期：2026-11-20

---

## 0. 一句话结论

> **不要按「音频 / 视频」分模块，要按「处理层级」分引擎。**
> 因为决定成本、速度、画质、失败率的是"要不要解码"，不是"是音频还是视频"。
> 工具箱的骨架应该是：`ToolSpec（声明）→ Probe（探测）→ Engine（执行）→ Verify（校验）`，UI 由声明自动生成。

---

## 1. 先把产品边界钉死

### 1.1 是什么

一个**本地批处理工具箱**：选文件 → 选工具 → 设参数 → 跑 → 拿到结果。

- 核心价值：**快、不传云、质量可控（能无损就无损）**
- 交互模型：任务队列，而不是"一次一个弹窗"
- 使用场景：手机里存了一堆录音/视频，想批量转格式、提取、压缩、改名

### 1.2 不是什么（这些会毁掉架构）

| 不做 | 原因 |
|---|---|
| 云端转码 | 与"隐私""离线"定位冲突，且成本结构完全不同 |
| 通用 FFmpeg 命令行输入框 | 99% 用户不会用；一旦开放就等于承诺"所有命令都能跑"，支持成本爆炸 |
| 动态插件（下载 .apk/.dex 执行） | Play 政策风险高、崩溃归因难、收益低；用编译期注册表足够 |
| 账号 / 云同步 / 社区 | 工具箱不需要账户体系 |
| 全盘文件管理 | `MANAGE_EXTERNAL_STORAGE` 审核红线；SAF + MediaStore 足够 |

### 1.3 一个容易被忽略的入口：系统分享

工具箱类 App 的高频入口不是"打开 App → 选文件"，而是**在文件管理/相册里点"分享"**。

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="audio/*" />
    <data android:mimeType="video/*" />
</intent-filter>
```

收到 `ACTION_SEND` 后，直接跳到"该 MIME 可用的工具列表"，一步到转换。这个入口要在 v0.1 就留好，否则后面重构路由很痛。

---

## 2. 能力地图：按「处理层级」分类

这是整套架构的地基。所有未来功能都能落到这四层之一，每层对应一种引擎能力边界。

| 层级 | 是否解码 | 典型功能 | 耗时（1GB） | 质量 | 对应引擎 |
|---|---|---|---|---|---|
| **L0 容器级** | 否 | m4a→mp4、mp4→m4a、换容器、提取/替换音轨、修 faststart、改元数据/封面、**无损裁剪**、同参数拼接 | 秒级 | 100% 原样 | 自研 box + 平台 Muxer |
| **L1 码流级** | 否（但解析码流） | ADTS↔MP4 的 AAC 互转、HE-AAC 重封装、Opus/FLAC 进 MP4 | 秒级 | 100% 原样 | 自研 + FFmpeg |
| **L2 转码级** | 是 | 音频转 MP3/FLAC/WAV/OGG、改码率/采样率/声道、视频压缩/转码/抽帧 | 分钟级 | 有损 | MediaCodec / Media3 / FFmpeg |
| **L3 合成级** | 是（+ 渲染） | 音频+封面→视频、加水印/字幕、裁剪拼接、变速变调、GIF | 分钟级 | 有损/可配 | Media3 / FFmpeg |

**关键推论：**

1. L0 的能力用平台 API 就能做到极致，**不需要任何 native 库**——这是本产品的护城河（速度、体积、保真度）。
2. L2/L3 才需要引入大引擎，而它们必须有独立的模块边界和 ABI 策略，不能污染主包。
3. 同一层级内的功能高度复用：实现了 L0 的"无损裁剪"，就等于同时拿到"提取片段""去掉片头""批量截取"三个功能。

### 2.1 建议的功能排布

| 版本 | 层级 | 功能 |
|---|---|---|
| v0.1 | L0 | m4a→mp4、mp4→m4a、提取音轨、修复 faststart |
| v0.3 | L0 | 无损裁剪（按时间）、批量重命名、改封面/标题 |
| v0.5 | L0+L1 | 同参数拼接、AAC 裸流↔MP4、Opus/FLAC 封装 |
| v0.8 | L2 | 音频转 MP3/FLAC/WAV、码率/采样率调整、视频压缩到 720p/1080p |
| v1.0 | L3 | 音频+封面→视频、视频加水印、GIF、变速变调 |

每一档都在前一层的能力基础上叠加，不会推倒重来。

---

## 3. 总体架构

### 3.1 分层

```
┌───────────────────────────────────────────────────────────┐
│ :app            组装、导航、DI、启动入口                     │
├───────────────────────────────────────────────────────────┤
│ :feature:home     工具箱首页（由 ToolRegistry 自动生成）      │
│ :feature:tool     通用工具页（表单由 ParamSpec 自动生成）      │
│ :feature:queue    任务队列 / 进度 / 失败详情                 │
│ :feature:settings 输出目录 / 并发 / 主题                     │
├───────────────────────────────────────────────────────────┤
│ :core:task      JobQueue、TaskService(FGS)、Room、状态机      │
│ :core:media     Probe（box walker）、MIME、校验器             │
│ :core:storage   SAF、MediaStore、OutputHandle、原子落盘       │
│ :core:ui        Design System、通用组件                      │
│ :core:model     ToolSpec / JobRequest / MediaInfo / 错误码    │
├───────────────────────────────────────────────────────────┤
│ :engine:api        MediaEngine 接口 + EngineRegistry         │
│ :engine:platform   FtypPatcher / PlatformRemuxer / Trimmer   │
│ :engine:media3     Transformer（转码、合成、水印）             │
│ :engine:ffmpeg     可选，flavor 隔离（L1/L2 长尾）            │
└───────────────────────────────────────────────────────────┘
```

依赖方向严格单向：`feature → core → engine:api`；`engine:*` 只依赖 `core:model` 和 `engine:api`。
`engine` 的具体实现**只有 `:app` 里的 `EngineRegistry` 能看见**，feature 层永远不 `import` 具体引擎。这条规则一旦破了，后面加 FFmpeg 就会到处漏风。

### 3.2 为什么是这个切法

| 切法 | 结果 |
|---|---|
| ❌ 按 `feature:audio` / `feature:video` 分包 | 音频和视频都要"转码"，转码代码会重复两份；加图片又要第三份 |
| ❌ 一个 `:core` 装所有工具逻辑 | 主包随功能线性膨胀，FFmpeg 一进来直接 40MB |
| ✅ 按 `engine` 分能力 + 按 `feature` 分页面 | 能力可复用、可替换、可按 flavor 裁剪；页面只是"声明 + 表单" |

---

## 4. 核心抽象（这套接口决定了后面好不好加功能）

### 4.1 工具声明：ToolSpec

```kotlin
enum class Category { CONTAINER, AUDIO, VIDEO, IMAGE }

@JvmInline value class ToolId(val value: String)

data class ToolSpec(
    val id: ToolId,                       // "m4a_to_mp4"
    val title: String,                    // "转成 MP4"
    val summary: String,                  // 一句话说明，列表里显示
    val category: Category,
    val inputs: InputSpec,
    val params: List<ParamSpec> = emptyList(),
    val outputExtension: String,          // "mp4"
    val acceptMultiple: Boolean = false,
    val minSdk: Int = 26
)

data class InputSpec(
    val mimeTypes: List<String>,          // ["audio/mp4", "audio/*"]
    val maxCount: Int = 1
)
```

**UI 只认 ToolSpec。** 首页的九宫格、搜索、分类筛选、"分享进来后匹配工具"，全部由 `ToolRegistry.all` 推导出来。加一个工具，首页自动多一格。

### 4.2 参数声明：ParamSpec（动态表单）

```kotlin
sealed interface ParamSpec {
    val key: String
    val title: String

    data class Toggle(
        override val key: String, override val title: String,
        val default: Boolean = false
    ) : ParamSpec

    data class Choice(
        override val key: String, override val title: String,
        val options: List<Option>, val default: String
    ) : ParamSpec {
        data class Option(val value: String, val label: String, val hint: String? = null)
    }

    data class Slider(
        override val key: String, override val title: String,
        val min: Int, val max: Int, val step: Int,
        val default: Int, val unit: String
    ) : ParamSpec

    data class Text(
        override val key: String, override val title: String,
        val default: String = "", val placeholder: String = ""
    ) : ParamSpec
}
```

四个控件覆盖 95% 的工具参数。真出现"拖拽选时间段"这类专用控件，就把它做成独立的 composable，用 `ParamSpec.Custom(key)` 挂进去——但**不要**为了一个功能去改通用表单。

### 4.3 探测结果：MediaInfo

```kotlin
data class MediaInfo(
    val sizeBytes: Long,
    val container: Container,             // ISOBMFF / MATROSKA / WAV / OGG / MPEG_TS / UNKNOWN
    val brands: List<String>,             // ftyp 品牌
    val tracks: List<TrackInfo>,
    val durationMs: Long,
    val flags: Set<MediaFlag>
) {
    val audio: TrackInfo? get() = tracks.firstOrNull { it.type == TrackType.AUDIO }
    val video: TrackInfo? get() = tracks.firstOrNull { it.type == TrackType.VIDEO }
}

data class TrackInfo(
    val index: Int,
    val type: TrackType,
    val codec: String,                    // "mp4a" / "alac" / "avc1" / "hvc1"
    val codecMime: String?,               // "audio/mp4a-latm"
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val width: Int = 0, val height: Int = 0,
    val bitrate: Int = 0,
    val language: String? = null
)

enum class MediaFlag {
    FRAGMENTED,        // moov 含 mvex 或出现 moof
    ENCRYPTED,         // pssh / sinf / schm
    QUICKTIME_SOUND,   // sound version >= 1 或存在 wave
    HAS_COVER,
    HAS_CHAPTERS,
    MOOV_AT_END,
    CO64
}
```

`flags` 是决策的核心。第 4 节的矩阵本质就是"看 flags 选引擎"。

### 4.4 引擎接口：MediaEngine

```kotlin
interface MediaEngine {
    val id: EngineId
    val availability: EngineAvailability

    /**
     * 只做决策：能不能做、怎么做、预计多大、有什么风险。
     * 必须快（< 50ms），不做重 IO。返回 null = 不支持。
     */
    suspend fun plan(request: JobRequest, probe: List<MediaInfo>): EnginePlan?

    /**
     * 执行。必须响应协程取消；必须周期性回调 progress；
     * 不允许直接往用户可见目录写半成品（由 OutputHandle 负责原子性）。
     */
    suspend fun execute(plan: EnginePlan, ctx: ExecutionContext): EngineResult
}

data class EnginePlan(
    val engine: EngineId,
    val outputMime: String,
    val estimatedBytes: Long?,            // null = 无法预估
    val warnings: List<Warning> = emptyList(),
    val fallback: EngineId? = null        // 失败后可尝试的引擎
)

data class ExecutionContext(
    val appContext: Context,
    val output: OutputHandle,
    val onProgress: (Progress) -> Unit,
    val isCancelled: () -> Boolean
)

data class Progress(
    val stage: Stage,                     // PROBING / COPYING / TRANSCODING / MUXING / VERIFYING
    val fraction: Float,                  // 0f..1f
    val message: String? = null
)
```

**注意 `plan` 与 `execute` 分离**——这不是学院派洁癖，有三个实际好处：

1. 能在**开始前**告诉用户"预计 38MB，约 2 秒"，而不是跑到一半才知道；
2. 能在**入队时**就判定"这个文件做不了"，直接把失败挡在队列外；
3. 决策逻辑可以纯函数化测试，不需要真跑一遍。

### 4.5 错误分类

```kotlin
enum class ErrorCode {
    SRC_UNREADABLE,        // 打不开 / 权限失效
    SRC_CORRUPTED,         // moov 缺失、mdat 截断
    UNSUPPORTED_CODEC,     // ALAC / AC-3 / Opus，且无可用引擎
    ENCRYPTED,             // DRM
    NO_SPACE,
    OUTPUT_CONFLICT,
    PLATFORM_CODEC_FAIL,   // MediaCodec 初始化失败
    ENGINE_UNAVAILABLE,
    VERIFY_FAILED,         // 输出校验不通过
    CANCELLED,
    TIMEOUT,
    UNKNOWN
}
```

**每一种错误都必须有独立文案和"下一步建议"。** 工具箱类产品的差评，八成来自"转换失败"四个字。

---

## 5. 任务体系

### 5.1 状态机

```
QUEUED → PROBING → PLANNING → RUNNING(0..1) → VERIFYING → DONE
                    ↘ FAILED(code, detail)
   ↘ CANCELLED（任意阶段可取消）
```

状态与进度都落 Room，UI 订阅 `Flow`。**不要**把状态只放内存——进程被杀后用户回来看到"任务消失"是最伤体验的 bug。

### 5.2 执行器：前台服务 + 自持队列

推荐组合：

| 组件 | 职责 |
|---|---|
| `JobQueue`（单例，内存 + Room 双写） | 入队、去重、排序、取消标记 |
| `TaskService`（FGS，`mediaProcessing`） | 串行消费队列，1~2 并发；持有通知 |
| `Room` | 任务持久化、历史记录、恢复 |
| WorkManager | **不**作为主执行器；只在需要"延迟/充电时批处理"时作为触发器 |

理由：WorkManager 的 10 分钟执行窗口、进度上报频率、前台服务配合都很别扭；而转换任务需要"长时间、持续通知、精确进度、随时取消"。用 FGS 自己管队列更直接。

关键配置：

```xml
<service
    android:name=".task.TaskService"
    android:exported="false"
    android:foregroundServiceType="mediaProcessing"
    android:stopWithTask="false" />
```

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`mediaProcessing` 是 Android 15（API 35）新增的类型，正是为"转码媒体文件"设计的：配额 6 小时 / 24 小时，超时回调 `Service.onTimeout()`，必须在几秒内 `stopSelf()`。

`stopWithTask="false"` 让用户从最近任务划掉后服务仍能跑完——这比引 WorkManager 简单得多。

### 5.3 并发与优先级

- **默认串行**。这是 IO 密集 + 可能 CPU 密集的任务，并发只会互相抢带宽、加剧发热。
- 允许用户在设置里开"极速模式"（2 并发），但要在 UI 上标注"更耗电、更热"。
- 队列排序：用户手动置顶 > 小文件优先（快速给出正反馈）> 入队顺序。
- 大文件单独占一个 lane，避免小文件被一个大文件堵死 5 分钟。

### 5.4 断点与恢复

| 任务类型 | 能否断点续传 | 策略 |
|---|---|---|
| L0 容器级（纯拷贝） | ✅ | 记录已写字节，重启后 `seek` 续写 |
| L2/L3 转码 | ❌ | 只能整段重来；记录阶段，重启后从 0 开始 |

**不要给转码类任务做"假断点"**（比如按时间切片再拼），拼接处的音画不同步会比重新跑更糟。

---

## 6. 存储与输出

### 6.1 输入

```kotlin
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenMultipleDocuments()
) { uris ->
    uris.forEach { uri ->
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}
```

`OpenMultipleDocuments` 而不是 `GetContent`：前者能持久化权限，后者在进程重启后 URI 就废了。

### 6.2 输出

统一走 `OutputHandle` 抽象，屏蔽 SAF 与 MediaStore 的差异：

```kotlin
interface OutputHandle {
    val displayName: String
    fun openStream(): OutputStream
    fun commit()          // 置 IS_PENDING=0 / 完成重命名
    fun abort()           // 清理半成品
}
```

两种实现：

| 实现 | 场景 | 要点 |
|---|---|---|
| `MediaStoreHandle` | 默认，API 29+ | `RELATIVE_PATH=Download/<App>/`，`IS_PENDING=1` 写入，`commit()` 置 0 |
| `SafHandle` | 用户自选目录 | 先写 `xxx.part`，校验通过后复制为正式名并删临时文件 |

`abort()` 必须幂等且在 `finally` 里调用。**半成品文件流到用户相册，是这类 App 最常见的差评来源。**

### 6.3 命名与冲突

```
原文件名 + 工具后缀 + 序号
录音_2026.m4a → 录音_2026_mp4.mp4
录音_2026.m4a → 录音_2026_mp4(1).mp4
```

规则集中在一个 `OutputNamer` 里，不要散落在各引擎。批处理时先整体算好名字，避免并发抢名。

---

## 7. UI 信息架构

### 7.1 页面

```
首页
├── 搜索框
├── 分类 Tab：全部 / 容器 / 音频 / 视频 / 图片
├── 工具网格（ToolSpec 驱动，长按可收藏/排序）
├── 最近任务（最多 3 条 + "查看全部"）
└── 底部：工具箱 / 队列 / 设置

工具页（通用，一个页面服务所有工具）
├── 已选文件（可增删、显示时长/大小/编码）
├── 参数表单（ParamSpec 渲染）
├── 输出预览（文件名、预计大小、目标目录）
└── 开始按钮 → 跳队列

队列页
├── 进行中（进度、当前阶段、取消）
├── 已完成（打开/分享/删除）
└── 失败（原因 + 建议 + 重试）

设置
├── 输出目录
├── 并发数
├── 保留原文件 / 完成后删除源文件（默认关）
└── 主题
```

### 7.2 工具箱首页的两种形态

| 形态 | 适合 | 建议 |
|---|---|---|
| 固定九宫格 | 工具 < 12 个 | v0.1 用它，简单直接 |
| 分类 + 搜索 + 收藏 | 工具 > 12 个 | v0.5 起切换 |

因为首页由 `ToolSpec` 生成，这个切换只是换一个 composable，不涉及业务逻辑。

### 7.3 一个必须做的小细节：能力预判

用户选完文件、还没点"开始"时，就应该跑一次 `plan`，把结果展示出来：

- ✅ "可无损转换，预计 2 秒，大小不变"
- ⚠️ "该文件是无损 ALAC，需要重新编码，音质会有损失（预计 45 秒）"
- ❌ "该文件已加密，无法处理"

**这一屏决定了产品是"专业"还是"碰运气"。** 大多数同类 App 是点了才开始报错。

---

## 8. 模块与构建

### 8.1 Gradle 结构

```
settings.gradle.kts
├── :app
├── :core:model          (纯 Kotlin / JVM，无 Android 依赖)
├── :core:media          (Probe、box walker、校验)
├── :core:storage        (SAF、MediaStore、OutputHandle)
├── :core:task           (JobQueue、TaskService、Room)
├── :core:ui             (主题、通用组件)
├── :feature:home
├── :feature:tool
├── :feature:queue
├── :feature:settings
├── :engine:api
├── :engine:platform
├── :engine:media3
└── :engine:ffmpeg       (仅 full flavor 引入)
```

`core:model` 保持纯 Kotlin，好处是 `plan` 的决策逻辑可以用纯 JVM 单测覆盖，不用连真机。

### 8.2 Flavor 与包体

```kotlin
flavorDimensions += "engine"
productFlavors {
    create("lite") { dimension = "engine" }   // 仅平台 API，~3MB
    create("full") { dimension = "engine" }   // + Media3，~8MB
}
```

FFmpeg 用独立 flavor 或 ABI split，不要一上来就全量打进主包。发布用 AAB，Play 会按设备下发单个 ABI。

### 8.3 依赖注入

推荐 **Hilt**（模板少、和 WorkManager/Service 集成好）。如果嫌编译慢，用手写 `AppGraph` 也完全够——这个规模不需要 Koin 的反射开销。

---

## 9. 新增一个工具的完整流程（验证架构是否成立）

以「音频无损裁剪」为例，看要改哪些地方：

| 步骤 | 改动 | 文件数 |
|---|---|---|
| 1 | 声明 `ToolSpec`（id=`audio_trim`，inputs=audio/*，params=起止时间） | 1 |
| 2 | 实现 `MediaEngine`（读 `stbl`，重建 `stts/stsc/stsz/stco` + `elst`） | 1 |
| 3 | 在 `EngineRegistry` 注册优先级 | 1（复用） |
| 4 | 加 fixture 测试：3 个样本 × 边界时间点 | 1 |
| 5 | 注册到 `ToolRegistry` | 1（复用） |
| 6 | 加图标 + 文案资源 | 2 |

**UI 代码 0 行改动。** 首页自动出现卡片，工具页自动渲染"开始/结束时间"两个输入，队列自动支持进度和取消。

如果某天发现"加一个工具要改 8 个文件、动 3 个 feature"，说明抽象漏了，应该停下来修架构而不是硬加。

---

## 10. 关键实现约定（写进团队规范）

1. **引擎不得直接依赖 Context 做存储**，一切输出走 `OutputHandle`。
2. **引擎不得自己弹 UI / Toast**，只返回 `ErrorCode`，文案由 UI 层映射。
3. **`plan()` 必须是纯函数**（除读 `MediaInfo` 外无副作用），便于测试。
4. **所有 IO 走 `Dispatchers.IO` + 显式缓冲区**，默认 1MB，大文件可按 4MB。
5. **禁止把整个文件读进内存**；`moov` 超过阈值（如 64MB）时改用流式解析或直接走透传路线。
6. **每个引擎必须自带 `verify()`**：重开输出，检查轨道数、时长误差 ≤ 50ms、可 seek。
7. **不许静默降级**：从"无损"降级到"重编码"必须让用户确认。
8. **失败不自动重试**：先分类，只有 `NO_SPACE` 清理后、`PLATFORM_CODEC_FAIL` 换软解这类可恢复错误才重试。

---

## 11. 测试策略

| 层 | 方式 | 覆盖 |
|---|---|---|
| `plan()` 决策 | 纯 JVM 单测 | 10 类 `MediaInfo` × 工具 → 期望 `EngineId` |
| 引擎正确性 | JVM 测试 + 真实 fixture | 输出 brand / 轨道数 / 时长 / 大小 |
| 平台编解码 | Instrumented test | MediaCodec 初始化、MediaMuxer 写入 |
| 全链路 | UI test | 选文件 → 转换 → 校验 → 出现在队列 |
| 兼容性 | 云真机 | API 26 / 29 / 33 / 35 各一台 + 一台国产 ROM |

**Fixture 库是资产，不是测试垃圾。** 建议在仓库里维护 `fixtures/` 目录，每个样本带一个 `.json` 描述（来源、预期 flags、预期路线）。它会随着踩坑不断增值。

建议的首批 10 个样本见上一份调研报告第 8 节，直接复用。

---

## 12. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 纯音频 mp4 被部分平台拒收 | 产品价值受质疑 | **v0.1 先验证**；不成立就转向"音频+封面→视频" |
| FFmpeg 引入导致包体/许可证问题 | 上架受阻 | 隔离到 `engine:ffmpeg` + full flavor，LGPL 动态链接 |
| `MediaCodec` 厂商差异 | 部分机型转码失败 | 引擎内做 codec 探测 + 软解回落 |
| 大文件 OOM | 崩溃 | 流式 + 阈值 + 大 `moov` 特殊处理 |
| 用户误以为"改后缀=转换" | 期望错位 | 首屏科普卡片 + 结果页展示"已保留原始音质" |
| 后台被杀 | 任务中断 | FGS + `stopWithTask=false` + 启动恢复队列 |

---

## 13. 落地节奏

| 阶段 | 周期 | 交付 |
|---|---|---|
| **P0 骨架** | 3~5 天 | 模块空壳 + ToolSpec/ToolRegistry + 首页网格 + 队列 UI（假数据） |
| **P1 首个工具** | 1 周 | `engine:platform` 的 FtypPatcher + Probe + Verify，跑通 m4a→mp4 |
| **P2 任务体系** | 1 周 | Room + TaskService + 批量 + 取消 + 恢复 |
| **P3 第二个工具** | 3 天 | mp4→m4a（验证"复用"是否成立，这是架构的试金石） |
| **P4 扩展** | 按需 | 无损裁剪、拼接、转码（引 Media3 / FFmpeg） |

**P3 是最关键的验证点。** 如果第二个工具能在 3 天内做完且没改 UI，架构就是对的；如果需要一周、动了 5 个文件，趁早重切。

---

## 14. 目录结构示例

```
app/
├── ToolboxApp.kt
├── MainActivity.kt
├── di/EngineModule.kt              // 唯一能看到具体引擎的地方
└── nav/AppNavHost.kt

core/model/
├── tool/ToolSpec.kt, ParamSpec.kt
├── media/MediaInfo.kt, TrackInfo.kt, MediaFlag.kt
├── job/JobRequest.kt, JobState.kt, Progress.kt
└── error/ErrorCode.kt

core/media/
├── probe/BoxWalker.kt, IsoBmffProbe.kt, ProbeDispatcher.kt
└── verify/OutputVerifier.kt

core/storage/
├── OutputHandle.kt, MediaStoreHandle.kt, SafHandle.kt
└── OutputNamer.kt

core/task/
├── JobQueue.kt, TaskService.kt, JobRepository.kt
├── db/JobEntity.kt, JobDao.kt
└── EngineRegistry.kt

engine/api/
└── MediaEngine.kt, EnginePlan.kt, ExecutionContext.kt

engine/platform/
├── FtypPatcher.kt
├── PlatformRemuxer.kt
├── Mp4Trimmer.kt
└── MetadataEditor.kt

engine/media3/
├── TranscodeEngine.kt
└── ComposeVideoEngine.kt

feature/tool/
├── ToolScreen.kt, ToolViewModel.kt
├── ParamForm.kt                    // ParamSpec → Compose
└── PreflightCard.kt                // "可无损 / 需转码 / 不支持"
```

---

## 15. 最后三条判断

1. **先做 L0，把"零依赖、秒级、无损"做到极致。** 这是唯一能同时对 FFmpeg 系产品和云服务形成差异化的点。L2 转码是红海，L0 精修是蓝海。

2. **工具箱的成败在"第 4 个工具"，不在第 1 个。** 第 1 个工具靠热情能写完；第 4 个工具是否还能 3 天上线，取决于 `ToolSpec` + `MediaEngine` 这两个接口有没有设计对。所以 P0 阶段宁可多花两天把接口磨平。

3. **不要为了"以后可能要做"而提前抽象。** 本方案里唯一提前留的口子是 `engine` 边界和 `ToolSpec`，其余（Pipeline、插件、跨引擎编排）都等真需求出现再加。过早引入的编排层，最后往往只服务一个用例。