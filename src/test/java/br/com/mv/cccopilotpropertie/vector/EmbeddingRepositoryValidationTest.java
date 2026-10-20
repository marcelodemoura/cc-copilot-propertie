package br.com.mv.cccopilotpropertie.vector;

import br.com.mv.cccopilotpropertie.config.EmbeddingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EmbeddingRepositoryValidationTest {

    private EmbeddingRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repository = new EmbeddingRepository(jdbc);
    }

    @Test
    void shouldThrowExceptionWhenVectorIsNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                repository.save(UUID.randomUUID(), "tenant-1", "kb-1", "path/file.java", "content", null)
        );
        assertTrue(ex.getMessage().contains("cannot be null"));
    }

    @Test
    void shouldThrowExceptionWhenVectorDimensionMismatch() {
        float[] wrongVector = new float[768]; // Nomic without padding

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                repository.save(UUID.randomUUID(), "tenant-1", "kb-1", "path/file.java", "content", wrongVector)
        );
        assertTrue(ex.getMessage().contains("expected " + EmbeddingConfig.DIMENSION));
        assertTrue(ex.getMessage().contains("got 768"));
    }

    @Test
    void shouldThrowExceptionWhenBatchContainsInvalidVector() {
        List<EmbeddingRepository.EmbeddingRecord> batch = List.of(
                new EmbeddingRepository.EmbeddingRecord(
                        UUID.randomUUID(), "tenant-1", "kb-1", "p1", "c1", new float[10]
                )
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                repository.saveBatch(batch)
        );
        assertTrue(ex.getMessage().contains("expected " + EmbeddingConfig.DIMENSION));
    }
}
