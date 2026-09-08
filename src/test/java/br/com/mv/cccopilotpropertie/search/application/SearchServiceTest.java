package br.com.mv.cccopilotpropertie.search.application;

import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private EmbeddingService embedder;

    @Mock
    private SearchRepository repo;

    @Mock
    private ReRanker reranker;

    @Test
    void shouldPerformHybridSearchWithKeywordAndVectorMatches() {
        when(embedder.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        SearchResult vectorResult = new SearchResult("FileA.java", "conteudo A", 0.7);
        SearchResult keywordResult = new SearchResult("MetasDiagProcessosController.java", "@RestController public class MetasDiagProcessosController", 1.0);

        when(repo.search(eq("tenant-1"), eq("kb-1"), any(), eq(20)))
                .thenReturn(List.of(vectorResult));
        when(repo.searchKeyword(eq("tenant-1"), eq("kb-1"), eq("MetasDiagProcessos"), eq(10)))
                .thenReturn(List.of(keywordResult));

        when(reranker.rerank(eq("MetasDiagProcessos"), anyList(), eq(10)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        SearchService service = new SearchService(embedder, repo, reranker);
        List<SearchResult> results = service.search("tenant-1", "kb-1", "MetasDiagProcessos", 10);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("MetasDiagProcessosController.java", results.get(0).path());
        assertEquals("FileA.java", results.get(1).path());

        verify(repo).searchKeyword(eq("tenant-1"), eq("kb-1"), eq("MetasDiagProcessos"), eq(10));
        verify(repo).search(eq("tenant-1"), eq("kb-1"), any(), eq(20));
    }
}
