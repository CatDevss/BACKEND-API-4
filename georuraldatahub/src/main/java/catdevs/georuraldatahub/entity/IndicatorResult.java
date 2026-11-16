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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "resultado_indicador")
public class IndicatorResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rin_id")
    private Long id;

    @Column(name = "rin_codigo", nullable = false, length = 100)
    private String indicatorCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "con_id", referencedColumnName = "con_id", nullable = false)
    private Dataset dataset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cam_id", referencedColumnName = "cam_id")
    private Layer layer;

    @Column(name = "rin_referencia_imovel", length = 150)
    private String propertyReference;

    @Column(name = "rin_unidade", length = 50)
    private String unit;

    @Lob
    @Column(name = "rin_dados", nullable = false, columnDefinition = "CLOB")
    private String resultData;

    @CreationTimestamp
    @Column(name = "rin_calculado_em", nullable = false, updatable = false)
    private LocalDateTime calculatedAt;

    public Long getId() { return id; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public Dataset getDataset() { return dataset; }
    public void setDataset(Dataset dataset) { this.dataset = dataset; }
    public Layer getLayer() { return layer; }
    public void setLayer(Layer layer) { this.layer = layer; }
    public String getPropertyReference() { return propertyReference; }
    public void setPropertyReference(String propertyReference) { this.propertyReference = propertyReference; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getResultData() { return resultData; }
    public void setResultData(String resultData) { this.resultData = resultData; }
    public LocalDateTime getCalculatedAt() { return calculatedAt; }
}