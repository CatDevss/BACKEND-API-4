package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.LayerInfoDTO;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FileRepository;
import catdevs.georuraldatahub.repository.LayerRepository;
import catdevs.georuraldatahub.repository.VersionRepository;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LayerService {

    private static final Set<String> RENDERABLE =
            Set.of("geojson", "json", "csv", "zip", "gpkg");

    /**
     * Status que permitem visualizar um arquivo validado.
     */
    private static final Set<VersionStatus> VALIDATED_STATUSES =
            Set.of(
                    VersionStatus.BRUTA,
                    VersionStatus.TRATADA,
                    VersionStatus.PUBLICADA
            );

    /**
     * Status que permitem visualizar o resultado tratado de um arquivo.
     */
    private static final Set<VersionStatus> TREATED_STATUSES =
            Set.of(
                    VersionStatus.TRATADA,
                    VersionStatus.PUBLICADA
            );

    private final FileRepository fileRepository;
    private final DatasetRepository datasetRepository;
    private final BucketService bucketService;
    private final VersionRepository versionRepository;
    private final LayerRepository layerRepository;

    public LayerService(
            FileRepository fileRepository,
            DatasetRepository datasetRepository,
            BucketService bucketService,
            VersionRepository versionRepository,
            LayerRepository layerRepository
    ) {
        this.fileRepository = fileRepository;
        this.datasetRepository = datasetRepository;
        this.bucketService = bucketService;
        this.versionRepository = versionRepository;
        this.layerRepository = layerRepository;
    }

    /**
     * Lista somente os arquivos de um conjunto que possuem formato
     * compatível e uma versão disponível na área validada.
     */
    @Transactional(readOnly = true)
    public List<LayerInfoDTO> listLayers(Long datasetId) {

        if (!datasetRepository.existsById(datasetId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Conjunto não encontrado"
            );
        }

        return fileRepository.findByDatasetId(datasetId)
                .stream()
                .filter(file -> isRenderable(file.getFormatFile()))
                .filter(file ->
                        versionRepository
                                .findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                                        file.getId(),
                                        VALIDATED_STATUSES,
                                        "validada/"
                                )
                                .isPresent()
                )
                .map(file -> new LayerInfoDTO(
                        file.getId(),
                        file.getName(),
                        file.getFormatFile(),
                        file.getSize(),
                        file.getReceivedDate()
                ))
                .toList();
    }

    /**
     * Obtém os dados geográficos de um arquivo.
     *
     * Prioridade:
     * 1. Versão tratada ou publicada mais recente.
     * 2. Versão disponível na localização validada/.
     *
     * Arquivos CSV são convertidos para GeoJSON pelo BucketService.
     */
    @Transactional(readOnly = true)
    public JsonNode getFileLayer(Long fileId) throws IOException {

        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Arquivo não encontrado."
                ));

        if (!isRenderable(file.getFormatFile())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Formato não suportado para visualização: "
                            + file.getFormatFile()
            );
        }

        String location = versionRepository
                .findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(
                        fileId,
                        TREATED_STATUSES
                )
                .or(() ->
                        versionRepository
                                .findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                                        fileId,
                                        VALIDATED_STATUSES,
                                        "validada/"
                                )
                )
                .map(version -> version.getLocation())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Arquivo sem registros aprovados para visualização."
                ));

        return readLayer(location);
    }

    /**
     * Obtém os dados da versão vinculada especificamente à camada.
     *
     * Não consulta a versão mais recente do arquivo, pois isso poderia
     * substituir os dados históricos associados à camada.
     */
    @Transactional(readOnly = true)
    public JsonNode getTreatedLayer(Long layerId) throws IOException {

        var layer = layerRepository.findById(layerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Camada não encontrada."
                ));

        var version = layer.getVersion();

        if (version == null
                || !TREATED_STATUSES.contains(version.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Camada sem resultado tratado aprovado."
            );
        }

        return readLayer(version.getLocation());
    }

    /**
     * Encaminha a leitura de acordo com a extensão do arquivo.
     */
    private JsonNode readLayer(String location) throws IOException {

        if (location == null || location.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A versão não possui uma localização válida."
            );
        }

        String normalizedLocation = location.toLowerCase(Locale.ROOT);

        if (normalizedLocation.endsWith(".csv")) {
            return bucketService.readCsvAsGeoJson(location);
        }

        return bucketService.readGeoJson(location);
    }

    /**
     * Verifica se o formato pode ser exibido como camada geográfica.
     */
    private boolean isRenderable(String format) {

        return format != null
                && RENDERABLE.contains(format.toLowerCase(Locale.ROOT));
    }
}