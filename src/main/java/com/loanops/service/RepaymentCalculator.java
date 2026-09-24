package com.loanops.service;

import com.loanops.domain.PaymentRecord;
import com.loanops.domain.RepaymentPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 按 {@link RepaymentPlan} 和实际 {@link PaymentRecord} 计算单个期次的确定性金额事实。
 * 上层 {@link LoanDiagnosisService} 使用这些结果选择当前待处理期次并判断逾期或结清；
 * 本类只负责金额计算，不负责选择期次，也不让大模型参与金融计算。
 */
@Service
public class RepaymentCalculator {

    /**
     * 计算指定还款计划的应还、已还和未还金额。
     * 传入的还款记录可以包含其他期次，本方法只汇总 {@code repaymentPlanId} 与当前计划匹配的记录。
     *
     * @param plan     要计算的某一期还款计划
     * @param payments 实际还款记录列表
     * @return 包含原计划及本期应还、已还、未还金额的计算结果
     */
    public RepaymentResult calculate(RepaymentPlan plan, List<PaymentRecord> payments) {
        BigDecimal dueAmount = plan.principalDue().add(plan.interestDue());
        BigDecimal paidAmount = payments.stream()
                .filter(payment -> payment.repaymentPlanId().equals(plan.id()))
                .map(PaymentRecord::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 多还款不会形成负的“未还金额”；MVP 也不在这里处理多还部分的跨期结转。
        BigDecimal outstandingAmount = dueAmount.subtract(paidAmount).max(BigDecimal.ZERO); // 未还金额 = 应还金额 - 已还金额
        return new RepaymentResult(plan, dueAmount, paidAmount, outstandingAmount);
    }
    /*
     * public RepaymentResult calculate(
     * RepaymentPlan plan,
     * List<PaymentRecord> payments) {
     * 
     * // 本期应还金额 = 本金 + 利息
     * BigDecimal dueAmount =
     * plan.principalDue().add(plan.interestDue());
     * 
     * // 初始已还金额为 0
     * BigDecimal paidAmount = BigDecimal.ZERO;
     * 
     * // 遍历所有实际还款记录
     * for (PaymentRecord payment : payments) {
     * 
     * // 只统计属于当前还款计划的记录
     * if (payment.repaymentPlanId().equals(plan.id())) {
     * paidAmount = paidAmount.add(payment.amount());
     * }
     * }
     * 
     * // 未还金额 = 应还 - 已还，最低为 0
     * BigDecimal outstandingAmount =
     * dueAmount.subtract(paidAmount)
     * .max(BigDecimal.ZERO);
     * 
     * // 返回计算结果
     * return new RepaymentResult(
     * plan,
     * dueAmount,
     * paidAmount,
     * outstandingAmount);
     * }
     * 
     */

    /**
     * 单个还款期次的金额计算结果。
     *
     * @param plan              本次计算对应的还款计划
     * @param dueAmount         本期应还总额，即应还本金与应还利息之和
     * @param paidAmount        与本期计划关联的全部实际还款金额之和；没有记录时为 0
     * @param outstandingAmount 本期剩余未还金额，即 {@code max(dueAmount - paidAmount, 0)}
     */
    public record RepaymentResult(
            RepaymentPlan plan,
            BigDecimal dueAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount) {
    }
}
