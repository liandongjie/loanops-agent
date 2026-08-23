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

    public SettlementStatusFacts getSettlementStatusFacts(String loanNo) {
        DiagnosisSnapshot snapshot = diagnose(loanNo);
        return new SettlementStatusFacts(
                snapshot.loan().loanNo(),
                snapshot.settlement().settled(),
                snapshot.settlement().totalOutstanding());
    }

    private DiagnosisSnapshot diagnose(String loanNo) {
        LoanData data = loadLoanData(loanNo);
        LoanDiagnosisService.RepaymentDiagnosis current =
                loanDiagnosisService.currentRepayment(data.loan(), data.plans(), data.payments());
        LoanDiagnosisService.SettlementDiagnosis settlement =
                loanDiagnosisService.settlement(data.loan(), data.plans(), data.payments());
        LocalDate asOfDate = current == null ? LocalDate.now(clock) : current.asOfDate();
        return new DiagnosisSnapshot(data.loan(), current, settlement, asOfDate);
    }

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

    private record LoanData(
            LoanContract loan,
            List<RepaymentPlan> plans,
            List<PaymentRecord> payments) {
    }

    private record DiagnosisSnapshot(
            LoanContract loan,
            LoanDiagnosisService.RepaymentDiagnosis current,
            LoanDiagnosisService.SettlementDiagnosis settlement,
            LocalDate asOfDate) {
    }
}
