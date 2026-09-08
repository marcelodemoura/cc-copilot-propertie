package br.com.mv.cccopilotpropertie.copilot.application;

import br.com.mv.cccopilotpropertie.copilot.agent.AgentLoop;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.history.application.CopilotHistoryService;
import org.springframework.stereotype.Service;

@Service
public class CopilotService {

    private final AgentLoop agentLoop;
    private final CopilotHistoryService historyService;

    public CopilotService(AgentLoop agentLoop, CopilotHistoryService historyService) {
        this.agentLoop = agentLoop;
        this.historyService = historyService;
    }

    public CopilotAnswer ask(String tenantId, String kb, String question, String sessionId) {
        CopilotAnswer answer = agentLoop.run(tenantId, kb, question, sessionId);
        historyService.save(tenantId, kb, sessionId, question, answer);
        return answer;
    }

    public CopilotAnswer ask(String tenantId, String kb, String question) {
        return ask(tenantId, kb, question, null);
    }

    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter askStream(
            String tenantId, String kb, String question, String sessionId) {
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(180_000L);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("init").data(java.util.Map.of("message", "Conexão com agente iniciada.")));

                CopilotAnswer answer = agentLoop.run(tenantId, kb, question, sessionId, (type, data) -> {
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                                .name(type).data(data));
                    } catch (Exception ignored) {
                    }
                });

                historyService.save(tenantId, kb, sessionId, question, answer);
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("complete").data(answer));
                emitter.complete();
            } catch (Exception e) {
                try {
                    String msg = e.getMessage() != null ? e.getMessage() : "Erro inesperado";
                    if (e instanceof org.springframework.web.client.HttpStatusCodeException se) {
                        msg = "OpenAI " + se.getStatusCode() + ": " + se.getResponseBodyAsString();
                    }
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("error").data(java.util.Map.of("error", msg)));
                } catch (Exception ignored) {
                }
                emitter.complete();
            }
        });

        return emitter;
    }
}
