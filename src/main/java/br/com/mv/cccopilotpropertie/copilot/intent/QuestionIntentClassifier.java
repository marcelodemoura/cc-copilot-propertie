package br.com.mv.cccopilotpropertie.copilot.intent;

import br.com.mv.cccopilotpropertie.copilot.domain.QuestionIntent;
import org.springframework.stereotype.Component;

@Component
public class QuestionIntentClassifier {

    public QuestionIntent classify(String question) {
        String q = normalize(question);

        // 🧠 Projeto
        if (q.contains("o que esse projeto faz")
                || q.contains("para que serve")
                || q.contains("qual dominio")) {
            return QuestionIntent.UNDERSTAND_PROJECT;
        }

        // 🧹 Remoção de campo
        if (q.contains("remover o campo")
                || q.contains("posso remover")
                || q.contains("excluir campo")) {
            return QuestionIntent.REMOVE_FIELD;
        }

        // 🔍 Uso de campo
        if (q.contains("campo") && q.contains("usado")) {
            return QuestionIntent.CHECK_FIELD_USAGE;
        }

        // 📍 Localização de campo
        if (q.contains("onde") && q.contains("campo")) {
            return QuestionIntent.LOCATE_FIELD;
        }

        // 💥 Breaking
        if (q.contains("breaking")
                || q.contains("quebra")
                || q.contains("isso é breaking")) {
            return QuestionIntent.CHECK_BREAKING;
        }

        // 🌐 Contrato HTTP / API
        if (q.contains("api")
                || q.contains("endpoint")
                || q.contains("http")
                || q.contains("rota")) {
            return QuestionIntent.CHECK_HTTP_CONTRACT;
        }

        // 🌍 Impacto externo
        if (q.contains("outro sistema")
                || q.contains("externo")
                || q.contains("contrato externo")) {
            return QuestionIntent.CHECK_EXTERNAL_IMPACT;
        }

        // 📦 Auditoria DTO
        if (q.contains("auditoria")
                || q.contains("risco")) {
            return QuestionIntent.AUDIT_DTO;
        }

        return QuestionIntent.GENERIC;
    }

    private String normalize(String q) {
        return q == null ? "" : q.toLowerCase().trim();
    }
}
