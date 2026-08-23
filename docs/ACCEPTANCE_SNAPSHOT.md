# Acceptance Snapshot

## Scope

此快照记录当前主线代码进入公开展示前的验收标准。它不是对未来所有提交永久有效的保证；后续修改后应重新运行脚本和测试。

## Automated verification

当前验收基线：

| Check | Result |
|---|---|
| Java 21 compilation | PASS |
| Domain unit tests | PASS |
| Persistence constraint tests | PASS |
| REST integration tests | PASS |
| 3 read-only Tool integration tests | PASS |
| Agent Controller tests | PASS |
| `mvn clean verify` | 以本阶段 H2/MySQL 实际执行结果为准 |

## Deterministic business cases

固定业务日期：`2026-08-23`。

| Case | Expected |
|---|---|
| `LN-10001` | due 8500, outstanding 8500, not overdue |
| `LN-10002` | due 8500, paid 5000, outstanding 3500, overdue 3 days |
| `LN-10003` | settled, total outstanding 0 |

## Live AI verification

DeepSeek 已完成真实 API E2E 验证，日志确认模型分别调用：

```text
getCurrentRepayment
getOverdueDiagnosis
getSettlementStatus
```

同时验证：

- 不存在贷款时不生成虚构贷款事实；
- 请求写入/结清时 Agent 拒绝执行；
- 写请求前后 `LN-10002` 的确定性状态保持一致。

Qwen / GLM 尚未完成当前项目的真实 E2E，因此不计入已支持 Provider。

## Re-run

```powershell
./scripts/verify-resume-mvp.ps1
```

需要真实 DeepSeek：

```powershell
./scripts/verify-resume-mvp.ps1 -WithAi
```

## Phase 5 resume-ready verification

2026-08-23 本地重新执行 scripts/verify-resume-mvp.ps1 -WithAi，结果为 PASS。验收同时覆盖固定业务日期、三笔确定性贷款案例、真实 DeepSeek Tool Calling、不存在贷款以及写操作拒绝。
## MySQL / Flyway verification

此前基线已用 Docker MySQL 8.0 验证 V1/V2 持久化路径；本阶段 V3 的真实 MySQL 结果必须以本阶段脚本执行为准：

- `mysql` Profile 下 `mvn clean verify`：以本阶段实际执行结果为准；
- 空数据库应应用 V1 / V2 / V3，schema version 到 3；
- `agent_audit_log` 与 `agent_tool_audit_log` 应在真实 MySQL 中存在；
- 本机本轮若无法访问 Docker/MySQL，不得把上述期望写成已通过结果。

本阶段仍保留 H2 作为默认快速测试数据库，MySQL 不改变 Java Domain、Tool 或 Agent 的业务规则。

## Agent Audit / Observability

本阶段新增 Flyway `V3__create_agent_audit_tables.sql`，V1/V2 保持不变，并在 H2 与 MySQL 8.0
创建 `agent_audit_log`、`agent_tool_audit_log`。请求先写入 `STARTED`，再更新为技术结果
`SUCCESS` 或 `FAILED`；Tool 事件记录顺序、名称、贷款号、耗时和技术结果。

默认 `loanops.audit.include-content=false`，只保存长度和 SHA-256 指纹，不保存完整 prompt/answer；
SHA-256 不是匿名化。Spring AI prompt/completion/tool content observations 默认关闭，指标不使用
`requestId`、`loanNo` 等高基数字段不进入 metric tag；tag 值只允许有限白名单，其他值归为 `unknown`。
`POST /api/agent/chat` 由 Servlet Filter 在 JSON 反序列化前生成服务端 `X-Request-Id`；客户端传入值会被替换，malformed JSON 仍保留 transport correlation id。
只有成功解析成 Agent message 的请求才创建 Agent Audit；空白 message 会写 `STARTED` 后转为 `FAILED / InvalidAgentMessageException`，且不调用模型。
Audit 对传给 ChatClient 的原始 message 计算指纹，不执行隐式 `trim`。
`GET /api/agent/audits/{requestId}` 返回该次 Agent 审计及按 sequence 排序的 Tool events。

Actuator 只暴露 `health`、`info`、`prometheus`。审计查询是当前无 RBAC 的本地 Demo/验收接口，
不代表生产安全边界。
