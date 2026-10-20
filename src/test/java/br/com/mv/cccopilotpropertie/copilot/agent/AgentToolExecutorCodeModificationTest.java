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
}
