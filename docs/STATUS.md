# A-BridgeFS 当前状态

更新时间：2026-10-04

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

## 2026-10-02 执行权限边界第十轮检查

- 发现并修复：独立对话触发 BridgeFS 时，原执行 Intent 为了区分独立对话回执而将 `projectId` 设为 null；FileBridgeService 因此无法恢复当前 Workspace，只能按全局权限重新计算。
- UI 发送前虽然已经按 `Workspace + 独立 Conversation` 检查权限，但 Service 作为最终执行入口必须再次拥有同一 Workspace 身份，否则存在“UI 判定禁止 / Service 重新判定为允许”的权限边界不一致。
- 现在独立执行 Intent 额外携带 `workspaceId`；`projectId` 继续保持 null，仅用于维持独立 Conversation 回执路由。
- FileBridgeService 现在按 `workspaceId` 恢复 Workspace，再与独立 Conversation 一起进入 `PermissionPolicy.authorization()`。
- 本轮仍未构建、未安装 APK、未真机验证。

## 2026-10-02 Receipt 持久化队列检查

- 发现：`FileBridgeService` 原先只有单个 `pending_receipt` 槽位；Activity 不在前台时，如果连续产生多个执行回执，后一个回执可能覆盖前一个。
- 修复：改为 `pending_receipts` JSON 队列，每条回执增加唯一 `receiptId`；广播同时携带该 ID。
- Activity 前台收到回执时按 `receiptId` 从队列中删除对应项，不再清空整个队列。
- Activity 启动恢复时会按队列顺序恢复全部可路由回执；目标 Conversation 暂时不存在的回执保留在队列中等待后续恢复。
- 保留旧 `pending_receipt` 的兼容读取，迁移后删除旧槽位。
- 本轮未构建、未安装 APK、未真机验证。

## 2026-10-02 协作状态机与施工锁第十一轮检查

- 发现：Verify 成功属于 GitHub 事实状态；原实现释放施工锁时使用严格 `require(holder)`。如果 App 重启/恢复后本地锁记录已经不存在，可能出现“真实 Actions 已成功，但任务无法进入 COMPLETE”的状态不一致。
- 修复：Verify 成功后的锁释放改为幂等处理。锁存在且由原施工者持有时正常释放；锁已不存在或已提前释放时记录诊断日志，不阻止任务进入 COMPLETE；异常释放失败同样记录日志。
- UI 状态入口复核：WAITING_CONSTRUCTION 仅提供 Worker 申请施工锁；WAITING_VERIFY 提供 Verify/继续协作；FAILED 且仍有施工者时提供 Verify 失败修复轮；与当前任务状态机保持一致。
- 本轮仍未构建、未安装 APK、未真机验证。
- 协作恢复再加一层保护：Transport 中未标记 handled 的旧 TASK/DECISION_RESPONSE 可能跨进程保留；现在只有任务仍处于 RUNNING/CONSTRUCTING 时才允许 Worker round 消费，WAITING_VERIFY/COMPLETE/FAILED 等状态不会重放旧消息，避免 Commit 后因进程重启再次施工。
## 2026-10-02 构建链与协作 Branch 对齐第十二轮

- 发现真实链路断点：Workspace 允许配置 Repository + Branch 施工锁，但正式 Android Workflow 原先只监听 `main`。因此非 main Branch 的真实 GitHub Contents Commit 不会触发正式 Verify Run，协作任务可能永久 WAITING_VERIFY。
- 修复：正式 `Android Build and Release` Workflow 现在对所有 push Branch 执行同一套 Release APK 构建与签名校验；但只有 `main` 才执行 `Publish latest Release`。
- 结果：施工 Branch 可以获得与正式链一致的 Build / signature verification / Artifact / Commit-scoped Verify；不会覆盖正式 `latest` Release。
- 注意：该修改目前只存在于开发分支，尚未产生新的 Actions Run，不能标记为已验证。



## 2026-10-02 构建 / 签名链第十三轮检查

- 按 Android 官方方式补强 APK 签名身份校验：apksigner verify --print-certs 可取得 APK 的 SHA-256 证书摘要；官方文档明确该工具可用于读取已签名应用的 SHA-256 certificate digest。 
- Workflow 现在从受保护的正式 Release Keystore 导出其公钥证书，计算 SHA-256，再与最终 app-release.apk 的实际 Signer #1 SHA-256 逐字比对。
- 因此不需要把证书指纹本身再作为 Secret 保存；私钥材料仍只通过 GitHub Secrets 注入，证书指纹作为公开身份信息在 CI 中计算。
- 新门槛同时保留 apksigner verify --verbose：一层确认 APK 签名结构有效，一层确认签名者就是当前正式 Keystore 的证书。
- 本次修改 Commit：68101b58eb79c5b79fdf32c1f2ed534039ce038a，目前仍在 PR #36 开发分支；该 Commit 尚无新的 Actions Run，因此不能标记为已验证。
- 结论：签名链从“签名有效”提升为“签名有效 + 签名身份与正式 Keystore 一致”；但尚未获得 CI 实际执行证据。


## 2026-10-02 Verify 状态机第十四轮检查

