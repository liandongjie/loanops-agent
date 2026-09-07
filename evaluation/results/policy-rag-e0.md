# Policy RAG E0

- Git HEAD: `d28f1d70105c1cdc6a4d1c47313edc3dc58fe1a3`
- Corpus: `policy-rag-eval-corpus-v1`
- Corpus SHA-256: `4c33abd1a2674d0afcf0df474d5b948869bdb6fae0748f5fc845ad044b22097f`
- Embedding: `bge-m3` / 1024 dimensions
- Retrieval: deterministic exact + BGE-M3 dense, topK=5, threshold=0.0
- Cases: 30, corpus chunks: 41

| Metric | Value |
|---|---:|
| Router Accuracy | 0.8333 |
| Policy-required Recall | 0.8077 |
| Recall@1 | 0.9565 |
| Recall@3 | 0.9565 |
| Recall@5 | 0.9565 |
| MRR | 0.9565 |
| ExactReferenceAccuracy | 0.7500 |
| TemporalVersionAccuracy | 1.0000 |
| NoMatchAccuracy | 0.0000 |
| FalseMatchCount | 3 |
| Retrieval latency p50 | 144 ms |
| Retrieval latency p95 | 183 ms |

## Router confusion matrix

| Actual \ Predicted | N | S | R |
|---|---:|---:|---:|
| N | 4 | 0 | 0 |
| S | 0 | 7 | 0 |
| R | 5 | 0 | 14 |

## Failed cases

- `exact-003`: SEMANTIC_RECALL, EXACT_MATCH; goldRank=0
- `semantic-001`: ROUTING; goldRank=1
- `semantic-002`: ROUTING; goldRank=1
- `semantic-003`: ROUTING; goldRank=1
- `semantic-004`: ROUTING; goldRank=1
- `temporal-002`: ROUTING; goldRank=1
- `nomatch-001`: NO_MATCH_THRESHOLD; goldRank=0
- `nomatch-002`: NO_MATCH_THRESHOLD; goldRank=0
- `nomatch-003`: NO_MATCH_THRESHOLD; goldRank=0

## Per-case retrieval

| Case | Decision | Status | Gold rank | Latency ms | Top hit |
|---|---|---|---:|---:|---|
| fin-001 | NOT_REQUIRED | NOT_RUN | 0 | 0 | - |
| fin-002 | NOT_REQUIRED | NOT_RUN | 0 | 0 | - |
| fin-003 | NOT_REQUIRED | NOT_RUN | 0 | 0 | - |
| fin-004 | NOT_REQUIRED | NOT_RUN | 0 | 0 | - |
| policy-001 | REQUIRED | MATCHED | 1 | 139 | EVAL-POST-2026 第四十四条 (0.8256) |
| policy-002 | REQUIRED | MATCHED | 1 | 100 | EVAL-POST-2026 第四十五条 (0.7963) |
| policy-003 | REQUIRED | MATCHED | 1 | 159 | EVAL-POST-2026 第四十二条 (0.8174) |
| policy-004 | REQUIRED | MATCHED | 1 | 142 | EVAL-RISK-2026 第十三条 (0.7509) |
| policy-005 | REQUIRED | MATCHED | 1 | 140 | EVAL-RIGHTS-2026 第二十六条 (0.7410) |
| mixed-001 | SUPPLEMENTAL | MATCHED | 1 | 153 | EVAL-POST-2026 第四十四条 (0.7027) |
| mixed-002 | SUPPLEMENTAL | MATCHED | 1 | 126 | EVAL-POST-2026 第四十五条 (0.6509) |
| mixed-003 | SUPPLEMENTAL | MATCHED | 1 | 87 | EVAL-POST-2026 第四十二条 (0.6244) |
| mixed-004 | SUPPLEMENTAL | MATCHED | 1 | 152 | EVAL-RISK-2026 第十二条 (0.8073) |
| mixed-005 | SUPPLEMENTAL | MATCHED | 1 | 128 | EVAL-RIGHTS-2026 第二十二条 (0.7203) |
| exact-001 | REQUIRED | MATCHED | 1 | 140 | EVAL-POST-2026 第四十四条 (1.0000) |
| exact-002 | REQUIRED | MATCHED | 1 | 165 | EVAL-RISK-2026 第十三条 (1.0000) |
| exact-003 | REQUIRED | MATCHED | 0 | 152 | EVAL-RIGHTS-2026 第二十五条 (1.0000) |
| exact-004 | REQUIRED | MATCHED | 1 | 197 | EVAL-OPS-2026 第三十二条 (1.0000) |
| semantic-001 | NOT_REQUIRED | MATCHED | 1 | 131 | EVAL-POST-2026 第四十二条 (0.7189) |
| semantic-002 | NOT_REQUIRED | MATCHED | 1 | 139 | EVAL-POST-2026 第四十五条 (0.6440) |
| semantic-003 | NOT_REQUIRED | MATCHED | 1 | 145 | EVAL-RISK-2026 第十二条 (0.7053) |
| semantic-004 | NOT_REQUIRED | MATCHED | 1 | 182 | EVAL-POST-2026 第四十七条 (0.7238) |
| temporal-001 | REQUIRED | MATCHED | 1 | 144 | EVAL-POST-2025 第四十四条 (0.7425) |
| temporal-002 | NOT_REQUIRED | MATCHED | 1 | 183 | EVAL-POST-2025 第四十五条 (0.8152) |
| temporal-003 | REQUIRED | MATCHED | 1 | 163 | EVAL-POST-2026 第四十四条 (0.8302) |
| nomatch-001 | REQUIRED | MATCHED | 0 | 142 | EVAL-OPS-2026 第三十六条 (0.5614) |
| nomatch-002 | REQUIRED | MATCHED | 0 | 149 | EVAL-OPS-2026 第三十六条 (0.5171) |
| nomatch-003 | REQUIRED | MATCHED | 0 | 143 | EVAL-OPS-2026 第三十三条 (0.4595) |
| multi-001 | SUPPLEMENTAL | MATCHED | 1 | 156 | EVAL-POST-2026 第四十四条 (0.6342) |
| multi-002 | SUPPLEMENTAL | MATCHED | 1 | 145 | EVAL-POST-2026 第四十五条 (0.6253) |
