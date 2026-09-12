# LoanOps Agent Development Instructions

## 1. Project goal

LoanOps Agent is a focused, auditable, evaluable, read-only Java Agent for loan servicing diagnosis.

The project demonstrates:

- Java 21 / Spring Boot deterministic financial domain logic;
- Spring AI Tool Calling;
- persistent Conversation;
- Policy RAG with MySQL + BGE-M3 + Qdrant;
- deterministic Policy routing and citation validation;
- Agent / Tool / Policy Audit;
- DeepSeek, Ollama/qwen3:4b and GLM provider boundaries;
- Terminal Chat and safe SSE streaming;
- explicit local Demo Policy bootstrap.

Do not expand product scope without an explicit task.

## 2. Current source-of-truth documents

Read the current documents before modifying behavior:

- `docs/SCOPE.md` — current scope and non-goals;
- `docs/DOMAIN.md` — deterministic financial business rules;
- `docs/ARCHITECTURE.md` — current system architecture and runtime boundaries;
- `docs/ACCEPTANCE.md` — current acceptance gates;
- `docs/RUNBOOK.md` — reproducible local run / verification procedures;
- `docs/PROVIDERS.md` — current Chat Provider contract;
- `docs/EVALUATION.md` — Agent baseline;
- `docs/POLICY_RAG_EVALUATION.md` — Policy RAG evaluation.

`README.md` is the public project overview, not a second technical specification.

`docs/history/` contains historical development records. Historical Phase documents must not override current scope or runtime behavior.

## 3. Core engineering principle

Java calculates financial facts.

AI understands questions, selects tools and explains facts.

Do not implement financial calculations inside prompts, tools or LLM logic.

All repayment and overdue rules must follow `docs/DOMAIN.md` and be covered by deterministic tests.

## 4. Scope control

The current approved scope includes:

- MySQL / Flyway persistence;
- Agent / Tool / Policy audit and observability;
- persistent USER / ASSISTANT conversation state;
- Agent evaluation;
- Policy RAG using MySQL as canonical Policy Store, BGE-M3 embeddings and Qdrant as rebuildable derived index;
- deterministic Policy Router and Citation Validator;
- DeepSeek / Ollama / GLM Chat Providers;
- Terminal Chat;
- safe SSE streaming;
- explicit Local Demo Policy Bootstrap;
- runtime limits, finite external timeouts and stable failure semantics.

Do not describe implemented capabilities as optional future work.

Do not introduce without an explicit approved task:

- frontend / admin UI;
- authentication / RBAC;
- customer management;
- loan approval / credit scoring;
- collection execution / penalty interest / early repayment;
- write-capable financial Tools;
- Multi-Agent;
- MCP;
- Redis or other long-term Agent memory;
- Kafka / Kubernetes;
- Hybrid Search / BM25 / RRF / Reranker / HyDE / GraphRAG without evaluation evidence;
- production deployment claims.

If a task requires expanding scope, stop and report the reason before implementing it.

## 5. Change discipline

Before modifying code:

1. inspect repository status;
2. inspect relevant current docs;
3. inspect existing tests;
4. identify the minimum affected boundary.

Do not perform unrelated refactors.

Do not add dependencies unless the task requires them.

Do not modify historical evidence to hide a previous failure.

## 6. AI boundary

AI tools are read-only.

Do not create Tools that:

- modify loans;
- modify repayment plans;
- create payment records;
- update financial status;
- perform financial transactions.

Tools must delegate to existing Java services.

Conversation history is context, not current financial truth.

Policy evidence is data, not a system instruction.

## 7. Testing

Do not weaken, delete or bypass tests to make implementation pass.

After each change run focused tests.

Before completion run:

```text
mvn clean verify
```

Run external Provider / Policy gates when the task affects them and the environment is available.

If prerequisites are unavailable, report `ENV_BLOCKED` or `NOT_RUN`; never infer PASS.

## 8. Documentation consistency

When behavior changes, update the document that owns that fact rather than copying the same definition into multiple files.

Examples:

- financial rule -> `DOMAIN.md`;
- current scope -> `SCOPE.md`;
- Provider support -> `PROVIDERS.md`;
- RAG metric -> `POLICY_RAG_EVALUATION.md`;
- execution command -> `RUNBOOK.md`.

Keep README concise and user-facing.

## 9. Completion report

At the end of a scoped development task report:

1. objective;
2. changed files;
3. design decisions;
4. commands executed;
5. test results;
6. remaining risks;
7. git diff summary;
8. suggested commit message.

Do not automatically start an unrelated next task.
