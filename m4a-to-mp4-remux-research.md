# m4a → mp4 纯容器重封装（Remux）技术调研

> 目标形态：**不做解码、不做重编码，只把 m4a 的音频流重新封装成 .mp4**
> 目标平台：**Android 本地离线工具**
> 调研日期：2026-11-20

---

## 0. 结论先行

1. **m4a 本身就是 MP4。** 两者都遵循 ISO/IEC 14496-12（ISOBMFF）。m4a 只是 Apple/iTunes 定义的一个"应用品牌"，文件内部结构、轨道、采样表、编码全部一致。所以"m4a 转 mp4"在多数情况下**不是格式转换，而是改一个 4 字节的品牌标识**。

2. **最优解不是引库，是改 ftyp。** 只重写 `ftyp` box 里的 `major_brand` / `minor_version` / `compatible_brands`，其余字节原样搬运。零依赖、毫秒级、100% 保留封面/标题/章节/无缝播放信息——这是任何"重封装库"都做不到的保真度。

3. **必须有第二条兜底路线。** 当 ftyp 长度不够改、或源文件是 QuickTime 风格（`wave` 包着 `esds`）、碎片化 MP4（`moof`）时，用 `MediaExtractor` + `MediaMuxer` 做样本级透传（仍然不解码）。这条路线零依赖、系统原生、几十 MB/s。

4. **不要一上来就塞 FFmpeg。** ffmpeg-kit 已于 2026-07 正式归档退役（继任者 FFmpegKitNext 仅分发源码），且 min 包也会给每个 ABI 增加十几 MB。对一个"只改容器"的工具，这是用大炮打蚊子。建议把 FFmpeg/GPAC 留给后续的"真转码"高级功能。

5. **最大的产品风险不在技术，在"纯音频 mp4"的接收方。** 部分平台（短视频投稿、聊天软件"发视频"）可能要求文件里存在视频轨，纯音频 mp4 会被拒绝或显示黑屏。**这个假设必须在写代码前用真实上传流程验证**，否则做出来的是"技术上正确、业务上没人用"的功能。

**一句话选型：** `ftyp 改写（主）+ MediaExtractor/MediaMuxer 透传（兜底）+ 纯 Kotlin box 解析器（探测/校验）`，APK 体积增量 ≈ 0。

---

## 1. 概念校准：m4a 与 mp4 的真实关系

### 1.1 同一套容器，不同的"应用品牌"

MP4 的正式名称是 MPEG-4 Part 14，其基础规范是 ISO/IEC 14496-12，即 **ISO Base Media File Format（ISOBMFF）**。这套标准只规定"如何组织媒体数据"：有几个轨道、每帧多大、时间戳怎么排、数据在文件里的物理偏移是多少。至于轨道里装的是 AAC 还是 ALAC、H.264 还是 HEVC，容器不关心。

m4a / m4b / m4v / 3gp / mov / heic / avif 全都是这套壳的不同"品牌"（brand）变体。

MP4 注册管理机构（mp4ra.org）对 `M4A ` 品牌的官方定义是：

> `M4A ` — iTunes MPEG-4 audio protected or not, **can contain audio + video + 3g text track + chapter track** — 规范来源：iTunes

注意这句话的两个含义：
- 它**允许**包含视频轨（封面图常以一条静止视频轨的形式存在）和章节轨；
- 它本质上是"iTunes 生态的约定"，不是另一种容器技术。

对比 `mp42`：MP4 version 2，规范来源 MP4v2；`isom`：所有基于 ISOBMFF 的文件。它们是"通用 MP4"的品牌。

### 1.2 文件层面的唯一差异：ftyp box

一个 ISOBMFF 文件的开头一定是：

```
offset 0   size(4)      00 00 00 20
offset 4   type(4)      "ftyp"
offset 8   major_brand  "M4A "   ← 注意尾部有空格，凑满 4 字节
offset 12  minor_version 00 00 00 00
offset 16  compatible_brands[]  "M4A " "mp42" "isom" ...
```

**除了这 8~20 个字节，一个 m4a 和一个"纯音频 mp4"可以逐字节相同。**

`major_brand` 的语义是"本文件遵从哪个规范，读我的程序至少要实现该规范"；`compatible_brands` 是"我也兼容这些"。所以把 `M4A ` 改成 `mp42`、在兼容列表里补上 `isom`，文件就从"iTunes 音频"变成了"通用 MP4 v2"——**数据一个字节没动，索引偏移全部有效**。

### 1.3 三个必须澄清的误解

| 误解 | 事实 |
|---|---|
| "改后缀 .m4a → .mp4 就是转换" | 后缀只是给操作系统/应用看的提示。改后缀不改任何字节，播放器靠内容嗅探照样能播；但**上传校验、扩展名白名单、MIME 判断**会因此变化。真正决定"能不能被识别为 mp4"的是 ftyp 品牌。 |
| "改品牌 = 换编码" | 编码（AAC / ALAC / AC-3）在 `stsd` 的 sample entry 里，与 ftyp 无关。品牌改成 mp42 不会让 ALAC 变成 AAC，也不会让不支持的设备突然能解 ALAC。 |
| "MP4 是一种编码" | MP4 是容器。同一个 .mp4 里可以是 H.264、HEVC、AV1、AAC、ALAC、Opus……"MP4 播不了"要分两层排查：容器层（品牌/moov/索引）还是编码层（解码器能力）。 |

---

## 2. 需要吃透的容器结构

### 2.1 box 语法

每个 box（QuickTime 里叫 atom）是：

```
size(4, big-endian) | type(4, ASCII) | payload
```

三个特例必须处理，否则解析器一定会在真实文件上崩：

| 情况 | 含义 | 处理 |
|---|---|---|
| `size == 1` | 后面紧跟 8 字节 `largesize`（64 位） | header 长度变 16，用于 >4GB 的 mdat |
| `size == 0` | 该 box 一直延伸到文件/父容器末尾 | 常见于文件尾部的 mdat |
| `type == "uuid"` | 用户自定义扩展 | size 后再跟 16 字节 UUID |

