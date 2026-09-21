package catdevs.georuraldatahub.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "usuario")
public class Usuario {
    
    @Id
    @Column(name = "usr_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usr_nome", nullable = false)
    private String nome;

    @Column(name = "usr_email", nullable = false, unique = true)
    private String email;

    @Column(name = "usr_senha", nullable = false)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(name = "usr_tipo", nullable = false)
    private TipoUsuario tipoUsuario;

    @Column(name = "usr_status", nullable = false)
    private int usuarioStatus;

    public Usuario(){
    }

    public Usuario(String nome, String email, String senha, TipoUsuario tipoUsuario, int usuarioStatus) {
        this.nome = nome;
        this.email = email;
        this.senha = senha;
        this.tipoUsuario = tipoUsuario;
        this.usuarioStatus = usuarioStatus;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public TipoUsuario getTipoUsuario() {
        return tipoUsuario;
    }

    public void setTipoUsuario(TipoUsuario tipoUsuario) {
        this.tipoUsuario = tipoUsuario;
    }

    public int getUsuarioStatus() {
        return usuarioStatus;
    }

    public void setUsuarioStatus(int usuarioStatus) {
        this.usuarioStatus = usuarioStatus;
    }
}