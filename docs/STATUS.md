# A-BridgeFS 当前状态

更新时间：2026-10-02

## 项目定位

A-BridgeFS 当前作为 **AI 协作移动端工作台 + 本地执行桥** 进行真实验证。

核心方向：

**双 AI 协作 + 工作区 + GitHub + BridgeFS 本地执行**

核心模型：

**两个 AI 共享读取能力；施工权限独立管理，同一 Repository / Branch 同时最多一个 AI 持有修改权。**

Decision AI / Worker AI 只作为任务阶段角色标签，不是固定身份。

## 当前状态

| 部分 | 状态 | 说明 |
| --- | --- | --- |
| Android 基础工程 | 已实现 | 当前工程可继续施工 |
| API / 对话 | 已实现 / 待统一验证 | API 可配置、可聊天；Chat → API 持久绑定已实现 |
| 本地指令 / BridgeFS | 已实现 | 保留真实本地执行能力 |
| GitHub 连接基础 | 已实现 / 待真机验证 | PAT、Keystore、账号、Repository、Branch 基础能力已落地 |
| 工作区 | 开发中 | Workspace → Conversations 数据结构与基础 UI 已落地；本地修改权限已进入 PermissionPolicy / FileBridgeService；仍需真机验证与清理兼容 facade |
| 双 AI 协作协议 | 已确认设计 / 尚未完整实现 | 共享读取、施工权独占且可转移 |
| 协作消息 | 开发中 | 聊天消息已开始保存并展示实际使用 API 的名称与头像；仍待构建/真机验证，协作链仍偏旧单轮实现 |
| 施工锁 | 开发中 | 已实现 Workspace + Repository + Branch 独占锁，并已接入显式协作任务申请；AI 自主施工链仍未完全接通 |
| 协作回执 | 已设计未完全实现 | Receipt 与消息时间线仍需统一 |
| 协作日志 | 已设计未完全实现 | 已有分类日志目录，产品级协作日志仍需补齐 |
| GitHub 实际写入链 | 开发中 | `updateFile()` 已可在 ConstructionLock 下真实写入并保存 Commit SHA；Worker 输出尚未自动映射到该入口 |
| Verify 实链 | 开发中 | Commit-scoped Actions 查询与 PASS/FAIL 状态已接入；尚待新的 Actions Run / APK / 真机实证及更细 Job/Step 展示 |
| 复杂调度 / 多 AI | 未开始 | 当前明确不做 |

## 当前设计基线

### AI

工作区包含两个协作 AI。

两者默认拥有工作区允许范围内的读取能力，可以同时读取同一项目，也可以同时分析、沟通和检查结果。

不因 AI 身份天然获得修改权。

### 施工权

施工权属于具体：

Workspace + Repository + Branch

同一 Repository / Branch：

- 同时最多一个 AI 持有施工权
- 施工权可以转移
- 施工权不等于 API Profile
- 施工权不等于 GitHub Token 本身权限
- GitHub Token 真实权限仍需独立检查

典型生命周期：

申请 → 授权 → 施工 → Commit → Verify → 释放 / 转移

## 2026-10-02 追加：工作区 / GitHub 第一批施工

### 已落地

- `BridgeProject` 已增加工作区级 `localFileModifyEnabled`，并持久化保存。
- V021 工作区页已出现“本地文件”权限卡；该开关目前表达**工作区意图**，尚未接入 `PermissionPolicy` 的最终执行判定。
- GitHub 低层 `GitHubApiClient.updateFile()` 已支持 Contents API 的文件更新。
- `GitHubWorkspaceService.updateFile()` 已作为工作区级写入边界；实际写入要求 AI Member，并检查 Workspace + Repository + Branch ConstructionLock 后才调用低层客户端。

### 尚未完成

- 本地文件修改权限已接入 `PermissionPolicy`，并由 `FileBridgeService` 在真实执行入口再次判定；尚待 APK 真机验证。
- Conversation 级本地文件权限覆盖已进入 `PermissionPolicy` 数据路径；尚待 UI / 真机验证。
- GitHub 协作任务已经能够显式申请施工权、进入 `CONSTRUCTING` 并通过 `updateFile()` 真实写入；Commit SHA 已持久化。
- Commit-scoped Actions / Verify 已接通代码链，但尚未获得新的 Actions Run / APK / 真机实证。
- Workspace → Conversation 数据结构已落地，V021 对话切换/新建已开始使用工作区内 Conversation；仍保留兼容 facade，后续继续清理旧调用。
- API 页面仍需从固定 Decision / Worker 配置迁移为 AI 成员 + API Profile 资源模型。