- 发现状态回退漏洞：任务进入 COMPLETE 或 FAILED 后，仍可通过旧的 lastCommitSha 再次调用 Verify；这可能重复写 VERIFY_PASS / VERIFY_FAIL Receipt，甚至让历史 Commit 的 CI 结果重新参与当前任务状态判断。
- 修复：CollaborationVerifyService 现在只允许 STATUS_WAITING_VERIFY 进入真实 Commit-scoped Verify。
- COMPLETE：重复检查直接返回 PASSED，但不再次查询/修改任务状态，也不新增 Receipt。
- FAILED：重复检查直接返回 FAILED，提示必须进入修复轮并产生新的 Commit。
- CREATED / RUNNING / CONSTRUCTING / WAITING_CONSTRUCTION 等状态不会拿历史 Commit 直接进入 Verify，而是返回 WAITING。
- 这使 lastCommitSha 可以继续作为历史审计字段保存，同时不再等同于“当前待验证 Commit”。
- 本轮修改 Commit：20f72b9c018d0c818b81725a4e8ac0afcf41a061。
- 仍未进行 Release 构建 / APK / 真机验证。


## 2026-10-02 ConstructionLock 第十五轮检查

- 发现任务记录中的 constructionHolderAiMemberId 与实际 ConstructionLock 存在短暂不一致风险：任务状态可能仍记录某 AI 为施工者，但真实 SharedPreferences 锁已经被清理或恢复失败。
- 修复：每次 GitHub Contents updateFile 写入前，除了检查任务记录持有者，还必须调用 ConstructionLockStore.requireHolder(workspace, aiMemberId) 重新确认 Repository / Branch 的实时施工锁。
- 因此“任务说我是施工者”不能单独获得 GitHub 写权限；只有“任务记录 + 实际 ConstructionLock”同时成立才可写入。
- Verify 失败仍保留施工者信息，允许修复轮重新获取/确认同一施工权；Verify 成功后释放实际锁并清除任务持有者。
- 本轮代码 Commit：01ae2970986e5b4bb4e3348002724d2659ad8eb6。
- 仍未进行 Release 构建、APK 发布或真机验证。


## 2026-10-02 Verify 修复轮第十六轮检查

- 发现 Verify 失败后的 retryAfterVerifyFailure() 原先只检查任务记录中的 constructionHolderAiMemberId，随后就把 FAILED 改为 RUNNING。
- 风险：实际 ConstructionLock 已被清理/丢失时，任务会进入 RUNNING，但修复轮并没有真实施工权。
- 修复：FAILED → RUNNING 前，必须通过 ConstructionLockStore.requireHolder() 确认记录的修复者仍持有当前 Workspace + Repository + Branch 的实际施工锁。
- 这样 Verify 失败后的修复轮不会产生“任务已恢复、施工权不存在”的中间状态。
- 本轮代码 Commit：19468a83a8cc22b20816bb835dc7d35c52edfce2。


## 2026-10-03 Verify 后续协作状态边界第十七轮

- 发现 verifyAndContinue() 原逻辑依赖 CollaborationVerifyService 在 Verify PASS 时先写 COMPLETE，随后再改回 RUNNING。
- 风险：如果进程恰好在两次持久化之间终止，任务恢复后会被视为 COMPLETE，但 Decision AI 后续审议尚未完成。
- 修复：Verify PASS 后直接进入 RUNNING，再追加系统 COMMIT 并执行 Decision AI 后续轮；不再暴露“Verify 已通过但后续协作尚未完成”的 COMPLETE 中间态。
- COMPLETE 现在只应由真正完成边界写入。
- 本轮代码 Commit：7969f8a8e7482d3a87712a8bfeb193b43e9a0ccf。


## 2026-10-03 协作状态机第十八轮：Verify / 写入恢复

- 修正第十七轮遗留的状态机问题：Verify PASS 不再先写 COMPLETE。新增 VERIFY_PASSED 中间态，只有 Verify 后续 Decision AI 真正返回 COMPLETE 时才进入 COMPLETE。
- 新增 CONSTRUCTION_WRITING 状态及待写入字段：path / content / commit message / 原始文件 SHA。GitHub Contents API 写入前先持久化写入意图。
- 如果 App 在 GitHub 写入成功、但本地保存 Commit SHA 之前终止，恢复流程不会盲目再次写入；会重新读取远端文件 Blob SHA，并检查当前 Branch HEAD 的 Commit 是否包含目标文件变更，确认后恢复为 WAITING_VERIFY。
- GitHub API 增加 Branch HEAD / Commit 查询能力，用于上述恢复核对。
- Verify 后继续轮与 Verify 失败修复轮进入新阶段前，会清理同一 Task 下遗留的 Worker TASK / DECISION_RESPONSE，避免旧输入抢先驱动新一轮。
- 以上属于代码级修复；当前 PR #36 仍未合并，尚未通过新的 Release Actions Run / APK / 真机验证。


## 2026-10-03 第十八轮补充：恢复数据最小化

