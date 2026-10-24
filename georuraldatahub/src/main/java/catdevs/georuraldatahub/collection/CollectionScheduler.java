package catdevs.georuraldatahub.collection;

import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import catdevs.georuraldatahub.exception.DuplicateFileException;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "collection.scheduling.enabled",
    havingValue = "true"
)
public class CollectionScheduler {

    private static final Logger LOG =
            LoggerFactory.getLogger(CollectionScheduler.class);

    private final CollectionProperties properties;
    private final CollectionService service;

    public CollectionScheduler(
            CollectionProperties properties,
            CollectionService service) {
        this.properties = properties;
        this.service = service;
    }

    @Scheduled(
        cron = "${collection.scheduling.cron:0 0 10 * * *}",
        zone = "America/Sao_Paulo"
    )
    public void run() {
        for (var job : properties.getJobs()) {
            try {
                var request = resolved(job.request());

                var result = service.collect(job.sourceId(), request);

                LOG.info(
                    "Coleta {} fonte {} arquivo {} resultado {}",
                    request.product(),
                    job.sourceId(),
                    result.file().id(),
                    result.file().validation().status()
                );

            } catch (DuplicateFileException e) {
                LOG.info(
                    "Coleta fonte {} sem conteúdo novo: {}",
                    job.sourceId(),
                    e.getMessage()
                );

            } catch (Exception e) {
                LOG.error(
                    "Falha na coleta da fonte {}",
                    job.sourceId(),
                    e
                );

                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }
        }
    }

    static CollectionRequest resolved(CollectionRequest request) {
        if (request != null
                && request.product() == CollectionRequest.Product.INPE_QUEIMADAS
                && "D-1".equals(request.competence())) {

            return new CollectionRequest(
                request.product(),
                request.datasetId(),
                request.userId(),
                LocalDate.now(ZoneId.of("America/Sao_Paulo"))
                        .minusDays(1)
                        .toString(),
                request.municipality(),
                request.biome(),
                request.bbox()
            );
        }

        return request;
    }
}