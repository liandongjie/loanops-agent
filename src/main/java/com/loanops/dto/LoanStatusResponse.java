package com.loanops.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code GET /api/loans/{loanNo}/status} 返回的综合贷款状态。
 * {@code LoanStatusService} 直接创建本对象，既包含当前待处理期次的诊断，
 * 也包含整笔贷款的结清状态。它不同于供大模型 Tool 使用的三个专用事实对象，
 * 是面向该 HTTP 查询接口的一次性组合结果。
 *
 * @param loanNo 被查询贷款的业务编号
 * @param installmentNo 当前待处理期次编号；没有未还期次时为空
 * @param dueDate 当前待处理期次的约定到期日；没有未还期次时为空
 * @param asOfDate 本次状态计算使用的业务日期
 * @param dueAmount 当前待处理期次的应还总额；没有未还期次时为空
 * @param paidAmount 当前待处理期次关联的实际还款金额之和；没有未还期次时为空
 * @param outstandingAmount 当前待处理期次的剩余未还金额；没有未还期次时为空
 * @param overdue 当前待处理期次是否逾期；没有未还期次时为 {@code false}
 * @param overdueDays 当前待处理期次的逾期天数；未逾期或没有未还期次时为 0
 * @param settled 整笔贷款是否已经结清
 * @param totalOutstanding 整笔贷款所有期次的未还金额之和
 */
public record LoanStatusResponse(
        String loanNo,
        Integer installmentNo,
        LocalDate dueDate,
        LocalDate asOfDate,
        BigDecimal dueAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        boolean overdue,
        long overdueDays,
        boolean settled,
        BigDecimal totalOutstanding) {
}
