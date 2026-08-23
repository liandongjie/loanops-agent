# AI Provider Boundary

## 1. 当前结论

当前版本只把 **DeepSeek** 标记为“已接入且通过真实 Tool Calling E2E 验证”的 Provider。

Qwen 和 GLM 只定义接入边界，不在没有真实验收的情况下写成“已支持”。

| Provider | 接入方式 | 当前状态 | 需要的验收 |
|---|---|---|---|
| DeepSeek | Spring AI DeepSeek ChatModel | 已接入 | 已完成 3 个固定 Agent Case |
| Qwen | OpenAI-compatible Chat API（计划） | 未接入 | 3 Tool + 3 Agent Case + 异常/只读 Case |
| GLM | OpenAI-compatible Chat API（计划） | 未接入 | 3 Tool + 3 Agent Case + 异常/只读 Case |

## 2. Provider 不应该影响什么

更换 Provider 时，下列代码原则上不应修改：

- `RepaymentCalculator`；
- `LoanDiagnosisService`；
- `LoanStatusService` 的业务编排；
- `LoanOpsTools` 的业务语义；
- H2 fixture 与领域测试；
- 普通 REST API。

Provider 的变化只应落在 Spring AI 模型依赖、连接配置和少量 Provider 特有参数上。

## 3. DeepSeek

当前配置：

```text
DEEPSEEK_API_KEY
DEEPSEEK_MODEL=deepseek-chat
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

## 4. Qwen 扩展路径

阿里云 Model Studio 提供 OpenAI-compatible Chat API，并支持 Function Calling。接入时优先复用 Spring AI OpenAI ChatModel，而不是新增一个 `QwenLoanOpsAgentService`。

建议 Provider 配置字段：

```text
QWEN_API_KEY / DASHSCOPE_API_KEY
QWEN_BASE_URL
QWEN_MODEL
```

接入完成前，不把 Qwen 写入“Supported Providers”。

官方参考：

- https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope
- https://www.alibabacloud.com/help/en/model-studio/qwen-function-calling

## 5. GLM 扩展路径

智谱 Chat Completions API 使用 Bearer API Key，GLM 系列支持 Function Calling。后续同样优先复用 OpenAI-compatible Adapter，不复制 Agent 业务层。

建议 Provider 配置字段：

```text
GLM_API_KEY
GLM_BASE_URL=https://open.bigmodel.cn/api/paas/v4
GLM_MODEL
```

部分 GLM 模型/模式存在工具调用特有参数，接入时必须以所选模型当期官方文档为准，并通过真实 E2E 验证，不能只以“接口兼容”推断 Tool Calling 一定兼容。

官方参考：

- https://docs.bigmodel.cn/api-reference/模型-api/对话补全
- https://docs.bigmodel.cn/cn/guide/models/text/glm-5.2

## 6. 验收原则

任何新 Provider 必须复用同一套业务事实，并至少通过：

1. Case A：应还 8500，未逾期；
2. Case B：应还 8500、已还 5000、剩余 3500、逾期 3 天；
3. Case C：已结清、未结清金额 0；
4. 不存在贷款不编造；
5. 写请求不改变数据库；
6. 日志能证明模型确实发起 Tool Calling，而不是靠 Prompt 猜答案。

只有全部通过，README 才能把 Provider 状态改成“已验证”。
