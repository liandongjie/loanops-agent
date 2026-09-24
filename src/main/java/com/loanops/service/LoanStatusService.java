package com.loanops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loanops.domain.LoanContract;
import com.loanops.domain.PaymentRecord;
import com.loanops.domain.RepaymentPlan;
import com.loanops.dto.CurrentRepaymentFacts;
import com.loanops.dto.LoanStatusResponse;
import com.loanops.dto.OverdueDiagnosisFacts;
import com.loanops.dto.SettlementStatusFacts;
import com.loanops.exception.LoanNotFoundException;
import com.loanops.persistence.entity.LoanContractEntity;
import com.loanops.persistence.entity.PaymentRecordEntity;
import com.loanops.persistence.entity.RepaymentPlanEntity;
import com.loanops.persistence.mapper.LoanContractMapper;
import com.loanops.persistence.mapper.PaymentRecordMapper;
import com.loanops.persistence.mapper.RepaymentPlanMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 连接数据库访问、领域诊断和对外数据对象的贷款状态编排层。
 *
 * <p>
 * 本类通过 Mapper 从数据库读取持久化 Entity（数据库记录结构），将其转换为 Domain（参与业务规则的领域对象），
 * 再交给 {@link LoanDiagnosisService} 完成当前期次、逾期和整笔贷款结清诊断，最后根据 Controller 或 Agent
 * Tool
 * 的不同需要组装对应 DTO（层与层之间传递的数据对象）。还款金额、逾期和结清公式不在这里重新实现。
 * </p>
 */
@Service
public class LoanStatusService {

    private final LoanContractMapper loanContractMapper;
    private final RepaymentPlanMapper repaymentPlanMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final LoanDiagnosisService loanDiagnosisService;
    private final Clock clock;

    public LoanStatusService(
            LoanContractMapper loanContractMapper,
            RepaymentPlanMapper repaymentPlanMapper,
            PaymentRecordMapper paymentRecordMapper,
            LoanDiagnosisService loanDiagnosisService,
            Clock clock) {
        this.loanContractMapper = loanContractMapper;
        this.repaymentPlanMapper = repaymentPlanMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.loanDiagnosisService = loanDiagnosisService;
        this.clock = clock;
    }

    /**
     * 为 {@code /api/loans/{loanNo}/status} 接口查询综合贷款状态。
     * 返回结果同时包含当前待处理期次的诊断和整笔贷款的结清信息，供 HTTP 调用方一次查看完整状态。
     *
     * @param loanNo 对外使用的业务贷款编号
     * @return 面向 HTTP 接口的综合状态响应
     * @throws LoanNotFoundException 找不到该贷款编号对应的合同记录时抛出
     */
    public LoanStatusResponse getStatus(String loanNo) {
        DiagnosisSnapshot snapshot = diagnose(loanNo);
        LoanDiagnosisService.RepaymentDiagnosis current = snapshot.current();

        if (current == null) {
            return new LoanStatusResponse(
                    snapshot.loan().loanNo(), null, null, snapshot.asOfDate(), null, null, null,
                    false, 0, snapshot.settlement().settled(), snapshot.settlement().totalOutstanding());
        }

        return new LoanStatusResponse(
                snapshot.loan().loanNo(),
                current.plan().installmentNo(),
                current.plan().dueDate(),
                current.asOfDate(),
                current.dueAmount(),
                current.paidAmount(),
                current.outstandingAmount(),
                current.overdue(),
                current.overdueDays(),
                snapshot.settlement().settled(),
                snapshot.settlement().totalOutstanding());
    }

    /**
     * 为 Agent Tool 提供当前待处理期次的还款事实。
     * 与综合状态响应相比，这个 DTO 聚焦本期约定本金、利息以及应还、已还、未还金额。
     *
     * @param loanNo 对外使用的业务贷款编号
     * @return 当前待处理期次事实；没有未还期次时会明确标记当前期次不可用
     * @throws LoanNotFoundException 找不到该贷款编号对应的合同记录时抛出
     */
    public CurrentRepaymentFacts getCurrentRepaymentFacts(String loanNo) {
        DiagnosisSnapshot snapshot = diagnose(loanNo);
        LoanDiagnosisService.RepaymentDiagnosis current = snapshot.current();
        if (current == null) {
            return new CurrentRepaymentFacts(
                    snapshot.loan().loanNo(), false, snapshot.settlement().settled(),
                    null, null, null, null, null, null, null, false, 0);
        }
        return new CurrentRepaymentFacts(
                snapshot.loan().loanNo(), true, snapshot.settlement().settled(),
                current.plan().installmentNo(), current.plan().dueDate(),
                current.plan().principalDue(), current.plan().interestDue(),
                current.dueAmount(), current.paidAmount(), current.outstandingAmount(),
                current.overdue(), current.overdueDays());
    }

    /**
     * 为 Agent Tool 提供当前待处理期次的逾期诊断事实。
     * 该 DTO 聚焦到期日、业务日期、未还金额和逾期天数，便于 Agent 解释“是否逾期、逾期多久”。
     *
     * @param loanNo 对外使用的业务贷款编号
     * @return 当前待处理期次的逾期诊断事实
     * @throws LoanNotFoundException 找不到该贷款编号对应的合同记录时抛出
     */
    public OverdueDiagnosisFacts getOverdueDiagnosisFacts(String loanNo) {
        DiagnosisSnapshot snapshot = diagnose(loanNo);
        LoanDiagnosisService.RepaymentDiagnosis current = snapshot.current();
        if (current == null) {
            return new OverdueDiagnosisFacts(
                    snapshot.loan().loanNo(), false, snapshot.settlement().settled(),
                    null, snapshot.asOfDate(), null, null, null, false, 0);
        }
        return new OverdueDiagnosisFacts(
                snapshot.loan().loanNo(), true, snapshot.settlement().settled(),
                current.plan().dueDate(), current.asOfDate(),
                current.dueAmount(), current.paidAmount(), current.outstandingAmount(),
                current.overdue(), current.overdueDays());
    }