### 本批次状态

| 项目 | 状态 |
| --- | --- |
| 工作区本地修改权限数据 | 已实现 |
| 工作区本地修改权限 UI | 已实现 |
| 本地修改权限实际执行拦截 | 已实现，待验证 |
| GitHub 低层文件更新 | 已实现 |
| GitHub Workspace 写入边界 | 已实现 |
| GitHub → Commit → Verify | 开发中 |

## 当前代码事实

- V021Activity 是当前 Launcher。
- MainActivity 仍存在，且包含尚未迁移的历史功能，暂不能直接删除。
- BridgeFS 本地执行链真实存在。
- GitHub Workspace 边界已有基础实现。
- V021 已移除 API Profile 的“允许修改”UI/模型字段；历史持久化中的 `write` 字段仅作兼容读取，不再作为权限来源。
- Receipt 仍存在 executions / pending_receipt / messages / input 四处分散状态。
- V021 启动时尚未恢复 pending receipt。
- Workspace 与 Conversation 的基础数据结构和当前 UI 已分离，但兼容 facade 仍存在，尚未完成彻底迁移。
- 当前聊天页已有 Workspace / Conversation 操作入口，但尚未完成 APK 真机验证。
- AI 消息已按消息保存实际使用的 API ID、名称与头像；Issue #30 已进入实现后待构建/真机验证阶段。
- 当前协作实现仍偏单轮协议链，尚未形成双 AI 连续协作循环。
- GitHub 实际写入尚未形成完整 GitHub → Verify 链。

此前关于“协作 API Key 明文写入 SharedPreferences”的判断已撤回；当前代码已有 SecretStore / Android Keystore 迁移逻辑。

## 当前施工优先级

1. 完成 Workspace / Conversation UI 真机验证，并继续清理兼容 facade。
2. 修复聊天消息 API 身份显示：消息保存实际使用的 API 名称与头像（Issue #30）。
3. 把工作区 / Conversation 权限真正接入 PermissionPolicy。
4. 把 API Profile 与 AI 成员、实际权限边界分离清楚。
5. 统一 Receipt 状态链。
6. 完成 Repository / Branch 施工锁与 AI 施工流程接入。
7. 把 Worker 文件修改结果安全映射到真实 GitHub 写入 → Commit → Verify。
8. 完成新的 Actions / APK / 真机验证。
9. 实现双 AI 连续协作循环。
10. 清理旧 MainActivity / 旧日志路径等历史实现。

## 2026-10-02 追加确认：API 页面与对话执行链

### API 页面
当前正式入口已经迁移到 V021「工作区」：API Profile 作为连接资源独立管理，AI A / AI B 在工作区中选择对应 Profile。

当前实现：
- 两个 `BridgeAiMember` 持久化在 Workspace 内
- AI Member 只保存 Profile ID，不复制 API Key / 连接参数
- API Profile 由 `ApiProfileStore` 统一管理
- 协作运行时按 Workspace 当前两个 AI Member 的 Profile ID 取 API
- 不再使用全局 `collaboration_first_api_id` / `collaboration_second_api_id`
- Decision / Worker 仅保留为当前协议的任务阶段路由，不作为固定身份或固定配置槽位

仍需处理：专用 `ApiSettingsActivity` 仍保留旧的 legacy API 设置兼容页面；它不再作为 V021 协作配置入口，后续可继续清理。

### 对话 → BridgeFS
当前对话已经存在真实的自动执行链：

`用户消息 → API 对话 → AI 回复 → [bridgefs] 区块识别 → CommandParser → PermissionPolicy → BridgeFS 执行 → Receipt`

因此「AI 对话会触发 A-BridgeFS / BridgeFS 执行回执」属于**当前已存在的实现能力**，不是待设计功能。

当前缺口不是是否执行，而是执行链与新双 AI 协作模型尚未统一：
- Receipt 已进入当前 Conversation 的消息时间线；`pending_receipt` 仍未在 V021 启动时恢复。
- API Profile 的 write 开关已不再作为实际权限来源；当前有效本地修改权限来自 Workspace / Conversation + `PermissionPolicy`。协作 AI 的 Profile 绑定现在以 Workspace 内 `BridgeAiMember.apiProfileId` 为唯一来源，不再使用全局协作 Profile ID。
- 当前代码批次尚未通过 GitHub Actions 构建；最近一次 PR 构建因 V021 新建 Conversation lambda 的非法 `return` 失败，已修复，等待下一轮构建。
- 双 AI 协作尚未接入同一套施工权 / Receipt / Verify 链

