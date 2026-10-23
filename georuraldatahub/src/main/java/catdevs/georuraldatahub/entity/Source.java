package catdevs.georuraldatahub.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;

@Entity
@Table(name = "fonte")
public class Source {

    @Id
    @Column(name = "fon_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fon_nome")
    private String name;

    @Column(name = "fon_url")
    private String url;

    @Column(name = "fon_descricao")
    private String description;

    @Column(name = "fon_ativa", nullable = false)
    private boolean active = true;

    @CreationTimestamp 
    @Column(name = "fon_data_criacao")
    private LocalDateTime dateCreation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usr_id", referencedColumnName = "usr_id")
    private User user;

    public Source() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}