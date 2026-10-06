# APS 当前任务总表

更新时间：2026-10-06

> 本文件只维护当前仍需施工 / 验收的任务。
> 产品模型、架构原则、UI 正式设计和仓库操作规则分别由 PROJECT 与对应 docs 负责，本文件不重复定义。

## 1. 当前施工主线

### T0 — 架构基线
**状态：已确认**

已完成：
- Project 作为核心对象；
- Project Address；
- API Profile / Project Member / Default AI 边界；
- 固定 Decision / Worker / AI A / AI B 废弃；
- 当前三页 UI 架构确认；
- “链路驱动施工 + 职责拆分”确定为当前重构方法。

旧 Workspace 残留不再单独开一轮全面清理，而是在实际链路施工经过时迁移或拆除。

### T1 — Project 主页面
**状态：开发中**

验收：
- Project Conversation 是项目首页主要工作面；
- ↑ / ↓ 只改变屏幕显示区域；
- 项目配置独立点击展开 / 收起；
- 配置收起后主要对话获得空间；
- 不把 GitHub / API / 权限 / Verify 等内部对象全部平铺到首页。

### T2 — Project Conversation 主链
**状态：开发中**

当前已施工：
- Project domain models 与 ProjectStore 持久化职责拆分；
- GitHub Address 从历史 Workspace 模型迁移为 Project Address 模型；
- Project Conversation 的 API 调用链抽出为独立 Service；
- PermissionPolicy 参数边界改为 Project；
- Local Project Address 已开始从历史 `workspaceDirectory` 迁移为 `localAddress`。

当前重点：
- Project Address / API Profile / Default Member / Conversation 完整闭环；
- Bridge 指令进入真实 Execution；
- 权限边界与状态恢复。

目标：

```
Project
 ↓
Project Address
 ↓
API Profile
 ↓
Project Conversation
 ↓
Permission
 ↓
真实访问 Project
```

施工原则：
- 按真实用户链路逐段打通；
- 每经过一个旧结构，就在该位置完成职责迁移；
- 不先做脱离链路的全仓库重构；
- 一个文件不长期承担多个独立业务职责；
- 能复用的底层能力保留，错误的业务语义替换；
- 每段接通后先做对应云端验证，再进入下一段。

### T3 — Task / Execution
**状态：开发中**

已实现：
- Task lifecycle 统一入口；
- 写入类操作要求 RUNNING Task；
- ConstructionLock 与 Task lifecycle 绑定；
- PermissionPolicy 区分 LIST / READ / WRITE / EDIT / COMMIT；
- Commit Service 校验 Task / Member。

待验证：
- 模块测试：Task 状态、Permission、ConstructionLock、CommandParser；
- 契约测试：Task → Execution → Lock → Commit；
- 真机只保留云端无法可靠证明的行为，如权限、生命周期和实际设备交互。

### T4 — GitHub / Commit / Verify
**状态：开发中**

已实现：
- Global GitHub AccessPolicy；
- Project Read / Write；
- ConstructionLock holder 校验；
- Commit 指令；
- Verify Run / Jobs / Artifacts。

待验证：
- 云端业务链：Commit → Actions → Verify；
- NOT_TRIGGERED / RUNNING / PASSED / FAILED 语义；
- 必要的真实 GitHub 环境行为。

### T5 — Project 主链 Integration
**状态：开发中**

主链：

```
Project → Address → Default Member → Conversation → Permission
→ Task → Execution → ConstructionLock → Commit → Verify
```

新增工程验证任务：
- 盘点并建立 `src/test`；
- 盘点并建立 `src/androidTest`；
- 建立 Gradle 测试依赖与执行入口；
- 建立第一批模块测试；
- 建立核心契约测试；
- 建立 Project 主链云端测试；
- 再评估 CI 测试闸门如何逐步接入。

测试体系必须支持未来增加 API、其他 Address / Module、远程能力和更多 Project，而不能绑定当前 GitHub 实现。

### T6 — 独立「对话」与「设置」
**状态：UI 已确认 / 开发中**

### T7 — 正式 APK / 真机验收
**状态：待云端主链收口后验收**

范围：
- 安装 / 启动；
- UI 操作；
- 键盘与输入区；
- 生命周期；
- 系统权限；
- 实际 API / GitHub；
- 最终用户体验。

APK 后置，不作为日常开发测试工具。

## 2. 当前优先级

T1 UI 收口 → T2 Conversation → T3 Task/Execution → T4 GitHub/Verify → T5 云端测试体系与主链 Integration → T6 独立对话/设置 → T7 真机验收

## 3. 明确不做

- 固定 Decision AI / Worker AI；
- 固定 AI A / AI B；
- 普通 Project 问题自动多 AI；
- 复杂 Project Dashboard；
- 无限 Agent Loop；
- GitHub 第二套 Workspace 模型；
- 现在提前建设 Secretary / Orchestrator；
- ProjectContinuationCoordinator 与 continuation 运行链。

## 4. 任务维护规则

1. 新任务先判断是否属于当前 Project 主线。
2. 施工优先按真实链路推进，而不是按文件数量推进。
3. 一个文件不长期承担多个独立业务职责。
4. 旧结构在链路经过时迁移、拆分或删除，不另开一轮无目标的大规模重构。
5. 已完成任务从当前施工重点中移出。
6. 历史过程写入 STATUS 或专项历史文档。
7. 架构决策写入 PROJECT，不在本表形成第二套规格。
8. 每次施工后只更新实际发生变化的任务状态。
