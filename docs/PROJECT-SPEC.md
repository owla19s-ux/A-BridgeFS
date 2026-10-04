# A-BridgeFS 项目需求功能书

更新时间：2026-10-04

> 记录 ≠ 确认。状态随讨论、施工和真实验证推进。

## 一、当前项目方向
A-BridgeFS 定位为：

**AI 项目工作与真实执行环境的 Android 工作台。**

核心能力：
- Project 项目管理
- Project Conversation
- Default AI / Project Members
- 按需 AI 协助
- Project Address（Local / GitHub）
- BridgeFS 本地真实执行
- 权限 / Commit / Verify / Receipt

核心关系：

Project → Project Address → Default AI → Project Conversation

需要协助时，其他 Project Member / 临时 AI 按需加入。施工权按 Project + Repository + Branch 独占。

## 二、协作需求

### R-001 Project 默认 AI 连续工作
状态：设计已确认 / 开发中

Project 的默认 AI 负责主要工作；需要其他 AI 时通过 Request AI Assistance 按需加入。不存在固定 Decision AI / Worker AI。

### R-002 施工权
状态：已确认 / 开发中

施工权属于 Project + Repository + Branch。

同一 Repository / Branch 同时最多一个 AI 持有施工权。

施工权可以授予、释放、转移，或因授权撤销而失效。

施工权不是 API 身份固有属性。

### R-003 小问题自主处理
状态：已确认方向 / 待验证

持有施工权的 AI，在当前任务范围内可自行处理普通实现问题，不得自行改变已确认架构、产品行为、任务范围或权限边界。

### R-004 协作消息
状态：设计已确认 / 开发中

至少支持：
PROPOSAL / QUESTION / DECISION / TASK / PROGRESS / REQUEST_WRITE / GRANT_WRITE / RELEASE_WRITE / COMMIT / VERIFY / BLOCKED / COMPLETE / ESCALATE

消息协议不绑定固定 AI 身份。

### R-005 Verify 工序确认
状态：开发中

Commit 后必须确认：
Commit SHA → Check Run / Workflow Run → Job → 实际结果

### R-006 A-BridgeFS 协作回执
状态：开发中

用户看到项目当前状态：
WAITING / RUNNING / SUCCESS / FAILURE / BLOCKED

### R-007 协作日志
状态：开发中

记录时间、Workspace、AI、Repository / Branch、任务、协作消息、施工权变化、Commit / Verify、Receipt。

与运行日志、Crash 日志分离。

### R-008 外部等待
状态：候选

需要表达等待对象、等待原因、对应 Commit、恢复条件、当前恢复位置。

### R-009 多 API 管理
状态：已实现第一阶段 / 待验证

当前已具备多 API Profile、添加 / 修改 / 移除、API 全局访问开关、Chat → API 持久绑定、对话页选择当前 Chat 使用的 API。

仍需实现：
- Workspace AI 成员模型
- AI 与 API 的关系
- 施工权与 API 身份解耦

## 三、Workspace / Conversation

Workspace 是协作资源集合，包含：
- AI 成员
- API / Model 资源
- GitHub Repository / Branch
- 权限
- 施工锁
- 协作任务
- 协作消息
- 执行与 Verify 记录

Conversation 是人 ↔ 某个 AI 的对话空间，可以选择 API / Model、产生普通聊天消息、查看协作状态、查看相关 Receipt。

Conversation 不等于 Workspace，也不等于 AI 身份。

## 四、UI / 产品原则
- 移动端优先
- 简洁、明确、低负担
- 人看状态，AI 查日志
- UI 解决人的理解和操作问题，不把内部复杂性全部暴露给用户
- 实际手机截图作为 UI 判断依据之一
- UI 想法先记录，确认后再进入正式施工
