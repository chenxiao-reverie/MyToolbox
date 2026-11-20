# 工具箱 UI 设计规范

> 风格关键词：**纸感、墨色、单点强调、克制**
> 目标：在一屏里让用户 3 秒内找到工具、1 秒内理解代价、0 疑问地拿到结果。
> 配套预览：聊天内的四屏高保真稿（首页 / 工具页 / 队列 / 完成态）。

---

## 1. 设计主张

### 1.1 三条原则

**① 像纸，不像玻璃。**
不用渐变、不用毛玻璃、不用投影堆叠。用暖白纸底 + 1px 细线 + 大留白建立层次。工具类产品每天被打开很多次，视觉噪音会在第三次使用时变成烦躁。

**② 颜色只用来表达状态，不用来装饰。**
全站只有一个强调色（松绿），只出现在三处：主按钮、当前选中、进度。其余全部是墨色灰度。这样"绿色出现"就等于"这里有可操作/正在进行的事"，形成稳定的视觉语言。

**③ 数字必须对齐。**
时长、大小、百分比、码率全部用等宽数字（tabular numerals）。这是"专业工具"和"玩具"最廉价也最有效的分界线。

### 1.2 一句话气质

> 像一本排版讲究的说明书，而不是一个想讨好你的 App。

---

## 2. 色彩

### 2.1 亮色（默认）

| Token | 值 | 用途 | 占比 |
|---|---|---|---|
| `paper` | `#F7F5EF` | 页面底色 | 60% |
| `surface` | `#FFFFFF` | 卡片、输入框、弹层 | 30% |
| `ink` | `#17170F` | 主文字、主按钮底 | 8% |
| `ink2` | `#5E5C52` | 次要文字、说明 | — |
| `ink3` | `#99968A` | 辅助信息、占位、单位 | — |
| `pine` | `#1E4B3D` | 强调：主按钮、选中、进度 | 2% |
| `pineSoft` | `#E7EDE9` | 强调底色：预判条、图标底 | — |
| `hairline` | `#E7E3D8` | 1px 描边、分隔线 | — |
| `clay` | `#A9552F` | 失败、警告（唯一允许的第二个彩色） | <1% |

**规则：**
- 一屏内出现 `pine` 的面积不超过 8%，否则强调失效。
- `clay` 只用于"需要用户注意"的状态，不用于普通说明。
- 不使用纯黑 `#000` 和纯白 `#FFF` 作为大面积底色（纸白更柔，长时间看不刺眼）。

### 2.2 暗色

| Token | 值 |
|---|---|
| `paper` | `#0E0F0C` |
| `surface` | `#181914` |
| `ink` | `#F2F1E9` |
| `ink2` | `#A7A499` |
| `ink3` | `#8A887C` |
| `pine` | `#7FBFA4`（提亮，保证对比度） |
| `pineSoft` | `#1B2A24` |
| `hairline` | `#2A2B25` |
| `clay` | `#D08462` |

暗色不是简单反色：强调色必须提亮，否则在深底上会"糊"成一片。纸感通过 `surface` 比 `paper` 亮一档来保留（而不是靠阴影）。

### 2.3 对比度

| 组合 | 对比度 | 结论 |
|---|---|---|
| `ink` on `paper` | 15.8:1 | AAA |
| `ink2` on `paper` | 6.9:1 | AA |
| `ink3` on `paper` | 3.0:1 | 仅用于 ≥11px 的非关键信息 |
| `#FBFAF5` on `pine` | 11.2:1 | AAA |

`ink3` 用在小字上要谨慎：时长、单位这类"看错了也不致命"的信息可以，操作按钮绝对不行。

---

## 3. 字体与数字

### 3.1 字阶

| 角色 | 字号 | 字重 | 字距 | 行高 | 用途 |
|---|---|---|---|---|---|
| Display | 28 | 600 | −0.5 | 1.2 | 完成态主标题（可选） |
| Title | 20 | 600 | −0.3 | 1.25 | 页面标题 |
| Card Title | 12.5 | 600 | −0.1 | 1.35 | 工具卡、文件卡 |
| Body | 11.5 | 400 | 0 | 1.55 | 说明文字 |
| Caption | 9.5 | 400 | 0 | 1.45 | 元信息 |
| Eyebrow | 9.5 | 600 | +2.1 | 1.2 | 全大写/英文小标 |
| Mono | 10 | 400 | −0.2 | 1.4 | 数字、时长、大小、百分比 |

