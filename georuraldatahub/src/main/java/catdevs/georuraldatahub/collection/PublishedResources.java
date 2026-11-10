package catdevs.georuraldatahub.collection;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import static catdevs.georuraldatahub.collection.CollectionRequest.Product;

/** Resolve edições no catálogo do órgão em cada consulta; nunca substitui um ano por outro. */
@Component
public class PublishedResources {
    static final String MMA_CATALOG = "https://dados.mma.gov.br/api/3/action/package_show?id=unidadesdeconservacao";
    static final String SFB_CATALOG = "https://dados.florestal.gov.br/api/3/action/package_show?id=cadastro-nacional-de-florestas-publicas-cnfp";
    static final String INCRA_ARCHIVE = "https://certificacao.incra.gov.br/csv_shp/zip/Assentamento%20Brasil.zip";
    static final String IBGE_MESHES = "https://geoftp.ibge.gov.br/organizacao_do_territorio/malhas_territoriais/malhas_municipais/";
    static final String FIRES_DIRECTORY = "https://dataserver-coids.inpe.br/queimadas/queimadas/focos/csv/diario/Brasil/";
    private final OfficialDataClient http;
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public PublishedResources(OfficialDataClient http) { this.http = http; }
    public record Edition(String competence, String url, String format, String description) {}
    public record Availability(String product, String temporalMode, List<Edition> editions, String limitation) {}

