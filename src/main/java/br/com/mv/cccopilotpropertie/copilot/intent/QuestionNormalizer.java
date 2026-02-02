package br.com.mv.cccopilotpropertie.copilot.intent;

import org.springframework.stereotype.Service;

@Service
public class QuestionNormalizer {

    public String normalize(String question) {
        String q = question.toLowerCase().trim();

        // 🔴 BREAKING
        if (q.matches(".*(breaking|quebra|vai quebrar|preciso versionar).*")) {
            return "BREAKING_CHANGE";
        }

        // 🌐 HTTP / API
        if (q.matches(".*(api|endpoint|http|contrato).*")) {
            return "HTTP_CONTRACT_IMPACT";
        }

        // 🌍 EXTERNO
        if (q.matches(".*(outro sistema|externo|contrato externo).*")) {
            return "EXTERNAL_IMPACT";
        }

        // 🧱 CAMPO
        if (q.matches(".*(remover|excluir).*campo.*")) {
            return "FIELD_REMOVAL";
        }

        return "GENERIC";
    }
}