> 上面的数值是 1x 视觉稿尺寸（对应 360dp 宽设备）。在 Compose 里用 `sp`，并**不要让系统字体缩放破坏布局**：容器用 `heightIn(min=)` 而不是固定 `height`。

### 3.2 中文排版

- 中文不设 `letterSpacing`（设为 0），英文标签才加字距。
- 标题不用衬线体。中文衬线在手机小字号下笔画会糊，优雅感来自留白和对比，不是字体本身。
- 中英混排时，数字/英文两侧留 0.15em 空隙由系统处理，不要手动加空格。
- 行高 ≥ 1.5，中文方块字在 1.4 以下会显得拥挤。

### 3.3 数字

```kotlin
val MonoNumber = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp,
    fontFeatureSettings = "tnum",   // tabular numerals
    letterSpacing = (-0.2).sp
)
```

三处必须用：
1. 进度百分比（`68%` 跳动时不能左右抖）
2. 时长（`3:42` / `2.1s`）
3. 文件大小（`8.6 MB` / `5.9 / 8.6 MB`）

---

## 4. 网格、圆角、描边

### 4.1 间距

8pt 基准，但允许 4 的倍数做微调：

```
4 / 8 / 12 / 16 / 20 / 24 / 32
```

| 场景 | 值 |
|---|---|
| 页面左右边距 | 16 |
| 卡片内边距 | 10–12 |
| 卡片之间 | 9 |
| 分组之间 | 14–16 |
| 底部安全区上方 | 10 + inset |

### 4.2 圆角

只有三档，**不允许出现第四档**：

| 档位 | 半径 | 用途 |
|---|---|---|
| S | 8 | 图标底、小标签 |
| M | 11 | 输入框、按钮 |
| L | 15 | 卡片、预判条 |
| Full | 999 | 只有搜索框、进度条 |

手机屏幕本身圆角 31，属于设备外壳，不算设计系统。

### 4.3 描边代替阴影

```kotlin
Modifier.border(1.dp, palette.hairline, shape)
```

- 全站不使用 `shadow` / `elevation`。
- 需要"浮起来"的组件（底部弹层），用 `surface` 白底 + 顶部 1px 线 + 16dp 圆角，**不加阴影**。
- 分割线优先用留白，其次用 1px 线，最后才用底色块。

---

## 5. 图标

- 线性图标，**1.5px 描边**，24×24 网格，圆角端点（`strokeLineCap = Round`）。
- 默认 `ink2`，选中/激活时 `pine`。
- 图标底：26×26 的 `pineSoft` 方块，圆角 8，**只在工具卡和文件卡上出现**，避免全站图标都带色块。
- 不使用多色插画。工具箱的价值在效率，不在可爱。

推荐图标语义映射：

| 工具 | 图标 |
|---|---|
| 转成 MP4 | 文件 + 外送箭头 |
| 转成 M4A | 音符 |
| 提取音轨 | 胶片 + 音轨 |
| 无损裁剪 | 剪刀 |
| 音频转码 | 四角箭头（转换） |
| 视频压缩 | 胶片 + 竖分割 |

---

## 6. 组件规范

### 6.1 工具卡 `ToolCard`

```
┌──────────────────┐
│ [icon]           │  26×26，pineSoft 底
│                  │
│ 转成 MP4          │  12.5 / 600
│ 容器 · 无损        │  9.5 / ink3
└──────────────────┘
```

- 尺寸：2 列网格，宽自适应，高约 74–78。
- 背景 `surface`，1px `hairline`，圆角 15。
- 按压态：整卡 `scale 0.98` + 背景转 `paper`，120ms。
- **不要**在卡上加阴影或渐变，不要用彩色图标。
- 未上线功能：右下角一个 3px 的 `pine` 小点 + `tsub` 写"即将"，**不要**置灰到看不清。

### 6.2 预判条 `PreflightBanner`

这是本产品最重要的组件——它把"失败"提前成"知情"。

| 语气 | 底色 | 文字 | 什么时候用 |
|---|---|---|---|
| 无损 | `pineSoft` | `pine` | L0 容器级，不改编码 |
| 需转码 | `warningBg` | `clay` | L2，音质会变 |
| 不可用 | `errorBg` | `clay` | 加密/损坏/不支持 |

```
● 可无损转换                      ≈ 2 秒
  不重新编码，音质完全保留
```

- 6px 圆点表示状态，不用图标（图标会抢标题）。
- 右侧固定显示"代价"：时间或大小，用 Mono。
- 文案必须包含**后果**，而不只是结论："音质完全保留" / "会重新编码，高频略有损失"。

