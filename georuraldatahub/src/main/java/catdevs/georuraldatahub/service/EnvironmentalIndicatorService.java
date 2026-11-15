package catdevs.georuraldatahub.service;

import java.sql.SQLException;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import catdevs.georuraldatahub.repository.EnvironmentalIndicatorRepository;

@Service
public class EnvironmentalIndicatorService {
    private final EnvironmentalIndicatorRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public EnvironmentalIndicatorService(EnvironmentalIndicatorRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly=true)
    public EnvironmentalIndicatorRepository.Page<EnvironmentalIndicatorRepository.LayerOption> layers(String search,int page,int size) {
        return repository.layers(search,page,size);
    }

    @Transactional(readOnly=true)
    public EnvironmentalIndicatorRepository.Page<EnvironmentalIndicatorRepository.FeatureOption> features(long layer,String search,String field,int page,int size) {
        positive(layer);
        return repository.features(layer,search,field,page,size);
    }

    @Transactional
    public EnvironmentalIndicatorRepository.ImportedLayer combineLayers(
            long datasetId, String name, java.util.List<Long> layers) {
        positive(datasetId);
        if (name == null || name.isBlank() || name.length() > 200
                || layers == null || layers.size() < 2 || layers.size() > 100
                || layers.stream().anyMatch(id -> id == null || id <= 0)
                || new java.util.HashSet<>(layers).size() != layers.size()) {
            throw new IllegalArgumentException("Informe nome e de 2 a 100 IDs de camadas distintos.");
        }
        try {
            return repository.combineLayers(datasetId, name, mapper.valueToTree(layers).toString());
        } catch (DataAccessException e) { throw translate(e); }
    }

    @Transactional
    public EnvironmentalIndicatorRepository.ImportedLayer importGeoJson(long datasetId, long versionId) {
        positive(datasetId);
        positive(versionId);
        try {
            return repository.importGeoJson(datasetId, versionId);
        } catch (DataAccessException e) {
            throw translate(e);
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public JsonNode calculate(long propertyId, JsonNode parameters) {
        positive(propertyId);
        if (parameters == null || !parameters.isObject())
            throw new IllegalArgumentException("Informe os parametros em um objeto JSON.");
        try {
            return withSources(repository.calculate(propertyId, parameters.toString()));
        } catch (DataAccessException e) {
            throw translate(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Oracle retornou um resultado JSON invalido.", e);
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public JsonNode calculateIcv(long propertyId, JsonNode parameters) {
        positive(propertyId);
        if (parameters == null || !parameters.isObject())
            throw new IllegalArgumentException("Informe os parametros em um objeto JSON.");
        try {
            return withSources(repository.calculateIcv(propertyId, parameters.toString()));
        } catch (DataAccessException e) {
            throw translate(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Oracle retornou JSON invalido.", e);
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public JsonNode calculateIrl(long propertyId, JsonNode parameters) {
        return calculateIndicator(propertyId, parameters, "irl");
    }

    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public JsonNode calculateIapp(long propertyId, JsonNode parameters) {
        return calculateIndicator(propertyId, parameters, "iapp");
    }

    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public JsonNode calculateIsap(long propertyId, JsonNode parameters) {
        return calculateIndicator(propertyId, parameters, "isap");
    }

    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public JsonNode calculateIae(long propertyId, JsonNode parameters) {
        return calculateIndicator(propertyId, parameters, "iae");
    }

    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public JsonNode calculateIdesmat(long propertyId, JsonNode parameters) {
        return calculateIndicator(propertyId, parameters, "idesmat");
    }

    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public JsonNode calculateIfc(long propertyId, JsonNode parameters) {
        return calculateIndicator(propertyId, parameters, "ifc");
    }

    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public JsonNode calculateIndicator(long propertyId, JsonNode parameters, String indicator) {
        positive(propertyId);
        if (parameters == null || !parameters.isObject())
            throw new IllegalArgumentException("Informe os parametros em um objeto JSON.");
        try {
            return withSources(repository.calculateIndicator(propertyId, parameters.toString(), indicator));
        } catch (DataAccessException e) {
            throw translate(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Oracle retornou JSON invalido.", e);
        }
    }

    private JsonNode withSources(String json) throws java.io.IOException {
        JsonNode result = mapper.readTree(json);
        var cache = new java.util.HashMap<Long,java.util.Map<String,Object>>();
        if (result.path("fontes").isArray()) for (JsonNode source : result.path("fontes")) {
            if (source instanceof com.fasterxml.jackson.databind.node.ObjectNode obj && source.path("camadaId").canConvertToLong()) {
                var metadata = cache.computeIfAbsent(source.path("camadaId").asLong(),repository::sourceMetadata);
                for (var entry : metadata.entrySet()) if (entry.getValue()!=null)
                    obj.put(entry.getKey().toLowerCase(java.util.Locale.ROOT),entry.getValue().toString());
            }
        }
        return result;
    }

    private static void positive(long id) {
        if (id <= 0)
            throw new IllegalArgumentException("Informe um ID positivo.");
    }

    private RuntimeException translate(DataAccessException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                if (sql.getErrorCode() == 1403)
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Registro ou configuracao ambiental nao encontrado.");
                if (sql.getErrorCode() >= 20100 && sql.getErrorCode() <= 20199) {
                    String message = sql.getMessage().split("\n", 2)[0].replaceFirst("^ORA-\\d+:\\s*", "");
                    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message);
                }
            }
        }
        return error;
    }
}
