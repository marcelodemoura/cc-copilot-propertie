package br.com.mv.cccopilotpropertie.project.infra;

import br.com.mv.cccopilotpropertie.project.domain.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
}