`size < 8`（且不为 0）即为损坏，必须立即停止解析，不能继续 `off += size` 造成死循环。

### 2.2 顶层布局与 faststart

典型 m4a：

```
ftyp | free? | mdat | moov        ← 相机/录音机常见（moov 在尾）
ftyp | moov | free? | mdat        ← "faststart" 优化，边下边播友好
```

`moov` 放前面意味着播放器拿到前几百 KB 就能建立索引、seek；放后面则必须下载完整文件。对本地播放无影响，但**对"上传后平台转码/预览"有实际影响**。m4a 绝大多数来自 iTunes/手机录音，通常是 moov 前置；但仍要做兼容。

### 2.3 moov 树（音频 remux 只关心这些）

```
moov
├── mvhd            整体时间基（timescale / duration）
├── trak (1..n)
│   ├── tkhd        轨道 ID / 时长 / 音量（音频 volume=1.0）
│   ├── edts
│   │   └── elst    编辑列表：起始偏移、空编辑（media_time = -1）
│   ├── mdia
│   │   ├── mdhd    本轨时间基（音频通常 = 采样率）、语言
│   │   ├── hdlr    handler = "soun"
│   │   └── minf
│   │       ├── smhd   声音媒体头（balance）
│   │       ├── dinf → dref → "url " (self-contained)
│   │       └── stbl
│   │           ├── stsd   采样描述（编码类型 + 初始化数据）★
│   │           ├── stts   解码时间 → 样本（帧时长表）
│   │           ├── stsc   样本 → chunk 映射
│   │           ├── stsz   每个样本的字节数
│   │           └── stco / co64   chunk 在文件中的偏移 ★
└── udta            用户数据（iTunes 元数据、封面、章节）
```

两条关键认识：

1. **音频 remux 不需要动 `stbl` 里的任何表。** 只要 `mdat` 不移动、不增删样本，`stco/co64` 的偏移就始终有效。这正是"改 ftyp"能成立的根本原因。
2. **`stco` 是 32 位的。** 文件超过 4GB 必须用 `co64`（64 位）。这是 MP4 的经典 4GB 墙，纯音频场景很少触发（4GB AAC ≈ 60+ 小时），但批量工具应当能识别并给出明确错误，而不是写出一个"能打开但进度条乱跳"的文件。

### 2.4 音频 sample entry 的精确布局

`stsd` 里第一个 entry（4CC = `mp4a`）的字节布局如下。这些偏移在写解析器时会反复用到，直接记表：

| 相对 entry 起点 | 长度 | 字段 | 说明 |
|---|---|---|---|
| 0 | 4 | size | box 总长 |
| 4 | 4 | type | `mp4a` / `alac` / `ac-3` / `ec-3` / `fLaC` / `Opus` |
| 8 | 6 | reserved | 全 0 |
| 14 | 2 | data_reference_index | 通常 1 |
| **16** | **2** | **sound version** | **0 = ISO 风格；1 / 2 = QuickTime 扩展** |
| 18 | 2 | revision | 0 |
| 20 | 4 | vendor | 0 |
| **24** | **2** | **channel count** | 2 = 立体声 |
| 26 | 2 | sample size | 通常 16 |
| 28 | 2 | compression id | 0 |
| 30 | 2 | packet size | 0 |
| **32** | **4** | **sample rate** | 16.16 定点（44100 → `0xAC440000`） |
| 36 | … | 子 box 区 | version 0：直接是 `esds`；version 1：多 16 字节扩展；version 2：多 36 字节 |

**Sound version 是判断"这个文件是不是 QuickTime 风格"的关键位。** 如果 version ≥ 1，`esds` 往往被包在 `wave` 里，且多了 `frma` / `chan` / terminator 等 atom。严格的 ISO 解析器（包括很多 Rust/Go 的 MP4 库）会把版本扩展误读成下一个 box 的 header，报出"box size 大于父容器"的错。

Apple 官方文档对 `wave` 的描述很明确：

> `'wave'` — An atom that stores data specific to a given audio decompressor. … A `'wave'` chunk for `'mp4a'` typically contains (in order) at least a `'frma'` atom, an `'mp4a'` atom, an `'esds'` atom, and a Terminator atom.

GStreamer 的 qtdemux 为此专门写了分支：先找 `mp4a` 下的 `wave`，再从 `wave` 里找 `esds`，找不到才回落。可见这不是边角情况，是真实世界里大量存在的文件形态。

### 2.5 esds 与 AudioSpecificConfig

AAC 的初始化信息在 `esds`（Elementary Stream Descriptor）里，结构是嵌套的描述符：

```
esds
└── ES_Descriptor (tag 0x03): ES_ID, flags
    ├── DecoderConfigDescriptor (tag 0x04):
    │     objectTypeIndication ★
    │     streamType, bufferSize, maxBitrate, avgBitrate
    │   └── DecoderSpecificInfo (tag 0x05) = AudioSpecificConfig
    └── SLConfigDescriptor (tag 0x06): 0x02
```

`objectTypeIndication` 的常见取值：

| 值 | 含义 |
|---|---|
| `0x40` | MPEG-4 Audio（AAC-LC / HE-AAC / HE-AACv2 都走这里，具体靠 ASC 区分） |
| `0x66` / `0x67` / `0x68` | MPEG-2 AAC Main / LC / SSR（ADTS 风格，常见于旧文件） |
| `0x69` / `0x6B` | MPEG-2 Audio（MP3 封装进 MP4） |
| `0xA5` | AC-3；`0xA6` | E-AC-3 |
| `0xDD` | Vorbis |

`AudioSpecificConfig` 的前 5 个 bit 是 `audioObjectType`：2 = AAC-LC，5 = SBR（HE-AAC），29 = PS（HE-AACv2）。**HE-AAC 的 SBR 可以是显式（ASC 里写 5/29）也可以是隐式（ASC 写 2，靠码流里的 SBR 头触发）**。这就是为什么有些 32kHz 的 HE-AAC 在只按 ASC 建解码器的播放器上会被当成 16kHz 播放——**这跟 ftyp 品牌毫无关系，改品牌救不了**。

