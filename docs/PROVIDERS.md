# LoanOps Agent 模型 Provider

这份文档回答三个问题：**当前支持哪些 Chat Model、怎么切换，以及为什么换模型不会改变贷款业务规则。**

## 1. 当前支持

| Chat Provider | Spring AI Adapter | 默认模型 | 当前仓库验收路径 |
|---|---|---|---|
| DeepSeek | deepseek | `deepseek-chat` | 6 个 Agent 固定用例 + Policy RAG 端到端测试 |
| Ollama | ollama | `qwen3:4b` | 6 个 Agent 固定用例 + Policy RAG 端到端测试 |
| GLM | zhipuai | `glm-5.2` | 6 个 Agent 固定用例 + Policy RAG 端到端测试 |

这里的“已验证”只表示通过当前仓库定义的有限用例，不代表生产稳定率或模型能力排名。

## 2. Chat Model 和 BGE-M3 不是一回事

项目里有两类模型：

```text
Chat Model
    DeepSeek / qwen3:4b / GLM
    负责理解问题、选择 Tool、组织回答

Embedding Model
    BGE-M3
    负责把政策条款和查询转换成向量，供 Qdrant 检索
```

所以把 Chat Model 从 DeepSeek 换成 GLM，不代表 Policy RAG 也要换 Embedding Model。

这两个配置维度彼此独立，Audit 也会分别记录实际 Chat Model 和 Policy Embedding model。

## 3. 为什么要支持多个 Chat Model

目的不是简单增加“支持模型数量”，而是验证 Agent 的业务边界是否真的独立于某一个模型。

三个模型使用：

- 同一套 Java 业务规则；
- 同一组只读 Tools；
- 同一套贷款演示数据；
- 同一套 6 个 Agent 测试用例；
- 同一套 Policy RAG 端到端测试。

如果换一个模型以后金额计算方式也跟着变了，说明架构边界就是错的。

## 4. 配置方式

Chat Model 由下面三项配置：

```text
LOANOPS_CHAT_PROVIDER
LOANOPS_CHAT_ADAPTER
LOANOPS_CHAT_MODEL
```

当前映射：

```text
deepseek -> deepseek -> deepseek-chat
ollama   -> ollama   -> qwen3:4b
glm      -> zhipuai  -> glm-5.2
```

DeepSeek / GLM 的 Key：

```text
DEEPSEEK_API_KEY
GLM_API_KEY
```

可以放在仓库根目录本地 `.env` 中；该文件保持 gitignored。

## 5. 推荐启动方式

不建议手工拼一长串 Spring profile 和系统参数，直接使用仓库 launcher：

```powershell
./scripts/run-agent.ps1 -Provider deepseek
./scripts/run-agent.ps1 -Provider ollama
./scripts/run-agent.ps1 -Provider glm
```

默认只启动业务 Agent，不开启 Policy RAG。

需要 Policy RAG：

```powershell
./scripts/run-agent.ps1 -Provider deepseek -WithPolicy
```

`ollama` 和 `glm` 同样支持 `-WithPolicy`。

首次本地体验 Policy RAG 时，先执行：

```powershell
./scripts/bootstrap-local-policy.ps1
```

## 6. 切换模型以后什么不能变

更换 Chat Provider 不应该修改：

- `RepaymentCalculator`；
- `LoanDiagnosisService`；
- `LoanStatusService`；
- `LoanOpsTools` 的业务含义；
- `DOMAIN.md`；
- 固定贷款数据；
- REST API 的金额和状态；
- Policy corpus 和版本适用规则。

换模型应该只影响模型 adapter、连接配置和必要的模型参数。

## 7. 怎么验证一个新 Provider 真的能用

任何新 Provider 至少要用同一套业务事实验证：

1. “本期应该还多少钱”能选对 Tool；
2. “为什么逾期”能选对 Tool；
3. “是否已经结清”能选对 Tool；
4. 查询不存在的贷款时不编造金额和逾期天数；
5. 用户要求修改贷款状态时不会写数据库；
6. 多轮追问能识别上一轮贷款，同时重新查询当前状态；
7. Audit 能证明实际用了哪个 Provider、哪个模型和哪个 Tool；
8. 如果声称支持 Policy RAG，还要通过同一套真实端到端测试。

统一命令：

```powershell
./scripts/evaluate-agent-baseline.ps1 -Provider deepseek
./scripts/evaluate-agent-baseline.ps1 -Provider ollama
./scripts/evaluate-agent-baseline.ps1 -Provider glm
```

测试为什么这样设计见 [EVALUATION.md](EVALUATION.md)。
