package com.loanops.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("payment_record")
public class PaymentRecordEntity {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long loanId;
    private Long repaymentPlanId;
    private LocalDate paymentDate;
    private BigDecimal amount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLoanId() { return loanId; }
    public void setLoanId(Long loanId) { this.loanId = loanId; }
    public Long getRepaymentPlanId() { return repaymentPlanId; }
    public void setRepaymentPlanId(Long repaymentPlanId) { this.repaymentPlanId = repaymentPlanId; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}