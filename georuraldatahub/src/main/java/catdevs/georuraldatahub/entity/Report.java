package catdevs.georuraldatahub.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Relatório de inconsistências associado a um arquivo.
 * Relatórios em RASCUNHO podem ser editados/excluídos; ao finalizar,
 * passam para HISTORICO e ficam imutáveis.
 */
@Entity
@Table(name = "relatorio_inconsistencia")
public class Report {

    public static final String STATUS_DRAFT = "RASCUNHO";
    public static final String STATUS_HISTORICAL = "HISTORICO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rel_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "arq_id", referencedColumnName = "arq_id", nullable = false)
    private File file;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usr_id", referencedColumnName = "usr_id", nullable = false)
    private User user;

    @Column(name = "rel_titulo", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "rel_conteudo", nullable = false)
    private String content;

    @Column(name = "rel_status", nullable = false, length = 20)
    private String status = STATUS_DRAFT;

    @CreationTimestamp
    @Column(name = "rel_criado_em", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "rel_atualizado_em")
    private LocalDateTime updatedAt;

    @Column(name = "rel_finalizado_em")
    private LocalDateTime finalizedAt;

    public Report() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public File getFile() { return file; }
    public void setFile(File file) { this.file = file; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(LocalDateTime finalizedAt) { this.finalizedAt = finalizedAt; }
}