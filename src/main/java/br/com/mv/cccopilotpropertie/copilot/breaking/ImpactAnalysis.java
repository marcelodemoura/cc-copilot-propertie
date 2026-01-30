package br.com.mv.cccopilotpropertie.copilot.breaking;

import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;

public record ImpactAnalysis(
        boolean internalUsage,
        boolean breaksHttpContract
) {

    public static ImpactAnalysis from(
            String tenantId,
            String knowledgeBase,
            ChangeSet change,
            SearchRepository repository
    ) {

        boolean internalUsage = false;
        boolean breaksHttpContract = false;

        // ==================================================
        // FIELD — remoção de campo
        // ==================================================
        if (change.target() == ChangeTarget.FIELD
                && change.type() == ChangeType.REMOVE) {

            String field = change.elementName();
            String dto = change.dtoName();

            // 1️⃣ Uso interno real
            internalUsage = repository
                    .findUsagesByClassName(tenantId, knowledgeBase, field)
                    .stream()
                    .anyMatch(r ->
                            r.content().contains("." + field)
                                    || r.content().contains("get" + capitalize(field))
                    );

            // 2️⃣ Quebra de contrato HTTP (PASSO 16 REAL)
            if (dto != null && internalUsage) {
                breaksHttpContract =
                        !repository
                                .findEndpointsUsingDto(
                                        tenantId,
                                        knowledgeBase,
                                        dto
                                )
                                .isEmpty();
            }
        }

        return new ImpactAnalysis(
                internalUsage,
                breaksHttpContract
        );
    }

    // helper local
    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
