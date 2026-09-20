# LoanOps Agent 项目范围

这份文档只回答两个问题：**项目现在做什么，以及明确不做什么。**

具体实现方式看 [系统架构](ARCHITECTURE.md)，贷款计算规则看 [DOMAIN.md](DOMAIN.md)。

## 1. 项目定位

LoanOps Agent 是一个面向贷后运营场景的只读智能诊断 Agent。

当前主要处理三类事情：

1. 查询一笔贷款当前应该还多少、已经还了多少、还欠多少；
2. 解释为什么逾期、逾期多少天、整笔贷款是否结清；
3. 在用户问“按照规定怎么办”时，查询 Policy 知识库并给出可以追溯到具体条款的依据。

本项目不是完整贷款核心系统，也不是银行生产系统。

## 2. 当前贷款业务范围

当前只实现：

- 贷款合同基础数据；
- 分期还款计划；
- 实际还款记录；
- 本期应还金额；
- 本期已还金额；
- 本期剩余未还金额；
- 当前待处理期次；
- 逾期判断；
- 逾期天数；
- 整笔贷款是否结清。

固定演示贷款：

```text
LN-10001
LN-10002
LN-10003
```

这些都是人为构造的测试数据。

## 3. 当前 Agent 能力

### 只读业务查询

三个 Tool：

```text
getCurrentRepayment
getOverdueDiagnosis
getSettlementStatus
```

它们只能查询，不能修改贷款、还款计划或付款记录。

### 多轮对话

Agent 可以理解上一轮讨论的是哪笔贷款，例如从：

```text
LN-10002 为什么逾期？
```

继续理解：

```text
那他现在还欠多少钱？
```

但只要问题涉及当前贷款状态，仍会重新查询 Tool，而不是直接复用旧回答。

### Policy RAG

Agent 可以判断问题是否需要政策依据，需要时检索 MySQL 中当前适用的政策版本，并通过 BGE-M3 + Qdrant 找到相关条款。

如果问题必须依赖政策回答、但知识库没有可靠依据，系统会明确返回无法确认。

回答里的 `[P1]`、`[P2]` 必须对应本轮真实检索到的条款。

### 多模型

当前 Chat Model 支持：

- DeepSeek / `deepseek-chat`；
- Ollama / `qwen3:4b`；
- GLM / `glm-5.2`。

Policy Embedding 独立使用 BGE-M3，因此更换 Chat Model 不需要更换政策向量模型。

### Terminal 与 SSE

项目支持：

- PowerShell Terminal Chat；
- 服务端持久化 Conversation；
- 同步 JSON API；
- SSE 流式响应。

### 本地 Demo Policy 初始化

通过：

```powershell
./scripts/bootstrap-local-policy.ps1
```

可以把演示 Policy 初始化到普通本地 MySQL，并重建 Qdrant 索引。

脚本可以安全重复执行；如果发现不认识的 Policy 数据，会拒绝覆盖，而不是直接清空数据库。

## 4. 当前工程基础设施

项目当前使用：

- Java 21；
- Spring Boot / Spring AI；
- MyBatis-Plus；
- Flyway；
- H2；
- MySQL 8；
- Qdrant；
- Ollama；
- Docker Compose；
- Micrometer / Actuator；
- GitHub Actions；
- PowerShell 启动和评测脚本。

## 5. 明确不做的贷款业务

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

Policy 中即使出现“催收、重组、转让、核销”等文字，Agent 也只能解释政策，不能执行这些动作。

## 6. 明确不做的生产能力

当前没有实现生产级：

- 前端管理后台；
- 用户中心；
- Authentication / OAuth / RBAC；
- 工作流引擎；
- 消息通知；
- 限流平台；
- Provider 自动 fallback；
- Kubernetes / cloud deployment；
- TLS / Secrets Manager；
- 高可用集群。

## 7. 当前没有引入的 Agent / RAG 组件

当前没有因为“Agent 项目应该看起来更复杂”而默认加入：

- Multi-Agent；
- MCP；
- 可写金融 Tool；
- Redis long-term memory；
- Hybrid Search；
- BM25 / RRF；
- Reranker；
- HyDE；
- GraphRAG；
- 大模型训练。

这些能力以后如果真的有需求，可以单独评估；当前不为了堆技术栈加入。

## 8. 始终保持的边界

无论以后怎么增强，当前设计都要求：

- 金融金额、日期、逾期和结清由 Java Service 计算；
- Agent Tool 保持只读；
- Policy 原文和版本信息以 MySQL 为准；
- Qdrant 只负责检索，不是最终政策事实来源；
- Conversation 只负责上下文，不是当前贷款事实来源；
- 政策引用必须校验；
- 人为构造的演示数据不能描述成真实银行数据；
- 固定测试集结果不能描述成生产准确率。

当前验收要求见 [ACCEPTANCE.md](ACCEPTANCE.md)。历史开发过程见 [history/](history/)。
