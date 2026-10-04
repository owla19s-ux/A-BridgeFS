# APS 当前任务总表

更新时间：2026-10-04

> 本文件只维护**当前仍需施工 / 验收的任务**。
> 产品模型、架构原则、UI 正式设计和仓库操作规则分别由 PROJECT 与对应 docs 负责，本文件不重复定义。

## 1. 当前施工主线

### T0 — 架构基线与旧链清理
**状态：基本完成 / 持续审查**

已完成：
- Project 作为核心对象；
- Project Address；
- API Profile / Project Member / Default AI 边界；
- 固定 Decision / Worker / AI A / AI B 废弃；
- 旧 Collaboration 运行链拆除；
- ConstructionLock 保留为 Project 施工权限基础；
- 当前架构、规格、UI 文档已建立。

剩余：
- 持续检查旧 Workspace / 双 AI 语义是否还有实际代码入口或文档残留。

### T1 — Project 主页面收口
**状态：当前施工重点**

验收：
- Project Conversation 是项目首页主要工作面；
- ↑ / ↓ 只改变屏幕显示区域；
- 项目配置独立点击展开 / 收起；
- 配置收起后主要对话获得空间；
- 不把 GitHub / API / 权限 / Verify 等内部对象全部平铺到首页；
- 不再保留旧 Workspace 页面作为实际入口。

### T2 — Project 配置闭环
**状态：实现基本完成，待完整闭环验证**

范围：
- Project 名称；
- Project Address（Local / GitHub）；
- Default AI；
- Project Members；
- Member → API Profile；
- 必要的本地修改权限。

验收：
```
UI 修改
 ↓
保存
 ↓
离开 Project
 ↓
重新进入
 ↓
状态保持
```

已知实现：
- Project Members：新增 / 编辑 / 删除 / API Profile 绑定 / Default AI；
- Local Address：目录选择、持久化、恢复；
- GitHub Address：Repository / Branch 配置与权限保存。

### T3 — Project Conversation 闭环
**状态：开发中**

目标：
```
Project
 ↓
Project Address
 ↓
Default AI
 ↓
读取 / 分析
 ↓
回答
```

验收：
- 正常问题只走 Default AI；
- 不自动召唤其他 Member；
- 消息 / API 身份 / 历史记录正常保存与恢复；
- 不恢复 Decision / Worker 模式。

### T4 — Project 连续施工
**状态：开发中 / 需要全链路验证**

目标：
```
理解
 ↓
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

已接入：
- 本地：PermissionPolicy → FileBridgeService → Receipt → Default AI 继续判断；
- GitHub：Project ConstructionLock + Project Member 身份执行文件创建 / 编辑 / Commit；
- 已读取对应 Actions runs 作为 Verify 信息；
- 已设置明确迭代上限。

待验证：
- 多文件 Commit / Verify 语义；
- Actions 尚未启动时的状态判断；
- 正式 APK + 真机连续施工闭环。

### T5 — Request AI Assistance
**状态：已设计未完整实现**

目标：
- Default AI 主导；
- 明确请求后才调用其他 Project Member / 临时 AI；
- 协助完成后回到 Default AI；
- 不形成固定 AI A / B 状态机。

### T6 — 独立「对话」与「配置」
**状态：暂缓**

本轮不阻塞 Project 主线。后续只做必要 UI / 可用性修正，不重新设计底层架构。

### T7 — 全链路验证
**状态：待 T1～T5 收口后执行**

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
T1 Project 主页面
 ↓
T2 Project 配置闭环验证
 ↓
T3 Project Conversation
 ↓
T4 连续施工
 ↓
T5 Request AI Assistance
 ↓
T7 全链路验证
```

T6 暂不阻塞。

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
2. 已完成任务从“当前施工重点”中移出，不重复堆进本表。
3. 历史过程写入 STATUS 或专项历史文档。
4. 架构决策写入 PROJECT，不在本表形成第二套规格。
5. 每次施工后只更新实际发生变化的任务状态。
