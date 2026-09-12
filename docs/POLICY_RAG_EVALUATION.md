# LoanOps Agent Policy RAG 评测

## 1. 这套评测到底在测什么

RAG 不能只看“搜到了几段文字”。真正需要回答的是：

- 这个问题到底需不需要查政策？
- 如果需要，找的是不是正确条款？
- 当前业务日期下，这个版本还适不适用？
- 用户明确问某个文号、某一条时，能不能直接找到它？
- 知识库根本没有答案时，会不会硬匹配一条看起来相似的规定？
- 最终回答里的 `[P1]`、`[P2]` 是否真的来自本轮检索结果？

例如用户问：

```text
EVAL-POST-2026 第四十四条怎么规定？
```

系统应该直接找到这一条；如果问的是另一个时间点的业务，就不能引用当时还没有生效的版本；如果知识库里根本没有依据，就应该明确说找不到，而不是给一条“最像”的答案。

因此项目把 Policy RAG 拆成路由、检索、版本适用性、无答案处理和引用几个部分分别测试。

## 2. 固定测试数据

主要输入：

```text
evaluation/policy-rag-cases.json
src/main/resources/policy/demo-policy-corpus.json
evaluation/policy-router-hardening-cases.json
```

当前包含：

- 30 个手工编写的 Policy RAG 测试问题；
- 22 个只测试“要不要查政策”的 Router 回归问题。

这些 Policy 都是人为构造的演示数据，不是真实银行制度或监管政策。

## 3. 为什么评测使用独立数据库和 Qdrant collection

评测脚本会创建隔离的 MySQL schema / database 和 Qdrant collection，结束后再清理。

这样可以避免：

- 测试数据污染普通本地 Policy 库；
- 上一次人工操作影响下一次评测；
- 为了跑评测而要求开发者先清空自己的本地数据。

普通本地体验使用 `bootstrap-local-policy.ps1`，与评测环境分开。

## 4. 正确答案为什么不用数据库 UUID 表示

数据库重新 ingest 后，document/version/chunk 的 UUID 可能变化。

如果 Gold Label 直接写 UUID，那么数据库一重建，测试就全部失效。

因此固定答案使用更稳定的业务信息，例如：

- document number；
- article number；
- version applicability；
- 期望的 Policy decision。

这样只要政策业务含义没有变，重新生成数据库 ID 也不会影响评测。

## 5. 主要指标怎么理解

### Router Accuracy

检查系统有没有正确判断：

```text
不需要政策
需要政策辅助
必须依赖政策
```

对应代码中的：

```text
NOT_REQUIRED
SUPPLEMENTAL
REQUIRED
```

### Policy-required Recall

所有本来需要政策的问题里，有多少没有被错误当成“无需政策”。

这个指标低，意味着一些应该查政策的问题根本没有进入 RAG。

### Recall@K

正确条款是否出现在前 K 个检索结果中。

例如 `Recall@3` 表示正确条款有没有出现在前三条候选里。

### MRR

不仅关心“找没找到”，还关心正确条款排得靠不靠前。正确条款越早出现，MRR 越高。

### Exact Reference Accuracy

用户已经明确给出文号或条款号时，系统能不能把对应条款放在第一位。

### Temporal Version Accuracy

检查政策版本是否符合当前业务日期：

- 应该出现的版本有没有出现；
- 已失效或尚未生效的版本有没有被错误引用。

### No Match Accuracy

知识库确实没有答案时，系统能不能正确返回“没有可靠匹配”。

### False Match Count

本来应该 no-match，却硬匹配出一条 Policy 的次数。

## 6. E0 → E1 → E2 做了什么

项目没有一开始就加 Reranker、BM25、GraphRAG 等更多组件，而是先用同一套 30 个问题定位问题，再一次只改主要变量。

| Metric | E0 | E1 | E2 |
|---|---:|---:|---:|
| Router Accuracy | 0.8333 | 0.8333 | 1.0000 |
| Policy-required Recall | 0.8077 | 0.8077 | 1.0000 |
| Exact Reference Accuracy | 0.7500 | 1.0000 | 1.0000 |
| No Match Accuracy | 0.0000 | 1.0000 | 1.0000 |
| False Match Count | 3 | 0 | 0 |

E2 直接检索结果：

```text
Recall@1 = 1.0000
Recall@3 = 1.0000
Recall@5 = 1.0000
MRR      = 1.0000
```

这些数字只适用于仓库内固定的 30 个测试问题和 synthetic corpus，**不是生产环境准确率**。

E1 主要解决“明确文号 / 条款引用”和“没有答案时不要硬匹配”的问题；E2 主要修正哪些问题应该进入 Policy RAG。

因为固定测试已经能定位主要问题，所以 E2 没有为了继续涨指标再叠加 Reranker、BM25、RRF 或 GraphRAG。

## 7. 除了指标，还必须满足哪些条件

固定数字之外，还要求：

- 时间版本问题必须选对适用版本；
- 必须有政策才能回答、但没有命中时，不能让模型自己编规定；
- 回答缺少或写错 `[P1]` 等引用时，不能把这一轮保存成成功回答；
- 涉及贷款金额和逾期时，事实仍来自只读 Tool；
- Conversation 中只保存 USER / ASSISTANT，不把内部检索上下文塞进聊天记录；
- Policy RAG 改动以后，还要重跑原来的 6 个 Agent 用例，防止“修 RAG 把 Tool Calling 修坏”。

## 8. 为什么还保留历史失败案例

检索正确，不代表大模型生成一定正确。

历史 `mixed-002` 曾出现过：检索到的 Policy Evidence 里没有“质押”，但模型回答自行扩展出了“质押”。后面一次运行没有复现，也不能说明这个问题已经被彻底解决，因为生成约束并没有发生足以证明它消失的变化。

另一次真实 DeepSeek 对抗测试中，模型输出缺少必需的 `[P1]`。这一条不能记为模型语义 PASS，但服务端的 `PolicyCitationValidator` 在保存成功对话前拒绝了该回答。

这里能证明的是：

> **确定性的引用校验边界生效。**

不能因此声称“模型幻觉或 Prompt Injection 已经解决”。

## 9. 怎么运行

依赖：

- Java 21；
- Docker MySQL；
- Qdrant；
- Ollama；
- BGE-M3。

```powershell
./scripts/evaluate-policy-rag.ps1 -Label e0 -Threshold 0.0
./scripts/evaluate-policy-rag.ps1 -Label e1 -Threshold 0.60
./scripts/evaluate-policy-rag.ps1 -Label e2 -Threshold 0.60
```

如果要让 DeepSeek 真实生成回答并做人工复查：

```powershell
./scripts/evaluate-policy-rag.ps1 -Label e2 -Threshold 0.60 -ManualReview
```

评测结果保存在 `evaluation/results/`，历史人工复查证据保存在 `evaluation/` 对应文件。

## 10. 修改 RAG 后怎么判断是真的变好

如果某次改动导致指标变化：

1. 先判断问题发生在“要不要查政策、找哪个条款、版本是否适用、模型生成、引用校验”中的哪一层；
2. 尽量一次只修改一个主要变量；
3. 用同一套 corpus 和同一套测试问题重跑；
4. 保留失败案例，不用后续一次 PASS 覆盖历史问题；
5. 如果没有数据证明新组件有收益，就不因为它热门而加入当前架构。

这套评测的目标不是制造一个好看的“100%”，而是让每一次 RAG 修改都能解释：**为什么改、改了什么、结果到底有没有变好。**
