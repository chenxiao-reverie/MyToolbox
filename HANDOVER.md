# 交接文档 · MyToolbox

> 给接手的人（或 AI）：读完这一份就能继续干，不需要翻聊天记录。
> 最后更新：2026-11-20｜HEAD `ab55a42`｜分支 `main`

---

## 0. 三十秒速览

**是什么**：Android 本地媒体工具箱。四个真实可用的工具——转 MP4（m4a→mp4，只改 ftyp 4 字节）、转 M4A（视频提取音轨）、无损裁剪（按区间删样本）、图片日记（照片+一句话→1200px 卡片，6 套字体）。

**在哪**：`/Users/junqiaolv/Documents/00-Projects/01-application/Android/MyTools`，已推到 https://github.com/chenxiao-reverie/MyToolbox（MIT + GitHub Actions）。

**状态**：可编译、可安装、已在模拟器逐项跑过。APK 26MB（22MB 是字体）。

**下一步**：PLAN.md 的 **P0-1 批量处理**（多选文件、整批入队）。当前一次只能选一个文件，这是最大的体验缺口。

**不能做的**：本机没有 ffmpeg；`github.com` 的 HTTPS 页面在本机超时（SSH 与 api.github.com 正常）。

---

## 1. 环境事实（照抄即可）

| 项 | 值 |
|---|---|
| Android SDK | `/opt/homebrew/share/android-commandlinetools`（platform 35、build-tools 35.0.0） |
| JDK | Temurin 17 |
| Gradle | 无系统安装，用仓库自带 `./gradlew`（8.9，AGP 8.7.3） |
| `local.properties` | 已 gitignore；本机需 `sdk.dir=/opt/homebrew/share/android-commandlinetools` |
| 模拟器 AVD | `toolbox`（API 35 arm64），启动：`emulator -avd toolbox -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect` |
| ffmpeg | **没有**。造音频用 `say` + `afconvert`；造带音轨视频用 `screenrecord` + Swift `AVMutableComposition` |
| 网络 | `github.com` HTTPS 不通（HTTP2 framing error / 超时）；SSH 与 `api.github.com` 正常 |
| `gh` CLI | 已安装但**未登录**，无法用 API 建仓库 |

---

## 2. 仓库与发布

```
origin  git@github.com:chenxiao-reverie/MyToolbox.git   （SSH，HTTPS 在本机不可用）
branch  main
HEAD    ab55a42  添加 MIT 许可与 GitHub Actions（单测 + APK 构建）
```

推送：

```bash
git push origin main
```

CI：`.github/workflows/android.yml`，push/PR 触发，跑单测 + 构建 + 上传 APK。首次运行已验证成功（run `44700974168`，3.5 分钟，artifact 25.1MB）。

查 CI 状态（本机 HTTPS 打不开网页，只能走 API）：

```bash
curl -s "https://api.github.com/repos/chenxiao-reverie/MyToolbox/actions/runs?per_page=1" \
  | python3 -c "import sys,json;r=json.load(sys.stdin)['workflow_runs'][0];print(r['status'],r['conclusion'],r['head_sha'][:7])"
```

---

## 3. 构建与验证（复制即用）

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug            # 需已连接设备/模拟器
./gradlew :core:media:testDebugUnitTest # 3 个单测
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 开模拟器

```bash
$ANDROID_HOME/emulator/emulator -avd toolbox -no-window -no-audio -no-boot-anim \
  -gpu swiftshader_indirect &
adb wait-for-device
adb shell getprop sys.boot_completed   # 等到 1
```

### 验证链

```bash
aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk   # 包名/版本
cmp -l samples/sample.m4a samples/sample.mp4                   # 无损路径应只有 4 字节差异
adb shell dumpsys activity services com.toolbox.app            # 前台服务
adb shell dumpsys notification --noredact | grep toolbox       # 通知
adb logcat -s "TaskService:*" "JobRepo:*" "Diary:*"            # 生命周期
adb exec-out screencap -p > /tmp/shot.png                      # 截图
```

无头看截图：用 `qwenwork_media_read` 读 PNG（本机可用）。

---

## 4. 代码地图

