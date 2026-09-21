package catdevs.georuraldatahub.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "conjunto")
public class Conjunto {

    @Id
    @Column(name = "con_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "con_nome", nullable = false)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fon_id")
    private Fonte fonte;

    public Conjunto() {
    }

    public Conjunto(String nome, Fonte fonte) {
        this.nome = nome;
        this.fonte = fonte;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Fonte getFonte() {
        return fonte;
    }

    public void setFonte(Fonte fonte) {
        this.fonte = fonte;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }
}