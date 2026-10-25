package catdevs.georuralprocessing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

@Entity @Table(name = "raster")
@Getter @Setter @NoArgsConstructor
public class RasterMetadata {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "ras_id") private Long id;
    @Column(name = "ver_id", nullable = false, updatable = false) private Long versionId;
    @Column(name = "usr_id", nullable = false, updatable = false) private Long userId;
    @Column(name = "ras_localizacao", nullable = false, updatable = false, length = 500) private String location;
    @Column(name = "ras_srid_origem", nullable = false, updatable = false) private Integer sourceSrid;
    @Column(name = "ras_largura", nullable = false, updatable = false) private Integer width;
    @Column(name = "ras_altura", nullable = false, updatable = false) private Integer height;
    @Column(name = "ras_bandas", nullable = false, updatable = false) private Integer bandCount;
    @Column(name = "ras_pixel_largura", nullable = false, updatable = false) private Double pixelWidth;
    @Column(name = "ras_pixel_altura", nullable = false, updatable = false) private Double pixelHeight;
    @Lob @Column(name = "ras_metadados", nullable = false, updatable = false) private String metadata;
    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "ras_cobertura", nullable = false, updatable = false, columnDefinition = "MDSYS.SDO_GEOMETRY")
    private Geometry footprint;
}
