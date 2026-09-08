# Reproducible Validation Runbook

## 1. Evidence Levels

三个层次验证不同边界，不应互相替代：

| Level | Verifies | External dependency |
|---|---|---|
| 1. deterministic local | Java domain、H2、REST、Tool adapter、Conversation/Policy runtime tests | Java 21 + Maven |
| 2. infrastructure | MySQL 8 / Flyway path、Qdrant service、local BGE-M3 availability | Docker + local Ollama |
| 3. real Hero E2E | DeepSeek Tool Calling + Conversation + Policy RAG + Citation + Audit | MySQL + Qdrant + Ollama/bge-m3 + DeepSeek key |

所有命令从仓库根目录使用 PowerShell 7。

## 2. Prerequisites

- JDK 21；
- Maven 3.9+；
- Level 2/3：Docker；
- Level 2/3 Policy：本机 Ollama；
- Level 3：有效 DEEPSEEK_API_KEY。

先确认实际运行时：

~~~powershell
java -version
mvn -version
docker version
ollama --version
~~~

不要降低 pom.xml 的 Java 21 target。若本机有多个 JDK，先把 JAVA_HOME 和 Path 指向真实 JDK 21。

## 3. Level 1 — Deterministic Local Verification

### 3.1 Full H2 verification

~~~powershell
mvn clean verify
~~~

这条命令不需要 DeepSeek、MySQL、Qdrant 或 Ollama。真实外部 E2E tests 由环境变量 opt-in，默认 skip。

### 3.2 Reproducible loan fixture smoke

~~~powershell
./scripts/verify-resume-mvp.ps1
~~~

脚本运行完整 Maven verification、打包应用、以固定业务日期启动临时 JAR，并验证：

- LN-10001：due 8500、未逾期；
- LN-10002：due 8500、paid 5000、outstanding 3500、逾期 3 天；
- LN-10003：settled、total outstanding 0。

脚本默认不启用 AI。-WithAi 会运行早期三个 live DeepSeek Tool cases、unknown-loan 和 read-only guard，但它**不覆盖** Policy RAG、Qdrant、BGE-M3、Policy Citation 或两轮 Hero。

## 4. Level 2 — Infrastructure

启动仓库提供的本地基础设施：

~~~powershell
docker compose up -d mysql qdrant
docker compose ps
~~~

- MySQL：mysql:8.0，默认映射 127.0.0.1:3307；
- Qdrant：qdrant/qdrant:v1.15.4，默认 127.0.0.1:6333；
- Ollama：由本机安装提供，不新增 Compose container。

准备 embedding model：

~~~powershell
ollama pull bge-m3
ollama list
~~~

验证 MySQL/Flyway 和确定性 JAR：

~~~powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
./scripts/verify-mysql.ps1
~~~

verify-mysql.ps1 会启动 MySQL、在 mysql Profile 下运行完整测试、启动实际 JAR、验证 LN-10002，并显式检查 Flyway V1-V3。V4-V6 由 Maven 中对应 integration tests 覆盖。

## 5. Level 3 — Real Agent + Policy Hero E2E

### 5.1 Environment

~~~powershell
docker compose up -d mysql qdrant
ollama pull bge-m3

$env:DEEPSEEK_API_KEY = "your-key"
$env:POLICY_AGENT_REAL_E2E_TEST = "true"

mvn "-Dtest=PolicyAgentRealE2EIntegrationTest" test
~~~

DEEPSEEK_API_KEY 只能来自当前进程环境或安全的本地 secret 注入，不要写入 YAML、日志或提交文件。.env.example 只是变量清单，不是 production secrets solution。
模型、base URL 和默认 collection 等可选覆盖项见 .env.example；Hero test 自行使用隔离 collection loanops_policy_agent_real_e2e。

### 5.2 Correct Maven/Surefire command on Windows

PowerShell 中把 -Dtest=... 作为一个带引号参数传给 Maven。测试类通过 @ActiveProfiles 启用 mysql、policy、ai，不需要另写 spring profile 参数。

