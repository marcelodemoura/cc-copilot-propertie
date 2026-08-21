package br.com.mv.cccopilotpropertie.index;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Service
public class FileScannerService {


    @Value("${indexer.base-path}")
    private String basePath;

    public List<Path> scan(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(this::isValid)
                    .toList();
        }
    }

    public List<Path> scan() throws IOException {
        Path root = Paths.get(basePath);

        if (!Files.exists(root)) {
            throw new IllegalArgumentException(
                    "Caminho não existe: " + root.toAbsolutePath()
            );
        }

        return scan(root);
    }

    private boolean isValid(Path path) {
        String p = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (p.contains("/.git/") || p.contains("/target/") || p.contains("/build/")
                || p.contains("/.idea/") || p.contains("/node_modules/")) {
            return false;
        }
        return p.endsWith(".java") || p.endsWith(".kt") || p.endsWith(".groovy")
                || p.endsWith(".xml") || p.endsWith(".yml") || p.endsWith(".yaml")
                || p.endsWith(".properties") || p.endsWith(".md") || p.endsWith(".sql")
                || p.endsWith(".json") || p.endsWith(".gradle") || p.endsWith(".gradle.kts")
                || p.endsWith(".tf") || p.endsWith(".sh");
    }
}
