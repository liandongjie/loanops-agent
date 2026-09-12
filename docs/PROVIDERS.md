# LoanOps Agent 模型 Provider

## 1. 当前支持

当前 Chat Provider：

| Provider | Spring AI Adapter | Model | Tool Calling Baseline | Policy Hero |
|---|---|---|---|---|
| DeepSeek | deepseek | `deepseek-chat` | 已验证 | 已验证 |
| Ollama | ollama | `qwen3:4b` | 已验证 | 已验证 |
| GLM | zhipuai | `glm-5.2` | 已验证 | 已验证 |

这些状态表示已经通过当前仓库定义的有限回归 Gate，不代表生产稳定率、模型排名或无限场景兼容性。

## 2. Chat Model 与 Policy Embedding 分离

项目把两个概念明确分开：

```text
Chat Provider
    -> DeepSeek / Ollama(qwen3:4b) / GLM
    -> 理解问题、Tool Calling、组织回答

Policy Embedding
    -> Ollama / bge-m3
    -> Policy Query / Chunk 向量化
```

因此：

- 切换 Chat Provider 不应改变金融业务 Service；
- 切换 Chat Provider 不应改变 Policy corpus；
- Chat Provider 使用 DeepSeek / GLM 时，Policy Embedding 仍可以是本地 `bge-m3`；
- Audit 必须分别记录实际 Chat Provider/model 与 Policy embedding model。

## 3. 配置契约

Chat Provider 通过：

```text
LOANOPS_CHAT_PROVIDER
LOANOPS_CHAT_ADAPTER
LOANOPS_CHAT_MODEL
```

当前固定映射：

```text
deepseek -> deepseek
ollama   -> ollama
glm      -> zhipuai
```

默认身份：

```text
deepseek / deepseek / deepseek-chat
```

项目根目录的本地 `.env` 通过 Spring Boot Config Data 加载，并保持 gitignored。

Secret：

```text
DEEPSEEK_API_KEY
GLM_API_KEY
```

Ollama 默认通过本机服务访问。

命令行 / 系统属性 / 操作系统环境变量仍遵循 Spring Boot 原生配置优先级。

## 4. 本地启动

推荐使用仓库 launcher，而不是手工拼 profile 和系统属性：

```powershell
./scripts/run-agent.ps1 -Provider deepseek
./scripts/run-agent.ps1 -Provider ollama
./scripts/run-agent.ps1 -Provider glm
```

默认 launcher 关闭 Policy RAG。

启用完整 Policy RAG：

```powershell
./scripts/run-agent.ps1 -Provider deepseek -WithPolicy
```

或：

```powershell
./scripts/run-agent.ps1 -Provider ollama -WithPolicy
./scripts/run-agent.ps1 -Provider glm -WithPolicy
```

首次在普通本地 MySQL / Qdrant 体验 Demo Policy 时，先执行：

```powershell
./scripts/bootstrap-local-policy.ps1
```

## 5. DeepSeek

当前身份：

```text
Provider = deepseek
Adapter  = deepseek
Model    = deepseek-chat
```

需要：

```text
DEEPSEEK_API_KEY
```

Provider 负责：

- 自然语言理解；
- Tool Calling；
- 基于 Tool / Policy Context 组织回答。

金融事实仍由 Java Service 计算。

## 6. Ollama / qwen3:4b

当前身份：

```text
Provider = ollama
Adapter  = ollama
Model    = qwen3:4b
```

需要本地 Ollama 可访问并安装：

```powershell
ollama pull qwen3:4b
```

历史回归曾观察到本地模型 Tool-choice variance，因此单次 6/6 PASS 只能作为回归证据，不能表述为统计稳定率。

`qwen3:4b` 是 Chat Model；`bge-m3` 是 Embedding Model，两者用途不同。

## 7. GLM / glm-5.2

当前身份：

```text
Provider = glm
Adapter  = zhipuai
Model    = glm-5.2
```

需要：

```text
GLM_API_KEY
```

项目使用 Spring AI 原生 ZhiPuAI adapter，而不是同时维护第二套 OpenAI-compatible GLM adapter。

## 8. Provider 不应该影响什么

切换 Provider 时，原则上不应修改：

- `RepaymentCalculator`；
- `LoanDiagnosisService`；
- `LoanStatusService`；
- `LoanOpsTools` 业务语义；
- `DOMAIN.md`；
- H2 / MySQL 固定贷款 fixture；
- REST API 的金融事实；
- Policy corpus 与适用性规则。

Provider 边界只应影响模型 adapter、连接配置与必要的模型参数。

## 9. 验收标准

任何新增 Chat Provider 至少需要复用同一套业务事实，并验证：

1. 当前还款问题选择正确 Tool；
2. 逾期问题选择正确 Tool；
3. 结清问题选择正确 Tool；
4. unknown loan 不编造金额与天数；
5. 写请求不改变数据库；
6. stateful follow-up 能解析上下文，同时重新查询当前 Tool；
7. Audit 能证明实际 Provider/model 和 Tool Calling；
8. 如果声称支持 Policy RAG，还需要通过同一 Policy Hero。

统一 runner：

```powershell
./scripts/evaluate-agent-baseline.ps1 -Provider deepseek
./scripts/evaluate-agent-baseline.ps1 -Provider ollama
./scripts/evaluate-agent-baseline.ps1 -Provider glm
```

更多说明见 [EVALUATION.md](EVALUATION.md)。
