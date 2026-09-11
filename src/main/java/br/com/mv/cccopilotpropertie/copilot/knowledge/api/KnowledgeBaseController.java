package br.com.mv.cccopilotpropertie.copilot.knowledge.api;

import br.com.mv.cccopilotpropertie.copilot.knowledge.application.KnowledgeBaseService;
import br.com.mv.cccopilotpropertie.copilot.knowledge.domain.KnowledgeBaseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenants/{tenantId}/knowledge-bases")
@Tag(name = "Knowledge Bases", description = "Gerenciamento de bases de conhecimento por tenant")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista bases de conhecimento do tenant")
    public java.util.List<KnowledgeBaseEntity> list(@PathVariable String tenantId) {
        return service.list(tenantId);
    }

    @PostMapping
    @Operation(summary = "Cria uma base de conhecimento")
    public KnowledgeBaseEntity create(@PathVariable String tenantId,
                                      @RequestParam String name) {
        return service.create(tenantId, name);
    }
}
