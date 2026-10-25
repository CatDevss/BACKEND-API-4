package catdevs.georuraldatahub.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import catdevs.georuraldatahub.entity.enums.GeometryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "camada")
public class Layer {

    @Id
    @Column(name = "cam_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "con_id", referencedColumnName = "con_id", updatable = false)
    private Dataset dataset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ver_id", referencedColumnName = "ver_id", updatable = false)
    private Version version;

    @Column(name = "cam_nome", nullable = false)
    private String name;

    @Column(name = "cam_descricao")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "cam_tipo_geom", nullable = false, updatable = false)
    private GeometryType geometryType;

    @Column(name = "cam_srid", nullable = false, updatable = false)
    private Integer srid;

    @CreationTimestamp
    @Column(name = "cam_data_criacao", updatable = false)
    private LocalDateTime createdAt;

    public Layer() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public GeometryType getGeometryType() {
        return geometryType;
    }

    public void setGeometryType(GeometryType geometryType) {
        this.geometryType = geometryType;
    }

    public Integer getSrid() {
        return srid;
    }

    public void setSrid(Integer srid) {
        this.srid = srid;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}