```
app/                     MainActivity · ToolboxApp(导航) · AppGraph(DI) · TaskService(FGS)
core/model/              ToolSpec/ParamSpec · ProbeResult · Job · ToolCatalog · Timecode
core/media/              IsoBmff(box 解析) · MediaProber · FtypPatcher · UriProber
core/storage/            OutputHandle · MediaStoreOutputStore（按 MIME 选集合）
core/task/               JobRepository（串行 + Room + 恢复） · db/JobDatabase
core/ui/                 Theme(Palette/Type) · Atoms · Cards · Chrome · Icons(自绘)
engine/api/              MediaEngine · EnginePlan · ExecutionContext · EngineResult
engine/platform/         FtypRemuxEngine · PlatformRemuxEngine · OutputVerifier
feature/home|tool|queue|diary/
tools/fonts/build_fonts.py  字体合并子集脚本
```

**依赖方向**：`feature → core → engine:api`。`engine:platform` 只被 `app/AppGraph.kt` 引用，feature 层永远不 import 具体引擎。

### 两条数据流

**转换**：`SAF Uri → UriProber → ProbeResult → engine.plan() → OutputHandle → engine.execute() → OutputVerifier → commit/abort → Room`

**日记**：`Uri → Bitmap → DiaryRenderer.render(预览 560px) → 导出 1200px → MediaStore Images/Pictures/图片日记`

---

## 5. 必须守住的设计约束

1. **UI 里不许出现 `if (toolId == ...)`。** 工具页的标题、参数、预判条全部来自 `ToolSpec` + `ProbeResult`。这是架构验收点，加新工具时用它自检。
2. **引擎实现只被 `AppGraph` 看见。** 想加引擎就往 `AppGraph` 的 `engines` 列表里加一个，UI 与仓库层不动。
3. **输出必须 `commit()` 或 `abort()`。** 半成品绝不能落到用户相册。
4. **能不解码就不解码。** L0 能力（换容器、裁剪、提取）优先走样本透传；真转码是 L2，另说。
5. **不留死控件。** 参数区渲染出来的东西必须真的生效；没实现的宁可删掉（`完成后：移入回收站` 就是这样被移除的）。
6. **设计 token**：暖纸底 `#F7F5EF`、松绿 `#1E4B3D`、1px 细线、无阴影/渐变、数字等宽。改 UI 前先看 `ui-design-spec.md`。

---

## 6. 已验证（有证据）

| 项 | 数据 |
|---|---|
| m4a→mp4 无损 | 51,403 B → 51,403 B，`cmp -l` 只有 4 字节不同（offset 8–11） |
| 兜底重封装 | 51,403 → 50,539 B，ftyp = mp42/isom/mp42，0.5s |
| 视频→m4a | 173,780 B 双轨 → 44,140 B 单轨，ftyp = M4A |
| 无损裁剪 | 5.00s → 2.018s（误差 18ms） |
| 中断恢复 | `am force-stop` 后重启，RUNNING→QUEUED 自动重跑完成 |
| 前台服务 | 通知 `actions=1`，`dumpsys` 可见“正在转换 / big.m4a · 42%” |
| 图片日记 | 1200×1600 PNG；切字体后日期笔画明显变化（马善政毛笔楷） |
| 单测 | `FtypPatcherTest` 3/3 |
| CI | run 44700974168 success，artifact 26,346,856 B |

---

## 7. 未验证 / 已知限制

- 通知栏「取消全部」只验证了 `actions=1` 与 Service 分支，**没在通知栏实际点过**。
- 碎片化 MP4（`moof`）会明确报错，未做拼接。
- 批量多选不存在（一次一个文件）。
- 分享入口只支持 `image/*`（音视频、多文件未做）。
- 日记预设删除靠长按，发现性差；字体缺字无系统回退（`CustomFallbackBuilder` 要 `FontFamily` 不是 `Typeface`，未接）。
- DB v1 升级仍走 destructive fallback（v2→v3 有迁移）。
- 重名提示只覆盖默认文件名，用户改名后不刷新（落盘仍会解析，不覆盖）。

---

## 8. 踩坑清单（照这个避）

