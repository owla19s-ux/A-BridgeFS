
# A-BridgeFS / BridgeFS+ 第一版整合架构 V0.1

状态：施工基线候选  
日期：2026-09-28  
来源：BridgeFS + AI-V1 + Weijia 资产审计

## 1. 第一版目标

验证：

用户配置 API 与 Role，并完成一次长期授权后，AI 可以持续向 Android 上的 BridgeFS+ 提交任务；BridgeFS+ 根据权限与风险策略决定是否执行，调用本地执行核心，并把真实 Receipt 返回给 AI。

最小闭环：

API / Local Chat → Role → Task → Command → Permission / Risk Policy → BridgeFS Executor → Android → Receipt → AI

## 2. 第一版模块

Provider  
Connection  
Role  
Chat  
Task  
Command  
Permission / Policy  
Executor  
Receipt / ExecutionRecord

### Provider

第一版配置：

- Name
- Base URL
- Credential Reference
- Model
- Enabled

Provider 不定义 Role。

### Connection

第一版以 API 为主，语义上保留 API、WEB、OAUTH、LOCAL。

### Role

第一版最小字段：

- id
- name
- systemPrompt
- providerId
- modelId
- enabled

Role 定义职责和 Prompt，不等于权限。

### Chat

本地聊天只是输入入口，不建立第二套执行系统。

### Task

建议字段：

- taskId
- createdAt
- source
- roleId
- connectionId
- status
- commandCount
- result

状态至少：

CREATED / RUNNING / WAITING_CONFIRMATION / SUCCEEDED / FAILED / DENIED

### Command

直接复用旧 BridgeFS Command。

第一版优先：

list / read / write / edit

后续再加入：

search / grep / path / copy-path / mkdir

### Permission / Policy

输入：

Subject + Role + Capability + Resource + Action + RiskLevel + Policy

输出：

ALLOW / WAITING_CONFIRMATION / DENY

### Executor

从旧 BridgeFS 迁移。

底层继续使用 PathSecurity.safe，并在其上增加授权范围。

### Receipt / ExecutionRecord

旧 BridgeFS 的文本执行结果标准化为结构化结果。

示例：

{
  taskId: task-001,
  status: SUCCEEDED,
  command: write,
  resource: test.txt,
  message: created
}

失败示例：

{
  taskId: task-001,
  status: DENIED,
  reason: permission_policy
}

只有 BridgeFS Receipt 才是本地执行事实。

## 3. 两个默认 Role

### 主AI

- 与用户交流
- 理解需求
- 判断是否需要本地操作
- 读取执行结果
- 继续工作

### 执行者

- 将需要本地操作的任务转换成标准 BridgeFS Command
- 遵守 Command 格式
- 不直接操作 Android

两个 Role 可以使用同一个或不同 Provider/Model。

第一版不要求两个 Role 必须由两个 AI 实例承担。

## 4. 长期授权

第一版采用：

用户授权一次 → 授权范围 + 风险策略持续生效 → 符合策略的任务自动执行。

例如：

Workspace：A3/A0项目/

允许：
L0 查询
L1 创建/修改
L2 批量修改

L3 删除：禁止

因此普通 write 可以自动执行，不需要每次弹确认。

## 5. 输入源

统一：

API  
Local Chat  
Clipboard/Share（后续）

→ Input Adapter → Role → Task → Command → Permission → Executor

新增输入源不能建立第二套执行系统。

## 6. Android 宿主

旧 BridgeFS Service 是重要资产。

第一版需要：

- Foreground Service
- 本地文件访问
- 持续运行
- Executor 宿主
- Receipt 返回

旧 Overlay UI 不作为核心依赖。

## 7. 第一版明确不做

- 多 Agent 自动协作
- 自动调度
- 书记员
- 复杂 Orchestration
- 完整长期记忆
- 完整 Work Context
- Project Engine
- Mapping
- NeuroMesh
- Browser Manager
- 云同步
- 多设备同步
- 复杂 UI

但这些方向不能被第一版的数据模型堵死。

## 8. 第一版验收

1. 添加 API Provider。
2. 创建或选择 Role。
3. Role 能调用 API。
4. AI 能生成 BridgeFS Command。
5. Parser 正确解析。
6. Permission 判断允许。
7. Android Executor 真实执行。
8. Receipt 返回真实结果。
9. AI 能读取 Receipt 并继续工作。
10. 连续多个任务在长期授权范围内无需重复授权。
11. 超出授权范围被拦截。
12. 高风险操作按 Policy 确认或拒绝。

通过后，BridgeFS+ 第一版成立。

## 9. 核心原则

AI 与执行分离。

Provider 与 Role 分离。

Capability 与 Permission 分离。

授权与确认分离。

云端已授权工具可以快速直接执行；本地设备必须经过 BridgeFS+ 权限层。

执行事实由 Runtime/Executor 产生，AI 自己的文字不能代替真实执行结果。

已有成熟资产优先复用。
