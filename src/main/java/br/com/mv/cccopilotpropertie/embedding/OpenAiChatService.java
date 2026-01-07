package br.com.mv.cccopilotpropertie.embedding;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenAiChatService {


    public String ask(String question, List<SearchResult> context) {

        StringBuilder prompt = new StringBuilder("""
                Você é um assistente técnico.
                Use apenas o contexto abaixo.
                
                """);

        for (var c : context) {
            prompt.append("\n").append(c.content());
        }

        prompt.append("\n\nPergunta: ").append(question);

        return "[MOCK LLM]\n\n" + prompt;
    }
}
