# Acceptance Snapshot

## Scope

此快照描述 Phase 9 收口时的当前验收面，不是对未来提交的永久保证。每次 fresh clone 或代码变更后都应重新执行相应 Gate；外部服务未运行时不得沿用历史 PASS。

## Phase 9 Verification Record — 2026-09-08

- Phase 9 status：DONE；
- Java：21.0.10；
- mvn clean verify：BUILD SUCCESS，115 tests，0 failures，0 errors，5 skipped；
- docker compose config：valid；
- Hero E2E：ENV_BLOCKED。DEEPSEEK_API_KEY、Ollama 和 bge-m3 可用，但 Docker engine 未运行，
  MySQL 127.0.0.1:3307 与 Qdrant 127.0.0.1:6333 均不可达。

5 个 skipped tests 是外部 Policy RAG / DeepSeek opt-in gates；它们不计为本轮 Hero PASS。

## Deterministic Local Gate

要求 Java 21，并执行：

~~~powershell
mvn clean verify
./scripts/verify-resume-mvp.ps1
~~~

mvn clean verify 覆盖当前默认 H2 自动测试，包括 Domain、Persistence、REST、只读 Tool、Conversation、Policy Runtime、Citation、Audit、Observability 和 Runtime Hardening。

verify-resume-mvp.ps1 会再次运行完整 Maven verification、打包并启动临时 Spring Boot JAR，在固定业务日期 2026-08-23 下验证：

| Case | Expected |
|---|---|
| LN-10001 | due 8500, outstanding 8500, not overdue |
| LN-10002 | due 8500, paid 5000, outstanding 3500, overdue 3 days |
| LN-10003 | settled, total outstanding 0 |

该脚本默认不调用 DeepSeek，也不启动或验证 Policy RAG。-WithAi 是早期三个 Tool Case 的 live DeepSeek smoke，不等于 Policy Hero E2E。

## MySQL / Flyway Gate

~~~powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
./scripts/verify-mysql.ps1
~~~

脚本启动 Docker MySQL 8，在 mysql Profile 下运行完整测试，启动实际 JAR，验证 LN-10002，并检查 Flyway V1-V3。当前数据库 schema 还包含：

- V4 Conversation Runtime；
- V5 canonical Policy document/version/chunk；
- V6 Policy Retrieval/Hit Audit。

V4-V6 的专项约束由当前 Maven integration tests 覆盖；verify-mysql.ps1 的显式 history assertions 仍只检查 V1-V3，因此不能把脚本描述成 V4-V6 的专用验收器。

## Policy RAG Evaluation Evidence

固定 30-case Gold Dataset 与同一 synthetic corpus 的已提交结果：

| Metric | E0 | E1 | E2 |
|---|---:|---:|---:|
| Router Accuracy | 0.8333 | 0.8333 | 1.0000 |
| Policy-required Recall | 0.8077 | 0.8077 | 1.0000 |
| ExactReferenceAccuracy | 0.7500 | 1.0000 | 1.0000 |
| NoMatchAccuracy | 0.0000 | 1.0000 | 1.0000 |
| FalseMatchCount | 3 | 0 | 0 |

E2 direct Retriever 的 Recall@1/3/5 和 MRR 均为 1.0000。数字只适用于该固定 corpus，不是 production accuracy。

历史 E1 mixed-002 曾出现证据外“质押”扩展；E2 未复现，但没有 generation change，因此不记为已修复。

## Runtime Hardening Evidence

- current message limit：4,000 characters；
- model-visible history：20 messages / 12,000 characters；
- finite HTTP connect/read timeout：5s / 60s；
- Spring AI max-attempts：1，不进行自动重试；
- Provider / required-policy / supplemental-policy / Citation / Conversation commit 失败语义由 deterministic tests 覆盖。

真实 DeepSeek adversarial review 为 4/5 model-output semantic PASS。ADV-04 的缺失引用回答是 FAIL；PolicyCitationValidator 在成功提交前拒绝它。该结果说明 deterministic boundary 生效，不说明 prompt injection 已解决。

## Real Hero E2E Gate

Prerequisites：Java 21、MySQL、Qdrant v1.15.4、Ollama + bge-m3、DEEPSEEK_API_KEY。

~~~powershell
docker compose up -d mysql qdrant
ollama pull bge-m3
$env:DEEPSEEK_API_KEY = "your-key"
$env:POLICY_AGENT_REAL_E2E_TEST = "true"
mvn "-Dtest=PolicyAgentRealE2EIntegrationTest" test
~~~

PolicyAgentRealE2EIntegrationTest 自行 ingest synthetic policy、重建隔离 collection，并验证：

- Turn 1 调用 getOverdueDiagnosis(LN-10002)；
- Turn 2 从 prior USER context 解析贷款；
- SUPPLEMENTAL / MATCHED；
- applicable article 成为 P1，回答包含有效 [P1]；
- Policy Retrieval、hit、citation、Agent 与 Tool Audit 可关联；
- transcript 恰为 USER、ASSISTANT、USER、ASSISTANT；
- fixture 在测试后清理。

模型措辞不固定。环境不完整时状态必须是 ENV_BLOCKED，而不是 PASS。

## CI Boundary

GitHub Actions 当前只执行：

- H2 full Maven verification；
- MySQL integration verification。

Ollama 下载、BGE-M3、Qdrant Policy RAG E2、DeepSeek secret-dependent test 均保持 opt-in，不进入 CI，以避免外部 secret、大模型下载、随机性和额外运行成本。

## Deployment Boundary

应用以 Java 21 Spring Boot JAR 运行；MySQL/Qdrant 本地基础设施由 Docker Compose 提供。项目没有 application Dockerfile、Kubernetes、cloud deployment、RBAC、provider fallback 或真实银行生产部署声明。
