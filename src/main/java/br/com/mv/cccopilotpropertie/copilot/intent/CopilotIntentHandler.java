package br.com.mv.cccopilotpropertie.copilot.intent;

import br.com.mv.cccopilotpropertie.copilot.domain.ConversationState;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;

public interface CopilotIntentHandler {

    boolean supports(String question, ConversationState state);

    CopilotIntent intent();

    CopilotAnswer handle(
            String tenantId,
            String knowledgeBase,
            String question,
            ConversationState state
    );
}


