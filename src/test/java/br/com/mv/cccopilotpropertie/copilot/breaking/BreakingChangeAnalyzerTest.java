package br.com.mv.cccopilotpropertie.copilot.breaking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BreakingChangeAnalyzerTest {

    private BreakingChangeAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new BreakingChangeAnalyzer();
    }

    @Test
    void removingFieldInHttpContractShouldBeBreakingWithVersioning() {
        ChangeSet change = new ChangeSet(ChangeTarget.FIELD, ChangeType.REMOVE, "cpf", "CustomerDto", null, null);
        ImpactAnalysis impact = new ImpactAnalysis(false, true); // breaks HTTP contract

        BreakingAnalysisResult result = analyzer.analyze(change, impact);

        assertEquals(BreakingType.BREAKING, result.breakingType());
        assertTrue(result.requiresVersioning());
        assertTrue(result.reason().contains("contrato HTTP"));
    }

    @Test
    void addingMandatoryFieldInHttpContractShouldBeBreakingWithVersioning() {
        ChangeSet change = new ChangeSet(
                ChangeTarget.FIELD, ChangeType.ADD, "taxId", "CustomerDto", null, "@NotNull String taxId"
        );
        ImpactAnalysis impact = new ImpactAnalysis(false, true); // breaks HTTP contract

        BreakingAnalysisResult result = analyzer.analyze(change, impact);

        assertEquals(BreakingType.BREAKING, result.breakingType());
        assertTrue(result.requiresVersioning());
        assertTrue(result.reason().contains("obrigatório"));
    }

    @Test
    void addingOptionalFieldShouldBeNonBreaking() {
        ChangeSet change = new ChangeSet(
                ChangeTarget.FIELD, ChangeType.ADD, "nickname", "CustomerDto", null, "String nickname"
        );
        ImpactAnalysis impact = new ImpactAnalysis(false, true);

        BreakingAnalysisResult result = analyzer.analyze(change, impact);

        assertEquals(BreakingType.NON_BREAKING, result.breakingType());
        assertFalse(result.requiresVersioning());
    }

    @Test
    void modifyingFieldTypeInHttpContractShouldBeBreaking() {
        ChangeSet change = new ChangeSet(
                ChangeTarget.FIELD, ChangeType.TYPE_CHANGE, "id", "CustomerDto", "Long", "UUID"
        );
        ImpactAnalysis impact = new ImpactAnalysis(false, true);

        BreakingAnalysisResult result = analyzer.analyze(change, impact);

        assertEquals(BreakingType.BREAKING, result.breakingType());
        assertTrue(result.requiresVersioning());
    }

    @Test
    void renamingFieldInHttpContractShouldBeBreaking() {
        ChangeSet change = new ChangeSet(
                ChangeTarget.FIELD, ChangeType.RENAME, "oldName", "CustomerDto", "oldName", "newName"
        );
        ImpactAnalysis impact = new ImpactAnalysis(false, true);

        BreakingAnalysisResult result = analyzer.analyze(change, impact);

        assertEquals(BreakingType.BREAKING, result.breakingType());
        assertTrue(result.requiresVersioning());
    }

    @Test
    void makingFieldRequiredInHttpContractShouldBeBreaking() {
        ChangeSet change = new ChangeSet(
                ChangeTarget.FIELD, ChangeType.REQUIRED_CHANGE, "phone", "CustomerDto", "optional", "required"
        );
        ImpactAnalysis impact = new ImpactAnalysis(false, true);

        BreakingAnalysisResult result = analyzer.analyze(change, impact);

        assertEquals(BreakingType.BREAKING, result.breakingType());
        assertTrue(result.requiresVersioning());
    }
}
