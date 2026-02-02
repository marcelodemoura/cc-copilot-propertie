package br.com.mv.cccopilotpropertie.copilot.intent.handlers;

import br.com.mv.cccopilotpropertie.copilot.domain.ConversationState;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntent;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntentHandler;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Order(2)
public class ExternalImpactHandler implements CopilotIntentHandler {

    private final SearchRepository searchRepository;

    public ExternalImpactHandler(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @Override
    public CopilotIntent intent() {
        return CopilotIntent.EXTERNAL_IMPACT;
    }

    @Override
    public boolean supports(String question, ConversationState state) {
        String q = question.toLowerCase();
        return q.contains("outro sistema")
                || q.contains("externo")
                || q.contains("contrato externo");
    }

    @Override
    public CopilotAnswer handle(
            String tenantId,
            String knowledgeBase,
            String question,
            ConversationState state
    ) {

        if (state == null || state.getLastChange() == null) {
            return simple(
                    "Ainda não sei qual mudança você está analisando.\n\n👉 Exemplo: \"posso remover o campo cnpj?\"",
                    0.6
            );
        }

        String dto = state.getDto();
        if (dto == null) {
            return simple("Não consegui identificar o DTO afetado.", 0.6);
        }

        List<SearchResult> external =
                searchRepository.findUsagesInOtherKnowledgeBases(
                        tenantId,
                        knowledgeBase,
                        dto
                );

        if (external.isEmpty()) {
            return simple(
                    "O DTO `" + dto + "` **não possui uso em outros sistemas**.",
                    1.0
            );
        }

        return new CopilotAnswer(
                "⚠️ **Impacto externo detectado**.\n\n"
                        + "O DTO `" + dto + "` é utilizado por outros sistemas:\n\n"
                        + external.stream()
                        .map(r -> "- " + r.path())
                        .distinct()
                        .reduce("", String::concat),
                external.stream()
                        .map(r -> new CopilotAnswer.Source(r.path(), r.score()))
                        .toList(),
                1.0,
                null,
                null
        );
    }

    private CopilotAnswer simple(String msg, double c) {
        return new CopilotAnswer(msg, List.of(), c, null, null);
    }
}
