package com.loanops.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 当前待处理期次的还款事实，由 {@code LoanStatusService} 计算并通过
 * {@code LoanOpsTools.getCurrentRepayment} 提供给大模型。
 * 它关注某一期应该还什么、已经还了多少和还欠多少；与
 * {@link SettlementStatusFacts} 的整笔贷款汇总口径不同。
 *
 * @param loanNo 被查询贷款的业务编号
 * @param currentRepaymentAvailable 是否存在仍有未还金额的当前待处理期次；为 {@code false} 时，期次和金额明细为空
 * @param settled 整笔贷款的所有期次是否均已还清，不只是当前一期的状态
 * @param installmentNo 当前待处理期次的编号
 * @param dueDate 当前待处理期次的约定到期日
 * @param principalDue 本期约定应还本金
 * @param interestDue 本期约定应还利息
 * @param dueAmount 本期应还总额，即应还本金与应还利息之和
 * @param paidAmount 与本期还款计划关联的实际还款金额之和
 * @param outstandingAmount 本期剩余未还金额，最低为 0
 * @param overdue 当前待处理期次是否仍有未还金额且业务日期已经晚于到期日
 * @param overdueDays 当前期次的逾期天数；未逾期时为 0
 */
public record CurrentRepaymentFacts(
        String loanNo,
        boolean currentRepaymentAvailable,
        boolean settled,
        Integer installmentNo,
        LocalDate dueDate,
        BigDecimal principalDue,
        BigDecimal interestDue,
        BigDecimal dueAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        boolean overdue,
        long overdueDays) {
}
