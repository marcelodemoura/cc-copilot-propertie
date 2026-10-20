package br.com.mv.cccopilotpropertie.copilot.agent;

import br.com.mv.cccopilotpropertie.copilot.alert.AlertResult;
import br.com.mv.cccopilotpropertie.copilot.alert.AlertService;
import br.com.mv.cccopilotpropertie.copilot.audit.AuditService;
import br.com.mv.cccopilotpropertie.copilot.breaking.*;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import br.com.mv.cccopilotpropertie.project.infra.ProjectRepository;
import br.com.mv.cccopilotpropertie.copilot.code.CodeModificationService;
import br.com.mv.cccopilotpropertie.copilot.code.FileModificationResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AgentToolExecutor {

    private final SearchService searchService;
    private final SearchRepository searchRepository;
    private final BreakingChangeAnalyzer breakingAnalyzer;
    private final AlertService alertService;
    private final AuditService auditService;
    private final ProjectRepository projectRepository;
    private final String allowedBasePath;
    private final CodeModificationService codeModificationService;

    public AgentToolExecutor(SearchService searchService,
                             SearchRepository searchRepository,
                             BreakingChangeAnalyzer breakingAnalyzer,
                             AlertService alertService,
                             AuditService auditService) {
        this(searchService, searchRepository, breakingAnalyzer, alertService, auditService, null, ".", null);
    }

    public AgentToolExecutor(SearchService searchService,
                             SearchRepository searchRepository,
                             BreakingChangeAnalyzer breakingAnalyzer,
                             AlertService alertService,
                             AuditService auditService,
                             ProjectRepository projectRepository,
                             String allowedBasePath) {
        this(searchService, searchRepository, breakingAnalyzer, alertService, auditService, projectRepository, allowedBasePath, null);
    }

    @Autowired
    public AgentToolExecutor(SearchService searchService,
                             SearchRepository searchRepository,
                             BreakingChangeAnalyzer breakingAnalyzer,
                             AlertService alertService,
                             AuditService auditService,
                             @Autowired(required = false) ProjectRepository projectRepository,
                             @Value("${indexer.base-path:.}") String allowedBasePath,
                             @Autowired(required = false) CodeModificationService codeModificationService) {
        this.searchService = searchService;
        this.searchRepository = searchRepository;
        this.breakingAnalyzer = breakingAnalyzer;
        this.alertService = alertService;
        this.auditService = auditService;
        this.projectRepository = projectRepository;
        this.allowedBasePath = allowedBasePath != null ? allowedBasePath : ".";
        this.codeModificationService = codeModificationService != null
                ? codeModificationService
                : new CodeModificationService(true, this.allowedBasePath);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public String execute(String toolName, String argsJson, String tenantId, String kb) {
        try {
            Map<String, Object> args = (argsJson != null && !argsJson.isBlank())
                    ? MAPPER.readValue(argsJson, MAP_TYPE)
                    : Map.of();
            String effectiveKb = resolveTargetKb(args, tenantId, kb);
            return switch (toolName) {
                case "list_projects" -> listProjects(tenantId);
                case "git_changes" -> gitChanges(args, tenantId, effectiveKb);
                case "search_code" -> searchCode(args, tenantId, effectiveKb);
                case "find_dto_definition" -> findDtoDefinition(args, tenantId, effectiveKb);
                case "find_dto_usages" -> findDtoUsages(args, tenantId, effectiveKb);
                case "find_endpoints_using_dto" -> findEndpoints(args, tenantId, effectiveKb);
                case "find_external_usages" -> findExternalUsages(args, tenantId, effectiveKb);
                case "analyze_breaking_change" -> analyzeBreaking(args, tenantId, effectiveKb);
                case "audit_dto" -> auditDto(args, tenantId, effectiveKb);
                case "read_file" -> readFile(args, tenantId, effectiveKb);
                case "list_files" -> listFiles(args, tenantId, effectiveKb);
                case "modify_file" -> modifyFile(args, tenantId, effectiveKb);
                case "write_file" -> writeFile(args, tenantId, effectiveKb);
                default -> "Tool desconhecida: " + toolName;
            };
        } catch (Exception e) {
            return "Erro ao executar tool " + toolName + ": " + e.getMessage();
        }
    }

    private String listProjects(String tenantId) {
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> listedPaths = new java.util.HashSet<>();

        if (projectRepository != null) {
            var projects = (tenantId != null && !tenantId.isBlank())
                    ? projectRepository.findByTenantId(tenantId)
                    : projectRepository.findAll();
            if (!projects.isEmpty()) {
                sb.append("PROJETOS CADASTRADOS NO BANCO:\n");
                for (var p : projects) {
                    sb.append("- Nome: ").append(p.getName())
                            .append(" | ID: ").append(p.getId())
                            .append(" | Caminho: ").append(p.getRootPath()).append("\n");
                    listedPaths.add(Path.of(p.getRootPath()).toAbsolutePath().normalize().toString());
                }
            }
        }

        try {
            Path base = Path.of(allowedBasePath).toAbsolutePath().normalize();
            if (Files.exists(base) && Files.isDirectory(base)) {
                List<Path> diskDirs = new ArrayList<>();
                try (var stream = Files.list(base)) {
                    diskDirs = stream.filter(Files::isDirectory)
                            .filter(p -> !p.getFileName().toString().startsWith("."))
                            .filter(p -> !IGNORED_DIRS.contains(p.getFileName().toString()))
                            .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                            .toList();
                }
                List<String> extraDirs = new ArrayList<>();
                for (Path d : diskDirs) {
                    String norm = d.toAbsolutePath().normalize().toString();
                    if (!listedPaths.contains(norm)) {
                        extraDirs.add("- Nome: " + d.getFileName().toString() + " | Caminho: " + norm);
                    }
                }
                if (!extraDirs.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append("DIRETÓRIOS/PROJETOS EM ").append(base).append(":\n");
                    extraDirs.forEach(line -> sb.append(line).append("\n"));
                }
            }
        } catch (Exception ignored) {
        }

        return sb.length() == 0 ? "Nenhum projeto encontrado no workspace." : sb.toString();
    }

    private String gitChanges(Map<String, Object> args, String tenantId, String effectiveKb) {
        boolean includeDiff = Boolean.TRUE.equals(args.get("includeDiff"));
        String filter = args.get("filter") != null ? args.get("filter").toString().trim().toLowerCase(java.util.Locale.ROOT) : "";
        String rawProjectArg = args.get("project") != null ? args.get("project").toString().trim() : "";

        if ("ALL".equalsIgnoreCase(effectiveKb) || "ALL".equalsIgnoreCase(rawProjectArg)) {
            StringBuilder allSb = new StringBuilder();
            java.util.Set<Path> targets = new java.util.LinkedHashSet<>();
            if (projectRepository != null) {
                var projects = (tenantId != null && !tenantId.isBlank())
                        ? projectRepository.findByTenantId(tenantId)
                        : projectRepository.findAll();
                for (var p : projects) {
                    targets.add(Path.of(p.getRootPath()).toAbsolutePath().normalize());
                }
            }
            try (var stream = Files.list(Path.of(allowedBasePath).toAbsolutePath().normalize())) {
                stream.filter(Files::isDirectory)
                        .filter(d -> Files.exists(d.resolve(".git")))
                        .limit(15)
                        .forEach(d -> targets.add(d.toAbsolutePath().normalize()));
            } catch (Exception ignored) {
            }
            for (Path dir : targets) {
                String out = inspectGitRepo(dir, includeDiff, filter);
                if (!out.isBlank()) {
                    allSb.append(out).append("\n");
                }
            }
            return allSb.length() == 0 ? "Nenhuma alteração Git encontrada nos projetos." : allSb.toString();
        }

        List<Path> candidatePaths = resolveAllMatchingProjectPaths(rawProjectArg.isEmpty() ? effectiveKb : rawProjectArg, tenantId, effectiveKb);
        if (candidatePaths.isEmpty()) {
            return "Diretório do projeto não encontrado: " + (rawProjectArg.isEmpty() ? effectiveKb : rawProjectArg);
        }

        StringBuilder sb = new StringBuilder();
        for (Path p : candidatePaths) {
            sb.append(inspectGitRepo(p, includeDiff, filter)).append("\n");
        }
        return sb.toString();
    }

    private List<Path> resolveAllMatchingProjectPaths(String ref, String tenantId, String effectiveKb) {
        java.util.Set<Path> paths = new java.util.LinkedHashSet<>();
        if (ref == null || ref.isBlank()) {
            Path p = resolveProjectPath(effectiveKb);
            if (Files.exists(p) && Files.isDirectory(p)) paths.add(p);
            return new ArrayList<>(paths);
        }

        String[] tokens = ref.split("[,;]+");
        Path base = Path.of(allowedBasePath).toAbsolutePath().normalize();

        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isEmpty()) continue;

            // 1. Pasta direta em allowedBasePath (ex: /home/marcelo/IdeaProjects/command-center-view)
            Path direct = base.resolve(token).toAbsolutePath().normalize();
            if (direct.startsWith(base) && Files.exists(direct) && Files.isDirectory(direct)) {
                paths.add(direct);
            }

            // 2. Projetos cadastrados no banco com esse ID ou nome
            if (projectRepository != null) {
                try {
                    UUID id = UUID.fromString(token);
                    projectRepository.findById(id).ifPresent(proj -> {
                        Path pr = Path.of(proj.getRootPath()).toAbsolutePath().normalize();
                        if (Files.exists(pr) && Files.isDirectory(pr)) {
                            paths.add(pr);
                            // Se o projeto no banco aponta para um submódulo (ex: command-center-parent/command-center-view),
                            // também inclui o diretório homônimo direto na raiz se existir
                            Path siblingInBase = base.resolve(proj.getName()).toAbsolutePath().normalize();
                            if (Files.exists(siblingInBase) && Files.isDirectory(siblingInBase)) {
                                paths.add(siblingInBase);
                            }
                        }
                    });
                } catch (IllegalArgumentException ignored) {
                }

                var allProjs = (tenantId != null && !tenantId.isBlank())
                        ? projectRepository.findByTenantId(tenantId)
                        : projectRepository.findAll();
                for (var proj : allProjs) {
                    if (proj.getName().equalsIgnoreCase(token) || proj.getRootPath().endsWith("/" + token)) {
                        Path pr = Path.of(proj.getRootPath()).toAbsolutePath().normalize();
                        if (Files.exists(pr) && Files.isDirectory(pr)) {
                            paths.add(pr);
                        }
                    }
                }
            }
        }

        if (paths.isEmpty()) {
            Path fallback = resolveProjectPath(effectiveKb);
            if (Files.exists(fallback) && Files.isDirectory(fallback)) {
                paths.add(fallback);
            }
        }
        return new ArrayList<>(paths);
    }

    private String inspectGitRepo(Path repoDir, boolean includeDiff, String filter) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PROJETO: ").append(repoDir.getFileName()).append(" (").append(repoDir).append(") ===\n");

        if (!Files.exists(repoDir.resolve(".git"))) {
            sb.append("(Sem repositório .git — listando arquivos modificados mais recentemente no disco)\n");
            try (var walk = Files.walk(repoDir, 6)) {
                var recent = walk.filter(Files::isRegularFile)
                        .filter(p -> {
                            String s = p.toString().replace('\\', '/');
                            return !s.contains("/.git/") && !s.contains("/node_modules/") && !s.contains("/target/") && !s.contains("/build/") && !s.contains("/.idea/");
                        })
                        .filter(p -> filter.isEmpty() || p.toString().toLowerCase(java.util.Locale.ROOT).contains(filter))
                        .sorted((a, b) -> {
                            try {
                                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .limit(15)
                        .toList();
                for (Path p : recent) {
                    sb.append("ARQUIVO: ").append(repoDir.relativize(p)).append("\n");
                }
            } catch (Exception ignored) {
            }
            return sb.toString();
        }

        String branch = runGitCommand(repoDir, List.of("git", "rev-parse", "--abbrev-ref", "HEAD")).trim();
        sb.append("Branch atual: ").append(branch.isEmpty() ? "desconhecida" : branch).append("\n");

        String status = runGitCommand(repoDir, List.of("git", "status", "-s"));
        sb.append("\n[Arquivos Modificados / Working Tree (git status -s)]:\n");
        if (status.isBlank()) {
            sb.append("(Nenhuma alteração pendente no working tree)\n");
        } else {
            for (String line : status.split("\n")) {
                if (filter.isEmpty() || line.toLowerCase(java.util.Locale.ROOT).contains(filter)) {
                    sb.append(line).append("\n");
                    String cleaned = line.length() > 3 ? line.substring(3).trim().replace("\"", "") : "";
                    if (!cleaned.isEmpty()) {
                        sb.append("ARQUIVO: ").append(repoDir.resolve(cleaned)).append("\n");
                    }
                }
            }
        }

        String logOut = runGitCommand(repoDir, List.of("git", "log", "-n", "3", "--name-status", "--oneline"));
        if (!logOut.isBlank()) {
            sb.append("\n[Últimos 3 Commits (git log -n 3 --name-status)]:\n");
            for (String line : logOut.split("\n")) {
                if (filter.isEmpty() || line.toLowerCase(java.util.Locale.ROOT).contains(filter) || line.matches("^[0-9a-f]{7,}\\s+.*")) {
                    sb.append(line).append("\n");
                }
            }
        }

        if (includeDiff) {
            String diffOut = runGitCommand(repoDir, List.of("git", "diff", "--unified=2"));
            if (!diffOut.isBlank()) {
                sb.append("\n[Diff das Alterações (git diff)]:\n");
                sb.append(diffOut.length() > 4000 ? diffOut.substring(0, 4000) + "\n... [diff truncado]" : diffOut).append("\n");
            }
        }
        return sb.toString();
    }

    private String runGitCommand(Path dir, List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return output;
        } catch (Exception e) {
            return "";
        }
    }

    private String resolveTargetKb(Map<String, Object> args, String tenantId, String defaultKb) {
        Object projObj = args.get("project");
        if (projObj == null) {
            return defaultKb;
        }
        String projRef = projObj.toString().trim();
        if (projRef.isEmpty()) {
            return defaultKb;
        }
        if ("ALL".equalsIgnoreCase(projRef) || "*".equals(projRef)) {
            return "ALL";
        }
        if (projectRepository != null) {
            try {
                UUID id = UUID.fromString(projRef);
                if (projectRepository.findById(id).isPresent()) {
                    return id.toString();
                }
            } catch (IllegalArgumentException ignored) {
            }
            var projects = (tenantId != null && !tenantId.isBlank())
                    ? projectRepository.findByTenantId(tenantId)
                    : projectRepository.findAll();
            for (var p : projects) {
                if (p.getName().equalsIgnoreCase(projRef) || p.getRootPath().endsWith("/" + projRef)) {
                    return p.getId().toString();
                }
            }
            for (var p : projects) {
                if (p.getName().toLowerCase().contains(projRef.toLowerCase())) {
                    return p.getId().toString();
                }
            }
        }
        return projRef;
    }

    private String searchCode(Map<String, Object> args, String tenantId, String kb) {
        String query = (String) args.get("query");
        int limit = args.containsKey("limit") && args.get("limit") != null ? ((Number) args.get("limit")).intValue() : 8;
        if ("ALL".equalsIgnoreCase(kb) && projectRepository != null) {
            var projects = (tenantId != null && !tenantId.isBlank())
                    ? projectRepository.findByTenantId(tenantId)
                    : projectRepository.findAll();
            StringBuilder sb = new StringBuilder();
            int perProjectLimit = Math.max(3, limit / Math.max(1, projects.size()));
            for (var p : projects) {
                List<SearchResult> res = searchService.search(tenantId, p.getId().toString(), query, perProjectLimit);
                if (!res.isEmpty()) {
                    sb.append("=== PROJETO: ").append(p.getName()).append(" (ID: ").append(p.getId()).append(") ===\n");
                    sb.append(formatResults(res));
                }
            }
            return sb.length() == 0 ? "Nenhum resultado encontrado nos projetos." : sb.toString();
        }
        List<SearchResult> results = searchService.search(tenantId, kb, query, limit);
        return formatResults(results);
    }

    private String findDtoDefinition(Map<String, Object> args, String tenantId, String kb) {
        String className = (String) args.get("className");
        if ("ALL".equalsIgnoreCase(kb)) {
            return searchRepository.findDtoDefinitionGlobal(tenantId, className)
                    .map(r -> "ARQUIVO: " + r.path() + "\n" + r.content())
                    .orElse("Definição de " + className + " não encontrada.");
        }
        return searchRepository.findByClassName(tenantId, kb, className)
                .or(() -> searchRepository.findDtoDefinitionGlobal(tenantId, className))
                .map(r -> "ARQUIVO: " + r.path() + "\n" + r.content())
                .orElse("Definição de " + className + " não encontrada.");
    }

    private String findDtoUsages(Map<String, Object> args, String tenantId, String kb) {
        String className = (String) args.get("className");
        List<SearchResult> results = searchRepository.findUsagesByClassName(tenantId, kb, className);
        return results.isEmpty() ? "Nenhum uso encontrado para " + className + "." : formatResults(results);
    }

    private String findEndpoints(Map<String, Object> args, String tenantId, String kb) {
        String dtoName = (String) args.get("dtoName");
        List<SearchResult> results = searchRepository.findEndpointsUsingDto(tenantId, kb, dtoName);
        return results.isEmpty() ? "Nenhum endpoint usa " + dtoName + "." : formatResults(results);
    }

    private String findExternalUsages(Map<String, Object> args, String tenantId, String kb) {
        String dtoName = (String) args.get("dtoName");
        List<SearchResult> results = searchRepository.findUsagesInOtherKnowledgeBases(tenantId, kb, dtoName);
        return results.isEmpty() ? dtoName + " não é usado em outros sistemas." : formatResults(results);
    }

    private String analyzeBreaking(Map<String, Object> args, String tenantId, String kb) {
        String fieldName = (String) args.get("fieldName");
        String dtoName = (String) args.get("dtoName");
        String typeStr = (String) args.getOrDefault("changeType", "REMOVE");
        String targetStr = (String) args.getOrDefault("target", "FIELD");
        Object beforeState = args.get("beforeState");
        Object afterState = args.get("afterState");

        ChangeType changeType;
        try {
            changeType = ChangeType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            changeType = ChangeType.REMOVE;
        }

        ChangeTarget changeTarget;
        try {
            changeTarget = ChangeTarget.valueOf(targetStr.toUpperCase());
        } catch (Exception e) {
            changeTarget = ChangeTarget.FIELD;
        }

        ChangeSet change = new ChangeSet(changeTarget, changeType, fieldName, dtoName, beforeState, afterState);
        ImpactAnalysis impact = ImpactAnalysis.from(tenantId, kb, change, searchRepository);
        BreakingAnalysisResult result = breakingAnalyzer.analyze(change, impact);
        AlertResult alert = alertService.evaluateBreaking(result).orElse(null);

        // Registra decisão arquitetural na auditoria
        auditService.recordChangeDecision(tenantId, kb, change, result, alert);

        return """
                Campo: %s | DTO: %s
                Classificação: %s
                Motivo: %s
                Requer versionamento: %s
                Uso interno: %s | Quebra contrato HTTP: %s
                Alerta: %s
                """.formatted(
                fieldName, dtoName,
                result.breakingType(),
                result.reason(),
                result.requiresVersioning() ? "SIM" : "NÃO",
                impact.internalUsage() ? "SIM" : "NÃO",
                impact.breaksHttpContract() ? "SIM" : "NÃO",
                alert != null ? alert.level() + " — " + alert.message() : "nenhum"
        );
    }

    private String auditDto(Map<String, Object> args, String tenantId, String kb) {
        String dtoName = (String) args.get("dtoName");

        List<SearchResult> usages = searchRepository.findUsagesByClassName(tenantId, kb, dtoName);
        List<SearchResult> endpoints = searchRepository.findEndpointsUsingDto(tenantId, kb, dtoName);
        List<SearchResult> external = searchRepository.findUsagesInOtherKnowledgeBases(tenantId, kb, dtoName);
        String definition = searchRepository.findByClassName(tenantId, kb, dtoName)
                .map(SearchResult::content).orElse("");

        boolean contractDto = !endpoints.isEmpty() || !external.isEmpty();
        boolean hasValidation = definition.contains("@NotNull") || definition.contains("@NotBlank") || definition.contains("@Valid");
        String risk = contractDto && !hasValidation ? "ALTO" : contractDto || usages.size() > 3 ? "MÉDIO" : "BAIXO";

        List<String> recommendations = new ArrayList<>();
        if (contractDto) recommendations.add("Versione alterações incompatíveis e comunique os consumidores.");
        if (!hasValidation) recommendations.add("Adicione validações explícitas aos campos obrigatórios.");
        if (recommendations.isEmpty()) recommendations.add("Mantenha testes de contrato para este DTO.");

        DtoAuditResult audit = new DtoAuditResult(dtoName, risk, !external.isEmpty(), hasValidation, hasValidation,
                usages.size(), recommendations, contractDto);
        AlertResult alert = alertService.evaluate(audit).orElse(null);
        auditService.record(tenantId, kb, audit, alert);

        return """
                DTO: %s
                Risco: %s | Contrato externo: %s | Validações: %s
                Usos internos: %d | Endpoints: %d | Sistemas externos: %d
                Recomendações: %s
                Alerta: %s
                """.formatted(
                dtoName, risk,
                contractDto ? "SIM" : "NÃO",
                hasValidation ? "SIM" : "NÃO",
                usages.size(), endpoints.size(), external.size(),
                String.join("; ", recommendations),
                alert != null ? alert.level() + " — " + alert.message() : "nenhum"
        );
    }

    private String formatResults(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (SearchResult r : results) {
            sb.append("ARQUIVO: ").append(r.path()).append(" (score: ").append(String.format("%.2f", r.score())).append(")\n");
            sb.append(r.content(), 0, Math.min(500, r.content().length())).append("\n---\n");
        }
        return sb.toString();
    }

    private String readFile(Map<String, Object> args, String tenantId, String kb) {
        String rawPath = (String) args.get("path");
        if (rawPath == null || rawPath.isBlank()) {
            return "Erro: Parâmetro 'path' é obrigatório.";
        }

        Path projectRoot = resolveProjectPath(kb);
        Path target = Path.of(rawPath);
        if (!target.isAbsolute()) {
            target = projectRoot.resolve(target);
        }
        target = target.normalize().toAbsolutePath();

        if (!Files.exists(target) || Files.isDirectory(target)) {
            return "Arquivo não encontrado ou é um diretório: " + rawPath;
        }

        try {
            Path realTarget = target.toRealPath();
            Path realProjectRoot = Files.exists(projectRoot) ? projectRoot.toRealPath() : projectRoot;
            Path allowed = Path.of(allowedBasePath).normalize().toAbsolutePath();
            Path realAllowed = Files.exists(allowed) ? allowed.toRealPath() : allowed;

            if (!realTarget.startsWith(realProjectRoot) && !realTarget.startsWith(realAllowed)) {
                return "Acesso negado: O arquivo está fora do diretório do projeto: " + target;
            }

            List<String> lines = Files.readAllLines(realTarget, StandardCharsets.UTF_8);
            int totalLines = lines.size();

            int start = args.containsKey("startLine") && args.get("startLine") != null
                    ? Math.max(1, ((Number) args.get("startLine")).intValue())
                    : 1;
            int end = args.containsKey("endLine") && args.get("endLine") != null
                    ? Math.min(totalLines, ((Number) args.get("endLine")).intValue())
                    : Math.min(totalLines, start + 250);

            if (start > totalLines) {
                return "O arquivo possui apenas " + totalLines + " linhas.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("ARQUIVO: ").append(realTarget).append(" (linhas ").append(start).append(" a ").append(end).append(" de ").append(totalLines).append(")\n");
            for (int i = start - 1; i < end; i++) {
                sb.append(i + 1).append(": ").append(lines.get(i)).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Erro ao ler arquivo " + rawPath + ": " + e.getMessage();
        }
    }

    private static final java.util.Set<String> IGNORED_DIRS = java.util.Set.of(
            ".git", ".gradle", "build", "target", ".idea", ".run", ".github", ".sixth",
            "log", "logs", "node_modules", "dist", "out"
    );

    private String listFiles(Map<String, Object> args, String tenantId, String kb) {
        String subPath = args.containsKey("path") && args.get("path") != null ? (String) args.get("path") : "";
        int maxDepth = args.containsKey("maxDepth") && args.get("maxDepth") != null
                ? Math.min(5, Math.max(1, ((Number) args.get("maxDepth")).intValue()))
                : 4;

        Path projectRoot = resolveProjectPath(kb);
        Path target = projectRoot.resolve(subPath).normalize().toAbsolutePath();

        if (!Files.exists(target) || !Files.isDirectory(target)) {
            return "Diretório não encontrado: " + subPath;
        }

        try {
            Path realTarget = target.toRealPath();
            Path realProjectRoot = Files.exists(projectRoot) ? projectRoot.toRealPath() : projectRoot;
            Path allowed = Path.of(allowedBasePath).normalize().toAbsolutePath();
            Path realAllowed = Files.exists(allowed) ? allowed.toRealPath() : allowed;

            if (!realTarget.startsWith(realProjectRoot) && !realTarget.startsWith(realAllowed)) {
                return "Acesso negado: Diretório fora do projeto: " + target;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("ÁRVORE DE ARQUIVOS: ").append(target).append(" (profundidade: ").append(maxDepth).append(")\n");
            List<String> treeLines = new ArrayList<>();
            buildTree(target, maxDepth, 1, treeLines);
            for (String line : treeLines) {
                sb.append(line).append("\n");
            }
            if (treeLines.isEmpty()) {
                sb.append("(diretório vazio ou todos os arquivos estão em pastas ignoradas)\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Erro ao listar diretório " + subPath + ": " + e.getMessage();
        }
    }

    private void buildTree(Path current, int maxDepth, int currentDepth, List<String> lines) {
        if (lines.size() >= 80) return;

        try (var stream = Files.list(current)) {
            List<Path> entries = stream.sorted(java.util.Comparator.comparing((Path p) -> !Files.isDirectory(p))
                    .thenComparing(p -> p.getFileName().toString().toLowerCase())).toList();

            for (Path entry : entries) {
                if (lines.size() >= 80) {
                    lines.add("... (listagem truncada em 80 itens)");
                    return;
                }

                String name = entry.getFileName().toString();
                if (IGNORED_DIRS.contains(name) || name.startsWith(".")) {
                    continue;
                }

                if (Files.isDirectory(entry)) {
                    // Colapsa cadeias de diretórios de filho único (ex: br/com/mv/cpv)
                    Path curr = entry;
                    List<String> parts = new ArrayList<>();
                    parts.add(curr.getFileName().toString());
                    while (true) {
                        try (var subStream = Files.list(curr)) {
                            List<Path> validSubs = subStream.filter(p -> !IGNORED_DIRS.contains(p.getFileName().toString())
                                    && !p.getFileName().toString().startsWith(".")).toList();
                            if (validSubs.size() == 1 && Files.isDirectory(validSubs.get(0))) {
                                curr = validSubs.get(0);
                                parts.add(curr.getFileName().toString());
                            } else {
                                break;
                            }
                        } catch (Exception e) {
                            break;
                        }
                    }

                    String collapsedName = String.join("/", parts);
                    String indent = "  ".repeat(Math.max(0, currentDepth - 1));
                    lines.add(indent + "[DIR] " + collapsedName + "/");

                    if (currentDepth < maxDepth) {
                        buildTree(curr, maxDepth, currentDepth + 1, lines);
                    }
                } else {
                    String indent = "  ".repeat(Math.max(0, currentDepth - 1));
                    lines.add(indent + "[ARQ] " + name);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Path resolveProjectPath(String kb) {
        if (projectRepository != null && kb != null && !kb.isBlank()) {
            try {
                UUID id = UUID.fromString(kb);
                var project = projectRepository.findById(id);
                if (project.isPresent()) {
                    return Path.of(project.get().getRootPath()).toAbsolutePath().normalize();
                }
            } catch (Exception ignored) {
                for (var p : projectRepository.findAll()) {
                    if (p.getName().equalsIgnoreCase(kb) || p.getRootPath().endsWith("/" + kb)) {
                        return Path.of(p.getRootPath()).toAbsolutePath().normalize();
                    }
                }
            }
        }
        if (kb != null && !kb.isBlank() && !"ALL".equalsIgnoreCase(kb)) {
            Path candidate = Path.of(allowedBasePath).resolve(kb).toAbsolutePath().normalize();
            if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return Path.of(allowedBasePath).toAbsolutePath().normalize();
    }

    private String modifyFile(Map<String, Object> args, String tenantId, String kb) {
        String path = (String) args.get("path");
        String targetContent = (String) args.get("targetContent");
        String replacementContent = (String) args.get("replacementContent");
        String reason = (String) args.getOrDefault("reason", "");

        if (path == null || targetContent == null || replacementContent == null) {
            return "Erro: 'path', 'targetContent' e 'replacementContent' são parâmetros obrigatórios para modify_file.";
        }

        Path projectRoot = resolveProjectPath(kb);
        FileModificationResult result = codeModificationService.modifyFile(
                projectRoot, tenantId, kb, path, targetContent, replacementContent, reason
        );
        return formatModificationResult(result);
    }

    private String writeFile(Map<String, Object> args, String tenantId, String kb) {
        String path = (String) args.get("path");
        String content = (String) args.get("content");
        String reason = (String) args.getOrDefault("reason", "");

        if (path == null || content == null) {
            return "Erro: 'path' e 'content' são parâmetros obrigatórios para write_file.";
        }

        Path projectRoot = resolveProjectPath(kb);
        FileModificationResult result = codeModificationService.writeFile(
                projectRoot, tenantId, kb, path, content, reason
        );
        return formatModificationResult(result);
    }

    private String formatModificationResult(FileModificationResult result) {
        if (!result.success()) {
            return "FALHA NA MODIFICAÇÃO DE CÓDIGO:\n" +
                    "Arquivo: " + result.path() + "\n" +
                    "Erro: " + result.message();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("MODIFICAÇÃO DE CÓDIGO EXECUTADA COM SUCESSO:\n");
        sb.append("Status: ").append(result.status()).append("\n");
        sb.append("Arquivo: ").append(result.path()).append("\n");
        if (result.backupPath() != null) {
            sb.append("Backup criado em: ").append(result.backupPath()).append("\n");
        }
        sb.append("Mensagem: ").append(result.message()).append("\n");
        if (result.diff() != null && !result.diff().isBlank()) {
            sb.append("Diff aplicado:\n```diff\n").append(result.diff()).append("```\n");
        }
        return sb.toString();
    }
}
