package br.com.mv.cccopilotpropertie.llm;
import br.com.mv.cccopilotpropertie.llm.apllication.LlmClient;
import org.springframework.stereotype.Service;


@Service
public class OpenAiChatService implements LlmClient {


    @Override
    public String complete(String prompt) {
        return "[MOCK LLM] " + prompt;
    }


}

