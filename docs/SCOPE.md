# LoanOps Agent — Scope

> 本文的 Phase 0-5 / Resume MVP 段落保留为历史初始范围记录。当前生效范围以
> “Current Approved Scope”为准；后续 Phase 的明确批准扩展了基础设施、会话、
> 评测和 Policy RAG，但没有扩大确定性贷款业务规则或 Agent 写权限。

## 1. 项目定位

LoanOps Agent 是一个面向贷后运营场景的**可审计、可评测、只读智能诊断 Agent**。

项目聚焦于贷后运营中的还款、逾期与政策诊断子域：

> 基于贷款合同、分期还款计划和实际还款记录，使用确定性的 Java 业务逻辑计算本期应还、未还、逾期状态与结清状态；再由 Spring AI Agent 通过只读 Tool Calling 查询这些业务事实，并向运营人员解释结果。

本项目不是完整贷款核心系统，也不尝试模拟真实银行全部信贷流程。

---

## 2. 为什么做这个项目

### 2.1 业务问题

在贷后运营场景中，运营人员面对一笔贷款时，经常需要回答：

1. 本期应该还多少钱？
2. 当前还了多少、还欠多少？
3. 为什么系统判定为逾期？
4. 已经逾期多少天？
5. 这笔贷款是否已经全部结清？

这些答案通常需要结合贷款合同、还款计划和实际还款记录进行计算。

### 2.2 技术问题

传统 REST API 可以返回数据，但运营人员仍需手工理解多个字段和业务规则。

本项目增加一个自然语言诊断入口：

```text
用户问题
  ↓
Spring AI Agent
  ↓
选择只读 Tool
  ↓
Java Service 返回确定性业务事实
  ↓
Agent 组织自然语言解释
```

AI 不负责决定金融事实，只负责理解问题、选择工具和解释已经计算出的结果。

### 2.3 求职目标

本项目用于证明以下能力：

- Java 21 基础与工程开发能力；
- Spring Boot 分层开发能力；
- BigDecimal、LocalDate、Clock 等 Java 后端基础实践；
- 金融金额与日期逻辑的确定性处理；
- 单元测试与可验证业务规则；
- Spring AI；
- Tool Calling；
- 金融业务中的 AI 安全边界设计。

---

## 3. Historical Initial MVP Scope（Phase 0-5）

MVP 只实现以下能力：

1. 贷款合同基础数据；
2. 分期还款计划；
3. 实际还款记录；
4. 本期应还金额计算；
5. 本期已还金额计算；
6. 本期未还金额计算；
7. 当前待处理期次识别；
8. 逾期判断；
9. 逾期天数计算；
10. 整笔贷款是否结清判断；
11. 普通 REST 查询接口；
12. Spring AI Agent；
13. 3 个只读 Tool；
14. 3 个固定 validation cases；
15. JUnit 自动测试；
16. README 和运行说明。

---

## 4. Historical Initial MVP Exclusions（Phase 0-5）

以下功能不属于 Resume MVP，除非后续明确重新立项，否则不得自行增加。

### 4.1 贷款业务范围

不做：

- 客户管理；
- 贷款产品管理；
- 授信审批；
- 放款流程；
- 信用评分；
- 风险定价；
- 催收；
- 罚息；
- 复利；
- 宽限期；
- 提前还款；
- 展期；
- 核销；
- 多币种；
- 复杂账务分配；
- 真实银行会计核算。

### 4.2 通用系统能力

不做：

- 登录；
- RBAC；
- 用户中心；
- 前端页面；
- 消息通知；
- 工作流引擎；
- 报表中心。

### 4.3 当时未纳入 Initial MVP 的 AI / 基础设施能力

以下项目在 Phase 0-5 当时不做；其中部分后来经过独立 Phase、独立 Gate 明确批准，
不能再解释为当前仓库“没有实现”：

- RAG；
- 向量数据库；
- MCP；
- Multi-Agent；
- Redis；
- Kafka；
- Kubernetes；
- 大模型训练；
- 本地大模型部署；
- 自动执行金融交易或修改贷款状态。

