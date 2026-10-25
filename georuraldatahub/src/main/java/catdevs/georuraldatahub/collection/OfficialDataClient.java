package catdevs.georuraldatahub.collection;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OfficialDataClient {
    private static final Set<String> HOSTS = Set.of("servicodados.ibge.gov.br", "geoftp.ibge.gov.br",
            "terrabrasilis.dpi.inpe.br", "dataserver-coids.inpe.br");
    private final HttpClient client;
    private final CollectionProperties properties;
    private final ThreadLocal<Long> collectionDeadline = new ThreadLocal<>();

    @org.springframework.beans.factory.annotation.Autowired
    public OfficialDataClient(CollectionProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    OfficialDataClient(CollectionProperties properties, HttpClient client) {
        this.properties = properties;
        this.client = client;
    }
    static void checkUri(URI uri) {
        if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost()) || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getPort() != -1 && uri.getPort() != 443)
            throw new IllegalArgumentException("Destino fora dos canais oficiais autorizados.");
    }

    public CollectionBudget collectionBudget() {
        Long previous = collectionDeadline.get();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.getMaxDurationSeconds());
        collectionDeadline.set(previous == null ? deadline : Math.min(previous, deadline));
        return () -> { if (previous == null) collectionDeadline.remove(); else collectionDeadline.set(previous); };
    }
    public void checkCollectionDeadline() {
        Long deadline = collectionDeadline.get();
        if (deadline != null && System.nanoTime() >= deadline) throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                "Coleta excedeu o prazo total de " + properties.getMaxDurationSeconds() + " segundos. Tente novamente ou solicite um recorte menor.");
    }
    @FunctionalInterface
    public interface CollectionBudget extends AutoCloseable { @Override void close(); }

    public byte[] download(URI uri) throws IOException, InterruptedException {
        checkUri(uri);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.getRequestTimeoutSeconds());
        Long collectionEnd = collectionDeadline.get();
        if (collectionEnd != null) deadline = Math.min(deadline, collectionEnd);
        for (int attempt = 0; attempt < 3; attempt++) {
            URI target = uri;
            for (int redirect = 0; redirect <= 3; redirect++) {
                checkUri(target);
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw timeout(uri);
                var request = HttpRequest.newBuilder(target).timeout(Duration.ofNanos(remaining))
                        .header("User-Agent", "GeoRuralDataHub/1.0 (public environmental data collection)")
                        .header("Accept", "application/json, text/csv, application/zip, */*").GET().build();
                CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, this::body);
                HttpResponse<byte[]> response;
                try { response = pending.get(remaining, TimeUnit.NANOSECONDS); }
                catch (TimeoutException e) { pending.cancel(true); throw timeout(uri); }
                catch (InterruptedException e) { pending.cancel(true); throw e; }
                catch (ExecutionException e) {
                    if (e.getCause() instanceof ResponseStatusException failure) throw failure;
                    if (e.getCause() instanceof HttpTimeoutException) throw timeout(uri);
                    if (e.getCause() instanceof IOException failure) throw new IOException("Falha ao ler " + target.getHost() + ": " + failure.getMessage(), failure);
                    throw new IOException("Falha de transporte no canal oficial " + target.getHost() + ".", e.getCause());
                }
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> upstream("Redirecionamento sem destino."));
                    try { target = target.resolve(location); }
                    catch (IllegalArgumentException e) { throw upstream("Redirecionamento inválido no canal oficial."); }
                    if (redirect == 3) throw upstream("Excesso de redirecionamentos.");
                    continue;
                }
                if ((status == 429 || status >= 500) && attempt < 2) {
                    long pause = Math.min(TimeUnit.SECONDS.toNanos(1L << attempt), deadline - System.nanoTime());
                    if (pause <= 0) throw timeout(uri);
                    TimeUnit.NANOSECONDS.sleep(pause);
                    break;
                }
                if (status == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Produto/competência não disponível neste canal. Confira o ano/data publicado: " + uri);
                if (status != 200) throw upstream("Fonte respondeu HTTP " + status + ": " + uri);
                byte[] bytes = response.body();
                if (bytes.length == 0) throw upstream("Fonte retornou arquivo vazio: " + uri);
                if (bytes.length > properties.getMaxDownloadBytes()) throw upstream("Arquivo excede o limite configurado para coleta.");
                String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT);
                if (type.contains("text/html")) throw upstream("O canal retornou uma página HTML, não um arquivo de dados: " + uri);
                return bytes;
            }
        }
        throw upstream("Fonte indisponível após três tentativas: " + uri);
    }

    private HttpResponse.BodySubscriber<byte[]> body(HttpResponse.ResponseInfo info) {
        if (info.statusCode() != 200) return HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.discarding(), ignored -> new byte[0]);
        int maximum = properties.getMaxDownloadBytes();
        if (maximum < 1) throw upstream("Limite de download inválido.");
        try {
            if (info.headers().firstValueAsLong("Content-Length").orElse(0) > maximum)
                throw upstream("Arquivo excede o limite configurado para coleta.");
        } catch (NumberFormatException e) { throw upstream("Canal oficial retornou tamanho de arquivo inválido."); }
        return new LimitedBodySubscriber(maximum);
    }

    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int maximum;
        private long received;
        private Flow.Subscription subscription;
        private boolean failed;
        LimitedBodySubscriber(int maximum) { this.maximum = maximum; }
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; delegate.onSubscribe(subscription); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (failed) return;
            for (var buffer : buffers) received += buffer.remaining();
            if (received > maximum) {
                failed = true; subscription.cancel();
                delegate.onError(upstream("Arquivo excede o limite configurado para coleta."));
            } else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { delegate.onError(error); }
        @Override public void onComplete() { if (!failed) delegate.onComplete(); }
    }
    private ResponseStatusException timeout(URI uri) {
        checkCollectionDeadline();
        return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                "O canal oficial não concluiu o download em " + properties.getRequestTimeoutSeconds() + " segundos: " + uri);
    }
    static ResponseStatusException upstream(String message) { return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message); }
}
