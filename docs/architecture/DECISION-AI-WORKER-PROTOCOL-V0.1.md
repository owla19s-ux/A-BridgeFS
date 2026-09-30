# Decision AI ↔ Worker 协作协议 v0.1

> 状态：**正式协议 / 已确认**
>
> 用于 A-BridgeFS 第一阶段 Decision AI ↔ Worker 自动协作闭环。
> 本文件定义角色间机器协作协议，不定义 UI、日志、调试输出、具体传输实现或多 Worker 调度。

## 0. 核心原则

1. **机器可判**：接收方可根据消息类型判断下一步动作。
2. **授权一次给足**：TASK 是一次施工授权；Worker 在授权范围内可连续施工。
3. **决策才暂停**：只有真正需要架构、产品或授权判断的问题才请求 Decision AI。
4. **事实不等于请求**：COMMIT、VERIFY、PROGRESS 只报告事实，不请求逐次批准。
5. **人不在正常循环里**：正常闭环不需要人工逐步确认；只有 ESCALATE 等情况进入人工环节。
6. **协议与传输分离**：协议不绑定 GitHub Issue、本地文件队列或其他具体传输方式。

## 1. 消息信封

所有正式消息共用：

```json
{
  "v": "0.1",
  "id": "msg_xxx",
  "ts": "2026-09-30T12:00:00Z",
  "from": "decision_ai | worker | human",
  "to": "decision_ai | worker | human",
  "task_id": "task_xxx",
  "type": "TASK",
  "reply_to": null,
  "payload": {}
}
```

必填：`v`、`id`、`ts`、`from`、`to`、`task_id`、`type`、`payload`。

无法解析或缺少必填字段：`BLOCKED / MALFORMED`。

## 2. TASK

**Decision AI → Worker**

定义目标、范围、验收条件和本次任务的自主边界。

```json
{
  "type": "TASK",
  "payload": {
    "objective": "一句话目标",
    "scope": {
      "allow_paths": ["src/**"],
      "deny_paths": ["**/*.key"],
      "allow_operations": ["read", "edit", "create", "delete", "run_test", "commit", "verify"]
    },
    "acceptance": ["可验证条件"],
    "autonomy": {
      "self_resolve": ["任务范围内普通实现问题"],
      "must_ask": ["必须请求 Decision AI 判断的情况"],
      "max_iterations": 20,
      "no_human_in_loop": true
    },
    "context_refs": ["docs/STATUS.md"]
  }
}
```

规则：

- `scope` 是硬边界，禁止顺手修改范围外内容。
- `must_ask` 是本次任务额外的决策边界。
- 实际自主权 = Worker 能力 ∩ TASK 授权。
- `scope_change` 产生的额外授权只对当前 `task_id` 生命周期有效，任务结束自动失效。

## 3. DECISION_REQUEST

**Worker → Decision AI**

用于真正需要决策的问题。

```json
{
  "type": "DECISION_REQUEST",
  "payload": {
    "kind": "ARCHITECTURE | PRODUCT | SCOPE_EXTENSION | OTHER",
    "question": "只问一个问题",
    "options": [
      {"id": "A", "summary": "方案", "implication": "影响"},
      {"id": "B", "summary": "方案", "implication": "影响"}
    ],
    "recommendation": "A",
    "reason": "为什么需要决策",
    "evidence": ["文件、行号或命令输出"],
    "blocked_on": "当前等待事项"
  }
}
```

原则上应提供至少两个合理选项。没有可供决策的方案、而是事实本身阻断施工时，使用 `BLOCKED`。

典型触发：

- 架构选择
- 产品行为选择
- 超出 TASK scope
- 命中 `must_ask`
- Worker 无法依据既定规则自行判断的关键问题

## 4. DECISION_RESPONSE

**Decision AI → Worker**

返回正式决策。

```json
{
  "type": "DECISION_RESPONSE",
  "payload": {
    "decision": "A",
    "instruction": "补充说明",
    "scope_change": null,
    "grants": {
      "extend_scope": [],
      "extra_iterations": 5
    }
  }
}
```

`DECISION_RESPONSE` 本身即为正式决策，**不再设置 `is_binding`**。

Worker 必须遵守该决策；如果新的决定超出 Decision AI 权限，应使用 `ESCALATE`。

## 5. COMMIT

**Worker → Decision AI**

纯事实通知，不请求批准。

```json
{
  "type": "COMMIT",
  "payload": {
    "sha": "abc123",
    "message": "commit message",
    "files": ["src/a.kt"],
    "diff_stat": "+12 -3"
  }
}
```

Commit 属于 TASK 授权范围内的正常施工动作，不得因此暂停等待 Decision AI。

## 6. VERIFY

**Worker → Decision AI**

报告实际验证结果。

```json
{
  "type": "VERIFY",
  "payload": {
    "target_commit": "abc123",
    "checks": [
      {"name": "build", "result": "pass", "detail": "..."}
    ],
    "verdict": "pass",
    "real_link_tested": true
  }
}
```

- VERIFY 不请求批准。
- `real_link_tested` 必须明确为 `true` 或 `false`，不得省略。
- 代码或仓库状态发生变化的 TASK，必须先 VERIFY 才能 COMPLETE。

