package br.com.mv.cccopilotpropertie.search.application;

import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchService {

    private final EmbeddingService embedder;
    private final SearchRepository repo;
    private final ReRanker reranker;

    public SearchService(EmbeddingService e, SearchRepository r, ReRanker rr) {
        embedder = e;
        repo = r;
        reranker = rr;
    }

    public List<SearchResult> search(String tenant, String kb, String question, int limit) {
        float[] q = embedder.embed(question);

        var raw = repo.search(tenant, kb, q, 20);  // pega mais
        return reranker.rerank(question, raw, limit);
    }
}


