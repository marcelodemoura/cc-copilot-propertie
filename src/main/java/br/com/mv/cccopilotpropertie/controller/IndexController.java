package br.com.mv.cccopilotpropertie.controller;

import br.com.mv.cccopilotpropertie.index.IndexResult;
import br.com.mv.cccopilotpropertie.index.IndexService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RequestMapping("/index")
@RestController
public class IndexController {

    private final IndexService indexService;

    public IndexController(IndexService indexService) {
        this.indexService = indexService;
    }

    @PostMapping
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
