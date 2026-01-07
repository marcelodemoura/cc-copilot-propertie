package br.com.mv.cccopilotpropertie.index;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Service
public class FileScannerService {

    public List<Path> scan(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walk(root)
                .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".yml"))
                .forEach(files::add);
        return files;
    }

    @Value("${indexer.base-path}")
    private String basePath;

    public Stream<Path> scan() throws IOException {
        Path root = Paths.get(basePath);

        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Caminho não existe: " + root.toAbsolutePath());
        }

        return Files.walk(root).filter(Files::isRegularFile);
    }
}