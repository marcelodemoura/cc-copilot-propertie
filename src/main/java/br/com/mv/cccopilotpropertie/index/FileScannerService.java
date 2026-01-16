package br.com.mv.cccopilotpropertie.index;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class FileScannerService {


    @Value("${indexer.base-path}")
    private String basePath;

    public List<Path> scan(Path root) throws IOException {
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(this::isValid)
                .toList();
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
        String p = path.toString();

        return (
                p.endsWith("DTO.java") ||
                        p.contains("/dto/")
        )
                && !p.contains("/test/")
                && !p.contains("/target/")
                && !p.contains("/build/")
                && !p.contains("/.git/");
    }
}