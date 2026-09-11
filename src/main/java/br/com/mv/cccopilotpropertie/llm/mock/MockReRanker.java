package br.com.mv.cccopilotpropertie.llm.mock;

import br.com.mv.cccopilotpropertie.search.application.ReRanker;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@Profile("mock")
public class MockReRanker implements ReRanker {

    @Override
    public List<SearchResult> rerank(String question, List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .toList();
    }
}