### 2.6 Apple 附赠的那些 box

一个来自 iTunes / iPhone 的 m4a 常常还带着：

| box | 作用 | 重封装时是否会丢 |
|---|---|---|
| `udta/meta/ilst` | 标题、艺人、专辑、封面（`covr`） | 改 ftyp：**保留**；MediaMuxer：**全丢** |
| `udta/meta/iTunSMPB` | gapless 播放的编码器延迟/填充信息 | 改 ftyp：保留；重写索引：通常丢 |
| `chpl` / `chpt` | 章节 | 改 ftyp：保留；MediaMuxer：丢 |
| `free` | 占位/对齐 | 可保留可丢，不影响播放 |
| `pssh` / `sinf` / `schm` | 加密（M4P / FairPlay） | 无法通过 remux 处理，必须拒绝 |

结论很清晰：**保真度排序 = 改 ftyp > 容器库重写 > MediaMuxer 透传 > 解码重编码**。这条排序直接决定了产品架构应该"先试最保真的、失败再降级"。

---

## 3. 三条技术路线

### 3.1 路线 A：ftyp 原地改写（Zero-copy branding）

#### 原理

只改文件头部 `ftyp` 的 12~20 个字节，其余字节按 1MB 块顺序拷贝。因为总长度不变，所有 `stco/co64` 偏移保持有效，`moov`/`mdat`/`udta` 一个字节都不动。

#### 前置条件（必须全部满足）

1. 顶层第一个 box 是 `ftyp`，且 `size >= 16`；
2. 至少存在 1 个 compatible brand（`size >= 20`）；
3. `major_brand` 是 4 字节（必然），可被覆盖；
4. 源的 compatible 列表里已经有 `isom` 或 `iso2`，或者至少有一个位置可以替换（例如把 `M4A ` 换成 `isom`）。

若第 4 条不满足，就需要"插入"一个品牌 → `ftyp` 变长 → 其后所有 box 偏移变化 → 必须移动 `mdat` → 退化到路线 B。

#### 品牌怎么写才"政治正确"

| 字段 | 建议值 | 理由 |
|---|---|---|
| `major_brand` | `mp42` | MP4 v2，兼容面最广；AOSP 的 MediaMuxer 也用它 |
| `minor_version` | `0` | 没有版本语义就别抄 512（0x200 是 isom 的历史值，DASH 一致性工具会挑刺） |
| `compatible_brands` | 保留原有 + 确保含 `isom`、`mp42` | 读方按"任一兼容品牌"判断；多余的品牌无副作用，但别塞 `avc1`（那是视频 AVC 的品牌） |

#### 参考实现（Kotlin，零依赖）

```kotlin
/**
 * 仅在"原地可改"时返回 true；返回 false 表示调用方应回退到重封装。
 * 全程只改 head 数组里的 ftyp 区，随后流式 copy，内存 O(1MB)。
 */
fun patchFtypAndCopy(input: InputStream, output: OutputStream): Boolean {
    val head = ByteArray(64 * 1024)
    var n = 0
    while (n < head.size) {
        val r = input.read(head, n, head.size - n)
        if (r <= 0) break
        n += r
    }
    if (n < 32) return false

    // 1) 定位 ftyp（正常在 offset 0，但不假设）
    var off = 0
    var ftypAt = -1
    var ftypSize = 0
    while (off + 8 <= n) {
        val size = readInt(head, off)
        val type = String(head, off + 4, 4, Charsets.ISO_8859_1)
        if (type == "ftyp") { ftypAt = off; ftypSize = size; break }
        if (size < 8) return false
        off += size
    }
    if (ftypAt < 0 || ftypSize < 20 || ftypAt + ftypSize > n) return false

    // 2) 收集品牌，判断能否在同长度内完成"归一化"
    val brandCount = (ftypSize - 16) / 4
    val brands = (0 until brandCount).map {
        String(head, ftypAt + 16 + it * 4, 4, Charsets.ISO_8859_1)
    }
    val hasIso = brands.any { it == "isom" || it == "iso2" }
    val replaceable = brands.indexOfFirst { it == "M4A " || it == "M4B " || it == "mp41" }
    if (!hasIso && replaceable < 0) return false   // 需要变长 → 回退

    // 3) 改写（长度不变，偏移全部有效）
    "mp42".toByteArray(Charsets.ISO_8859_1).copyInto(head, ftypAt + 8)
    writeInt(head, ftypAt + 12, 0)                  // minor_version = 0
    if (replaceable >= 0) {
        "isom".toByteArray(Charsets.ISO_8859_1)
            .copyInto(head, ftypAt + 16 + replaceable * 4)
    }

    // 4) 流式落盘
    output.write(head, 0, n)
    val buf = ByteArray(1 shl 20)
    while (true) {
        val r = input.read(buf)
        if (r < 0) break
        output.write(buf, 0, r)
    }
    output.flush()
    return true
}

private fun readInt(b: ByteArray, p: Int): Int =
    ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or
    ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)

private fun writeInt(b: ByteArray, p: Int, v: Int) {
    b[p] = (v ushr 24).toByte(); b[p + 1] = (v ushr 16).toByte()
    b[p + 2] = (v ushr 8).toByte(); b[p + 3] = v.toByte()
}
```

**代价：** 一次完整读写。1GB 文件在 UFS 3.1 上约 1~2 秒（受写入速度限制），不是"瞬间"，但已经是最快的一档。

#### 适用边界

- ✅ iPhone 语音备忘录、iTunes AAC、ffmpeg 生成的 m4a、绝大多数社交软件导出的 m4a
- ❌ 需要新增品牌（列表已满且无 `isom`）
- ❌ 源文件 ftyp 被非标准工具写坏
- ❌ 需要顺带修 `wave` / 拼 `moof` / 去 DRM

### 3.2 路线 B1：MediaExtractor + MediaMuxer 样本透传

#### 原理

