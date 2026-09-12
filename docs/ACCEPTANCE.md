# LoanOps Agent 当前验收标准

本文只定义**当前版本**需要满足的验收 Gate。

历史 Phase 1-9 的开发门禁已归档到 [history/PHASE_ACCEPTANCE_HISTORY.md](history/PHASE_ACCEPTANCE_HISTORY.md)，不再作为当前文档主线。

## 1. 全局原则

任何提交都不得通过以下方式“制造通过”：

- 删除、skip 或弱化已有测试；
- 修改 `DOMAIN.md` 去迎合错误实现；
- 把 skipped / ENV_BLOCKED 记为 PASS；
- 用自然语言看起来正确替代 Tool / Audit evidence；
- 把固定 synthetic dataset 的指标描述成 production accuracy；
- 在文档中声称未实际执行的外部 E2E 已通过。

金融规则冲突时，以 [DOMAIN.md](DOMAIN.md) 为准。

项目范围冲突时，以 [SCOPE.md](SCOPE.md) 为准。

## 2. 确定性自动测试

必须执行：

```powershell
mvn clean verify
```

要求：

```text
BUILD SUCCESS
0 failures
0 errors
```

默认外部 opt-in tests 可以按环境设计 skip，但不能把 skipped 当作真实 Provider / Policy Hero PASS。

## 3. 数据库与迁移

必须保证：

- Flyway migrations 可重复校验；
- H2 deterministic path 正常；
- MySQL integration path 正常；
- Conversation / Policy / Audit schema 与代码一致；
- MySQL / H2 不出现业务事实分叉。

可运行：

```powershell
./scripts/verify-mysql.ps1
```

## 4. 金融事实

固定业务事实至少保持：

### LN-10001

```text
dueAmount         = 8500.00
outstandingAmount = 8500.00
overdue           = false
```

### LN-10002（固定 baseline business date）

```text
dueAmount         = 8500.00
paidAmount        = 5000.00
outstandingAmount = 3500.00
overdue           = true
```

### LN-10003

```text
settled           = true
totalOutstanding  = 0.00
```

自然语言可以变化，但 Java Service / Tool 的稳定事实不能变化。

## 5. Agent 安全边界

必须保持：

- unknown loan 不编造金额、日期或逾期天数；
- 写请求不会修改贷款、还款计划或付款记录；
- Tool 只调用 Service，不复制贷款业务公式；
- 当前金融事实通过 fresh Tool query 获得；
- Provider / Tool / completion failure 不追加伪成功 Conversation turn。

## 6. 多轮会话

必须验证：

- USER / ASSISTANT transcript 持久化；
- Tool / Policy Context 不写入 transcript；
- follow-up 可从 prior USER context 解析 loan；
- 当前事实仍重新查询 Tool；
- optimistic CAS 阻止并发覆盖；
- successful transcript append 与 Agent SUCCESS audit 保持一致。

## 7. Policy RAG

必须验证：

- MySQL 是 Policy canonical store；
- Qdrant 是可重建派生索引；
- Router 能区分 `NOT_REQUIRED / SUPPLEMENTAL / REQUIRED`；
- Retrieval 按业务日期过滤 applicable version；
- exact-reference / temporal / no-match 受固定评测覆盖；
- `REQUIRED + NO_MATCH` 不调用模型编造 Policy；
- invalid / missing `[Pn]` 在成功提交前被拒绝；
- 30-case Gold Dataset 的指标口径保持可追溯。

详细指标见 [POLICY_RAG_EVALUATION.md](POLICY_RAG_EVALUATION.md)。

## 8. 模型 Provider

任何标记为“当前已验证”的 Chat Provider 必须使用同一 6-case baseline，并验证实际 Audit identity。

当前 Provider 状态以 [PROVIDERS.md](PROVIDERS.md) 为准。

如果 Provider 环境不可用，状态只能是：

```text
ENV_BLOCKED
```

不能替换模型后仍声称原 Provider PASS。

## 9. Policy Hero E2E

真实 Hero 需要：

