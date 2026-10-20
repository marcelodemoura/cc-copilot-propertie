package br.com.mv.cccopilotpropertie.index;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChunkService {

    private static final int FALLBACK_SIZE = 800;
    private static final int OVERLAP_SIZE = 150;

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile(
            "\\b(class|record|interface|enum)\\s+(\\w+)"
    );

    private static final Pattern DTO_NAME_PATTERN = Pattern.compile(
            "(?si).*\\b(class|record)\\s+\\w*(dto|request|response|command|query|vo)\\b.*"
    );

    private static final Pattern SERVICE_PATTERN = Pattern.compile(
            "(?si).*\\b(class|record)\\s+\\w+service\\b.*"
    );

    private static final Pattern CONTROLLER_PATTERN = Pattern.compile(
            "(?si).*\\b(class|record)\\s+\\w+controller\\b.*"
    );

    private static final List<String> VALIDATION_NAMES = List.of(
            "NotNull", "NotBlank", "NotEmpty", "Size", "Min", "Max", "Pattern", "Email", "Valid", "Positive", "Negative"
    );

    private static final List<String> LOMBOK_ANNOTATIONS = List.of(
            "@Data", "@Value", "@Getter", "@Setter", "@Builder", "@NoArgsConstructor", "@AllArgsConstructor", "@ToString", "@EqualsAndHashCode"
    );

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();

        // 🔒 REGRA DEFINITIVA: DTO nunca é fragmentado
        if (isDto(text)) {
            chunks.add(enrichDtoChunk(text));
            return chunks;
        }

        if (isController(text)) {
            if (text.length() <= 3000) {
                chunks.add(enrichControllerChunk(text));
            } else {
                chunks.addAll(chunkJavaClassByMethods(text, "CONTROLLER / API"));
            }
            return chunks;
        }

        if (isService(text)) {
            if (text.length() <= 3000) {
                chunks.add(enrichServiceChunk(text));
            } else {
                chunks.addAll(chunkJavaClassByMethods(text, "SERVICE"));
            }
            return chunks;
        }

        String[] classBlocks = text.split(
                "(?=\\b(public\\s+|protected\\s+|private\\s+|abstract\\s+|final\\s+)*(class|record|interface|enum)\\s+)"
        );

        for (String block : classBlocks) {
            if (block.trim().length() < 80) continue;

            if (isDto(block)) {
                chunks.add(enrichDtoChunk(block));
                continue;
            }

            if (isController(block)) {
                if (block.length() <= 3000) {
                    chunks.add(enrichControllerChunk(block));
                } else {
                    chunks.addAll(chunkJavaClassByMethods(block, "CONTROLLER / API"));
                }
                continue;
            }

            if (isService(block)) {
                if (block.length() <= 3000) {
                    chunks.add(enrichServiceChunk(block));
                } else {
                    chunks.addAll(chunkJavaClassByMethods(block, "SERVICE"));
                }
                continue;
            }

            if (block.length() > 3000 && looksLikeJavaClass(block)) {
                chunks.addAll(chunkJavaClassByMethods(block, "CLASSE"));
                continue;
            }

            chunks.addAll(chunkFallback(block));
        }

        if (chunks.isEmpty() && !text.isBlank()) {
            chunks.addAll(chunkFallback(text));
        }

        return chunks;
    }

    /* ===================== SERVICE ===================== */

    public boolean isService(String block) {
        if (block == null) return false;
        return block.contains("@Service") || SERVICE_PATTERN.matcher(block).find();
    }

    private String enrichServiceChunk(String block) {
        String className = extractClassName(block);

        return """
                TIPO: SERVICE
                NOME: %s

                RESPONSABILIDADE:
                - Executa lógica de negócio
                - Pode persistir dados
                - Pode publicar eventos ou mensagens

                === CÓDIGO ===
                %s
                """.formatted(className, block);
    }

    /* ===================== CONTROLLER ===================== */

    public boolean isController(String block) {
        if (block == null) return false;
        return block.contains("@RestController")
                || block.contains("@Controller")
                || CONTROLLER_PATTERN.matcher(block).find();
    }

    private String enrichControllerChunk(String block) {
        String className = extractClassName(block);

        return """
                TIPO: CONTROLLER / API
                NOME: %s

                RESPONSABILIDADE:
                - Expõe endpoints HTTP REST
                - Recebe e valida requisições de entrada
                - Retorna respostas para clientes externos

                === CÓDIGO ===
                %s
                """.formatted(className, block);
    }

    /* ===================== DTO ===================== */

    public boolean isDto(String block) {
        if (block == null || block.isBlank()) {
            return false;
        }

        // Não é DTO se for explicitamente um Service, Controller, Entity ou Repository
        if (block.contains("@RestController") || block.contains("@Controller")
                || block.contains("@Repository") || block.contains("@Entity")
                || block.contains("@Configuration") || block.contains("@Table")) {
            return false;
        }

        if (DTO_NAME_PATTERN.matcher(block).find()) {
            return true;
        }

        // Se tem anotações de validação e não é service
        if (!isService(block)) {
            for (String valName : VALIDATION_NAMES) {
                if (block.contains("@" + valName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String enrichDtoChunk(String block) {
        String className = extractClassName(block);
        String validations = extractValidations(block);
        String lombokInfo = extractLombokInfo(block);

        String lombokSection = lombokInfo.isBlank() ? "" : "ANOTAÇÕES LOMBOK:\n" + lombokInfo + "\n\n";

        return """
                TIPO: DTO DE ENTRADA
                NOME: %s

                FINALIDADE:
                - Representa dados recebidos por APIs
                - Usado em cadastros ou atualizações

                %sCAMPOS OBRIGATÓRIOS:
                %s

                REGRA:
                - Campos marcados com validações são validados na entrada HTTP

                === CÓDIGO ===
                %s
                """.formatted(className, lombokSection, validations, block);
    }

    String extractLombokInfo(String block) {
        List<String> found = new ArrayList<>();
        for (String ann : LOMBOK_ANNOTATIONS) {
            if (block.contains(ann)) {
                found.add(ann);
            }
        }
        if (found.isEmpty()) {
            return "";
        }
        return String.join(", ", found);
    }

    String extractValidations(String block) {
        StringBuilder sb = new StringBuilder();
        Set<String> processedFields = new HashSet<>();

        String valRegex = "@(" + String.join("|", VALIDATION_NAMES) + ")(?:\\([^)]*\\))?"
                + "(?:\\s*@[\\w]+(?:\\([^)]*\\))?)*"
                + "\\s+(?:(?:public|protected|private|final|transient|volatile)\\s+)*"
                + "[\\w\\[\\]]+(?:<[^>]+>)?"
                + "\\s+(\\w+)\\s*[,;)=]";

        Pattern pattern = Pattern.compile(valRegex);
        Matcher matcher = pattern.matcher(block);

        while (matcher.find()) {
            String annotation = matcher.group(1);
            String field = matcher.group(2);

            String key = field + ":@" + annotation;
            if (processedFields.add(key)) {
                sb.append("- ")
                        .append(field)
                        .append(" (@")
                        .append(annotation)
                        .append(")\n");
            }
        }

        return sb.length() == 0
                ? "Nenhuma validação explícita encontrada"
                : sb.toString();
    }

    /* ===================== FALLBACK ===================== */

    List<String> chunkFallback(String block) {
        if (block == null || block.isBlank()) {
            return List.of();
        }

        if (block.length() <= FALLBACK_SIZE) {
            return List.of(block);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int step = Math.max(1, FALLBACK_SIZE - OVERLAP_SIZE);

        while (start < block.length()) {
            int end = Math.min(start + FALLBACK_SIZE, block.length());
            chunks.add(block.substring(start, end));
            if (end >= block.length()) {
                break;
            }
            start += step;
        }

        return chunks;
    }

    /* ===================== UTIL ===================== */

    String extractClassName(String block) {
        Matcher m = CLASS_NAME_PATTERN.matcher(block);
        return m.find() ? m.group(2) : "CLASSE_DESCONHECIDA";
    }

    public List<String> chunkJavaClassByMethods(String block, String type) {
        if (block == null || block.isBlank()) {
            return List.of();
        }

        String className = extractClassName(block);
        String classHeader = extractClassHeader(block);
        List<String> methodBlocks = extractMethodBlocks(block);

        if (methodBlocks.isEmpty()) {
            return chunkFallback(block);
        }

        List<String> chunks = new ArrayList<>();
        for (String methodCode : methodBlocks) {
            String methodName = extractMethodName(methodCode);
            String chunk = """
                    TIPO: %s
                    CLASSE: %s
                    MÉTODO: %s

                    === CONTEXTO DA CLASSE ===
                    %s

                    === CÓDIGO DO MÉTODO ===
                    %s
                    """.formatted(type, className, methodName, classHeader, methodCode.trim());
            chunks.add(chunk);
        }
        return chunks;
    }

    boolean looksLikeJavaClass(String block) {
        if (block == null) return false;
        return CLASS_NAME_PATTERN.matcher(block).find();
    }

    String extractClassHeader(String block) {
        Matcher m = CLASS_NAME_PATTERN.matcher(block);
        if (!m.find()) {
            return "";
        }
        int classIdx = m.start();
        int openBrace = block.indexOf('{', classIdx);
        if (openBrace == -1) {
            return block.substring(0, Math.min(block.length(), 500)).trim();
        }

        String beforeClass = block.substring(0, openBrace + 1).trim();

        // Tenta incluir campos e dependências antes do primeiro método
        String inside = block.substring(openBrace + 1);
        Pattern methodStartPattern = Pattern.compile(
                "(?m)^\\s*(?:(?:public|protected|private|static|final|synchronized|abstract|default)\\s+)+[\\w<>\\[\\],\\s]+\\s+(\\w+)\\s*\\("
        );
        Matcher mm = methodStartPattern.matcher(inside);
        if (mm.find()) {
            String fields = inside.substring(0, mm.start()).trim();
            if (!fields.isBlank() && fields.length() < 1000) {
                return beforeClass + "\n    " + fields;
            }
        }

        return beforeClass;
    }

    List<String> extractMethodBlocks(String block) {
        List<String> methods = new ArrayList<>();
        Matcher m = CLASS_NAME_PATTERN.matcher(block);
        if (!m.find()) {
            return methods;
        }
        int openBrace = block.indexOf('{', m.start());
        if (openBrace == -1) {
            return methods;
        }

        int depth = 1;
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int currentMethodStart = -1;

        int len = block.length();
        for (int i = openBrace + 1; i < len; i++) {
            char c = block.charAt(i);
            char next = (i + 1 < len) ? block.charAt(i + 1) : '\0';

            // Comentários
            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (!inString && !inChar) {
                if (c == '/' && next == '/') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (c == '/' && next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }

            // Strings e Caracteres Literais
            if (!inChar && c == '"' && (i == 0 || block.charAt(i - 1) != '\\')) {
                inString = !inString;
                continue;
            }
            if (!inString && c == '\'' && (i == 0 || block.charAt(i - 1) != '\\')) {
                inChar = !inChar;
                continue;
            }
            if (inString || inChar) {
                continue;
            }

            // Balanço de chaves
            if (c == '{') {
                if (depth == 1) {
                    currentMethodStart = findMethodDeclarationStart(block, openBrace + 1, i);
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 1 && currentMethodStart != -1) {
                    String methodText = block.substring(currentMethodStart, i + 1).trim();
                    if (methodText.length() > 20) {
                        methods.add(methodText);
                    }
                    currentMethodStart = -1;
                }
            }
        }

        return methods;
    }

    private int findMethodDeclarationStart(String block, int classBodyStart, int braceIndex) {
        int idx = braceIndex - 1;
        while (idx >= classBodyStart) {
            char c = block.charAt(idx);
            if (c == ';' || c == '}') {
                return idx + 1;
            }
            idx--;
        }
        return Math.max(classBodyStart, idx + 1);
    }

    String extractMethodName(String methodCode) {
        Pattern pattern = Pattern.compile(
                "(?:(?:public|protected|private|static|final|synchronized|abstract|default)\\s+)*[\\w<>\\[\\],\\s]+\\s+(\\w+)\\s*\\("
        );
        Matcher m = pattern.matcher(methodCode);
        if (m.find()) {
            return m.group(1);
        }
        return "metodo";
    }
}