`MediaExtractor` 解析容器、逐帧吐出**已编码的样本**（不解码），`MediaMuxer` 把这些样本按新容器重新写一遍。整个过程不碰 PCM，所以无音质损失，速度约等于读写磁盘。

这相当于 Android 平台内置的 `ffmpeg -c copy`，但只支持平台认识的容器/编码组合。

#### 参考实现（Kotlin）

```kotlin
fun remuxWithPlatform(srcFd: FileDescriptor, dstFd: FileDescriptor) {
    val extractor = MediaExtractor()
    extractor.setDataSource(srcFd)

    // 选第一条音频轨；找不到就明确报错，不要写出空 mp4
    val track = (0 until extractor.trackCount).firstOrNull {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
            ?.startsWith("audio/") == true
    } ?: error("no audio track")

    extractor.selectTrack(track)
    val format = extractor.getTrackFormat(track)   // 自带 csd-0，必须整份传给 muxer

    val muxer = MediaMuxer(dstFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val outTrack = muxer.addTrack(format)
    muxer.start()

    val buf = ByteBuffer.allocateDirect(512 * 1024)
    val info = MediaCodec.BufferInfo()
    var lastPtsUs = 0L

    while (true) {
        val size = extractor.readSampleData(buf, 0)
        if (size < 0) break
        info.offset = 0
        info.size = size
        info.presentationTimeUs = extractor.sampleTime
        info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
            MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        muxer.writeSampleData(outTrack, buf, info)
        lastPtsUs = info.presentationTimeUs
        extractor.advance()
    }

    // 用空 buffer + EOS 显式声明最后一帧时长，避免末尾被截断 ~21ms
    info.set(0, 0, lastPtsUs + 21_333, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
    muxer.writeSampleData(outTrack, ByteBuffer.allocate(0), info)

    muxer.stop(); muxer.release(); extractor.release()
}
```

#### 会丢什么（必须先接受，再决定是否用）

| 丢失项 | 影响 |
|---|---|
| `udta/meta/ilst` | 标题、艺人、专辑、**封面全丢** |
| `iTunSMPB` | 无缝播放失效（专辑连播时首尾会有几十 ms 空隙） |
| `chpl` 章节 | 章节导航丢失 |
| `elst` 编辑列表 | 开头的编码器延迟不再被裁掉，可能多出 20~40ms 静音 |
| 原始 `stco` 布局 | 索引被重建（正常，无害） |

**换来的好处：** 输出是平台自己写的干净 MP4，`major_brand = mp42`（AOSP `MPEG4Writer.cpp` 自 2014 年起就是这么写的），兼容性由系统保证；同时天然修掉 `wave`、多余的 `free`、非标准 box 等"脏"结构。

#### 平台限制

- `MediaMuxer` 自 **API 18** 起支持 MP4 单音轨；`FileDescriptor` 构造器要 **API 26**。
- 音频轨的 codec-specific data（AAC 的 `csd-0`）必须在 `addTrack(MediaFormat)` 里给，**不能**通过 `writeSampleData` 写 codec config buffer。
- MP4 输出支持的音频编码：AAC（API 16+）、AMR-NB/WB。**ALAC / AC-3 / Opus / FLAC 不在 MP4 输出支持列表内**——遇到这些编码，B1 会直接失败，必须走 C 或拒绝。
- 样本必须按时间戳单调递增写入，否则 muxer 可能抛异常或写出错乱索引。

### 3.3 路线 B2：容器库重写

当需要"移动 mdat / 拼接 moof / 增删品牌 / 修 ftyp"时，用成熟库比手写安全。

| 库 | 语言 | 能做什么 | 不能做什么 | 许可证 | Android 集成 |
|---|---|---|---|---|---|
| **mp4parser / isoparser** | 纯 Java | 读写 box 树、改 ftyp、mux/demux、章节、CENC | 不解码；对超大 `moov` 内存占用高；项目 2022 年后基本停滞 | Apache-2.0 | Gradle 直接引入，需排除 `aspectjrt` |
| **Bento4** | C++ | `mp4edit`/`mp4mux`/`mp4info`/`mp4dump`，工业级 | 不易裁剪；GPL 传染 | GPL-2.0 或商业授权 | NDK 交叉编译，体积大 |
| **GPAC / libgpac** | C | `MP4Box -brand mp42:0`、`-add`、`-inter`、DASH/HLS | 学习曲线陡；库很大 | LGPL-2.1+ | NDK 可编，但为了改 4 字节不值 |
| **FFmpeg** | C | `-c copy -brand mp42` 一把梭；`-movflags +faststart` | 体积、许可证、JNI 复杂度 | LGPL-2.1+（`--enable-gpl` 则 GPL） | 见第 5 节 |

**mp4parser 的正确用法不是 `MovieCreator + DefaultMp4Builder`**（那是有损重建，会丢未知 box），而是：

```java
IsoFile iso = new IsoFile(channel);          // 惰性解析 box 树
FtypBox ftyp = (FtypBox) iso.getBoxes().get(0);
ftyp.setMajorBrand("mp42");
ftyp.setMinorVersion(0);
// 直接 writeContainer：未修改的 box 按原字节输出，mdat 原样拷贝
iso.writeContainer(outChannel);
```

注意：`IsoFile` 会把 `moov` 全量解析成对象。一个 2 小时的 AAC 有声书可能有十几万样本，`stsz`/`stts` 数组会占几十 MB 堆——批量处理时要限制并发，或对超大文件直接走路线 A。

### 3.4 路线 C：解码重编码（不在本期范围，但必须知道何时被迫）

以下情况，remux 无论怎么做都救不了，只能解码再编码：

- 编码本身目标端不支持：ALAC → AAC、AC-3/E-AC-3 → AAC、Opus/FLAC in MP4 → AAC
- 采样率/声道数超出目标端能力
- 源文件 `esds` 损坏，但 `mdat` 里的 AAC 帧是好的（可以按 ADTS 重新封装）
- 需要往音频里加画面（封面图、波形、歌词）——这是"音频转视频"，属于另一条产品线

