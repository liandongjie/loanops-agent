# LoanOps Agent — Development Plan

## 1. 开发总原则

本项目采用**阶段式、门禁式、可回滚开发**。

目标不是让 Coding Agent 一次性完成整个项目，而是：

```text
冻结规格
  ↓
小阶段实现
  ↓
测试
  ↓
Code Review
  ↓
PASS
  ↓
小 Commit
  ↓
下一阶段
```

任何阶段失败，都只修复当前阶段，不扩大范围。

---

## 2. 开发角色

### 架构 / Review 侧

负责：

- 业务边界；
- 领域规则；
- 技术架构；
- 阶段划分；
- 验收标准；
- Code Review；
- 是否允许进入下一阶段。

### Coding Agent

负责：

- 阅读仓库；
- 按当前阶段实现代码；
- 运行测试；
- 修复当前阶段 Bug；
- 输出 diff 和测试结果；
- 提供推荐 commit message。

Coding Agent 不负责自行扩大项目范围。

---

# 3. Phase 0 — Specification Freeze

状态：

```text
DONE
```

产物：

- `SCOPE.md`
- `DOMAIN.md`
- `ARCHITECTURE.md`
- `ACCEPTANCE.md`
- `DEVELOPMENT_PLAN.md`
- `AGENTS.md`

冻结：

- 3 个核心领域对象；
- 3 个核心业务 Case；
- 3 个只读 Tool；
- Java 计算 / AI 解释；
- Resume MVP Stop Point A。

---

# 4. Phase 1 — Java Domain Baseline

## 4.1 目标

在完全不使用 AI、不依赖数据库的情况下，证明贷款还款业务规则能够由 Java 正确、稳定、可测试地实现。

## 4.2 任务

只做：

1. Maven / Spring Boot 工程骨架；
2. Java 21；
3. `LoanContract`；
4. `RepaymentPlan`；
5. `PaymentRecord`；
6. 必要 DTO / Value Object；
7. `RepaymentCalculator`；
8. `LoanDiagnosisService`；
9. 可注入 `Clock`；
10. Case A/B/C 的 JUnit。

## 4.3 不做

- H2；
- MyBatis；
- Controller；
- Spring AI；
- DeepSeek；
- Tool；
- Agent。

## 4.4 Gate

执行：

```bash
mvn test
```

Case A/B/C 全部 PASS 才能结束。

## 4.5 推荐 Commit

```text
feat: implement deterministic loan repayment domain
```

---

# 5. Phase 2 — H2 Persistence + REST

## 5.1 目标

把已经通过测试的领域规则接入持久层，并通过普通 REST API 对外暴露，从而建立独立于 AI 的 Java 后端基线。

## 5.2 任务

1. 引入 H2；
2. 引入 MyBatis-Plus；
3. 建立表结构；
4. 初始化 LN-10001 / 10002 / 10003 Fixture；
5. Mapper；
6. Service 与持久层集成；
7. REST DTO；
8. 普通 Loan Status Controller；
9. REST / Integration Test。

## 5.3 关键约束

Mapper 不实现业务规则。

Controller 不实现业务规则。

业务结果仍由：

```text
LoanDiagnosisService
```

产生。

## 5.4 Gate

至少验证：

```http
GET /api/loans/LN-10002/status
```

返回：

```text
8500.00
5000.00
3500.00
overdue = true
overdueDays = 3
```

并运行：

```bash
mvn test
```

## 5.5 推荐 Commit

```text
feat: expose deterministic loan diagnosis api
```

---

# 6. Phase 3 — Spring AI + Read-only Tools

## 6.1 目标

把已经成熟的 Java Service 能力暴露为 Agent 可调用的只读工具。

## 6.2 任务

1. 确认 Spring Boot / Spring AI 版本兼容；
2. 接入 Spring AI；
3. 接入一个 DeepSeek 模型；
4. API Key 使用环境变量；
5. 实现 3 个只读 Tool：

```text
getCurrentRepayment
getOverdueDiagnosis
getSettlementStatus
```

6. Tool Test。

## 6.3 不做

- RAG；
- MCP；
- Multi-Agent；
- 多模型；
- 写操作 Tool。

