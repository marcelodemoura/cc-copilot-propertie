package br.com.mv.cccopilotpropertie.controller;

import br.com.mv.cccopilotpropertie.index.IndexResult;
import br.com.mv.cccopilotpropertie.index.IndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

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
            @RequestParam String knowledgeBase
    ) throws IOException {
        if (knowledgeBase == null || knowledgeBase.isBlank()) {
            throw new IllegalArgumentException("knowledgeBase é obrigatória");
        }
        return indexService.indexPath(path, knowledgeBase);
    }
}
