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

    public SearchService(EmbeddingService e, SearchRepository r) {
        embedder = e;
        repo = r;
    }

    public List<SearchResult> search(String question) {
        float[] q = embedder.embed(question);
        return repo.search(q, 6);
    }


}
