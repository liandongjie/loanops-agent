# Reproducible Demo

## 1. 为什么需要固定业务日期

`LN-10002` 的到期日是 `2026-08-20`。如果运行时直接使用真实系统日期，逾期天数会随时间变化，就无法把“逾期 3 天”作为可重复的演示结果。

项目因此允许仅在演示/验收时显式指定：

```text
loanops.business-date=2026-08-23
loanops.business-zone=Asia/Shanghai
```

生产默认仍使用系统 Clock。

## 2. 自动验收

PowerShell：

```powershell
./scripts/verify-resume-mvp.ps1
```

确定性模式不需要 API Key。脚本会运行完整测试，并启动一个临时 Spring Boot 进程检查三笔 fixture。

## 3. 确定性 REST Case

### A. 当前应还

```http
GET /api/loans/LN-10001/status
```

核心期望：

```text
dueAmount = 8500.00
paidAmount = 0.00
outstandingAmount = 8500.00
overdue = false
```

### B. 逾期诊断

```http
GET /api/loans/LN-10002/status
```

核心期望：

```text
dueAmount = 8500.00
paidAmount = 5000.00
outstandingAmount = 3500.00
dueDate = 2026-08-20
asOfDate = 2026-08-23
overdue = true
overdueDays = 3
```

### C. 结清

```http
GET /api/loans/LN-10003/status
```

核心期望：

```text
settled = true
totalOutstanding = 0.00
```

## 4. 真实 Agent E2E

先配置：

```powershell
$env:DEEPSEEK_API_KEY="your-key"
```

执行：

```powershell
./scripts/verify-resume-mvp.ps1 -WithAi
```

三个固定问题：

```text
LN-10001 本期应该还多少钱？
LN-10002 为什么逾期？
LN-10003 是否已经结清？
```

自然语言措辞可能变化，因此验收不比较整段文本，而是同时检查：

- 回答包含关键业务事实；
- Spring AI Debug 日志出现正确 Tool 名称；
- 不存在的贷款不会得到虚构诊断；
- 写请求前后确定性 REST 状态不变。

## 5. 代理环境

如果 PowerShell/curl 能访问模型 API，但 Java 进程访问模型 API 超时，先检查 JVM 是否使用了本机代理。

脚本支持：

```powershell
./scripts/verify-resume-mvp.ps1 -WithAi -ProxyHost 127.0.0.1 -ProxyPort 7890
```

脚本只把代理传给临时 JVM 进程，不修改系统全局配置。
