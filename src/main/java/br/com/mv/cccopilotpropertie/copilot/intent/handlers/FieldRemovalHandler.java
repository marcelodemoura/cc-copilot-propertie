package br.com.mv.cccopilotpropertie.copilot.intent.handlers;

import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeSet;
import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeTarget;
import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeType;
import br.com.mv.cccopilotpropertie.copilot.domain.ConversationState;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntent;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntentHandler;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
@Service
public class FieldRemovalHandler implements CopilotIntentHandler {

    private static final Pattern FIELD_PATTERN =
            Pattern.compile("campo\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(String question, ConversationState state) {
        String q = question.toLowerCase();
        return q.contains("remover") && q.contains("campo");
    }

    @Override
    public CopilotIntent intent() {
        return CopilotIntent.FIELD_REMOVAL;
    }

    @Override
    public CopilotAnswer handle(
            String tenantId,
            String knowledgeBase,
            String question,
            ConversationState state
    ) {

        String field = extractField(question);
        if (field == null) {
            return new CopilotAnswer(
                    "Não consegui identificar o campo.",
                    List.of(),
                    0.4,
                    null,
                    null
            );
        }

        // 👉 cria ChangeSet (sem DTO ainda — correto para V2)
        ChangeSet change = new ChangeSet(
                ChangeTarget.FIELD,
                ChangeType.REMOVE,
                field,
                null,
                null,
                null
        );

        // 👉 atualiza estado da conversa
        state.setField(field);
        state.setLastChange(change);

        return new CopilotAnswer(
                "Campo `" + field + "` registrado para análise de impacto.",
                List.of(),
                1.0,
                null,
                null
        );
    }

    private String extractField(String q) {
        Matcher m = FIELD_PATTERN.matcher(q);
        return m.find() ? m.group(1) : null;
    }
}