代价：音质二次损失、耗时（1 小时音频约 1~3 分钟）、耗电、发热。**产品上应当把 C 做成显式的"高级模式"，而不是默认路径。**

### 3.5 三条路线横向对比

| 维度 | A. 改 ftyp | B1. 平台透传 | B2. 容器库 | C. 重编码 |
|---|---|---|---|---|
| 是否解码 | 否 | 否 | 否 | 是 |
| 音质 | 100% 原样 | 100% 原样 | 100% 原样 | 有损 |
| 速度（1GB） | 1~2 s（IO 上限） | 2~5 s | 2~8 s | 分钟级 |
| 元数据/封面 | 全保留 | 全丢 | 取决于实现 | 需手动搬 |
| 无缝播放 | 保留 | 丢 | 视实现 | 丢 |
| 能修 `wave` | 不能 | 能 | 能 | 能 |
| 能处理 fMP4 | 不能 | 不能 | 能 | 能 |
| APK 增量 | 0 | 0 | +0.3~2 MB | +10~20 MB |
| 实现成本 | 半天 | 1~2 天 | 3~5 天 | 2 周+ |
| 失败率 | 低（但覆盖面窄） | 中 | 低 | 极低 |

---

## 4. 决策矩阵：每种输入走哪条路

| 输入特征 | 判定 | 路线 | 说明 |
|---|---|---|---|
| `mp4a` + AAC-LC，ftyp 含 `isom` | 标准 m4a | **A** | 90% 的真实场景 |
| `mp4a` + HE-AAC v1/v2 | 标准 m4a | **A** | 品牌无关；能否播取决于解码器 |
| `mp4a` + version 1/2 + `wave` | QuickTime 风格 | **B1**（失败转 B2） | 常见于 QuickTime / 部分剪辑软件导出 |
| `alac` sample entry | Apple 无损 | **A 试 + 校验**，失败提示"需转码" | Android 各版本 ALAC 支持不一致 |
| `ac-3` / `ec-3` / `dts*` | 影院音频 | 拒绝或 **C** | MP4 输出不支持，且目标端多半播不了 |
| `Opus` / `fLaC` in MP4 | 新式封装 | 拒绝或 **C** | 同上 |
| `moov` 含 `mvex` + 有 `moof` | 碎片化 MP4 | **B2** | 需拼接为普通 MP4，A/B1 都不行 |
| 存在 `pssh` / `sinf` / `schm` | 加密（M4P/FairPlay） | 拒绝 | 无密钥无法处理 |
| 无 `moov` / `mdat` 截断 | 文件损坏 | 拒绝并给出明确原因 | 别做"尽力修复"，会产出坏文件 |
| 多轨（音频 + 章节文本轨） | 有声书 | **A** | A 保留；B1 会丢章节 |
| 超过 4GB | 超大文件 | A（若 `co64` 已就绪）/ B2 | 检查是 `stco` 还是 `co64` |

**产品化建议：** 探测阶段就把这张表实现成 `enum Route { PATCH_FTYP, PLATFORM_REMUX, LIB_REMUX, TRANSCODE, REJECT }`，UI 上对 `TRANSCODE` 和 `REJECT` 给出人话解释（"这个文件是无损格式，转成 mp4 需要重新编码，音质会有损失"），而不是笼统的"转换失败"。

---

## 5. 开源技术盘点

### 5.1 FFmpeg 生态（含 ffmpeg-kit 退役现状）

**能力：** `ffmpeg -i in.m4a -c copy -brand mp42 out.mp4` 一行完成；`-movflags +faststart` 可顺手把 moov 前置；`-map` 可精确选轨。`-c copy` 就是标准的 remux，不会解码。

**Android 集成现状（2026-11 核实）：**

- `arthenica/ffmpeg-kit` 仓库**已于 2026-07-02 被作者归档（read-only）**，README 明确写 "FFmpegKit has been officially retired"；最后一版停在 6.0（2023-08）。
- 继任者是 **FFmpegKitNext**（同作者），**只分发源码**，需要自己跑 `android.sh` 编 `.aar`。
- 各包管理器上有社区 fork（Maven Central / pub / npm），质量与许可证需逐个核对。
- 默认 LGPL-3.0；带 `-gpl` 后缀的包因链接 x264/x265 而整体变成 GPL-3.0。

**体积代价：** 即使 `min` 包（无外部库），每个 ABI 的 `.so` 也是十几 MB 量级；四 ABI 全打，APK 增加 40MB+。对"改 4 字节"的需求，性价比极低。

**结论：** 本期不用；如果未来要做"ALAC/AC-3 → AAC"的真转码，再引 FFmpegKitNext 或自编 min 包。

### 5.2 GPAC / MP4Box

LGPL-2.1+，MP4/ISOBMFF 领域最权威的实现之一，`MP4Box -brand mp42:0` 可以精确设置 major brand 和 minor version（连 FFmpeg 的 `-brand` 都改不了 minor）。支持 Android 交叉编译。

**问题：** 库体积大、API 是 C 风格、文档偏学术。为一个 ftyp 改写引入它，属于"用起重机拧螺丝"。

**适用：** 服务端批处理、需要 DASH/HLS/CMAF 的场景。

### 5.3 Bento4

C++，工具链齐全（`mp4info`/`mp4dump`/`mp4edit`/`mp4mux`/`mp4fragment`），对 Apple 私有 box、CENC、iTunes 元数据的覆盖非常完整，是做**离线批处理/质检**的好选择。

**许可证是硬门槛：** GPL-2.0（buildroot 的 `bento4.mk` 里写的是 `GPL-2.0+`），闭源商用必须买商业授权。**不要**在闭源 App 里直接链接。

### 5.4 mp4parser / isoparser（Apache-2.0）

纯 Java，无需 NDK，Gradle 一行引入：

```gradle
implementation('org.mp4parser:isoparser:1.9.27') {
    exclude group: 'org.aspectj', module: 'aspectjrt'
}
```

