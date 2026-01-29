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

    // 🌍 DTO DEFINIÇÃO GLOBAL (INTER-PROJETOS)
    Optional<SearchResult> findDtoDefinitionGlobal(
            String tenantId,
            String dtoName
    );

    // 🌍 USOS DO DTO EM OUTROS PROJETOS
    List<SearchResult> findUsagesInOtherKnowledgeBases(
            String tenantId,
            String excludeKnowledgeBase,
            String dtoName
    );
    List<SearchResult> findEndpointsUsingDto(
            String tenantId,
            String knowledgeBase,
            String dtoName
    );

}
