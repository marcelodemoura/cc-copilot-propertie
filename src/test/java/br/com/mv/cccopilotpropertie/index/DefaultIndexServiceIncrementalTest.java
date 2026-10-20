package br.com.mv.cccopilotpropertie.index;

import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.vector.EmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultIndexServiceIncrementalTest {

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
    void shouldSkipUnmodifiedFilesBasedOnHash() throws IOException {
        Path file1 = tempDir.resolve("File1.java");
        String content = "public class File1 {}";
        Files.writeString(file1, content);

        String hash = computeSha256(content);

        when(scanner.scan(any())).thenReturn(List.of(file1));
        when(repo.findExistingFileHashes("tenant-1", "kb-1"))
                .thenReturn(Map.of(file1.toString(), hash));

        IndexResult result = service.indexPath("tenant-1", tempDir.toString(), "kb-1");

        assertEquals(1, result.fileCount());
        assertEquals(0, result.chunkCount());
        verify(chunker, never()).chunk(any());
        verify(embedder, never()).embedBatch(any());
    }

    @Test
    void shouldReindexModifiedFileAndCleanOldEmbeddings() throws IOException {
        Path file1 = tempDir.resolve("File1.java");
        String content = "public class File1 { int x; }";
        Files.writeString(file1, content);

        when(scanner.scan(any())).thenReturn(List.of(file1));
        when(repo.findExistingFileHashes("tenant-1", "kb-1"))
                .thenReturn(Map.of(file1.toString(), "old-different-hash"));
        when(chunker.chunk(content)).thenReturn(List.of("chunk1"));
        when(embedder.embedBatch(any())).thenReturn(List.of(new float[1536]));

        IndexResult result = service.indexPath("tenant-1", tempDir.toString(), "kb-1");

        assertEquals(1, result.fileCount());
        assertEquals(1, result.chunkCount());
        verify(repo).deleteByPath("tenant-1", "kb-1", file1.toString());
        verify(embedder).embedBatch(any());
        verify(repo).saveBatch(any());
    }

    @Test
    void shouldDeleteEmbeddingsForFilesRemovedFromDisk() throws IOException {
        Path file1 = tempDir.resolve("File1.java");
        Files.writeString(file1, "class File1 {}");
        String deletedFilePath = tempDir.resolve("Deleted.java").toString();

        when(scanner.scan(any())).thenReturn(List.of(file1));
        when(repo.findExistingFileHashes("tenant-1", "kb-1"))
                .thenReturn(Map.of(
                        file1.toString(), computeSha256("class File1 {}"),
                        deletedFilePath, "hash-of-deleted"
                ));

        IndexResult result = service.indexPath("tenant-1", tempDir.toString(), "kb-1");

        assertEquals(1, result.fileCount());
        verify(repo).deleteByPath("tenant-1", "kb-1", deletedFilePath);
    }

    private String computeSha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(content.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
