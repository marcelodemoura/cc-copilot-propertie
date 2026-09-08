package br.com.mv.cccopilotpropertie.project;

import br.com.mv.cccopilotpropertie.project.api.DirectoryBrowseResultDto;
import br.com.mv.cccopilotpropertie.project.api.DirectoryEntryDto;
import br.com.mv.cccopilotpropertie.project.application.ProjectFileSystemService;
import br.com.mv.cccopilotpropertie.project.domain.ProjectEntity;
import br.com.mv.cccopilotpropertie.project.infra.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectFileSystemServiceTest {

    @TempDir
    Path tempDir;

    private ProjectRepository projectRepository;
    private ProjectFileSystemService fileSystemService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        fileSystemService = new ProjectFileSystemService(tempDir.toString(), projectRepository);
    }

    @Test
    void shouldBrowseDirectoriesAndDetectProjects() throws IOException {
        Path gradleProj = tempDir.resolve("my-gradle-app");
        Files.createDirectories(gradleProj);
        Files.writeString(gradleProj.resolve("build.gradle"), "plugins { id 'java' }");

        Path mavenProj = tempDir.resolve("my-maven-app");
        Files.createDirectories(mavenProj);
        Files.writeString(mavenProj.resolve("pom.xml"), "<project></project>");

        Path regularDir = tempDir.resolve("docs");
        Files.createDirectories(regularDir);

        DirectoryBrowseResultDto result = fileSystemService.browse(tempDir.toString());
        assertNotNull(result);
        assertEquals(tempDir.toAbsolutePath().normalize().toString(), result.currentPath());
        assertNull(result.parentPath());
        assertEquals(3, result.directories().size());

        DirectoryEntryDto first = result.directories().get(0);
        assertTrue(first.isProject());
    }

    @Test
    void shouldSearchDirectoriesByQuery() throws IOException {
        Path target1 = tempDir.resolve("auth-service");
        Files.createDirectories(target1);
        Files.writeString(target1.resolve("pom.xml"), "<xml/>");

        Path target2 = tempDir.resolve("auth-client");
        Files.createDirectories(target2);

        Path other = tempDir.resolve("payment-service");
        Files.createDirectories(other);

        List<DirectoryEntryDto> results = fileSystemService.searchDirectories("auth");
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(d -> d.name().equals("auth-service")));
        assertTrue(results.stream().anyMatch(d -> d.name().equals("auth-client")));
    }

    @Test
    void shouldUploadAndExtractZipSuccessfully() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry1 = new ZipEntry("sample-app/build.gradle");
            zos.putNextEntry(entry1);
            zos.write("plugins { id 'java' }".getBytes());
            zos.closeEntry();

            ZipEntry entry2 = new ZipEntry("sample-app/src/Main.java");
            zos.putNextEntry(entry2);
            zos.write("class Main {}".getBytes());
            zos.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "sample-app.zip", "application/zip", baos.toByteArray()
        );

        when(projectRepository.save(any(ProjectEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProjectEntity created = fileSystemService.uploadAndExtractZip(file, "Sample App", "tenant-1");
        assertNotNull(created);
        assertEquals("Sample App", created.getName());
        assertEquals("tenant-1", created.getTenantId());
        assertTrue(Files.exists(Path.of(created.getRootPath())));
        assertTrue(Files.exists(Path.of(created.getRootPath(), "build.gradle")));
    }
}
