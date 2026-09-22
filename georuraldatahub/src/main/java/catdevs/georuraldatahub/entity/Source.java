package catdevs.georuraldatahub.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name="fonte")
public class Source {
    @Id
    @Column(name = "fon_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fon_nome", nullable = false, unique = true)
    private String name;

    @Column(name = "fon_data_criacao", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "fon_url", nullable = false)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usr_id")
    private User user;

    public Source(){
    }

    public Source(String name, LocalDateTime dateCreation, String url, User user) {
        this.name = name;
        this.dateCreation = dateCreation;
        this.url = url;
        this.user = user;
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

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}