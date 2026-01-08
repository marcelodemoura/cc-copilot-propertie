package br.com.mv.cccopilotpropertie.search.application;

import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;@Service
public class SearchService {

    private final EmbeddingService embedder;
    private final SearchRepository repo;

    public SearchService(EmbeddingService e, SearchRepository r) {
        this.embedder = e;
        this.repo = r;
    }

    public List<SearchResult> search(String tenantId,
                                     String knowledgeBase,
                                     String question,
                                     int limit) {

        float[] embedding = embedder.embed(question);
        return repo.search(tenantId, knowledgeBase, embedding, limit);
    }
}

