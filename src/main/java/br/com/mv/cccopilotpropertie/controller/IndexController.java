package br.com.mv.cccopilotpropertie.controller;

import br.com.mv.cccopilotpropertie.index.IndexJob;
import br.com.mv.cccopilotpropertie.index.IndexService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/index")
public class IndexController {

    private final IndexService indexService;

    public IndexController(IndexService indexService) {
        this.indexService = indexService;
    }

    @PostMapping
    public IndexJob index(@RequestParam String path) throws IOException {
        return indexService.indexPath(path);
    }
}
