package br.com.mv.cccopilotpropertie.copilot.code;

import br.com.mv.cccopilotpropertie.index.IndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

@Service
public class CodeModificationService {

    private static final Logger log = LoggerFactory.getLogger(CodeModificationService.class);

    private final boolean enabled;
    private final String allowedBasePath;
    private final IndexService indexService;

    private static final Set<String> FORBIDDEN_DIRS = Set.of(
            ".git", ".gradle", "build", "target", "node_modules", ".idea", ".run", ".github", ".sixth"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "java", "kt", "groovy", "properties", "yaml", "yml", "xml", "json", "sql", "md", "txt", "gradle", "html", "css", "js", "ts"
    );

    public CodeModificationService() {
        this(true, ".", null);
    }

    public CodeModificationService(boolean enabled, String allowedBasePath) {
        this(enabled, allowedBasePath, null);
    }

    @Autowired
    public CodeModificationService(
            @Value("${copilot.code-modification.enabled:true}") boolean enabled,
            @Value("${indexer.base-path:.}") String allowedBasePath,
            @Autowired(required = false) IndexService indexService
    ) {
        this.enabled = enabled;
        this.allowedBasePath = allowedBasePath != null ? allowedBasePath : ".";
        this.indexService = indexService;
    }

    public FileModificationResult modifyFile(
            Path projectRoot,
            String tenantId,
            String knowledgeBase,
            String relativePath,
            String targetContent,
            String replacementContent,
            String reason
    ) {
        if (!enabled) {
            return FileModificationResult.failure(relativePath, "Modificação de código está desabilitada no servidor.");
        }

        if (relativePath == null || relativePath.isBlank()) {
            return FileModificationResult.failure("", "O caminho do arquivo não pode ser vazio.");
        }

        if (targetContent == null || targetContent.isEmpty()) {
            return FileModificationResult.failure(relativePath, "O parâmetro 'targetContent' não pode ser vazio.");
        }

        String safeReplacement = replacementContent != null ? replacementContent : "";

        try {
            Path targetFile = validateAndResolvePath(projectRoot, relativePath);

            if (!Files.exists(targetFile) || Files.isDirectory(targetFile)) {
                return FileModificationResult.failure(relativePath, "Arquivo não encontrado ou é um diretório: " + relativePath);
            }

            String originalContent = Files.readString(targetFile, StandardCharsets.UTF_8);

            if (!originalContent.contains(targetContent)) {
                return FileModificationResult.failure(
                        relativePath,
                        "O trecho de código alvo ('targetContent') não foi encontrado no arquivo. " +
                        "Certifique-se de ler o arquivo atualizado com 'read_file' antes de aplicar a alteração."
                );
            }

            // Aplica a substituição em memória
            String updatedContent = originalContent.replace(targetContent, safeReplacement);

            // Validação sintática antes de qualquer persistência ou backup
            String syntaxError = validateSyntax(relativePath, updatedContent);
            if (syntaxError != null) {
                log.warn("Rejeitando modificação em {} devido a erro sintático: {}", relativePath, syntaxError);
                return FileModificationResult.failure(relativePath, "Validação sintática falhou: " + syntaxError);
            }

            // Cria cópia de segurança (.bak)
            Path backupPath = createBackup(targetFile);

            // Grava no disco
            Files.writeString(targetFile, updatedContent, StandardCharsets.UTF_8);

            // Atualiza os embeddings no pgvector incrementalmente se disponível
            if (indexService != null) {
                indexService.reindexSingleFile(tenantId, knowledgeBase, targetFile);
            }

            String diff = generateDiff(relativePath, targetContent, safeReplacement);
            String msg = "Código alterado com sucesso." + (reason != null && !reason.isBlank() ? " Motivo: " + reason : "");
            log.info("Arquivo modificado: {} no projeto {} (backup: {})", relativePath, projectRoot, backupPath);

            return FileModificationResult.success(relativePath, diff, backupPath.toString(), msg);

        } catch (SecurityException se) {
            log.warn("Violação de segurança ao tentar modificar {}: {}", relativePath, se.getMessage());
            return FileModificationResult.failure(relativePath, se.getMessage());
        } catch (Exception e) {
            log.error("Erro ao modificar arquivo {}: {}", relativePath, e.getMessage(), e);
            return FileModificationResult.failure(relativePath, "Erro interno ao modificar arquivo: " + e.getMessage());
        }
    }

