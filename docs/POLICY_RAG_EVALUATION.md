# Policy RAG Evaluation

Phase 7.4 uses a fixed, reviewable dataset rather than adding retrieval components by default.
Phase 7.5 reuses those frozen retrieval inputs and adds only a focused Router regression set.

## Inputs

- `evaluation/policy-rag-cases.json`: 30 hand-authored Gold cases.
- `evaluation/policy-rag-corpus.json`: versioned `EVALUATION_SYNTHETIC` corpus.
- `evaluation/policy-router-hardening-cases.json`: 22 Router-only regression cases; it is not used to
  calculate retrieval metrics.
- The runner creates `loanops_policy_rag_eval` as an isolated MySQL schema and Qdrant collection,
  then removes both after the run. It never downloads policy text at test time.

The reported corpus SHA-256 is calculated after normalizing CRLF and CR line endings to LF. It is a
normalized-content hash, not a raw-file hash.

Gold is expressed with stable business metadata such as decision, retrieval status, document number,
article number, version applicability and expected Tool names. Database UUIDs are not Gold labels.

## Metrics

- **Router Accuracy**: correct three-way `NOT_REQUIRED/SUPPLEMENTAL/REQUIRED` decisions divided by all cases.
- **Policy-required Recall**: Gold `SUPPLEMENTAL/REQUIRED` cases that were not routed to `NOT_REQUIRED`.
- **Recall@K**: cases whose Gold `(documentNumber, articleNo)` occurs in the first K retrieval hits.
- **MRR**: mean of `1 / first Gold rank`; a missing Gold contributes zero.
- **ExactReferenceAccuracy**: exact-reference cases with the Gold evidence at rank 1.
- **TemporalVersionAccuracy**: temporal cases containing the Gold version and no inapplicable version of
  the same document.
- **NoMatchAccuracy**: Gold no-match cases whose post-threshold context status is `NO_MATCH`.
- **FalseMatchCount**: Gold no-match cases incorrectly producing `MATCHED`.

Retrieval metrics are evaluated directly even when the Router is wrong. This keeps routing failures
separate from retrieval failures.

## Run

Requirements: Java 21, Docker MySQL/Qdrant, and Ollama with `bge-m3`.

```powershell
$env:JAVA_HOME = "D:\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Frozen baseline
.\scripts\evaluate-policy-rag.ps1 -Label e0 -Threshold 0.0

# Accepted targeted change
.\scripts\evaluate-policy-rag.ps1 -Label e1 -Threshold 0.60

# Router hardening on the same dataset and corpus
.\scripts\evaluate-policy-rag.ps1 -Label e2 -Threshold 0.60
```

For the representative real DeepSeek review:

```powershell
.\scripts\evaluate-policy-rag.ps1 -Label e2 -Threshold 0.60 -ManualReview
```

The API key remains an environment variable. Reports are written to `evaluation/results/`, while the
historical E1 and clean-commit E2 human reviews are preserved separately as
`evaluation/POLICY_RAG_MANUAL_REVIEW_E1.md` and `evaluation/POLICY_RAG_MANUAL_REVIEW_E2.md`.

Phase 7.5 changes Router behavior only; Retriever code, topK, threshold, embedding model and Qdrant
configuration remain unchanged. Retrieval latency varies between runs and is not a Phase 7.5
optimization claim. The E2 `mixed-002` run did not reproduce the E1 unsupported “质押” expansion,
but generation constraints were not changed and that historical finding is not considered fixed.

## Hard gates

- on the fixed 30-case corpus, TemporalVersionAccuracy is 1.0000;
- required no-match requests abstain without calling the model;
- invalid citations are rejected before transcript completion;
- financial facts come from read-only Tools;
- conversation persistence contains only USER/ASSISTANT messages;
- rerun the existing five-case live Agent baseline and preserve any failure evidence.

The live Agent baseline is a bounded stochastic provider regression gate, not a stable-rate claim.
Historical unchanged-configuration runs showed Tool-choice variance, so one 5/5 run must not be
reported as statistically stable behavior.

When E0 fails, classify failures by responsible layer before changing code. Apply at most one main
optimization, rerun the identical corpus and dataset, and remove the change if E1 does not improve
the targeted metric without regression.