1. `M4A ` 品牌**尾部有空格**，比较必须用四字符。
2. MediaMuxer 只写 `mp42`；输出 `.m4a` 要用 `Os.pwrite` 把 ftyp 主品牌改成 `M4A `。
3. MediaStore 集合必须按 MIME 选：图片写 `Images`，写 `Downloads` 会报 `Primary directory Pictures not allowed`。
4. API 35 上 `foregroundServiceType="mediaProcessing"` 抛 `InvalidForegroundServiceTypeException`，用 `dataSync`。
5. 无头模拟器 Photo Picker 索引库读不到 `adb push` 的图片（重启也无效）→ 用 SAF `OpenDocument`；分享入口 `ACTION_SEND image/*` 更接近真实路径。
6. Compose 预览区不限高会把控件全挤出屏幕（日记页已限 `heightIn(max=340.dp)`）。
7. 文字下划线别用 `fillMaxWidth()`，会把标签撑满整行挤出屏幕；用 `drawBehind` 按文字宽度画。
8. 暗色模式辅助文字对比度要 ≥4.5:1（`ink3` 暗色用 `#8A887C`）。
9. `adb push` 到 `/sdcard/` 可能 `Operation not permitted`：`adb root` 或用 `run-as com.toolbox.app cp` 到应用私有目录。
10. `screenrecord` 不支持 `--audio`，带音轨视频要用 Swift `AVMutableComposition` 合成。
11. Room 升级先写 Migration，别只留 `fallbackToDestructiveMigration`。
12. `Typeface.CustomFallbackBuilder` 接收的是 `FontFamily`，不是 `Typeface`——想给自定义字体补系统回退得另找路。

---

## 9. 待办（按优先级，含验收）

### P0-1 批量处理（建议立刻做）

- 用 `OpenMultipleDocuments()` 多选；N 个文件先建成 N 个 `QUEUED` 写库，再启动服务。
- 沿用 `Mutex` 串行；队列显示“3 / 10 已完成”；每条独立进度。
- 整批一次性解析输出名，避免 N 个任务抢同一个 `name (1)`。
- **验收**：选 10 个文件全部成功且无重名冲突；取消第 5 条不影响其余；中途 `force-stop` 重启后剩余继续、已完成不重跑。

### P0-2 分享入口补齐

音视频 `ACTION_SEND` / `SEND_MULTIPLE`，与批量共用入队逻辑。**验收**：相册多选 3 个 m4a 分享 → 队列出现 3 条。

### P1-1 裁剪可视化

波形 + 双滑块（`ParamSpec.Custom`），参数仍落回 `params["start"]/["end"]`，引擎不改。**验收**：输出时长与选段误差 ≤100ms。

### P1-3 日记细节

预设删除改成显式按钮；缺字回退方案；字数预算提示更清楚。

### P2 能力扩展

真转码（MediaCodec AAC/FLAC；MP3 需 FFmpeg，隔离到 `engine:ffmpeg` + full flavor）；fMP4 拼接。

---

## 10. 第一小时怎么开始

```bash
cd /Users/junqiaolv/Documents/00-Projects/01-application/Android/MyTools
./gradlew :app:assembleDebug :core:media:testDebugUnitTest   # 应成功
```

1. 读 `PLAN.md` 了解全貌与取舍。
2. 开模拟器，装 APK，走一遍三个工具（截图对比 `screenshots/`）。
3. 从 P0-1 开始：先看 `feature/home/HomeScreen.kt`、`app/ToolboxApp.kt` 的 tool 路由、`core/task/JobRepository.kt` 的 `start()`。

---

## 11. 文档索引

| 文件 | 用途 |
|---|---|
| `PLAN.md` | 计划书：定位、状态、路线、技术债、决策记录（**最该先读**） |
| `README.md` | 对外门面：徽章、截图、工具表、快速开始 |
| `android-toolbox-architecture.md` | 架构方案原始设计（模块、接口、分层） |
| `ui-design-spec.md` | 设计规范（色彩、字阶、组件、动效） |
| `m4a-to-mp4-remux-research.md` | 技术调研（容器原理、开源选型、决策矩阵） |
| `HANDOVER.md` | 本文件 |

---

## 12. 交接提醒

- **不要为了"支持更多格式"随手塞 FFmpeg**。L0 能力用平台 API 就够了，这是产品差异点；真转码再引。
- **改完跑一次 `cmp -l`**。无损是这套东西的立身之本，别让它悄悄退化。
- **提交信息保持中文短句**，和现有三条一致。
- **字体子集脚本**在 `tools/fonts/build_fonts.py`，别再从 `/tmp` 找。
- 本机 HTTPS 打不开 GitHub 页面：要确认远端内容用 `git fetch` + `git show origin/main:文件`，不要以为是仓库坏了。