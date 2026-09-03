package br.com.mv.cccopilotpropertie.project.api;

import br.com.mv.cccopilotpropertie.copilot.application.CopilotService;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.index.IndexResult;
import br.com.mv.cccopilotpropertie.project.application.ProjectService;
import br.com.mv.cccopilotpropertie.project.domain.ProjectEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
@Tag(name = "Projetos", description = "Cadastro, indexação e perguntas por projeto")
public class ProjectController {

    private final ProjectService projects;
    private final CopilotService copilot;

    public ProjectController(ProjectService projects, CopilotService copilot) {
        this.projects = projects;
        this.copilot = copilot;
    }

    @GetMapping
    @Operation(summary = "Lista todos os projetos")
    public List<ProjectEntity> list() {
        return projects.list();
    }

    @PostMapping
    @Operation(summary = "Cadastra um projeto", description = "Registra um projeto local. O `rootPath` deve estar dentro de `INDEXER_BASE_PATH`.")
    public ProjectEntity create(@Valid @RequestBody CreateProjectRequest request) {
        return projects.create(request.tenantId(), request.name(), request.rootPath());
    }

    @PostMapping("/{id}/index")
    @Operation(summary = "Indexa o projeto", description = "Lê arquivos `.java`, `.kt`, `.groovy`, `.yaml`, `.sql` e `.md` do projeto, gera embeddings e armazena no pgvector.")
    public IndexResult index(@PathVariable UUID id) throws IOException {
        return projects.index(id);
    }

    @PostMapping("/{id}/index/async")
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    @Operation(summary = "Indexação assíncrona", description = "Inicia a indexação do projeto em segundo plano e retorna o status com jobId.")
    public br.com.mv.cccopilotpropertie.index.IndexJobStatus indexAsync(@PathVariable UUID id) {
        return projects.indexAsync(id);
    }

    @GetMapping("/{id}/index/status")
    @Operation(summary = "Status da indexação do projeto", description = "Consulta o progresso e status da última indexação do projeto.")
    public org.springframework.http.ResponseEntity<br.com.mv.cccopilotpropertie.index.IndexJobStatus> indexStatus(@PathVariable UUID id) {
        return projects.getLatestIndexStatus(id)
                .map(org.springframework.http.ResponseEntity::ok)
                .orElse(org.springframework.http.ResponseEntity.notFound().build());
    }

    @GetMapping("/index/jobs/{jobId}")
    @Operation(summary = "Status de um job de indexação por ID")
    public org.springframework.http.ResponseEntity<br.com.mv.cccopilotpropertie.index.IndexJobStatus> jobStatus(@PathVariable UUID jobId) {
        return projects.getIndexStatus(jobId)
                .map(org.springframework.http.ResponseEntity::ok)
                .orElse(org.springframework.http.ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/ask")
    @Operation(summary = "Pergunta ao agente", description = "O agente usa tool calling para buscar código, analisar DTOs e breaking changes. Use o mesmo `sessionId` para manter contexto entre perguntas.")
    public CopilotAnswer ask(@PathVariable UUID id, @Valid @RequestBody ProjectAskRequest request) {
        ProjectEntity project = projects.get(id);
        return copilot.ask(project.getTenantId(), id.toString(), request.question(), request.sessionId());
    }

    @PostMapping(value = "/{id}/ask/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Pergunta ao agente com streaming SSE", description = "Transmite eventos de progresso, chamadas de ferramentas e resposta em tempo real via Server-Sent Events.")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter askStream(
            @PathVariable UUID id, @Valid @RequestBody ProjectAskRequest request) {
        ProjectEntity project = projects.get(id);
        return copilot.askStream(project.getTenantId(), id.toString(), request.question(), request.sessionId());
    }
}
