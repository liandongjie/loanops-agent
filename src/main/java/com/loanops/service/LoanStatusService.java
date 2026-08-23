package com.loanops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loanops.domain.LoanContract;
import com.loanops.domain.PaymentRecord;
import com.loanops.domain.RepaymentPlan;
import com.loanops.dto.LoanStatusResponse;
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

        LoanContract loan = toDomain(loanEntity);
        List<RepaymentPlan> plans = planEntities.stream().map(this::toDomain).toList();
        List<PaymentRecord> payments = paymentEntities.stream().map(this::toDomain).toList();

        LoanDiagnosisService.RepaymentDiagnosis current =
                loanDiagnosisService.currentRepayment(loan, plans, payments);
        LoanDiagnosisService.SettlementDiagnosis settlement =
                loanDiagnosisService.settlement(loan, plans, payments);

        if (current == null) {
            return new LoanStatusResponse(
                    loan.loanNo(), null, null, LocalDate.now(clock), null, null, null,
                    false, 0, settlement.settled(), settlement.totalOutstanding());
        }

        return new LoanStatusResponse(
                loan.loanNo(),
                current.plan().installmentNo(),
                current.plan().dueDate(),
                current.asOfDate(),
                current.dueAmount(),
                current.paidAmount(),
                current.outstandingAmount(),
                current.overdue(),
                current.overdueDays(),
                settlement.settled(),
                settlement.totalOutstanding());
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
}
