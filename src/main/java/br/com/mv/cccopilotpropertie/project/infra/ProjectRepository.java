package br.com.mv.cccopilotpropertie.project.infra;

import br.com.mv.cccopilotpropertie.project.domain.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    List<ProjectEntity> findByTenantId(String tenantId);

    Optional<ProjectEntity> findByIdAndTenantId(UUID id, String tenantId);

    boolean existsByTenantIdAndName(String tenantId, String name);
}
