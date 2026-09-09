# AI Provider Boundary

## 1. 当前结论

当前版本把 **DeepSeek**、**Ollama / qwen3:4b** 和 **GLM / glm-5.2** 标记为已通过真实 Tool Calling E2E 验证的 Chat Provider。

| Provider | 接入方式 | 当前状态 | 需要的验收 |
|---|---|---|---|
| DeepSeek | Spring AI DeepSeek ChatModel（`deepseek-chat`） | FULLY VERIFIED | 统一 6-case baseline + Policy Hero |
| Ollama | Spring AI Ollama ChatModel（`qwen3:4b`） | FULLY VERIFIED | 统一 6-case baseline + Policy Hero |
| GLM | Spring AI ZhiPuAI ChatModel（`glm-5.2`） | FULLY VERIFIED | 统一 6-case baseline + Policy Hero |

## 2. Provider 不应该影响什么

更换 Provider 时，下列代码原则上不应修改：

- `RepaymentCalculator`；
- `LoanDiagnosisService`；
- `LoanStatusService` 的业务编排；
- `LoanOpsTools` 的业务语义；
- H2 fixture 与领域测试；
- 普通 REST API。

Provider 的变化只应落在 Spring AI 模型依赖、连接配置和少量 Provider 特有参数上。

## 3. A1 配置契约

A1 只建立配置基础设施，不代表新增 Provider 已接入或已验证。

```text
LOANOPS_CHAT_PROVIDER = deepseek | ollama | glm
LOANOPS_CHAT_ADAPTER  = deepseek | ollama | zhipuai
LOANOPS_CHAT_MODEL    = 实际模型名称
```

Provider 与 Spring AI ChatModel Adapter 的固定映射为：

```text
deepseek -> deepseek
ollama  -> ollama
glm     -> zhipuai
```

默认值仍为 `deepseek / deepseek / deepseek-chat`。项目根目录的 `.env` 通过 Spring Boot Config Data 作为 properties 文件加载；命令行、系统属性和操作系统环境变量仍按 Spring Boot 原生优先级覆盖它。`.env` 保持 gitignored，示例文件只保存安全 placeholder。

Chat 的 provider / adapter / model 与 Policy Embedding 的 `ollama / bge-m3` 是两个独立配置维度。

## 4. DeepSeek

当前配置：

```text
DEEPSEEK_API_KEY
LOANOPS_CHAT_PROVIDER=deepseek
LOANOPS_CHAT_ADAPTER=deepseek
LOANOPS_CHAT_MODEL=deepseek-chat
```

Spring profile：

```text
ai
```

真实验收必须覆盖：

```text
LN-10001 -> getCurrentRepayment
LN-10002 -> getOverdueDiagnosis
LN-10003 -> getSettlementStatus
```

## 5. Ollama / qwen3:4b

当前 A2 运行身份为：

```text
Provider = ollama
Adapter  = ollama
Model    = qwen3:4b
```

该路径已完成 A4 统一 6-case baseline 与 Policy Hero。实际 Agent Audit 为 `provider=ollama`、`model=qwen3:4b`；Policy retrieval audit 的 embedding model 仍为 `bge-m3`。历史 unchanged-config 运行曾观察到 Tool-selection variance；本轮单次 6/6 只是一份回归证据，不代表统计稳定性结论。

Alibaba Model Studio / OpenAI-compatible Qwen 仅保留为 future alternative，不是当前 A2 实施路径。若未来单独批准该路径，再评估以下配置：

```text
QWEN_API_KEY / DASHSCOPE_API_KEY
QWEN_BASE_URL
QWEN_MODEL
```

官方参考：

- https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope
- https://www.alibabacloud.com/help/en/model-studio/qwen-function-calling

## 6. GLM / glm-5.2

当前 A3 运行身份为：

```text
Provider = glm
Adapter  = zhipuai
Model    = glm-5.2
```

项目使用 Spring AI 1.1.1 原生 `ZhiPuAiChatModel` 和智谱标准开放平台 endpoint；`GLM_API_KEY` 是唯一新增的 Provider Secret，model 继续由 `LOANOPS_CHAT_MODEL` 控制。该路径已完成 A4 统一 6-case baseline 与 Policy Hero，实际 Agent Audit 为 `provider=glm`、`model=glm-5.2`，Policy retrieval audit 的 embedding model 仍为 `bge-m3`。

不同时保留 GLM OpenAI-compatible adapter；`glm / openai` 会在配置边界 fail-fast。

官方参考：

- https://docs.bigmodel.cn/api-reference/模型-api/对话补全
- https://docs.bigmodel.cn/cn/guide/models/text/glm-5.2

## 7. A4 Final Regression Matrix

以下是 2026-09-09 在同一 A4 working tree（基于 `98ae325`）上形成的当前本地回归证据。Baseline 使用同一六用例 manifest、固定业务日期和时区；Policy Hero 使用同一个 `PolicyAgentRealE2EIntegrationTest`。这些是有限样本的回归结果，不是性能、质量排名或统计稳定率。

| Provider | Adapter | Model | Baseline | Unknown | Stateful | Policy Hero | Audit identity | Known variance |
|---|---|---|---|---|---|---|---|---|
| deepseek | deepseek | deepseek-chat | 6/6 PASS | PASS | PASS | PASS | 一致 | 本轮未观察到；单次运行不证明稳定 |
| ollama | ollama | qwen3:4b | 6/6 PASS | PASS | PASS | PASS | 一致 | 历史曾观察 Tool-selection variance；本轮首次 6/6 |
| glm | zhipuai | glm-5.2 | 6/6 PASS | PASS | PASS | PASS | 一致 | 本轮未观察到；单次运行不证明稳定 |

三组 Hero 的 embedding audit 均为 `ollama / bge-m3`，没有被 Chat model 覆盖。首次 DeepSeek Hero 尝试在模型调用前因 Docker engine 未运行而 ENV_BLOCKED；恢复既有 MySQL/Qdrant 后三组 Hero 均 PASS。

## 8. 验收原则

任何新 Provider 必须复用同一套业务事实，并至少通过：

1. Case A：应还 8500，未逾期；
2. Case B：应还 8500、已还 5000、剩余 3500、逾期 3 天；
3. Case C：已结清、未结清金额 0；
4. 不存在贷款不编造；
5. 写请求不改变数据库；
6. 日志能证明模型确实发起 Tool Calling，而不是靠 Prompt 猜答案。

只有全部通过，README 才能把 Provider 状态改成“已验证”。
