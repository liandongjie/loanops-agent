# LoanOps Agent 开发历程（历史记录）

> 本文件记录项目从最小 Java 贷款诊断到当前 Agent + Policy RAG 工程的演进。它是历史说明，不定义当前范围。

## 开发原则

项目长期采用：

```text
冻结小范围
  ↓
实现一个阶段
  ↓
自动测试
  ↓
Review
  ↓
真实验收
  ↓
小 Commit / PR
  ↓
下一阶段
```

核心原则一直保持：

> Java 计算金融事实，AI 负责理解问题、选择 Tool 和解释事实。

## Phase 0：规格冻结

建立：

- `SCOPE.md`
- `DOMAIN.md`
- `ARCHITECTURE.md`
- `ACCEPTANCE.md`
- `DEVELOPMENT_PLAN.md`
- `AGENTS.md`

冻结最初 3 个领域对象、3 个只读 Tool 和 3 个核心贷款 Case。

## Phase 1：Java Domain Baseline

目标：

- 不依赖 AI；
- 不依赖数据库；
- 用 Java 正确实现还款、逾期和结清规则。

主要产物：

- `LoanContract`
- `RepaymentPlan`
- `PaymentRecord`
- `RepaymentCalculator`
- `LoanDiagnosisService`
- `BigDecimal`
- 可注入 `Clock`
- JUnit cases

## Phase 2：Persistence + REST

引入：

- H2；
- MyBatis-Plus；
- fixture；
- Mapper / Repository；
- REST API。

目的：

> 在完全不调用 LLM 时，也能独立验证同一套贷款事实。

## Phase 3：Spring AI + Read-only Tools

将现有 Java Service 暴露成 3 个 Agent Tools：

```text
getCurrentRepayment
getOverdueDiagnosis
getSettlementStatus
```

保持 Tool 不复制业务算法、不写数据库。

## Phase 4：Agent End-to-End

打通：

```text
自然语言
  -> Spring AI Agent
  -> Tool Calling
  -> Java Service
  -> 自然语言解释
```

并加入 unknown loan、写请求拒绝等安全 case。

## Phase 5：Resume MVP Hardening

当时的目标是停止扩展业务范围，把项目整理成：

- 可运行；
- 可测试；
- 可解释；
- 可放到简历；
- 可由 Reviewer 复现。

这一阶段形成最早的 Stop Point A。

## Phase 6：Stateful Conversation Runtime

新增：

- Flyway V4；
- `conversation` / `conversation_message`；
- USER / ASSISTANT transcript；
- restart persistence；
- prior USER 指代消解；
- fresh Tool query；
- optimistic CAS；
- completion transaction。

核心边界：

> Conversation 是上下文，不是当前金融事实源。

## Phase 7：Policy RAG + Evaluation

新增：

- MySQL versioned Policy Store；
- BGE-M3；
- Qdrant；
- Policy Router；
- Policy Retriever；
- Grounding Context；
- Citation Validator；
- Policy Retrieval Audit；
- 30-case Gold Dataset；
- E0 / E1 / E2 评测；
- Router hardening。

架构原则：

```text
MySQL  = Policy source of truth
Qdrant = rebuildable derived index
```

## Phase 8：Runtime Hardening

补充：

- current message limit；
- model-visible history limit；
- finite HTTP timeout；
- 有限重试；
- required / supplemental Policy failure semantics；
- invalid Citation fail closed；
- adversarial evidence。

这一阶段保留了模型失败证据，而不是通过 Prompt 包装成“全部解决”。

## Phase 9：Project Closure & Reproducible Delivery

当时聚焦：

- 文档与实现对齐；
- fresh clone 可复现；
- Hero Gate；
- `.env.example`；
- CI 边界；
- claim discipline。

该 Phase 当时的一次 Hero 重跑因 Docker / MySQL / Qdrant 不可用记录为 `ENV_BLOCKED`，这个状态只属于当时环境，不代表后来真实验收结果。

## Phase 9 之后的工程增强

### 多 Provider

在保持相同业务事实与 Tool 的前提下，接入并验证：

- DeepSeek；
- Ollama / qwen3:4b；
- GLM / glm-5.2。

### 本地 Launcher 与 Terminal Chat

增加：

- `run-agent.ps1`；
- provider 显式选择；
- `.env` 本地配置；
- Terminal Conversation 体验。

### Safe SSE Streaming

增加：

- `/api/agent/chat/stream`；
- provisional delta；
- Policy buffered delivery；
- `done(committed=true)` 成功语义；
- cancellation / error 边界。

### Local Policy Bootstrap

增加：

- 显式 one-shot Demo bootstrap；
- 普通本地 MySQL canonical store；
- Qdrant rebuild；
- idempotency；
- partial recovery；
- missing chunk recovery；
- existing unknown data fail closed；
- 不自动 seed、不提供 destructive reset。

## 为什么不继续编号 Phase 10 / 11 / 12

项目现在已经进入稳定的“当前规格 + 按需 PR”阶段。

继续把每一个小增强都扩成新的大 Phase，会让公开文档越来越像内部项目管理日志。

因此：

- 当前事实回到 `SCOPE / ARCHITECTURE / ACCEPTANCE`；
- 历史过程留在本文件；
- 新需求按独立 issue / branch / PR 验收；
- 不通过增加 Phase 编号证明工程严谨性。
