# APS 当前任务总表

更新时间：2026-10-04

> 本文件只维护**当前仍需施工 / 验收的任务**。
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

### T2 — 第一条核心链路：Project → Address → API → Conversation → Permission
**状态：当前施工重点**

当前已施工：
- Project domain models 与 ProjectStore 持久化职责拆分；
- GitHub Address 从历史 Workspace 模型迁移为 Project Address 模型；
- Project Conversation 的 API 调用链抽出为独立 Service；
- PermissionPolicy 参数边界改为 Project；
- Local Project Address 已开始从历史 `workspaceDirectory` 迁移为 `localAddress`。

当前未验证：
- 编译；
- APK；
- 真机；
- Project → Address → API → Conversation → Permission 完整运行闭环。

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
- 每段接通后再进入下一段。

第一阶段重点：
- Project Address 统一入口；
- Project → API Profile / Default AI 关系接通；
- Project Conversation 独立职责；
- Permission 改为 Project / Conversation 边界；
- 最终通过真实 Project Address 完成读取。

### T3 — Project Conversation 完整闭环
**状态：开发中**

验收：
- 正常问题只走 Default AI；
- 不自动召唤其他 Member；
- 消息 / API 身份 / 历史记录正常保存与恢复；
- 不恢复 Decision / Worker 模式。

### T4 — 任务与协助链
**状态：已设计未完整实现**

目标：

```
任务
 ↓
勾选
 ↓
@任务进入输入框
 ↓
Project Conversation
 ↓
Request AI Assistance
```

协助仍是按需动作，不形成固定 AI A / B 状态机。

### T5 — 连续施工
**状态：开发中 / 待核心链路收口后继续**

目标：

```
读取
 ↓
修改
 ↓
ConstructionLock
 ↓
Commit
 ↓
Verify
 ↓
必要时继续修复
 ↓
完成
```

### T6 — 独立「对话」与「设置」
**状态：UI 已确认 / 开发中**

当前正式三页：
- 项目
- 对话
- 设置

独立「对话」：
- 可选择 / 切换 API；
- 普通 AI 对话；
- 在授权范围内访问当前 Project 地址。

「设置」：
- 连接
- 权限
- 文件
- 执行
- 外观
- 通知
- 日志
- 系统

设置采用分类展开 / 收起，维护全局资源与系统配置。

### T7 — 全链路验证
**状态：待主链收口后执行**

```
代码
 ↓
UI
 ↓
用户操作
 ↓
状态保存 / 恢复
 ↓
真实行为
 ↓
正式签名 APK
 ↓
真机
```

每项 Verify 必须记录：
- Type
- Number
- Run ID
- Commit
- Job（如适用）
- Result

没有真实验证不得标记“已验证”。

## 2. 当前优先级

```
T1 Project UI 收口
 ↓
T2 Project → Address → API → Conversation → Permission
 ↓
T3 Project Conversation 闭环
 ↓
T4 任务 / @任务 / 协助
 ↓
T5 连续施工
 ↓
T6 独立对话 / 设置闭环
 ↓
T7 全链路验证
```

> 实际施工时，T1 与 T2 可在同一条代码链路中同步推进；不为了任务编号人为切断链路。

## 3. 明确不做

- 固定 Decision AI / Worker AI；
- 固定 AI A / AI B；
- 普通 Project 问题自动多 AI；
- 复杂 Project Dashboard；
- 无限 Agent Loop；
- GitHub 第二套 Workspace 模型；
- 现在提前建设 Secretary / Orchestrator。

## 4. 任务维护规则

1. 新任务先判断是否属于当前 Project 主线。
2. 施工优先按真实链路推进，而不是按文件数量推进。
3. 一个文件不长期承担多个独立业务职责。
4. 旧结构在链路经过时迁移、拆分或删除，不另开一轮无目标的大规模重构。
5. 已完成任务从“当前施工重点”中移出。
6. 历史过程写入 STATUS 或专项历史文档。
7. 架构决策写入 PROJECT，不在本表形成第二套规格。
8. 每次施工后只更新实际发生变化的任务状态。
