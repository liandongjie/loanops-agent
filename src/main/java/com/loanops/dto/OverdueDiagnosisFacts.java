package com.loanops.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 用于解释当前待处理期次是否逾期、逾期多少天的事实，由 {@code LoanStatusService}
 * 计算并通过 {@code LoanOpsTools.getOverdueDiagnosis} 提供给大模型。
 * 与 {@link CurrentRepaymentFacts} 相比，它突出到期日与业务日期的比较；
 * 与 {@link SettlementStatusFacts} 相比，它不是整笔贷款的汇总结果。
 *
 * @param loanNo 被诊断贷款的业务编号
 * @param currentRepaymentAvailable 是否存在仍有未还金额的当前待处理期次；为 {@code false} 时，本期到期日和金额明细为空
 * @param settled 整笔贷款是否已经结清
 * @param dueDate 当前待处理期次的约定到期日
 * @param asOfDate 本次诊断使用的业务日期，逾期判断以它为准而不是由大模型推算
 * @param dueAmount 本期应还总额
 * @param paidAmount 与本期还款计划关联的实际还款金额之和
 * @param outstandingAmount 本期剩余未还金额，最低为 0
 * @param overdue 本期是否仍有未还金额且 {@code asOfDate} 晚于 {@code dueDate}
 * @param overdueDays 本期逾期天数；未逾期时为 0
 */
public record OverdueDiagnosisFacts(
        String loanNo,
        boolean currentRepaymentAvailable,
        boolean settled,
        LocalDate dueDate,
        LocalDate asOfDate,
        BigDecimal dueAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        boolean overdue,
        long overdueDays) {
}
