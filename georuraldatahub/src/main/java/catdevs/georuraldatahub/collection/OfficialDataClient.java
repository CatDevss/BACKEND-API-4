package catdevs.georuraldatahub.collection;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OfficialDataClient {
    private static final Set<String> HOSTS = Set.of("servicodados.ibge.gov.br", "geoftp.ibge.gov.br",
            "terrabrasilis.dpi.inpe.br", "dataserver-coids.inpe.br");
    private final HttpClient client;
    private final CollectionProperties properties;
    @org.springframework.beans.factory.annotation.Autowired
    public OfficialDataClient(CollectionProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    OfficialDataClient(CollectionProperties properties, HttpClient client) {
        this.properties = properties; this.client = client;
    }
    static void checkUri(URI uri) {
        if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost()) || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getPort() != -1 && uri.getPort() != 443)
            throw new IllegalArgumentException("Destino fora dos canais oficiais autorizados.");
    }
    public byte[] download(URI uri) throws IOException, InterruptedException {
        checkUri(uri);
        for (int attempt = 0; attempt < 3; attempt++) {
            URI target = uri;
            for (int redirect = 0; redirect <= 3; redirect++) {
                checkUri(target);
                var request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(90))
                        .header("User-Agent", "GeoRuralDataHub/1.0 (public environmental data collection)")
                        .header("Accept", "application/json, text/csv, application/zip, */*").GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream input = response.body()) {
                    int status = response.statusCode();
                    if (status >= 300 && status < 400) {
                        String location = response.headers().firstValue("Location")
                                .orElseThrow(() -> upstream("Redirecionamento sem destino."));
                        target = target.resolve(location);
                        if (redirect == 3) throw upstream("Excesso de redirecionamentos.");
                        continue;
                    }
                    if ((status == 429 || status >= 500) && attempt < 2) {
                        Thread.sleep(1000L << attempt);
                        break;
                    }
                    if (status == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "O produto/competência não foi publicado neste canal: " + uri);
                    if (status != 200) throw upstream("Fonte respondeu HTTP " + status + ": " + uri);
                    int maximum = properties.getMaxDownloadBytes();
                    if (maximum < 1 || response.headers().firstValueAsLong("Content-Length").orElse(0) > maximum)
                        throw upstream("Arquivo excede o limite configurado para coleta.");
                    byte[] bytes = input.readNBytes(maximum + 1);
                    if (bytes.length > maximum) throw upstream("Arquivo excede o limite configurado para coleta.");
                    if (bytes.length == 0) throw upstream("Fonte retornou arquivo vazio.");
                    return bytes;
                }
            }
        }
        throw upstream("Fonte indisponível após três tentativas.");
    }
    static ResponseStatusException upstream(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }
}