## 2026-10-02 CI 验证检查点

`bd27214` 的 V021 新建 Conversation lambda 编译修复已提交；此前 Actions #72 仍针对旧 SHA `e023469b`，不能作为该修复的验证结果。当前可用 GitHub 连接器能够检查/重跑既有 Run，但没有可用的 workflow_dispatch 写入口；本次通过 Branch 文档提交产生的新 Commit 未自动产生新的 Actions Run。因此构建通过前不将本批次标记为已验证。

### #20 AI Member / API Profile 解耦
- 🟡 **开发中**：已抽出 `ApiProfileStore`，API Profile 不再承担协作角色身份。
- 🟡 **开发中**：Workspace 已持久化两个 `BridgeAiMember`，AI A / AI B 可绑定不同 API Profile。
- 🟡 **开发中**：协作运行时按 Workspace 内参与者 Profile ID 取 API，不再读取固定 Decision / Worker API 配置。
- 🟡 **开发中**：V021 工作区 UI 已提供两个协作 AI 的选择入口；旧 API 设置页不再承担协作角色配置，但 legacy API 设置页仍保留兼容代码。
- ⚠️ **未验证**：新的 Actions Run / APK / 实机链路尚未验证。


### 2026-10-02 本轮检查补充

- 已修复协作 AI 选择的 Workspace 隔离问题：不再从全局 SharedPreferences 读取协作 Profile ID，Workspace 的 `aiMembers` 成为唯一来源。
- 移除 API 时同步解除所有 Workspace AI Member 的 Profile 绑定；若协作双 AI 配置因此不完整，会自动关闭协作模式，避免继续调用已删除 Profile。
- 协作运行提示词已改为“规划参与者 / 执行参与者”任务阶段描述，不再把 Decision AI / Worker 写成两个永久身份。
- ⚠️ 未验证：以上代码尚未经过新的 Actions Run、APK 安装及真机验证。

### 2026-10-02 协作运行时第二轮检查

- 修复协作 Transport 原先使用全局消息队列的问题：现在按 `Workspace + Conversation` 隔离协议消息与已处理状态，避免不同工作区/聊天互相消费任务。
- `CollaborationCoordinator` 现在接收当前 Workspace / Conversation ID，并使用对应隔离的 Transport。
- 协作结果消息现在保存实际产生该协议消息的 API Profile ID、名称与头像，与普通 AI 消息的身份持久化规则统一。
- ⚠️ 当前仍未通过新的 Actions Run / APK / 真机验证；以上为代码层检查与修复结果。


### 2026-10-02 协作施工链第三轮检查

- 新增 CollaborationTaskStore：持久化 Workspace + Conversation + Task 的施工状态，支持 App 退出后的任务状态恢复基础。
- CollaborationCoordinator.requestConstruction(taskId, aiMemberId)：只有显式申请才获取 ConstructionLock，普通分析协作不会自动抢占 Repository / Branch 施工权。
- CollaborationCoordinator.updateFile(...)：要求任务处于 CONSTRUCTING、当前 AI 持有任务施工权，然后进入 GitHubWorkspaceService.updateFile()。
- GitHub Contents API 返回的 Commit SHA 与变更路径写回任务，并将状态推进为 WAITING_VERIFY。
- 修复 GitHubWorkspaceService 通过反射读取 GitHubApiClient.context 的做法，改为直接使用自身持有的 Context。
- ⚠️ 未验证：以上代码尚未经过新的 Actions Run、APK 安装及真机验证。


### 2026-10-02 Verify 实链第四轮

- GitHubApiClient 新增按 Commit SHA 查询 Actions Runs。
- GitHubWorkspaceService 暴露 Commit-scoped workflow 查询。
- 新增 CollaborationVerifyService：只认与任务 lastCommitSha 完全一致的 Actions Run。
- Run 不存在或仍在运行：任务保持 WAITING_VERIFY。
- Run completed + success：任务 COMPLETE，写入 VERIFY_PASS Receipt，并释放 ConstructionLock。
- Run completed + 非 success：任务 FAILED，并写入 VERIFY_FAIL Receipt；施工锁不自动释放，便于继续修复。
- Commit 产生后禁止普通 releaseConstruction 提前释放施工锁，避免 Verify 等待期间其他 AI 修改同一 Repository / Branch。
- 当前分支存在 PR #16，但当前代码尚未产生新的 Actions Run；因此 Verify 代码链仍未获得真实 CI 验证。


