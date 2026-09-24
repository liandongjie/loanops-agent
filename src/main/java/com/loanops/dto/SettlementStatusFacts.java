package com.loanops.dto;

import java.math.BigDecimal;

/**
 * 整笔贷款维度的结清事实，由 {@code LoanStatusService} 汇总全部还款计划后，
 * 通过 {@code LoanOpsTools.getSettlementStatus} 提供给大模型。
 * 它不描述某个当前期次；如果要查看当前一期的应还、已还和未还金额，应使用
 * {@link CurrentRepaymentFacts}。
 *
 * @param loanNo 被查询贷款的业务编号
 * @param settled 全部还款计划的未还金额是否都为 0
 * @param totalOutstanding 各期未还金额之和，表示整笔贷款当前的未还总额
 */
public record SettlementStatusFacts(
        String loanNo,
        boolean settled,
        BigDecimal totalOutstanding) {
}
