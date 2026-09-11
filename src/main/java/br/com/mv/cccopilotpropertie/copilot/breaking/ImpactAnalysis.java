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
        if (change == null) {
            return new ImpactAnalysis(false, false);
        }

        boolean internalUsage = false;
        boolean breaksHttpContract = false;

        String field = change.elementName();
        String dto = change.dtoName();

        // 1️⃣ Quebra de contrato HTTP / externo
        // Se o DTO associado à alteração for utilizado em endpoints REST ou em outras KBs,
        // a alteração de contrato quebra a compatibilidade externa, INDEPENDENTE de uso interno!
        if (dto != null && !dto.isBlank()) {
            boolean inEndpoints = !repository.findEndpointsUsingDto(tenantId, knowledgeBase, dto).isEmpty();
            boolean inOtherKbs = !repository.findUsagesInOtherKnowledgeBases(tenantId, knowledgeBase, dto).isEmpty();
            breaksHttpContract = inEndpoints || inOtherKbs;
        } else if (change.target() == ChangeTarget.DTO && field != null && !field.isBlank()) {
            boolean inEndpoints = !repository.findEndpointsUsingDto(tenantId, knowledgeBase, field).isEmpty();
            boolean inOtherKbs = !repository.findUsagesInOtherKnowledgeBases(tenantId, knowledgeBase, field).isEmpty();
            breaksHttpContract = inEndpoints || inOtherKbs;
        } else if (change.target() == ChangeTarget.ENDPOINT) {
            breaksHttpContract = true;
        }

        // 2️⃣ Uso interno real
        if (field != null && !field.isBlank()) {
            if (change.target() == ChangeTarget.FIELD) {
                internalUsage = repository
                        .findUsagesByClassName(tenantId, knowledgeBase, field)
                        .stream()
                        .anyMatch(r ->
                                r.content().contains("." + field)
                                        || r.content().contains("get" + capitalize(field))
                                        || r.content().contains("set" + capitalize(field))
                                        || r.content().contains("is" + capitalize(field))
                        );
            } else if (change.target() == ChangeTarget.DTO) {
                internalUsage = !repository
                        .findUsagesByClassName(tenantId, knowledgeBase, field)
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
