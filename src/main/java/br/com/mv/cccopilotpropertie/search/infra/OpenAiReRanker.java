package br.com.mv.cccopilotpropertie.search.infra;

import br.com.mv.cccopilotpropertie.search.application.ReRanker;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@Profile("!mock")
public class OpenAiReRanker implements ReRanker {

    private final String apiKey;

    public OpenAiReRanker(
            @Value("${llm.openai.api-key:}") String apiKey
    ) {
        this.apiKey = apiKey;
    }

    @Override
    public List<SearchResult> rerank(
            String question,
            List<SearchResult> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .toList();
    }
}