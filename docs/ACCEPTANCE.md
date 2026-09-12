# LoanOps Agent 当前验收标准

这份文档只定义**当前版本至少要满足什么条件，才能说“这个能力已经完成”**。

历史 Phase 1-9 的验收记录放在 [history/PHASE_ACCEPTANCE_HISTORY.md](history/PHASE_ACCEPTANCE_HISTORY.md)，不再作为当前主线。

## 1. 基本原则

任何提交都不能通过下面这些方式“制造通过”：

- 删除、跳过或弱化已有测试；
- 修改 `DOMAIN.md` 去迎合错误实现；
- 把 `skipped` / `ENV_BLOCKED` 记成 PASS；
- 只因为自然语言答案“看起来对”就忽略 Tool / Audit 证据；
- 把固定演示数据集的结果写成生产准确率；
- 没有实际运行真实外部测试，却在文档里写“已通过”。

贷款业务规则冲突时，以 [DOMAIN.md](DOMAIN.md) 为准；项目范围冲突时，以 [SCOPE.md](SCOPE.md) 为准。

## 2. 默认自动测试

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

某些依赖真实外部环境的测试可以按设计跳过，但 skipped 不能解释成“真实 Provider / Policy RAG 已通过”。

## 3. 数据库与迁移

必须保证：

- Flyway migration 可以正常校验和执行；
- H2 自动测试路径正常；
- MySQL 集成路径正常；
- Conversation / Policy / Audit 表结构与代码一致；
- H2 与 MySQL 不出现同一业务规则算出不同结果的情况。

MySQL 路径可运行：

```powershell
./scripts/verify-mysql.ps1
```

## 4. 固定贷款事实

至少保持以下结果：

### LN-10001

```text
dueAmount         = 8500.00
outstandingAmount = 8500.00
overdue           = false
```

### LN-10002（固定测试业务日期）

```text
dueAmount         = 8500.00
paidAmount        = 5000.00
outstandingAmount = 3500.00
overdue           = true
```

### LN-10003

```text
settled          = true
totalOutstanding = 0.00
```

模型措辞可以变化，但 Java Service / Tool 的稳定事实不能变化。

## 5. Agent 安全边界

必须保持：

- 查询不存在的贷款时不编造金额、日期或逾期天数；
- 用户要求修改贷款状态时，不写贷款、还款计划或付款记录；
- Tool 只调用已有 Service，不复制贷款计算公式；
- 涉及当前贷款状态时重新查询 Tool，不把历史 Assistant 回答当数据库；
- Provider、Tool 或最终提交失败时，不追加“看起来成功”的 Conversation turn。

## 6. 多轮会话

必须验证：

- 成功的 USER / ASSISTANT 消息会持久化；
- Tool 调用和 Policy Context 不混进 transcript；
- 下一轮可以从前文识别正在讨论的 loan number；
- 识别出贷款以后仍重新查询当前 Tool；
- 并发写同一个 Conversation 时不会静默覆盖；
- 成功 transcript 与 Agent SUCCESS Audit 保持一致。

## 7. Policy RAG

必须验证：

- Policy 原文、版本和条款以 MySQL 为准；
- Qdrant 索引可以重新生成；
- 系统能区分“不需要政策、需要政策辅助、必须依赖政策”；
- 检索会按照业务日期过滤适用版本；
- 用户明确给出文号 / 条款时能精确查找；
- 知识库没有答案时能正确 no-match；
- 必须依赖政策但没有命中时，不让模型自己编规定；
- 回答缺少或写错 `[Pn]` 时，不能按成功提交；
- 30 个固定测试问题的指标口径可以复现。

详细指标见 [POLICY_RAG_EVALUATION.md](POLICY_RAG_EVALUATION.md)。

## 8. Chat Model

任何标记为“已验证”的 Chat Provider，都必须跑同一套 6 个 Agent 测试问题，并通过 Audit 确认实际使用的 Provider / Model。

当前支持状态见 [PROVIDERS.md](PROVIDERS.md)。

如果 API Key、Ollama 服务或模型没有准备好，只能记录：

```text
ENV_BLOCKED
```

不能偷偷换成另一个模型以后仍宣称原模型 PASS。

