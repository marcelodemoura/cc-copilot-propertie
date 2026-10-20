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

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CopilotService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CopilotService.class);

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

    public SseEmitter askStream(
            String tenantId, String kb, String question, String sessionId) {
        var emitter = new SseEmitter(180_000L);
        var completed = new AtomicBoolean(false);

        emitter.onCompletion(() -> completed.set(true));
        emitter.onTimeout(() -> {
            log.warn("Timeout de conexão SSE [tenant={}, kb={}, session={}]", tenantId, kb, sessionId);
            completed.set(true);
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
        emitter.onError(ex -> {
            log.debug("Erro/Desconexão SSE [tenant={}, kb={}, session={}]: {}", tenantId, kb, sessionId, ex.getMessage());
            completed.set(true);
        });

        CompletableFuture.runAsync(() -> {
            try {
                sendSseEvent(emitter, completed, "init", Map.of("message", "Conexão com agente iniciada."));

                CopilotAnswer answer = agentLoop.run(tenantId, kb, question, sessionId, (type, data) -> {
                    if (completed.get()) {
                        return;
                    }
                    sendSseEvent(emitter, completed, type, data);
                });

                historyService.save(tenantId, kb, sessionId, question, answer);

                if (!completed.get()) {
                    sendSseEvent(emitter, completed, "complete", answer);
                    completeQuietly(emitter, completed);
                }
            } catch (Exception e) {
                if (!completed.get()) {
                    log.error("Erro durante o processamento do chat/stream [tenant={}, kb={}, session={}]: {}", tenantId, kb, sessionId, e.getMessage(), e);
                    try {
                        String msg = e.getMessage() != null ? e.getMessage() : "Erro inesperado";
                        if (e instanceof org.springframework.web.client.HttpStatusCodeException se) {
                            msg = "OpenAI " + se.getStatusCode() + ": " + se.getResponseBodyAsString();
                        }
                        sendSseEvent(emitter, completed, "error", Map.of("error", msg));
                    } catch (Exception ignored) {
                    }
                    completeQuietly(emitter, completed);
                } else {
                    log.debug("Fluxo SSE interrompido por desconexão/timeout [tenant={}, kb={}, session={}]: {}", tenantId, kb, sessionId, e.getMessage());
                }
            }
        });

        return emitter;
    }

    private void sendSseEvent(SseEmitter emitter, AtomicBoolean completed, String name, Object data) {
        if (completed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception e) {
            completed.set(true);
            log.debug("Falha ao enviar evento SSE '{}' (cliente desconectou?): {}", name, e.getMessage());
        }
    }

    private void completeQuietly(SseEmitter emitter, AtomicBoolean completed) {
        if (completed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
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