### 6.3 分段控件 `SegmentedTabs`

两种形态，按场景选，不要混用：

| 形态 | 用法 |
|---|---|
| 下划线式 | 页面级筛选（首页分类），2px `pine` 下划线，无背景 |
| 药丸式 | 表单内二选一（保留/删除原文件），`ink` 实底 + 白字 |

药丸式必须"选中项反色"，避免用户分不清哪边是当前值。

### 6.4 细进度 `ThinProgress`

```kotlin
Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape)
        .background(palette.hairline)) {
    Box(Modifier.fillMaxWidth(progress).fillMaxHeight()
        .background(palette.pine))
}
```

- 列表里 **2dp**，任务详情页 3dp。**不要**用 6–8dp 的粗条，那是 dashboard 的语言。
- 动画：`animateFloatAsState(target=progress, tween(300, easing=LinearEasing))`，进度条用线性缓动，不要 spring（会回弹，显得不准确）。
- 不确定进度（无法预估）：改用 1px 的"来回滑动"细线，不要用旋转菊花。

### 6.5 主按钮 `PrimaryCTA`

- 高 44–48，圆角 12，`pine` 底，`#FBFAF5` 字，12.5/600。
- 全宽，贴在底部安全区上方，上方 10dp 留白。
- 按钮下方一行 Mono 小字：`预计 8.6 MB · 无需联网`。
- 禁用态：`hairline` 底 + `ink3` 字，**不要**降低整体透明度（纸感会脏）。
- 同屏只能有一个主按钮。次要操作永远用 ghost（白底 + 描边）。

### 6.6 任务行 `JobRow`

```
录音_2026-11-18.m4a                 68%
───────────────
正在写入 · 5.9 / 8.6 MB            取消
```

- 文件名 11/600，右对齐百分比 Mono。
- 阶段文案必须**具体**："正在写入 5.9/8.6 MB" 优于 "处理中"。
- 取消是文字按钮（`clay`），不用 ✕ 图标——误触率低，且更克制。
- 失败行：整卡描边转 `#EBD3C6`，标题转 `clay`，第二行给原因，右侧"重试"。
- 完成行：22px 空心圆 + 对勾，右侧"打开"。**不弹 Toast**，在列表里就地反馈。

### 6.7 空状态

- 一句话 + 一个动作，不要插画。
- 例：「还没有任务 / 从上面的工具开始」，配一个 ghost 按钮。
- 不要用"空空如也~"这类卖萌文案。

---

## 7. 四个页面的视觉决策

### 7.1 首页

**为什么标题是"今天要处理什么"而不是"工具箱"？**
"工具箱"是产品视角，"今天要处理什么"是用户视角。Eyebrow 已经用小字标了 Toolbox，标题留给任务，层级就清楚了。

**为什么分类用下划线而不是 chips？**
chips 会跟工具卡抢视觉重量（都是圆角块）。下划线只用一条 2px 的线，几乎不占面积，让 6 张卡成为唯一焦点。

**为什么"进行中"放页面底部而不是顶部？**
用户打开 App 的第一意图是"发起新任务"，不是"看进度"。进行中的信息用一条细进度带过即可，不打断主路径；要细节就点进任务页。

### 7.2 工具页

**信息顺序 = 决策顺序**：是什么文件 → 能不能无损 → 要改什么 → 存哪里 → 开始。

**为什么预判条在参数之前？**
如果这个文件根本做不了，用户不该先填三个参数再被拒绝。预判条是"决策"，参数是"执行"，顺序不能反。

**为什么波形要放在文件卡里？**
音频文件没有缩略图。一段静态波形能在 0.2 秒内传达"这是 3 分 42 秒的人声，不是 40 分钟的音乐"，比文字元信息更快。波形只用 `pine` 单色 + 透明度区分已播放段，不做彩色频谱。

### 7.3 队列

**为什么用卡片列表而不是表格？**
移动端表格的可读性差，且任务行信息量小（名称 + 进度 + 状态），卡片更松弛。

**为什么失败行不用红色整块？**
整块红会让整个页面显得"出事了"。只把标题和描边染成 `clay`，信息密度不变，但用户能一眼定位。

### 7.4 完成态

**为什么用空心圆 + 细对勾，而不是绿色实心大对勾？**
实心绿勾是"营销式庆祝"，每天用十次会腻。空心细线更像"确认"，符合工具气质。

**为什么三个指标而不是一句"转换成功"？**
用户真正关心的是"多快、多大、有没有损失"。把这三个数摆出来，等于替用户完成验收。

