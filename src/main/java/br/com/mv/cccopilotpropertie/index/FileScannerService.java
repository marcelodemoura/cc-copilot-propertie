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

    private static final long MAX_FILE_SIZE = 300 * 1024; // 300 KB

    private boolean isValid(Path path) {
        String p = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (p.contains("/.git/") || p.contains("/target/") || p.contains("/build/")
                || p.contains("/.idea/") || p.contains("/node_modules/")
                || p.contains("/.gradle/") || p.contains("/log/") || p.contains("/logs/")) {
            return false;
        }

        // Ignora dumps pesados de banco de dados em recursos de testes
        if (p.endsWith(".sql") && (p.contains("/src/test/") || p.contains("/test/resources/") || p.contains("/resources/test/"))) {
            return false;
        }

        try {
            if (Files.size(path) > MAX_FILE_SIZE) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        return p.endsWith(".java") || p.endsWith(".kt") || p.endsWith(".groovy")
                || p.endsWith(".xml") || p.endsWith(".yml") || p.endsWith(".yaml")
                || p.endsWith(".properties") || p.endsWith(".md") || p.endsWith(".sql")
                || p.endsWith(".json") || p.endsWith(".gradle") || p.endsWith(".gradle.kts")
                || p.endsWith(".tf") || p.endsWith(".sh");
    }
}
