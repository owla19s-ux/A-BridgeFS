# APS 当前架构基线

更新时间：2026-10-04

## 产品模型

APS 以 **Project** 为核心对象：

```
Project
├─ Project Address
├─ Default AI
├─ Project Members
├─ Project Conversation
└─ Request AI Assistance
```

### Project Address

统一表示项目资源地址：
- Local
- GitHub
- future other storage

GitHub 是 Project Address 的一种资源类型，不再建立第二套 Workspace 业务模型。

### AI 模型

- API Profile：API 连接资源；
- Project Member：AI 在 Project 中的成员关系；
- Default AI：当前 Project 默认 Member；
- Request AI Assistance：按需请求其他 Member / 临时 AI 协助。

不使用固定 Decision AI / Worker AI。
不使用固定 AI A / AI B。

### 连续施工

```
理解
 ↓
读取
 ↓
修改
 ↓
Commit
 ↓
Verify
 ↓
必要时继续修复
 ↓
完成
```

ConstructionLock 用于控制修改阶段，不阻塞普通读取。

## 施工原则：按链路打通，同时按职责拆分

APS 当前重构不采用“先全面审查、再一次性重写”的方式。

施工以**真实可用链路**为主线：走到哪个功能，就修改哪个功能，并在经过旧结构时完成必要的职责迁移。

第一条核心链路：

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

后续再沿实际链路继续：

```
任务
 ↓
@任务
 ↓
Request AI Assistance
 ↓
读取 / 修改
 ↓
ConstructionLock
 ↓
Commit
 ↓
Verify
```

### 职责拆分原则

一个文件不应长期承担多个独立业务职责。

例如：
- Project UI 不承担 API 管理；
- Project Store 不承担完整 Conversation 业务；
- API Profile 不承担 Project 业务；
- Permission 不承担文件执行；
- FileBridgeService 不承担 Project UI / API / Conversation 状态管理；
- GitHub Address 不重新形成 Workspace 业务模型。

拆分不是为了机械增加文件数量，而是为了让**修改一个能力时，不需要连带修改无关能力**。

### 施工中的旧结构处理

遇到旧 Workspace、workspaceId、兼容字段或旧大文件时：

1. 如果当前链路仍需要其底层能力，先迁移职责；
2. 如果只是历史命名，随当前链路迁移为 Project 语义；
3. 如果职责已经被新模型取代，则拆除；
4. 暂未经过当前链路的代码，不为了“审查完整”而提前大规模改动。

因此，**链路施工本身同时承担审查、重构和验证职责**。

## UI

Project 页面以项目主要对话为主体：

```
项目
├─ 项目主要对话
├─ ↑ / ↓ 屏幕显示切换
└─ 项目配置
    └─ 独立点击展开 / 收起
```

↑ / ↓ 只改变屏幕显示，不控制项目配置展开。

## 独立「对话」

独立「对话」不属于 Project 主工作流，定位为 API 选择、普通问答、读取 / 分析；按当前正式 UI 设计，也允许在授权范围内访问当前 Project 的项目地址。

## 当前实现边界

具体“已实现 / 开发中 / 已验证”状态以 `docs/STATUS.md` 为准。
产品需求与行为边界以 `PROJECT/SPEC/APS-PRODUCT-SPEC.md` 为准。
UI 细节以 `PROJECT/UI/` 为准。
当前施工顺序以 `docs/CURRENT-TASKS.md` 为准。

## 架构规则

代码实现事实优先于文档描述。历史架构文档只能用于追溯，不能作为新功能设计依据。
