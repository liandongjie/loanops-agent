package com.loanops.controller;

import com.loanops.audit.AgentAuditService;
import com.loanops.dto.AgentAuditResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/audits")
public class AgentAuditController {

    private final AgentAuditService auditService;

    public AgentAuditController(AgentAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{requestId}")
    public AgentAuditResponse get(@PathVariable String requestId) {
        return auditService.get(requestId);
    }
}