### 2026-10-02 协作 UI 可达性检查第五轮

- V021 工作区页此前只有“选择两个协作 AI / 启用 AI 协作”，没有任务状态、施工锁、Verify 的用户入口。
- 本轮已新增“协作任务”卡片：按当前 Workspace + Conversation 展示最新 Task、状态、Commit、施工者。
- WAITING_CONSTRUCTION 时提供“AI A 申请施工锁”入口，实际调用 CollaborationCoordinator.requestConstruction()。
- WAITING_VERIFY 时提供“检查当前 Commit”入口，实际调用 CollaborationCoordinator.verifyTask()。
- ⚠️ 当前仍有一个关键断点：`CollaborationCoordinator.updateFile()` 虽已具备真实 GitHub Contents 写入能力，但 `runObjective()` 当前 Worker 提示词仍明确禁止直接修改 GitHub，协议运行链也没有把 Worker 的文件修改结果映射到 `updateFile()`；因此“AI 自主施工 → Commit”目前仍不可达。该问题现列为下一施工节点。
- 该断点属于 GitHub 实际写入链继续施工范围，不将本轮 UI 补口误标为完整施工链或已验证。


### 2026-10-02 CI 触发检查

- PR #16 的 Android Verify 工作流使用 `pull_request` → `main` 触发。
- 旧 Actions Run 不作为当前施工分支构建证据；后续以最新 Commit SHA 对应的 Run 为准。


## 2026-10-02 追加：Workspace / 独立对话 UI 分离

本轮根据真机 UI 检查确认并修正：

- 原问题：V021「对话」页直接使用 Workspace.activeConversation()，因此普通独立对话实际上仍属于 Workspace。
- 原问题：Workspace 的 `collaboration_mode_enabled` 会让普通对话发送路径直接进入双 AI 协作。
- 修正：新增 `BridgeConversationStore`，独立对话单独持久化。
- 修正：底部「对话」现在只管理独立对话。
- 修正：Workspace 新增「进入协作对话」入口。
- 修正：Workspace 协作对话独立使用 Workspace.conversations，可新建 / 切换。
- 修正：独立对话继续支持 API 对话、BridgeFS 本地执行、回执及独立本地修改权限。
- 修正：BridgeFS 回执增加 standaloneConversationId 路由，不再写入 Workspace Conversation。

状态：**代码已实现，待 Actions / Release APK / 真机验证。**


## 2026-10-02 Worker 自主施工协议接线

- 新增 `FILE_CHANGE_REQUEST`：Worker 可以提交明确的 path / operation / content / commit_message。
- Coordinator 在实际写入前校验 TASK.scope 的 allow_paths / deny_paths / allow_operations、Workspace GitHub writeEnabled、Worker AI Member 与 ConstructionLock。
- 写入前重新读取 GitHub 当前文件 SHA，避免使用 AI 旧状态覆盖新版本。
- 写入成功后保存 Commit SHA，并立即触发一次 Commit-scoped Verify 查询；Verify 仍以真实 Actions Run 为准。
- Decision AI 的 `COMPLETE` 不再绕过已有 Commit 的 Verify；已有 Commit 的任务保持 `WAITING_VERIFY`，最终 COMPLETE 由 Verify 服务确认。
- 本批次仍为开发中，未产生新的 Actions Run / APK / 真机证据前不标记已验证。


## 2026-10-02 协作施工链第五轮代码检查

- 修复 FILE_CHANGE_REQUEST 被协议 when 重复枚举导致的编译错误。
- Worker 输出收紧为 DECISION_REQUEST / PROGRESS / BLOCKED / FILE_CHANGE_REQUEST；COMMIT / VERIFY / COMPLETE 不再由 Worker 直接声明，避免 AI 伪造施工完成状态。
- Worker 协作轮现在按 TASK / DECISION_RESPONSE 继续，并受 TASK.autonomy.max_iterations（1–20）限制。
- 每轮仍以真实任务状态为停止条件；产生 Commit 后进入 WAITING_VERIFY，不在同一轮继续写下一文件。
- FILE_CHANGE_REQUEST 已接入 UI 展示，Worker 施工请求可被直接识别。
- UI 中所有 CollaborationCoordinator 入口均绑定 Worker AI Member。
- 当前仍未形成 Verify PASS 后自动继续下一施工轮的后台循环；这属于下一阶段，不在本轮伪装成已完成。
- 本批次仍未合并、未发布 APK、未进行真机验证。

## 2026-10-02 构建 / 签名链第六轮检查

