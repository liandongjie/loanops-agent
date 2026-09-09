# LoanOps Agent

LoanOps Agent 是一个面向贷后运营场景的**可审计、可评测、只读智能诊断 Agent**。

它把两类事实源严格分开：贷款金额、日期、逾期和结清状态由确定性 Java 服务计算，并通过只读 Tools 提供；政策依据来自版本化 MySQL canonical store，经 BGE-M3 向量化、Qdrant 检索、上下文组装和引用校验后提供给模型。LLM 只负责语言理解、Tool 选择和回答组织，不自行计算金融事实，也不是政策事实来源。

仓库提供完整的可复现实现、自动测试、固定评测集，以及 DeepSeek、Ollama/qwen3:4b、GLM/glm-5.2 共用的真实端到端验收路径。

## 1. Project Overview

项目回答两类贷后问题：

- **Financial Facts**：本期应还、已还、未还、到期日、逾期状态、逾期天数、整笔贷款是否结清；
- **Policy Evidence**：当前适用政策对处置、手续或流程的规定，并给出可审计的 [P1]、[P2] 引用。

核心能力包括：

- Java 21、Spring Boot、MyBatis-Plus、Flyway；
- H2 默认验证路径与 MySQL 8 集成路径；
- 3 个只读 Spring AI Tools；
- 可配置的 DeepSeek、Ollama/qwen3:4b、GLM/glm-5.2 Tool Calling 与持久化多轮 Conversation；
- deterministic Policy Router、BGE-M3 embeddings、Qdrant derived vector index；
- Policy Citation Validation、Agent / Tool / Policy Audit；
- 固定 Agent baseline、30-case Policy RAG Gold Dataset 和真实 Hero E2E。

## 2. Why Agent / Trust Boundaries

Agent 的价值是把自然语言问题映射到正确的只读能力，并把不同事实源组合成可读回答；它不替代业务规则引擎。

| 内容 | 权威来源 | LLM 的角色 |
|---|---|---|
| 金额、日期、期次、逾期、结清 | Java deterministic services + 当前贷款数据 | 选择 Tool、解释 Tool 返回值 |
| 政策版本、条款与适用证据 | MySQL versioned policy store | 基于检索上下文组织政策回答并引用 |
| 向量相似度候选 | Qdrant derived index | 不直接接触；由 Retriever 过滤和组装 |
| 对话指代 | 持久化 USER / ASSISTANT transcript | 用于理解上下文；当前金融事实仍须重新调用 Tool |

以下内容不得交给 LLM 自行计算或猜测：金额、日期、逾期状态、当前贷款状态。Tool 不复制领域算法，只委托现有 Java Service。

## 3. Hero Flow

现有 PolicyAgentRealE2EIntegrationTest 是项目的 Hero evidence，不另建重复验收子系统。它使用真实 MySQL、Ollama/BGE-M3、Qdrant 和当前选择的 Chat Provider，执行两轮对话：

1. LN-10002 为什么逾期？
   - Agent 调用 getOverdueDiagnosis；
   - 金融事实来自 Java Tool；
   - 成功 turn 写入 Conversation。
2. 按照规定现在应该怎么处理？
   - 从先前 USER 消息解析 LN-10002；
   - Policy decision 为 SUPPLEMENTAL；
   - Policy RAG 为 MATCHED；
   - 命中适用政策条款，回答包含有效 [P1]；
   - 检索、命中、引用、Agent 和 Tool 均留下 Audit；
   - transcript 只包含 USER / ASSISTANT。

测试只断言稳定 invariants，不固定任一 Provider 的整段自然语言；模型措辞不保证每次一致。

## 4. Architecture

