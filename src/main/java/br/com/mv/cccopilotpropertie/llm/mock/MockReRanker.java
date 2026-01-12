package br.com.mv.cccopilotpropertie.llm.mock;

import br.com.mv.cccopilotpropertie.search.application.ReRanker;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

//@Service
//@Profile("mock")
//public abstract class MockReRanker implements ReRanker {
//
//    @Override
//    public List<SearchResult> rerank(String question, List<SearchResult> candidates, int topK) {
//        return List.of();
//    }
//}
@Service
@Profile("mock")
public class MockReRanker implements ReRanker {

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
