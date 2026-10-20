package br.com.mv.cccopilotpropertie.project;

import br.com.mv.cccopilotpropertie.copilot.alert.ResourceNotFoundException;
import br.com.mv.cccopilotpropertie.index.IndexService;
import br.com.mv.cccopilotpropertie.project.application.ProjectService;
import br.com.mv.cccopilotpropertie.project.domain.ProjectEntity;
import br.com.mv.cccopilotpropertie.project.infra.ProjectRepository;
import br.com.mv.cccopilotpropertie.vector.EmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectServiceTest {

    @TempDir
    Path tempDir;

    private ProjectRepository projectRepository;
    private IndexService indexService;
    private EmbeddingRepository embeddingRepository;
    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        indexService = mock(IndexService.class);
        embeddingRepository = mock(EmbeddingRepository.class);

        projectService = new ProjectService(
                projectRepository, indexService, embeddingRepository, tempDir.toString()
        );
    }

    @Test
    void shouldCreateProjectSuccessfullyInsideAllowedPath() throws IOException {
        Path validProjDir = tempDir.resolve("my-proj");
        Files.createDirectories(validProjDir);

        when(projectRepository.existsByTenantIdAndName("default", "my-proj")).thenReturn(false);
        when(projectRepository.save(any(ProjectEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ProjectEntity created = projectService.create("default", "my-proj", validProjDir.toString());
        assertNotNull(created);
        assertEquals("my-proj", created.getName());
        assertEquals("default", created.getTenantId());
    }

    @Test
    void shouldRejectProjectWithDuplicateNameForSameTenant() throws IOException {
        Path validProjDir = tempDir.resolve("my-proj");
        Files.createDirectories(validProjDir);

        when(projectRepository.existsByTenantIdAndName("tenant-x", "dup-proj")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                projectService.create("tenant-x", "dup-proj", validProjDir.toString())
        );
    }

    @Test
    void shouldRejectNonExistentPath() {
        Path nonExistent = tempDir.resolve("does-not-exist");

        assertThrows(IllegalArgumentException.class, () ->
                projectService.create("default", "proj", nonExistent.toString())
        );
    }

    @Test
    void shouldThrowResourceNotFoundWhenProjectDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.get(id));
    }

    @Test
    void shouldDeleteProjectAndCleanUpEmbeddings() throws IOException {
        UUID id = UUID.randomUUID();
        Path projDir = tempDir.resolve("my-app");
        Files.createDirectories(projDir);

        ProjectEntity proj = new ProjectEntity("tenant-1", "my-app", projDir.toString());
        when(projectRepository.findById(id)).thenReturn(Optional.of(proj));

        projectService.delete(id);

        verify(embeddingRepository).deleteByKnowledgeBase("tenant-1", id.toString());
        verify(projectRepository).delete(proj);
    }
}
