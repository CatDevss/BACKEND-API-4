package catdevs.georuraldatahub.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import catdevs.georuraldatahub.entity.enums.IngestionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity 
@Table (name = "ingestao")
public class DataIngestion {

    @Id 
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    @Column (name = "ing_id")
    private Long id;

    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn (name = "dic_id", referencedColumnName = "dic_id")
    private Dictionary dictionary;

    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn (name = "usr_id", referencedColumnName = "usr_id")
    private User user;

    @Enumerated( EnumType.STRING)
    @Column (name = "ing_status")
    private IngestionStatus ingStatus;

    @CreationTimestamp 
    @Column (name = "ing_inicio")
    private LocalDateTime startTime;

    public DataIngestion() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Dictionary getDictionary() {
        return dictionary;
    }

    public void setDictionary(Dictionary dictionary) {
        this.dictionary = dictionary;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public IngestionStatus getIngStatus() {
        return ingStatus;
    }

    public void setIngStatus(IngestionStatus ingStatus) {
        this.ingStatus = ingStatus;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
}