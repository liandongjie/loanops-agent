# LoanOps Agent — Domain Rules

## 1. 文档作用

本文件是 LoanOps Agent MVP 的**业务规则唯一事实来源（Source of Truth）**。

所有涉及贷款还款、逾期和结清判断的代码必须以本文件为准。

如果代码、测试、Prompt 或其他文档与本文件冲突，应停止开发并先处理冲突，不允许开发者或 Coding Agent 自行发明新的业务规则。

---

## 2. 核心领域对象

MVP 只包含 3 个核心领域对象。

---

### 2.1 LoanContract

代表一笔贷款合同。

最小字段：

```text
id
loanNo
borrowerName
principal
startDate
endDate
```

约束：

- `principal` 使用 `BigDecimal`；
- 日期使用 `LocalDate`；
- MVP 不保存 `OVERDUE`、`SETTLED` 等派生业务状态作为事实来源；
- 逾期和结清状态由还款计划和实际还款记录动态计算。

---

### 2.2 RepaymentPlan

代表一笔贷款的某一期还款计划。

最小字段：

```text
id
loanId
installmentNo
dueDate
principalDue
interestDue
```

约束：

- `principalDue`、`interestDue` 使用 `BigDecimal`；
- `installmentNo` 用于稳定排序；
- `dueDate` 使用 `LocalDate`。

---

### 2.3 PaymentRecord

代表某一期实际发生的还款记录。

最小字段：

```text
id
loanId
repaymentPlanId
paymentDate
amount
```

约束：

- `amount` 使用 `BigDecimal`；
- MVP 默认记录即为有效还款；
- 不建 `PROCESSING / FAILED / REVERSED / REFUNDED` 等支付状态；
- 复杂支付渠道和冲正逻辑不属于 MVP。

---

## 3. 金额处理规则

### 3.1 类型约束

所有金融金额必须使用：

```java
BigDecimal
```

禁止使用：

```java
float
double
```

所有金额比较必须使用 `compareTo`，不得依赖 `equals` 判断数值大小。

---

### 3.2 本期应还金额

定义：

```text
dueAmount = principalDue + interestDue
```

示例：

```text
principalDue = 8000.00
interestDue  = 500.00

dueAmount = 8500.00
```

---

### 3.3 本期已还金额

定义：

```text
paidAmount
=
当前 repaymentPlan 关联的全部 PaymentRecord.amount 之和
```

如果不存在还款记录：

```text
paidAmount = 0
```

---

### 3.4 本期未还金额

定义：

```text
rawOutstanding = dueAmount - paidAmount

outstandingAmount = max(rawOutstanding, 0)
```

示例：

```text
dueAmount  = 8500.00
paidAmount = 5000.00

outstandingAmount = 3500.00
```

若：

```text
dueAmount  = 8500.00
paidAmount = 9000.00
```

则：

```text
outstandingAmount = 0
```

MVP 不处理多还部分的跨期结转逻辑。

---

## 4. 当前待处理期次

当前待处理期次定义为：

1. 取得该贷款全部 `RepaymentPlan`；
2. 分别计算每一期 `outstandingAmount`；
3. 过滤 `outstandingAmount > 0` 的计划；
4. 按 `dueDate ASC` 排序；
5. 如果 `dueDate` 相同，再按 `installmentNo ASC` 排序；
6. 第一条结果即 `currentRepaymentPlan`。

如果不存在 `outstandingAmount > 0` 的计划：

```text
currentRepaymentPlan = none
```

通常意味着贷款已经结清。

---

## 5. 业务日期

### 5.1 禁止直接使用系统当前日期

领域业务代码不得直接调用：

```java
LocalDate.now()
```

必须通过可注入的：

```java
Clock
```

取得当前业务日期。

生产环境可以使用系统 Clock。

测试环境必须使用固定 Clock，例如：

```text
asOfDate = 2026-08-23
```

这样保证测试结果可重复。

---

## 6. 逾期判断

对当前待处理期次：

```text
overdue =
outstandingAmount > 0
AND
asOfDate > dueDate
```

特别约束：

```text
asOfDate == dueDate
```

不算逾期。

---

## 7. 逾期天数

仅当：

```text
overdue == true
```

时：

```text
overdueDays = DAYS.between(dueDate, asOfDate)
```

否则：

```text
overdueDays = 0
```

示例：

```text
dueDate  = 2026-08-20
asOfDate = 2026-08-23

overdueDays = 3
```

---

## 8. 贷款结清判断

整笔贷款：

```text
settled = true
```

当且仅当：

```text
所有 RepaymentPlan 的 outstandingAmount == 0
```

同时：

```text
totalOutstanding
=
所有 repayment plan.outstandingAmount 之和
```

如果：

```text
totalOutstanding == 0
```

则：

```text
settled = true
```

MVP 不通过 `LoanContract.status` 判断结清。

---

## 9. 固定业务 Case

### 9.1 Case A — LN-10001

用途：验证本期应还查询。

固定业务事实：

```text
loanNo = LN-10001

current installment:
principalDue = 8000.00
interestDue  = 500.00
paidAmount   = 0.00
```

期望：

```text
dueAmount         = 8500.00
paidAmount        = 0.00
outstandingAmount = 8500.00
```

到期日应设置为业务日期之后，从而形成未到期案例。

---

### 9.2 Case B — LN-10002

用途：核心逾期诊断 Case。

固定业务事实：

```text
loanNo = LN-10002

principalDue = 8000.00
interestDue  = 500.00
paidAmount   = 5000.00

dueDate  = 2026-08-20
asOfDate = 2026-08-23
```

必须得到：

```text
dueAmount         = 8500.00
paidAmount        = 5000.00
outstandingAmount = 3500.00

overdue     = true
overdueDays = 3
```

---

### 9.3 Case C — LN-10003

用途：验证整笔贷款结清判断。

固定事实：

```text
loanNo = LN-10003
```

该贷款所有还款计划均完全偿还。

必须得到：

```text
settled = true
totalOutstanding = 0.00
```

---

## 10. MVP 不定义的业务规则

以下情况本文件故意不定义，Coding Agent 不得自行实现：

- 罚息；
- 逾期利息；
- 宽限期；
- 节假日顺延；
- 提前还款；
- 部分提前还款；
- 多还款跨期结转；
- 还款冲正；
- 账务流水；
- 自动扣款；
- 催收；
- 展期；
- 核销；
- 贷款重组；
- 复杂利率模型；
- 真实银行会计分录。

如果未来需要这些能力，应先更新本文件，再开发代码。

---

## 11. AI 与业务规则关系

AI 不拥有本文件中的业务规则。

正确调用路径：

```text
Agent
  ↓
Tool
  ↓
LoanDiagnosisService
  ↓
RepaymentCalculator
  ↓
确定性结果
```

错误实现：

```text
Agent Prompt
  ↓
让 LLM 自己计算逾期、金额或结清状态
```

Tool 内也不得复制本文件的业务算法。

业务规则只能有一套实现。
