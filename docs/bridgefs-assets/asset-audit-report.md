# BridgeFS → A-BridgeFS 资产迁移报告 V0.1

> 审计日期：2026-09-28  
> 旧仓库：[`owla19s-ux/BridgeFS`](https://github.com/owla19s-ux/BridgeFS)，审计基准 `main` @ `4a1275064913faeba3354745be69523ccf5bbaf9`  
> 新仓库：[`owla19s-ux/A-BridgeFS`](https://github.com/owla19s-ux/A-BridgeFS)，审计基准 `main` @ `7b0bd01c92a04cf99f2f88ddb2bf6dae5497d2ea`  
> 范围：只读源码和仓库文档审计；本提交只新增本报告，不修改功能代码。未进行 Android 实机验证或构建验证。

## 1. 结论摘要

旧 BridgeFS 是一个 Android 11+ 原生 Kotlin 文件操作原型，当前代码集中在 `BridgeFS-0.1/`，采用单一应用模块和单一 Kotlin package。它已经具备可识别的文本指令协议、根目录内文件操作、Android 前台 Service、Overlay 悬浮入口和操作结果展示，证明了“用户粘贴指令 → 本地解析 → 用户点击执行 → 手机本地操作”的基本可行性。

最值得保留的是**命令语义与根目录约束下的本地文件操作经验**；不宜把现有 Android UI / Service 与解析和执行逻辑整包搬入新项目。当前代码没有单独的 Receipt 模型或回执持久化；回执是显示在 Overlay 的文本，并可由用户复制。更重要的是，现有执行入口没有逐条操作预览/确认门：用户点击“执行”后，解析出的所有命令会立即依次执行。BridgeFS+ 应在 Executor 之前把验证和明确确认作为必经步骤。

建议新项目先冻结统一 Command 与 Receipt 契约，再按边界重构迁移执行能力。AI / API 适配只负责把模型输出转换为 Command 候选；任何外部模型输出不得绕过本地校验和用户确认直接到达设备执行层。

## 2. 审计范围与方法

本报告以旧仓库 `main` 的上述 commit 为固定快照，读取了完整递归文件树以及以下关键文件：

- `BridgeFS-0.1/README.md`
- `BridgeFS-0.1/app/src/main/AndroidManifest.xml`
- `BridgeFS-0.1/app/src/main/java/com/owla19s/bridgefs/CommandParser.kt`
- `BridgeFS-0.1/app/src/main/java/com/owla19s/bridgefs/CommandExecutor.kt`
- `BridgeFS-0.1/app/src/main/java/com/owla19s/bridgefs/PathSecurity.kt`
- `BridgeFS-0.1/app/src/main/java/com/owla19s/bridgefs/TreeUriResolver.kt`
- `BridgeFS-0.1/app/src/main/java/com/owla19s/bridgefs/FileBridgeService.kt`
- `BridgeFS-0.1/app/src/main/java/com/owla19s/bridgefs/MainActivity.kt`
- `BridgeFS-0.1/app/src/main/java/com/owla19s/bridgefs/BridgeFSApp.kt`
- `BridgeFS-0.1/app/src/main/java/com/owla19s/bridgefs/ContextCompatCompat.kt`
- `BridgeFS-0.1/app/build.gradle`
- `BRIDGEFS_CODE_REVIEW_2026-09-25.md`

也核对了 A-BridgeFS 当前 `docs/bridgefs-assets/` 下的既有文档。代码审计描述的是静态源码事实；代码巡检记录中的实机待测项仍未验证，不视为已发生缺陷或已通过验证。

## 3. 旧仓库结构

旧仓库根目录包含 GitHub Actions 构建工作流、2026-09-25 代码巡检记录，以及 Android 工程 `BridgeFS-0.1/`。该工程的主要源码树如下：

```text
BridgeFS-0.1/
├── README.md
├── build.gradle
├── settings.gradle
├── gradle.properties
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/owla19s/bridgefs/
        │   ├── BridgeFSApp.kt
        │   ├── CommandExecutor.kt
        │   ├── CommandParser.kt
        │   ├── ContextCompatCompat.kt
        │   ├── FileBridgeService.kt
        │   ├── MainActivity.kt
        │   ├── PathSecurity.kt
        │   └── TreeUriResolver.kt
        └── res/values/
            ├── strings.xml
            └── styles.xml
```

项目设置为 compileSdk 35、minSdk 30、targetSdk 35、Kotlin/JVM 17；依赖 AndroidX Core KTX 与 AppCompat。仓库未见独立测试源码目录、独立 domain/core 模块或独立回执实现。这个目录结构是当前快照中可见的工程结构，不代表所有历史版本的完整情况。

## 4. 核心代码审计

### 4.1 Command / CommandParser

`CommandParser.kt` 定义了 sealed `Command` 类型，包含：

- `ListTree`
- `Read(path)`
- `Write(path, content)`
- `Edit(path, old, new)`
- `Search(glob)`
- `Grep(keyword)`
- `Path(path)`
- `CopyPath(path)`
- `Mkdir(path)`

解析入口是 `CommandParser.parse(input: String): List<Command>`。简单命令以逐行方括号格式表达，如 `[list]`、`[read: 相对路径]`、`[search: *.txt]`、`[grep: 关键词]`、`[path: 路径]`、`[copy-path: 路径]`、`[mkdir: 路径]`。写入命令采用 `[write: 路径]\n内容\n[/write]`，编辑命令采用 `[edit: 路径]\n旧内容====新内容\n[/edit]`；解析结果按输入中命令的起始位置排序。

这是稳定的原型资产，但仍是面向旧格式的正则解析器，不包含版本字段、命令 ID、来源、风险级别或解析错误对象。无法匹配的内容会被忽略；`[edit: ...]` 缺少 `====` 时不会产生对应命令。调用方只在最终命令列表为空时显示“未发现可执行指令”，不能逐条指出无效/歧义输入。迁移时应保留已验证的命令语义与兼容性样例，把输入解析和规范化迁入平台无关的 `core/command`，并为无效输入返回显式错误，避免静默丢弃。

### 4.2 Executor / 文件操作 / 路径安全

`CommandExecutor(root: File, context: Context)` 是执行入口，`execute(Command): String` 直接分派到对应操作。实现覆盖列举、UTF-8 读取、新建写入、查找并替换一次、按文件名 glob 搜索、内容 grep、绝对路径查询、复制路径到剪贴板以及创建目录。

多数路径型操作先调用 `PathSecurity.safe(root, relative)`：清理分隔符，拒绝空路径、绝对路径和含 `..` 片段的路径，并通过 canonical path 检查目标仍在根目录内。这是应保留的安全意图。执行策略仍需进一步加固：`ListTree` 对整个目录递归遍历且没有结果/耗时上限；`Search` 也递归遍历；`Mkdir` 对已存在路径返回绝对路径；写文件通过 `mkdirs()` 自动创建父目录；编辑直接替换首个匹配文本；所有结果均为面向人的字符串。即使有路径边界检查，执行器也没有用户确认、命令权限策略或明确的资源/输入限制。

`Grep` 已实现二进制文件跳过、500 文件 / 5 秒 / 200 条结果上限并把读取异常写入返回文本；不过扫描仍在调用线程同步运行。代码巡检记录也将大目录卡顿列为实机观察项，而非已验证故障。

建议：把文件 IO 封装为可注入的本地执行端口，纯命令/路径策略移入 `core/executor` 或 `core/command`，Android Context 操作（如剪贴板）留在平台适配层。迁移时优先完善所有操作的范围限制、错误结构和撤销/覆盖策略；尤其保留“写入不覆盖已有文件”的现有默认行为，除非新协议明确设计安全的覆盖确认。

### 4.3 Receipt / 结果回传

源码中没有独立的 `Receipt` 数据类、JSON 序列化协议、任务 ID 或历史存储。执行器返回字符串；`FileBridgeService` 将所有命令结果用空行拼接后显示在面板内，并提供复制到剪贴板。运行日志由 Service 写入 `/sdcard/BridgeFS/logs/run.log`（失败时退回应用外部文件目录），最多保留最近约 500 行；日志包含命令输入截断文本和操作状态，不能等同于结构化回执记录。崩溃日志由 `BridgeFSApp` 写入日志目录或应用外部文件目录。

迁移判定：旧实现只证明了“能返回可读结果”，并未证明结构化 Receipt 或持久化能力。第一版应**参考重写**统一回执数据结构，最少包含 `receipt_id` 或 `command_id`、`status`、`operation`、`target`、`message`、`created_at`；对拒绝、取消、校验失败、部分成功和执行失败定义明确状态。API / 本地聊天适配层不得自行伪造执行成功状态。

### 4.4 权限、目录选择与 Android 组件

`AndroidManifest.xml` 声明 `SYSTEM_ALERT_WINDOW`、`MANAGE_EXTERNAL_STORAGE`、`FOREGROUND_SERVICE` 和 `FOREGROUND_SERVICE_SPECIAL_USE`；启动 Activity 为 `MainActivity`；`FileBridgeService` 未导出并以 `specialUse` 前台服务运行。应用 targetSdk 35，权限授予及 Android / 厂商后台策略需实机验证。

`MainActivity` 提供 Overlay 与“所有文件访问”状态及跳转设置、自动显示开关、前台 Service 手动启动、根目录列表和项目目录选择。目录选择器是应用内直接浏览 `/storage/emulated/0` 的文件系统 UI；激活的根目录路径保存在 SharedPreferences。启动 Service 前会检查悬浮窗权限、所有文件访问权限和已选根目录。自动启动分支主要检查悬浮窗授权及开关，不能据此推断所有权限和厂商后台行为均可靠。

`TreeUriResolver` 将 SAF tree URI 的 document ID 推导为 primary 或 `/storage/<volume>` 文件路径；在本快照所见源码中未发现其被主流程调用。README 也说明无法映射时拒绝保存；这类 URI 到裸路径的映射不可作为新架构的通用存储授权方式，需重新评估 Android Storage Access Framework 与持久 URI 授权。

### 4.5 Service / Overlay / 旧 UI

`FileBridgeService` 同时负责前台常驻、悬浮球和面板 UI、粘贴输入、解析命令、同步执行、结果展示/复制、目录浏览及运行日志。Overlay 包含“粘贴 AI 指令”、粘贴、执行、回执复制、日志和文件浏览；独立 Overlay Dialog 用于多行输入。虽然代码已有明显的 UI 与执行耦合，但 `CommandParser` / `CommandExecutor` 可被识别为可拆出的独立类型。

迁移判定：Service 的生命周期和 Android 前台通知机制作为平台经验**参考重写**；将执行调度从 Service 中移出，Service 只承载必要生命周期 / 平台连接。悬浮球、面板布局、浏览器和输入 Dialog 属旧 UI 方案，应**废弃**为产品实现，仅把“浮层可快速提交本地指令”的交互经验作为需求参考。代码巡检所列 Dialog、输入法、窗口生命周期问题尚待设备验证，本审计不判定为已发生问题。

### 4.6 CI 与可验证性

旧仓库根目录有 `.github/workflows/build.yml`，README 给出云端构建方式；但本次任务未运行工作流、编译或设备验证。仓库快照未见自动化单元测试源码。解析兼容样例、路径逃逸、拒绝/取消、部分成功和回执序列化应作为新项目迁移后的验收重点。

## 5. 迁移决策表

优先级定义：P0 = 第一版必须；P1 = 第一版可后置；P2 = 后续版本或不进入第一版。策略仅使用：直接迁移、重构迁移、参考重写、废弃、暂缓。

| 模块 | 旧位置 | 已知作用 / 依赖 | 建议 A-BridgeFS 位置 | 策略 | 优先级 | 风险与备注 |
| --- | --- | --- | --- | --- | --- | --- |
| Command 模型与解析 | `app/src/main/java/.../CommandParser.kt` | 文本协议解析；无 Android 依赖 | `core/command` | 重构迁移 | P0 | 保留旧指令兼容；为无效输入给出显式诊断，冻结协议前补充样例 |
| Executor 文件操作 | `.../CommandExecutor.kt` | 文件 IO、路径安全、Context/剪贴板 | `core/executor` + Android adapter | 重构迁移 | P0 | 与平台解耦；逐命令范围/资源限制；禁止绕过确认 |
| 路径边界策略 | `.../PathSecurity.kt` | canonical path 根目录约束 | `core/security` 或 `core/executor` | 重构迁移 | P0 | 保留边界检查并覆盖符号链接、根路径和异常场景 |
| Receipt | Service 展示字符串；日志散落在 Service / App | 拼接结果、复制、文本日志；没有独立模型或历史 | `core/receipt` | 参考重写 | P0 | 先定状态与字段；不得把展示文本当成稳定协议 |
| 用户确认与校验 | 当前只有“执行”按钮，执行前无逐条确认层 | Service 直接解析后执行所有匹配命令 | `core/validation` + UI confirmation | 参考重写 | P0 | 安全边界；展示操作、目标和影响，确认后才调用 Executor |
| Android 文件授权 / 根目录 | Manifest、`MainActivity.kt`、`TreeUriResolver.kt` | 全文件访问权限、手工路径 picker、SharedPreferences | `platform/android/storage` | 重构迁移 | P0 | 权限最小化；验证 SAF URI，不依赖未授权路径猜测 |
| 前台执行 Service | Manifest、`FileBridgeService.kt` | 特殊用途前台服务、生命周期、执行和 UI 混杂 | `platform/android/service` | 参考重写 | P1 | 分离生命周期与业务执行；服务类型/后台规则需按目标 Android 实测 |
| Overlay 悬浮球 / 面板 | `FileBridgeService.kt` | 快速输入、复制结果、目录浏览 | 新 UI / `platform/android/overlay`（若仍需要） | 废弃 | P2 | 不照搬旧布局；新交互另行设计，安全确认必须保留 |
| 运行 / 崩溃日志 | `FileBridgeService.kt`、`BridgeFSApp.kt` | 公共存储或应用目录的文本日志 | `platform/android/diagnostics` | 参考重写 | P1 | 评估敏感命令文本的日志脱敏、轮转和用户控制 |
| Tree URI 映射 | `.../TreeUriResolver.kt` | 从 URI document ID 构造文件路径；主流程未见调用 | `platform/android/storage` | 暂缓 | P1 | 先确认是否仍有需求及授权模型；禁止将猜测映射直接当授权 |
| Activity 权限与目录界面 | `MainActivity.kt` | 设置入口、目录管理、启停 Service | 新 Android UI | 参考重写 | P1 | 旧 UI 非核心资产；保留权限流程经验，重新设计 |
| 构建配置 / CI | `app/build.gradle`、根 Gradle、`.github/workflows/build.yml` | Android Kotlin 构建与 APK 产物 | 新项目构建配置 | 参考重写 | P1 | 对齐新模块结构、SDK 目标和签名策略 |
| 旧资源 / 主题 | `app/src/main/res/values/*` | 旧 App 标签和主题 | 无 | 废弃 | P2 | 随旧 UI 不迁移 |
| 旧检查记录 | `BRIDGEFS_CODE_REVIEW_2026-09-25.md` | 实机验证清单和静态巡检结果 | `docs/bridgefs-assets` 参考 | 直接迁移 | P1 | 仅作为待验证观察项，不视为测试通过或确定缺陷 |

## 6. BridgeFS+ 第一版边界建议

### 必须具备

1. 一个版本化的统一 Command 协议；旧格式由 Adapter 兼容，输入源统一生成 Command 候选。
2. Validator 对类型、路径、权限、参数和资源限制做本地校验。
3. 用户确认是 Executor 的必经门：先展示将执行的具体操作、目标和影响；用户确认后才执行。拒绝/取消不得触发 IO。
4. Android 本地执行只开放首版明确支持的命令，路径限制在用户授权的工作根目录内。
5. 每个执行和拒绝都生成结构化 Receipt；API、本地聊天共享同一回执语义。
6. 输入源 Adapter（API / 本地聊天）与模型供应商解耦，均无权直接调用设备能力。
7. 保留审计日志的必要能力，同时定义敏感内容最小化与脱敏规则。

### 第一版不做

- 多 Agent、多角色和多模型自动协作
- 自动调度、长期记忆、复杂任务系统
- 云端同步
- AI 输出直连 Executor 或后台无确认执行
- 为旧 Overlay UI 做功能堆叠

## 7. 建议后续施工顺序

1. 冻结旧 BridgeFS：只维护必要缺陷与安全修复，不在旧项目继续叠加 AI 功能。
2. 定稿 Command、Validator / Confirmation 边界和 Receipt 字段 / 状态。
3. 在 A-BridgeFS 建立平台无关的 command、validation、receipt 契约。
4. 将旧命令语义和文件操作按接口重构迁移；增加解析兼容、路径边界、取消确认等验收覆盖。
5. 接入 Android 授权存储与执行适配器；验证目标 Android 版本和设备行为。
6. 最后接 API / 本地聊天输入 Adapter，跑通“AI 提议 → 解析校验 → 用户确认 → 本地执行 → 结构化回执”。

## 8. 需要在施工前解决的事项

- Command 是否允许 `Mkdir`、`Edit` 等变更类操作进入第一版；每种命令的风险级别与确认文案是什么？
- 根目录授权选用 SAF URI 持久授权、应用沙盒，还是确有必要申请 `MANAGE_EXTERNAL_STORAGE`？该决策需结合目标分发方式和 Android 实机验证。
- 回执是否需要本地持久化、保留期限和脱敏策略？首版建议至少支持当前会话可查询，不把任意历史长期保存作为前置要求。
- Overlay 是否为首版强需求？若不是，可先走 Activity 内确认与执行路径，降低前台 Service / Overlay 生命周期耦合。

## 9. 审计限制

- 结论以指定 commit 的静态源码为准；后续 main 分支变化不会自动反映在本报告中。
- 未运行构建、自动化测试或 Android 实机验证；静态代码可见不等于行为已在设备验证。
- 此次仅审计 `BridgeFS-0.1/` 当前工程及其根目录巡检记录；仓库完整 Git 历史与其他外部部署资产不在范围内。
- A-BridgeFS 在审计基准时仅含 `docs/bridgefs-assets/` 文档，没有功能代码可与旧实现逐模块比对。