---

## 8. 动效

| 场景 | 参数 | 说明 |
|---|---|---|
| 页面进入 | `slideInHorizontally(8dp) + fadeIn`，220ms | 位移要小，大了像 PPT |
| 卡片按压 | `scale 0.98`，120ms，`FastOutSlowIn` | 只做按下去，不做弹回来 |
| 进度更新 | `tween(300, LinearEasing)` | 线性，避免"跳一下" |
| 完成态 | 圆环 `drawCircle` 400ms → 文字 `fadeIn` 200ms（延迟 200ms） | 只在这里允许一次"仪式感" |
| 列表增删 | `animateItemPlacement` 200ms | 不要用翻转/缩放 |
| 主题切换 | 无动画 | 动画会暴露配色断层 |

**禁止**：回弹、overshoot、连续抖动、超过 400ms 的过渡。

---

## 9. 文案规范

### 9.1 按钮

动词 + 对象，2–4 字：

| ✅ | ❌ |
|---|---|
| 开始转换 | 确定 |
| 打开文件 | 提交 |
| 重试 | 再次尝试一下 |
| 全部暂停 | 暂停所有任务 |

### 9.2 错误

公式：**发生了什么 + 为什么 + 怎么办**（三句都不可省，可压缩成两句）。

| 场景 | 文案 |
|---|---|
| 同名冲突 | 目标文件夹已有同名文件。换个名字，或先删除旧文件。 |
| 编码不支持 | 这个文件是 ALAC 无损格式，MP4 播放器普遍不支持。可以转成 AAC（会重新编码）。 |
| 加密 | 文件受 DRM 保护，无法转换。 |
| 空间不足 | 存储空间不足，还差约 240 MB。 |
| 损坏 | 文件索引缺失（moov 损坏），无法读取。 |

### 9.3 数字与单位

- 秒以内用 `2.1s`，超过 60 秒用 `1:24`。
- 大小统一 `MB`，小于 1MB 用 `KB`，保留一位小数。
- 百分比整数，不带小数。
- 中英文之间不手动加空格。

---

## 10. Compose 实现

### 10.1 主题

```kotlin
// core:ui/theme/Palette.kt
@Immutable
data class ToolboxPalette(
    val paper: Color, val surface: Color,
    val ink: Color, val ink2: Color, val ink3: Color,
    val pine: Color, val pineSoft: Color,
    val hairline: Color, val clay: Color,
    val claySoft: Color
)

val LightPalette = ToolboxPalette(
    paper = Color(0xFFF7F5EF), surface = Color(0xFFFFFFFF),
    ink = Color(0xFF17170F), ink2 = Color(0xFF5E5C52), ink3 = Color(0xFF99968A),
    pine = Color(0xFF1E4B3D), pineSoft = Color(0xFFE7EDE9),
    hairline = Color(0xFFE7E3D8),
    clay = Color(0xFFA9552F), claySoft = Color(0xFFF7E9E1)
)

val DarkPalette = ToolboxPalette(
    paper = Color(0xFF0E0F0C), surface = Color(0xFF181914),
    ink = Color(0xFFF2F1E9), ink2 = Color(0xFFA7A499), ink3 = Color(0xFF6E6C62),
    pine = Color(0xFF7FBFA4), pineSoft = Color(0xFF1B2A24),
    hairline = Color(0xFF2A2B25),
    clay = Color(0xFFD08462), claySoft = Color(0xFF2C1D17)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
```

```kotlin
// core:ui/theme/ToolboxTheme.kt
@Composable
fun ToolboxTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme(
                background = p.paper, surface = p.surface,
                primary = p.pine, onPrimary = Color(0xFFFBFAF5),
                onBackground = p.ink, onSurface = p.ink
            ) else lightColorScheme(
                background = p.paper, surface = p.surface,
                primary = p.pine, onPrimary = Color(0xFFFBFAF5),
                onBackground = p.ink, onSurface = p.ink
            ),
            typography = ToolboxTypography,
            content = content
        )
    }
}
```

### 10.2 字阶

```kotlin
object Type {
    val Title = TextStyle(fontSize = 20.sp, lineHeight = 25.sp,
        letterSpacing = (-0.3).sp, fontWeight = FontWeight.SemiBold)
    val CardTitle = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp,
        letterSpacing = (-0.1).sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 11.5.sp, lineHeight = 18.sp)
    val Caption = TextStyle(fontSize = 9.5.sp, lineHeight = 14.sp)
    val Eyebrow = TextStyle(fontSize = 9.5.sp, letterSpacing = 2.1.sp,
        fontWeight = FontWeight.SemiBold)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp,
        letterSpacing = (-0.2).sp, fontFeatureSettings = "tnum")
}
```

