package com.loanops.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 某一期实际发生的一笔还款，与 {@link RepaymentPlan} 描述的“约定应该还什么”相对应。
 * {@code LoanStatusService} 从数据库读取并转换出本对象，{@code RepaymentCalculator}
 * 再按 {@link #repaymentPlanId()} 汇总金额，得到该期已还金额。
 * 当前项目范围默认保存的记录就是有效还款，不在本对象中表示处理中、失败、退款或冲正状态。
 *
 * @param id              该笔实际还款记录的唯一标识，例如 {@code 201}
 * @param loanId          所属贷款合同的内部唯一标识，关联LoanContract.id，例如 {@code 1}
 * @param repaymentPlanId 本次还款对应的还款计划标识，用于把金额计入正确期次，例如 {@code 1}
 * @param paymentDate     这笔还款实际发生的日期，例如 {@code 2028-08-19}
 * @param amount          这笔记录实际偿还的金额，例如 {@code 8500}
 */
public record PaymentRecord(
        Long id,
        Long loanId,
        Long repaymentPlanId,
        LocalDate paymentDate,
        BigDecimal amount) {
}
