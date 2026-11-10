package catdevs.georuraldatahub.collection;

import java.io.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.locationtech.jts.geom.Envelope;
import catdevs.georuraldatahub.processing.*;
import static catdevs.georuraldatahub.collection.CollectionRequest.Product;
import static catdevs.georuraldatahub.collection.CollectedData.*;

/** Conectores adicionais reutilizam transporte, paginação, leitura GeoTools, recorte e ingestão existentes. */
final class PublishedDataConnectors {
    private static final String ICMBIO = "https://geoservicos.inde.gov.br/geoserver/ICMBio/ows";
    private static final String FUNAI = "https://geoserver.funai.gov.br/geoserver/Funai/ows";
    private static final String MMA = "https://cnuc-mapserv.mma.gov.br/cgi-bin/mapserv?MAP=%2Fvar%2Fwww%2Fstorage%2Fapp%2Fmapfiles%2Fucs.map";
    private static final String IBAMA = "https://pamgia.ibama.gov.br/server/rest/services/01_Publicacoes_Bases/adm_embargos_ibama_a/MapServer/0";
    private final OfficialDataClient http;
    private final CollectionProperties limits;
    private final CollectionCatalog catalog;
    private final PublishedResources resources;
    private final ParanaSpatialFilter spatial;
    private final RemoteFeatureReader remote;

    PublishedDataConnectors(OfficialDataClient http, CollectionProperties limits, CollectionCatalog catalog) {
        this.http = http; this.limits = limits; this.catalog = catalog;
        resources = new PublishedResources(http); spatial = new ParanaSpatialFilter(http); remote = new RemoteFeatureReader(http, limits);
    }

    CollectedData collect(CollectionRequest request) throws IOException, InterruptedException {
        Product product = request.product();
        if (request.municipality() != null || request.biome() != null)
            throw new IllegalArgumentException("Este produto aceita apenas competência e bbox opcional no Paraná.");
        validateBbox(request.bbox());
        boolean current = "ATUAL".equalsIgnoreCase(request.competence());
        if (!current && product != Product.MMA && product != Product.SFB)
            throw new IllegalArgumentException("Este canal publica somente a base atual. Use competence=ATUAL; ano de criação não representa edição histórica.");
        if (current && product == Product.SFB) throw new IllegalArgumentException("O SFB exige uma edição publicada. Consulte /edicoes.");
        var edition = !current ? resources.resolve(product, request.competence()) : null;
        var boundary = spatial.restrict(spatial.boundary(), request.bbox());
        Envelope bbox = boundary.geometry().getEnvelopeInternal();
        RemoteFeatureReader.Result data;
        String originFormat;
        String competence = current ? "ATUAL" : edition.competence();
        if (edition != null || product == Product.INCRA) {
            String url = edition == null ? PublishedResources.INCRA_ARCHIVE : edition.url();
            byte[] archive = http.download(URI.create(url));
            var processing = new ProcessingProperties();
            processing.setMaxInputBytes(limits.getMaxDownloadBytes());
            processing.setMaxOutputBytes(limits.getMaxTotalBytes());
            processing.setMaxExpandedBytes(limits.getMaxExpandedBytes());
            processing.setMaxFeatures(limits.getMaxFeatures());
            processing.setTimeoutSeconds(limits.getMaxDurationSeconds());
            var converted = new GeospatialReader(processing).read(new ByteArrayInputStream(archive), "zip", 0, bbox,
                    product == Product.MMA);
            data = new RemoteFeatureReader.Result(converted.content(), List.of(new Original(product.name().toLowerCase(Locale.ROOT)
                    + "_" + competence + ".zip", url, "application/zip", archive)));
            originFormat = "shapefile.zip";
        } else {
            data = switch (product) {
                case ICMBIO -> remote.wfs(ICMBIO, "ICMBio:limiteucsfederais_a", null, "ogc_fid", null, bbox, false);
                case ICMBIO_EMBARGOS -> remote.wfs(ICMBIO, "ICMBio:embargos_icmbio", null, "ogc_fid",
                        "the_geom,ogc_fid,numero_emb,serie,uf,municipio,ano,data,area,nome_uc,cnuc,julgamento", bbox, false);
                case FUNAI -> remote.wfs(FUNAI, "Funai:tis_poligonais", "uf_sigla LIKE '%PR%'", "gid", null, null, false);
                case MMA -> remote.wfs(MMA, "ms:ucs_selected", null, "uc_id,docleg_id", null, bbox, true);
                case IBAMA -> remote.arcgis(IBAMA, "objectid,seq_tad,num_tad,serie_tad,uf,cod_municipio,municipio,dat_embargo,dat_ult_alteracao,des_tipo_bioma,qtd_area_embargada", bbox);
                default -> throw new IllegalArgumentException("Produto sem canal público configurado.");
            };
            originFormat = product == Product.MMA ? "WFS GML paginado" : product == Product.IBAMA ? "ArcGIS GeoJSON paginado" : "WFS GeoJSON paginado";
        }
        byte[] content = spatial.clip(data.content(), boundary);
        http.checkCollectionDeadline();
        if (content.length > limits.getMaxTotalBytes()) throw OfficialDataClient.upstream("Conteúdo convertido excede o limite da coleta.");
        var originals = new ArrayList<>(data.originals());
        originals.add(boundary.original());
        var entry = catalog.entry(product);
        var metadata = new Metadata(product.name(), competence, "geojson", originFormat, "Paraná (UF 41); recorte estadual"
                + (request.bbox() == null ? "" : "; bbox=" + request.bbox()), Instant.now().toString(), entry.documentation(),
                List.of(entry.limitations(), ParanaSpatialFilter.LIMITATION,
                        "Atributos de área da origem não são recalculados pelo recorte.",
                        current ? "Retrato da base na data da coleta; não representa um ano histórico." : "Edição publicada: " + competence),
                originals.stream().map(o -> new OriginalMetadata(o.filename(), o.origin(), o.contentType(), o.content().length, o.sha256())).toList());
        return new CollectedData(product.name().toLowerCase(Locale.ROOT) + "_PR_" + competence + ".geojson", "geojson", content, List.copyOf(originals), metadata);
    }

    static void validateBbox(List<Double> bbox) {
        if (bbox != null && (bbox.size() != 4 || bbox.stream().anyMatch(n -> n == null || !Double.isFinite(n))
                || bbox.get(0) < -180 || bbox.get(2) > 180 || bbox.get(1) < -90 || bbox.get(3) > 90
                || bbox.get(0) >= bbox.get(2) || bbox.get(1) >= bbox.get(3)))
            throw new IllegalArgumentException("bbox deve ser [oeste,sul,leste,norte].");
    }
}
