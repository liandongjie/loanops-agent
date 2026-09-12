# LoanOps Agent 文档导航

`docs/` 用于保存 LoanOps Agent 的**当前技术事实、运行方法和可复现证据**。

README 负责项目展示；这里的文档负责回答“项目现在到底怎么实现、怎么验证、边界在哪里”。

## 推荐阅读顺序

如果第一次阅读项目：

1. [项目范围](SCOPE.md)：当前做什么、不做什么；
2. [系统架构](ARCHITECTURE.md)：系统如何组织金融事实、Agent、Policy RAG、Conversation 与 Audit；
3. [领域规则](DOMAIN.md)：金额、逾期与结清的确定性业务规则；
4. [本地运行与验收](RUNBOOK.md)：如何启动、体验和复现；
5. [Agent 评测](EVALUATION.md) / [Policy RAG 评测](POLICY_RAG_EVALUATION.md)：项目如何避免只靠人工感觉判断效果。

## 文档职责

| 文档 | 唯一职责 |
|---|---|
| [SCOPE.md](SCOPE.md) | 当前产品与工程范围：做什么、不做什么 |
| [DOMAIN.md](DOMAIN.md) | 确定性贷款业务规则唯一事实来源 |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 当前系统设计、数据边界、请求链路与失败语义 |
| [RUNBOOK.md](RUNBOOK.md) | 启动、手工体验、自动验收、排错和清理 |
| [PROVIDERS.md](PROVIDERS.md) | DeepSeek / Ollama / GLM 的接入契约与验收边界 |
| [EVALUATION.md](EVALUATION.md) | Agent Tool Calling / Conversation 的固定回归方法 |
| [POLICY_RAG_EVALUATION.md](POLICY_RAG_EVALUATION.md) | Router / Retrieval / Version / Citation 的专项评测 |
| [ACCEPTANCE.md](ACCEPTANCE.md) | 当前版本需要满足的验收条件 |
| [ACCEPTANCE_SNAPSHOT.md](ACCEPTANCE_SNAPSHOT.md) | 最近一次已执行验收的时间、commit 和结果 |
| [history/](history/) | 历史 Phase、旧快照与开发过程，不定义当前行为 |

## 文档一致性原则

为了避免多轮开发后出现“同一个事实写在五个地方、最后彼此矛盾”，当前采用以下规则：

- **金融业务规则**只在 `DOMAIN.md` 定义；
- **当前项目范围**只在 `SCOPE.md` 定义；
- **Provider 当前支持状态**只在 `PROVIDERS.md` 定义；
- **Policy RAG 指标与口径**只在 `POLICY_RAG_EVALUATION.md` 定义；
- **如何运行**只在 `RUNBOOK.md` 给出完整步骤；
- README 只提炼，不成为第二套技术规格；
- `history/` 中的历史记录不能覆盖当前文档。

如果历史记录和当前文档冲突，以当前代码、测试以及上述当前文档为准；如果当前文档彼此冲突，应先停止扩展并完成一致性修正。
