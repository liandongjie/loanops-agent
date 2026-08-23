# LoanOps Agent

一个用 Java 计算贷款还款事实、用 Spring AI + DeepSeek 做自然语言查询的只读贷后诊断 Demo。

项目刻意把 LLM 放在业务规则之外：金额、逾期天数、结清状态都由 Java 计算；模型只负责理解问题、选择 Tool 和组织回答。这样即使以后换模型，贷款事实也不应该跟着变化。

## 已实现

- 贷款合同、还款计划、还款记录三张表，H2 + MyBatis-Plus 持久化；
- `BigDecimal` 金额计算和可注入 `Clock`；
- `GET /api/loans/{loanNo}/status` 确定性查询接口；
- 3 个只读 Tool：`getCurrentRepayment`、`getOverdueDiagnosis`、`getSettlementStatus`；
- `POST /api/agent/chat` 自然语言查询；
- DeepSeek Tool Calling 已做真实 E2E 验证；
- Maven 自动测试、GitHub Actions 和一键验收脚本。

## 本地启动

### 环境

- JDK 21
- Maven 3.9+

先确认当前终端使用的是 Java 21：

```powershell
java -version
mvn -version
```

### 1. 先跑普通 REST

不启用 AI，也不需要 API Key：

```powershell
# 8080 被占用时可以换端口；下面统一用 18080 演示
$env:SERVER_PORT = "18080"

mvn spring-boot:run
```

另开一个 PowerShell：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:18080/api/loans/LN-10002/status" |
  ConvertTo-Json -Depth 10
```

固定测试数据下，`LN-10002` 的核心结果是：应还 `8500`、已还 `5000`、剩余 `3500`。

### 2. 再启用 Agent

PowerShell 下不要使用未加引号的 `-Dspring-boot.run.profiles=ai`。最稳妥的方式是直接设置 Spring Profile 环境变量：

```powershell
$env:SERVER_PORT = "18080"
$env:SPRING_PROFILES_ACTIVE = "ai"
$env:DEEPSEEK_API_KEY = "your-key"
$env:DEEPSEEK_MODEL = "deepseek-chat"

mvn spring-boot:run
```

API Key 只通过环境变量传入，不要写进 `application.yml` 或提交到 Git。

请求 Agent：

```powershell
$body = @{
    message = "LN-10002 为什么逾期？"
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:18080/api/agent/chat" `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) |
    ConvertTo-Json -Depth 10
```

一次真实调用中，Agent 会选择 `getOverdueDiagnosis`，再基于 Java 返回的 `8500 / 5000 / 3500 / 3 天`组织解释。

如果 Java 访问模型 API 需要本机代理，见 [Demo 文档](docs/DEMO.md) 中的 JVM 代理说明。

## 架构

```mermaid
graph TD
    Client["API Client"] --> Rest["LoanStatusController"]
    Client --> Agent["AgentController"]
    Agent --> AgentService["LoanOpsAgentService"]
    AgentService --> ChatClient["Spring AI ChatClient"]
    ChatClient --> Model["DeepSeek"]
    Model --> Tools["LoanOpsTools"]
    Tools --> StatusService["LoanStatusService"]
    Rest --> StatusService
    StatusService --> Diagnosis["LoanDiagnosisService"]
    Diagnosis --> Calculator["RepaymentCalculator"]
    StatusService --> Mapper["MyBatis-Plus Mappers"]
    Mapper --> Database["H2"]
```

依赖方向只有一条：Agent / Tool 可以调用业务服务，但不能直接访问 Mapper，也不能重新实现贷款计算。

更完整的分层和扩展说明见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 三个固定案例

| 贷款 | 场景 | 预期结果 |
|---|---|---|
| `LN-10001` | 本期应该还多少钱 | 应还 `8500`，未逾期 |
| `LN-10002` | 为什么逾期 | 剩余 `3500`，在业务日 `2026-08-23` 时逾期 `3` 天 |
| `LN-10003` | 是否已经结清 | `settled=true`，未偿金额 `0` |

这些数据来自 `src/main/resources/data.sql`，只用于 Demo 和测试，不代表真实银行完整业务规则。

## 业务规则放在哪里

确定性规则只在 Java Domain / Service 中实现。例如：

```text
dueAmount        = principalDue + interestDue
paidAmount       = sum(payment records for the repayment plan)
outstandingAmount = max(dueAmount - paidAmount, 0)
overdue          = outstandingAmount > 0 && asOfDate > dueDate
```

Tool 不计算金额，只调用已有服务；Prompt 里也不复制这些公式。

## 测试和可复现验收

普通测试：

```powershell
mvn clean verify
```

当前基线是 `23` 个测试，覆盖 Domain、数据库约束、REST、Tool、Agent Controller 和时间配置。

一键跑固定业务案例：

```powershell
.\scripts\verify-resume-mvp.ps1
```

连真实 DeepSeek 一起验证：

```powershell
$env:DEEPSEEK_API_KEY = "your-key"
.\scripts\verify-resume-mvp.ps1 -WithAi
```

如果 JVM 需要走本机 `127.0.0.1:7890` 代理：

```powershell
.\scripts\verify-resume-mvp.ps1 `
  -WithAi `
  -ProxyHost 127.0.0.1 `
  -ProxyPort 7890
```

验收脚本除了检查三个业务案例，还会检查真实 Tool Calling、未知贷款不编造结果，以及写请求不会改变贷款状态。

## Provider

当前只把 **DeepSeek** 标记为已验证。Qwen 和 GLM 还没有完成真实接入与 E2E，因此 README 不宣称已经支持它们。

Provider 设计边界和后续接入方式见 [docs/PROVIDERS.md](docs/PROVIDERS.md)。

## 当前边界

这是一个小型贷后诊断项目，不是贷款核心系统。目前没有实现授信审批、评分、放款、催收、罚息、提前还款、RBAC、RAG、MCP 或写操作 Tool。

这样做是有意的：先把“数据库事实 -> Java 计算 -> Tool -> LLM 解释”这一条链路做完整，再决定是否扩展业务范围。

## 主要代码

```text
src/main/java/com/loanops/
├── agent/          # Agent HTTP 入口和 ChatClient 调用
├── controller/     # 确定性 REST
├── domain/         # 贷款领域对象
├── persistence/    # MyBatis-Plus Entity / Mapper
├── service/        # 确定性业务计算和查询编排
└── tool/           # Spring AI 只读 Tools
```

更详细的规则、验收和演示步骤：

- [领域规则](docs/DOMAIN.md)
- [架构说明](docs/ARCHITECTURE.md)
- [Demo](docs/DEMO.md)
- [Provider 边界](docs/PROVIDERS.md)
- [验收快照](docs/ACCEPTANCE_SNAPSHOT.md)