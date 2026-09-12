# LoanOps Agent 评测

## 1. 为什么不能只靠“我问了几遍都对”

大模型的 Tool 选择和自然语言表达存在随机性。

同一个问题今天能选对 Tool，不代表换一个模型以后仍然能选对；一次回答看起来合理，也不代表背后真的调用了正确接口。

因此项目固定了 **6 个 Agent 测试问题**，让 DeepSeek、Ollama 和 GLM 都跑同一套用例。这样可以直接检查：

> 换模型以后，原来已经能做对的事情有没有坏掉？

这套测试主要验证 Agent 行为，不能替代 Java 业务规则测试。金额、日期、逾期和结清的最终事实仍由 Java Service 决定。

## 2. 六个固定测试问题

测试定义在：

```text
evaluation/agent-baseline-cases.json
```

| Case | 实际检查什么 |
|---|---|
| `current-repayment-ln10001` | 问“本期应该还多少钱”时，是否调用 `getCurrentRepayment` 并返回正确金额 |
| `overdue-diagnosis-ln10002` | 问“为什么逾期”时，是否调用 `getOverdueDiagnosis` 并返回正确逾期事实 |
| `settlement-ln10003` | 问“是否结清”时，是否调用 `getSettlementStatus` |
| `read-only-write-refusal` | 用户要求修改贷款状态时，Agent 是否拒绝且数据库不变化 |
| `stateful-reference-and-fresh-tool` | 下一轮说“他”时能否识别上一轮贷款，同时重新查询当前数据 |
| `unknown-loan-no-hallucination` | 查询不存在的贷款时，Tool 可以失败，但回答不能编造金额和天数 |

## 3. 为什么不能只看最终回答文本

例如模型回答：

```text
这笔贷款当前逾期 3 天。
```

文字看起来没问题，但它可能是模型猜出来的，也可能真的调用了 `getOverdueDiagnosis`。

所以测试不只检查回答，还会看 Agent / Tool Audit，确认：

- 调用了哪个 Tool；
- 查询的是哪笔贷款；
- Tool 成功还是失败；
- 实际使用了哪个 Provider / Model；
- Conversation 是否符合预期；
- 关键金额、日期和状态是否与 Java 业务事实一致。

自然语言允许措辞变化，不要求完整字符串完全相同。

## 4. 为什么 Agent 评测使用 H2

这组测试主要比较“不同模型是否还能正确使用 Agent”，并不是再次验证 MySQL、Flyway 或 Qdrant。

因此使用固定 H2 数据有几个好处：

- 每次贷款事实一致；
- 外部依赖更少；
- 模型回归速度更快；
- 不会把数据库环境问题误判成模型问题。

MySQL 持久化、Conversation restart、并发提交等能力由各自集成测试和真实端到端测试覆盖。

## 5. 怎么运行

先只检查测试定义是否合法：

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

如果 API Key、Ollama 服务或指定模型没有准备好，脚本报告：

```text
ENV_BLOCKED
```

它表示环境阻塞，不是 PASS，也不是业务 FAIL。

## 6. 为什么固定业务日期

测试固定：

```text
business date = 2026-08-23
business zone = Asia/Shanghai
```

否则同一笔贷款今天跑可能逾期 3 天，下个月跑就变成 30 多天，回归结果没有可比性。

## 7. 测试报告里记录什么

报告保存在：

```text
target/evaluation/
```

其中会记录：

- repository HEAD；
- 期望使用的 Provider / Model；
- Audit 里实际使用的 Provider / Model；
- 固定业务日期；
- system prompt hash；
- PASS / FAIL 数量；
- request / conversation identifier；
- Tool Audit；
- 哪一项检查失败。

API Key 等 Secret 不进入报告。

## 8. 怎么看待模型随机性

这套 6-case 测试是回归检查，不是统计学意义上的模型 benchmark。

历史上，本地小模型曾出现同样配置下 Tool 选择不一致的情况。因此项目保留这样的失败证据，而不是后面偶尔跑一次 6/6，就反过来说模型“100% 稳定”。

所以：

- 后续 PASS 不删除历史 FAIL；
- 单次 6/6 只证明这一轮固定用例通过；
- 不把它包装成模型准确率；
- 当前也没有用 LLM-as-a-Judge 给主观答案打一个看起来很精确的分数。

## 9. 为什么三个 Provider 必须共用同一套题

如果 DeepSeek 用一套题、Ollama 用另一套更简单的题，那么“都通过”没有比较意义。

因此 DeepSeek、Ollama / qwen3:4b、GLM / glm-5.2 统一使用同一个 manifest。

新增 Provider 也不能通过为自己定制更容易的问题获得“已验证”状态。

## 10. Agent 评测和 Policy RAG 评测为什么分开

Agent 评测主要问：

```text
问题理解对不对？
Tool 选对没有？
贷款事实有没有编？
多轮对话还能不能正确查当前数据？
```

Policy RAG 评测主要问：

```text
该不该查政策？
找到了正确条款没有？
政策版本对不对？
没有答案时会不会硬匹配？
引用是否真实？
```

把两者拆开，出问题时才知道应该修 Agent 行为还是 RAG 检索。

Policy RAG 评测见 [POLICY_RAG_EVALUATION.md](POLICY_RAG_EVALUATION.md)。
