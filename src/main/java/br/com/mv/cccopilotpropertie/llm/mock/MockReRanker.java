package br.com.mv.cccopilotpropertie.llm.mock;

import br.com.mv.cccopilotpropertie.search.application.ReRanker;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@Profile("mock")
public class MockReRanker implements ReRanker {

    @Override
    public List<SearchResult> rerank(String question, List<SearchResult> results) {
        return results.stream().sorted(Comparator.comparingDouble(SearchResult::score).reversed()).toList();
    }

    @Override
    public List<SearchResult> rerank(String question, List<SearchResult> candidates, int topK) {
        return rerank(question, candidates).stream().limit(topK).toList();
    }
}