    public Availability availability(Product product) throws IOException, InterruptedException {
        try (var budget = http.collectionBudget()) {
            if (product == Product.INPE_QUEIMADAS) {
                var entries = new ArrayList<Edition>();
                var today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
                for (String link : links(FIRES_DIRECTORY)) {
                    var date = Pattern.compile("focos_diario_br_([0-9]{8})\\.csv").matcher(link);
                    if (!date.matches()) continue;
                    var day = java.time.LocalDate.parse(date.group(1), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
                    if (day.isBefore(today)) entries.add(new Edition(day.toString(), FIRES_DIRECTORY + link, "csv", "Arquivo diário nacional; coleta filtrada ao Paraná"));
                }
                return new Availability(product.name(), "DIARIO", entries.stream().sorted(Comparator.comparing(Edition::competence)).toList(),
                        "Somente dias encerrados presentes no diretório oficial. A retenção é limitada; ausência não significa zero focos.");
            }
            if (product == Product.IBGE_MUNICIPIOS) {
                var entries = new ArrayList<Edition>();
                for (String link : links(IBGE_MESHES)) {
                    var year = Pattern.compile("municipio_([0-9]{4})/").matcher(link);
                    if (year.matches()) entries.add(new Edition(year.group(1), IBGE_MESHES + link, "shapefile.zip", "Malha municipal publicada"));
                }
                if (entries.isEmpty()) throw OfficialDataClient.upstream("IBGE não informou edições de malhas reconhecidas.");
                return new Availability(product.name(), "EDICOES_PUBLICADAS", entries.stream().sorted(Comparator.comparing(Edition::competence)).toList(),
                        "Diretórios publicados pelo IBGE. Formatos históricos são resolvidos pela edição; consulte /verificacao para confirmar a conversão.");
            }
            if (product == Product.MMA || product == Product.SFB) {
                var editions = new ArrayList<>(editions(product));
                if (product == Product.MMA) editions.addFirst(new Edition("ATUAL", null, "WFS GML",
                        "Retrato do CNUC na data da coleta, independente das edições ZIP."));
                return new Availability(product.name(), "EDICOES_PUBLICADAS", editions,
                        "Edições descobertas no catálogo oficial. O download pode ficar temporariamente indisponível; use /verificacao para validar o conteúdo. Ano sem edição não é substituído pelo atual.");
            }
            if (Set.of(Product.ICMBIO, Product.ICMBIO_EMBARGOS, Product.FUNAI, Product.INCRA, Product.IBAMA).contains(product))
                return new Availability(product.name(), "BASE_ATUAL", List.of(new Edition("ATUAL", null, "geojson",
                        "Retrato do serviço na data da coleta; não é série histórica anual.")),
                        "Use competence=ATUAL. O órgão não publica histórico anual neste canal.");
            if (product == Product.IBGE_BIOMAS)
                return new Availability(product.name(), "EDICOES_PUBLICADAS", List.of(
                        new Edition("2019", null, "shapefile.zip", "Biomas 1:250.000"),
                        new Edition("2025", null, "shapefile.zip", "Biomas e Sistema Costeiro-Marinho 1:250.000")),
                        "Não existe edição para cada ano. A verificação baixa e valida a edição solicitada.");
            return new Availability(product.name(), "ANUAL", List.of(),
                    "Informe ano YYYY e bioma em /verificacao. Séries antigas incluem períodos agregados; resultado vazio no recorte não comprova publicação do ano.");
        }
    }

    List<String> links(String url) throws IOException, InterruptedException {
        String html = new String(http.listing(URI.create(url)), StandardCharsets.UTF_8);
        var matches = Pattern.compile("(?i)href=[\"']([^\"']+)[\"']").matcher(html);
        var links = new ArrayList<String>();
        while (matches.find()) {
            String link = matches.group(1).replace("&amp;", "&");
            if (!link.contains("..") && !link.contains(":") && !link.startsWith("/") && !link.startsWith("?")) links.add(link);
        }
        return links;
    }

    String municipalArchive(int year) throws IOException, InterruptedException {
        String path = IBGE_MESHES + "municipio_" + year + "/";
        path += year >= 2015 ? "UFs/PR/" : year >= 2013 ? "PR/"
                : year == 2007 ? "escala_2500mil/proj_geografica_sirgas2000/uf/pr/"
                : year == 2005 ? "escala_2500mil/proj_geografica/arcview_shp/uf/pr/" : "pr/";
        List<String> candidates = links(path).stream().filter(n -> n.toLowerCase(Locale.ROOT).matches("(?:pr_municipios(?:_[0-9]{4})?|41mu2500g(?:c|sr)?)\\.zip")).toList();
        if (candidates.size() != 1) throw OfficialDataClient.upstream("IBGE não publicou um ZIP municipal do Paraná reconhecido para " + year + ".");
        return path + candidates.getFirst();
    }

    List<Edition> editions(Product product) throws IOException, InterruptedException {
        String url = product == Product.MMA ? MMA_CATALOG : SFB_CATALOG;
        JsonNode root = mapper.readTree(http.download(URI.create(url)));
        if (root == null || !root.path("success").asBoolean() || !root.path("result").path("resources").isArray())
            throw OfficialDataClient.upstream("Catálogo oficial sem recursos verificáveis.");
        List<Edition> editions = new ArrayList<>();
        for (var resource : root.path("result").path("resources")) {
            String name = resource.path("name").asText(), target = resource.path("url").asText();
            if (product == Product.MMA) {
                if (!resource.path("format").asText().toLowerCase(Locale.ROOT).contains("zip")) continue;
                var match = Pattern.compile("(20[0-9]{2})[_ -](0[1-9]|1[0-2])").matcher(name);
                if (!match.find()) continue;
                if (target.startsWith("https://mmabr-my.sharepoint.com/")) target += (target.contains("?") ? "&" : "?") + "download=1";
                OfficialDataClient.checkUri(URI.create(target));
                editions.add(new Edition(match.group(1) + "-" + match.group(2), target, "shapefile.zip", name));
            } else {
                var match = Pattern.compile("^(20[0-9]{2})\\b").matcher(name);
                if (!match.find() || !target.startsWith("https://mapas.florestal.gov.br/portal/home/item.html?id=")) continue;
                String id = target.substring(target.indexOf("id=") + 3);
                if (!id.matches("[a-f0-9]{32}")) throw OfficialDataClient.upstream("Identificador de recurso SFB inválido.");
                editions.add(new Edition(match.group(1), "https://mapas.florestal.gov.br/portal/sharing/rest/content/items/" + id + "/data",
                        "shapefile.zip", name));
            }
        }
        if (editions.isEmpty()) throw OfficialDataClient.upstream("Catálogo não publicou edições geográficas reconhecidas.");
        return editions.stream().sorted(Comparator.comparing(Edition::competence)).toList();
    }

    Edition resolve(Product product, String competence) throws IOException, InterruptedException {
        if (competence == null || !competence.matches("20[0-9]{2}(-(?:0[1-9]|1[0-2]))?"))
            throw new IllegalArgumentException("Informe uma edição YYYY ou YYYY-MM publicada no catálogo.");
        return editions(product).stream().filter(e -> e.competence().equals(competence)
                || competence.length() == 4 && e.competence().startsWith(competence + "-"))
                .max(Comparator.comparing(Edition::competence))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Não há edição geográfica publicada para " + product + " em " + competence + ". Consulte /edicoes."));
    }

    static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
