package br.com.mv.cccopilotpropertie.project.application;

import br.com.mv.cccopilotpropertie.index.IndexResult;
import br.com.mv.cccopilotpropertie.index.IndexService;
import br.com.mv.cccopilotpropertie.project.domain.ProjectEntity;
import br.com.mv.cccopilotpropertie.project.infra.ProjectRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final IndexService indexService;

    public ProjectService(ProjectRepository projects, IndexService indexService) {
        this.projects = projects;
        this.indexService = indexService;
    }

    public List<ProjectEntity> list() {
        return projects.findAll();
    }

    public ProjectEntity create(String tenantId, String name, String rootPath) {
        return projects.save(new ProjectEntity(tenantId, name, rootPath));
    }

    public ProjectEntity get(UUID id) {
        return projects.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Projeto não encontrado: " + id));
    }

    public IndexResult index(UUID id) throws IOException {
        ProjectEntity project = get(id);
        return indexService.indexPath(project.getTenantId(), project.getRootPath(), id.toString());
    }
}
