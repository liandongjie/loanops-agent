# Policy RAG E2

- Git HEAD: `a49028db8903b5354abb48b4288b6bab16dcd507`
- Corpus: `policy-rag-eval-corpus-v1`
- Corpus SHA-256: `4c33abd1a2674d0afcf0df474d5b948869bdb6fae0748f5fc845ad044b22097f`
- Embedding: `bge-m3` / 1024 dimensions
- Retrieval: deterministic exact + BGE-M3 dense, topK=5, threshold=0.6
- Cases: 30, corpus chunks: 41

| Metric | Value |
|---|---:|
| Router Accuracy | 1.0000 |
| Policy-required Recall | 1.0000 |
| Recall@1 | 1.0000 |
| Recall@3 | 1.0000 |
| Recall@5 | 1.0000 |
| MRR | 1.0000 |
| ExactReferenceAccuracy | 1.0000 |
| TemporalVersionAccuracy | 1.0000 |
| NoMatchAccuracy | 1.0000 |
| FalseMatchCount | 0 |
| Retrieval latency p50 | 101 ms |
| Retrieval latency p95 | 146 ms |

## Router confusion matrix

| Actual \ Predicted | N | S | R |
|---|---:|---:|---:|
| N | 4 | 0 | 0 |
| S | 0 | 7 | 0 |
| R | 0 | 0 | 19 |

## Failed cases

None.

## Per-case retrieval

| Case | Decision | Status | Gold rank | Latency ms | Top hit |
|---|---|---|---:|---:|---|
| fin-001 | NOT_REQUIRED | NOT_RUN | 0 | 0 | - |
| fin-002 | NOT_REQUIRED | NOT_RUN | 0 | 0 | - |
| fin-003 | NOT_REQUIRED | NOT_RUN | 0 | 0 | - |
| fin-004 | NOT_REQUIRED | NOT_RUN | 0 | 0 | - |
| policy-001 | REQUIRED | MATCHED | 1 | 198 | EVAL-POST-2026 第四十四条 (0.8256) |
| policy-002 | REQUIRED | MATCHED | 1 | 101 | EVAL-POST-2026 第四十五条 (0.7963) |
| policy-003 | REQUIRED | MATCHED | 1 | 109 | EVAL-POST-2026 第四十二条 (0.8174) |
| policy-004 | REQUIRED | MATCHED | 1 | 117 | EVAL-RISK-2026 第十三条 (0.7509) |
| policy-005 | REQUIRED | MATCHED | 1 | 101 | EVAL-RIGHTS-2026 第二十六条 (0.7410) |
| mixed-001 | SUPPLEMENTAL | MATCHED | 1 | 115 | EVAL-POST-2026 第四十四条 (0.7027) |
| mixed-002 | SUPPLEMENTAL | MATCHED | 1 | 110 | EVAL-POST-2026 第四十五条 (0.6509) |
| mixed-003 | SUPPLEMENTAL | MATCHED | 1 | 86 | EVAL-POST-2026 第四十二条 (0.6244) |
| mixed-004 | SUPPLEMENTAL | MATCHED | 1 | 107 | EVAL-RISK-2026 第十二条 (0.8073) |
| mixed-005 | SUPPLEMENTAL | MATCHED | 1 | 94 | EVAL-RIGHTS-2026 第二十二条 (0.7203) |
| exact-001 | REQUIRED | MATCHED | 1 | 100 | EVAL-POST-2026 第四十四条 (1.0000) |
| exact-002 | REQUIRED | MATCHED | 1 | 99 | EVAL-RISK-2026 第十三条 (1.0000) |
| exact-003 | REQUIRED | MATCHED | 1 | 123 | EVAL-RIGHTS-2026 第二十四条 (1.0000) |
| exact-004 | REQUIRED | MATCHED | 1 | 146 | EVAL-OPS-2026 第三十二条 (1.0000) |
| semantic-001 | REQUIRED | MATCHED | 1 | 101 | EVAL-POST-2026 第四十二条 (0.7189) |
| semantic-002 | REQUIRED | MATCHED | 1 | 106 | EVAL-POST-2026 第四十五条 (0.6440) |
| semantic-003 | REQUIRED | MATCHED | 1 | 100 | EVAL-RISK-2026 第十二条 (0.7053) |
| semantic-004 | REQUIRED | MATCHED | 1 | 102 | EVAL-POST-2026 第四十七条 (0.7238) |
| temporal-001 | REQUIRED | MATCHED | 1 | 115 | EVAL-POST-2025 第四十四条 (0.7425) |
| temporal-002 | REQUIRED | MATCHED | 1 | 99 | EVAL-POST-2025 第四十五条 (0.8152) |
| temporal-003 | REQUIRED | MATCHED | 1 | 83 | EVAL-POST-2026 第四十四条 (0.8302) |
| nomatch-001 | REQUIRED | NO_MATCH | 0 | 83 | EVAL-OPS-2026 第三十六条 (0.5614) |
| nomatch-002 | REQUIRED | NO_MATCH | 0 | 102 | EVAL-OPS-2026 第三十六条 (0.5171) |
| nomatch-003 | REQUIRED | NO_MATCH | 0 | 83 | EVAL-OPS-2026 第三十三条 (0.4595) |
| multi-001 | SUPPLEMENTAL | MATCHED | 1 | 97 | EVAL-POST-2026 第四十四条 (0.6342) |
| multi-002 | SUPPLEMENTAL | MATCHED | 1 | 101 | EVAL-POST-2026 第四十五条 (0.6253) |
