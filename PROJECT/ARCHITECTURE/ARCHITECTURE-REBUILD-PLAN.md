# APS Architecture Rebuild

> 状态：施工策略  
> 分支：`architecture-rebuild`  
> 基线：`main`  
> 定位：在保留现有可用能力的基础上，以新架构重新适配 UI、功能、底层与模块。

## 一、这次重建是什么

这不是从零重写 APS，也不是对旧代码做一次全面的 Workspace → Project 改名迁移。

本次采用：

**新架构 + 维加体系参考 + 现有 APS 代码零件库 → 按完整业务链路重新适配。**

旧代码中已经验证可用的能力优先复用；结构不合适但逻辑有价值的部分提取/适配；与新架构冲突或已经废弃的部分不迁移。

目标不是“把旧代码整理得更漂亮”，而是建立一套真正打通：

**UI → 业务 → Store → Service → Module → 外部能力 → Execution → Result / Evidence → Verify**

的 APS 新结构。

## 二、核心重建原则

### 1. 新架构定方向，旧代码提供零件

- 以 UI 已确认的 Space / Context / Connection / Dispatcher 模型作为当前主方向。
- 参考 AI+ / AI-V1 / 维加已经思考和试错过的概念。
- 不复制维加规模，不把维加完整体系搬入 APS。
- 现有代码按实际价值选择复用、适配、提取、重写或放弃。

| 旧代码状态 | 处理 |
|---|---|
| 与新架构一致、已验证可用 | 复用 |
| 功能正确、边界/命名旧 | 适配 |
| 核心逻辑有价值、结构不合适 | 提取 |
| 局部与新架构冲突 | 重写该部分 |
| 已废弃、无有效调用 | 不迁移 / 删除 |
| 暂时无法判断 | 暂留，不强行处理 |

### 2. 按业务链路施工，不按旧文件追引用施工

不再采用：

旧文件 → 找引用 → 改引用 → 再发现引用 → 回头修旧文件

作为主要施工方式。

改为：

**先定义完整业务链路 → 从 UI 一直向下落 → 每层确定职责 → 复用/适配已有实现 → 完整验证。**

### 3. UI 与功能必须真正打通

功能存在但 UI 无法调用，不视为完成。

UI 存在但下面没有真实功能，也不视为完成。

每条主要链路都必须能够从用户操作进入真实功能，并最终得到可验证结果。

### 4. Activity 不再成为业务总管家

Activity 主要承担 Android 生命周期、页面承载、导航和页面级状态协调，不把 Project、Conversation、GitHub、API、施工、文件执行、权限等业务继续堆进一个 Activity。

### 5. 按职责拆分文件

原则：

> 一个文件 / 类承担一个清晰职责；一个业务链路可以由多个职责明确的文件共同完成。

不追求文件越多越好，也不接受大文件长期承担多个独立职责。

### 6. 包名同步重建

内部 Kotlin/Java package 可以按新职责重建；Android `applicationId` `com.abridgefs.app` 保持独立，不因内部包重建自动修改。

### 7. Context 是当前工作核心，Connection 是统一能力入口

重点保持：

- Space
- Context
- Connection
- Resource
- Task
- Receipt / Evidence
- Verification
- Dispatcher

Resource、Relation、Capability、Connection、Account、Identity、Policy、Control Domain 等保留架构空间，但当前不建设完整基础设施。

### 8. 维加只提供参考，不成为 APS 的施工清单

吸收 Resource / Address、Capability / Module、Connection / Permission / Credential 分离、Conversation 与 UI 分离、Execution → Result + Evidence → Verification、ConstructionLock 与 Permission 分离、Module / Adapter 优先扩展等思想。

不施工完整身份体系、通用 Capability Registry、跨控制域授权、Mapping、大规模多维对象或跨组织基础设施。

## 三、重建施工顺序

### Phase 0：基线确认
- 保留 `main` 作为当前可运行基线。
- architecture-rebuild 从 main 开始。
- 记录当前关键功能和构建状态。
- 不在重建分支中破坏 main。

### Phase 1：包与基础结构
- 确定 package 结构。
- 建立 UI / Domain / Store / Service / Module / Infrastructure 边界。
- 将现有可复用代码按职责迁入或适配。
- 不为了拆分制造空壳层。

### Phase 2：Project 主链路
```
Space / Context UI → Context State → Context Domain → Context Store / Service → Connection / Resource
```

### Phase 3：Context Conversation
```
Context → Conversation → AI Connection → API Profile → AI Request → Response
```

