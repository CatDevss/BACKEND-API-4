package catdevs.georuraldatahub.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import catdevs.georuraldatahub.entity.enums.OccurrenceStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "ocorrencia")
public class Occurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oco_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "arq_id", nullable = false)
    private File file;

    @Column(name = "oco_registro", nullable = false)
    private Integer recordIndex;

    @Column(name = "oco_campo", length = 255)
    private String fieldName;

    @Column(name = "oco_posicao")
    private Integer position;

    @Column(name = "oco_codigo", nullable = false, length = 80)
    private String code;

    @Column(name = "oco_motivo", nullable = false, length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "oco_status", nullable = false, length = 20)
    private OccurrenceStatus status = OccurrenceStatus.ABERTA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usr_responsavel_id")
    private User assignedTo;

    @CreationTimestamp
    @Column(name = "oco_criada_em", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "oco_atualizada_em")
    private LocalDateTime updatedAt;

    @Column(name = "oco_resolvida_em")
    private LocalDateTime resolvedAt;

    @Column(name = "oco_tentativa_validacao", nullable = false)
    private Integer validationAttempt = 1;

    public Occurrence() {
    }

    public Long getId() {
        return id;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public Integer getRecordIndex() {
        return recordIndex;
    }

    public void setRecordIndex(Integer recordIndex) {
        this.recordIndex = recordIndex;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OccurrenceStatus getStatus() {
        return status;
    }

    public void setStatus(OccurrenceStatus status) {
        this.status = status;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Integer getValidationAttempt() {
        return validationAttempt;
    }

    public void setValidationAttempt(Integer validationAttempt) {
        this.validationAttempt = validationAttempt;
    }
}