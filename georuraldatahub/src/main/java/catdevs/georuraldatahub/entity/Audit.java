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
@Table(name = "auditoria")
public class Audit {

    @Id 
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    @Column(name = "aud_id")
    private Long id;

    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn (name = "usr_id", referencedColumnName = "usr_id")
    private User user;

    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn (name = "arq_id", referencedColumnName = "arq_id")
    private File file;

    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn (name = "ing_id", referencedColumnName = "ing_id")
    private DataIngestion ingestion;

    @Column(name = "aud_entidade")
    private String entityName;

    @Column(name = "aud_acao")
    private String action;

    @Column(name = "aud_detalhes")
    private String details;

    @CreationTimestamp 
    @Column(name = "aud_data")
    private LocalDateTime timestamp;

    public Audit() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public DataIngestion getIngestion() {
        return ingestion;
    }

    public void setIngestion(DataIngestion ingestion) {
        this.ingestion = ingestion;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }   
}