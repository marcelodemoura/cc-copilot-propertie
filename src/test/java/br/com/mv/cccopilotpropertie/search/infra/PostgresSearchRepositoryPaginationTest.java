package br.com.mv.cccopilotpropertie.search.infra;

import br.com.mv.cccopilotpropertie.config.EmbeddingConfig;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostgresSearchRepositoryPaginationTest {

    private JdbcTemplate jdbc;
    private PostgresSearchRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repository = new PostgresSearchRepository(jdbc);
    }

    @Test
    void searchWithOffsetShouldPassOffsetToQuery() {
        float[] vector = new float[EmbeddingConfig.DIMENSION];

        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), anyInt(), eq(10)))
                .thenReturn(List.of(new SearchResult("file1.java", "content", 0.9)));

        List<SearchResult> results = repository.search("tenant-1", "kb-1", vector, 5, 10);

        assertEquals(1, results.size());
        verify(jdbc).query(
                contains("LIMIT ? OFFSET ?"),
                any(RowMapper.class),
                any(), eq("tenant-1"), eq("kb-1"), any(), anyInt(), eq(10)
        );
    }

    @Test
    void searchKeywordWithOffsetShouldPassOffsetToQuery() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), anyInt(), eq(20)))
                .thenReturn(List.of(new SearchResult("controller.java", "content", 1.0)));

        List<SearchResult> results = repository.searchKeyword("tenant-1", "kb-1", "paciente", 10, 20);

        assertEquals(1, results.size());
        verify(jdbc).query(
                contains("LIMIT ? OFFSET ?"),
                any(RowMapper.class),
                eq("tenant-1"), eq("kb-1"), any(), any(), any(), eq(10), eq(20)
        );
    }
}
