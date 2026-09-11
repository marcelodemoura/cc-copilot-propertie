package br.com.mv.cccopilotpropertie.project.application;

import br.com.mv.cccopilotpropertie.copilot.alert.ResourceNotFoundException;
import br.com.mv.cccopilotpropertie.index.IndexResult;
import br.com.mv.cccopilotpropertie.index.IndexService;
import br.com.mv.cccopilotpropertie.project.domain.ProjectEntity;
import br.com.mv.cccopilotpropertie.project.infra.ProjectRepository;
import br.com.mv.cccopilotpropertie.vector.EmbeddingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final IndexService indexService;
    private final EmbeddingRepository embeddingRepository;
    private final Path allowedBasePath;

    public ProjectService(
            ProjectRepository projects,
            IndexService indexService,
            EmbeddingRepository embeddingRepository,
            @Value("${indexer.base-path:.}") String allowedBasePath
    ) {
        this.projects = projects;
        this.indexService = indexService;
        this.embeddingRepository = embeddingRepository;
        this.allowedBasePath = Path.of(allowedBasePath != null ? allowedBasePath : ".").toAbsolutePath().normalize();
    }

    public List<ProjectEntity> list(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            return projects.findByTenantId(tenantId.trim());
        }
        return projects.findAll();
    }

    public List<ProjectEntity> list() {
        return list(null);
    }

    public ProjectEntity create(String tenantId, String name, String rootPath) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : "default";
        String effectiveName = name != null ? name.trim() : "";

        if (effectiveName.isBlank()) {
            throw new IllegalArgumentException("O nome do projeto é obrigatório.");
        }

        if (projects.existsByTenantIdAndName(effectiveTenant, effectiveName)) {
            throw new IllegalArgumentException("Já existe um projeto cadastrado com o nome '" + effectiveName + "' para o tenant '" + effectiveTenant + "'.");
        }

        if (rootPath == null || rootPath.isBlank()) {
            throw new IllegalArgumentException("O caminho raiz (rootPath) do projeto é obrigatório.");
        }

        Path root = Path.of(rootPath.trim()).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("O caminho informado não existe no servidor: " + rootPath);
        }

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("O caminho informado não é um diretório: " + rootPath);
        }

        try {
            Path realRoot = root.toRealPath();
            Path realAllowed = allowedBasePath.toRealPath();
            if (!realRoot.startsWith(realAllowed)) {
                throw new SecurityException("Acesso negado: O caminho do projeto está fora da área permitida (" + allowedBasePath + "): " + rootPath);
            }
            return projects.save(new ProjectEntity(effectiveTenant, effectiveName, realRoot.toString()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível resolver o caminho do projeto: " + e.getMessage(), e);
        }
    }

    public ProjectEntity get(UUID id) {
        return projects.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado: " + id));
    }

    public ProjectEntity get(UUID id, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return get(id);
        }
        return projects.findByIdAndTenantId(id, tenantId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado para o tenant " + tenantId + ": " + id));
    }

    @Transactional
    public void delete(UUID id) {
        ProjectEntity project = get(id);
        // Exclui os embeddings vetoriais associados ao projeto
        embeddingRepository.deleteByKnowledgeBase(project.getTenantId(), id.toString());

        // Se o projeto for oriundo de um upload na pasta uploads/, remove os arquivos físicos
        try {
            Path root = Path.of(project.getRootPath()).toAbsolutePath().normalize();
            Path uploadsDir = allowedBasePath.resolve("uploads").toAbsolutePath().normalize();
            if (root.startsWith(uploadsDir) && Files.exists(root)) {
                try (var walk = Files.walk(root)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                            });
                }
            }
        } catch (Exception ignored) {
        }

        projects.delete(project);
    }

    public IndexResult index(UUID id) throws IOException {
        ProjectEntity project = get(id);
        return indexService.indexPath(project.getTenantId(), project.getRootPath(), id.toString());
    }

    public br.com.mv.cccopilotpropertie.index.IndexJobStatus indexAsync(UUID id) {
        ProjectEntity project = get(id);
        return indexService.indexAsync(project.getTenantId(), project.getRootPath(), id.toString());
    }

    public java.util.Optional<br.com.mv.cccopilotpropertie.index.IndexJobStatus> getIndexStatus(UUID jobId) {
        return indexService.getJobStatus(jobId);
    }

    public java.util.Optional<br.com.mv.cccopilotpropertie.index.IndexJobStatus> getLatestIndexStatus(UUID projectId) {
        return indexService.getLatestJobStatus(projectId.toString());
    }

    public boolean cancelIndex(UUID projectId) {
        return indexService.cancelLatestJob(projectId.toString());
    }

    public boolean cancelJob(UUID jobId) {
        return indexService.cancelJob(jobId);
    }
}
