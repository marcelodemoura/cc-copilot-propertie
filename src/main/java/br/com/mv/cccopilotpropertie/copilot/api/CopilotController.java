package br.com.mv.cccopilotpropertie.copilot.api;

import br.com.mv.cccopilotpropertie.copilot.application.CopilotService;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/copilot")
@Tag(name = "Copilot (legado)", description = "Endpoint legado para perguntas sem vínculo de projeto")
public class CopilotController {

    private final CopilotService copilot;

    public CopilotController(CopilotService copilot) {
        this.copilot = copilot;
    }

    @PostMapping("/ask")
    @Operation(summary = "Pergunta ao agente (legado)", description = "Informe `tenantId`, `knowledgeBase`, `question` e opcionalmente `sessionId`. Prefira o endpoint `/projects/{id}/ask`.")
    public CopilotAnswer ask(@Valid @RequestBody AskRequest req) {
        return copilot.ask(req.tenantId(), req.knowledgeBase(), req.question(), req.sessionId());
    }

    @PostMapping(value = "/ask/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Pergunta ao agente com streaming (legado)")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter askStream(@Valid @RequestBody AskRequest req) {
        return copilot.askStream(req.tenantId(), req.knowledgeBase(), req.question(), req.sessionId());
    }
}
