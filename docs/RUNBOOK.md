# LoanOps Agent 本地运行与验收手册

本文档给出从 fresh local environment 到手工对话、自动测试和专项评测的可复现路径。

所有命令默认从仓库根目录使用 **PowerShell 7** 执行。

## 1. 环境要求

基础：

- JDK 21；
- Maven 3.9+；
- PowerShell 7。

完整 Policy RAG / Hero：

- Docker；
- MySQL 8（Compose）；
- Qdrant v1.15.4（Compose）；
- 本机 Ollama；
- `bge-m3`；
- 对应 Chat Provider 的 API Key 或本地模型。

先确认：

```powershell
java -version
mvn -version
docker version
ollama --version
```

不要降低 `pom.xml` 的 Java 21 target。

如果本机有多个 JDK，可先显式设置：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## 2. 最快手工体验路径

如果目标是亲自体验完整的 Tool + Conversation + Policy RAG：

### 2.1 启动 MySQL 和 Qdrant

```powershell
docker compose up -d mysql qdrant
docker compose ps
```

默认：

```text
MySQL   127.0.0.1:3307
Qdrant  127.0.0.1:6333
```

Ollama 不在 Compose 中，由本机安装提供。

### 2.2 准备 BGE-M3

```powershell
ollama pull bge-m3
ollama list
```

### 2.3 初始化 Demo Policy

```powershell
./scripts/bootstrap-local-policy.ps1
```

成功结果类似：

```text
LOCAL_POLICY_BOOTSTRAP_SUCCESS documents=4 versions=5 chunks=41 indexed=41
```

该 Bootstrap：

- 使用普通本地 MySQL 作为 canonical Policy Store；
- 复用现有 `PolicyIngestionService`；
- 复用现有 `PolicyIndexRebuilder`；
- 可重复执行且不产生重复数据；
- 可以恢复缺失的 Demo document/version/chunk；
- 发现未知 / 非 Demo Policy 时拒绝写入和 rebuild；
- 不支持 force/reset/delete；
- 不会在日常应用启动时自动 seed。

### 2.4 启动 Agent

DeepSeek：

```powershell
./scripts/run-agent.ps1 -Provider deepseek -WithPolicy
```

Ollama：

```powershell
./scripts/run-agent.ps1 -Provider ollama -WithPolicy
```

GLM：

```powershell
./scripts/run-agent.ps1 -Provider glm -WithPolicy
```

`.env.example` 展示安全配置项。仓库根目录本地 `.env` 保持 gitignored，可配置：

```text
DEEPSEEK_API_KEY
GLM_API_KEY
```

Launcher 不打印 secret。

### 2.5 Terminal Chat

新开 PowerShell：

```powershell
./scripts/chat.ps1
```

推荐按顺序尝试：

```text
LN-10002 为什么逾期？
按照规定现在应该怎么处理？
那他现在还欠多少钱？
这个规定具体是哪一条？
```

预期稳定行为：

1. 第一问调用 `getOverdueDiagnosis`，贷款金额与逾期事实来自 Java Tool；
2. 第二问触发 `SUPPLEMENTAL` Policy Retrieval，命中证据并包含有效 `[P1]`；
3. 第三问从 Conversation 识别 `LN-10002`，但重新查询当前 Tool；
4. 第四问能够继续解释上一轮 Policy Evidence。

自然语言措辞不要求字节级一致。

Terminal 命令：

```text
/new    清空客户端当前 conversationId，不删除服务端历史
/id     显示当前 conversationId
/exit   退出
```

## 3. 查看持久化 Conversation

进入 MySQL：

```powershell
docker compose exec `
    -e MYSQL_PWD=loanops_dev `
    mysql `
    mysql --default-character-set=utf8mb4 -uloanops loanops
```

查看会话数量：

```sql
SELECT COUNT(*) FROM conversation;
SELECT COUNT(*) FROM conversation_message;
```

查看最近消息：

```sql
SELECT
    conversation_id,
    sequence_no,
    role,
    content,
    created_at
FROM conversation_message
ORDER BY created_at DESC
LIMIT 20;
```

按一个 conversation 查看完整顺序：

```sql
SELECT
    sequence_no,
    role,
    content,
    created_at
