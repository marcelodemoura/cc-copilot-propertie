package br.com.mv.cccopilotpropertie.copilot.agent;

@FunctionalInterface
public interface AgentProgressListener {
    void onProgress(String type, Object data);
}