    public FileModificationResult writeFile(
            Path projectRoot,
            String tenantId,
            String knowledgeBase,
            String relativePath,
            String content,
            String reason
    ) {
        if (!enabled) {
            return FileModificationResult.failure(relativePath, "Modificação de código está desabilitada no servidor.");
        }

        if (relativePath == null || relativePath.isBlank()) {
            return FileModificationResult.failure("", "O caminho do arquivo não pode ser vazio.");
        }

        if (content == null) {
            return FileModificationResult.failure(relativePath, "O conteúdo do arquivo não pode ser nulo.");
        }

        try {
            Path targetFile = validateAndResolvePath(projectRoot, relativePath);

            // Validação sintática antes de qualquer persistência ou backup
            String syntaxError = validateSyntax(relativePath, content);
            if (syntaxError != null) {
                log.warn("Rejeitando gravação em {} devido a erro sintático: {}", relativePath, syntaxError);
                return FileModificationResult.failure(relativePath, "Validação sintática falhou: " + syntaxError);
            }

            boolean existed = Files.exists(targetFile);
            String backupPathStr = null;

            if (existed) {
                if (Files.isDirectory(targetFile)) {
                    return FileModificationResult.failure(relativePath, "O caminho especificado é um diretório existente.");
                }
                Path backup = createBackup(targetFile);
                backupPathStr = backup.toString();
            } else if (targetFile.getParent() != null && !Files.exists(targetFile.getParent())) {
                Files.createDirectories(targetFile.getParent());
            }

            Files.writeString(targetFile, content, StandardCharsets.UTF_8);

            if (indexService != null) {
                indexService.reindexSingleFile(tenantId, knowledgeBase, targetFile);
            }

            String diff = generateDiff(relativePath, existed ? "(conteúdo anterior)" : "(arquivo novo)", content);
            String msg = existed ? "Arquivo sobrescrito com sucesso." : "Arquivo criado com sucesso.";
            if (reason != null && !reason.isBlank()) {
                msg += " Motivo: " + reason;
            }

            log.info("Arquivo gravado: {} no projeto {} (novo={})", relativePath, projectRoot, !existed);
            return existed
                    ? FileModificationResult.success(relativePath, diff, backupPathStr, msg)
                    : FileModificationResult.created(relativePath, diff, msg);

        } catch (SecurityException se) {
            log.warn("Violação de segurança ao gravar {}: {}", relativePath, se.getMessage());
            return FileModificationResult.failure(relativePath, se.getMessage());
        } catch (Exception e) {
            log.error("Erro ao gravar arquivo {}: {}", relativePath, e.getMessage(), e);
            return FileModificationResult.failure(relativePath, "Erro interno ao gravar arquivo: " + e.getMessage());
        }
    }

