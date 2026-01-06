package br.com.mv.cccopilotpropertie.index;

import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.vector.EmbeddingRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class AbstractIndexService {


        protected final FileScannerService scanner;
        protected final ChunkService chunker;
        protected final EmbeddingService embedder;
        protected final EmbeddingRepository repo;

        protected AbstractIndexService(FileScannerService s,
                                       ChunkService c,
                                       EmbeddingService e,
                                       EmbeddingRepository r) {
            scanner = s;
            chunker = c;
            embedder = e;
            repo = r;
        }

        protected IndexJob runIndex(Path root) throws IOException {
            UUID jobId = UUID.randomUUID();
            int fileCount = 0;
            int chunkCount = 0;

            for (Path file : scanner.scan(root)) {
                fileCount++;
                String content = Files.readString(file);

                for (String chunk : chunker.chunk(content)) {
                    chunkCount++;
                    repo.save(jobId, file.toString(), chunk, embedder.embed(chunk));
                }
            }
            return new IndexJob(jobId, fileCount, chunkCount);
        }
    }
