# LoanOps Agent 评测

## 1. 为什么需要固定评测

接入 LLM 后，Tool 选择和自然语言表达存在随机性。

因此项目不能只依赖：

> “我手工问了几遍，看起来都对。”

当前使用一套固定的 Provider-neutral Agent baseline，把模型行为与确定性 Java 事实分开验证。

这套评测不是 Java domain tests 的替代品。

金融金额、日期、逾期和结清的最终事实仍由 Java Service 与数据库状态决定。

## 2. 固定测试集

manifest：

```text
evaluation/agent-baseline-cases.json
```

当前包含 6 个 live Agent cases：

| Case | 主要验证 |
|---|---|
| `current-repayment-ln10001` | 为 LN-10001 选择 `getCurrentRepayment` 并返回确定性金额事实 |
| `overdue-diagnosis-ln10002` | 为 LN-10002 选择 `getOverdueDiagnosis` 并返回确定性逾期事实 |
| `settlement-ln10003` | 为 LN-10003 选择 `getSettlementStatus` 并说明结清结果 |
| `read-only-write-refusal` | 拒绝写请求，贷款状态不改变 |
| `stateful-reference-and-fresh-tool` | 从 Conversation 解析 follow-up 指代，并重新调用当前事实 Tool |
| `unknown-loan-no-hallucination` | unknown loan 的 Tool Audit 按预期失败，同时回答不编造金额 / 天数 |

## 3. 如何判定通过

Tool selection 不从自然语言答案“猜”，而是从 Agent / Tool Audit 验证。

自然语言只检查稳定事实或允许一定措辞差异，不比较完整字符串。

一个 case 的 PASS 需要结合：

- Agent Audit 状态；
- Tool Audit 状态；
- Tool 名称；
- loan number；
- Conversation 边界；
- requested provider/model 与实际 Audit identity；
- case-specific 稳定事实。

`unknown-loan-no-hallucination` 中，预期的 `LoanNotFoundException` Tool failure 不是 baseline failure，只要最终回答不编造业务事实。

## 4. 为什么 baseline 可以使用 H2

这套 baseline 的主要目的不是再次证明 MySQL transaction 或 Flyway，而是比较不同 Chat Provider 的 Agent 行为。

Conversation / MySQL persistence、restart continuity、CAS 等约束由 deterministic integration tests 和 MySQL Gate 覆盖。

使用固定 H2 fixture 可以：

- 保持金融事实稳定；
- 减少外部依赖；
- 提高 Provider 回归速度；
- 避免把数据库波动误判成模型波动。

## 5. 运行

仅验证 manifest：

```powershell
./scripts/evaluate-agent-baseline.ps1 -ValidateOnly
```

DeepSeek：

```powershell
./scripts/evaluate-agent-baseline.ps1 -Provider deepseek
```

Ollama：

```powershell
ollama pull qwen3:4b
./scripts/evaluate-agent-baseline.ps1 -Provider ollama
```

GLM：

```powershell
./scripts/evaluate-agent-baseline.ps1 -Provider glm
```

如果依赖不可用，runner 报告：

```text
ENV_BLOCKED
```

不得：

- 自动替换成其他模型；
- 把 skipped 当 PASS；
- 只根据答案文本宣称 Tool Calling 成功。

## 6. 固定业务时间

baseline 固定：

```text
business date = 2026-08-23
business zone = Asia/Shanghai
```

这样贷款事实不会因为运行当天日期不同而变化。

## 7. 报告

报告输出到 ignored：

```text
target/evaluation/
```

包括：

- repository HEAD；
- requested provider / adapter / model；
- 实际 Audit provider / model；
- 固定 business date / zone；
- system prompt hash；
- PASS / FAIL 数量；
- request / conversation identifier；
- duration；
- Tool Audit evidence；
- failed structured checks。

Secret 不进入报告。

## 8. 模型随机性如何处理

Agent baseline 是**回归 Gate**，不是统计学模型 benchmark。

历史 unchanged-config 运行曾出现 Tool-choice variance，尤其本地小模型路径。

因此：

- 保留首次失败证据；
- 可在完全不改配置时做有限 repeat；
- 后续一次 6/6 不能抹掉历史失败；
- 单次 6/6 不等于“100% 稳定”；
- 当前不使用 LLM-as-a-Judge 把主观语义分数包装成精确事实。

## 9. Provider 一致性原则

DeepSeek、Ollama / qwen3:4b、GLM / glm-5.2 使用同一 manifest。

新增 Provider 不能通过“给它定制一套更容易的问题”获得已验证状态。

具体 Provider 配置见 [PROVIDERS.md](PROVIDERS.md)。

## 10. 与 Policy RAG 评测的关系

Agent baseline 关注：

```text
自然语言
  -> Tool selection
  -> deterministic financial facts
  -> Conversation / refusal / unknown-loan behavior
```

Policy RAG 评测关注：

```text
Policy Router
  -> applicable version
  -> retrieval
  -> no-match
  -> citation
```

两者不应混成一个模糊的“Agent 准确率”。

Policy RAG 指标见 [POLICY_RAG_EVALUATION.md](POLICY_RAG_EVALUATION.md)。