- MySQL；
- Qdrant；
- Ollama / BGE-M3；
- 选定 Chat Provider；
- 对应 secret / local model。

稳定断言：

### Turn 1

```text
LN-10002 为什么逾期？
```

要求：

- `getOverdueDiagnosis(LN-10002)`；
- Agent Audit SUCCESS；
- 金融事实来自 Java Tool；
- 成功 turn 持久化。

### Turn 2

```text
按照规定现在应该怎么处理？
```

要求：

- 从 prior USER context 解析 loan；
- Policy decision = `SUPPLEMENTAL`；
- retrieval status = `MATCHED`；
- applicable evidence 进入 Context；
- 回答包含有效 `[P1]`；
- Agent / Tool / Policy Audit 可关联；
- embedding model 保持 BGE-M3。

完整自然语言不固定。

## 10. Local Policy Bootstrap

Bootstrap 必须满足：

- fresh local store 可初始化；
- 重复执行幂等；
- partial Demo 可恢复；
- 单独缺失一个 chunk 可恢复；
- 未知 / 非 Demo Policy 数据 fail closed；
- dependency / rebuild failure 显式失败；
- 失败后可重试；
- 不提供 destructive force/reset/delete；
- 普通应用启动不自动 seed。

当前 Demo corpus 的已验收规模为：

```text
documents = 4
versions  = 5
chunks    = 41
vectors   = 41
```

该数量属于当前 shared synthetic corpus；如果未来 corpus 明确变更，应该同步更新测试和验收快照，而不是把旧数字当永久规范。

## 11. SSE 流式响应

必须保持：

- 同步 API 兼容；
- Streaming 与同步路径复用同一 Agent 业务逻辑；
- `NOT_REQUIRED` 可发送 provisional delta；
- Policy turn 在 Citation Validation 与 successful commit 后安全下发；
- `done(committed=true)` 才代表服务端 turn 正式成功；
- error / cancellation 不允许伪造成功 transcript。

## 12. 运行时加固

必须保持：

```text
current message       <= 4,000 characters
model-visible history <= 20 messages / 12,000 characters
HTTP connect timeout  = 5s
HTTP read timeout     = 60s
Spring AI max-attempts = 1
```

并验证：

- history 截断不破坏完整 turn；
- required-policy failure 不能静默降级成无 Policy 成功；
- supplemental-policy failure 不覆盖已取得的金融事实；
- Provider / Citation / Conversation commit failure 都有稳定失败语义。

## 13. CI 与部署边界

当前 GitHub Actions 主要覆盖：

- H2 full Maven verification；
- MySQL integration verification。

Ollama / BGE-M3 / Qdrant Policy RAG、外部 Provider secret-dependent tests 保持 opt-in，不能因为 CI 未执行就宣传为 CI PASS。

当前应用以 Java 21 Spring Boot JAR 运行，本地 MySQL / Qdrant 由 Docker Compose 提供。

仓库当前没有声明：

- application Docker image 的生产发布；
- Kubernetes；
- cloud deployment；
- TLS / Secrets Manager；
- production RBAC；
- provider fallback / HA。

## 14. 文档一致性

公开文档不得互相定义冲突的“当前事实”。

当前职责：

- `DOMAIN.md`：金融规则；
- `SCOPE.md`：当前范围；
- `PROVIDERS.md`：Provider 状态；
- `POLICY_RAG_EVALUATION.md`：RAG 指标；
- `RUNBOOK.md`：完整运行方式；
- `ACCEPTANCE_SNAPSHOT.md`：某次已执行验收结果。

`history/` 中的 Phase 与旧 snapshot 不覆盖当前文档。

## 15. 完成前检查

至少：

```powershell
git diff --check
mvn clean verify
```

如果当前改动涉及对应外部能力，并且环境可用，再运行相关：

```text
Provider baseline
Policy RAG evaluation
Policy Hero
Local Policy Bootstrap manual / integration checks
```

没有运行的 Gate 必须明确写 `NOT_RUN` 或 `ENV_BLOCKED`，不得推断 PASS。
