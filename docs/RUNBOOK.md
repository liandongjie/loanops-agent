# LoanOps Agent 本地运行与验收手册

这份文档用于回答：**怎样把项目跑起来、怎样验证关键能力、常见问题怎么排查。**

所有命令默认从仓库根目录使用 **PowerShell 7** 执行。

## 1. 环境要求

基础环境：

- JDK 21；
- Maven 3.9+；
- PowerShell 7。

如果要运行完整 Policy RAG，还需要：

- Docker；
- MySQL 8（Docker Compose）；
- Qdrant v1.15.4（Docker Compose）；
- 本机 Ollama；
- BGE-M3；
- 对应 Chat Model 的 API Key，或本地 Ollama 模型。

先确认：

```powershell
java -version
mvn -version
docker version
ollama --version
```

Maven 必须实际使用 Java 21。

## 2. 最快体验完整对话

### 2.1 启动 MySQL 和 Qdrant

```powershell
docker compose up -d mysql qdrant
docker compose ps
```

默认地址：

```text
MySQL   127.0.0.1:3307
Qdrant  127.0.0.1:6333
```

Ollama 由本机安装提供，不在 Compose 中。

### 2.2 准备 BGE-M3

```powershell
ollama pull bge-m3
ollama list
```

BGE-M3 用于政策文本和查询的 Embedding，不是聊天模型。

### 2.3 初始化演示 Policy

```powershell
./scripts/bootstrap-local-policy.ps1
```

成功时会看到类似：

```text
LOCAL_POLICY_BOOTSTRAP_SUCCESS documents=4 versions=5 chunks=41 indexed=41
```

这个脚本会：

- 把演示 Policy 写入本地 MySQL；
- 通过现有 Policy ingestion 逻辑处理文档和条款；
- 用 BGE-M3 生成向量并重建 Qdrant 索引；
- 重复执行时保持幂等，不产生重复数据；
- 如果发现不属于当前 Demo 的 Policy 数据，直接拒绝执行，避免覆盖未知数据。

它不会在普通应用启动时自动清库、自动 seed 或自动 rebuild。

### 2.4 启动 Agent

DeepSeek：

```powershell
./scripts/run-agent.ps1 -Provider deepseek -WithPolicy
```

Ollama / qwen3:4b：

```powershell
./scripts/run-agent.ps1 -Provider ollama -WithPolicy
```

GLM：

```powershell
./scripts/run-agent.ps1 -Provider glm -WithPolicy
```

DeepSeek / GLM 的 Key 可以放在仓库根目录本地 `.env` 中，参考 `.env.example`。`.env` 保持 gitignored。

### 2.5 打开 Terminal Chat

新开一个 PowerShell：

```powershell
./scripts/chat.ps1
```

建议按顺序尝试：

```text
LN-10002 为什么逾期？
按照规定现在应该怎么处理？
那他现在还欠多少钱？
这个规定具体是哪一条？
```

预期行为：

1. 第一问查询 `LN-10002` 的真实业务数据并解释逾期原因；
2. 第二问检索适用的政策条款，并在回答中给出 `[P1]` 等引用；
3. 第三问能理解“他”仍然指 `LN-10002`，但会重新查询当前业务数据；
4. 第四问能继续解释上一轮引用的具体政策条款。

Terminal 命令：

```text
/new    开始一个新的客户端会话
/id     查看当前 conversationId
/exit   退出
```

## 3. 查看多轮对话是否真的保存到了 MySQL

进入 MySQL：

```powershell
docker compose exec `
    -e MYSQL_PWD=loanops_dev `
    mysql `
    mysql --default-character-set=utf8mb4 -uloanops loanops
