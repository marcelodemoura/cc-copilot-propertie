package br.com.mv.cccopilotpropertie.index;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChunkService {

    private static final int FALLBACK_SIZE = 500;

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();

        // 🔒 REGRA DEFINITIVA: DTO nunca é fragmentado
        if (isDto(text)) {
            chunks.add(enrichDtoChunk(text));
            return chunks;
        }

        String[] classBlocks = text.split(
                "(?=\\b(public\\s+)?(class|record|interface)\\s+)"
        );

        for (String block : classBlocks) {

            if (block.trim().length() < 80) continue;

            if (isService(block)) {
                chunks.add(enrichServiceChunk(block));
                continue;
            }

            chunks.addAll(chunkFallback(block));
        }

        return chunks;
    }
    /* ===================== SERVICE ===================== */

    private boolean isService(String block) {
        return block.contains("@Service")
                || block.matches("(?s).*class\\s+\\w+Service\\b.*");
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

    /* ===================== DTO ===================== */

    private boolean isDto(String block) {
        return block.matches("(?s).*class\\s+\\w+DTO\\b.*")
                || block.contains("@NotNull")
                || block.contains("@NotBlank");
    }

    private String enrichDtoChunk(String block) {
        String className = extractClassName(block);
        String validations = extractValidations(block);

        return """
                TIPO: DTO DE ENTRADA
                NOME: %s
                
                FINALIDADE:
                - Representa dados recebidos por APIs
                - Usado em cadastros ou atualizações
                
                CAMPOS OBRIGATÓRIOS:
                %s
                
                REGRA:
                - Campos marcados com @NotNull ou @NotBlank são obrigatórios
                
                === CÓDIGO ===
                %s
                """.formatted(className, validations, block);
    }

    private String extractValidations(String block) {
        StringBuilder sb = new StringBuilder();

        Pattern p = Pattern.compile(
                "@(NotNull|NotBlank)\\s*\\n\\s*private\\s+[\\w<>]+\\s+(\\w+);"
        );
        Matcher m = p.matcher(block);

        while (m.find()) {
            sb.append("- ")
                    .append(m.group(2))
                    .append(" (@")
                    .append(m.group(1))
                    .append(")\n");
        }

        return sb.length() == 0
                ? "Nenhuma validação explícita encontrada"
                : sb.toString();
    }

    /* ===================== FALLBACK ===================== */

    private List<String> chunkFallback(String block) {
        List<String> chunks = new ArrayList<>();

        for (int i = 0; i < block.length(); i += FALLBACK_SIZE) {
            chunks.add(
                    block.substring(i, Math.min(block.length(), i + FALLBACK_SIZE))
            );
        }
        return chunks;
    }

    /* ===================== UTIL ===================== */

    private String extractClassName(String block) {
        Matcher m = Pattern.compile(
                "(public\\s+)?(class|record|interface)\\s+(\\w+)"
        ).matcher(block);

        return m.find() ? m.group(3) : "CLASSE_DESCONHECIDA";
    }
}