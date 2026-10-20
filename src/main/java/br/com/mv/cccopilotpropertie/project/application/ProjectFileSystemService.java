package br.com.mv.cccopilotpropertie.project.application;

import br.com.mv.cccopilotpropertie.project.api.DirectoryBrowseResultDto;
import br.com.mv.cccopilotpropertie.project.api.DirectoryEntryDto;
import br.com.mv.cccopilotpropertie.project.domain.ProjectEntity;
import br.com.mv.cccopilotpropertie.project.infra.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProjectFileSystemService {

    private final Path basePath;
    private final ProjectRepository projectRepository;

    private static final Set<String> IGNORED_NAMES = Set.of(
            ".git", ".gradle", "build", "target", ".idea", ".run", ".github",
            ".sixth", "node_modules", "logs", "log", "out", "dist"
    );

    private static final long MAX_ZIP_SIZE = 50 * 1024 * 1024; // 50 MB
    private static final long MAX_UNCOMPRESSED_SIZE = 250 * 1024 * 1024; // 250 MB
    private static final int MAX_FILES = 10_000;
    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB

    public ProjectFileSystemService(
            @Value("${indexer.base-path:.}") String basePathStr,
            ProjectRepository projectRepository
    ) {
        Path p = Path.of(basePathStr != null ? basePathStr : ".").toAbsolutePath().normalize();
        try {
            this.basePath = Files.exists(p) ? p.toRealPath() : p;
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao resolver base path: " + basePathStr, e);
        }
        this.projectRepository = projectRepository;
    }

    public DirectoryBrowseResultDto browse(String requestedPath) {
        Path current;
        if (requestedPath == null || requestedPath.isBlank()) {
            current = basePath;
        } else {
            Path target = Path.of(requestedPath).toAbsolutePath().normalize();
            try {
                if (Files.exists(target) && Files.isDirectory(target) && target.toRealPath().startsWith(basePath)) {
                    current = target.toRealPath();
                } else {
                    current = basePath;
                }
            } catch (Exception e) {
                current = basePath;
            }
        }

        String parentPath = null;
        if (!current.equals(basePath) && current.getParent() != null && current.getParent().startsWith(basePath)) {
            parentPath = current.getParent().toString();
        }

        List<DirectoryEntryDto> entries = new ArrayList<>();
        try (var stream = Files.list(current)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith(".") && !IGNORED_NAMES.contains(p.getFileName().toString()))
                    .forEach(dir -> entries.add(toEntry(dir)));
        } catch (Exception ignored) {
        }

        entries.sort(Comparator.comparing(DirectoryEntryDto::isProject).reversed()
                .thenComparing(DirectoryEntryDto::name, String.CASE_INSENSITIVE_ORDER));

        return new DirectoryBrowseResultDto(current.toString(), parentPath, basePath.toString(), entries);
    }

    public List<DirectoryEntryDto> searchDirectories(String query) {
        if (query == null || query.isBlank() || query.trim().length() < 2) {
            return List.of();
        }

        String q = query.trim().toLowerCase(Locale.ROOT);
        List<DirectoryEntryDto> results = new ArrayList<>();

        try {
            Files.walkFileTree(basePath, Set.of(), 4, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (results.size() >= 30) {
                        return FileVisitResult.TERMINATE;
                    }
                    String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (name.startsWith(".") || IGNORED_NAMES.contains(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    if (!dir.equals(basePath) && name.toLowerCase(Locale.ROOT).contains(q)) {
                        results.add(toEntry(dir));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {
        }

        results.sort(Comparator.comparing(DirectoryEntryDto::isProject).reversed()
                .thenComparing(DirectoryEntryDto::name, String.CASE_INSENSITIVE_ORDER));

        return results;
    }

    public ProjectEntity uploadAndExtractZip(MultipartFile file, String requestedName, String tenantId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo ZIP é obrigatório.");
        }

        if (file.getSize() > MAX_ZIP_SIZE) {
            throw new IllegalArgumentException("O arquivo ZIP excede o limite máximo permitido de 50MB.");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "project.zip";
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("O arquivo enviado deve ser do tipo .zip");
        }

        String baseName = originalName.substring(0, originalName.length() - 4).replaceAll("[^a-zA-Z0-9._-]", "_");
        if (baseName.isBlank()) {
            baseName = "project_" + System.currentTimeMillis();
        }

        Path uploadsDir = basePath.resolve("uploads").normalize();
        Files.createDirectories(uploadsDir);

        Path targetDir = uploadsDir.resolve(baseName);
        if (Files.exists(targetDir)) {
            targetDir = uploadsDir.resolve(baseName + "_" + System.currentTimeMillis());
        }
        Files.createDirectories(targetDir);

        long totalUncompressedBytes = 0;
        int totalEntries = 0;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                totalEntries++;
                if (totalEntries > MAX_FILES) {
                    throw new SecurityException("Arquivo ZIP excede a quantidade máxima de " + MAX_FILES + " arquivos (possível Zip Bomb).");
                }

                Path dest = targetDir.resolve(entry.getName()).normalize();
                if (!dest.startsWith(targetDir)) {
                    throw new SecurityException("Entrada inválida no arquivo ZIP (Zip Slip): " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    long entryBytes = 0;
                    try (var out = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            entryBytes += read;
                            totalUncompressedBytes += read;

                            if (entryBytes > MAX_FILE_SIZE) {
                                throw new SecurityException("Arquivo individual no ZIP excede 25MB: " + entry.getName());
                            }
                            if (totalUncompressedBytes > MAX_UNCOMPRESSED_SIZE) {
                                throw new SecurityException("Tamanho descompactado excede o limite de 250MB (Zip Bomb detectado).");
                            }
                            out.write(buffer, 0, read);
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            deleteQuietly(targetDir);
            throw e;
        }

        Path effectiveRoot = targetDir;
        try (var stream = Files.list(targetDir)) {
            List<Path> contents = stream.toList();
            if (contents.size() == 1 && Files.isDirectory(contents.get(0))) {
                effectiveRoot = contents.get(0);
            }
        } catch (Exception ignored) {
        }

        String finalName = (requestedName != null && !requestedName.isBlank())
                ? requestedName.trim()
                : effectiveRoot.getFileName().toString();

        String finalTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : "default";

        return projectRepository.save(new ProjectEntity(finalTenant, finalName, effectiveRoot.toRealPath().toString()));
    }

    private void deleteQuietly(Path dir) {
        if (dir != null && Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }
    }

    private DirectoryEntryDto toEntry(Path dir) {
        String name = dir.getFileName().toString();
        String type = detectProjectType(dir);
        return new DirectoryEntryDto(name, dir.toString(), type != null, type);
    }

    private String detectProjectType(Path dir) {
        if (Files.exists(dir.resolve("build.gradle")) || Files.exists(dir.resolve("build.gradle.kts"))) {
            return "Gradle";
        }
        if (Files.exists(dir.resolve("pom.xml"))) {
            return "Maven";
        }
        if (Files.exists(dir.resolve("angular.json"))) {
            return "Angular";
        }
        if (Files.exists(dir.resolve("next.config.js")) || Files.exists(dir.resolve("next.config.mjs")) || Files.exists(dir.resolve("next.config.ts"))) {
            return "Next.js";
        }
        if (Files.exists(dir.resolve("package.json"))) {
            return "Node/TS";
        }
        if (Files.exists(dir.resolve("pyproject.toml")) || Files.exists(dir.resolve("requirements.txt")) || Files.exists(dir.resolve("Pipfile")) || Files.exists(dir.resolve("manage.py"))) {
            return "Python";
        }
        if (Files.exists(dir.resolve("go.mod"))) {
            return "Go";
        }
        if (Files.exists(dir.resolve("Cargo.toml"))) {
            return "Rust";
        }
        if (Files.exists(dir.resolve("pubspec.yaml"))) {
            return "Flutter/Dart";
        }
        if (Files.exists(dir.resolve("composer.json"))) {
            return "PHP";
        }
        if (Files.exists(dir.resolve("Gemfile"))) {
            return "Ruby";
        }
        try (var stream = Files.list(dir)) {
            if (stream.anyMatch(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".sln") || n.endsWith(".csproj");
            })) {
                return ".NET/C#";
            }
        } catch (Exception ignored) {
        }
        if (Files.exists(dir.resolve(".git"))) {
            return "Git";
        }
        return null;
    }
}
