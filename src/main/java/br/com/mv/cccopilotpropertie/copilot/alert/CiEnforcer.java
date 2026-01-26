package br.com.mv.cccopilotpropertie.copilot.alert;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CiEnforcer {

    @Value("${copilot.ci.fail-on-critical:false}")
    private static boolean failOnCritical;

    public void enforce(AlertResult alert) {
        if (!failOnCritical || alert == null) return;

        if (alert.level() == AlertLevel.CRITICAL) {
            throw new BuildFailException(
                    "Build bloqueado: " + alert.title()
            );
        }
    }
}
