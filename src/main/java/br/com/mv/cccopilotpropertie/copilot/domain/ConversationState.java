package br.com.mv.cccopilotpropertie.copilot.domain;

import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeSet;

public class ConversationState {

    private String dto;
    private String field;
    private ChangeSet lastChange;

    public String getDto() {
        return dto;
    }

    public void setDto(String dto) {
        this.dto = dto;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public ChangeSet getLastChange() {
        return lastChange;
    }

    public void setLastChange(ChangeSet lastChange) {
        this.lastChange = lastChange;
    }

    public boolean hasChange() {
        return lastChange != null;
    }
}
