# LoanOps Agent Development Instructions

## 1. Project goal

LoanOps Agent is a focused, auditable, evaluable, read-only Java Agent for
loan servicing diagnosis.

The project demonstrates:

- Java 21
- Spring Boot
- deterministic financial domain logic
- Spring AI
- Tool Calling
- persistent conversation state
- Policy RAG with deterministic routing, citation validation and audit

The project is intentionally focused. Do not expand the product scope.

Detailed requirements are defined in:

- docs/SCOPE.md
- docs/DOMAIN.md
- docs/ARCHITECTURE.md
- docs/ACCEPTANCE.md
- docs/DEVELOPMENT_PLAN.md

These documents are the source of truth.

---

## 2. Core engineering principle

Java calculates financial facts.
AI only understands questions, selects tools and explains facts.

Do not implement financial calculations inside prompts, tools or LLM logic.

All repayment and overdue rules must be implemented in deterministic Java
domain services and covered by tests.

---

## 3. Scope control

Do NOT add functionality outside docs/SCOPE.md.

The current approved scope includes the independently gated Phase 6-8 work:

- MySQL / Flyway persistence;
- audit and observability;
- persistent USER / ASSISTANT conversation state;
- Agent evaluation;
- Policy RAG using MySQL as the canonical policy store, BGE-M3 embeddings
  and Qdrant as a rebuildable derived vector index;
- deterministic Policy Router hardening;
- runtime limits, finite external timeouts and failure semantics.

Do not describe these implemented capabilities as optional future work.

Especially do not introduce without an explicit new task:

- frontend
- authentication / RBAC
- customer management
- loan approval
- credit scoring
- collection
- penalty interest
- early repayment
- MCP
- Multi-Agent
- Redis or other long-term Agent memory
- Kafka
- Kubernetes
- write-capable financial Tools
- new retrieval techniques such as Hybrid Search, BM25, RRF, Reranker,
  HyDE or GraphRAG without evaluation evidence and an explicitly approved phase

If a task appears to require expanding scope, stop and report the reason.

---

## 4. Change discipline

Before modifying code:

1. inspect the repository;
2. inspect relevant docs;
3. inspect existing tests;
4. inspect git status.

Do not perform unrelated refactors.

Prefer narrow, auditable changes.

Do not modify files outside the task scope unless necessary.
If necessary, explain why before doing so.

Do not introduce a new dependency unless it is required by the current task.

---

## 5. Business rules

Never invent or change financial rules.

Use docs/DOMAIN.md as the only source of truth.

Amounts must use BigDecimal.

Business dates must be based on an injectable Clock instead of directly
calling LocalDate.now() inside domain logic.

---

## 6. AI boundary

AI tools are read-only.

Do not create tools that:

- modify loans;
- modify repayment plans;
- create payment records;
- update status;
- perform financial transactions.

Tools must delegate to existing Java services.

Do not duplicate domain calculations inside tool implementations.

---

## 7. Testing

Do not weaken, delete or bypass tests to make implementation pass.

After every code change run the tests relevant to that change.

Before completing a phase run the complete test suite.

Current baseline command:

mvn clean verify

If tests fail, diagnose the root cause and fix it.
If the specification and tests conflict, stop and report the conflict.

---

## 8. Completion report

At the end of every development phase report:

1. phase objective;
2. files changed;
3. important implementation decisions;
4. tests executed;
5. test results;
6. remaining risks or unresolved issues;
7. git diff summary;
8. suggested commit message.

Do not automatically start the next phase.
Stop after the current phase and wait for approval.
