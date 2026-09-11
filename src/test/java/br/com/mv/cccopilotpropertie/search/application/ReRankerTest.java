package br.com.mv.cccopilotpropertie.search.application;

import br.com.mv.cccopilotpropertie.llm.mock.MockReRanker;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.OpenAiReRanker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReRankerTest {

    @Test
    void openAiReRankerShouldSortDescendingByScoreAndLimit() {
        OpenAiReRanker reranker = new OpenAiReRanker("dummy-key");

        SearchResult low = new SearchResult("Low.java", "low content", 0.3);
        SearchResult high = new SearchResult("High.java", "high content", 0.95);
        SearchResult mid = new SearchResult("Mid.java", "mid content", 0.6);

        List<SearchResult> ranked = reranker.rerank("qual classe?", List.of(low, high, mid));

        assertEquals(3, ranked.size());
        assertEquals("High.java", ranked.get(0).path());
        assertEquals("Mid.java", ranked.get(1).path());
        assertEquals("Low.java", ranked.get(2).path());

        List<SearchResult> top2 = reranker.rerank("qual classe?", List.of(low, high, mid), 2);
        assertEquals(2, top2.size());
        assertEquals("High.java", top2.get(0).path());
        assertEquals("Mid.java", top2.get(1).path());
    }

    @Test
    void mockReRankerShouldSortDescendingByScoreAndLimit() {
        MockReRanker reranker = new MockReRanker();

        SearchResult low = new SearchResult("Low.java", "low content", 0.2);
        SearchResult high = new SearchResult("High.java", "high content", 0.88);

        List<SearchResult> ranked = reranker.rerank("question", List.of(low, high), 1);
        assertEquals(1, ranked.size());
        assertEquals("High.java", ranked.get(0).path());
    }

    @Test
    void reRankerShouldHandleNullAndEmptyLists() {
        OpenAiReRanker reranker = new OpenAiReRanker("dummy-key");

        assertTrue(reranker.rerank("q", null).isEmpty());
        assertTrue(reranker.rerank("q", List.of()).isEmpty());
        assertTrue(reranker.rerank("q", List.of(), 5).isEmpty());
        assertTrue(reranker.rerank("q", List.of(new SearchResult("A", "B", 1.0)), 0).isEmpty());
        assertTrue(reranker.rerank("q", List.of(new SearchResult("A", "B", 1.0)), -1).isEmpty());
    }
}