    /**
     * 为 Agent Tool 提供整笔贷款维度的结清事实。
     * 它不描述某一期是否还清，而是返回所有期次合计后的结清状态和未还总额。
     *
     * @param loanNo 对外使用的业务贷款编号
     * @return 整笔贷款的结清状态事实
     * @throws LoanNotFoundException 找不到该贷款编号对应的合同记录时抛出
     */
    public SettlementStatusFacts getSettlementStatusFacts(String loanNo) {
        DiagnosisSnapshot snapshot = diagnose(loanNo);
        return new SettlementStatusFacts(
                snapshot.loan().loanNo(),
                snapshot.settlement().settled(),
                snapshot.settlement().totalOutstanding());
    }

    /**
     * 内部统一诊断入口：一次加载贷款数据，完成当前期次诊断和整笔贷款结清诊断，
     * 再组合为同一份 {@link DiagnosisSnapshot}。四个公开查询方法因此可以复用相同流程，
     * 只按各自调用场景选择和组装字段。
     *
     * @param loanNo 对外使用的业务贷款编号
     * @return 本次查询共享的内部诊断快照
     */
    private DiagnosisSnapshot diagnose(String loanNo) {
        LoanData data = loadLoanData(loanNo);
        LoanDiagnosisService.RepaymentDiagnosis current = loanDiagnosisService.currentRepayment(data.loan(),
                data.plans(), data.payments());
        LoanDiagnosisService.SettlementDiagnosis settlement = loanDiagnosisService.settlement(data.loan(), data.plans(),
                data.payments());
        LocalDate asOfDate = current == null ? LocalDate.now(clock) : current.asOfDate();
        return new DiagnosisSnapshot(data.loan(), current, settlement, asOfDate);
    }

    /**
     * 根据业务贷款编号加载一次诊断所需的全部数据。
     * 先查询贷款合同，再使用合同的内部主键查询归属于该贷款的计划和还款记录；找不到合同时抛出
     * {@link LoanNotFoundException}，不会把“不存在”误解释为余额为 0 或已经结清。
     *
     * @param loanNo 对外使用的业务贷款编号
     * @return 已转换为领域对象的合同、计划和还款记录集合
     * @throws LoanNotFoundException 找不到该贷款编号对应的合同记录时抛出
     */
    private LoanData loadLoanData(String loanNo) {
        LoanContractEntity loanEntity = loanContractMapper.selectOne(
                new LambdaQueryWrapper<LoanContractEntity>().eq(LoanContractEntity::getLoanNo, loanNo));
        if (loanEntity == null) {
            throw new LoanNotFoundException(loanNo);
        }

        List<RepaymentPlanEntity> planEntities = repaymentPlanMapper.selectList(
                new LambdaQueryWrapper<RepaymentPlanEntity>()
                        .eq(RepaymentPlanEntity::getLoanId, loanEntity.getId()));
        List<PaymentRecordEntity> paymentEntities = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getLoanId, loanEntity.getId()));

        return new LoanData(
                toDomain(loanEntity),
                planEntities.stream().map(this::toDomain).toList(),
                paymentEntities.stream().map(this::toDomain).toList());
    }

    // Entity 对应数据库持久化结构；转换后的 Domain 对象用于确定性的业务诊断。
    private LoanContract toDomain(LoanContractEntity entity) {
        return new LoanContract(
                entity.getId(), entity.getLoanNo(), entity.getBorrowerName(), entity.getPrincipal(),
                entity.getStartDate(), entity.getEndDate());
    }

    private RepaymentPlan toDomain(RepaymentPlanEntity entity) {
        return new RepaymentPlan(
                entity.getId(), entity.getLoanId(), entity.getInstallmentNo(), entity.getDueDate(),
                entity.getPrincipalDue(), entity.getInterestDue());
    }

    private PaymentRecord toDomain(PaymentRecordEntity entity) {
        return new PaymentRecord(
                entity.getId(), entity.getLoanId(), entity.getRepaymentPlanId(),
                entity.getPaymentDate(), entity.getAmount());
    }

    /**
     * 一次数据库加载后形成的 Service 内部领域数据集合，不是返回给 Controller 或 Agent Tool 的外部 DTO。
     *
     * @param loan     本次查询的贷款合同
     * @param plans    归属于该贷款的约定还款计划
     * @param payments 归属于该贷款的实际还款记录
     */
    private record LoanData(
            LoanContract loan,
            List<RepaymentPlan> plans,
            List<PaymentRecord> payments) {
    }

    /**
     * 一次统一诊断产生的 Service 内部结果，供不同公开查询方法组装各自的 DTO。
     *
     * @param loan       本次诊断对应的贷款合同
     * @param current    当前待处理期次诊断；全部期次已还清时为 {@code null}
     * @param settlement 整笔贷款的结清诊断
     * @param asOfDate   本次诊断采用的业务日期
     */
    private record DiagnosisSnapshot(
            LoanContract loan,
            LoanDiagnosisService.RepaymentDiagnosis current,
            LoanDiagnosisService.SettlementDiagnosis settlement,
            LocalDate asOfDate) {
    }
}
