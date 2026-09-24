package com.loanops.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一笔贷款合同，是还款计划和实际还款记录共同归属的贷款主体。
 * {@code LoanStatusService} 从数据库实体转换出本对象，再交给 {@code LoanDiagnosisService}
 * 与该贷款的计划、还款记录一起计算当前还款和结清事实。
 * 本对象保存合同本身的数据，不保存逾期、未还金额或结清状态等动态计算结果。
 *
 * @param id           数据库内部的贷款唯一标识，例如 {@code 1}，供还款计划和实际还款记录关联
 * @param loanNo       面向查询使用的业务贷款编号，例如 {@code LN-10002}，不同于内部 {@code id}
 * @param borrowerName 合同记录的借款人姓名，例如 {@code 张三}
 * @param principal    整笔贷款最初约定的本金，不是某一期应还本金或当前未还金额，例如 {@code 8000}
 * @param startDate    贷款合同开始日期，不是某一期还款开始日期，例如 {@code 2026-01-01}
 * @param endDate      贷款合同结束日期，不是某一期还款计划的到期日，例如 {@code 2027-01-01}
 */
public record LoanContract(
        Long id,
        String loanNo,
        String borrowerName,
        BigDecimal principal,
        LocalDate startDate,
        LocalDate endDate) {
}