## 6.4 Gate

必须证明：

- Tool 只调用 Service；
- Tool 不复制业务计算；
- Tool 不修改数据；
- Tool 返回结果与普通 REST / Service 一致。

## 6.5 推荐 Commit

```text
feat: add spring ai loan diagnosis tools
```

---

# 7. Phase 4 — Agent End-to-End

## 7.1 目标

实现自然语言到 Tool Calling 再到业务解释的最小闭环。

## 7.2 任务

1. `LoanOpsAgentService`；
2. Spring AI ChatClient；
3. 系统约束；
4. 注册 3 个 Tools；
5. `POST /api/agent/chat`；
6. Agent Case A/B/C；
7. 不存在贷款 Case；
8. 只读安全边界 Case。

## 7.3 Agent 系统原则

必须明确：

```text
只允许根据 Tool 返回结果解释事实。

不得自行计算金融金额。

不得编造贷款信息。

不得修改贷款状态。

不得执行金融交易。
```

## 7.4 Gate

三个问题全部稳定跑通：

```text
LN-10001 本期应该还多少钱？

LN-10002 为什么逾期？

LN-10003 是否已经结清？
```

## 7.5 推荐 Commit

```text
feat: add loanops diagnostic agent
```

---

# 8. Phase 5 — Resume MVP Hardening

## 8.1 目标

停止增加业务功能，把现有项目整理成可以公开展示、可以投简历、可以被面试官复现的工程。

## 8.2 必做

1. 完整 `mvn test`；
2. 异常处理；
3. 参数校验；
4. README；
5. 架构图；
6. 项目边界；
7. 运行说明；
8. DeepSeek Key 配置说明；
9. 3 个演示问题；
10. 项目限制；
11. `.gitignore`；
12. 检查敏感信息。

## 8.3 可选二选一

时间允许时：

```text
Docker
```

或：

```text
GitHub Actions
```

不要因增强项推迟 Stop Point A。

## 8.4 推荐 Commit

```text
test: harden loanops agent resume mvp
```

也可按实际修改拆分：

```text
docs: document loanops agent architecture and demo
```

---

# 9. Stop Point A — Resume MVP

达到以下结果立即停止业务开发：

```text
Java 21
+ Spring Boot
+ MyBatis-Plus / H2
+ 确定性贷款领域逻辑
+ REST API
+ Spring AI
+ 3 个 Tool
+ 1 个 Agent
+ 3 个业务 Case
+ JUnit
+ README
```

此时项目已经可以进入：

```text
简历
↓
投递
↓
边投边按需要增强
```

---

# 10. Stop Point B — Future Enhancements

仅在 Resume MVP 已经完成且不影响投递时考虑：

### B1

```text
MySQL Profile
```

### B2

```text
Docker
```

### B3

```text
GitHub Actions
```

### B4

```text
Agent Diagnosis Audit Log
```

### B5

```text
MCP Server
```

### B6

```text
Agent Evaluation
```

每一个增强项都必须独立评估收益，禁止一次性全部增加。

---

# 11. Codex 每阶段工作流

每次进入新阶段：

```text
1. git status
2. 阅读 AGENTS.md
3. 阅读 docs
4. 阅读当前测试
5. 只实现当前 Phase
6. 运行相关测试
7. 运行 mvn test
8. 检查 git diff
9. 输出 Completion Report
10. STOP
```

如果发现：

- 文档冲突；
- 需要新业务规则；
- 需要扩大范围；
- 测试与冻结规则冲突；

必须停止并报告。

---

# 12. 范围失控检查

任何开发阶段出现以下问题，应立即停止：

- 新增第四个核心业务 Case；
- 新增前端；
- 新增审批/信用评分/催收；
- Agent 获得写操作；
- 引入 RAG/MCP/Multi-Agent；
- 引入与当前 Phase 无关的基础设施；
- 大范围重构；
- 为了“更真实”修改冻结业务规则。

项目的成功标准不是“功能多”，而是：

> 小范围内业务正确、Java 扎实、Agent 边界清晰、测试可信、能够解释每个设计决定。
