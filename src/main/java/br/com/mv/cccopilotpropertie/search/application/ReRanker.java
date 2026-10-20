package br.com.mv.cccopilotpropertie.search.application;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;

import java.util.List;

public interface ReRanker {

    List<SearchResult> rerank(
            String question,
            List<SearchResult> results
    );

    default List<SearchResult> rerank(String question, List<SearchResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        return rerank(question, candidates).stream()
                .limit(topK)
                .toList();
    }
}
