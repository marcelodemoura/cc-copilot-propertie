package br.com.mv.cccopilotpropertie.ingest;

import br.com.mv.cccopilotpropertie.domain.CodeChunk;
import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.util.TextSplitter;
import br.com.mv.cccopilotpropertie.vectorstore.VectorRepository;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.UUID;

@Service
public class CodeIngestionService {

    private final EmbeddingService embedder;
    private final VectorRepository repo;

    public CodeIngestionService(EmbeddingService e, VectorRepository r) {
        this.embedder = e;
        this.repo = r;
    }

    public void ingest(Path root) throws Exception {
        Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(this::processFile);
    }

    private void processFile(Path path) {
        try {
            String content = Files.readString(path);
            for (String part : TextSplitter.split(content, 1500)) {
                float[] vector = embedder.embed(part);
                repo.save(new CodeChunk(UUID.randomUUID(), path.toString(), part, vector));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
