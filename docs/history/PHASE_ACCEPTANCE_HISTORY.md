# LoanOps Agent 历史 Phase 验收记录

> 本文件是早期阶段式开发的验收摘要。当前版本 Gate 见 `../ACCEPTANCE.md`。

| 阶段 | 主要目标 | 历史 Gate |
|---|---|---|
| Phase 1 | Java Domain Baseline | 3 个固定贷款 Case、`BigDecimal`、可注入 `Clock`、JUnit |
| Phase 2 | H2 Persistence + REST | fixture、Mapper、Service、REST 与 domain facts 一致 |
| Phase 3 | Spring AI + Tools | 3 个只读 Tools，只委托 Service |
| Phase 4 | Agent E2E | 3 个自然语言 Case、unknown loan、read-only guard |
| Phase 5 | Resume MVP Hardening | 测试、README、架构、边界、运行说明、secret hygiene |
| Phase 6 | Stateful Conversation | persistence、restart、fresh Tool、rollback、CAS |
| Phase 7 | Policy RAG | versioned Policy、BGE-M3、Qdrant、Router/Retriever、Citation、Audit、Gold Dataset |
| Phase 8 | Runtime Hardening | limits、timeout、failure semantics、adversarial evidence |
| Phase 9 | Closure | docs reconciliation、reproducibility、Hero runbook、claim discipline |

## 历史业务 Cases

### Case A — LN-10001

```text
dueAmount         = 8500.00
paidAmount        = 0.00
outstandingAmount = 8500.00
```

### Case B — LN-10002

在历史 fixed business date 下：

```text
dueAmount         = 8500.00
paidAmount        = 5000.00
outstandingAmount = 3500.00
overdue           = true
```

### Case C — LN-10003

```text
settled          = true
totalOutstanding = 0.00
```

这些 deterministic facts 后来继续作为 Provider / Conversation / Policy Hero 的基础，不因为 Agent 功能增加而被模型接管。

## 历史安全原则

阶段开发期间长期保持：

- 不通过删除或弱化测试获得 PASS；
- 不为模型输出修改 `DOMAIN.md`；
- 不把金融计算写进 Prompt / Tool；
- 不给 Agent 增加金融写权限；
- 环境不可用时记录 `ENV_BLOCKED`；
- 固定数据集指标不宣传为 production accuracy。

## 历史记录的使用方式

历史 Phase 说明主要用于：

- 解释项目是如何逐步演进的；
- 复盘某项架构为什么存在；
- 面试时说明工程取舍；
- 对照 Git commit / PR 查找当时实现。

它不再用于判断当前仓库“现在支持什么”。

当前事实以：

```text
SCOPE.md
DOMAIN.md
ARCHITECTURE.md
PROVIDERS.md
ACCEPTANCE.md
```

为准。