- 优点：进程内、无 JNI、可精细操作 box、许可证友好（Apache-2.0）、能保留未识别 box（只要不动它）。
- 缺点：项目更新停滞（最后一次实质提交 2022 年）；`MovieCreator + DefaultMp4Builder` 是有损重建；大文件内存占用需评估。
- 定位：**B2 路线里最务实的选择**，适合处理 `wave`、需要插入品牌、或需要读 `udta` 的场景。

### 5.5 AndroidX Media3 Transformer（Apache-2.0）

官方文档明确写了 transmux 机制：

> If the input media format already matches the configurations for audio or video, Transformer automatically switches to *transmuxing*, that is, copying the compressed samples from the input container to the output container without modification.

默认输出 `InAppMp4Muxer` 生成的标准 MP4。`setAudioMimeType(MimeTypes.AUDIO_AAC)` 即可走透传。

**但它仍然会重建容器**，和 B1 一样会丢 `udta`/封面/章节。它的价值在于：如果未来要加"封面图 + 音频合成视频""裁剪""音量归一化"，Transformer 是官方支持、维护活跃的那条路。

**结论：** 本期不作为主路径；作为 v2 的功能扩展基座。

### 5.6 平台 API（零依赖）

`MediaExtractor` + `MediaMuxer`：见 3.2。**这是本项目最该重仓的技术**——它就在系统里，不增加包体，不引入许可证问题，速度够快。

### 5.7 其他

| 项目 | 说明 | 结论 |
|---|---|---|
| mp4v2 | MPL-1.1，老牌 C++ 库，维护停滞 | 不推荐 |
| Shaka Packager | Apache-2.0，主打打包/加密，不是轻量 remux | 不适用 |
| `Videotomp3`（GitHub） | 真实参考实现：`MediaExtractor + MediaMuxer` 从视频抽 M4A，零第三方库，GPL-3.0 | 可读代码，不可抄许可证 |
| `OpenConverter` | Android 端用 FFmpegKit 做格式转换 | 说明"重转码"场景才需要 FFmpeg |

---

## 6. 推荐架构

### 6.1 分层

```
┌─────────────────────────────────────────────────┐
│ UI (Compose) 列表 / 批量 / 进度 / 失败原因        │
├─────────────────────────────────────────────────┤
│ Task 层  WorkManager + 前台服务(mediaProcessing) │
├─────────────────────────────────────────────────┤
│ Policy 层  探测结果 → Route 决策 → 用户可读文案     │
├─────────────────────────────────────────────────┤
│ Executor   A: FtypPatcher                        │
│            B1: PlatformRemuxer                   │
│            B2: BoxRewriter (mp4parser)           │
│            C:  TranscodeWorker (可选，FFmpeg)     │
├─────────────────────────────────────────────────┤
│ Probe 层   自研 box walker（只读 header，不建全表）│
├─────────────────────────────────────────────────┤
│ IO 层      SAF / MediaStore / FileDescriptor     │
└─────────────────────────────────────────────────┘
```

### 6.2 模块职责

| 模块 | 输入 | 输出 | 关键点 |
|---|---|---|---|
| `SourcePicker` | 用户选择 | `List<Uri>` + 持久化权限 | `ACTION_OPEN_DOCUMENT` + `EXTRA_ALLOW_MULTIPLE` + `takePersistableUriPermission` |
| `Prober` | `Uri` | `ProbeResult` | 只扫顶层 box + `moov` 的必要子树；不解析 `stsz` 全表 |
| `Policy` | `ProbeResult` | `Route` + `Reason` | 见第 4 节矩阵 |
| `FtypPatcher` | fd | 新 fd | 见 3.1 |
| `PlatformRemuxer` | fd | 新 fd | 见 3.2 |
| `BoxRewriter` | fd | 新 fd | mp4parser，仅处理 A/B1 覆盖不了的样本 |
| `Verifier` | 输出 fd | `VerifyResult` | 重开文件，校验 brand / 轨道数 / 时长 / 可 seek |
| `Sink` | 输出流 | 用户可见文件 | MediaStore `IS_PENDING` 原子落盘 |

### 6.3 ProbeResult 该包含什么

```kotlin
data class ProbeResult(
    val fileSize: Long,
    val majorBrand: String,
    val compatibleBrands: List<String>,
    val ftypOffset: Int,
    val ftypSize: Int,
    val moovBeforeMdat: Boolean,
    val isFragmented: Boolean,      // moov 里有 mvex，或出现过 moof
    val isEncrypted: Boolean,       // pssh / sinf / schm
    val audioCodec: String?,        // "mp4a" / "alac" / "ac-3" ...
    val soundVersion: Int,          // 0 / 1 / 2
    val hasWaveAtom: Boolean,
    val objectTypeIndication: Int?, // 0x40 / 0x67 ...
    val sampleRate: Int,
    val channelCount: Int,
    val durationMs: Long,
    val trackCount: Int,
    val hasCoverArt: Boolean,       // udta/meta/ilst/covr
    val hasChapters: Boolean,       // chpl / chpt
    val chunkOffset64: Boolean      // co64 而非 stco
)
```

有了这份结构，第 4 节的矩阵就是十几行 `when`。

### 6.4 存储与落盘

- **读取**：`contentResolver.openFileDescriptor(uri, "r")` → `FileInputStream(fd)`。不要尝试把 `content://` 转成 `/storage/...` 路径（Android 10+ 基本拿不到）。
- **写入**：API 29+ 用 `MediaStore.Downloads` / `MediaStore.Video`：

```kotlin
val values = ContentValues().apply {
    put(MediaStore.MediaColumns.DISPLAY_NAME, "$base.mp4")
    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/MyTools")
    put(MediaStore.MediaColumns.IS_PENDING, 1)   // 写完再置 0，避免半成品被扫到
}
```

- **原子性**：先写 `xxx.mp4.part`，`Verifier` 通过后再 rename / 置 `IS_PENDING = 0`。任何异常路径都要清理临时文件（用 `try/finally`，不要依赖 GC）。
- **同名冲突**：`DISPLAY_NAME` 冲突时系统会自动加 `(1)`，但 SAF 写入不会；两条路径都要自己处理。

