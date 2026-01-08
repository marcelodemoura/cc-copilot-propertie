package br.com.mv.cccopilotpropertie.copilot.history.api;

import br.com.mv.cccopilotpropertie.copilot.history.domain.CopilotInteractionEntity;
import br.com.mv.cccopilotpropertie.copilot.history.infra.CopilotInteractionRepository;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping("/history")
public class CopilotHistoryController {

    private final CopilotInteractionRepository repo;

    public CopilotHistoryController(CopilotInteractionRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public Page<CopilotHistoryResponse> list(
            @RequestParam String tenantId,
            @RequestParam(required = false) String knowledgeBase,
            Pageable pageable
    ) {

        Page<CopilotInteractionEntity> page =
                knowledgeBase == null
                        ? repo.findByTenantId(tenantId, pageable)
                        : repo.findByTenantIdAndKnowledgeBase(
                        tenantId, knowledgeBase, pageable
                );

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
