package br.com.mv.cccopilotpropertie.controller;

import br.com.mv.cccopilotpropertie.index.IndexResult;
import br.com.mv.cccopilotpropertie.index.IndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

import br.com.mv.cccopilotpropertie.index.IndexJobStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@RestController
@RequestMapping("/index")
@Tag(name = "Indexação (legado)", description = "Indexação manual sem vínculo de projeto")
public class IndexController {

    private final IndexService indexService;

    public IndexController(IndexService indexService) {
        this.indexService = indexService;
    }

    @PostMapping
    @Operation(summary = "Indexa um diretório", description = "Indexa todos os arquivos elegíveis dentro de `path`. O caminho deve estar dentro de `INDEXER_BASE_PATH`.")
    public IndexResult index(
            @RequestParam String path,
            @RequestParam String knowledgeBase,
            @RequestParam(defaultValue = "default") String tenantId
    ) throws IOException {
        if (knowledgeBase == null || knowledgeBase.isBlank()) {
            throw new IllegalArgumentException("knowledgeBase é obrigatória");
        }
        return indexService.indexPath(tenantId, path, knowledgeBase);
    }

    @PostMapping("/async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Inicia indexação assíncrona com progresso observável")
    public IndexJobStatus indexAsync(
            @RequestParam String path,
            @RequestParam String knowledgeBase,
            @RequestParam(defaultValue = "default") String tenantId
    ) {
        if (knowledgeBase == null || knowledgeBase.isBlank()) {
            throw new IllegalArgumentException("knowledgeBase é obrigatória");
        }
        return indexService.indexAsync(tenantId, path, knowledgeBase);
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Consulta status de um job de indexação")
    public ResponseEntity<IndexJobStatus> getJobStatus(@PathVariable UUID jobId) {
        return indexService.getJobStatus(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status")
    @Operation(summary = "Consulta status da última indexação da knowledge base")
    public ResponseEntity<IndexJobStatus> getLatestStatus(@RequestParam String knowledgeBase) {
        return indexService.getLatestJobStatus(knowledgeBase)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Cancela um job de indexação por ID")
    public ResponseEntity<IndexJobStatus> cancelJob(@PathVariable UUID jobId) {
        indexService.cancelJob(jobId);
        return indexService.getJobStatus(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
