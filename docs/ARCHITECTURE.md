# LoanOps Agent — Architecture

## 1. 架构目标

本项目的架构不是追求组件数量，而是保证：

1. Java 是核心；
2. 金融业务逻辑确定、可测试；
3. AI 只位于最外层；
4. 业务逻辑只有一个事实来源；
5. Agent Tool 全部只读；
6. 每一层可以单独验证；
7. 1–2 天内可形成 Resume MVP。

---

## 2. 核心架构原则

### Principle 1：Java 算，AI 说

金融事实由 Java 计算：

- 应还金额；
- 已还金额；
- 未还金额；
- 当前待处理期次；
- 是否逾期；
- 逾期天数；
- 是否结清。

AI 只负责：

- 理解问题；
- 选择 Tool；
- 使用 Tool 返回结果组织自然语言说明。

---

### Principle 2：业务逻辑只能存在一份

业务规则只能位于确定性的 Java Domain / Service 层。

禁止：

- Controller 重复计算；
- Tool 重复计算；
- Prompt 重复计算；
- Agent 自行计算；
- Repository 承担业务判断。

---

### Principle 3：AI Tool 全部只读

所有 Tool 仅提供查询/诊断能力。

Tool 不得：

- 写数据库；
- 修改贷款；
- 修改还款计划；
- 创建还款记录；
- 更新状态；
- 执行任何金融动作。

---

### Principle 4：先验证 Java，再验证 Agent

必须保留普通 REST 查询能力，使开发者可以不经过 AI 验证 Java 业务结果。

这样出现问题时可快速区分：

```text
Java Domain 错误
```

还是：

```text
Agent / Tool Calling 错误
```

---

## 3. MVP 逻辑架构

```text
                 ┌─────────────────────┐
                 │ Swagger / Postman   │
                 └──────────┬──────────┘
                            │
               ┌────────────┴─────────────┐
               │                          │
               ▼                          ▼
      LoanStatusController        AgentController
               │                 POST /api/agent/chat
               │                          │
               ▼                          ▼
       LoanDiagnosisService       LoanOpsAgentService
               │                          │
               │                    Spring AI ChatClient
               │                          │
               │                    Tool Calling
               │             ┌────────────┼────────────┐
               │             ▼            ▼            ▼
               │      CurrentRepay-  Overdue-     Settlement-
               │      mentTool       Diagnosis    StatusTool
               │             │            │            │
               └─────────────┴────────────┴────────────┘
                                      │
                                      ▼
                             LoanDiagnosisService
                                      │
                                      ▼
                            RepaymentCalculator
                                      │
                                      ▼
                            Mapper / Repository
                                      │
                                      ▼
                                     H2
```

---

## 4. 推荐代码分层

最终包结构可以按职责组织，例如：

```text
src/main/java/.../loanops/
├── LoanOpsApplication.java
├── config/
├── controller/
├── domain/
├── dto/
├── mapper/
├── service/
├── agent/
└── tool/
```

推荐职责：

### `domain/`

保存核心领域对象：

- `LoanContract`
- `RepaymentPlan`
- `PaymentRecord`

不包含 Spring AI 逻辑。

---

### `service/`

至少包含：

- `RepaymentCalculator`
- `LoanDiagnosisService`

负责全部确定性业务规则。

---

### `mapper/`

负责 H2 数据读写。

Mapper 不负责计算：

- overdue；
- outstanding；
- settled。

---

### `controller/`

普通 REST Controller。

作用：

- 调用 Service；
- 返回 DTO；
- 支持无 AI 验证。

Controller 不实现业务计算。

---

### `tool/`

只读 Spring AI Tools。

MVP 固定 3 个能力：

```text
getCurrentRepayment(loanNo)
getOverdueDiagnosis(loanNo)
getSettlementStatus(loanNo)
```

每个 Tool 只调用现有 `LoanDiagnosisService`。

---

### `agent/`

负责：

- Spring AI ChatClient；
- 系统指令；
- 注册 Tools；
- 用户问题处理；
- 返回自然语言诊断。

Agent 不直接访问 Mapper。

---

## 5. 依赖方向

允许：

```text
Controller
  ↓
Service
  ↓
Mapper
```

允许：

```text
Agent
  ↓
Tool
  ↓
Service
  ↓
Mapper
```

禁止：

```text
Agent
  ↓
Mapper
```

禁止：

```text
Tool
  ↓
Mapper
```

禁止：

```text
Mapper
  ↓
Service
```

禁止循环依赖。

---

## 6. 普通 REST API

必须至少保留一个不经过 AI 的诊断入口。

推荐：

```http
GET /api/loans/{loanNo}/status
```

该接口返回确定性业务结果。

目的：

1. 验证 Java 领域逻辑；
2. 支持自动化测试；
3. Agent 出错时可独立定位问题；
4. 证明项目不是只有 LLM 封装。

---

## 7. Agent API

MVP 只需要一个自然语言入口：

```http
POST /api/agent/chat
```

请求示例：

```json
{
  "message": "LN-10002 为什么逾期？"
}
```

回答可以是自然语言，也可以在后续迭代中增加结构化字段。

Stop Point A 不要求复杂会话管理。

---

## 8. Tool 设计

### 8.1 getCurrentRepayment

职责：

- 查询当前待处理期次；
- 返回本金、利息、应还、已还、未还、到期日。

不负责：

- 生成自然语言答案；
- 判断用户意图；
- 修改数据。

---

### 8.2 getOverdueDiagnosis

职责：

返回：

- dueAmount；
- paidAmount；
- outstandingAmount；
- dueDate；
- asOfDate；
- overdue；
- overdueDays。

业务计算必须来自 `LoanDiagnosisService`。

---

### 8.3 getSettlementStatus

职责：

返回：

- settled；
- totalOutstanding；
- 必要的还款计划汇总信息。

---

## 9. 数据库策略

### Resume MVP

使用：

```text
H2
```

原因：

- 零外部依赖；
- clone 后快速运行；
- 适合固定 Fixture；
- 节省 1–2 天开发中的环境成本。

### Future Work

后续可以增加：

```text
application-mysql.yml
```

支持 MySQL。

在 MySQL 未实际实现前，简历和 README 不得声称已使用 MySQL。

---

## 10. 时间策略

业务时间必须使用可注入 `Clock`。

推荐：

```text
生产：system Clock
测试：fixed Clock
```

核心 Case B 固定：

```text
2026-08-23
```

这样 `overdueDays = 3` 不会随着真实日期变化。

---

## 11. AI 模型策略

Resume MVP 只接入一个模型。

优先：

```text
DeepSeek
```

通过 Spring AI 官方支持方式配置。

不得在 MVP 中增加：

- 多模型路由；
- fallback 模型；
- 动态模型切换；
- 本地模型部署。

---

## 12. 技术选型

MVP：

- Java 21
- Spring Boot
- Spring MVC
- Spring AI
- DeepSeek
- MyBatis-Plus
- H2
- Maven
- JUnit 5
- Swagger / OpenAPI（如依赖兼容且引入成本低）

金额：

- BigDecimal

日期：

- LocalDate
- Clock

---

## 13. 架构演进原则

Stop Point A 后，如新增 MySQL、MCP 或 Audit，应保证：

```text
Domain Service
```

无需因基础设施变化重写核心业务规则。

例如：

```text
H2 Mapper
   ↓
未来替换/增加 MySQL
```

不应影响：

```text
RepaymentCalculator
LoanDiagnosisService
```

这保证核心业务逻辑与基础设施解耦。
