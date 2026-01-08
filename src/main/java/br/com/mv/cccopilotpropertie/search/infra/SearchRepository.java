package br.com.mv.cccopilotpropertie.search.infra;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;

import java.util.List;


public interface SearchRepository {

    List<SearchResult> search(String tenantId,
                              String knowledgeBase,
                              float[] embedding,
                              int limit);
}