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

        // 🧠 Estado da conversa (por request)
        ConversationState state = new ConversationState();

        // 🔥 PASSO 5 — enriquecer contexto
        parser.enrich(question, state);

        // 🔥 PASSO 7 — normalizar intenção
        String normalizedIntent = normalizer.normalize(question);

        // 🔁 Resolver handler correto
        for (CopilotIntentHandler handler : handlers) {

            if (!handler.intent().name().equals(normalizedIntent)) {
                continue;
            }

            if (handler.supports(question, state)) {
                return handler.handle(
                        tenantId,
                        knowledgeBase,
                        question,
                        state
                );
            }
        }

        // 🔚 Fallback explícito
        return fallback();
    }

    private CopilotAnswer fallback() {
        return new CopilotAnswer(
                """
                Ainda não consegui entender bem a pergunta 🤔

                👉 Exemplos que eu entendo:
                - "posso remover o campo cnpj?"
                - "isso é breaking?"
                - "isso quebra alguma API?"
                - "impacta outro sistema?"
                """,
                List.of(),
                0.4,
                null,
                null
        );
    }
}
