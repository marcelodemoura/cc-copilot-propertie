package br.com.mv.cccopilotpropertie.copilot.history.api;

import br.com.mv.cccopilotpropertie.copilot.history.application.CopilotHistoryService;
import br.com.mv.cccopilotpropertie.copilot.history.domain.CopilotInteractionEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/history")
@Tag(name = "Histórico", description = "Histórico paginado de interações com o agente")
public class CopilotHistoryController {

    private final CopilotHistoryService service;

    public CopilotHistoryController(CopilotHistoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista histórico de interações")
    public Page<CopilotHistoryResponse> list(
            @RequestParam String tenantId,
            @RequestParam(required = false) String knowledgeBase,
            Pageable pageable
    ) {
        Page<CopilotInteractionEntity> page = service.list(tenantId, knowledgeBase, pageable);

        return page.map(e -> new CopilotHistoryResponse(
                        e.getTenantId(),
                        e.getKnowledgeBase(),
                        e.getQuestion(),
                        e.getAnswer(),
                        e.getConfidence(),
                        e.getCreatedAt()

                )
        );
    }
}
