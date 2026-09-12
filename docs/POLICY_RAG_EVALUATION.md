# LoanOps Agent Policy RAG 评测

## 1. 评测目标

Policy RAG 不能只靠“问几个问题，看起来检索到了”判断效果。

项目维护一套固定、可审阅、可重复运行的 Gold Dataset，把问题拆分到不同责任层：

- Router 是否正确决定要不要检索 Policy；
- Retrieval 是否找到正确文档 / 条款；
- Policy version 是否符合业务日期；
- exact reference 是否精确命中；
- no-match 是否会错误匹配；
- Citation 是否来自实际提供给模型的证据。

这使 Router、Retriever 和 Generation 的问题可以分开定位。

## 2. 固定输入

主要输入：

```text
evaluation/policy-rag-cases.json
src/main/resources/policy/demo-policy-corpus.json
evaluation/policy-router-hardening-cases.json
```

当前：

- Policy RAG Gold Dataset：30 个 hand-authored cases；
- Router hardening：22 个 Router-only regression cases；
- Router-only cases 不计入 retrieval metrics。

corpus 为 synthetic validation data，不是真实银行制度或监管政策。

## 3. 隔离方式

评测 runner 使用独立：

```text
MySQL schema/database
Qdrant collection
```

评测结束后清理，不把临时评测状态当作普通本地知识库。

普通本地体验使用 `bootstrap-local-policy.ps1` 初始化 Local Demo；两者用途不同。

## 4. Gold Label

Gold 不使用数据库 UUID 作为标签，而使用稳定业务 metadata：

- decision；
- retrieval status；
- document number；
- article number；
- version applicability；
- expected Tool names。

这样数据库重新 ingest 后，只要业务含义不变，评测标签仍然稳定。

## 5. 指标

### Router Accuracy

三分类：

```text
NOT_REQUIRED
SUPPLEMENTAL
REQUIRED
```

正确数 / 全部 cases。

### Policy-required Recall

Gold 为 `SUPPLEMENTAL / REQUIRED` 的问题中，没有错误路由成 `NOT_REQUIRED` 的比例。

### Recall@K

Gold `(documentNumber, articleNo)` 是否出现在前 K 个 retrieval hits。

### MRR

第一条 Gold evidence 的 reciprocal rank 平均值。

### Exact Reference Accuracy

明确引用文号 / 条款的问题是否把 Gold evidence 放在 rank 1。

### Temporal Version Accuracy

时间版本问题是否：

- 包含正确适用版本；
- 不包含同一文档的不适用版本。

### No Match Accuracy

Gold no-match case 是否正确得到 `NO_MATCH`。

### False Match Count

本应 no-match，却错误产生 `MATCHED` 的数量。

Retrieval metrics 会直接评估 Retrieval 本身，即使 Router 判断错误，也不会把两层错误混在一起。

## 6. E0 → E1 → E2

项目没有默认不断增加 Retriever 组件，而是通过固定数据集先定位问题。

| Metric | E0 | E1 | E2 |
|---|---:|---:|---:|
| Router Accuracy | 0.8333 | 0.8333 | 1.0000 |
| Policy-required Recall | 0.8077 | 0.8077 | 1.0000 |
| Exact Reference Accuracy | 0.7500 | 1.0000 | 1.0000 |
| No Match Accuracy | 0.0000 | 1.0000 | 1.0000 |
| False Match Count | 3 | 0 | 0 |

E2 direct Retriever：

```text
Recall@1 = 1.0000
Recall@3 = 1.0000
Recall@5 = 1.0000
MRR      = 1.0000
```

这些数字只属于**固定 30-case synthetic corpus**，不能解释为 production accuracy。

### E1

主要针对 exact-reference 约束和 no-match threshold。

### E2

针对 Router 做 focused hardening。

E2 没有为了提高数字继续叠加：

- Reranker；
- BM25；
- RRF；
- GraphRAG；
- 新 Embedding model。

这样可以把收益归因到具体修改，而不是“加了一堆组件以后指标变好了”。

## 7. 硬性验收条件

固定评测还要求：

- TemporalVersionAccuracy = 1.0000；
- `REQUIRED + NO_MATCH` 不调用模型编造 Policy；
- invalid citation 在 transcript 完成前被拒绝；
- 金融事实来自只读 Tools；
- Conversation transcript 只包含 USER / ASSISTANT；
- 运行现有 **6-case** live Agent baseline，确保 Policy RAG 变化没有破坏基础 Tool 行为。

## 8. 生成阶段与历史失败证据

Retrieval 命中正确，不代表模型一定不会扩展证据外内容。

历史 `mixed-002` 曾出现证据外“质押”扩展；后续一次运行未复现，但 Generation 约束没有变化，因此不能声称问题已经被修复。

真实 DeepSeek adversarial review 还出现过缺失必需 `[P1]` 的模型输出。该输出语义 case 不能记为 PASS，但 `PolicyCitationValidator` 在 successful commit 前拒绝了它。

这说明：

> deterministic safety boundary 生效，不等价于“Prompt Injection / Hallucination 已解决”。

## 9. 运行

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

真实 DeepSeek manual review：

```powershell
./scripts/evaluate-policy-rag.ps1 -Label e2 -Threshold 0.60 -ManualReview
```

报告保存在 `evaluation/results/`，历史人工审阅证据保存在 `evaluation/` 下对应文件。

## 10. 评测纪律

如果新修改导致指标变化：

1. 先按责任层分类：Router / Retrieval / Applicability / Generation / Citation；
2. 不允许先堆新组件再解释原因；
3. 优先一次只改变一个主要变量；
4. 用同一 corpus、同一 dataset 重跑；
5. 保留 bad case，不用后续单次 PASS 覆盖历史失败；
6. 如果没有评测证据支持，不把新 Retriever 技术加入当前范围。

这套评测的目的不是制造一个漂亮的“100%”，而是让每次 Agent / RAG 修改有可复现的证据。