### Phase 4：AI Connection / API
明确 AI Connection ≠ API Profile，完成 AI Connection 管理、Context 权限、API Profile 绑定与实际调用。

### Phase 5：Connection / Resource / Connector
```
Context → Connection → Resource → Connector → 具体外部能力
```
GitHub 是 Connection 类型及其 Connector / Resource 实现，不重新形成 Workspace 或 Project Address 业务模型。

### Phase 6：施工链路
```
Context → Task → Dispatcher → 拆分 → 做 → Connection / Connector → 实际结果 → 审查 → Receipt / Evidence → Verify
```
不恢复旧 Decision AI / Worker AI 模型。

### Phase 7：Request AI Assistance
在三阶段任务链稳定后，再完善多 AI 按需协助，不形成固定 AI 角色状态机。

### Phase 8：独立 Conversation / Config
保持独立「对话」与「设置」边界；独立对话不自动获得 Context 施工权限。

### Phase 9：清理与验证
只有新结构和新链路验证稳定后，才处理旧 Workspace 残留、无效兼容字段、废弃 Store、旧 Activity、无效包和旧文档引用。

## 四、测试与验证策略

重建不是“全部写完再测”。

**云端验证前置，真机验证后置。** 能在云端可靠证明的问题，不把真机当主要测试场。

标准路径：

```
需求 / 验收条件
 ↓
业务链 / 模块边界
 ↓
数据 / 状态 / 权限 / 接口约定
 ↓
最小实现
 ↓
代码级检查
 ↓
模块测试
 ↓
契约测试
 ↓
功能 / 业务链测试
 ↓
云端工程验证
 ↓
必要时 APK
 ↓
少量真机验收
 ↓
Verify / 收口
 ↓
PR / 合并
 ↓
main 正式构建
```

测试边界必须支持未来扩展，不绑定当前 GitHub、API 或 UI 实现。外部能力应允许使用测试实现；当前继续保持单 `app` 模块，只有构建、依赖、复用或独立发布等实际需求出现时才评估 Gradle 多模块。

Build 是工程验证的一环，不是模块完成条件。APK 是验收产物，不是日常开发测试工具。

结构性问题进入“重写候选”评估，不继续无限堆兼容补丁。

完整规范见 `PROJECT/ARCHITECTURE/TESTING-AND-VALIDATION.md`。

## 五、每条链路的完成标准

每个功能至少需要：
1. UI 可以进入；
2. UI 可以触发；
3. 中间业务层职责清楚；
4. Store / Service / Module 正常工作；
5. 必要的真实外部能力可执行；
6. 结果返回；
7. 必要时产生 Evidence；
8. 通过相应等级的云端验证；
9. 云端无法可靠证明的部分再进行真机验收。

状态仍区分：已设计未实现、开发中、已实现、已验证、废弃。

## 六、重建期间的保护原则

- `main` 是安全基线。
- 重建分支可以调整结构，但不能假装验证完成。
- 每完成一个完整链路就验证，不等全部重写后才第一次运行。
- 发生架构冲突先判断，不用兼容代码无限堆叠。
- 不为了“新架构”强行重写稳定且边界合理的底层实现。
- 不为了“复用旧代码”继承明显不合理的旧结构。
- 不恢复 Decision AI / Worker AI / 固定 AI A/B。
- 不把 Activity 再次发展成巨型业务文件。
- 不为了测试方便提前制造大量 Gradle 模块。

## 七、当前阶段

APS 已从“大规模架构重建”进入：

**主链收口 + 云端测试体系建设 + 验证。**

当前主链：

Space / Context → Connection → Dispatcher → Task → 拆分 → 做 → 实际结果 → 审查 → Verify

当前只继续处理明确 Bug、集成缺口、UI 行为、验证发现的问题和必要文档同步。

静态审查没有明确硬问题时，不再为了重构而重构。

## 八、文档边界

- 当前架构：PROJECT/ARCHITECTURE/APS-CURRENT-ARCHITECTURE.md
- 施工策略：PROJECT/ARCHITECTURE/ARCHITECTURE-REBUILD-PLAN.md
- 测试规范：PROJECT/ARCHITECTURE/TESTING-AND-VALIDATION.md
- 当前产品规格：PROJECT/SPEC/APS-PRODUCT-SPEC.md
- 当前 UI：PROJECT/UI/
- 当前状态：docs/STATUS.md
- 当前任务：docs/CURRENT-TASKS.md
- 历史资料：PROJECT/HISTORY/ 与明确标记为历史的专项文档。
