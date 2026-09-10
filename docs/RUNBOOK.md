# Reproducible Validation Runbook

## 1. Evidence Levels

三个层次验证不同边界，不应互相替代：

| Level | Verifies | External dependency |
|---|---|---|
| 1. deterministic local | Java domain、H2、REST、Tool adapter、Conversation/Policy runtime tests | Java 21 + Maven |
| 2. infrastructure | MySQL 8 / Flyway path、Qdrant service、local BGE-M3 availability | Docker + local Ollama |
| 3. real Provider/Hero E2E | selected Chat Provider + Tool Calling + Conversation + Policy RAG + Citation + Audit | Provider prerequisite + MySQL + Qdrant + Ollama/bge-m3 |

所有命令从仓库根目录使用 PowerShell 7。

## 2. Prerequisites

- JDK 21；
- Maven 3.9+；
- Level 2/3：Docker；
- Level 2/3 Policy：本机 Ollama；
- Level 3：DeepSeek 需要有效 DEEPSEEK_API_KEY；Ollama 需要本地 qwen3:4b；GLM 需要有效 GLM_API_KEY。

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

这条命令不需要 Chat Provider、MySQL、Qdrant 或 Ollama。真实外部 E2E tests 由环境变量 opt-in，默认 skip。

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

## 5. Level 3 — Real Provider Baseline + Policy Hero E2E

### 5.1 Unified six-case baseline

同一个 runner 和 manifest 固定业务日期 `2026-08-23`、时区 `Asia/Shanghai`，并按 Provider 显式传入 provider / adapter / model。Secret 只来自当前进程或安全本地注入，不写入 YAML、日志或报告。

DeepSeek：

~~~powershell
$env:DEEPSEEK_API_KEY = "your-key"
./scripts/evaluate-agent-baseline.ps1 -Provider deepseek
~~~

Ollama / qwen3:4b：

~~~powershell
$env:OLLAMA_BASE_URL = "http://localhost:11434"
ollama pull qwen3:4b
./scripts/evaluate-agent-baseline.ps1 -Provider ollama
~~~

GLM / glm-5.2：

~~~powershell
$env:GLM_API_KEY = "your-key"
./scripts/evaluate-agent-baseline.ps1 -Provider glm
~~~

缺少当前 Provider 的 key、Ollama 服务或指定 model 时，self-start runner 报告 `ENV_BLOCKED`。不得用其他 model 替代，也不得把普通 chat 或答案文本当作 Tool Calling PASS。`-Model` 只用于显式验收 override；默认冻结身份是：

~~~text
deepseek / deepseek / deepseek-chat
ollama   / ollama   / qwen3:4b
glm      / zhipuai  / glm-5.2
~~~

### 5.2 Provider-aware Policy Hero

先准备共享 Policy 基础设施：

~~~powershell
docker compose up -d mysql qdrant
ollama pull bge-m3
$env:POLICY_AGENT_REAL_E2E_TEST = "true"
~~~

每次只选择一组身份，然后运行同一个测试类。

DeepSeek：

~~~powershell
$env:DEEPSEEK_API_KEY = "your-key"
$env:LOANOPS_CHAT_PROVIDER = "deepseek"
$env:LOANOPS_CHAT_ADAPTER = "deepseek"
$env:LOANOPS_CHAT_MODEL = "deepseek-chat"
mvn "-Dtest=PolicyAgentRealE2EIntegrationTest" test
~~~

Ollama / qwen3:4b：

~~~powershell
$env:OLLAMA_BASE_URL = "http://localhost:11434"
$env:LOANOPS_CHAT_PROVIDER = "ollama"
$env:LOANOPS_CHAT_ADAPTER = "ollama"
$env:LOANOPS_CHAT_MODEL = "qwen3:4b"
mvn "-Dtest=PolicyAgentRealE2EIntegrationTest" test
~~~

GLM / glm-5.2：

~~~powershell
$env:GLM_API_KEY = "your-key"
$env:LOANOPS_CHAT_PROVIDER = "glm"
$env:LOANOPS_CHAT_ADAPTER = "zhipuai"
$env:LOANOPS_CHAT_MODEL = "glm-5.2"
mvn "-Dtest=PolicyAgentRealE2EIntegrationTest" test
~~~

