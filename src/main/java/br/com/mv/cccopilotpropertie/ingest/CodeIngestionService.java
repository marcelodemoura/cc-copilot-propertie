package br.com.mv.cccopilotpropertie.ingest;


import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.util.TextSplitter;
import br.com.mv.cccopilotpropertie.vector.EmbeddingRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
@Service
public class CodeIngestionService {

    private final EmbeddingService embedder;
    private final EmbeddingRepository repo;

    public CodeIngestionService(
            EmbeddingService embedder,
            EmbeddingRepository repo
    ) {
        this.embedder = embedder;
        this.repo = repo;
    }

    public void ingest(
            Path root,
            String tenantId,
            String knowledgeBase
    ) throws IOException {

        Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> processFile(p, tenantId, knowledgeBase));
    }

    private void processFile(
            Path path,
            String tenantId,
            String knowledgeBase
    ) {
        try {
            String content = Files.readString(path);

            for (String part : TextSplitter.split(content, 1500)) {
                float[] vector = embedder.embed(part);

                repo.save(
                        UUID.randomUUID(),
                        tenantId,
                        knowledgeBase,
                        path.toString(),
                        part,
                        vector
                );
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Erro ao ingerir arquivo: " + path,
                    e
            );
        }
    }
}
