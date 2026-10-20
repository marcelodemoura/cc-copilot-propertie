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
                || p.contains("/.gradle/") || p.contains("/log/") || p.contains("/logs/")
                || p.contains("/dist/") || p.contains("/.next/") || p.contains("/.nuxt/")
                || p.contains("/__pycache__/") || p.contains("/.venv/") || p.contains("/venv/")
                || p.contains("/coverage/") || p.contains("/out/")) {
            return false;
        }

        // Ignora arquivos minificados ou locks pesados
        if (p.endsWith(".min.js") || p.endsWith(".min.css") || p.endsWith("-lock.json") || p.endsWith("yarn.lock") || p.endsWith("pnpm-lock.yaml")) {
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

        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.equals("dockerfile") || fileName.equals("makefile") || fileName.equals("jenkinsfile")) {
            return true;
        }

        return p.endsWith(".java") || p.endsWith(".kt") || p.endsWith(".kts") || p.endsWith(".groovy")
                || p.endsWith(".ts") || p.endsWith(".tsx") || p.endsWith(".js") || p.endsWith(".jsx")
                || p.endsWith(".mjs") || p.endsWith(".cjs") || p.endsWith(".vue") || p.endsWith(".svelte")
                || p.endsWith(".py") || p.endsWith(".go") || p.endsWith(".rs") || p.endsWith(".cs")
                || p.endsWith(".php") || p.endsWith(".rb") || p.endsWith(".swift") || p.endsWith(".dart")
                || p.endsWith(".c") || p.endsWith(".cpp") || p.endsWith(".h") || p.endsWith(".hpp")
                || p.endsWith(".html") || p.endsWith(".css") || p.endsWith(".scss") || p.endsWith(".less")
                || p.endsWith(".xml") || p.endsWith(".yml") || p.endsWith(".yaml") || p.endsWith(".toml")
                || p.endsWith(".properties") || p.endsWith(".md") || p.endsWith(".graphql") || p.endsWith(".prisma")
                || p.endsWith(".sql") || p.endsWith(".pks") || p.endsWith(".pkb") || p.endsWith(".pls")
                || p.endsWith(".plb") || p.endsWith(".prc") || p.endsWith(".fnc") || p.endsWith(".trg")
                || p.endsWith(".vw") || p.endsWith(".tps") || p.endsWith(".tpb") || p.endsWith(".ddl")
                || p.endsWith(".dml") || p.endsWith(".hql") || p.endsWith(".cql")
                || p.endsWith(".json") || p.endsWith(".gradle") || p.endsWith(".tf") || p.endsWith(".sh") || p.endsWith(".bat");
    }
}
