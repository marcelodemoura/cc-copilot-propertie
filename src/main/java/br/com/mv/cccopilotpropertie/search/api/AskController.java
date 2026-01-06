package br.com.mv.cccopilotpropertie.search.api;

import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ask")
public class AskController {


    private final SearchService service;

    public AskController(SearchService service) {
        this.service = service;
    }

    @PostMapping
    public List<SearchResult> ask(@RequestBody String question) {
        return service.search(question);
    }
}