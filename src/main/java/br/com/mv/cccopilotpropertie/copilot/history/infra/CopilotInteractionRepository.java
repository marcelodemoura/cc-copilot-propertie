package br.com.mv.cccopilotpropertie.copilot.history.infra;

import br.com.mv.cccopilotpropertie.copilot.history.domain.CopilotInteractionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CopilotInteractionRepository
        extends JpaRepository<CopilotInteractionEntity, UUID> {

    Page<CopilotInteractionEntity> findByTenantId(
            String tenantId,
            Pageable pageable
    );

    Page<CopilotInteractionEntity> findByTenantIdAndKnowledgeBase(
            String tenantId,
            String knowledgeBase,
            Pageable pageable
    );

    @Query("SELECT h FROM CopilotInteractionEntity h WHERE h.tenantId = :tenantId AND h.knowledgeBase = :knowledgeBase ORDER BY h.createdAt DESC")
    Page<CopilotInteractionEntity> findRecentBySession(
            String tenantId,
            String knowledgeBase,
            Pageable pageable
    );
}
