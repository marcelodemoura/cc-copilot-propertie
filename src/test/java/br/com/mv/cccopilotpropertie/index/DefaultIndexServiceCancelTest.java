package br.com.mv.cccopilotpropertie.index;

import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.vector.EmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultIndexServiceCancelTest {

    private FileScannerService scanner;
    private ChunkService chunker;
    private EmbeddingService embedder;
    private EmbeddingRepository repo;
    private DefaultIndexService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        scanner = mock(FileScannerService.class);
        chunker = mock(ChunkService.class);
        embedder = mock(EmbeddingService.class);
        repo = mock(EmbeddingRepository.class);

        service = new DefaultIndexService(scanner, chunker, embedder, repo, tempDir.toString());
    }

    @Test
    void shouldCancelRunningJob() throws IOException, InterruptedException {
        Path dummyFile = tempDir.resolve("Test.java");
        Files.writeString(dummyFile, "class Test {}");

        when(scanner.scan(any())).thenAnswer(inv -> {
            Thread.sleep(150);
            return List.of(dummyFile);
        });
        when(chunker.chunk(any())).thenReturn(List.of("chunk1"));
        when(embedder.embedBatch(any())).thenReturn(List.of(new float[1536]));

        IndexJobStatus initial = service.indexAsync("default", tempDir.toString(), "kb-test");
        assertNotNull(initial);
        UUID jobId = initial.jobId();

        boolean cancelled = service.cancelJob(jobId);
        assertTrue(cancelled);

        var status = service.getJobStatus(jobId);
        assertTrue(status.isPresent());
        assertEquals("CANCELLED", status.get().status());
        assertTrue(status.get().errorMessage().contains("cancelada"));
    }

    @Test
    void shouldCancelLatestJobByKb() throws IOException {
        Path dummyFile = tempDir.resolve("Test.java");
        Files.writeString(dummyFile, "class Test {}");

        when(scanner.scan(any())).thenAnswer(invocation -> {
            Thread.sleep(200);
            return List.of(dummyFile);
        });

        IndexJobStatus initial = service.indexAsync("default", tempDir.toString(), "kb-latest");
        UUID jobId = initial.jobId();

        boolean cancelled = service.cancelLatestJob("kb-latest");
        assertTrue(cancelled);

        var status = service.getLatestJobStatus("kb-latest");
        assertTrue(status.isPresent());
        assertEquals(jobId, status.get().jobId());
        assertEquals("CANCELLED", status.get().status());
    }

    @Test
    void shouldReturnFalseWhenJobNotFound() {
        assertFalse(service.cancelJob(UUID.randomUUID()));
    }
}
