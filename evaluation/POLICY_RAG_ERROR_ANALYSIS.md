# Policy RAG Error Analysis

## Frozen E0

E0 used corpus `policy-rag-eval-corpus-v1` with SHA-256
`4c33abd1a2674d0afcf0df474d5b948869bdb6fae0748f5fc845ad044b22097f`, BGE-M3 1024-dimensional
embeddings, topK 5 and threshold 0.0.

| Failure class | Cases | Finding |
|---|---|---|
| ROUTING | `semantic-001`–`semantic-004`, `temporal-002` | Implicit normative wording such as “应该”“需要履行”“能不能” is outside the deliberately small keyword rule. Direct retrieval still put every Gold at rank 1. |
| EXACT_MATCH | `exact-003` | A quoted document title and article were combined with OR, marking the whole document exact and pushing the requested article outside Top5. Deterministic matching bug, not a ranking-model problem. |
| NO_MATCH_THRESHOLD | `nomatch-001`–`nomatch-003` | Threshold 0.0 accepted every dense result. Unrelated top scores were 0.5614, 0.5171 and 0.4595. |

No semantic recall, dense ranking or temporal-filter pattern justified Hybrid, sparse retrieval or a
reranker: 22 of 23 Gold evidence cases were rank 1, and temporal correctness was 100%.

## Change decision

The single experimental optimization was threshold calibration from 0.0 to 0.60. The weakest relevant
Gold score was 0.6244 and the strongest no-match score was 0.5614, leaving an observed margin without
changing topK, embeddings or ranking.

The exact-reference conjunction was repaired separately as a deterministic correctness bug, with a
focused regression test. Router rules were intentionally left unchanged so E1 remains interpretable
and the residual limitation is visible.

## E0 versus E1

| Metric | E0 | E1 |
|---|---:|---:|
| Router Accuracy | 0.8333 | 0.8333 |
| Policy-required Recall | 0.8077 | 0.8077 |
| Recall@1 | 0.9565 | 1.0000 |
| Recall@3 | 0.9565 | 1.0000 |
| Recall@5 | 0.9565 | 1.0000 |
| MRR | 0.9565 | 1.0000 |
| ExactReferenceAccuracy | 0.7500 | 1.0000 |
| TemporalVersionAccuracy | 1.0000 | 1.0000 |
| NoMatchAccuracy | 0.0000 | 1.0000 |
| FalseMatchCount | 3 | 0 |

Fixed cases: `exact-003`, `nomatch-001`, `nomatch-002`, `nomatch-003`. No new failed case appeared.
The remaining five Router false negatives are a clear future candidate, but changing them in this
experiment would violate the one-major-change boundary.

## Manual generation/citation review

Ten representative full-Agent cases used real MySQL, BGE-M3, Qdrant and DeepSeek. Eight passed all
human checks. `semantic-002` safely avoided unsupported policy claims but was incomplete because of the
known Router miss. `mixed-002` cited the correct evidence for all core procedures but added the word
“质押”, which was not present in the selected evidence; this is one minor GENERATION expansion, not a
retrieval failure. No citation pointed to the wrong article.

## Phase 7.5 Router Hardening (E2)

The five E1 false negatives (`semantic-001`–`semantic-004`, `temporal-002`) were Router failures,
not Retriever failures: direct retrieval already returned every Gold reference at rank 1. Phase 7.5
therefore changed only the deterministic `PolicyRetrievalDecisionEngine` production behavior. The
small rule now recognizes explicit policy signals, explicit policy references, or a normative modal
combined with a policy/process action. A current or prior USER loan number upgrades policy intent to
`SUPPLEMENTAL`; an Assistant-only loan number does not.

The final E2 was executed on clean Router commit
`a49028db8903b5354abb48b4288b6bab16dcd507` with the same 30 cases, corpus, BGE-M3 embeddings,
real MySQL/Qdrant, topK 5 and threshold 0.60.

| Metric | E1 | E2 |
|---|---:|---:|
| Router Accuracy | 0.8333 | 1.0000 |
| Policy-required Recall | 0.8077 | 1.0000 |
| Recall@1 | 1.0000 | 1.0000 |
| Recall@3 | 1.0000 | 1.0000 |
| Recall@5 | 1.0000 | 1.0000 |
| MRR | 1.0000 | 1.0000 |
| ExactReferenceAccuracy | 1.0000 | 1.0000 |
| TemporalVersionAccuracy | 1.0000 | 1.0000 |
| NoMatchAccuracy | 1.0000 | 1.0000 |
| FalseMatchCount | 0 | 0 |

The report corpus hash is a normalized-content SHA-256: CRLF and CR are converted to LF before
hashing. It is not a raw-file hash. Retriever code and configuration were not modified, and E1/E2
retrieval latency variation is not treated as a performance improvement.

Manual reviews are preserved separately as `POLICY_RAG_MANUAL_REVIEW_E1.md` and
`POLICY_RAG_MANUAL_REVIEW_E2.md`. In E2, `semantic-002` followed
`REQUIRED → MATCHED → Policy Context → DeepSeek → valid [P1]`. The E2 `mixed-002` answer did not
reproduce the earlier unsupported “质押” generation expansion. Phase 7.5 did not change generation
constraints, so the historical generation issue is not considered fixed.
