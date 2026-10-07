# APS 对象与资源模型

更新时间：2026-10-07

> 本文件定义 APS 第一阶段的对象、资源、状态、任务与回执关系。这里的“对象”是架构层中性术语，不限定具体业务类型；UI 与产品文案不要求直接使用该名称。

## 一、核心原则

APS 不要求一个对象只有一个资源地址，也不负责把同一对象复制到多个端进行同步。

一个对象可以关联多个资源来源：

```
APS 对象
├── Resource A → Local
├── Resource B → GitHub
├── Resource C → Cloud
└── ...
```

因此必须区分：

- **对象**：APS 持续管理的独立上下文；
- **Resource**：对象实际涉及的外部资源；
- **State**：对象当前可持续工作的状态；
- **Task**：需要完成的工作单元；
- **Receipt**：AI / 工具提交的结果及其确认依据。

## 二、对象与资源

对象本身不等于资源，也不等于某一个 Connector。

例如一个对象可以同时包含：

```
对象 A
├── GitHub Repository
├── 本地设计文档
├── 云端资料
└── 其他资源
```

Connector 只负责访问相应 Resource：

```
Resource
 ↓
对应 Connector
 ↓
读取 / 修改 / 查询
```

APS 不因为存在多个 Resource 就建立多端同步副本。

## 三、状态存储

State 是对象持续施工所需的权威状态。

State 至少可以包含：

- 当前目标；
- Task 树；
- Task 依赖；
- 当前进度；
- 当前施工权；
- 最近结果；
- Receipt；
- Commit / Verify 等证据引用；
- 下一步。

State 必须有明确的权威存储位置。

对象存在多个 Resource 时，不要求 State 与所有 Resource 各保存一份。

例如：

```
对象 A
├── State → GitHub .aps/
├── Resource → GitHub Repository
└── Resource → Local 文档
```

或者：

```
对象 A
├── State → Local
├── Resource → GitHub Repository
└── Resource → Local 文档
```

State 存储位置是对象配置的一部分。

## 四、任务与资源

Task 不直接等同于某个 AI。

一个 Task 可以声明：

- 目标；
- 依赖；
- 所需 Resource；
- 施工权限；
- 当前执行者；
- 验收者；
- 当前状态；
- Receipt。

例如：

```
Task：修改 GitHub 代码
 ↓
GitHub Connector
 ↓
AI 施工
 ↓
Commit
 ↓
Verify
 ↓
Receipt
```

另一个 Task 可以：

```
Task：整理本地文档
 ↓
Local Connector
 ↓
AI 施工
 ↓
Receipt
```

同一个对象下的不同 Task 可以访问不同 Resource。

## 五、回执与事实确认

AI 回执不直接等于事实。

```
AI 回执
 ↓
实际证据
 ↓
确认
 ↓
State 更新
```

对于代码施工，典型证据包括：

```
Task
 ↓
Commit
 ↓
Actions / Verify
 ↓
验收结果
 ↓
Task 状态
```

因此“已完成”“已验证”等状态必须能够追溯到对应 Receipt / Evidence。

## 六、多 AI 协作

多 AI 协作以 Task 为基本交接单位，而不是以完整 AI 上下文为交接单位。

基本方向：

```
Task
 ↓
分配 AI
 ↓
施工
 ↓
Receipt
 ↓
验收
 ↓
State 更新
 ↓
下一 AI
```

AI 可以被替换，不要求继承前一个 AI 的完整聊天上下文。

当前仅确定这一原则；具体的任务树、分配、验收、并行施工、施工权和冲突控制协议仍属于后续设计。

## 七、并行施工

对象可以拥有多个 Task，但多个 AI 是否能够同时修改同一 Resource 必须单独判断。

对于 GitHub 等支持版本控制的 Resource，未来并行施工可能需要：

```
Task A → Branch / Worktree A
Task B → Branch / Worktree B
Task C → Branch / Worktree C
          ↓
       Review / Merge
          ↓
        Verify
```

第一阶段不因为存在多 AI 就预先建立复杂并行执行引擎。

## 八、APS 的职责边界

APS 负责：

1. 识别对象及其 Resource；
2. 根据 Resource 选择 Connector；
3. 读取 / 更新 State；
4. 根据 Task 进行调度分配；
5. 接收 Receipt；
6. 根据实际证据确认状态。

APS 不负责：

- 把所有 Resource 复制到本地；
- 建立默认多端同步系统；
- 把云端作为本地缓存之外的第二份事实源；
- 保存所有 AI 的完整长上下文；
- 在没有需求时建立复杂 Agent Runtime。

## 九、示例

### 示例 1：代码 + 本地文档

```
APS 对象
├── State → GitHub .aps/
├── Resource → GitHub Repository
└── Resource → 手机本地文档

Task A → 修改 GitHub 代码
Task B → 整理本地文档
Task C → 综合验收
```

### 示例 2：本地素材 + 云端资料

```
APS 对象
├── State → Local
├── Resource → 手机素材目录
└── Resource → 云端资料

Task A → 整理素材
Task B → 读取云端资料
Task C → 综合生成 / 验收
```

以上都不要求把资源复制到 APS 内部。

## 十、后续设计重点

下一阶段需要继续明确：

1. 对象的正式命名；
2. Resource 的标识与生命周期；
3. State 的最小数据结构；
4. Task 树与依赖模型；
5. Receipt / Evidence 模型；
6. State 的权威存储选择；
7. 多 AI 施工权；
8. 并行 Task 的隔离与合并。
