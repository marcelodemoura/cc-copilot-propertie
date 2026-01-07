package br.com.mv.cccopilotpropertie.copilot.knowledge.application;

import br.com.mv.cccopilotpropertie.copilot.knowledge.domain.KnowledgeBaseEntity;
import br.com.mv.cccopilotpropertie.copilot.knowledge.infra.KnowledgeBaseRepository;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repo;

    public KnowledgeBaseService(KnowledgeBaseRepository repo) {
        this.repo = repo;
    }

    public KnowledgeBaseEntity create(String tenantId, String name) {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity(tenantId, name);
        return repo.save(kb);
    }

}
