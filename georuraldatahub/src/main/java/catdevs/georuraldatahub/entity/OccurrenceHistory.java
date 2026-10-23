package catdevs.georuraldatahub.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import catdevs.georuraldatahub.entity.enums.OccurrenceStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "historico_ocorrencia")
public class OccurrenceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hoco_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oco_id", nullable = false)
    private Occurrence occurrence;

    @Enumerated(EnumType.STRING)
    @Column(name = "hoco_status_anterior", length = 20)
    private OccurrenceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "hoco_status_novo", length = 20)
    private OccurrenceStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usr_id")
    private User actor;

    @Column(name = "hoco_acao", nullable = false, length = 100)
    private String action;

    @Column(name = "hoco_detalhes", length = 2000)
    private String details;

    @CreationTimestamp
    @Column(name = "hoco_data", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public OccurrenceHistory() {
    }

    public OccurrenceHistory(
            Occurrence occurrence,
            OccurrenceStatus previousStatus,
            OccurrenceStatus newStatus,
            User actor,
            String action,
            String details) {

        this.occurrence = occurrence;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.actor = actor;
        this.action = action;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public Occurrence getOccurrence() {
        return occurrence;
    }

    public OccurrenceStatus getPreviousStatus() {
        return previousStatus;
    }

    public OccurrenceStatus getNewStatus() {
        return newStatus;
    }

    public User getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}