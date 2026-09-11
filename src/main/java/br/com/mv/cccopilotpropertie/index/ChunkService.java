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

        if (isController(text) && text.length() <= 3000) {
            chunks.add(enrichControllerChunk(text));
            return chunks;
        }

        if (isService(text) && text.length() <= 3000) {
            chunks.add(enrichServiceChunk(text));
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
                chunks.add(enrichControllerChunk(block));
                continue;
            }

            if (isService(block)) {
                chunks.add(enrichServiceChunk(block));
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
}
