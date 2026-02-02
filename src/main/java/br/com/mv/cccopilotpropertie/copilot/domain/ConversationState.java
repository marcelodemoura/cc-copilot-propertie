package br.com.mv.cccopilotpropertie.copilot.domain;

import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeSet;

public class ConversationState {

    private String dto;
    private String field;
    private ChangeSet lastChange;

    // ===== getters =====
    public String getDto() {
        return dto;
    }

    public String getField() {
        return field;
    }

    public ChangeSet getLastChange() {
        return lastChange;
    }

    // ===== setters =====
    public void setDto(String dto) {
        this.dto = dto;
    }

    public void setField(String field) {
        this.field = field;
    }

    public void setLastChange(ChangeSet lastChange) {
        this.lastChange = lastChange;
    }

    // utilitário opcional
    public boolean hasChange() {
        return lastChange != null;
    }
}