- 写入恢复不再把待写文件正文保存到 SharedPreferences；任务只保存预期 Git Blob SHA、目标路径、原始文件 SHA 等恢复元数据。
- 恢复时以远端 Blob SHA 为第一判断依据，避免重复 Contents API 写入；确认 Branch HEAD 包含目标文件后才恢复为 WAITING_VERIFY。
- Workspace UI 已暴露 VERIFY_PASSED / CONSTRUCTION_WRITING 两种可恢复状态，进程在 Verify PASS 后中断或写入阶段中断时不会留下无入口状态。
- 静态检查曾发现并已修复一次旧字段残留；当前最新开发分支头为 486413343a3f9eab521abefb72c217d706ebe69a。
- 当前仍无该 Commit 对应的新 Actions Run，因此本轮结论仍是代码级检查，不是构建/真机验证。


## 2026-10-03 第十八轮结构审查补充

- 发现并删除重复的 `app/src/main/java/com/abridgefs/CollaborationTransport.kt`。该文件虽然目录较旧，但声明同一个 `com.abridgefs.app` package，并重复定义 CollaborationTransport / CollaborationApiConfig / CollaborationApiClient / CollaborationCoordinator；保留统一的新路径 `app/src/main/java/com/abridgefs/app/CollaborationTransport.kt`。
- 修正 Verify PASS 后 `VERIFY_PASSED -> DECISION_RESPONSE -> Worker` 的继续路径：Decision AI 要求继续时，现在显式恢复 `RUNNING` 后再派发 Worker，避免因 Worker 调度器只接受 RUNNING / CONSTRUCTING 而静默停止。
- 当前 PR #36 已不再包含重复 Transport 文件。
- 最新修复仍未合并；截至本轮，最新 Commit 尚未出现新的 Android Build and Release Actions Run，因此没有把代码级结果冒充为构建验证。


## 2026-10-03 Receipt 路由与执行权限第十九轮检查

- 发现 Receipt 广播缺少 workspaceId：独立对话执行虽然在 Intent 中携带了 workspaceId，但 FileBridgeService 广播回 Activity 时没有继续传递，可能导致回执在 Activity 恢复时落入当前 Workspace。
- 修复：FileBridgeService 的 Receipt 持久化与 Broadcast 均保留 workspaceId；V021Activity 前台接收与 pending_receipts 恢复均优先按 workspaceId，再按 conversationId 定位原始对话。
- 权限链复核：全局 perm_* → Workspace localFileModifyEnabled → Conversation localFileModifyOverride 最终统一进入 PermissionPolicy；FileBridgeService 作为最终执行入口会再次计算权限，不依赖 UI 单次判断。
- ai_auto_bridgefs_enabled 当前只控制独立 AI 对话自动解析并触发 BridgeFS 指令；Workspace AI 协作走独立的 Collaboration 流程，没有误用该开关。
- 本轮仍未构建、未发布 APK、未真机验证。


## 2026-10-03 Receipt UI 第十九轮补充

- 发现并修复：Receipt 已正确写入目标 Workspace，但 Activity 当前正在查看另一个 Workspace 时，原逻辑仍会直接刷新 `WORKSPACE_CHAT`，用户会看不到目标回执，容易误判为回执丢失。
- 现在只有当前页面确实属于目标 Workspace 时才直接刷新；如果用户正在查看其他 Workspace，则保留目标回执并提示“收到执行回执”，不强制切换用户当前工作区。
- 消息气泡已确认：user / assistant / tool / receipt 均有独立显示路径，所有消息均提供复制入口；Receipt 不是只能通过“回执”按钮取出，而是直接进入聊天消息。
- 本轮仍未构建、未发布 APK、未真机验证。


## 2026-10-03 Receipt 幂等性第二十轮检查

- FileBridgeService 已生成唯一 `receiptId`；此前 Workspace / standalone 的历史模型没有持久化该 ID，导致前台 Broadcast 与后台 pending recovery 缺少统一幂等键。
- 现在 `BridgeReceiptRecord` 持久化 `receiptId`；旧历史数据加载时自动生成兼容 ID。
- 前台 Broadcast 插入 Receipt 前按 `receiptId` 去重；后台恢复同样按 `receiptId` 去重。
- standalone chat 与 Workspace chat 都只在用户当前确实正在查看目标对话时刷新；其他情况下只提示，不抢夺当前页面。
- 本轮仍未构建 APK、未发布、未真机验证。


## 2026-10-04 第十九轮收口与正式构建前检查

- PR #36 已合并到 main，当前 main 头为 `f47bd1131d4fe6e607966bc936d083d5c911a762`。
- 本轮已合并 Receipt 幂等、目标 Workspace 路由、Standalone 对话路由、聊天 UI 批量反馈等修复。
- API 消息身份的数据保存与 V021 消息展示代码均已存在：AI 消息按发送时保存的 API 名称 / 头像展示；但尚无该 main Commit 对应的正式 Release Actions Run，因此仍只能标记为“代码已实现 / 待构建验证”。
- 下一步按 `docs/BUILD-PRECHECK.md` 触发正式 `android-build.yml`，优先获得当前 main 的 Release APK / 签名 / Actions 事实，再进行真机回归。
