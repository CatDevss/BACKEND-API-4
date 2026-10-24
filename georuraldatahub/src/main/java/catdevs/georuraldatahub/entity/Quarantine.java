package catdevs.georuraldatahub.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quarentena")
public class Quarantine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qua_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ing_id", referencedColumnName = "ing_id")
    private DataIngestion ingestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arq_id", referencedColumnName = "arq_id")
    private File file;

    @Column(name = "qua_status")
    private String status;

    @Column(name = "qua_motivo")
    private String reason;

    @Column(name = "qua_tratamento")
    private String treatment;

    @CreationTimestamp 
    @Column(name = "qua_data_entrada")
    private LocalDateTime dateEntry;

    @Column (name = "qua_data_resolucao")
    private LocalDateTime dateResolution;

    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn (name = "usr_id", referencedColumnName = "usr_id")
    private User user;

    public Quarantine() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DataIngestion getIngestion() {
        return ingestion;
    }

    public void setIngestion(DataIngestion ingestion) {
        this.ingestion = ingestion;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTreatment() {
        return treatment;
    }

    public void setTreatment(String treatment) {
        this.treatment = treatment;
    }

    public LocalDateTime getDateEntry() {
        return dateEntry;
    }

    public void setDateEntry(LocalDateTime dateEntry) {
        this.dateEntry = dateEntry;
    }

    public LocalDateTime getDateResolution() {
        return dateResolution;
    }

    public void setDateResolution(LocalDateTime dateResolution) {
        this.dateResolution = dateResolution;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    
}