### 6.5 任务与并发

- **单文件、< 30 秒**：`viewModelScope` + `Dispatchers.IO`，页面销毁即取消（用户预期内）。
- **批量 / 大文件**：`WorkManager` + `setForeground`，前台服务类型用 **`mediaProcessing`**（Android 15 / API 35 新增，正是为"转码媒体文件"设计的；配额 6 小时 / 24 小时，超时回调 `Service.onTimeout`，必须几秒内 `stopSelf()`）。
- **并发度**：1~2 个。这是 IO 密集型任务，并发多了只会互相抢磁盘带宽，还容易触发发热降频。
- **进度**：按已拷贝字节 / 总字节；不要用"样本数"（音频样本数对用户无意义）。

### 6.6 校验（最容易被跳过，但最该做）

```kotlin
fun verify(outFd: FileDescriptor, expectedMs: Long): Boolean {
    val ex = MediaExtractor()
    return try {
        ex.setDataSource(outFd)
        if (ex.trackCount < 1) return false
        val f = ex.getTrackFormat(0)
        val mime = f.getString(MediaFormat.KEY_MIME) ?: return false
        val durUs = if (f.containsKey(MediaFormat.KEY_DURATION))
            f.getLong(MediaFormat.KEY_DURATION) else 0L
        mime.startsWith("audio/") &&
            (expectedMs <= 0 || kotlin.math.abs(durUs / 1000 - expectedMs) <= 50)
    } catch (e: Exception) {
        false
    } finally {
        ex.release()
    }
}
```

校验失败就删掉输出、标记 `FAILED(VERIFY)`，绝不把半成品交给用户。这一步能把 90% 的"用户反馈说文件坏了"挡在发布前。

---

## 7. 十八个坑（按踩到的概率排序）

1. **`M4A ` 尾部有空格。** 用 `"M4A"` 去比较永远匹配不上，必须用 `"M4A "`（4 字符）。
2. **不要假设 ftyp 在 offset 0。** 规范要求它在最前，但坏文件什么都有；按 box 链走更稳。
3. **`size == 0` 的 mdat 表示"到文件尾"。** 解析循环若不做特判会死循环。
4. **`size == 1` 的 largesize。** 少读 8 字节，后面全错位。
5. **`stco` 是 32 位。** 超 4GB 必须 `co64`；改写前先判断，别产出偏移溢出的文件。
6. **QuickTime `meta` box 不是 FullBox。** ISO 的 `meta` 有 version+flags 4 字节，QuickTime 的没有。写通用解析器时这是经典 bug，会导致 `ilst` 全部解析失败。
7. **`wave` 里的 `esds`。** version ≥ 1 时必须往 `wave` 里找，否则 `objectTypeIndication` 读出来是垃圾。
8. **`esds` 的描述符长度是变长编码（1~4 字节，高 bit 续位）。** 按固定 1 字节读会在长 ASC 上错位。
9. **HE-AAC 的隐式 SBR。** ASC 写的是 LC，实际是 HE；不要用 ASC 的采样率去推断真实输出采样率。
10. **`elst` 的 `media_time = -1`** 表示空编辑（延迟），不是"从 0 开始"。忽略它会让开头多出静音。
11. **`iTunSMPB` 一旦丢失，无缝播放就没了。** 这是"改 ftyp"相对 MediaMuxer 的隐性优势，别轻易放弃。
12. **MediaMuxer 的 csd 必须来自 extractor 的 `MediaFormat`。** 自己拼 `csd-0` 十有八九拼错，表现为"能播但全是噪音"或"时长正常但无声"。
13. **不要把 `BUFFER_FLAG_CODEC_CONFIG` 的样本写进 muxer。** 会多出一帧垃圾数据。
14. **最后一帧时长。** MediaMuxer 默认用"前一帧时长"，音频结尾会被截掉约 21ms；用空 buffer + EOS 显式声明。
15. **`MediaMuxer(FileDescriptor)` 要 API 26。** minSdk 低于 26 时用路径构造器 + app 私有目录中转。
16. **SAF 写入没有 `rename`。** 要原子性只能"写临时 DocumentsFile → 校验 → 复制/移动 → 删临时"，或者直接走 MediaStore。
17. **前台服务类型。** Android 14 起必须声明类型，Android 15 起 `mediaProcessing` 有 6h/24h 配额，且超时要 `stopSelf()`，否则 ANR/异常。
18. **不要在 UI 线程读文件头。** 哪怕是 64KB，网络盘/云盘 Uri 也能卡住几百毫秒。

---

## 8. 测试矩阵

### 8.1 输入样本（必须覆盖）

| 样本 | 构造方式 | 主要验证点 |
|---|---|---|
| iPhone 语音备忘录 | 真机录一段 | 标准 AAC，路线 A |
| iTunes AAC 歌曲 | 商店购买/导入 | `ilst` + `covr` + `iTunSMPB` 是否保留 |
| iTunes ALAC | `ffmpeg -i in.wav -c:a alac out.m4a` | 是否被正确识别为"需转码" |
| ffmpeg 生成 | `ffmpeg -i in.wav -c:a aac -b:a 128k out.m4a` | 最干净的基准 |
| QuickTime 风格 | `ffmpeg -f mov` 或 macOS 导出，带 `wave` | 路线 B1 是否成功 |
| 带章节有声书 | `ffmpeg -i in.m4a -i chapters.txt -map_metadata 1` | `chpl` 保留/丢失是否符合预期 |
| fMP4 音频 | `ffmpeg -movflags frag_keyframe+empty_moov` | 是否被判为 `isFragmented` |
| 4GB+ | `ffmpeg -stream_loop` 循环生成 | `co64` 判断与耗时 |
| 截断文件 | `head -c` 切掉尾部 | 必须明确失败，不能产出坏文件 |
| DRM 文件 | 若有 M4P 样本 | 必须明确拒绝 |

### 8.2 目标端（决定"能不能用"）

