package catdevs.georuraldatahub.entity;

import java.time.LocalDateTime;

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
    @JoinColumn(name = "arq_id", referencedColumnName = "arq_id")
    private File file;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usr_id", referencedColumnName = "usr_id")
    private User user;

    @Column(name = "qua_status")
    private String status;

    @Column(name = "qua_motivo")
    private String reason;

    @Column(name = "qua_data_entrada")
    private LocalDateTime dateEntry;

    public Quarantine() {
    }

    public Quarantine(File file, User user, String status, String reason, LocalDateTime dateEntry) {
        this.file = file;
        this.user = user;
        this.status = status;
        this.reason = reason;
        this.dateEntry = dateEntry;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public LocalDateTime getDateEntry() {
        return dateEntry;
    }

    public void setDateEntry(LocalDateTime dateEntry) {
        this.dateEntry = dateEntry;
    }
}