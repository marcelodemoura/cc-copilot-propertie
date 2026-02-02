package br.com.mv.cccopilotpropertie.copilot.intent;

import org.springframework.stereotype.Service;

@Service
public class IntentClassifier {

    public CopilotIntent classify(String question) {
        String q = question.toLowerCase();

        // 1️⃣ Entendimento do projeto
        if (q.contains("o que esse projeto faz")
                || q.contains("para que serve")) {
            return CopilotIntent.PROJECT_UNDERSTANDING;
        }

        // 2️⃣ Remoção de campo (cria contexto)
        if (q.contains("remover") && q.contains("campo")) {
            return CopilotIntent.FIELD_REMOVAL;
        }

        // 3️⃣ Breaking change
        if (q.contains("breaking")
                || q.contains("quebra")
                || q.contains("versionar")) {
            return CopilotIntent.BREAKING_CHANGE;
        }

        // 4️⃣ Impacto em contrato HTTP / API
        if (q.contains("api")
                || q.contains("http")
                || q.contains("contrato")) {
            return CopilotIntent.HTTP_CONTRACT_IMPACT;
        }

        // 5️⃣ Impacto externo (outro sistema)
        if (q.contains("outro sistema")
                || q.contains("externo")) {
            return CopilotIntent.EXTERNAL_IMPACT;
        }

        // 6️⃣ Uso / localização de campo
        if (q.contains("onde") && q.contains("campo")) {
            return CopilotIntent.FIELD_USAGE;
        }

        if (q.contains("usado") && q.contains("campo")) {
            return CopilotIntent.FIELD_USAGE;
        }

        // 7️⃣ Endpoints (listagem genérica)
        if (q.contains("endpoint")) {
            return CopilotIntent.ENDPOINT_USAGE;
        }

        // 8️⃣ Auditoria / risco
        if (q.contains("auditoria")
                || q.contains("risco")) {
            return CopilotIntent.DTO_AUDIT;
        }

        // 9️⃣ Fallback
        return CopilotIntent.GENERIC_QUESTION;
    }
}