- 当前正式 APK 仍只有 `.github/workflows/android-build.yml` 这一条 Release 构建链：main push / 手动触发 → 固定触发 Commit → Release assemble → 官方 Keystore → `apksigner verify` → 统一 `A-BridgeFS.apk`。
- Workflow 会先校验 `git rev-parse HEAD == GITHUB_SHA`，因此不会因为 checkout 到错误 Commit 而静默构建旧代码。
- 当前签名配置同时区分 `storePassword` / `keyPassword` 两个 Gradle 字段，但 Workflow 实际把两者都设置为 `KEYSTORE_PASSWORD`。这在“Keystore 密码与 Key 密码相同”的现有设置下可以工作，但比 Android 官方示例的独立配置更窄；暂不改动，先确认现有正式 Keystore 的真实密码关系。
- 当前 Workflow 只执行 `apksigner verify --verbose`，尚未将“期望证书 SHA-256 指纹”作为硬性构建门槛。因此“APK 可验证签名”与“APK 一定由我们指定的正式证书签名”仍应区分记录。
- GitHub 官方建议敏感签名材料使用 Secrets；当前 Keystore Base64、Keystore 密码、Alias 均通过 GitHub Secrets 注入，符合这一基本方向。
- 当前 PR #36 仍为开发中，未合并、未发布新的 APK；本轮检查结果不作为真机验证证据。

## 2026-10-02 协作状态机第六轮

- Verify PASS 后新增**有界继续轮**：系统先确认真实 Commit-scoped Verify 通过，再恢复任务为 RUNNING，交给 Decision AI 判断 COMPLETE 或下一步 DECISION_RESPONSE。
- 下一轮 Worker 仍通过既有 `dispatchOneWorkerRound()`，若需要文件修改，必须重新经过 `FILE_CHANGE_REQUEST`、施工范围校验与 ConstructionLock。
- Verify FAIL 后新增“修复轮”入口：保留原施工锁，由 Decision AI 根据失败状态生成 DECISION_RESPONSE，再进入一次 Worker 轮；不得直接把 Verify FAIL 任务标记为完成。
- 这不是无限后台循环：每次 Verify 后最多继续一轮，后续 Commit 必须再次经过真实 Verify。
- Workspace UI 已提供 Verify 失败后的“根据 Verify 失败结果继续修复”入口；Verify 等待入口改为执行“Verify + 有界继续”。
- 本轮仍未合并、未构建、未真机验证；状态机代码需要下一轮 Release 构建确认编译与运行行为。


## 2026-10-02 协作状态机第七轮代码审查

- 修复：Worker 施工后若 GitHub Actions Verify 已经返回失败，随后 Decision AI 返回 COMPLETE 时，不再把 FAILED 覆盖为 WAITING_VERIFY。
- 状态优先保留真实 Verify 结果；COMPLETE 不能覆盖 FAILED / WAITING_VERIFY / 已完成状态。
- 当前仍未执行 Release 构建或真机验证；该修复需要后续正式构建确认编译，并用真实 Commit → Actions → Verify 流验证。


## 2026-10-02 协作状态机第八轮代码审查

- 修复：Verify PASS 后任务进入 RUNNING 时，历史 lastCommitSha 不再被当作“当前仍需 Verify”的依据；Decision AI 在没有产生新 Commit 的情况下可以正常 COMPLETE。
- 修复：GitHub Contents 写入后如果响应缺少 Commit SHA，不再继续进入 WAITING_VERIFY，直接停止并报告异常，避免形成无法验证的任务状态。
- 修复：Workspace 的 Verify / Verify 失败修复按钮增加任务级互斥，避免重复点击同时启动两次协作继续轮。
- 本轮仅修改开发分支，仍未合并、未构建、未真机验证。

## 2026-10-02 Verify 对应 Run 第九轮检查

- 修复：Verify 不再只按 `head_sha` 命中第一个 Actions Run。
- 正式 Verify 现在限定为 `.github/workflows/android-build.yml` / `Android Build and Release`。
- 同一 Commit 存在多个正式 Run 时，按 `created_at` 选择最新 Run；较旧 Run 不再抢先决定 PASS / FAIL。
- 这与当前正式 APK 构建链保持一致：**Android Build and Release 才是 A-BridgeFS 的正式 Release / Verify 来源**。
- 当前实际历史 Commit 查询显示，现有正式构建 Commit 各只有一个对应 Run；本轮修复主要针对未来增加其他 Workflow、同 SHA 手动触发等情况。
- 本轮仍仅修改开发分支，未合并、未构建、未真机验证。

