package com.loanops.service;

import com.loanops.domain.LoanContract;
import com.loanops.domain.PaymentRecord;
import com.loanops.domain.RepaymentPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 确定性的贷款诊断业务层。
 *
 * <p>本类接收一笔 {@link LoanContract}、其约定的 {@link RepaymentPlan} 和实际发生的
 * {@link PaymentRecord}，负责找出当前待处理期次、判断是否逾期及逾期天数，并判断整笔贷款是否结清。
 * 单个期次的应还、已还和未还金额统一交给 {@link RepaymentCalculator} 计算，避免在诊断规则中重复金额公式。
 * 本类不查询数据库，也不处理 Agent 或大模型调用。</p>
 */
@Service
public class LoanDiagnosisService {

    private final RepaymentCalculator repaymentCalculator;

    /** 可注入的业务时钟；测试使用固定时间，便能稳定验证逾期判断和逾期天数。 */
    private final Clock clock;

    public LoanDiagnosisService(RepaymentCalculator repaymentCalculator, Clock clock) {
        this.repaymentCalculator = repaymentCalculator;
        this.clock = clock;
    }

    /**
     * 找出这笔贷款当前最先需要处理的未还期次，并生成该期次的诊断结果。
     *
     * <p>参与计算的数据会先限定为 {@code loan} 自己的计划和还款记录，防止其他贷款的数据污染结果。
     * 候选期次必须仍有未还金额；随后按到期日升序排列，到期日相同时再按期号升序排列，取第一条。
     * 如果所有期次都已还清，则没有“当前待处理期次”，返回 {@code null}。</p>
     *
     * @param loan 要诊断的一笔贷款合同，用其内部标识确定数据归属
     * @param plans 可能包含多笔贷款数据的约定还款计划
     * @param payments 可能包含多笔贷款数据的实际还款记录
     * @return 当前待处理期次的金额与逾期诊断；没有未还期次时返回 {@code null}
     */
    public RepaymentDiagnosis currentRepayment(
            LoanContract loan, List<RepaymentPlan> plans, List<PaymentRecord> payments) {
        List<RepaymentPlan> loanPlans = plansForLoan(loan, plans);
        List<PaymentRecord> loanPayments = paymentsForLoan(loan, payments);

        return loanPlans.stream()
                .map(plan -> repaymentCalculator.calculate(plan, loanPayments))
                .filter(result -> result.outstandingAmount().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing((RepaymentCalculator.RepaymentResult result) -> result.plan().dueDate())
                        .thenComparing(result -> result.plan().installmentNo()))
                .findFirst()
                .map(this::toDiagnosis)
                .orElse(null);
    }

    /**
     * 判断整笔贷款是否已经结清，而不是只判断当前一期是否还清。
     *
     * <p>本方法复用 {@link RepaymentCalculator} 计算属于该贷款的每一期未还金额，再将其相加；
     * 只有全部期次的未还总额为 0，整笔贷款才算结清。</p>
     *
     * @param loan 要判断结清状态的一笔贷款合同
     * @param plans 可能包含多笔贷款数据的约定还款计划
     * @param payments 可能包含多笔贷款数据的实际还款记录
     * @return 整笔贷款的结清标志和全部期次未还总额
     */
    public SettlementDiagnosis settlement(
            LoanContract loan, List<RepaymentPlan> plans, List<PaymentRecord> payments) {
        List<RepaymentPlan> loanPlans = plansForLoan(loan, plans);
        List<PaymentRecord> loanPayments = paymentsForLoan(loan, payments);

        BigDecimal totalOutstanding = loanPlans.stream()
                .map(plan -> repaymentCalculator.calculate(plan, loanPayments).outstandingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SettlementDiagnosis(totalOutstanding.compareTo(BigDecimal.ZERO) == 0, totalOutstanding);
    }

    private List<RepaymentPlan> plansForLoan(LoanContract loan, List<RepaymentPlan> plans) {
        // 领域服务负责守住贷款聚合边界，避免调用方混入其他贷款的计划后产生误诊断。
        return plans.stream()
                .filter(plan -> Objects.equals(plan.loanId(), loan.id()))
                .toList();
    }

    private List<PaymentRecord> paymentsForLoan(LoanContract loan, List<PaymentRecord> payments) {
        return payments.stream()
                .filter(payment -> Objects.equals(payment.loanId(), loan.id()))
                .toList();
    }

    private RepaymentDiagnosis toDiagnosis(RepaymentCalculator.RepaymentResult result) {
        LocalDate asOfDate = LocalDate.now(clock);
        LocalDate dueDate = result.plan().dueDate();
        // isAfter 表示严格晚于：到期日当天不算逾期，同时本期还必须存在未还金额。
        boolean overdue = result.outstandingAmount().compareTo(BigDecimal.ZERO) > 0 && asOfDate.isAfter(dueDate);
        long overdueDays = overdue ? ChronoUnit.DAYS.between(dueDate, asOfDate) : 0;
        return new RepaymentDiagnosis(
                result.plan(), result.dueAmount(), result.paidAmount(), result.outstandingAmount(),
                asOfDate, overdue, overdueDays);
    }

    /**
     * 当前待处理期次的一次确定性诊断结果，供上层 Service 组装不同用途的 DTO。
     *
     * @param plan 被选中的当前待处理期次
     * @param dueAmount 本期本金与利息之和
     * @param paidAmount 归属于本期的实际还款总额
     * @param outstandingAmount 本期仍需偿还的金额，最低为 0
     * @param asOfDate 本次诊断采用的业务日期
     * @param overdue 本期在业务日期是否已经逾期
     * @param overdueDays 本期逾期天数；未逾期时为 0
     */
    public record RepaymentDiagnosis(
            RepaymentPlan plan,
            BigDecimal dueAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
            LocalDate asOfDate,
            boolean overdue,
            long overdueDays) {
    }

    /**
     * 整笔贷款的一次结清诊断结果，与上面的当前期次诊断处于不同业务范围。
     *
     * @param settled 全部期次是否均无未还金额
     * @param totalOutstanding 全部期次的未还金额之和
     */
    public record SettlementDiagnosis(boolean settled, BigDecimal totalOutstanding) {
    }
}
