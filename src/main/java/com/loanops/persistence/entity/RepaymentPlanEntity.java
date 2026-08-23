package com.loanops.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("repayment_plan")
public class RepaymentPlanEntity {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long loanId;
    private Integer installmentNo;
    private LocalDate dueDate;
    private BigDecimal principalDue;
    private BigDecimal interestDue;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLoanId() { return loanId; }
    public void setLoanId(Long loanId) { this.loanId = loanId; }
    public Integer getInstallmentNo() { return installmentNo; }
    public void setInstallmentNo(Integer installmentNo) { this.installmentNo = installmentNo; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getPrincipalDue() { return principalDue; }
    public void setPrincipalDue(BigDecimal principalDue) { this.principalDue = principalDue; }
    public BigDecimal getInterestDue() { return interestDue; }
    public void setInterestDue(BigDecimal interestDue) { this.interestDue = interestDue; }
}