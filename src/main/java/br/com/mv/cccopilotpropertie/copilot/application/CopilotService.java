package br.com.mv.cccopilotpropertie.copilot.application;

import br.com.mv.cccopilotpropertie.copilot.agent.AgentLoop;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.history.application.CopilotHistoryService;
import br.com.mv.cccopilotpropertie.copilot.code.CodeModificationService;
import br.com.mv.cccopilotpropertie.copilot.code.FileModificationResult;
import br.com.mv.cccopilotpropertie.project.infra.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

@Service
public class CopilotService {

    private final AgentLoop agentLoop;
    private final CopilotHistoryService historyService;
    private final CodeModificationService codeModificationService;
    private final ProjectRepository projectRepository;
    private final String allowedBasePath;

    public CopilotService(AgentLoop agentLoop, CopilotHistoryService historyService) {
        this(agentLoop, historyService, new CodeModificationService(true, "."), null, ".");
    }

    @Autowired
    public CopilotService(AgentLoop agentLoop,
                          CopilotHistoryService historyService,
                          @Autowired(required = false) CodeModificationService codeModificationService,
                          @Autowired(required = false) ProjectRepository projectRepository,
                          @Value("${indexer.base-path:.}") String allowedBasePath) {
        this.agentLoop = agentLoop;
        this.historyService = historyService;
        this.codeModificationService = codeModificationService != null
                ? codeModificationService
                : new CodeModificationService(true, allowedBasePath != null ? allowedBasePath : ".");
        this.projectRepository = projectRepository;
        this.allowedBasePath = allowedBasePath != null ? allowedBasePath : ".";
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

    public FileModificationResult modifyFile(String tenantId, String kb, String path, String targetContent, String replacementContent, String reason) {
        Path projectRoot = resolveProjectPath(kb);
        return codeModificationService.modifyFile(projectRoot, tenantId, kb, path, targetContent, replacementContent, reason);
    }

    private Path resolveProjectPath(String kb) {
        if (projectRepository != null && kb != null && !kb.isBlank()) {
            try {
                UUID id = UUID.fromString(kb);
                var project = projectRepository.findById(id);
                if (project.isPresent()) {
                    return Path.of(project.get().getRootPath()).toAbsolutePath().normalize();
                }
            } catch (Exception ignored) {
            }
        }
        return Path.of(allowedBasePath).toAbsolutePath().normalize();
    }
}