| 接收方 | 关注点 |
|---|---|
| 系统相册 / MediaPlayer / ExoPlayer | 能否识别为视频、时长是否正确 |
| 微信"发视频" | 纯音频 mp4 是否被接受（**重点验证**） |
| 抖音 / B站 投稿 | 是否要求存在视频轨、封面、时长下限 |
| Chrome / Safari | `<video>` 标签能否播放（`audio/mp4` vs `video/mp4`） |
| Windows Media Player / PowerPoint 插入 | 老解码器对 `mp42` 的接受度 |
| 剪映 / CapCut 导入 | 是否把纯音频 mp4 当音频素材 |

### 8.3 设备

- 最低支持版本（如 API 26）、Android 13 / 14 / 15 各一台
- 至少一台国产 ROM（后台限制、FGS 行为差异明显）
- 至少一台 4GB 内存设备（验证大 `moov` 的内存表现）

---

## 9. 许可证与合规

| 方案 | 许可证 | 闭源商用 | 注意 |
|---|---|---|---|
| 自研 box 解析 + 平台 API | 你自己的 | ✅ | 无第三方义务 |
| mp4parser | Apache-2.0 | ✅ | 保留 NOTICE |
| Media3 | Apache-2.0 | ✅ | 保留 NOTICE |
| GPAC | LGPL-2.1+ | ✅（动态链接） | 需提供替换库的方式 |
| FFmpeg | LGPL-2.1+ | ✅（动态链接、不启用 GPL 组件） | 必须可替换 `.so`；`--enable-gpl` 后整体变 GPL |
| ffmpeg-kit | LGPL-3.0 | ⚠️ | LGPLv3 含反锁定条款，Embedded 设备需注意 |
| Bento4 | GPL-2.0 / 商业 | ❌（GPL 版） | 闭源必须买授权 |

**AAC 专利：** AAC 的专利池（Via LA）历史上对"编码/解码"收费，对"仅重新封装、不解码"的产品通常不构成实施行为。但这不是法律意见——如果产品要做付费分发，建议让法务确认当前政策。**只做 remux 的一个额外好处，就是绕开了编解码专利的大部分风险。**

**隐私：** 全本地处理是核心卖点，务必写进隐私政策；不要申请 `MANAGE_EXTERNAL_STORAGE`（商店审核会重点查），用 SAF + MediaStore 完全够用。

---

## 10. 落地建议（分三期）

**v0.1（1~2 周）——验证假设**
- `Prober`（只支持 AAC/`mp4a`）
- `FtypPatcher` + `Verifier`
- 单文件、SAF 输入、MediaStore 输出
- **先拿 20 个真实样本跑通"改品牌后各端能播"**，尤其是微信/抖音的上传路径
- 如果这一步就发现"纯音频 mp4 平台不收"，立刻停下来重新定义产品（改做"音频转视频"或"音频转 m4a 规范化"）

**v0.2（2~3 周）——覆盖长尾**
- `PlatformRemuxer` 兜底（`wave`、脏结构）
- 批量队列 + WorkManager + `mediaProcessing` 前台服务
- 失败原因分类与用户可读文案
- 覆盖矩阵里的 10 类样本

**v0.3（可选）——增值**
- 封面/标题保留与编辑（读 `ilst`）
- fMP4 拼接（mp4parser）
- "音频 + 封面图 → 视频"（Media3 Transformer 或 MediaCodec）
- 真转码（FFmpegKitNext），作为付费/高级功能

---

## 11. 主要参考来源

- MP4 Registration Authority — Registered Brands（`M4A ` / `mp42` / `isom` 的官方定义）：https://mp4ra.org/registered-types/brands
- Apple Developer — QuickTime File Format, siDecompressionParam atom (`wave`)：https://developer.apple.com/documentation/quicktime-file-format/sidecompressionparam_atom
- Android Developers — `MediaMuxer`（支持的容器/编码、csd 要求、EOS 语义）：https://developer.android.com/reference/android/media/MediaMuxer
- Android Developers — Media3 Transformer, Transformations（transmux 机制）：https://developer.android.com/media/media3/transformer/transformations
- Android Developers — Media3 Transformer, Supported formats（默认输出 `InAppMp4Muxer`）：https://developer.android.com/media/media3/transformer/supported-formats
- AOSP — `MPEG4Writer.cpp` 使用 `mp42` 作为 major brand（commit 8284de3）：https://android.googlesource.com/platform/frameworks/av/+/8284de3be2ac07d8774b15e6565df5aba084db04%5E%21/media/libstagefright/MPEG4Writer.cpp
- AOSP — `movenc.c` 增加 `-brand` 选项（Override major brand）：https://android.googlesource.com/platform/external/ffmpeg/+/1e9db41e2a2166be5671b088ef4ad06a40af459f%5E%21/libavformat/movenc.c
- FFmpeg Trac #5178 — `MP4Box -brand mp42:0` 与 major/minor brand 的实际行为：https://trac.ffmpeg.org/ticket/5178
- arthenica/ffmpeg-kit — 仓库已归档（2026-07-02），FFmpegKitNext 为继任者：https://github.com/arthenica/ffmpeg-kit
- sannies/mp4parser — Apache-2.0，`isoparser` 1.9.27：https://github.com/sannies/mp4parser
- GPAC — LGPL-2.1+，MP4Box：https://github.com/gpac/gpac
- Bento4 — GPL-2.0 / 商业双许可：https://www.bento4.com
- Linuxoid-cn/Videotomp3 — `MediaExtractor + MediaMuxer` 的真实参考实现（GPL-3.0）：https://github.com/Linuxoid-cn/Videotomp3
- 小米澎湃 OS 开发者平台 — Android 15 适配指南（`mediaProcessing` 前台服务类型、6h/24h 配额）：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1826

---

### 附：一句话备忘

> m4a 与 mp4 的差别，本质是**一张标签**，不是**一种编码**。
> 所以这个 App 的核心竞争力不在"转得快"，而在"**敢保证一个字节都没动**"——这恰恰是 FFmpeg 方案讲不出来的故事。
