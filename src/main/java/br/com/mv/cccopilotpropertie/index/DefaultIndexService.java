package br.com.mv.cccopilotpropertie.index;


import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.vector.EmbeddingRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class DefaultIndexService implements IndexService {

    private final FileScannerService scanner;
    private final ChunkService chunker;
    private final EmbeddingService embedder;
    private final EmbeddingRepository repo;

    public DefaultIndexService(FileScannerService scanner,
                               ChunkService chunker,
                               EmbeddingService embedder,
                               EmbeddingRepository repo) {
        this.scanner = scanner;
        this.chunker = chunker;
        this.embedder = embedder;
        this.repo = repo;
    }

    @Override
    public IndexResult indexPath(String rootPath) throws IOException {

        Path root = Path.of(rootPath).normalize().toAbsolutePath();

        if (!Files.exists(root)) {
            throw new IllegalArgumentException(
                    "Caminho não existe: " + root
            );
        }

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(
                    "Caminho não é um diretório: " + root
            );
        }
        UUID jobId = UUID.randomUUID();

        int fileCount = 0;
        int chunkCount = 0;

        for (Path file : scanner.scan(root)) {
            fileCount++;

            String content;
            try {
                content = Files.readString(file);
            } catch (Exception e) {
                // log.warn("Ignorando arquivo {}: {}", file, e.getMessage());
                continue;
            }

            for (String chunk : chunker.chunk(content)) {
                chunkCount++;
                repo.save(
                        UUID.randomUUID(),      // 🔑 ID único por chunk
                        file.toString(),
                        chunk,
                        embedder.embed(chunk)
                );
            }
        }

        return new IndexResult(jobId, fileCount, chunkCount);
    }

}