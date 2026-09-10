package com.loanops.agent;

public record AgentStreamEvent(String event, Object data) {

    public static AgentStreamEvent start(String requestId, String conversationId, String deliveryMode) {
        return new AgentStreamEvent("start", new StartData(requestId, conversationId, deliveryMode));
    }

    public static AgentStreamEvent delta(String text) {
        return new AgentStreamEvent("delta", new DeltaData(text));
    }

    public static AgentStreamEvent done(String requestId, String conversationId) {
        return new AgentStreamEvent("done", new DoneData(requestId, conversationId, true));
    }

    public static AgentStreamEvent error(
            String requestId, String conversationId, String code, String message) {
        return new AgentStreamEvent("error", new ErrorData(requestId, conversationId, code, message, false));
    }

    public record StartData(String requestId, String conversationId, String deliveryMode) {}

    public record DeltaData(String text) {}

    public record DoneData(String requestId, String conversationId, boolean committed) {}

    public record ErrorData(
            String requestId, String conversationId, String code, String message, boolean committed) {}
}
