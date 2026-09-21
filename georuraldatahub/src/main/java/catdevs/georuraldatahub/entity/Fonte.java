package catdevs.georuraldatahub.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name="fonte")
public class Fonte {
    @Id
    @Column(name = "fon_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fon_nome", nullable = false, unique = true)
    private String nome;

    @Column(name = "fon_data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "fon_url", nullable = false)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usr_id")
    private Usuario usuario;

    public Fonte(){
    }

    public Fonte(String nome, LocalDateTime dataCriacao, String url, Usuario usuario) {
        this.nome = nome;
        this.dataCriacao = dataCriacao;
        this.url = url;
        this.usuario = usuario;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }
}