~~~mermaid
flowchart TD
    Client --> API[Agent API]
    API --> Snapshot[Conversation Snapshot]
    Snapshot --> Router[Policy Router]
    Router --> Decision[Policy Decision]

    Decision -->|NOT_REQUIRED| LLM[Selected Chat Provider]
    LLM --> Tools[LoanOpsTools]
    Tools --> Java[Deterministic Java Services]
    Java --> LoanDB[(MySQL / H2 loan data)]

    Decision -->|SUPPLEMENTAL / REQUIRED| Query[Policy Query]
    Query --> PolicyDB[(MySQL canonical policy store<br/>applicable versions and chunks)]
    PolicyDB --> Exact[Exact-reference candidates]
    Query --> Embed[BGE-M3 query embedding]
    Embed --> Vector[(Qdrant rebuildable derived index)]
    Vector --> Semantic[Semantic candidates]
    Exact --> Merge
    Semantic --> Merge
    Merge[Retriever merge / rank / applicability filtering] --> RequiredNoMatch{REQUIRED + NO_MATCH?}
    RequiredNoMatch -->|Yes| Abstain[Return no-match notice<br/>no model call]
    RequiredNoMatch -->|No| Context[Grounding Context]
    Context --> LLM
    Abstain --> Citation
    LLM --> Citation[PolicyCitationValidator]
    Citation --> Answer[Answer]

    Answer --> Commit[Conversation commit]
    Answer --> Audit[Agent / Tool / Policy Audit]
~~~

MySQL 保存 canonical policy document/version/chunk metadata，是政策事实来源；Qdrant 只保存可从 MySQL 重建的向量索引，不是 policy source of truth。H2 用于默认本地测试；真实 Policy RAG 和 Hero 门禁使用 MySQL。

详细职责、失败语义和数据边界见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 5. Key Engineering Decisions

- **Java owns financial truth**：BigDecimal 处理金额，Clock 提供可注入业务日期；REST 可绕过 AI 独立验证同一事实。
- **Read-only Tools**：getCurrentRepayment、getOverdueDiagnosis、getSettlementStatus 只调用业务 Service。
- **Conversation is context, not truth**：完整成功 transcript 持久化；模型可见历史限制为 20 条消息和 12,000 字符，当前事实必须 fresh Tool call。
- **Version-aware policy evidence**：政策文档、版本、有效期和 chunk 元数据落 MySQL；检索按业务日期筛选适用版本。
- **Deterministic citation gate**：需要引用时，缺失或未知引用由 PolicyCitationValidator 拒绝，不依赖 Prompt 自律。
- **Auditable execution**：服务端 request id 关联 Agent、Tool、Conversation 和 Policy Retrieval Audit；默认只保存长度与 SHA-256 指纹，不保存 prompt/answer 原文。
- **Bounded runtime**：消息和历史有上限，外部 HTTP 有有限 timeout，Spring AI max-attempts = 1（不进行自动重试）；required-policy 失败 fail closed，supplemental-policy 失败可保留金融事实并附加提示。

### Deliberate Non-goals / Design Trade-offs

- **No Multi-Agent**：当前问题没有自然的角色拆分，增加协作协议只会扩大不确定性与审计面。
- **No MCP**：现有 Tools 都是同一 Java 进程内能力，没有跨进程或跨应用共享边界。
- **No long-term Memory**：金融事实必须来自当前数据库和 fresh Tool；陈旧记忆不能成为金融事实源。
- **No Hybrid Search / Reranker**：E1 后固定语料的直接 Retriever 指标未显示排序瓶颈，没有评测证据支持增加 BM25、RRF 或 reranker。
- **No write Tools**：权限、确认、幂等、恢复和人工审批会实质扩大金融安全边界；当前 Agent 结构性只读。

## 6. Evaluation Results

以下结果只适用于固定、手工审阅的 **30-case Policy RAG Gold Dataset** 和对应 synthetic corpus，不代表生产准确率：

| Metric | E0 | E1 | E2 |
|---|---:|---:|---:|
| Router Accuracy | 0.8333 | 0.8333 | 1.0000 |
| Policy-required Recall | 0.8077 | 0.8077 | 1.0000 |
| ExactReferenceAccuracy | 0.7500 | 1.0000 | 1.0000 |
| NoMatchAccuracy | 0.0000 | 1.0000 | 1.0000 |
| FalseMatchCount | 3 | 0 | 0 |

