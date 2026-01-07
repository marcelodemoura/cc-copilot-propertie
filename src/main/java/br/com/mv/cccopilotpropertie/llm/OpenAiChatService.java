package br.com.mv.cccopilotpropertie.llm;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenAiChatService {

    public String ask(String question, List<SearchResult> context) {

        StringBuilder prompt = new StringBuilder("""
                You are a senior software copilot.
                Use ONLY the information below to answer.
                If you do not know, say you don't know.

                === CONTEXT ===
                """);

        for (SearchResult r : context) {
            prompt.append("- ").append(r.content()).append("\n");
        }

        prompt.append("\n=== QUESTION ===\n").append(question);

        // (por enquanto mock)
        return "[MOCK-LLM]\n" + prompt;
    }

        public String complete(String prompt) {
            // chamada real OpenAI depois
            return "[MOCK] " + prompt;
        }
    }

