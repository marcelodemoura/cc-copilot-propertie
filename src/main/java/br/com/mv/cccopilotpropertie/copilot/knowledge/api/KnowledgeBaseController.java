package br.com.mv.cccopilotpropertie.copilot.knowledge.api;

import br.com.mv.cccopilotpropertie.copilot.knowledge.application.KnowledgeBaseService;
import br.com.mv.cccopilotpropertie.copilot.knowledge.domain.KnowledgeBaseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tenants/{tenantId}/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) {
        this.service = service;
    }

    @PostMapping
    public KnowledgeBaseEntity create(@PathVariable String tenantId,
                                      @RequestParam String name) {
        return service.create(tenantId, name);
    }
}
