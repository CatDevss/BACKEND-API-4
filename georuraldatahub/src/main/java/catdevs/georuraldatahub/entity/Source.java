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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "fonte")
public class Source {

    @Id
    @Column(name = "fon_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome da fonte é obrigatório.")
    @Size(max = 255, message = "O nome da fonte deve ter no máximo 255 caracteres.")
    @Column(name = "fon_nome", nullable = false, length = 255)
    private String name;

    @NotBlank(message = "A URL da fonte é obrigatória.")
    @Size(max = 2048, message = "A URL deve ter no máximo 2048 caracteres.")
    @Column(name = "fon_url", nullable = false, length = 2048)
    private String url;

    @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres.")
    @Column(name = "fon_descricao", length = 2000)
    private String description;

    @CreationTimestamp
    @Column(name = "fon_data_criacao", nullable = false, updatable = false)
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}