PowerShell 中把 `-Dtest=...` 作为一个带引号参数传给 Maven。测试类通过 `@ActiveProfiles` 启用 mysql、policy、ai，不需要另写 Spring profile。外部环境或 key 缺失时报告 `ENV_BLOCKED`；JUnit skipped 不是 Hero PASS。

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
- transcript roles 恰为 USER / ASSISTANT / USER / ASSISTANT；
- Agent Audit identity 等于当前 Provider/model；
- Policy retrieval embedding_model = bge-m3。

PolicyAgentRealE2EIntegrationTest 会自行清理同名旧 fixture、ingest synthetic policy、由 MySQL canonical chunks 重建隔离 Qdrant collection、调用当前选择的 Chat Provider，并验证 Tool、Conversation、Policy RAG、Citation 和 Audit；AfterEach 清理请求、对话、政策 fixture 和 vector collection。

它不比较完整自然语言输出，也不保证每次 wording 或 Tool 选择相同。单次 PASS 不是稳定率结论。

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

推荐用本地 launcher 显式选择 Chat Provider；它会验证 Maven 使用 Java 21，并只为本次启动设置 provider / adapter / model：

~~~powershell
./scripts/run-agent.ps1 -Provider ollama
./scripts/run-agent.ps1 -Provider deepseek
./scripts/run-agent.ps1 -Provider glm
~~~

DeepSeek / GLM API Key 可分别放在仓库根目录 ignored `.env` 的 `DEEPSEEK_API_KEY` / `GLM_API_KEY` 中；Spring Boot Config Data 自动加载该文件，不需要每次把 key 复制到当前 PowerShell 环境。launcher 不读取、解析、打印或修改 `.env`。操作系统环境变量仍可作为 Spring Boot 原生高级 override。

默认 launcher 使用 `ai` profile 并显式设置 `POLICY_RETRIEVAL_ENABLED=false`。启用完整的现有 MySQL + Policy + selected Chat runtime：

~~~powershell
./scripts/run-agent.ps1 -Provider ollama -WithPolicy
# deepseek / glm 同样支持 -WithPolicy
~~~

`-WithPolicy` 选择 `mysql,policy,ai` profiles 并显式设置 `POLICY_RETRIEVAL_ENABLED=true`。它要求 MySQL、Qdrant、Ollama、bge-m3、已 ingest 的 policy corpus 和已构建的 vector index 均可用，但不会自动启动基础设施、拉取模型、ingest 或 rebuild，也不会静默退回 Policy OFF。空 fresh database 只有 schema 和 synthetic loan fixture；Hero test 会自行完成隔离的 synthetic policy 准备，不需要新增 seeding subsystem。

### 7.1 Terminal Chat

Terminal Chat 不会自动启动后端。先按上文用 launcher 启动 Agent，再在另一个 PowerShell 7 终端运行：

~~~powershell
./scripts/chat.ps1
./scripts/chat.ps1 -BaseUrl "http://127.0.0.1:8080"
~~~

- `/new`：清空客户端的 current conversation ID，不删除服务端 Conversation；
- `/id`：显示当前 conversation ID；
- `/exit`：退出。

Terminal 默认调用 `POST /api/agent/chat/stream`，使用 SSE 增量读取；原 `POST /api/agent/chat` 继续提供同步完整 JSON response。客户端本地只保存 current conversation ID，不保存或重发 USER/ASSISTANT 历史。Provider identity 仍由运行中服务的 `LOANOPS_CHAT_PROVIDER`、`LOANOPS_CHAT_ADAPTER`、`LOANOPS_CHAT_MODEL` 决定；Terminal 不读取 Provider secret。

Streaming 主要改善 perceived latency / TTFT，不保证降低总请求耗时。`NOT_REQUIRED` 回答会真正增量输出；Policy `SUPPLEMENTAL` / `REQUIRED` 回答会在引用校验与 successful-turn atomic commit 后一次性显示。任何 `delta` 在 `done` 前都是 provisional；只有 `done` 且 `committed=true` 才表示服务端提交成功。若收到 `error` 或 stream 在 `done` 前异常结束，Terminal 不更新 current conversation ID。

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
