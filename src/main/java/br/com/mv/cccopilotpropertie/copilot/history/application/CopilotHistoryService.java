package br.com.mv.cccopilotpropertie.copilot.history.application;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.history.domain.CopilotInteractionEntity;
import br.com.mv.cccopilotpropertie.copilot.history.infra.CopilotInteractionRepository;
import org.springframework.stereotype.Service;

@Service
public class CopilotHistoryService {

    private final CopilotInteractionRepository repo;

    public CopilotHistoryService(CopilotInteractionRepository repo) {
        this.repo = repo;
    }

    public void save(
            String tenant,
            String kb,
            String question,
            CopilotAnswer answer
    ) {
        repo.save(new CopilotInteractionEntity(
                tenant,
                kb,
                question,
                answer.answer(),
                answer.confidence()
        ));
    }
}
