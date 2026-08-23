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
| `mvn clean verify` | PASS（23 tests, 0 failures, 0 errors） |

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

2026-08-23 本地使用 Docker MySQL 8.0 重新验证持久化路径：

- `mysql` Profile 下 `mvn clean verify`：25 tests，0 failures，0 errors；
- 空数据库启动时 Flyway 成功应用 V1 / V2，schema version 到 2；
- 后续 Spring Context 再启动时显示 schema 已是 version 2，无重复 migration；
- `PersistenceConstraintIntegrationTest` 在真实 MySQL 下通过，组合外键与期次唯一约束有效；
- 实际 JAR 使用 MySQL 启动后，`LN-10002` 返回 8500 / 5000 / 3500、逾期 3 天；
- `flyway_schema_history` 中 V1 / V2 均为 success。

本阶段仍保留 H2 作为默认快速测试数据库，MySQL 不改变 Java Domain、Tool 或 Agent 的业务规则。
