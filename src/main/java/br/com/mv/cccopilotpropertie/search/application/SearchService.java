package br.com.mv.cccopilotpropertie.search.application;

import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

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

    private static final Set<String> STOP_WORDS = Set.of(
            "onde", "classe", "projeto", "como", "esta", "está", "para", "com", "qual", "quais",
            "esse", "essa", "sobre", "pelo", "pela", "show", "find", "view", "codigo", "código",
            "metodo", "método", "arquivo", "pasta", "package", "class"
    );

    private static final Pattern WORD_SPLIT = Pattern.compile("[^a-zA-Z0-9_]+");

    public List<SearchResult> search(String tenant, String kb, String question, int limit) {
        return search(tenant, kb, question, limit, 0);
    }

    public List<SearchResult> search(String tenant, String kb, String question, int limit, int offset) {
        float[] q = embedder.embed(question);

        var vectorResults = offset > 0
                ? repo.search(tenant, kb, q, limit * 2, offset)
                : repo.search(tenant, kb, q, limit * 2);

        // Busca simbólica por palavras-chave relevantes
        List<SearchResult> keywordResults = new ArrayList<>();
        if (question != null && !question.isBlank()) {
            String cleanQ = question.trim();
            if (cleanQ.length() >= 3 && !cleanQ.contains(" ")) {
                keywordResults.addAll(offset > 0
                        ? repo.searchKeyword(tenant, kb, cleanQ, limit, offset)
                        : repo.searchKeyword(tenant, kb, cleanQ, limit));
            } else {
                for (String word : WORD_SPLIT.split(question)) {
                    String lower = word.toLowerCase(Locale.ROOT);
                    if (word.length() >= 4 && !STOP_WORDS.contains(lower)) {
                        keywordResults.addAll(offset > 0
                                ? repo.searchKeyword(tenant, kb, word, 4, offset)
                                : repo.searchKeyword(tenant, kb, word, 4));
                    }
                }
            }
        }

        // Combina priorizando correspondência de palavras-chave exatas no topo
        Map<String, SearchResult> merged = new LinkedHashMap<>();
        for (SearchResult kwr : keywordResults) {
            merged.put(kwr.path() + ":" + kwr.content().hashCode(), kwr);
        }
        for (SearchResult vr : vectorResults) {
            merged.putIfAbsent(vr.path() + ":" + vr.content().hashCode(), vr);
        }

        List<SearchResult> combined = new ArrayList<>(merged.values());
        return reranker.rerank(question, combined, limit);
    }
}


