# LoanOps Agent 最近一次验收快照

> 这是一次**时间点证据**，不是未来所有 commit 的永久保证。代码变更后应重新执行相关 Gate。

## 基线

验收日期：**2026-09-10**。


```text
验收代码基线（main merge commit）:
2cc3fb1e6779db49950c5376394b6bc72963b989

Local Policy Bootstrap feature commit:
ecc69ea7ce7b79b8ebb105f4bdcff9b21f5efd6d
```

PR #8 已合入 `main`。

## 验收记录

本轮围绕 Local Policy Bootstrap 与完整本地 Policy RAG 体验进行了自动与手工验收。

### Maven

```text
164 tests
0 failures
0 errors
5 skipped
BUILD SUCCESS
```

skipped 为外部依赖 opt-in gates 时，不应解释为外部 Provider / Hero PASS。

### Local Policy Bootstrap

真实本地运行：

```text
LOCAL_POLICY_BOOTSTRAP_SUCCESS
documents=4
versions=5
chunks=41
indexed=41
```

MySQL 手工确认：

```text
policy_document         = 4
policy_document_version = 5
policy_chunk            = 41
```

Qdrant 手工确认：

```text
status       = green
points_count = 41
```

重复 Bootstrap 仍保持同一规模。

### Bootstrap 专项行为

提交前 Review 已确认：

- fresh bootstrap；
- 重复执行幂等；
- partial Demo recovery；
- isolated missing chunk recovery；
- unknown existing policy refusal；
- dependency / index rebuild failure 显式失败；
- 不修改 production ingestion 逻辑来迎合测试。

### DeepSeek 手工对话

本地真实 Terminal Chat：

```text
LN-10002 为什么逾期？
```

成功获得确定性业务事实：

```text
应还 8500.00
已还 5000.00
欠款 3500.00
逾期 21 天（业务日期 2026-09-10）
```

第二轮：

```text
按照规定现在应该怎么处理？
```

Policy RAG 返回：

```text
[P1] EVAL-POST-2026 第四十四条
[P2] EVAL-RISK-2026 第十二条
```

并把 Policy Evidence 与当前贷款事实组合解释。

第三轮：

```text
那他现在还欠多少钱？
```

正确保持 `LN-10002` Conversation context，并返回当前期剩余欠款 `3500.00`。

第四轮：

```text
这个规定具体是哪一条？
```

正确继续解释上一轮 `[P1] / [P2]` 条款。

### 本轮人工验证覆盖

```text
Terminal Chat
+ DeepSeek
+ Tool Calling
+ Conversation
+ MySQL persistence
+ Policy Router
+ BGE-M3
+ Qdrant
+ Policy Retrieval
+ Citation [P1]/[P2]
```

## 重要限制

- 本轮政策语料是 synthetic demo corpus；
- 贷款 fixture 也是 synthetic data；
- Policy RAG 固定指标不代表 production accuracy；
- 手工 DeepSeek PASS 是真实 E2E 证据，但单次模型运行不代表统计稳定率；
- 当前项目不是银行生产服务。

## 历史快照

2026-09-08 的 Phase 9 snapshot 已移至：

[history/ACCEPTANCE_SNAPSHOT_2026-09-08.md](history/ACCEPTANCE_SNAPSHOT_2026-09-08.md)
