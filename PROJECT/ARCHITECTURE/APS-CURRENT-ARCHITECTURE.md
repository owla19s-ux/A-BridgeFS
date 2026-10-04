# APS 当前架构基线

更新时间：2026-10-04

## 产品模型

APS 以 **Project** 为核心对象：

```text
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

```text
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

### UI

Project 页面以项目主要对话为主体：

```text
项目
├─ 项目主要对话
├─ ↑ / ↓ 屏幕显示切换
└─ 项目配置
    └─ 独立点击展开 / 收起
```

↑ / ↓ 只改变屏幕显示，不控制项目配置展开。

### 独立「对话」

独立「对话」不属于 Project 主工作流，默认定位为 API 选择、普通问答、读取 / 分析。

## 当前实现边界

具体“已实现 / 开发中 / 已验证”状态以 `docs/STATUS.md` 为准。
产品需求与行为边界以 `PROJECT/SPEC/APS-PRODUCT-SPEC.md` 为准。
UI 细节以 `PROJECT/UI/` 为准。

## 架构规则

代码实现事实优先于文档描述。历史架构文档只能用于追溯，不能作为新功能设计依据。