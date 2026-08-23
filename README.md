# LoanOps Agent

一个面向贷款贷后运营场景的 Java + Spring AI 只读诊断服务。

它解决的不是“让大模型替银行算账”，而是把确定性的还款计算留在 Java 业务层，再让 Agent 通过 Tool Calling 查询这些结果，并把结果解释成人能直接读懂的话。项目当前围绕三个问题展开：本期应还多少、为什么逾期、是否已经结清。

> 核心原则：**Java 算，AI 说。** 金额、日期、逾期天数和结清状态由确定性 Java 代码计算；LLM 只负责理解问题、选择只读 Tool 和组织解释。

## 当前状态

当前版本已经形成完整闭环：

- Java 21 + Spring Boot 3.4.5；
- H2 + MyBatis-Plus 保存贷款合同、还款计划和实际还款记录；
- `BigDecimal` 处理金额，注入 `Clock` 处理业务日期；
- 普通 REST 接口可完全绕过 AI 验证业务结果；
- Spring AI 1.1.1 + DeepSeek；
- 3 个只读 Tool：`getCurrentRepayment`、`getOverdueDiagnosis`、`getSettlementStatus`；
- `POST /api/agent/chat` 完成自然语言 → Tool Calling → Java 业务计算 → 自然语言解释；
- 自动化测试覆盖领域规则、数据约束、REST、Tool 和 Agent Controller；
- 提供可重复的固定业务日期演示脚本和 GitHub Actions CI。

## 为什么要把 Java 和 AI 分开

贷款金额和逾期状态属于确定性业务事实。如果把 `8500 - 5000 = 3500`、`2026-08-20 → 2026-08-23 = 3 天` 这类规则放进 Prompt，就会把可测试的业务规则变成概率模型的一部分，也会让模型切换影响业务结果。

LoanOps Agent 把职责拆成两层：Java 层负责事实，AI 层负责交互。这样即使未来更换模型，贷款状态的计算结果也不应该改变。

## 架构

```mermaid
flowchart TD
    U[用户 / API Client] --> AC[AgentController<br/>POST /api/agent/chat]
    U --> RC[LoanStatusController<br/>GET /api/loans/{loanNo}/status]

    AC --> AS[LoanOpsAgentService]
    AS --> CC[Spring AI ChatClient]
    CC --> LLM[LLM Provider<br/>DeepSeek: verified]
    LLM --> TC[Tool Calling]

    TC --> T1[getCurrentRepayment]
    TC --> T2[getOverdueDiagnosis]
    TC --> T3[getSettlementStatus]

    T1 --> LSS[LoanStatusService]
    T2 --> LSS
    T3 --> LSS
    RC --> LSS

    LSS --> LDS[LoanDiagnosisService]
    LDS --> CALC[RepaymentCalculator]
    LSS --> MP[MyBatis-Plus Mappers]
    MP --> H2[(H2)]

    CALC --> LDS
```

依赖方向刻意保持单向：`Agent → Tool → Service → Domain/Persistence`。Agent 和 Tool 都不直接访问 Mapper，也不复制金额或逾期算法。

更完整的分层与扩展说明见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

## 三个固定业务案例

| 贷款 | 问题 | 固定业务事实 | 期望结果 |
|---|---|---|---|
| `LN-10001` | 本期应该还多少钱？ | 本金 8000，利息 500，尚未还款，到期日 2026-08-30 | 应还 8500，未逾期 |
| `LN-10002` | 为什么逾期？ | 应还 8500，已还 5000，到期日 2026-08-20，业务日 2026-08-23 | 剩余 3500，逾期 3 天 |
| `LN-10003` | 是否已经结清？ | 两期均已足额还款 | `settled=true`，未结清金额 0 |

这些 fixture 只用于演示与测试，不代表真实银行贷款规则全集。

## 快速开始

### 1. 环境

需要：

- JDK 21；
- Maven 3.9+。

确认当前终端使用的是 JDK 21：

```bash
java -version
mvn -version
```

### 2. 运行测试

```bash
mvn clean verify
```

普通测试不会请求大模型 API，也不需要任何 API Key。

### 3. 启动确定性 REST 服务

```bash
mvn spring-boot:run
```

查询逾期案例：

```bash
curl http://localhost:8080/api/loans/LN-10002/status
```

如果希望演示结果始终固定在 `2026-08-23`，启动时显式钉住业务日期：

```bash
java -jar target/loanops-agent-0.0.1-SNAPSHOT.jar \
  --loanops.business-date=2026-08-23 \
  --loanops.business-zone=Asia/Shanghai
```

### 4. 启动 AI Agent

当前已验证 Provider 是 DeepSeek。API Key 只通过环境变量注入：

