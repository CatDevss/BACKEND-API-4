package catdevs.georuraldatahub.service;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import catdevs.georuraldatahub.repository.CarPropertyRepository;
import catdevs.georuraldatahub.repository.CarPropertyRepository.Filter;
import catdevs.georuraldatahub.repository.CarPropertyRepository.Property;
import catdevs.georuraldatahub.repository.CarPropertyRepository.PropertyGeometry;
import catdevs.georuraldatahub.repository.EnvironmentalIndicatorRepository.Page;

@Service
public class CarPropertyService {
    public static final int MAX_MAP_FEATURES = 200;
    private static final int MAX_TEXT = 100;
    private static final int MAX_MAP_CHARS = 5_000_000;

    private final CarPropertyRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public CarPropertyService(CarPropertyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<Property> list(String carCode, String municipality, Long layerId, Long datasetId, int page,
            int size) {
        return repository.list(filter(carCode, municipality, layerId, datasetId), page, size);
    }

    /** Imóvel com todos os atributos do arquivo de origem. */
    @Transactional(readOnly = true)
    public String detail(long id) {
        positive(id);
        var found = repository.findDetail(id).orElseThrow(this::notFound);
        var out = mapper.createObjectNode();
        out.put("id", found.property().id());
        describe(out, found.property());
        try {
            out.set("atributos", found.attributes() == null ? null : mapper.readTree(found.attributes()));
        } catch (IOException e) {
            throw new IllegalStateException("Atributos do imóvel " + id + " não são um JSON válido.", e);
        }
        return out.toString();
    }

    /** GeoJSON Feature em EPSG:4326, pronto para o mapa. */
    @Transactional(readOnly = true)
    public String geometry(long id) {
        positive(id);
        return feature(repository.findGeometry(id).orElseThrow(this::notFound)).toString();
    }

    /** FeatureCollection dos imóveis filtrados; "truncated" indica que havia mais que o limite. */
    @Transactional(readOnly = true)
    public String geoJson(String carCode, String municipality, Long layerId, Long datasetId, int limit) {
        var filter = filter(carCode, municipality, layerId, datasetId);
        if (filter.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe ao menos um filtro: codigoCar, municipio, camada ou conjunto.");
        if (limit < 1 || limit > MAX_MAP_FEATURES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O limite deve estar entre 1 e " + MAX_MAP_FEATURES + ".");
        List<PropertyGeometry> rows = repository.geometries(filter, limit + 1);
        boolean truncated = rows.size() > limit;
        var out = mapper.createObjectNode();
        out.put("type", "FeatureCollection");
        out.put("truncated", truncated);
        var features = out.putArray("features");
        long chars = 0;
        for (var row : rows.subList(0, Math.min(rows.size(), limit))) {
            chars += row.geometry() == null ? 0 : row.geometry().length();
            if (chars > MAX_MAP_CHARS)
                throw new IllegalArgumentException("Mapa excede o limite de tamanho. Reduza o limite ou filtre mais.");
            features.add(feature(row));
        }
        return out.toString();
    }

    private ObjectNode feature(PropertyGeometry row) {
        var feature = mapper.createObjectNode();
        feature.put("type", "Feature");
        feature.put("id", row.property().id());
        try {
            feature.set("geometry", row.geometry() == null ? null : mapper.readTree(row.geometry()));
        } catch (IOException e) {
            throw new IllegalStateException("Geometria do imóvel " + row.property().id() + " inválida.", e);
        }
        describe(feature.putObject("properties"), row.property());
        return feature;
    }

    private void describe(ObjectNode node, Property p) {
        node.put("codigoCar", p.carCode());
        node.put("municipio", p.municipality());
        node.put("camadaId", p.layerId());
        node.put("camada", p.layer());
        node.put("conjuntoId", p.datasetId());
        node.put("conjunto", p.dataset());
        node.put("versaoId", p.versionId());
        node.put("arquivoId", p.fileId());
        node.put("arquivo", p.file());
        node.put("srid", p.srid());
    }

    private static Filter filter(String carCode, String municipality, Long layerId, Long datasetId) {
        if (layerId != null)
            positive(layerId);
        if (datasetId != null)
            positive(datasetId);
        return new Filter(text(carCode), text(municipality), layerId, datasetId);
    }

    private static String text(String value) {
        if (value == null || value.isBlank())
            return null;
        var trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de texto muito longo.");
        return trimmed;
    }

    private static void positive(long id) {
        if (id <= 0)
            throw new IllegalArgumentException("Informe um ID positivo.");
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Imóvel do CAR não encontrado.");
    }
}