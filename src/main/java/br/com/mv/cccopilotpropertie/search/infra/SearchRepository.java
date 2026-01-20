package br.com.mv.cccopilotpropertie.search.infra;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;

import java.util.List;
import java.util.Optional;

public interface SearchRepository {

    List<SearchResult> search(
            String tenantId,
            String knowledgeBase,
            float[] vector,
            int limit
    );

    Optional<SearchResult> findByClassName(
            String tenantId,
            String knowledgeBase,
            String className
    );

    List<SearchResult> findUsagesByClassName(
            String tenantId,
            String knowledgeBase,
            String className
    );

    Optional<SearchResult> findByClassNameGlobal(
            String tenantId,
            String className
    );


}