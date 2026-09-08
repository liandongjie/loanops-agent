# Architecture

## 1. Design Goal

LoanOps Agent 的核心是隔离“确定性金融事实”和“概率型语言/政策回答”：

- Java 领域服务拥有金额、日期、逾期和结清规则；
- MySQL 版本化 policy store 拥有政策文档、版本与 chunk metadata；
- Qdrant 是可从 MySQL 重建的派生向量索引；
- LLM 只负责理解问题、选择只读 Tool 和基于受控证据组织回答；
- Citation Validator 与 Audit 提供独立于模型自律的硬边界和证据链。

REST 可以绕过 AI 验证金融事实。Policy RAG 不能修改贷款事实，Conversation 历史也不能替代 fresh Tool query。

## 2. Current Request Flow

~~~mermaid
flowchart TD
    Client --> API[Agent API]
    API --> Snapshot[Conversation Snapshot]
    Snapshot --> Router[Policy Router]
    Router --> Decision[Policy Decision]

    subgraph FinancialFacts[Financial facts trust path]
        Model[DeepSeek Tool Calling] --> Tools[LoanOpsTools]
        Tools --> Service[Java deterministic services]
        Service --> LoanData[(MySQL or H2 loan data)]
    end

    subgraph PolicyEvidence[Policy evidence trust path]
        Decision -->|SUPPLEMENTAL / REQUIRED| Query[Policy Query]
        Query --> Canonical[(MySQL canonical policy store<br/>applicable versions and chunks)]
        Canonical --> Exact[Exact-reference candidates]
        Query --> Embedding[BGE-M3 query embedding]
        Embedding --> Qdrant[(Qdrant rebuildable derived vector index)]
        Qdrant --> Semantic[Semantic candidates]
        Exact --> Merge
        Semantic --> Merge
        Merge[Retriever merge / rank / applicability filtering] --> RequiredNoMatch{REQUIRED + NO_MATCH?}
        RequiredNoMatch -->|Yes| Abstain[Return no-match notice<br/>no model call]
        RequiredNoMatch -->|No| Context[Grounding Context]
        Context --> Model
        Abstain --> Citation
        Model --> Citation[Policy Citation Validation]
    end

    Decision -->|NOT_REQUIRED| Model
    Citation --> Answer[Answer]
    Answer --> Commit[Conversation commit]
    Answer --> Audit[Agent, Tool and Policy Audit]
~~~

运行顺序由 LoanOpsAgentService 编排：

1. 校验当前消息长度；
2. 创建或读取持久化 Conversation Snapshot；
3. 写入 Agent Audit STARTED；
4. PolicyRuntimeService 计算 NOT_REQUIRED / SUPPLEMENTAL / REQUIRED；
5. 需要政策时构造 query、检索适用版本并建立 Grounding Context；
6. SpringAiAgentChatGateway 把历史、政策上下文、当前问题和只读 Tools 交给 DeepSeek；
7. PolicyCitationValidator 校验回答中的引用；
8. 在同一完成事务中追加 USER / ASSISTANT，并将 Agent Audit 标记为 SUCCESS。

任何 Provider、Tool、必要政策检索、Citation 或 Conversation commit 失败，都不会留下伪成功 turn。

## 3. Two Trust Paths

### 3.1 Financial Facts

~~~text
DeepSeek Tool Calling
  -> LoanOpsTools
  -> LoanStatusService
  -> LoanDiagnosisService
  -> RepaymentCalculator
  -> MySQL / H2 loan data
~~~

RepaymentCalculator 使用 BigDecimal；LoanDiagnosisService 使用注入的 Clock。金额、日期、当前期次、逾期和结清不出现在 Prompt 算法中，也不由 Tool 重新实现。

三个 Tool 均只读：

- getCurrentRepayment
- getOverdueDiagnosis
- getSettlementStatus

如果 REST 与 Agent 输出冲突，先用 REST / Service 测试核对 Java 事实，再检查 Tool 选择或模型表达。

### 3.2 Policy Evidence

~~~text
Policy Router
  -> Policy Query
  -> MySQL applicable versions / chunks
       -> exact-reference candidates
  -> BGE-M3 query embedding
       -> Qdrant semantic candidates
  -> merge / rank with applicable-chunk filtering
  -> Grounding Context
  -> DeepSeek
  -> PolicyCitationValidator
  -> Answer
~~~

PolicyRetrievalDecisionEngine 是 deterministic Router：

- NOT_REQUIRED：纯金融事实问题不运行政策检索；
- SUPPLEMENTAL：贷款事实和政策依据需要组合；
- REQUIRED：纯政策问题必须取得足够依据，否则 fail closed 或明确 abstain。

PolicyRetriever 先从 MySQL 取得业务日期适用的版本和 chunks，再合并 exact-reference 与 Qdrant dense retrieval。Qdrant 命中只有能映射回适用 MySQL chunk 时才可进入结果。

