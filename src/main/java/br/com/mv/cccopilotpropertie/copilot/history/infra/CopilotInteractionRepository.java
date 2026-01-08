package br.com.mv.cccopilotpropertie.copilot.history.infra;

import br.com.mv.cccopilotpropertie.copilot.history.domain.CopilotInteractionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CopilotInteractionRepository  extends JpaRepository<CopilotInteractionEntity, UUID> {
}
