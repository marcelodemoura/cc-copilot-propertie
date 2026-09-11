package br.com.mv.cccopilotpropertie.copilot.code;

import br.com.mv.cccopilotpropertie.index.IndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodeModificationServiceTest {

    @TempDir
    Path tempDir;

    private CodeModificationService service;
    private IndexService indexService;

    @BeforeEach
    void setUp() {
        indexService = mock(IndexService.class);
        service = new CodeModificationService(true, tempDir.toString(), indexService);
    }

    @Test
    void shouldModifyFileSuccessfullyAndCreateBackup() throws IOException {
        Path javaFile = tempDir.resolve("UsuarioDTO.java");
        String originalCode = """
                package br.com.mv;
                
                public class UsuarioDTO {
                    private String nome;
                    private String telefone;
                }
                """;
        Files.writeString(javaFile, originalCode);

        FileModificationResult result = service.modifyFile(
                tempDir,
                "tenant-1",
                "kb-1",
                "UsuarioDTO.java",
                "    private String telefone;\n",
                "    @Deprecated\n    private String telefone;\n",
                "Deprecar campo telefone"
        );

        assertTrue(result.success());
        assertEquals("MODIFIED", result.status());
        assertNotNull(result.diff());
        assertTrue(result.diff().contains("-     private String telefone;"));
        assertTrue(result.diff().contains("+     @Deprecated"));

        // Valida arquivo modificado
        String modifiedCode = Files.readString(javaFile);
        assertTrue(modifiedCode.contains("@Deprecated"));

        // Valida criação do backup
        Path backupFile = tempDir.resolve("UsuarioDTO.java.bak");
        assertTrue(Files.exists(backupFile));
        assertEquals(originalCode, Files.readString(backupFile));

        // Valida reindexação do arquivo
        verify(indexService, times(1)).reindexSingleFile("tenant-1", "kb-1", javaFile);
    }

    @Test
    void shouldFailWhenTargetContentIsNotFound() throws IOException {
        Path javaFile = tempDir.resolve("ClienteDTO.java");
        Files.writeString(javaFile, "public class ClienteDTO {}");

        FileModificationResult result = service.modifyFile(
                tempDir,
                "tenant-1",
                "kb-1",
                "ClienteDTO.java",
                "trecho inexistente",
                "novo trecho",
                "teste"
        );

        assertFalse(result.success());
        assertEquals("FAILED", result.status());
        assertTrue(result.message().contains("não foi encontrado"));
    }

    @Test
    void shouldWriteNewFileWithParentDirectories() throws IOException {
        FileModificationResult result = service.writeFile(
                tempDir,
                "tenant-1",
                "kb-1",
                "src/main/java/br/com/mv/NovoServico.java",
                "public class NovoServico {}",
                "Criação de serviço"
        );

        assertTrue(result.success());
        assertEquals("CREATED", result.status());

        Path createdFile = tempDir.resolve("src/main/java/br/com/mv/NovoServico.java");
        assertTrue(Files.exists(createdFile));
        assertEquals("public class NovoServico {}", Files.readString(createdFile));
    }

    @Test
    void shouldRestoreBackupSuccessfully() throws IOException {
        Path file = tempDir.resolve("Config.java");
        Files.writeString(file, "original content");

        // Modifica gerando backup
        service.modifyFile(tempDir, "tenant-1", "kb-1", "Config.java", "original", "updated", "test");
        assertEquals("updated content", Files.readString(file));

        // Restaura
        FileModificationResult restoreResult = service.restoreBackup(tempDir, "Config.java");
        assertTrue(restoreResult.success());
        assertEquals("RESTORED", restoreResult.status());
        assertEquals("original content", Files.readString(file));
        assertFalse(Files.exists(tempDir.resolve("Config.java.bak")));
    }

    @Test
    void shouldRejectPathTraversal() {
        FileModificationResult result = service.modifyFile(
                tempDir,
                "tenant-1",
                "kb-1",
                "../../etc/passwd",
                "root",
                "hacked",
                "ataque"
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("Acesso negado"));
    }

    @Test
    void shouldRejectForbiddenDirectory() {
        FileModificationResult result = service.writeFile(
                tempDir,
                "tenant-1",
                "kb-1",
                ".git/config",
                "[core]",
                "tentativa"
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("Acesso negado: Tentativa de modificar pasta protegida"));
    }

    @Test
    void shouldRejectForbiddenFile() {
        FileModificationResult result = service.writeFile(
                tempDir,
                "tenant-1",
                "kb-1",
                ".env",
                "SECRET=123",
                "tentativa"
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("Acesso negado: Arquivo protegido"));
    }

    @Test
    void shouldRejectForbiddenExtension() {
        FileModificationResult result = service.writeFile(
                tempDir,
                "tenant-1",
                "kb-1",
                "malware.exe",
                "binario",
                "tentativa"
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("Extensão '.exe' não permitida"));
    }

    @Test
    void shouldReturnFailureWhenDisabledByConfiguration() {
        CodeModificationService disabledService = new CodeModificationService(false, tempDir.toString(), indexService);

        FileModificationResult result = disabledService.modifyFile(
                tempDir, "tenant-1", "kb-1", "UsuarioDTO.java", "a", "b", "test"
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("desabilitada"));
    }
}
