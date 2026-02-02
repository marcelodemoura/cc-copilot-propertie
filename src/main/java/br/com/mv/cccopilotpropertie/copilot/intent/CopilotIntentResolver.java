package br.com.mv.cccopilotpropertie.copilot.intent;

import org.springframework.stereotype.Service;

@Service
public class CopilotIntentResolver {

    public CopilotIntent resolve(String question) {
        String q = question.toLowerCase();

        if (q.contains("remover") && q.contains("campo"))
            return CopilotIntent.FIELD_REMOVAL;

        if (q.contains("breaking") || q.contains("quebra"))
            return CopilotIntent.BREAKING_CHANGE;

        if (q.contains("quebra api") || q.contains("api") || q.contains("http"))
            return CopilotIntent.HTTP_CONTRACT_IMPACT;

        if (q.contains("outro sistema") || q.contains("externo"))
            return CopilotIntent.EXTERNAL_IMPACT;

        if (q.contains("endpoint"))
            return CopilotIntent.ENDPOINT_USAGE;

        if (q.contains("onde") && q.contains("campo"))
            return CopilotIntent.FIELD_USAGE;

        if (q.contains("auditoria") || q.contains("risco"))
            return CopilotIntent.DTO_AUDIT;

        if (q.contains("o que esse projeto faz") || q.contains("para que serve"))
            return CopilotIntent.PROJECT_UNDERSTANDING;

        return CopilotIntent.GENERIC_QUESTION;
    }
}
