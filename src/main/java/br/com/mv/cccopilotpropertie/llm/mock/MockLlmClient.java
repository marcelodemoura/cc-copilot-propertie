package br.com.mv.cccopilotpropertie.llm.mock;

import br.com.mv.cccopilotpropertie.llm.application.LlmClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mock")
public class MockLlmClient implements LlmClient {

    @Override
    public String complete(String prompt) {
        return """
        [RESPOSTA SIMULADA]

        Esta resposta foi gerada por um mock de LLM.
        O fluxo de RAG foi executado corretamente.

        Trechos utilizados:
        """ + prompt.substring(0, Math.min(300, prompt.length()));
    }
}
