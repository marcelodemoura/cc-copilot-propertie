package br.com.mv.cccopilotpropertie.copilot.intent;

import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeSet;
import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeTarget;
import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeType;
import br.com.mv.cccopilotpropertie.copilot.domain.ConversationState;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class RemoveFieldHandler implements CopilotIntentHandler {

    private final SearchService search;

    public RemoveFieldHandler(SearchService search) {
        this.search = search;
    }

    @Override
    public CopilotIntent intent() {
        return CopilotIntent.FIELD_REMOVAL;
    }

    @Override
    public boolean supports(String question, ConversationState state) {
        String q = question.toLowerCase();
        return q.contains("remover") && q.contains("campo");
    }

    @Override
    public CopilotAnswer handle(
            String tenantId,
            String knowledgeBase,
            String question,
            ConversationState state
    ) {

        String field = extractField(question);

        List<SearchResult> docs =
                search.search(tenantId, knowledgeBase, field, 20);

        String dto = inferDto(docs);

        ChangeSet change = new ChangeSet(
                ChangeTarget.FIELD,
                ChangeType.REMOVE,
                field,
                dto,
                null,
                null
        );

        // ✅ ATUALIZA O ESTADO (SEM FLUENT, SEM state.state)
        state.setField(field);
        state.setLastChange(change);
        state.setDto(dto);

        return new CopilotAnswer(
                "Campo `" + field + "` possui usos e pode gerar impacto.",
                docs.stream()
                        .map(d -> new CopilotAnswer.Source(d.path(), d.score()))
                        .toList(),
                1.0,
                null,
                null
        );
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private String extractField(String q) {
        return q.replaceAll("(?i).*campo\\s+", "").trim();
    }

    private String inferDto(List<SearchResult> docs) {
        return docs.stream()
                .map(SearchResult::path)
                .filter(p -> p.endsWith("DTO.java"))
                .map(p -> p.substring(p.lastIndexOf("/") + 1)
                        .replace(".java", ""))
                .findFirst()
                .orElse(null);
    }
}
