# Acceptance Snapshot — 2026-09-08（历史）

> 这是旧 Phase 9 收口时的环境快照，仅用于保留历史证据。它不代表当前 `main` 的最新验收状态。

当时记录：

```text
Phase 9 status: DONE
Java: 21.0.10
mvn clean verify: BUILD SUCCESS
tests: 115
failures: 0
errors: 0
skipped: 5
```

当时 Hero E2E 状态：

```text
ENV_BLOCKED
```

原因：

```text
Docker engine 未运行
MySQL 127.0.0.1:3307 不可达
Qdrant 127.0.0.1:6333 不可达
```

该快照明确要求：

> 环境未执行的 Hero 不能记为 PASS。

之后项目继续完成了：

- 多 Chat Provider 验收；
- Terminal Chat；
- Safe SSE Streaming；
- Local Policy Bootstrap；
- MySQL / Qdrant / BGE-M3 本地链路；
- DeepSeek Tool + Conversation + Policy RAG 手工验收。

因此当前状态请查看：

[../ACCEPTANCE_SNAPSHOT.md](../ACCEPTANCE_SNAPSHOT.md)

保留本文件的原因不是继续展示旧状态，而是证明项目没有用后续 PASS 覆盖当时真实的 `ENV_BLOCKED` 证据。
