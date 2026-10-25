package catdevs.georuraldatahub.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

@Entity
@Table(name = "feicao")
public class Feature {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "fei_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cam_id", updatable = false)
    private Layer layer;
    @Column(name = "fei_id_origem", length = 100, updatable = false)
    private String originId;
    // Oracle JDBC retorna o nome sem MDSYS; manter esse nome para validar o schema sem perder o binder espacial.
    @JdbcTypeCode(SqlTypes.GEOMETRY) @Column(name = "fei_geometria", columnDefinition = "SDO_GEOMETRY", updatable = false)
    private Geometry geometry;
    @Lob @Column(name = "fei_atributos", updatable = false)
    private String attributes;
    public Long getId() { return id; }
    public Layer getLayer() { return layer; }
    public void setLayer(Layer value) { layer = value; }
    public String getOriginId() { return originId; }
    public void setOriginId(String value) { originId = value; }
    public Geometry getGeometry() { return geometry; }
    public void setGeometry(Geometry value) { geometry = value; }
    public String getAttributes() { return attributes; }
    public void setAttributes(String value) { attributes = value; }
}
