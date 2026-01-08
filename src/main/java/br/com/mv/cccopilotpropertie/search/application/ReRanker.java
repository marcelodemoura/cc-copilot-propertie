package br.com.mv.cccopilotpropertie.search.application;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;

import java.util.List;

public interface ReRanker {

    List<SearchResult> rerank(String question, List<SearchResult> candidates, int topK);
}
