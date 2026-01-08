package br.com.mv.cccopilotpropertie.copilot.api;

import br.com.mv.cccopilotpropertie.copilot.application.CopilotService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/copilot")
public class CopilotController {

    private final CopilotService copilot;

    public CopilotController(CopilotService copilot) {
        this.copilot = copilot;
    }

    @PostMapping("/ask")
    public String ask(@RequestBody AskRequest req) {
        return copilot.ask(req.tenantId(), req.knowledgeBase(), req.question());
    }
}