PolicyIngestionService 将规范化文本、内容 hash、版本有效期和结构化 chunks 写入 MySQL。PolicyIndexRebuilder 从 MySQL 读取当前可索引 chunks，经 BGE-M3 生成 embeddings 后整体替换 Qdrant collection。因此：

- **MySQL = canonical policy store / source of truth**
- **Qdrant = rebuildable derived vector index**

Policy Context 是不可信证据数据，不是系统指令。需要政策引用时，PolicyCitationValidator 拒绝缺失或未知 [Pn] 的回答。

## 4. Conversation Runtime

Conversation 使用 Flyway V4 的 conversation / conversation_message 表持久化。

- transcript 只保存成功的 USER / ASSISTANT；
- Tool messages 和 Policy Context 不写入 transcript；
- 历史用于指代消解，不能成为当前金融事实来源；
- 完整成功 transcript 留存，但模型可见窗口最多 20 条消息、12,000 字符，并保留完整 turn；
- append 使用 version + last_message_sequence optimistic CAS，避免并发 turn 覆盖；
- AgentTurnCompletionService 在一个事务中完成 transcript append 与 Agent SUCCESS audit。

Hero 第二轮从 prior USER 内容恢复 LN-10002，而不是依赖 Assistant 输出。

## 5. Data and Migrations

Flyway 是数据库结构的唯一版本来源：

| Migration | Responsibility |
|---|---|
| V1 | loan_contract、repayment_plan、payment_record |
| V2 | LN-10001 / LN-10002 / LN-10003 synthetic validation fixture |
| V3 | agent_audit_log、agent_tool_audit_log |
| V4 | conversation、conversation_message 与 Agent/Conversation 关联 |
| V5 | policy_document、policy_document_version、policy_chunk |
| V6 | policy_retrieval_audit、policy_retrieval_hit |

H2 是默认快速开发/测试路径；MySQL 8 是本地集成、Policy RAG 和 Hero E2E 路径。两者共享 MyBatis-Plus 和 Flyway migration。

V2 loan data、Policy RAG evaluation corpus 和 Hero policy 都是 synthetic validation fixture，不代表真实银行数据或完整法规。

## 6. Audit and Observability

Audit 与运行时 Observability 分层：

- Agent Audit：request、conversation、history/system-prompt fingerprint、SUCCESS/FAILED；
- Tool Audit：Tool 顺序、名称、loan number、耗时和技术结果；
- Policy Audit：decision、status、as-of date、query/context/config hash、embedding model、collection、hits 与 cited_in_answer；
- Micrometer / Actuator：低基数运行指标和 health/info/prometheus。

Audit STARTED 在远程调用前独立提交，模型调用不包在长数据库事务中。默认 loanops.audit.include-content=false，只保存长度与 SHA-256 指纹；SHA-256 不是匿名化。Spring AI prompt、completion、Tool content observations 默认关闭。

AgentRequestCorrelationFilter 在 JSON 反序列化前生成服务端 UUID，并写入 X-Request-Id、request attribute 和 MDC。客户端传入的 request id 不受信任。

当前 GET /api/agent/audits/{requestId} 没有 RBAC，只是本地验证接口，不是生产暴露方案。

## 7. Runtime Failure Semantics

- 当前消息最大 4,000 字符；
- 模型可见历史最多 20 条消息和 12,000 字符；
- HTTP connect/read timeout 默认 5s / 60s；
- Spring AI max-attempts = 1，不进行自动重试；
- REQUIRED 检索不可用时请求失败；
- REQUIRED + NO_MATCH 不调用模型生成政策结论；
- SUPPLEMENTAL 检索失败或无命中时，金融回答可继续，但必须带政策不可用/不足提示；
- Provider 异常映射为稳定的 AgentProviderUnavailableException；
- Citation Validation 失败和 Conversation CAS 冲突均阻止成功提交。

这些边界降低失败放大和错误落盘风险，但不代表完整生产韧性；当前没有 provider fallback、circuit breaker、RBAC 或 rate limiting。

## 8. Provider Boundary

Agent 通过 Spring AI ChatClient 使用 DeepSeek，业务类不调用厂商 SDK。当前只有 DeepSeek 完成真实 Tool Calling 和 Hero E2E。

Qwen/GLM 仅保留历史接入分析，未接入、未验证，不能列为 supported provider。任何 Provider 变化都不得修改 RepaymentCalculator、LoanDiagnosisService、LoanOpsTools 的业务语义或数据库事实。

## 9. Deliberate Non-goals

- Multi-Agent：没有自然角色分解；
- MCP：现有 Tools 都是本地 Java 能力，没有跨进程/跨应用共享需求；
- long-term Agent Memory：陈旧状态不能成为金融事实来源；
- Hybrid Search / Reranker：E1 后固定 corpus 未显示排序瓶颈；
- write Tools：权限、确认、幂等、恢复和人工批准尚未设计；
- frontend、Kubernetes、cloud deployment：不属于当前可复现工程收口目标。
