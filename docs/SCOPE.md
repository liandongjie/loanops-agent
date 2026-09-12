# LoanOps Agent 项目范围

## 1. 项目定位

LoanOps Agent 是一个面向贷后运营场景的**可审计、可评测、只读智能诊断 Agent**。

它聚焦于贷款还款、逾期、结清与 Policy Evidence 解释：

- 金融事实由确定性的 Java 业务逻辑计算；
- Agent 通过只读 Tool 查询这些事实；
- Policy RAG 为“按照规定应该怎么理解”提供版本化证据；
- Conversation 支持多轮上下文；
- Citation 和 Audit 为模型输出增加可验证边界。

本项目不是完整贷款核心系统，也不是银行生产系统。

## 2. 当前业务范围

当前确定性贷款业务只覆盖：

1. 贷款合同基础数据；
2. 分期还款计划；
3. 实际还款记录；
4. 本期应还金额；
5. 本期已还金额；
6. 本期剩余未还金额；
7. 当前待处理期次；
8. 逾期判断；
9. 逾期天数；
10. 整笔贷款是否结清。

业务规则以 [DOMAIN.md](DOMAIN.md) 为唯一事实来源。

当前核心验证贷款：

```text
LN-10001
LN-10002
LN-10003
```

这些数据都是 synthetic fixture。

## 3. 当前 Agent 能力

### 3.1 只读 Tool Calling

当前只提供：

```text
getCurrentRepayment
getOverdueDiagnosis
getSettlementStatus
```

Tool 必须委托 Java Service，不允许复制贷款业务计算。

### 3.2 持久化 Conversation

当前支持：

- 持久化 USER / ASSISTANT transcript；
- 多轮指代消解；
- 当前事实重新调用 Tool；
- optimistic CAS 防止并发覆盖；
- 失败 turn 不留下伪成功 transcript。

### 3.3 Policy RAG

当前支持：

- MySQL versioned Policy Store；
- BGE-M3 Embedding；
- Qdrant 派生向量索引；
- `NOT_REQUIRED / SUPPLEMENTAL / REQUIRED` Policy Router；
- exact-reference + semantic retrieval；
- 版本适用性过滤；
- `REQUIRED + NO_MATCH` abstain；
- `[P1]`、`[P2]` Citation Validation；
- Policy Retrieval / Hit / Citation Audit；
- 固定 30-case Gold Dataset。

### 3.4 Chat Provider

当前已接入并经过同一套 Provider baseline 与 Policy Hero 路径验证：

- DeepSeek / `deepseek-chat`；
- Ollama / `qwen3:4b`；
- GLM / `glm-5.2`。

Chat Model 与 Policy Embedding 是两个独立配置维度。即使 Chat Provider 使用 DeepSeek / GLM，Policy Embedding 仍可使用本地 Ollama / BGE-M3。

### 3.5 Terminal 与 SSE

当前支持：

- PowerShell Terminal Chat；
- 服务端持久化 Conversation；
- 同步 JSON Agent API；
- SSE streaming Agent API；
- 非 Policy turn 的 provisional 增量输出；
- Policy turn 在 Citation Validation 和成功提交后输出安全结果。

### 3.6 Local Policy Bootstrap

当前提供显式 one-shot Bootstrap：

```powershell
./scripts/bootstrap-local-policy.ps1
```

用于把共享 synthetic Demo Policy 写入普通本地 MySQL，并从 MySQL 重建配置的 Qdrant index。

安全边界：

- 允许空 store、完整 Demo 或可恢复的部分 Demo；
- 遇到未知 / 非 Demo Policy 数据会拒绝执行；
- 没有 `force / reset / delete` 选项；
- 不在普通应用启动时自动 seed；
- 失败后可修复依赖并安全重跑。

## 4. 当前工程基础设施

项目当前包含：

- Java 21；
- Spring Boot；
- Spring AI；
- MyBatis-Plus；
- Flyway；
- H2 deterministic test path；
- MySQL 8 integration path；
- Qdrant；
- 本机 Ollama；
- Docker Compose（MySQL / Qdrant）；
- Micrometer / Actuator；
- GitHub Actions 的 H2 / MySQL 验证路径；
- PowerShell 启动、评测与验收脚本。

## 5. 明确不做的业务功能

当前不实现：

- 客户管理；
- 贷款产品管理；
- 授信审批；
- 放款流程；
- 信用评分；
- 风险定价；
- 催收执行；
- 自动划扣；
- 罚息、复利、宽限期；
- 提前还款；
- 展期；
- 核销执行；
- 多币种；
- 真实银行会计核算。

Policy 中出现“催收、重组、转让、核销”等文本时，Agent 只能解释证据，不能执行这些操作。

## 6. 明确不做的系统能力

当前不实现生产级：

- 前端管理后台；
- 用户中心；
- Authentication / OAuth；
- RBAC；
- 工作流引擎；
- 消息通知；
- 限流平台；
- Provider fallback；
- circuit breaker；
- Kubernetes / cloud deployment；
- TLS / Secrets Manager；
- 高可用集群。

## 7. AI 与 Agent 非目标

当前没有充分需求或评测证据，因此不默认引入：

- Multi-Agent；
- MCP；
- write-capable financial Tools；
- Redis 等 long-term Agent memory；
- Hybrid Search；
- BM25 / RRF；
- Reranker；
- HyDE；
- GraphRAG；
- 大模型训练。

这些不是“永远禁止”，而是需要独立需求、风险分析和验收标准后再决定。

## 8. 安全边界

必须始终保持：

- 金融金额、日期、逾期、结清由确定性 Java Service 计算；
- Tool 只读；
- MySQL 是 Policy source of truth；
- Qdrant 不是 Policy 最终事实源；
- Conversation 不是当前金融事实源；
- Citation Validator 独立验证 Policy 引用；
- synthetic fixture 不得描述为真实银行数据；
- 固定评测指标不得描述为 production accuracy。

## 9. 项目完成标准

本项目的目标不是堆叠更多 Agent 概念，而是在有限范围内做到：

> 业务事实可验证、Agent 行为可观察、Policy Evidence 可追溯、失败语义可解释、评测可以复现。

当前验收标准见 [ACCEPTANCE.md](ACCEPTANCE.md)。

历史 MVP、Phase 及开发过程见 [history/](history/)，历史记录不定义当前范围。
