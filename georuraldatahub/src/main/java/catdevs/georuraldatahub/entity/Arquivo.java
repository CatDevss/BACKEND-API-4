package catdevs.georuraldatahub.entity;

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
@Table(name = "arquivo")
public class Arquivo {

    @Id
    @Column(name = "arq_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id", referencedColumnName = "ver_id")
    private Versao versao;

    @Column(name = "arq_nome")
    private String nome;

    @Column(name = "arq_formato")
    private String formatoArquivo;

    @Column(name = "arq_hash")
    private String hash;

    @Column(name = "arq_localizacao")
    private String localizacao;

    public Arquivo(){
    }

    public Arquivo(Versao versao, String nome, String formatoArquivo, String hash, String localizacao) {
        this.versao = versao;
        this.nome = nome;
        this.formatoArquivo = formatoArquivo;
        this.hash = hash;
        this.localizacao = localizacao;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Versao getVersao() {
        return versao;
    }

    public void setVersao(Versao versao) {
        this.versao = versao;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getFormatoArquivo() {
        return formatoArquivo;
    }

    public void setFormatoArquivo(String formatoArquivo) {
        this.formatoArquivo = formatoArquivo;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getLocalizacao() {
        return localizacao;
    }

    public void setLocalizacao(String localizacao) {
        this.localizacao = localizacao;
    }
}