## 9. 真实 Policy RAG 端到端测试

这项测试需要：

- MySQL；
- Qdrant；
- Ollama / BGE-M3；
- 选定的 Chat Model；
- 对应 API Key 或本地模型。

第一轮：

```text
LN-10002 为什么逾期？
```

至少验证：

- 真的调用 `getOverdueDiagnosis(LN-10002)`；
- 金融事实来自 Java Tool；
- Agent Audit 成功；
- 成功对话持久化。

第二轮：

```text
按照规定现在应该怎么处理？
```

至少验证：

- 能从上一轮识别 `LN-10002`；
- 系统判断需要政策辅助；
- 找到适用 Policy；
- 回答包含有效 `[P1]`；
- Agent、Tool、Policy Audit 可以关联；
- Policy Embedding 仍使用 BGE-M3。

不要求每次完整自然语言输出完全相同。

## 10. Local Policy Bootstrap

Bootstrap 必须满足：

- 空 Policy 库可以初始化；
- 重复运行不产生重复数据；
- 演示数据部分缺失时可以恢复；
- 单独缺失一个 chunk 时可以恢复；
- 遇到未知 / 非 Demo Policy 时拒绝覆盖；
- MySQL、BGE-M3、Qdrant 或 rebuild 失败时明确失败；
- 修复依赖后可以重跑；
- 不提供 destructive force/reset/delete；
- 普通应用启动不自动 seed。

当前共享 Demo corpus 的已验收规模：

```text
documents = 4
versions  = 5
chunks    = 41
vectors   = 41
```

如果以后明确修改 corpus，应同步更新测试和验收快照，不把旧数字当永久规范。

## 11. SSE 流式响应

必须保持：

- 原同步 API 继续可用；
- Streaming 与同步接口复用同一套 Agent 逻辑；
- 不需要 Policy 的回答可以发送增量内容；
- 需要 Policy 的回答先完成引用校验和服务端提交，再把最终安全结果发给客户端；
- `done(committed=true)` 才代表这一轮正式提交成功；
- error / cancellation 不能留下伪成功 transcript。

## 12. 运行时限制

当前保持：

```text
单条用户消息         <= 4,000 characters
给模型的历史         <= 20 messages / 12,000 characters
HTTP connect timeout  = 5s
HTTP read timeout     = 60s
Spring AI max-attempts = 1
```

还需要保证：

- 历史截断不切掉半个 turn；
- 必须依赖政策的问题在检索失败时不能静默降级成成功；
- 辅助政策检索失败时不覆盖已经取得的贷款事实；
- Provider、引用校验或 Conversation commit 失败都有明确失败结果。

## 13. CI 与部署边界

当前 GitHub Actions 主要覆盖：

- H2 full Maven verification；
- MySQL integration verification。

依赖 Ollama / BGE-M3 / Qdrant 或外部 Provider secret 的测试仍然显式运行，不能因为 CI 没报错就宣称这些真实环境测试已经 PASS。

当前应用以 Java 21 Spring Boot JAR 运行，本地 MySQL / Qdrant 由 Docker Compose 提供。

仓库没有声明：

- 生产 application Docker image；
- Kubernetes；
- cloud deployment；
- TLS / Secrets Manager；
- production RBAC；
- Provider fallback / HA。

## 14. 文档一致性

公开文档不能互相定义冲突的“当前事实”。

当前分工：

- `DOMAIN.md`：贷款业务规则；
- `SCOPE.md`：当前范围；
- `PROVIDERS.md`：当前模型支持；
- `POLICY_RAG_EVALUATION.md`：RAG 指标；
- `RUNBOOK.md`：完整运行方式；
- `ACCEPTANCE_SNAPSHOT.md`：某一次真实执行结果。

`history/` 中的历史 Phase 和旧 snapshot 不覆盖当前文档。

## 15. 提交前至少检查什么

至少执行：

```powershell
git diff --check
mvn clean verify
```

如果本次修改涉及真实 Provider / Policy RAG，并且环境可用，再运行对应的 Provider baseline、Policy RAG 评测或端到端测试。

没有运行的检查必须明确写 `NOT_RUN` 或 `ENV_BLOCKED`，不能凭推测写 PASS。
