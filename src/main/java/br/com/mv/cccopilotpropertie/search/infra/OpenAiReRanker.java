package br.com.mv.cccopilotpropertie.search.infra;

import br.com.mv.cccopilotpropertie.search.application.ReRanker;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

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
        return candidates;
    }

    @Override
    public List<SearchResult> rerank(
            String question,
            List<SearchResult> candidates,
            int topK
    ) {
        return candidates.stream()
                .limit(topK)
                .toList();
    }
}