package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.domain.ConversationState;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntentHandler;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagServiceV2 {

    private final List<CopilotIntentHandler> handlers;

    public RagServiceV2(List<CopilotIntentHandler> handlers) {
        this.handlers = handlers;
    }

    public CopilotAnswer ask(
            String tenantId,
            String knowledgeBase,
            String question
    ) {
        ConversationState state = new ConversationState();

        for (CopilotIntentHandler handler : handlers) {
            if (handler.supports(question, state)) {
                return handler.handle(
                        tenantId,
                        knowledgeBase,
                        question,
                        state
                );
            }
        }

        return new CopilotAnswer(
                "Ainda não consegui entender essa pergunta 🤔",
                List.of(),
                0.3,
                null,
                null
        );
    }
}