E2 direct Retriever metrics：Recall@1/3/5 = 1.0000，MRR = 1.0000。E1 先修正 exact-reference 约束并把 threshold 调整为 0.60；E2 只修 Router，未改变 Retriever、embedding、topK 或 threshold。

真实 DeepSeek adversarial review 为 **4/5 model-output semantic PASS**。ADV-04 中，恶意政策证据诱导模型省略了必需的 [P1]；该 case 不能记为 PASS，但 deterministic PolicyCitationValidator 拒绝了缺失引用的回答，阻止其提交为成功 transcript。这说明防线有效，不代表 prompt injection 或 hallucination 已解决。

六例 provider-neutral live Agent baseline 是小型回归门禁，不具统计显著性；历史运行曾在未改配置时出现 Tool-choice variance，因此单次 6/6 不能表述为稳定率。

完整定义、E0/E1/E2 报告和历史 bad cases 见 [docs/EVALUATION.md](docs/EVALUATION.md) 与 [docs/POLICY_RAG_EVALUATION.md](docs/POLICY_RAG_EVALUATION.md)。

## 7. Quick Start / Reproduction

所有命令从仓库根目录用 PowerShell 7 执行。先确认 Java 21：

~~~powershell
java -version
mvn -version
~~~

### Level 1 — deterministic local verification

~~~powershell
mvn clean verify
./scripts/verify-resume-mvp.ps1
~~~

mvn clean verify 运行默认 H2 自动测试。verify-resume-mvp.ps1 仍是早期 DeepSeek smoke：它会打包并启动临时 JAR，以固定业务日期验证三笔 loan fixture，只有显式传入 -WithAi 时才调用 AI。统一的三 Provider 回归请使用 evaluate-agent-baseline.ps1；前者不覆盖 Policy RAG、BGE-M3、Qdrant、政策引用或 Hero E2E。

### Level 2 — local infrastructure

~~~powershell
docker compose up -d mysql qdrant
docker compose ps
ollama pull bge-m3
~~~

Compose 提供 MySQL 8 和 Qdrant v1.15.4。Ollama 由本机安装提供，不在 Compose 中；默认地址和模型可由 .env.example 中的变量覆盖。

真实 MySQL/Flyway 路径可单独验证：

~~~powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
./scripts/verify-mysql.ps1
~~~

### Terminal Chat

先按现有方式启动启用了 `ai` profile 的 Agent，再在另一个 PowerShell 7 终端运行：

~~~powershell
./scripts/chat.ps1
# 非默认端口：
./scripts/chat.ps1 -BaseUrl "http://127.0.0.1:8080"
~~~

`/new` 只清空客户端当前 conversation ID，`/id` 显示当前 ID，`/exit` 退出。Terminal 通过 `POST /api/agent/chat` 使用服务端持久化的 Conversation，不保存或重发本地历史。Provider 由已启动服务的配置决定，Terminal 不选择 Provider 或管理 secret。当前调用为同步完整响应，不是 token streaming。

### Level 3 — real Agent + Policy Hero E2E

Provider baseline 使用同一份六用例 manifest，可按固定身份运行：

~~~powershell
./scripts/evaluate-agent-baseline.ps1 -Provider deepseek
./scripts/evaluate-agent-baseline.ps1 -Provider ollama
./scripts/evaluate-agent-baseline.ps1 -Provider glm
~~~

DeepSeek 和 GLM 分别需要当前进程中的 DEEPSEEK_API_KEY、GLM_API_KEY；Ollama 需要可访问的服务和 qwen3:4b。三 Provider 的 Policy Hero 还需要健康的 MySQL/Qdrant、Ollama/bge-m3，并通过 LOANOPS_CHAT_PROVIDER、LOANOPS_CHAT_ADAPTER、LOANOPS_CHAT_MODEL 选择运行身份。完整可复制命令见 docs/RUNBOOK.md。

PolicyAgentRealE2EIntegrationTest 会自行 ingest synthetic policy、重建隔离 collection，并覆盖 Tool + Conversation + Policy RAG + Citation + Audit，结束后清理 fixture。缺少 key、model 或基础设施时只能报告 ENV_BLOCKED，不能将未执行或 skipped 记为 PASS。

