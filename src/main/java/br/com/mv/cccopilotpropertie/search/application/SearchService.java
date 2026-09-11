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

    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public SearchService(EmbeddingService e, SearchRepository r, ReRanker rr) {
        this(e, r, rr, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SearchService(EmbeddingService e, SearchRepository r, ReRanker rr,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.embedder = e;
        this.repo = r;
        this.reranker = rr;
        this.meterRegistry = meterRegistry;
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
        String effectiveTenant = tenant != null ? tenant : "default";

        long startEmbed = System.nanoTime();
        float[] q = embedder.embed(question);
        long embedTimeMs = (System.nanoTime() - startEmbed) / 1_000_000;

        if (meterRegistry != null) {
            meterRegistry.timer("rag.embedding.time", "tenant", effectiveTenant)
                    .record(embedTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        long startSearch = System.nanoTime();

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

        long searchTimeMs = (System.nanoTime() - startSearch) / 1_000_000;

        if (meterRegistry != null) {
            meterRegistry.timer("rag.search.time", "tenant", effectiveTenant)
                    .record(searchTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            meterRegistry.counter("rag.queries.total", "tenant", effectiveTenant).increment();
        }

        List<SearchResult> combined = new ArrayList<>(merged.values());
        List<SearchResult> finalResults = reranker.rerank(question, combined, limit);

        if (meterRegistry != null) {
            meterRegistry.summary("rag.results.count", "tenant", effectiveTenant).record(finalResults.size());
        }

        return finalResults;
    }
}