```

查看数量：

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

查看指定 Conversation：

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

MySQL 使用 Docker named volume 保存数据。普通：

```powershell
docker compose down
```

会停止容器，但保留 volume。

下面这个命令会删除 volume，不要把它当普通清理命令：

```powershell
docker compose down -v
```

## 4. 检查 Policy 数据和 Qdrant

MySQL：

```sql
SELECT COUNT(*) FROM policy_document;
SELECT COUNT(*) FROM policy_document_version;
SELECT COUNT(*) FROM policy_chunk;
```

当前 Demo corpus 预期：

```text
documents = 4
versions  = 5
chunks    = 41
```

查看 Qdrant collection：

```powershell
Invoke-RestMethod http://localhost:6333/collections/loanops_policy_chunks
```

对于只有几十个向量的小 collection，`indexed_vectors_count=0` 不代表没有向量；应结合 `points_count` 判断实际 point 是否存在。

## 5. 先跑不依赖外部大模型的自动测试

```powershell
mvn clean verify
```

这条命令主要验证 Java / Spring / H2 的确定性逻辑，不要求真实 Chat Model、Qdrant 或 Ollama 全部在线。

某些依赖真实外部环境的测试是显式开启的。如果环境不完整而被跳过，不能把“skipped”当成真实端到端测试已通过。

MySQL 集成路径可以单独运行：

```powershell
./scripts/verify-mysql.ps1
```

## 6. 三个模型共用同一套 Agent 测试

项目固定了 6 个 Agent 测试问题，让 DeepSeek、Ollama 和 GLM 都跑同一套用例。

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

如果当前模型、API Key 或 Ollama 服务不可用，脚本会报告：

```text
ENV_BLOCKED
```

这表示“环境没准备好”，不是 PASS，也不是 FAIL。

具体测试内容见 [EVALUATION.md](EVALUATION.md)。

## 7. 真实 Policy RAG 端到端测试

这项测试会把真实 MySQL、BGE-M3、Qdrant、Chat Model、Tool、Conversation 和 Audit 串在一起。

先准备：

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

测试重点不是比较完整自然语言，而是检查：

- 第一轮是否真的调用逾期 Tool；
- 金额和逾期事实是否来自 Java；
- 第二轮能否利用上一轮上下文识别贷款；
- 是否真的检索到适用政策；
- 回答中的 `[P1]` 是否对应真实命中条款；
- Agent、Tool、Policy Audit 是否能够关联起来；
- Policy Embedding 是否仍然使用 BGE-M3。

## 8. Policy RAG 专项评测

```powershell
./scripts/evaluate-policy-rag.ps1 -Label e0 -Threshold 0.0
./scripts/evaluate-policy-rag.ps1 -Label e1 -Threshold 0.60
./scripts/evaluate-policy-rag.ps1 -Label e2 -Threshold 0.60
```

如果要让 DeepSeek 真实生成答案并做人工复查：

```powershell
./scripts/evaluate-policy-rag.ps1 -Label e2 -Threshold 0.60 -ManualReview
```

评测会使用隔离的 MySQL schema 和 Qdrant collection，结束后清理，不会把测试数据混进普通本地 Policy 库。

指标和 E0 → E1 → E2 的变化见 [POLICY_RAG_EVALUATION.md](POLICY_RAG_EVALUATION.md)。

## 9. SSE 流式响应

Terminal 默认调用：

```http
POST /api/agent/chat/stream
```

同步接口仍然保留：

```http
POST /api/agent/chat
```

不需要政策的回答可以边生成边显示。

需要政策引用的回答会先在服务端完成生成、引用校验和成功提交，再把最终结果发给客户端。这样可以避免客户端已经看到一段政策回答，服务端最后才发现引用是错的。

只有收到：

```text
done + committed=true
```

才表示这一轮在服务端正式保存成功。

## 10. 常见问题

### Maven 用错了 Java 版本

```powershell
java -version
mvn -version
```

确认 Maven 实际使用 Java 21。

### Policy RAG 没有命中

依次确认：

```text
MySQL 中是否已有 Policy document / version / chunk
Ollama 是否运行
bge-m3 是否已经安装
Qdrant 是否健康
启动 Agent 时是否加了 -WithPolicy
```

### 中文在 mysql CLI 显示成 `????`

使用：

```powershell
mysql --default-character-set=utf8mb4 ...
```

必要时：

```sql
SELECT title, HEX(title) FROM policy_document;
```

可以区分“终端显示问题”和“数据库里真的存成问号”。

### PowerShell 能访问大模型，但 Java 超时

检查 JVM / 系统代理配置。不要通过改业务逻辑绕过本机网络问题。

## 11. 清理

停止 MySQL 和 Qdrant，同时保留数据：

```powershell
docker compose down
```

如果没有明确需求，不要在验收脚本里自动删除 volumes。