    public FileModificationResult restoreBackup(Path projectRoot, String relativePath) {
        try {
            Path targetFile = validateAndResolvePath(projectRoot, relativePath);
            Path backupFile = targetFile.resolveSibling(targetFile.getFileName().toString() + ".bak");

            if (!Files.exists(backupFile)) {
                return FileModificationResult.failure(relativePath, "Nenhum arquivo de backup (.bak) encontrado para restauração.");
            }

            Files.copy(backupFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(backupFile);

            log.info("Backup restaurado para o arquivo {}", relativePath);
            return FileModificationResult.restored(relativePath, "Backup restaurado com sucesso. Arquivo original recuperado.");
        } catch (Exception e) {
            return FileModificationResult.failure(relativePath, "Falha ao restaurar backup: " + e.getMessage());
        }
    }

    private Path validateAndResolvePath(Path projectRoot, String relativePath) throws IOException {
        Path rootAbs = (projectRoot != null ? projectRoot : Path.of(allowedBasePath)).toAbsolutePath().normalize();
        Path allowedAbs = Path.of(allowedBasePath).toAbsolutePath().normalize();

        Path resolved = rootAbs.resolve(relativePath).normalize().toAbsolutePath();

        if (!resolved.startsWith(rootAbs) && !resolved.startsWith(allowedAbs)) {
            throw new SecurityException("Acesso negado: Caminho fora do diretório do projeto: " + relativePath);
        }

        // Validação de diretórios proibidos
        for (Path part : resolved) {
            String pName = part.toString().toLowerCase(Locale.ROOT);
            if (FORBIDDEN_DIRS.contains(pName)) {
                throw new SecurityException("Acesso negado: Tentativa de modificar pasta protegida do sistema: " + pName);
            }
        }

        // Validação de arquivo protegido
        String fileName = resolved.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.startsWith(".env") || fileName.equals(".bashrc") || fileName.equals(".zshrc")) {
            throw new SecurityException("Acesso negado: Arquivo protegido do sistema: " + fileName);
        }

        // Validação de extensão permitida
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < fileName.length() - 1) {
            String ext = fileName.substring(dotIdx + 1);
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                throw new SecurityException("Acesso negado: Extensão '." + ext + "' não permitida para edição automatizada.");
            }
        }

        // Validação de symlink caso o arquivo já exista
        if (Files.exists(resolved)) {
            Path realPath = resolved.toRealPath();
            if (!realPath.startsWith(rootAbs) && !realPath.startsWith(allowedAbs)) {
                throw new SecurityException("Acesso negado: Link simbólico aponta para fora do projeto.");
            }
        }

        return resolved;
    }

    private Path createBackup(Path file) throws IOException {
        Path backup = file.resolveSibling(file.getFileName().toString() + ".bak");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }

    private String generateDiff(String relativePath, String originalSnippet, String newSnippet) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- a/").append(relativePath).append("\n");
        sb.append("+++ b/").append(relativePath).append("\n");
        sb.append("@@ Modificação @@\n");
        for (String line : originalSnippet.split("\n", -1)) {
            sb.append("- ").append(line).append("\n");
        }
        for (String line : newSnippet.split("\n", -1)) {
            sb.append("+ ").append(line).append("\n");
        }
        return sb.toString();
    }

    public String validateSyntax(String relativePath, String content) {
        if (relativePath == null || content == null) {
            return null;
        }
        String lower = relativePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) {
            return validateJavaSyntax(content);
        } else if (lower.endsWith(".json")) {
            return validateJsonSyntax(content);
        }
        return null;
    }

    public String validateJavaSyntax(String code) {
        if (code == null || code.isBlank()) {
            return "O arquivo Java não pode estar vazio.";
        }

        java.util.ArrayDeque<Delimiter> stack = new java.util.ArrayDeque<>();
        int len = code.length();
        int line = 1;
        int col = 0;

        int i = 0;
        while (i < len) {
            char c = code.charAt(i);
            col++;

            if (c == '\n') {
                line++;
                col = 0;
                i++;
                continue;
            }

            // 1. Bloco de texto multilinhas: """
            if (i + 2 < len && c == '"' && code.charAt(i + 1) == '"' && code.charAt(i + 2) == '"') {
                int startLine = line;
                i += 3;
                col += 2;
                boolean closed = false;
                while (i < len) {
                    if (code.charAt(i) == '\n') {
                        line++;
                        col = 0;
                    } else {
                        col++;
                    }
                    if (code.charAt(i) == '\\') {
                        i += 2;
                        col++;
                        continue;
                    }
                    if (i + 2 < len && code.charAt(i) == '"' && code.charAt(i + 1) == '"' && code.charAt(i + 2) == '"') {
                        i += 3;
                        col += 2;
                        closed = true;
                        break;
                    }
                    i++;
                }
                if (!closed) {
                    return "Bloco de texto multilinhas (\"\"\") aberto na linha " + startLine + " não foi fechado.";
                }
                continue;
            }

            // 2. String literal: "..."
            if (c == '"') {
                int startLine = line;
                i++;
                boolean closed = false;
                while (i < len) {
                    char sc = code.charAt(i);
                    col++;
                    if (sc == '\n') {
                        return "String literal não fechada na linha " + startLine + ".";
                    }
                    if (sc == '\\') {
                        i += 2;
                        col++;
                        continue;
                    }
                    if (sc == '"') {
                        closed = true;
                        i++;
                        break;
                    }
                    i++;
                }
                if (!closed) {
                    return "String literal aberta na linha " + startLine + " não foi fechada.";
                }
                continue;
            }

            // 3. Char literal: '...'
            if (c == '\'') {
                int startLine = line;
                i++;
                boolean closed = false;
                while (i < len) {
                    char cc = code.charAt(i);
                    col++;
                    if (cc == '\n') {
                        return "Caractere literal não fechado na linha " + startLine + ".";
                    }
                    if (cc == '\\') {
                        i += 2;
                        col++;
                        continue;
                    }
                    if (cc == '\'') {
                        closed = true;
                        i++;
                        break;
                    }
                    i++;
                }
                if (!closed) {
                    return "Caractere literal aberto na linha " + startLine + " não foi fechado.";
                }
                continue;
            }

            // 4. Comentário de linha única: //
            if (c == '/' && i + 1 < len && code.charAt(i + 1) == '/') {
                i += 2;
                col++;
                while (i < len && code.charAt(i) != '\n') {
                    i++;
                    col++;
                }
                continue;
            }

            // 5. Comentário de bloco: /* ... */
            if (c == '/' && i + 1 < len && code.charAt(i + 1) == '*') {
                int startLine = line;
                i += 2;
                col++;
                boolean closed = false;
                while (i + 1 < len) {
                    if (code.charAt(i) == '\n') {
                        line++;
                        col = 0;
                    } else {
                        col++;
                    }
                    if (code.charAt(i) == '*' && code.charAt(i + 1) == '/') {
                        i += 2;
                        col++;
                        closed = true;
                        break;
                    }
                    i++;
                }
                if (!closed) {
                    return "Comentário de bloco /* aberto na linha " + startLine + " não foi fechado.";
                }
                continue;
            }

            // 6. Delimitadores { }, ( ), [ ]
            if (c == '{' || c == '(' || c == '[') {
                stack.push(new Delimiter(c, line, col));
            } else if (c == '}' || c == ')' || c == ']') {
                if (stack.isEmpty()) {
                    return "Fechamento inesperado de '" + c + "' na linha " + line + ", coluna " + col + ".";
                }
                Delimiter open = stack.pop();
                if (!matches(open.type, c)) {
                    return "Delimitador incompatível na linha " + line + ", coluna " + col +
                            ": esperado fechamento para '" + open.type + "' (aberto na linha " + open.line + "), mas encontrado '" + c + "'.";
                }
            }

            i++;
        }

        if (!stack.isEmpty()) {
            Delimiter unclosed = stack.peek();
            return "Delimitador '" + unclosed.type + "' aberto na linha " + unclosed.line +
                    ", coluna " + unclosed.col + " não foi fechado.";
        }

        return null;
    }

    public String validateJsonSyntax(String json) {
        if (json == null || json.isBlank()) {
            return "O arquivo JSON não pode estar vazio.";
        }

        java.util.ArrayDeque<Delimiter> stack = new java.util.ArrayDeque<>();
        int len = json.length();
        int line = 1;
        int col = 0;
        int i = 0;

        while (i < len) {
            char c = json.charAt(i);
            col++;

            if (c == '\n') {
                line++;
                col = 0;
                i++;
                continue;
            }

            if (c == '"') {
                int startLine = line;
                i++;
                boolean closed = false;
                while (i < len) {
                    char sc = json.charAt(i);
                    col++;
                    if (sc == '\\') {
                        i += 2;
                        col++;
                        continue;
                    }
                    if (sc == '"') {
                        closed = true;
                        i++;
                        break;
                    }
                    i++;
                }
                if (!closed) {
                    return "String JSON não fechada na linha " + startLine + ".";
                }
                continue;
            }

            if (c == '{' || c == '[') {
                stack.push(new Delimiter(c, line, col));
            } else if (c == '}' || c == ']') {
                if (stack.isEmpty()) {
                    return "Fechamento inesperado de '" + c + "' no JSON na linha " + line + ".";
                }
                Delimiter open = stack.pop();
                if (!matches(open.type, c)) {
                    return "Delimitador incompatível no JSON na linha " + line +
                            ": esperado '" + (open.type == '{' ? '}' : ']') + "' mas encontrado '" + c + "'.";
                }
            }
            i++;
        }

        if (!stack.isEmpty()) {
            Delimiter unclosed = stack.peek();
            return "Estrutura JSON não finalizada: '" + unclosed.type + "' aberto na linha " + unclosed.line + " não foi fechado.";
        }

        return null;
    }

    private static boolean matches(char open, char close) {
        return (open == '{' && close == '}') ||
               (open == '(' && close == ')') ||
               (open == '[' && close == ']');
    }

    private record Delimiter(char type, int line, int col) {}
}
