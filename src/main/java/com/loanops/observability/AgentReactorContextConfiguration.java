package com.loanops.observability;

import com.loanops.audit.AgentRequestAuditContext;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import jakarta.annotation.PostConstruct;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Hooks;

@Configuration(proxyBeanMethods = false)
@Profile("ai")
public class AgentReactorContextConfiguration {

    public static final String AUDIT_CONTEXT_KEY = "loanops.agent.auditContext";
    public static final String REQUEST_ID_CONTEXT_KEY = "loanops.agent.requestId";

    @PostConstruct
    void configure() {
        ContextRegistry registry = ContextRegistry.getInstance();
        registry.registerThreadLocalAccessor(new AuditContextAccessor());
        registry.registerThreadLocalAccessor(new RequestIdMdcAccessor());
        Hooks.enableAutomaticContextPropagation();
    }

    private static final class AuditContextAccessor
            implements ThreadLocalAccessor<AgentRequestAuditContext.State> {

        @Override
        public Object key() {
            return AUDIT_CONTEXT_KEY;
        }

        @Override
        public AgentRequestAuditContext.State getValue() {
            return AgentRequestAuditContext.current().orElse(null);
        }

        @Override
        public void setValue(AgentRequestAuditContext.State value) {
            AgentRequestAuditContext.restore(value);
        }

        @Override
        public void setValue() {
            AgentRequestAuditContext.clear();
        }
    }

    private static final class RequestIdMdcAccessor implements ThreadLocalAccessor<String> {

        @Override
        public Object key() {
            return REQUEST_ID_CONTEXT_KEY;
        }

        @Override
        public String getValue() {
            return MDC.get("requestId");
        }

        @Override
        public void setValue(String value) {
            MDC.put("requestId", value);
        }

        @Override
        public void setValue() {
            MDC.remove("requestId");
        }
    }
}