---

## 5. AI 能力边界

Agent 必须是**只读诊断 Agent**。

Agent 可以：

- 理解自然语言问题；
- 提取 loanNo；
- 选择合适的只读 Tool；
- 使用 Tool 返回的业务事实生成解释；
- 在数据不足时明确说明无法判断。

Agent 不可以：

- 创建贷款；
- 修改贷款合同；
- 修改还款计划；
- 创建或修改还款记录；
- 修改贷款状态；
- 自动催收；
- 自动划扣；
- 自动审批；
- 信用评分；
- 自行推导新的金融业务规则。

---

## 6. 三个核心 Agent Case

### Case A：本期应该还多少钱

示例：

> LN-10001 本期应该还多少钱？

目标：Agent 调用 `getCurrentRepayment`，解释当前待处理期次的本金、利息、应还金额、已还金额、剩余金额和到期日。

### Case B：为什么逾期

示例：

> LN-10002 为什么逾期？

目标：Agent 调用 `getOverdueDiagnosis`，解释应还、已还、未还、到期日、业务日期、逾期状态与逾期天数。

### Case C：是否已经结清

示例：

> LN-10003 是否已经结清？

目标：Agent 调用 `getSettlementStatus`，解释整笔贷款是否还有未偿金额。

MVP 不增加第四个核心 Case。

---

## 7. Resume MVP 停止条件

本节是 Phase 0-5 的 historical Stop Point A，不是当前项目终态。

当以下条件全部满足时，达到 Stop Point A，立即停止扩展功能：

- Java 21 项目可运行；
- Spring Boot 分层结构清晰；
- 贷款领域规则由确定性 Java Service 实现；
- 3 个固定业务 Case 的 JUnit 全部通过；
- H2 数据可支撑可复现验证；
- 普通 REST API 可独立验证业务结果；
- Spring AI Agent 可调用至少 3 个只读 Tool；
- 3 个自然语言问题均可稳定验证；
- README 包含启动、架构、边界、Runbook 和测试说明。

达到 Stop Point A 后即可进入简历并继续投递。

---

## 8. Current Approved Scope

Stop Point A 之后，以下能力已经分别通过独立 Phase 批准并实现：

- MySQL 8 / Flyway 持久化路径和 Docker Compose 本地基础设施；
- Agent / Tool Audit、Micrometer / Actuator Observability；
- 持久化 Conversation、USER / ASSISTANT transcript、optimistic CAS；
- 固定 live Agent baseline；
- 版本化 Policy canonical store（MySQL）；
- BGE-M3 embeddings 与 Qdrant rebuildable derived vector index；
- deterministic Policy Router、Policy Query、Grounding Context；
- Citation Validation 与 Policy Retrieval Audit；
- Policy RAG 固定 Gold Dataset、E0/E1/E2 评测与 Router hardening；
- 消息/历史上限、有限外部 timeout、Spring AI max-attempts=1（不进行自动重试）与稳定失败语义。

这些能力没有改变以下核心边界：

- 金融事实仍只由确定性 Java 服务计算；
- Agent Tools 仍然只读；
- MySQL 是 policy source of truth，Qdrant 不是；
- synthetic policy 与 fixture 不代表真实银行生产数据；
- 项目没有被部署为真实银行生产服务。

## 9. Current Non-goals

未经新的明确 Phase 批准，不得增加：

- frontend、authentication / RBAC、客户管理或无关贷款业务；
- write-capable financial Tools 或自动金融交易；
- Multi-Agent、MCP；
- Redis long-term memory、Kafka、Kubernetes；
- Hybrid Search、BM25、RRF、Reranker、HyDE、GraphRAG 或其他没有评测证据的新检索技术；
- 大模型训练、本地聊天模型部署或新的 Provider fallback 体系。

早期把 MySQL、Agent Evaluation 和 Policy RAG 列为未来项，是历史范围控制，
不应被改写为当时已经实现；当前状态以本节和实际代码为准。