外部环境或 key 缺失时，不运行并报告 ENV_BLOCKED；不要把 JUnit 的 opt-in skip 当成 Hero PASS。

### 5.3 Hero conversation and stable invariants

Turn 1：

~~~text
LN-10002 为什么逾期？
~~~

稳定断言：

- Agent Audit SUCCESS；
- getOverdueDiagnosis(LN-10002) SUCCESS；
- 金融事实来自 Java Service / Tool；
- conversation 已创建，成功 turn 被持久化。

Turn 2（复用 Turn 1 的 conversationId）：

~~~text
按照规定现在应该怎么处理？
~~~

稳定断言：

- prior USER context 解析 LN-10002；
- Policy decision = SUPPLEMENTAL；
- Policy retrieval status = MATCHED；
- synthetic policy fixture 的适用条款进入 context；
- P1 映射到命中条款，answer 包含有效 [P1]；
- retrieval config/query/context/hit/citation 有 Audit；
- transcript roles 恰为 USER / ASSISTANT / USER / ASSISTANT。

PolicyAgentRealE2EIntegrationTest 会自行：

1. 清理同名旧 fixture；
2. ingest synthetic policy fixture；
3. 由 MySQL canonical chunks 重建隔离 Qdrant collection；
4. 调用真实 DeepSeek；
5. 验证 Tool、Conversation、Policy RAG、Citation 和 Audit；
6. 在 AfterEach 清理请求、对话、政策 fixture 和 vector collection。

它不比较完整自然语言输出，也不保证每次 wording 相同。

## 6. Policy RAG Evaluation

Requirements：Java 21、Docker MySQL/Qdrant、Ollama + bge-m3。

~~~powershell
./scripts/evaluate-policy-rag.ps1 -Label e0 -Threshold 0.0
./scripts/evaluate-policy-rag.ps1 -Label e1 -Threshold 0.60
./scripts/evaluate-policy-rag.ps1 -Label e2 -Threshold 0.60
~~~

脚本创建固定的 loanops_policy_rag_eval MySQL schema 和同名 Qdrant collection，运行 frozen 30-case dataset，并在 finally 中清理两者。

真实 DeepSeek manual review：

~~~powershell
$env:DEEPSEEK_API_KEY = "your-key"
./scripts/evaluate-policy-rag.ps1 -Label e2 -Threshold 0.60 -ManualReview
~~~

E0/E1/E2 指标仅属于固定 synthetic corpus。历史 mixed-002 generation expansion 和 Phase 8 ADV-04 missing-citation failure 都必须保留，不得用后续一次 PASS 覆盖。

## 7. Optional Application Start

只验证确定性 REST：

~~~powershell
$env:SERVER_PORT = "18080"
mvn spring-boot:run
~~~

启用 MySQL + Policy + DeepSeek runtime：

~~~powershell
$env:SPRING_PROFILES_ACTIVE = "mysql,policy,ai"
$env:DEEPSEEK_API_KEY = "your-key"
$env:SERVER_PORT = "18080"
mvn spring-boot:run
~~~

空 fresh database 只有 schema 和 synthetic loan fixture；Policy RAG 需要显式 ingest policy 并 rebuild index。Hero test 会自行完成隔离的 synthetic policy 准备，不需要新增 seeding subsystem。

## 8. Proxy Note

若 PowerShell 可访问 DeepSeek，但 Java 进程超时，可对早期 smoke script 显式传入本机代理：

~~~powershell
./scripts/verify-resume-mvp.ps1 -WithAi -ProxyHost 127.0.0.1 -ProxyPort 7890
~~~

脚本只把代理参数传给临时 JVM，不修改系统全局配置。Hero E2E 没有单独代理参数；如环境确需代理，应通过 JVM/环境配置提供，并如实记录。

## 9. Cleanup

~~~powershell
docker compose down
~~~

该命令停止容器但保留命名 volumes。不要在常规验收中自动删除 volumes；其中可能包含 Reviewer 的本地数据。
