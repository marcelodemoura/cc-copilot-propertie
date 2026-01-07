package br.com.mv.cccopilotpropertie.index;

import br.com.mv.cccopilotpropertie.domain.IndexJobEntity;
import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.vector.EmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
public class AbstractIndexServiceTest {


    @Mock
    FileScannerService scanner;
    @Mock
    ChunkService chunker;
    @Mock
    EmbeddingService embedder;
    @Mock
    EmbeddingRepository repo;

    AbstractIndexService service;

    @BeforeEach
    void setup() {
        service = new AbstractIndexService(scanner, chunker, embedder, repo) {
        };
    }

    @Test
    void should_index_all_files_and_chunks() throws Exception {

        Path root = Path.of("/root");
        Path f1 = Path.of("/root/a.txt");
        Path f2 = Path.of("/root/b.txt");

        when(scanner.scan(root)).thenReturn(List.of(f1, f2));
        when(chunker.chunk("A")).thenReturn(List.of("a1", "a2"));
        when(chunker.chunk("B")).thenReturn(List.of("b1"));

        when(embedder.embed(any())).thenReturn(new float[]{1, 2, 3});

        try (MockedStatic<Files> files = mockStatic(Files.class)) {
            files.when(() -> Files.readString(f1)).thenReturn("A");
            files.when(() -> Files.readString(f2)).thenReturn("B");

            IndexJobEntity job = service.runIndex(root);

            assertEquals(2, job.getFileCount());
            assertEquals(3, job.getChunkCount());


            verify(repo, times(3)).save(any(), any(), any(), any());
        }
    }
}
