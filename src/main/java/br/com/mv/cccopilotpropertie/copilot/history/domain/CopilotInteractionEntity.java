package br.com.mv.cccopilotpropertie.copilot.history.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "copilot_interaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CopilotInteractionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    private String tenantId;
    private String knowledgeBase;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    private double confidence;

    private Instant createdAt = Instant.now();

    public CopilotInteractionEntity(
            String tenantId,
            String knowledgeBase,
            String question,
            String answer,
            double confidence
    ) {
        this.tenantId = tenantId;
        this.knowledgeBase = knowledgeBase;
        this.question = question;
        this.answer = answer;
        this.confidence = confidence;
    }
}