PowerShell：

```powershell
$env:DEEPSEEK_API_KEY="your-key"
$env:DEEPSEEK_MODEL="deepseek-chat"
mvn -Dspring-boot.run.profiles=ai spring-boot:run
```

请求：

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"LN-10002 为什么逾期？"}'
```

API Key 不写入 `application.yml`、README 或 Git 历史。

## 一键可复现验收

Windows / PowerShell：

```powershell
./scripts/verify-resume-mvp.ps1
```

脚本会：

1. 检查 Java 21；
2. 执行 `mvn clean verify`；
3. 用固定业务日期 `2026-08-23` 启动服务；
4. 验证 `LN-10001 / LN-10002 / LN-10003` 的确定性结果；
5. 结束临时服务进程。

如果要同时跑真实 DeepSeek Agent E2E：

```powershell
$env:DEEPSEEK_API_KEY="your-key"
./scripts/verify-resume-mvp.ps1 -WithAi
```

如果本机 Java 访问外网必须经过代理，可以显式传入：

```powershell
./scripts/verify-resume-mvp.ps1 -WithAi -ProxyHost 127.0.0.1 -ProxyPort 7890
```

演示细节见 [`docs/DEMO.md`](docs/DEMO.md)。

## Provider 边界

业务代码不依赖 DeepSeek SDK，而是依赖 Spring AI 的 `ChatClient / ChatModel` 抽象。因此 Provider 更换不应该进入 `RepaymentCalculator`、`LoanDiagnosisService`、Tool 或 Controller。

当前状态必须区分“已验证”和“可扩展”：

| Provider | 当前代码状态 | Tool Calling E2E |
|---|---|---|
| DeepSeek | 已接入 | 已验证 |
| Qwen | 仅定义扩展边界，未接入 | 未验证 |
| GLM | 仅定义扩展边界，未接入 | 未验证 |

Qwen 和 GLM 均提供 OpenAI-compatible Chat API，可以作为后续独立 Provider Adapter 接入；在真实 E2E 验证前，本项目不宣称已经支持它们。详细设计见 [`docs/PROVIDERS.md`](docs/PROVIDERS.md)。

## 只读安全边界

当前 Agent 只有查询工具，没有任何写工具。

允许：

- 查询本期应还、已还、剩余金额；
- 查询逾期状态与逾期天数；
- 查询是否结清。

不允许：

- 新增或修改还款记录；
- 修改贷款、还款计划或结清状态；
- 审批、放款、核销、催收或执行交易；
- 让 LLM 自己创造新的贷款业务规则。

即使 Prompt 被要求“把 LN-10002 标记为已结清”，当前 Agent 也没有对应写入 Tool，数据库状态不会因为自然语言请求被修改。

## 项目边界

这是一个聚焦“贷后还款诊断”的工程样例，不是完整信贷核心系统。当前故意不实现：

- 授信、审批、放款、信用评分；
- 罚息、复利、宽限期、节假日顺延；
- 提前还款、冲正、展期、核销；
- 登录、RBAC、用户中心；
- RAG、向量数据库、MCP、Multi-Agent；
- 自动执行任何金融写操作。

完整领域规则见 [`docs/DOMAIN.md`](docs/DOMAIN.md)。

## 技术栈

| 层 | 技术 |
|---|---|
| Runtime | Java 21 |
| Web | Spring Boot 3.4.5 / Spring MVC |
| AI | Spring AI 1.1.1 / DeepSeek |
| Persistence | MyBatis-Plus 3.5.16 / H2 |
| Domain | BigDecimal / LocalDate / Clock |
| Test | JUnit 5 / Spring Boot Test / MockMvc |
| Build | Maven |
| CI | GitHub Actions |

## 测试与验收

本项目的验收不是“接口能返回 200”就结束，而是逐层验证：

- Domain：金额、当前期次、逾期、结清；
- Persistence：主外键与跨贷款还款记录约束；
- REST：不经过 LLM 也能得到正确事实；
- Tool：3 个 Tool 只调用业务 Service；
- Agent：自然语言问题能触发正确 Tool；
- Safety：不存在贷款不编造，写请求不改变数据库。

当前验收快照见 [`docs/ACCEPTANCE_SNAPSHOT.md`](docs/ACCEPTANCE_SNAPSHOT.md)。

## 后续扩展

如果继续扩展，优先保持“新增能力而不是改写已有核心”：

- H2 → MySQL Profile；
- DeepSeek → 可验证的 Qwen / GLM Provider Adapter；
- Agent 调用审计与可观测性；
- Docker 化；
- 更系统的 Agent Evaluation。

任何新增金融规则都应先补充领域定义和测试，再开放给 Agent 使用。
