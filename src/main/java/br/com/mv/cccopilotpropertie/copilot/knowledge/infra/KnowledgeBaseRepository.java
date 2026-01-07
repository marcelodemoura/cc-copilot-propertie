package br.com.mv.cccopilotpropertie.copilot.knowledge.infra;

import br.com.mv.cccopilotpropertie.copilot.knowledge.domain.KnowledgeBaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.UUID;

public interface KnowledgeBaseRepository
        extends JpaRepository<KnowledgeBaseEntity, UUID> {
}

