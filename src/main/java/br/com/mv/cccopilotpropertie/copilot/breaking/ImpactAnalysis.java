package br.com.mv.cccopilotpropertie.copilot.breaking;

import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;

public record ImpactAnalysis(
        boolean internalUsage,
        boolean externalUsage,
        boolean contract,
        boolean versioned,
        boolean breaksHttpContract

) {

    public static ImpactAnalysis from(
            String tenantId,
            String knowledgeBase,
            ChangeSet change,
            SearchRepository repository
    ) {

        boolean internalUsage = false;

        // =====================================
        // FIELD — remoção de campo
        // =====================================
        if (change.target() == ChangeTarget.FIELD) {
            String field = change.elementName();

            // Reaproveita busca existente no repositório
            // (mesmo que o nome do método não seja perfeito)
            internalUsage = repository
                    .findUsagesByClassName(
                            tenantId,
                            knowledgeBase,
                            field
                    )
                    .stream()
                    .anyMatch(r ->
                            r.content().contains("." + field)
                                    || r.content().contains("get" + capitalize(field))
                    );
        }

        return new ImpactAnalysis(
                internalUsage,
                false, // externalUsage
                false, // contract
                false, // versioned
                false  // breaksHttpContract
        );
    }


    // 🔒 helper privado — só ImpactAnalysis usa
    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
