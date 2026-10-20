package br.com.mv.cccopilotpropertie.copilot.agent;

import br.com.mv.cccopilotpropertie.copilot.alert.AlertService;
import br.com.mv.cccopilotpropertie.copilot.audit.AuditService;
import br.com.mv.cccopilotpropertie.copilot.breaking.BreakingChangeAnalyzer;
import br.com.mv.cccopilotpropertie.copilot.code.CodeModificationService;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hibernate.validator.internal.util.Contracts.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AgentToolExecutorCodeModificationTest {

    @TempDir
    Path tempDir;

    private AgentToolExecutor executor;

    @BeforeEach
    void setUp() {
        CodeModificationService codeModService = new CodeModificationService(true, tempDir.toString(), null);
        executor = new AgentToolExecutor(
                mock(SearchService.class),
                mock(SearchRepository.class),
                new BreakingChangeAnalyzer(),
                new AlertService(),
                mock(AuditService.class),
                null,
                tempDir.toString(),
                codeModService
        );
    }

    @Test
    void shouldExecuteModifyFileTool() throws IOException {
        Path file = tempDir.resolve("PedidoDTO.java");
        Files.writeString(file, "public class PedidoDTO { private Long id; }");

        String argsJson = """
                {
                    "path": "PedidoDTO.java",
                    "targetContent": "private Long id;",
                    "replacementContent": "private UUID id;",
                    "reason": "Migração para UUID"
                }
                """;

        String result = executor.execute("modify_file", argsJson, "tenant-1", "kb-1");

        assertNotNull(result);
        assertTrue(result.contains("MODIFICAÇÃO DE CÓDIGO EXECUTADA COM SUCESSO"));
        assertTrue(result.contains("PedidoDTO.java"));
        assertTrue(result.contains("Diff aplicado"));

        String updated = Files.readString(file);
        assertTrue(updated.contains("private UUID id;"));
    }

    @Test
    void shouldExecuteWriteFileTool() throws IOException {
        String argsJson = """
                {
                    "path": "src/main/java/br/com/mv/NovaEntidade.java",
                    "content": "public class NovaEntidade {}",
                    "reason": "Nova classe solicitada"
                }
                """;

        String result = executor.execute("write_file", argsJson, "tenant-1", "kb-1");

        assertNotNull(result);
        assertTrue(result.contains("MODIFICAÇÃO DE CÓDIGO EXECUTADA COM SUCESSO"));
        assertTrue(result.contains("NovaEntidade.java"));

        Path created = tempDir.resolve("src/main/java/br/com/mv/NovaEntidade.java");
        assertTrue(Files.exists(created));
        assertEquals("public class NovaEntidade {}", Files.readString(created));
    }

    @Test
    void shouldReadAndModifyAcrossProjects() throws IOException {
        Path projectA = tempDir.resolve("projeto-a");
        Path projectB = tempDir.resolve("projeto-b");
        Files.createDirectories(projectA);
        Files.createDirectories(projectB);

        Files.writeString(projectA.resolve("ContratoDTO.java"), "public record ContratoDTO(String codigo) {}");
        Files.writeString(projectB.resolve("ClienteDTO.java"), "public record ClienteDTO(Long id) {}");

        String readArgs = """
                {
                    "path": "ContratoDTO.java",
                    "project": "projeto-a"
                }
                """;
        String readResult = executor.execute("read_file", readArgs, "tenant-1", "projeto-b");
        assertTrue(readResult.contains("public record ContratoDTO(String codigo) {}"));

        String modifyArgs = """
                {
                    "path": "ClienteDTO.java",
                    "targetContent": "Long id",
                    "replacementContent": "String codigo",
                    "reason": "Sincronizando contrato com projeto-a",
                    "project": "projeto-b"
                }
                """;
        String modResult = executor.execute("modify_file", modifyArgs, "tenant-1", "projeto-a");
        assertTrue(modResult.contains("MODIFICAÇÃO DE CÓDIGO EXECUTADA COM SUCESSO"));
        assertEquals("public record ClienteDTO(String codigo) {}", Files.readString(projectB.resolve("ClienteDTO.java")));

        String gitResult = executor.execute("git_changes", "{\"project\": \"projeto-b\"}", "tenant-1", "projeto-a");
        assertTrue(gitResult.contains("ClienteDTO.java"));
    }

    @Test
    void shouldHighlightFrontendAndIncludeSectionMapOnLargeFiles() throws IOException {
        Path staticDir = tempDir.resolve("src/main/resources/static");
        Path javaDir = tempDir.resolve("src/main/java/br/com/mv");
        Files.createDirectories(staticDir);
        Files.createDirectories(javaDir);

        for (int i = 1; i <= 30; i++) {
            Files.writeString(javaDir.resolve("Classe" + i + ".java"), "public class Classe" + i + " {}");
        }

        StringBuilder html = new StringBuilder("<!DOCTYPE html>\n<html>\n<head>\n<style>\nbody { color: #fff; }\n</style>\n</head>\n<body>\n");
        for (int i = 1; i <= 360; i++) {
            html.append("<div>Linha ").append(i).append("</div>\n");
        }
        html.append("<script>\nfunction sendQuestion() {\n  return true;\n}\n</script>\n</body>\n</html>");
        Files.writeString(staticDir.resolve("index.html"), html.toString());

        String listResult = executor.execute("list_files", "{}", "tenant-1", "");
        assertTrue(listResult.contains("src/main/resources/static/index.html"), "Deveria destacar index.html no list_files");

        String searchResult = executor.execute("search_code", "{\"query\": \"interface grafica front\"}", "tenant-1", "");
        assertTrue(searchResult.contains("src/main/resources/static/index.html"), "Deveria encontrar index.html na busca local de UI");

        String readResult = executor.execute("read_file", "{\"path\": \"src/main/resources/static/index.html\"}", "tenant-1", "");
        assertTrue(readResult.contains("[Mapa das próximas seções do arquivo"), "Deveria incluir mapa das próximas seções");
        assertTrue(readResult.contains("function sendQuestion()"), "Deveria mapear função JS após a página 1");
    }

    @Test
    void shouldStripLineNumberPrefixesInModifyFileAndBlockPartialWriteOnLargeFile() throws IOException {
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            content.append("linha ").append(i).append("\n");
        }
        Path bigFile = tempDir.resolve("index.html");
        Files.writeString(bigFile, content.toString());

        // Tenta sobrescrever arquivo de 100 linhas com apenas 3 linhas via write_file -> deve bloquear
        String writeArgs = """
                {
                    "path": "index.html",
                    "content": "<div>parcial</div>\\n"
                }
                """;
        String writeRes = executor.execute("write_file", writeArgs, "tenant-1", "");
        assertTrue(writeRes.contains("FALHA NA MODIFICAÇÃO DE CÓDIGO"));
        assertTrue(writeRes.contains("use 'modify_file'"));

        // Usa modify_file mesmo com prefixo de número de linha copiado do read_file -> deve funcionar
        String modArgs = """
                {
                    "path": "index.html",
                    "targetContent": "10: linha 10\\n11: linha 11",
                    "replacementContent": "linha 10 alterada\\nlinha 11 alterada"
                }
                """;
        String modRes = executor.execute("modify_file", modArgs, "tenant-1", "");
        assertTrue(modRes.contains("MODIFICAÇÃO DE CÓDIGO EXECUTADA COM SUCESSO"));
        assertTrue(Files.readString(bigFile).contains("linha 10 alterada"));
    }
}
