package catdevs.georuralprocessing.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity @Table(name = "execucao_tratamento")
@Getter @Setter @NoArgsConstructor
public class TreatmentExecution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "etr_id") private Long id;
    @Column(name = "arq_id", nullable = false, updatable = false) private Long fileId;
    @Column(name = "ver_id_entrada", nullable = false, updatable = false) private Long inputVersionId;
    @Column(name = "usr_id", nullable = false, updatable = false) private Long userId;
    @Column(name = "etr_status", nullable = false) private String status;
    @Column(name = "etr_inicio", nullable = false, updatable = false) private LocalDateTime startedAt;
    @Column(name = "etr_fim") private LocalDateTime finishedAt;
    @Lob @Column(name = "etr_parametros", nullable = false, updatable = false) private String parameters;
    @Lob @Column(name = "etr_rastreabilidade") private String provenance;
    @Column(name = "etr_erro", length = 500) private String error;
    @Column(name = "ver_id_saida") private Long outputVersionId;
    @Column(name = "cam_id") private Long layerId;
    @Column(name = "ras_id") private Long rasterId;
    @Column(name = "etr_total_entrada") private Integer inputCount;
    @Column(name = "etr_total_saida") private Integer outputCount;
}
