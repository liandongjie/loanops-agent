package com.loanops.tool;

import com.loanops.audit.AgentToolAuditService;
import com.loanops.dto.CurrentRepaymentFacts;
import com.loanops.dto.OverdueDiagnosisFacts;
import com.loanops.dto.SettlementStatusFacts;
import com.loanops.service.LoanStatusService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class LoanOpsTools {

    private final LoanStatusService loanStatusService;
    private final AgentToolAuditService toolAuditService;

    public LoanOpsTools(LoanStatusService loanStatusService, AgentToolAuditService toolAuditService) {
        this.loanStatusService = loanStatusService;
        this.toolAuditService = toolAuditService;
    }

    @Tool(
            name = "getCurrentRepayment",
            description = "Read the deterministic current repayment facts for a loan. Use this for questions about the current installment, principal, interest, amount due, amount paid, remaining amount, due date, or whether the current installment is overdue. This includes follow-up questions after an overdue diagnosis about how much is currently owed for the installment.")
    public CurrentRepaymentFacts getCurrentRepayment(
            @ToolParam(description = "Loan number, for example LN-10001") String loanNo) {
        return toolAuditService.execute(
                "getCurrentRepayment", loanNo, () -> loanStatusService.getCurrentRepaymentFacts(loanNo));
    }

    @Tool(
            name = "getOverdueDiagnosis",
            description = "Read the deterministic overdue diagnosis for a loan, including due date, business date, due amount, paid amount, outstanding amount, overdue flag, and overdue days. Do not use this tool to modify loan data.")
    public OverdueDiagnosisFacts getOverdueDiagnosis(
            @ToolParam(description = "Loan number, for example LN-10002") String loanNo) {
        return toolAuditService.execute(
                "getOverdueDiagnosis", loanNo, () -> loanStatusService.getOverdueDiagnosisFacts(loanNo));
    }

    @Tool(
            name = "getSettlementStatus",
            description = "Read whether a whole loan is fully settled and its total outstanding balance. Use this only for whole-loan settlement or total outstanding questions, not for the current installment amount after an overdue diagnosis. This tool is read-only and never changes loan status.")
    public SettlementStatusFacts getSettlementStatus(
            @ToolParam(description = "Loan number, for example LN-10003") String loanNo) {
        return toolAuditService.execute(
                "getSettlementStatus", loanNo, () -> loanStatusService.getSettlementStatusFacts(loanNo));
    }
}
