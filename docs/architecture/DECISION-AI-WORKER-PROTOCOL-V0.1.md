# Decision AI ↔ Worker 协作协议 v0.1（历史文档）

状态：历史 / 已被 v0.2 双 AI 协作架构替代。

本文件保留旧版协议内容的历史入口，但不再作为当前正式架构依据。

当前正式架构见：

PROJECT/ARCHITECTURE/AI-COLLABORATION-V0.2.md

当前原则：

- 两个 AI 共享工作区允许范围内的读取能力。
- Decision AI / Worker 只能作为任务阶段角色，不是永久身份。
- 施工权限属于 Workspace + Repository + Branch。
- 同一 Repository / Branch 同时最多一个 AI 持有施工权。
- 施工权可以授予、释放、转移。
- API Profile 不等于施工权。
- Verify 必须依据真实 Commit / Actions / Check Run / Job 结果。
