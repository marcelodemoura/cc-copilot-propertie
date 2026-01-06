package br.com.mv.cccopilotpropertie.index;


import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.vector.EmbeddingRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class DefaultIndexService extends AbstractIndexService implements IndexService {

    public DefaultIndexService(FileScannerService s,
                               ChunkService c,
                               EmbeddingService e,
                               EmbeddingRepository r) {
        super(s, c, e, r);
    }

    @Override
    public IndexJob indexPath(String rootPath) throws IOException {
        Path root = Path.of(rootPath).normalize().toAbsolutePath();
        return runIndex(root);
    }
}