### Policy evaluation

Prerequisites：Java 21、Docker MySQL/Qdrant、Ollama + bge-m3。脚本使用隔离的 schema/collection 并在结束时清理：

~~~powershell
./scripts/evaluate-policy-rag.ps1 -Label e0 -Threshold 0.0
./scripts/evaluate-policy-rag.ps1 -Label e1 -Threshold 0.60
./scripts/evaluate-policy-rag.ps1 -Label e2 -Threshold 0.60
~~~

只有需要真实 DeepSeek 人工审阅时才设置 DEEPSEEK_API_KEY 并加 -ManualReview。更多说明见 [docs/RUNBOOK.md](docs/RUNBOOK.md)。

## 8. Failure / Safety Boundaries

- REQUIRED + NO_MATCH 不调用模型编造政策，返回明确无法确认的提示；
- required-policy 检索失败会使请求失败；supplemental-policy 检索失败不覆盖已取得的金融事实；
- 需要政策依据却缺少或引用未知 [Pn] 时，Citation Validator fail closed；
- Provider、Tool、Citation 或 Conversation commit 失败不会留下伪成功 turn；
- Conversation 使用 optimistic CAS，冲突请求失败而不是覆盖并发更新；
- Agent 没有写 Tool，不能修改贷款、还款计划、付款记录或状态；
- Audit 查询目前无 RBAC，只适合本地验证，不能裸露为生产接口。

## 9. Known Limitations

- 未作为真实银行生产服务部署，也不包含真实银行生产数据；
- 没有 RBAC、OAuth、rate limit、provider fallback 或 circuit breaker；
- DeepSeek/deepseek-chat、Ollama/qwen3:4b、GLM/glm-5.2 已通过同一六用例 baseline 与 Policy Hero；这仍是有限本地验收，不代表生产稳定率；
- 模型措辞和 Tool 选择具有随机性，固定评测集规模有限；
- loan fixture 与 policy evaluation corpus 均为 synthetic validation data，不包含真实银行生产数据；
- synthetic policy corpus 只用于验证版本、检索、引用与审计链路，不证明真实法规覆盖；
- 不包含授信审批、评分、放款、催收执行、罚息、提前还款等业务；
- CI 只覆盖 H2 full Maven verification 和 MySQL integration verification；Ollama、BGE-M3、Qdrant RAG E2 与各 Provider 的外部依赖/secret gates 保持本地 opt-in。

应用以 Java 21 Spring Boot JAR 运行；MySQL/Qdrant 仅作为本地 Docker Compose 基础设施。仓库没有 Dockerfile、Kubernetes、云部署、TLS、Secrets Manager 或生产运维声明。

## 10. Repository Map

~~~text
src/main/java/com/loanops/
├── agent/          # Agent orchestration, ChatClient gateway, turn completion
├── audit/          # Agent / Tool audit lifecycle
├── conversation/   # Persistent USER / ASSISTANT snapshots and CAS append
├── controller/     # Deterministic REST, Agent API, audit query
├── domain/         # Loan domain objects
├── persistence/    # MyBatis-Plus entities and mappers
├── policy/         # Ingestion, Router, Retriever, grounding, citation, policy audit
├── service/        # Deterministic financial calculations and query orchestration
└── tool/           # Three read-only Spring AI Tools

src/main/resources/
├── application*.yml
└── db/migration/   # V1-V6 loan, audit, conversation, policy and retrieval audit schemas

docs/               # Scope, architecture, acceptance, runbook and evaluation details
evaluation/         # Frozen Agent/Policy datasets and committed result evidence
scripts/            # Reproducible PowerShell verification/evaluation runners
~~~

主要文档：

- [Scope](docs/SCOPE.md)
- [Domain Rules](docs/DOMAIN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Acceptance](docs/ACCEPTANCE.md)
- [Validation Runbook](docs/RUNBOOK.md)
- [Agent Evaluation](docs/EVALUATION.md)
- [Policy RAG Evaluation](docs/POLICY_RAG_EVALUATION.md)
- [Provider Boundary](docs/PROVIDERS.md)
