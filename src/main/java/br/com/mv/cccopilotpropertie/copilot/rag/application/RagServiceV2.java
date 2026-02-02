package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.domain.ConversationState;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntentHandler;
import br.com.mv.cccopilotpropertie.copilot.intent.QuestionNormalizer;
import br.com.mv.cccopilotpropertie.copilot.intent.parser.QuestionParser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagServiceV2 {

    private final List<CopilotIntentHandler> handlers;
    private final QuestionParser parser;
    private final QuestionNormalizer normalizer;

    public RagServiceV2(
            List<CopilotIntentHandler> handlers,
            QuestionParser parser,
            QuestionNormalizer normalizer
    ) {
        this.handlers = handlers;
        this.parser = parser;
        this.normalizer = normalizer;
    }

    public CopilotAnswer ask(
            String tenantId,
            String knowledgeBase,
            String question
    ) {

        ConversationState state = new ConversationState();

        // V2 — enriquecimento mínimo de contexto
        String normalized = normalizer.normalize(question);
        parser.enrich(normalized, state);

        for (CopilotIntentHandler handler : handlers) {
            if (handler.supports(normalized, state)) {
                return handler.handle(
                        tenantId,
                        knowledgeBase,
                        normalized,
                        state
                );
            }
        }

        return fallback();
    }

    private CopilotAnswer fallback() {
        return new CopilotAnswer(
                """
                Ainda não consegui entender bem a pergunta.

                👉 Exemplos:
                - "posso remover o campo cnpj?"
                - "isso é breaking?"
                - "isso quebra alguma api?"
                """,
                List.of(),
                0.4,
                null,
                null
        );
    }
}
