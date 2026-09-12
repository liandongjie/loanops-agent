# LoanOps Agent 文档导航

README 负责让第一次打开仓库的人快速看懂项目；`docs/` 负责回答“具体怎么实现、怎么运行、怎么验证”。

如果你只是想了解这个项目，先看仓库根目录的 `README.md`。只有想继续深挖时，再从这里进入对应文档。

## 你想了解什么？

| 如果你想知道…… | 看这里 |
|---|---|
| 金额、逾期、结清到底怎么算 | [贷款业务规则](DOMAIN.md) |
| Agent、Tool、RAG、多轮对话是怎么串起来的 | [系统架构](ARCHITECTURE.md) |
| 项目现在做什么、不做什么 | [项目范围](SCOPE.md) |
| 怎么在本地跑起来、怎么排错 | [本地运行与验收](RUNBOOK.md) |
| DeepSeek、Ollama、GLM 怎么切换 | [模型 Provider](PROVIDERS.md) |
| Agent 怎么测试，为什么不是“手工问几次就算通过” | [Agent 评测](EVALUATION.md) |
| Policy RAG 怎么测试，指标分别代表什么 | [Policy RAG 评测](POLICY_RAG_EVALUATION.md) |
| 当前版本最低要通过哪些检查 | [当前验收标准](ACCEPTANCE.md) |
| 最近一次真实验收跑出了什么 | [验收快照](ACCEPTANCE_SNAPSHOT.md) |
| 项目过去是怎么一步步演进的 | [历史记录](history/) |

## 文档分工

为了避免同一个事实在多份文档里重复维护，当前约定：

- 贷款业务规则只在 `DOMAIN.md` 定义；
- 当前项目范围只在 `SCOPE.md` 定义；
- 当前支持哪些模型只在 `PROVIDERS.md` 维护；
- Policy RAG 的指标和测试口径只在 `POLICY_RAG_EVALUATION.md` 维护；
- 完整运行命令只在 `RUNBOOK.md` 维护；
- `ACCEPTANCE_SNAPSHOT.md` 记录某一次真实执行结果，不代表未来所有版本；
- `history/` 保存历史过程，不覆盖当前文档。

如果历史记录和当前文档冲突，以当前代码、测试和当前文档为准。
