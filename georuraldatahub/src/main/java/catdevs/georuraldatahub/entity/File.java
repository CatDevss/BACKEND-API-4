package catdevs.georuraldatahub.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "arquivo")
public class File {

    @Id
    @Column(name = "arq_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ing_id", referencedColumnName = "ing_id")
    private DataIngestion ingestion;

    @Column(name = "arq_nome")
    private String name;

    @Column(name = "arq_formato")
    private String formatFile;

    @Column(name = "arq_tamanho")
    private Long size;

    @Column(name = "arq_hash", updatable = false)
    private String hash;

    @Column(name = "arq_localizacao", updatable = false)
    private String location;

    @CreationTimestamp 
    @Column(name = "arq_data_recebimento")
    private LocalDateTime receivedDate;

    @ManyToMany (mappedBy = "files")
    private List<Dataset> datasets;

    public File(){
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DataIngestion getIngestion() {
        return ingestion;
    }

    public void setIngestion(DataIngestion ingestion) {
        this.ingestion = ingestion;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFormatFile() {
        return formatFile;
    }

    public void setFormatFile(String formatFile) {
        this.formatFile = formatFile;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        if (location == null || !location.startsWith("bruta/"))
            throw new IllegalArgumentException("ARQUIVO deve apontar para o original na zona bruta.");
        if (this.location != null && !this.location.equals(location))
            throw new IllegalStateException("A localização do original é imutável; atualize a cópia na versão.");
        this.location = location;
    }

    public LocalDateTime getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(LocalDateTime receivedDate) {
        this.receivedDate = receivedDate;
    }

    public List<Dataset> getDatasets() {
        return datasets;
    }

    public void setDatasets(List<Dataset> datasets) {
        this.datasets = datasets;
    }
}