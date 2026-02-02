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
@Order(1)
public class HttpContractImpactHandler implements CopilotIntentHandler {

    private final SearchRepository searchRepository;

    public HttpContractImpactHandler(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @Override
    public CopilotIntent intent() {
        return CopilotIntent.HTTP_CONTRACT_IMPACT;
    }

    @Override
    public boolean supports(String question, ConversationState state) {
        if (state == null || state.getLastChange() == null) return false;

        String q = question.toLowerCase();
        return q.contains("api")
                || q.contains("endpoint")
                || q.contains("http")
                || q.contains("contrato");
    }

    @Override
    public CopilotAnswer handle(
            String tenantId,
            String knowledgeBase,
            String question,
            ConversationState state
    ) {

        String dto = state.getDto();
        if (dto == null) {
            return simple("Não consegui identificar o DTO afetado.", 0.6);
        }

        List<SearchResult> endpoints =
                searchRepository.findEndpointsUsingDto(
                        tenantId,
                        knowledgeBase,
                        dto
                );

        if (endpoints.isEmpty()) {
            return simple(
                    "A alteração **não quebra contrato HTTP**. Nenhum endpoint usa o DTO `" + dto + "`.",
                    1.0
            );
        }

        return new CopilotAnswer(
                "⚠️ **Quebra de contrato HTTP detectada**.\n\n"
                        + "O DTO `" + dto + "` é usado nos seguintes endpoints:\n\n"
                        + endpoints.stream()
                        .limit(3)
                        .map(e -> "- " + e.path())
                        .distinct()
                        .reduce("", String::concat),
                endpoints.stream()
                        .map(e -> new CopilotAnswer.Source(e.path(), e.score()))
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
