package catdevs.georuraldatahub.collection;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import catdevs.georuraldatahub.collection.CollectedData.*;

@Component
public class PublicDataConnectors {
    private static final String WFS = "https://terrabrasilis.dpi.inpe.br/geoserver/ows";
    private static final Map<String, String> BIOMES = Map.of(
        "CERRADO", "prodes-cerrado-nb:yearly_deforestation",
        "MATA_ATLANTICA", "prodes-mata-atlantica-nb:yearly_deforestation");
    private final OfficialDataClient http;
    private final BiomesShapefileReader shapes;
    private final CollectionProperties limits;
    private final CollectionCatalog catalog;
    private final ParanaSpatialFilter parana;
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public PublicDataConnectors(OfficialDataClient http, BiomesShapefileReader shapes,
            CollectionProperties limits, CollectionCatalog catalog) {
        this.http = http; this.shapes = shapes; this.limits = limits; this.catalog = catalog;
        this.parana = new ParanaSpatialFilter(http);
    }
    public CollectedData collect(CollectionRequest request) throws IOException, InterruptedException {
        if (request == null || request.product() == null) throw new IllegalArgumentException("Informe o produto.");
        try (var budget = http.collectionBudget()) {
            return switch (request.product()) {
                case IBGE_MUNICIPIOS -> municipalities(request);
                case IBGE_BIOMAS -> biomes(request);
                case INPE_PRODES -> prodes(request);
                case INPE_QUEIMADAS -> fires(request);
            };
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("Competência inválida para o produto.", e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw OfficialDataClient.upstream("O canal oficial retornou conteúdo inválido para o produto.");
        }
    }
    private int year(String competence) {
        if (competence == null || !competence.matches("[12][0-9]{3}")) throw new IllegalArgumentException("Competência deve ser um ano YYYY.");
        int year = Integer.parseInt(competence);
        if (year > LocalDate.now(ZoneId.of("America/Sao_Paulo")).getYear()) throw new IllegalArgumentException("Ano futuro não permitido.");
        return year;
    }
    private CollectedData municipalities(CollectionRequest r) throws IOException, InterruptedException {
        int year = year(r.competence());
        String municipality = r.municipality();
        if (municipality != null && !municipality.matches("41[0-9]{5}")) throw new IllegalArgumentException("Informe um município do Paraná: código IBGE de sete dígitos iniciado por 41.");
        if (r.bbox() != null || r.biome() != null) throw new IllegalArgumentException("Malha municipal aceita apenas ano e código IBGE opcional.");
        if (year >= 2023) return municipalArchive(r, year, municipality);
        String path = municipality == null ? "/estados/PR" : "/municipios/" + municipality;
        String url = "https://servicodados.ibge.gov.br/api/v3/malhas" + path
                + "?formato=application%2Fvnd.geo%2Bjson&qualidade=maxima&periodo=" + year
                + (municipality == null ? "&intrarregiao=municipio" : "");
        Original raw = download("malha_" + year + ".geojson", url, "application/geo+json");
        JsonNode mesh = featureCollection(raw.content());
        Set<String> codes = new HashSet<>();
        for (JsonNode feature : mesh.path("features")) {
            String code = feature.path("properties").path("codarea").asText();
            if (!code.matches("41[0-9]{5}") || !codes.add(code) || municipality != null && !municipality.equals(code))
                throw OfficialDataClient.upstream("Resposta IBGE não corresponde aos municípios solicitados.");
        }
        if (codes.isEmpty() || municipality == null && codes.size() < 2)
            throw OfficialDataClient.upstream("IBGE não retornou a subdivisão municipal solicitada.");
        return result(r, "malha_" + (municipality == null ? "PR" : municipality) + "_" + year + ".geojson",
                "geojson", raw.content(), List.of(raw), municipality == null ? "Paraná (UF 41), municípios" : "Paraná, município " + municipality, "geojson");
    }
    private CollectedData municipalArchive(CollectionRequest r, int year, String municipality) throws IOException, InterruptedException {
        String filename = "PR_Municipios_" + year + ".zip";
        String url = "https://geoftp.ibge.gov.br/organizacao_do_territorio/malhas_territoriais/malhas_municipais/municipio_"
                + year + "/UFs/PR/" + filename;
        Original original = download(filename, url, "application/zip");
        JsonNode mesh;
        try { mesh = featureCollection(shapes.toGeoJson(original.content())); }
        catch (IOException | IllegalArgumentException e) {
            throw OfficialDataClient.upstream("Malha municipal IBGE incompatível: " + e.getMessage());
        }
        var output = mapper.createObjectNode().put("type", "FeatureCollection");
        var features = output.putArray("features");
        Set<String> codes = new HashSet<>();
        for (var feature : mesh.path("features")) {
            http.checkCollectionDeadline();
            String code = feature.path("properties").path("CD_MUN").asText();
            if (!code.matches("41[0-9]{5}") || !codes.add(code))
                throw OfficialDataClient.upstream("ZIP IBGE contém código municipal fora do Paraná ou duplicado.");
            if (municipality == null || municipality.equals(code)) {
                var copy = ((ObjectNode) feature).deepCopy();
                ((ObjectNode) copy.path("properties")).put("codarea", code);
                features.add(copy);
            }
        }
        if (features.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Município solicitado não encontrado na malha IBGE de " + year + ".");
        if (municipality == null && codes.size() < 2) throw OfficialDataClient.upstream("ZIP IBGE não contém a malha municipal estadual.");
        return result(r, "malha_" + (municipality == null ? "PR" : municipality) + "_" + year + ".geojson",
                "geojson", mapper.writeValueAsBytes(output), List.of(original),
                municipality == null ? "Paraná (UF 41), municípios" : "Paraná, município " + municipality, "shapefile.zip");
    }

    private CollectedData biomes(CollectionRequest r) throws IOException, InterruptedException {
        int year = year(r.competence());
        String filename = switch (year) {
            case 2019 -> "Biomas_250mil.zip";
            case 2025 -> "2025_Biomas-e-Sistema-Costeiro-Marinho-do-Brasil-1-250000_shp.zip";
            default -> throw new IllegalArgumentException("Edições de biomas suportadas: 2019 e 2025.");
        };
        if (r.municipality() != null || r.bbox() != null || r.biome() != null)
            throw new IllegalArgumentException("A coleta de biomas já é restrita ao Paraná; não aceita filtros adicionais.");
        Original raw = download(filename, "https://geoftp.ibge.gov.br/informacoes_ambientais/estudos_ambientais/biomas/vetores/" + filename, "application/zip");
        byte[] normalized;
        try { normalized = shapes.toGeoJson(raw.content()); }
        catch (IOException | IllegalArgumentException e) { throw OfficialDataClient.upstream("Arquivo de biomas incompatível: " + e.getMessage()); }
        var boundary = parana.boundary();
        normalized = parana.clip(normalized, boundary);
        return result(r, "biomas_PR_" + year + ".geojson", "geojson", normalized,
                List.of(raw, boundary.original()), "Paraná (UF 41); interseção dos biomas com o limite estadual", "shapefile.zip");
    }
    private CollectedData fires(CollectionRequest r) throws IOException, InterruptedException {
        if (r.competence() == null) throw new IllegalArgumentException("Informe uma data YYYY-MM-DD.");
        LocalDate date = LocalDate.parse(r.competence());
        if (!date.isBefore(LocalDate.now(ZoneId.of("America/Sao_Paulo"))))
            throw new IllegalArgumentException("Selecione um dia encerrado para evitar coleta diária incompleta.");
        if (r.municipality() != null || r.biome() != null || r.bbox() != null)
            throw new IllegalArgumentException("Focos diários já são filtrados para o Paraná; não aceitam filtros adicionais.");
        String filename = "focos_diario_br_" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        Original raw = download(filename, "https://dataserver-coids.inpe.br/queimadas/queimadas/focos/csv/diario/Brasil/" + filename, "text/csv");
        byte[] filtered = paranaFires(raw.content());
        return result(r, filename.replace("_br_", "_PR_"), "csv", filtered, List.of(raw),
                "Paraná (UF 41); estado_id=41 e municipio_id iniciado por 41; todos os satélites", "csv");
    }
    private CollectedData prodes(CollectionRequest r) throws IOException, InterruptedException {
        int year = year(r.competence());
        String layer = BIOMES.get(r.biome() == null ? "" : r.biome().toUpperCase(Locale.ROOT));
        if (layer == null || !(r.biome().equalsIgnoreCase("CERRADO") || r.biome().equalsIgnoreCase("MATA_ATLANTICA")))
            throw new IllegalArgumentException("Biomas do Paraná aceitos: CERRADO e MATA_ATLANTICA.");
        if (r.municipality() != null) throw new IllegalArgumentException("Use bbox para recortar o PRODES.");
        bboxFilter(r.bbox());
        var boundary = parana.boundary();
        var envelope = boundary.geometry().getEnvelopeInternal();
        if (r.bbox() != null) envelope = envelope.intersection(new org.locationtech.jts.geom.Envelope(
                r.bbox().get(0), r.bbox().get(2), r.bbox().get(1), r.bbox().get(3)));
        if (envelope.isNull()) throw new IllegalArgumentException("O bbox não intersecta o Paraná.");
        List<Double> effectiveBbox = List.of(envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY());
        String filter = "year=" + year + " AND state IN ('PR','PARANA','PARANÁ')" + bboxFilter(effectiveBbox);
        int pageSize = limits.getPageSize();
        if (pageSize < 1 || pageSize > 10000 || limits.getMaxFeatures() < 1) throw new IllegalArgumentException("Limites de paginação inválidos.");
        List<Original> originals = new ArrayList<>();
        ObjectNode output = mapper.createObjectNode().put("type", "FeatureCollection");
        ArrayNode features = output.putArray("features");
        Set<String> identities = new HashSet<>();
        long expected = -1, totalBytes = 0;
        for (int start = 0; ; start += pageSize) {
            String url = WFS + "?service=WFS&version=2.0.0&request=GetFeature&typeNames=" + encode(layer)
                + "&outputFormat=application%2Fjson&srsName=CRS%3A84&sortBy=uuid&count=" + pageSize
                + "&startIndex=" + start + "&CQL_FILTER=" + encode(filter);
            Original raw = download("prodes_" + start + ".geojson", url, "application/geo+json");
            totalBytes += raw.content().length;
            if (totalBytes > limits.getMaxTotalBytes()) throw OfficialDataClient.upstream("Coleta PRODES excede o limite total; solicite um recorte bbox menor.");
            JsonNode page = featureCollection(raw.content());
            JsonNode matched = page.has("numberMatched") ? page.get("numberMatched") : page.path("totalFeatures");
            if (!matched.isIntegralNumber() || matched.asLong() < 0) throw OfficialDataClient.upstream("WFS não informou total verificável de feições.");
            long count = matched.asLong();
            if (expected < 0) expected = count;
            if (count != expected) throw OfficialDataClient.upstream("Base PRODES mudou durante a paginação; repita a coleta.");
            if (expected > limits.getMaxFeatures()) throw OfficialDataClient.upstream("PRODES excede o limite de feições; solicite recorte bbox menor.");
            JsonNode batch = page.path("features");
            if (batch.size() > pageSize || start + batch.size() > expected) throw OfficialDataClient.upstream("Paginação WFS inconsistente.");
            for (JsonNode feature : batch) {
                String id = feature.path("properties").path("uuid").asText();
                if (id.isBlank() || !identities.add(id)) throw OfficialDataClient.upstream("Página WFS repetida ou UUID ausente.");
                if (feature.path("properties").path("year").asInt(-1) != year)
                    throw OfficialDataClient.upstream("Fonte ignorou o filtro anual solicitado.");
                String state = feature.path("properties").path("state").asText().toUpperCase(Locale.ROOT);
                if (!Set.of("PR", "PARANA", "PARANÁ").contains(state))
                    throw OfficialDataClient.upstream("Fonte ignorou o filtro de UF do Paraná.");
                features.add(feature);
            }
            originals.add(raw);
            if (features.size() == expected) break;
            if (batch.isEmpty()) throw OfficialDataClient.upstream("WFS interrompeu a paginação antes do total.");
            if (batch.size() != pageSize) throw OfficialDataClient.upstream("WFS retornou uma página incompleta antes do total.");
        }
        originals.add(boundary.original());
        byte[] clipped = parana.clip(mapper.writeValueAsBytes(output), boundary);
        return result(r, "prodes_PR_" + r.biome().toLowerCase(Locale.ROOT) + "_" + year + ".geojson", "geojson",
                clipped, originals, "Paraná (UF 41); " + r.biome().toUpperCase(Locale.ROOT)
                + "; recorte estadual exato sobre a malha IBGE 2022; bbox=" + effectiveBbox, "WFS GeoJSON paginado");
    }
    private byte[] paranaFires(byte[] content) throws IOException {
        try (var reader = new catdevs.georuraldatahub.validation.CsvRecordValidator.TableReader(new java.io.ByteArrayInputStream(content))) {
            List<String> headers = reader.readRecord();
            if (headers == null) throw new IOException("CSV de focos vazio.");
            headers = headers.stream().map(String::trim).toList();
            if (!new HashSet<>(headers).containsAll(Set.of("id", "lat", "lon", "data_hora_gmt", "satelite", "estado_id", "municipio_id")))
                throw OfficialDataClient.upstream("CSV de focos não contém o cabeçalho oficial esperado.");
            int state = headers.indexOf("estado_id"), municipality = headers.indexOf("municipio_id");
            StringBuilder output = new StringBuilder(catdevs.georuraldatahub.validation.CsvRecordValidator.encode(headers)).append('\n');
            List<String> row;
            while ((row = reader.readRecord()) != null) {
                if (row.size() == 1 && row.getFirst().isBlank()) continue;
                if (row.size() != headers.size()) throw new IOException("CSV de focos com quantidade de colunas inválida.");
                if (row.get(state).trim().equals("41") && row.get(municipality).trim().matches("41[0-9]{5}"))
                    output.append(catdevs.georuraldatahub.validation.CsvRecordValidator.encode(row)).append('\n');
            }
            return output.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
    private String bboxFilter(List<Double> bbox) {
        if (bbox == null) return "";
        if (bbox.size() != 4 || bbox.stream().anyMatch(n -> n == null || !Double.isFinite(n))
                || bbox.get(0) < -180 || bbox.get(2) > 180 || bbox.get(1) < -90 || bbox.get(3) > 90
                || bbox.get(0) >= bbox.get(2) || bbox.get(1) >= bbox.get(3)) throw new IllegalArgumentException("bbox deve ser [oeste,sul,leste,norte].");
        return " AND BBOX(geom," + bbox.get(0) + "," + bbox.get(1) + "," + bbox.get(2) + "," + bbox.get(3) + ",'EPSG:4326')";
    }
    private JsonNode featureCollection(byte[] content) throws IOException {
        JsonNode root = mapper.readTree(content);
        if (root == null || !root.path("type").asText().equals("FeatureCollection") || !root.path("features").isArray())
            throw OfficialDataClient.upstream("Fonte não retornou um GeoJSON FeatureCollection.");
        return root;
    }
    private Original download(String filename, String url, String type) throws IOException, InterruptedException {
        return new Original(filename, url, type, http.download(URI.create(url)));
    }
    private CollectedData result(CollectionRequest r, String filename, String format, byte[] content,
            List<Original> originals, String scope, String originFormat) {
        http.checkCollectionDeadline();
        if (content.length > limits.getMaxTotalBytes()) throw OfficialDataClient.upstream("Conteúdo convertido excede o limite total da coleta.");
        var entry = catalog.entry(r.product());
        var metadata = new Metadata(r.product().name(), r.competence(), format, originFormat, scope, Instant.now().toString(),
                entry.documentation(), r.product() == CollectionRequest.Product.IBGE_BIOMAS || r.product() == CollectionRequest.Product.INPE_PRODES
                    ? List.of(entry.limitations(), ParanaSpatialFilter.LIMITATION,
                        "Atributos de área da origem não são recalculados pelo recorte; derive áreas somente após a reprojeção equivalente.")
                    : List.of(entry.limitations(), "Escopo exclusivo do Paraná (UF 41)."), originals.stream().map(o ->
                    new OriginalMetadata(o.filename(), o.origin(), o.contentType(), o.content().length, o.sha256())).toList());
        return new CollectedData(filename, format, content, List.copyOf(originals), metadata);
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