FROM conversation_message
WHERE conversation_id = 'your-conversation-id'
ORDER BY sequence_no;
```

MySQL 使用 Docker named volume：

```text
loanops_mysql_data
```

`docker compose stop` 或普通 `docker compose down` 不会删除该 volume。

不要把下面的命令当成普通清理命令：

```powershell
docker compose down -v
```

它会删除 Compose volumes。

## 4. 检查 Policy 数据与 Qdrant

MySQL：

```sql
SELECT COUNT(*) FROM policy_document;
SELECT COUNT(*) FROM policy_document_version;
SELECT COUNT(*) FROM policy_chunk;
```

Demo Bootstrap 的当前 corpus 预期为：

```text
documents = 4
versions  = 5
chunks    = 41
```

Qdrant：

```powershell
(Invoke-RestMethod http://localhost:6333/collections).result.collections |
    Select-Object name
```

查看 collection：

```powershell
Invoke-RestMethod http://localhost:6333/collections/loanops_policy_chunks
```

小规模 collection 的 `indexed_vectors_count=0` 不等价于“没有向量”；应结合 `points_count` / count API 判断 point 是否存在。

## 5. 自动测试

### 5.1 默认 deterministic verification

```powershell
mvn clean verify
```

该路径以 H2 为主，不要求真实 Chat Provider、Qdrant 或 Ollama。

外部 E2E tests 通过显式环境开关 opt-in，环境不完整时不得把 skipped 当 PASS。

### 5.2 贷款 fixture smoke

```powershell
./scripts/verify-resume-mvp.ps1
```

用于验证固定贷款业务 fixture 和早期最小回归。

它不是 Policy RAG Hero 的替代品。

### 5.3 MySQL / Flyway

```powershell
./scripts/verify-mysql.ps1
```

用于验证真实 MySQL / Flyway 路径和确定性业务查询。

## 6. 模型 Provider 基线回归

同一份六用例 manifest 用于 DeepSeek、Ollama 和 GLM。

DeepSeek：

```powershell
./scripts/evaluate-agent-baseline.ps1 -Provider deepseek
```

Ollama：

```powershell
ollama pull qwen3:4b
./scripts/evaluate-agent-baseline.ps1 -Provider ollama
```

GLM：

```powershell
./scripts/evaluate-agent-baseline.ps1 -Provider glm
```

如果依赖不满足，runner 必须报告 `ENV_BLOCKED`，不能替换模型或把普通 Chat 输出当成 Tool Calling PASS。

完整评测语义见 [EVALUATION.md](EVALUATION.md)。

## 7. Policy Hero 端到端验收

依赖：

```powershell
docker compose up -d mysql qdrant
ollama pull bge-m3
$env:POLICY_AGENT_REAL_E2E_TEST = "true"
```

DeepSeek：

```powershell
$env:LOANOPS_CHAT_PROVIDER = "deepseek"
$env:LOANOPS_CHAT_ADAPTER = "deepseek"
$env:LOANOPS_CHAT_MODEL = "deepseek-chat"
mvn "-Dtest=PolicyAgentRealE2EIntegrationTest" test
```

Ollama：

```powershell
$env:LOANOPS_CHAT_PROVIDER = "ollama"
$env:LOANOPS_CHAT_ADAPTER = "ollama"
$env:LOANOPS_CHAT_MODEL = "qwen3:4b"
mvn "-Dtest=PolicyAgentRealE2EIntegrationTest" test
```

GLM：

```powershell
$env:LOANOPS_CHAT_PROVIDER = "glm"
$env:LOANOPS_CHAT_ADAPTER = "zhipuai"
$env:LOANOPS_CHAT_MODEL = "glm-5.2"
mvn "-Dtest=PolicyAgentRealE2EIntegrationTest" test
```

稳定断言包括：

- Turn 1 Tool Calling 成功；
- 金融事实来自 Java；
- Turn 2 从 prior USER context 识别 loan；
- Policy decision = `SUPPLEMENTAL`；
- retrieval = `MATCHED`；
- 回答包含有效 `[P1]`；
- Agent / Tool / Policy Audit 可关联；
- transcript 只包含 USER / ASSISTANT；
- Policy embedding model 仍为 BGE-M3。

测试不固定完整自然语言答案。

## 8. Policy RAG 专项评测

依赖 Java 21、MySQL、Qdrant、Ollama / BGE-M3。

```powershell
./scripts/evaluate-policy-rag.ps1 -Label e0 -Threshold 0.0
./scripts/evaluate-policy-rag.ps1 -Label e1 -Threshold 0.60
./scripts/evaluate-policy-rag.ps1 -Label e2 -Threshold 0.60
```

真实 DeepSeek manual review：

```powershell
./scripts/evaluate-policy-rag.ps1 -Label e2 -Threshold 0.60 -ManualReview
```

runner 使用隔离的 MySQL schema / Qdrant collection，并在结束时清理。

指标定义和 E0 → E1 → E2 过程见 [POLICY_RAG_EVALUATION.md](POLICY_RAG_EVALUATION.md)。

## 9. Streaming 行为

Terminal 默认调用：

```http
POST /api/agent/chat/stream
```

同步接口仍然保留：

```http
POST /api/agent/chat
```

`NOT_REQUIRED` turn 可以增量显示 provisional delta。

`SUPPLEMENTAL / REQUIRED` Policy turn 会在引用校验与 successful-turn commit 后下发安全结果。

只有：

```text
done + committed=true
```

表示服务端 turn 正式成功。

如果流在 `done` 前异常结束，客户端不应假定 Conversation 已成功提交。

## 10. 常见问题

### Java 版本不对

```powershell
java -version
mvn -version
```

确认 Maven 实际使用 Java 21。

### Policy RAG 没有命中

依次确认：

```text
MySQL 是否有 Policy document/version/chunk
Ollama 是否运行
bge-m3 是否存在
Qdrant 是否健康
-WithPolicy 是否启用
```

### 中文在 mysql CLI 显示为 `????`

使用：

```powershell
mysql --default-character-set=utf8mb4 ...
```

必要时通过：

```sql
SELECT title, HEX(title) FROM policy_document;
```

区分“终端显示问题”和“数据库实际存成问号”。

### PowerShell 能访问 Provider，但 Java 超时

检查 JVM / 系统代理配置。不要通过修改业务代码绕过网络环境问题。

## 11. 清理

停止本地基础设施，同时保留数据 volume：

```powershell
docker compose down
```

不要在常规验收中自动删除 volumes，因为其中可能包含本地 Conversation、Audit 与 Demo Policy 数据。
