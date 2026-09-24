package com.loanops.tool;

import com.loanops.audit.AgentToolAuditService;
import com.loanops.dto.CurrentRepaymentFacts;
import com.loanops.dto.OverdueDiagnosisFacts;
import com.loanops.dto.SettlementStatusFacts;
import com.loanops.service.LoanStatusService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 把只读的 Java 贷款业务能力暴露为大模型可调用的 Tool（工具方法）的适配层。
 * 大模型负责选择合适的 Tool；本类不实现或复制贷款计算规则，而是把查询交给
 * {@link LoanStatusService}，并通过 {@link AgentToolAuditService} 记录调用审计。
 * 因此模型最终获得的贷款事实来自业务 Service 和当前数据库，而不是由 Prompt 或 Tool 自行推算。
 */
@Component
public class LoanOpsTools {

    /** 提供当前还款、逾期和结清等确定性业务事实。 */
    private final LoanStatusService loanStatusService;

    /** 包装每次 Tool 调用并记录工具名、贷款编号、耗时和结果。 */
    private final AgentToolAuditService toolAuditService;

    /**
     * 创建 Tool 适配层。
     *
     * @param loanStatusService 贷款状态查询 Service，负责提供最终业务事实
     * @param toolAuditService Tool 调用审计 Service
     */
    public LoanOpsTools(LoanStatusService loanStatusService, AgentToolAuditService toolAuditService) {
        this.loanStatusService = loanStatusService;
        this.toolAuditService = toolAuditService;
    }

    /**
     * 查询贷款当前待处理期次的本金、利息、应还、已还、未还、到期日和逾期状态。
     *
     * @param loanNo 要查询的贷款编号，例如 {@code LN-10001}
     * @return {@link LoanStatusService} 计算并组装的当前期次事实
     */
    @Tool(
            name = "getCurrentRepayment",
            description = "Read the deterministic current repayment facts for a loan. Use this for questions about the current installment, principal, interest, amount due, amount paid, remaining amount, due date, or whether the current installment is overdue. This includes follow-up questions after an overdue diagnosis about how much is currently owed for the installment.")
    public CurrentRepaymentFacts getCurrentRepayment(
            @ToolParam(description = "Loan number, for example LN-10001") String loanNo) {
        return toolAuditService.execute(
                "getCurrentRepayment", loanNo, () -> loanStatusService.getCurrentRepaymentFacts(loanNo));
    }

    /**
     * 查询贷款当前待处理期次为什么逾期，包括到期日、业务日期、金额和逾期天数。
     *
     * @param loanNo 要诊断的贷款编号，例如 {@code LN-10002}
     * @return {@link LoanStatusService} 提供的确定性逾期诊断事实
     */
    @Tool(
            name = "getOverdueDiagnosis",
            description = "Read the deterministic overdue diagnosis for a loan, including due date, business date, due amount, paid amount, outstanding amount, overdue flag, and overdue days. Do not use this tool to modify loan data.")
    public OverdueDiagnosisFacts getOverdueDiagnosis(
            @ToolParam(description = "Loan number, for example LN-10002") String loanNo) {
        return toolAuditService.execute(
                "getOverdueDiagnosis", loanNo, () -> loanStatusService.getOverdueDiagnosisFacts(loanNo));
    }

    /**
     * 查询整笔贷款是否已经结清以及全部期次的未还总额，而不是只查询当前一期。
     *
     * @param loanNo 要查询的贷款编号，例如 {@code LN-10003}
     * @return {@link LoanStatusService} 提供的整笔贷款结清事实
     */
    @Tool(
            name = "getSettlementStatus",
            description = "Read whether a whole loan is fully settled and its total outstanding balance. Use this only for whole-loan settlement or total outstanding questions, not for the current installment amount after an overdue diagnosis. This tool is read-only and never changes loan status.")
    public SettlementStatusFacts getSettlementStatus(
            @ToolParam(description = "Loan number, for example LN-10003") String loanNo) {
        return toolAuditService.execute(
                "getSettlementStatus", loanNo, () -> loanStatusService.getSettlementStatusFacts(loanNo));
    }
}