### 10.3 工具卡

```kotlin
@Composable
fun ToolCard(spec: ToolSpec, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(15.dp)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(120), label = "press")

    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (pressed) p.paper else p.surface)
            .border(1.dp, p.hairline, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(p.pineSoft),
            contentAlignment = Alignment.Center
        ) { Icon(spec.icon, null, tint = p.pine, modifier = Modifier.size(14.dp)) }

        Spacer(Modifier.height(9.dp))
        Text(spec.title, style = Type.CardTitle, color = p.ink, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(spec.summary, style = Type.Caption, color = p.ink3, maxLines = 1)
    }
}
```

按压态用 `interactionSource` 收集 `PressInteraction`，上面为了可读性简化了。

### 10.4 预判条

```kotlin
enum class PreflightTone { Lossless, Transcode, Blocked }

@Composable
fun PreflightBanner(tone: PreflightTone, title: String, reason: String, cost: String?) {
    val p = LocalPalette.current
    val (bg, fg) = when (tone) {
        PreflightTone.Lossless -> p.pineSoft to p.pine
        PreflightTone.Transcode -> p.claySoft to p.clay
        PreflightTone.Blocked -> p.claySoft to p.clay
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(bg)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.CardTitle, color = fg)
            Spacer(Modifier.height(2.dp))
            Text(reason, style = Type.Caption, color = fg.copy(alpha = 0.72f))
        }
        if (cost != null) Text(cost, style = Type.Mono, color = fg)
    }
}
```

`tone` 必须由 `EnginePlan` 推导，不能在 UI 里按 MIME 猜。

### 10.5 细进度

```kotlin
@Composable
fun ThinProgress(progress: Float, height: Dp = 2.dp) {
    val p = LocalPalette.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = LinearEasing),
        label = "progress"
    )
    Box(
        Modifier.fillMaxWidth().height(height).clip(CircleShape).background(p.hairline)
    ) {
        Box(
            Modifier.fillMaxWidth(animated).fillMaxHeight()
                .clip(CircleShape).background(p.pine)
        )
    }
}
```

### 10.6 主按钮

```kotlin
@Composable
fun PrimaryCTA(text: String, hint: String?, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) p.pine else p.hairline)
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(text, style = Type.CardTitle.copy(fontSize = 12.5.sp),
                color = if (enabled) Color(0xFFFBFAF5) else p.ink3)
        }
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(hint, style = Type.Mono, color = p.ink3)
        }
    }
}
```

---

## 11. 落地检查清单

发布前逐条过：

- [ ] 全屏没有 `shadow` / `elevation`
- [ ] 一屏内强调色面积 < 8%
- [ ] 圆角只有 8 / 11 / 15 / 999 四档
- [ ] 所有数字（时长、大小、百分比）用等宽
- [ ] 主按钮同屏唯一
- [ ] 每个失败状态都有"原因 + 下一步"
- [ ] 暗色下强调色已提亮，无"糊底"
- [ ] 系统字体放大到 1.3x 后，卡片文字不截断（用 `heightIn` 而非固定高）
- [ ] 空状态有一句话 + 一个动作
- [ ] 完成态不需要 Toast 也能看懂结果
- [ ] 从"分享"进入时，落到的工具列表已按 MIME 过滤

---

## 12. 与架构的对应关系

| 设计要求 | 架构支撑 |
|---|---|
| 首页网格由工具自动生成 | `ToolSpec` / `ToolRegistry` |
| 参数表单不写页面 | `ParamSpec` 渲染器 |
| 预判条语气三态 | `EnginePlan.warnings` + `ErrorCode` |
| 进度具体到"5.9 / 8.6 MB" | `Progress(bytesDone, bytesTotal)` |
| 失败原因可读 | `ErrorCode` → 文案映射表 |
| 完成态三指标 | `EngineResult(durationMs, bytes, lossless)` |

> UI 的每一处"高级感"，背后都必须有一个明确的数据来源。**没有数据支撑的视觉，只是装饰。**

---

### 附：一句话

> 简约不是"少画东西"，是**每个元素都有理由，且理由能被用户读懂**。
> 这套设计里最贵的不是配色，是"预判条"和"完成态三指标"——它们把不确定性提前消灭了。