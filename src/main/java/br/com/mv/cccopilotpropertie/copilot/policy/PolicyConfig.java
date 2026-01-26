package br.com.mv.cccopilotpropertie.copilot.policy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class PolicyConfig {

    @Bean
    public ProjectPolicy projectPolicy(
            @Value("${copilot.policy.allow-contract-dto:true}") boolean allowContractDto,
            @Value("${copilot.policy.allow-internal-dto-leak:false}") boolean allowInternalDtoLeak,
            @Value("${copilot.ci.fail-on-critical:true}") boolean failOnCritical
    ) {
        return new ProjectPolicy(
                allowContractDto,
                allowInternalDtoLeak,
                failOnCritical
        );
    }
}
