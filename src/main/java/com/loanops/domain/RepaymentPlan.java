package com.loanops.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一笔贷款的某一期还款计划，是后续金额计算和逾期判断使用的领域数据。
 * 它只记录该期约定的本金、利息和到期日，不保存“已还”“未还”或“逾期”等派生状态；
 * 这些状态由业务 Service 根据实际还款记录和业务日期动态计算。
 *
 * @param id            该期还款计划的唯一标识，供 {@link PaymentRecord} 关联到具体期次，例如 {@code 1}
 * @param loanId        所属贷款合同的唯一标识，关联LoanContract.id，例如 {@code 1}
 * @param installmentNo 期次编号，也用于到期日相同时的稳定排序，例如 {@code 1}，代表第一期
 * @param dueDate       该期约定到期日；当本期仍有未还金额且业务日期晚于此日期时，本期才构成逾期，例如
 *                      {@code 2023-01-01}
 * @param principalDue  该期应归还的本金，不包含利息，例如 {@code 8000}
 * @param interestDue   该期应支付的利息，不包含本金，例如 {@code 500}
 */
public record RepaymentPlan(
        Long id,
        Long loanId,
        int installmentNo,
        LocalDate dueDate,
        BigDecimal principalDue,
        BigDecimal interestDue) {
}