## 7. PROGRESS

**Worker → Decision AI**

低频施工状态通知，不改变授权，也不请求继续。

```json
{
  "type": "PROGRESS",
  "payload": {
    "phase": "调查 | 编辑 | 测试",
    "done": "已完成事项",
    "next": "下一步",
    "files_touched": ["path"]
  }
}
```

默认不主动发送。Decision AI 不得仅因没有 PROGRESS 就判定 Worker 停止。

## 8. BLOCKED

**Worker → Decision AI**

事实性阻塞，当前无法继续。

```json
{
  "type": "BLOCKED",
  "payload": {
    "reason": "MALFORMED | TEST_FAILURE | MISSING_INFO | CONFLICT | RETRY_EXHAUSTED | EXTERNAL_FAILURE",
    "detail": "",
    "evidence": [],
    "needs": "需要什么才能继续"
  }
}
```

区别：

- 知道缺什么决定 → `DECISION_REQUEST`
- 没有可供决策的方案，事实本身阻断 → `BLOCKED`

## 9. ESCALATE

**Worker 或 Decision AI → Human**

当前问题超出对应 AI 的决策权限，需要人介入。

```json
{
  "type": "ESCALATE",
  "payload": {
    "reason": "HUMAN_AUTHORIZATION | OUT_OF_AUTHORITY | IRREVERSIBLE_RISK | UNRESOLVED_DECISION",
    "question": "需要人决定什么",
    "options": [{"id": "A", "summary": "方案 A"}, {"id": "B", "summary": "方案 B"}],
    "evidence": [],
    "blocked_on": "..."
  }
}
```

ESCALATE 用于防止 Worker ↔ Decision AI 无法解决时形成无限循环。

## 10. COMPLETE

**Worker → Decision AI**

当前 TASK 完成。

```json
{
  "type": "COMPLETE",
  "payload": {
    "summary": "完成了什么",
    "commits": ["abc123"],
    "acceptance_met": true,
    "leftover": []
  }
}
```

`acceptance_met=false` 时仍可 COMPLETE，但必须填写 `leftover`，不得掩盖未完成事项。

代码或仓库状态发生变化时：

```
COMMIT → VERIFY → COMPLETE
```

纯分析任务无代码/仓库状态变化时，可以直接 COMPLETE。

## 11. 核心闭环

正常：

```
TASK
 ↓
WORKING
 ↓
COMMIT
 ↓
VERIFY
 ↓
COMPLETE
```

决策问题：

```
WORKING
 ↓
DECISION_REQUEST
 ↓
DECISION_RESPONSE
 ↓
WORKING
```

授权不足：

```
WORKING
 ↓
DECISION_REQUEST (SCOPE_EXTENSION)
 ↓
DECISION_RESPONSE
 ↓
WORKING
```

无法继续：

```
WORKING → BLOCKED
```

AI 无权决定：

```
WORKING → ESCALATE → HUMAN
```

验证失败：

```
VERIFY(fail)
 ↓
Worker继续修复
 ↓
COMMIT
 ↓
VERIFY
```

超过限制：

```
RETRY_EXHAUSTED → BLOCKED
```

## 12. iteration

`iteration` 指一次完整的施工尝试周期，例如：

```
调查 → 修改 → 测试
```

算一次 iteration。

COMMIT / VERIFY 是该周期的结果确认，不单独计数。

达到 `max_iterations` 后：

```
→ BLOCKED / RETRY_EXHAUSTED
```

不得伪装成 COMPLETE。

## 13. 任务恢复

第一版不建立复杂 RESUME 消息。

恢复依靠：

- `task_id`
- 已确认消息
- Commit SHA
- VERIFY 结果

示例：

```
TASK task_001
 ↓
COMMIT abc123
 ↓
Worker异常退出
 ↓
恢复 task_001
 ↓
确认 abc123 已存在
 ↓
继续 VERIFY / 后续施工
```

`task_id` 是任务身份，Commit SHA 是代码状态锚点。

## 14. context_refs

第一版不绑定知识库系统。

推荐使用可解析引用，例如：

```json
"context_refs": [
  "docs/PROJECT-SPEC.md",
  "docs/STATUS.md"
]
```

未来可扩展为结构化 file / knowledge 引用，但协议不强制绑定具体知识库。

## 15. 传输层

协议与传输层分离：

```
Decision AI
     ↕
Message Protocol
     ↕
Transport Adapter
   ↙    ↓    ↘
GitHub  File  API
```

第一版可选择最容易验证的传输方式，但不得把具体传输写死进协议。

## 16. 第一版明确不做

- 多 Worker
- Worker 分片
- 多 Decision AI
- 复杂任务调度
- Agent 间推理共享
- 心跳协议
- 复杂日志协议
- UI 协议
- 完整人工审批系统
- 自动化多级权限系统

## 17. 正式状态

本协议已于 2026-09-30 确认。

状态：

**【正式协议 / v0.1 / 已确认】**

本文件是 A-BridgeFS 第一阶段 Decision AI ↔ Worker 实现的正式设计依据。
