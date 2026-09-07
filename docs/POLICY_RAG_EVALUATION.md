# Policy RAG Evaluation

Phase 7.4 uses a fixed, reviewable dataset rather than adding retrieval components by default.

## Inputs

- `evaluation/policy-rag-cases.json`: 30 hand-authored Gold cases.
- `evaluation/policy-rag-corpus.json`: versioned `EVALUATION_SYNTHETIC` corpus.
- The runner creates `loanops_policy_rag_eval` as an isolated MySQL schema and Qdrant collection,
  then removes both after the run. It never downloads policy text at test time.

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
```

For the representative real DeepSeek review:

```powershell
.\scripts\evaluate-policy-rag.ps1 -Label e1 -Threshold 0.60 -ManualReview
```

The API key remains an environment variable. Reports are written to `evaluation/results/`, while the
human evidence review is `evaluation/POLICY_RAG_MANUAL_REVIEW.md`.

## Hard gates

- temporal version correctness is 100%;
- required no-match requests abstain without calling the model;
- invalid citations are rejected before transcript completion;
- financial facts come from read-only Tools;
- conversation persistence contains only USER/ASSISTANT messages;
- the existing five-case live Agent baseline remains 5/5.

When E0 fails, classify failures by responsible layer before changing code. Apply at most one main
optimization, rerun the identical corpus and dataset, and remove the change if E1 does not improve
the targeted metric without regression.
